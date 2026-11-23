package com.example.quickgrade

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

class ScanResult(
    val studentId: String,   // digits, '?' where a digit was blank or ambiguous
    val answers: String,     // per question: A-E, '-' blank, '*' multiple marks
    val overlay: Bitmap      // straightened sheet with green/red rings
)

fun gradeScore(key: String, answers: String): Int =
    key.indices.count { key[it] == answers.getOrNull(it) }

object OmrScanner {
    private const val S = 2.0              // pixels per PDF point after straightening
    private const val MARK_THRESHOLD = 0.30 // fraction of bubble interior that must look dark

    /** Returns null when the four corner squares can't be found. */
    fun scan(input: Bitmap, key: String): ScanResult? {
        val bmp = shrink(input, 2000)
        val color = Mat()
        Utils.bitmapToMat(bmp, color)

        val corners = findCorners(color)
        if (corners == null) { color.release(); return null }

        val src = MatOfPoint2f(*corners.toTypedArray())
        val dst = MatOfPoint2f(*SheetLayout.MARKERS.map { Point(it.first * S, it.second * S) }.toTypedArray())
        val transform = Imgproc.getPerspectiveTransform(src, dst)
        val warped = Mat()
        Imgproc.warpPerspective(
            color, warped, transform,
            Size(SheetLayout.PAGE_W * S, SheetLayout.PAGE_H * S), Imgproc.INTER_LINEAR
        )
        color.release()

        // Flatten uneven lighting: divide by a "paper only" background estimate.
        val gray = Mat()
        Imgproc.cvtColor(warped, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
        val bg = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(41.0, 41.0))
        Imgproc.morphologyEx(gray, bg, Imgproc.MORPH_CLOSE, kernel)
        val norm = Mat()
        Core.divide(gray, bg, norm, 255.0)
        gray.release(); bg.release()

        val masks = HashMap<Int, Mat>()
        fun score(cx: Float, cy: Float, r: Float): Double {
            val rr = (0.65 * r * S).roundToInt()
            val x0 = (cx * S).roundToInt() - rr
            val y0 = (cy * S).roundToInt() - rr
            val side = 2 * rr + 1
            if (x0 < 0 || y0 < 0 || x0 + side > norm.cols() || y0 + side > norm.rows()) return 0.0
            val mask = masks.getOrPut(rr) {
                Mat.zeros(Size(side.toDouble(), side.toDouble()), CvType.CV_8UC1).also {
                    Imgproc.circle(it, Point(rr.toDouble(), rr.toDouble()), rr, Scalar(255.0), -1)
                }
            }
            val roi = Mat(norm, Rect(x0, y0, side, side))
            return 1.0 - Core.mean(roi, mask).`val`[0] / 255.0
        }

        val letters = "ABCDE"
        val answers = StringBuilder()
        for (q in key.indices) {
            val sc = DoubleArray(SheetLayout.CHOICES) { ch ->
                val (x, y) = SheetLayout.answerCenter(q, ch)
                score(x, y, SheetLayout.BUBBLE_R)
            }
            answers.append(pick(sc, letters))
        }

        val id = StringBuilder()
        for (p in 0 until SheetLayout.ID_DIGITS) {
            val sc = DoubleArray(10) { d ->
                val (x, y) = SheetLayout.idCenter(p, d)
                score(x, y, SheetLayout.ID_BUBBLE_R)
            }
            val ch = pick(sc, "0123456789")
            id.append(if (ch == '-' || ch == '*') '?' else ch)
        }
        val studentId = if (id.all { it == '?' }) "" else id.toString()

        // Overlay: green ring on the correct answer, red ring on a wrong mark.
        val green = Scalar(0.0, 170.0, 0.0, 255.0)
        val red = Scalar(220.0, 0.0, 0.0, 255.0)
        val ringR = (6.5 * S).roundToInt()
        for (q in key.indices) {
            val k = letters.indexOf(key[q])
            if (k >= 0) {
                val (x, y) = SheetLayout.answerCenter(q, k)
                Imgproc.circle(warped, Point(x * S, y * S), ringR, green, 2)
            }
            val a = letters.indexOf(answers[q])
            if (a >= 0 && a != k) {
                val (x, y) = SheetLayout.answerCenter(q, a)
                Imgproc.circle(warped, Point(x * S, y * S), ringR, red, 2)
            }
        }
        val out = Bitmap.createBitmap(warped.cols(), warped.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(warped, out)

        masks.values.forEach { it.release() }
        norm.release(); warped.release()
        return ScanResult(studentId, answers.toString(), out)
    }

    private fun pick(sc: DoubleArray, symbols: String): Char {
        val best = sc.indices.maxByOrNull { sc[it] }!!
        if (sc[best] < MARK_THRESHOLD) return '-'
        val others = sc.indices.count { it != best && sc[it] >= MARK_THRESHOLD && sc[it] >= 0.6 * sc[best] }
        return if (others > 0) '*' else symbols[best]
    }

    /** Finds the four black corner squares; returns [TL, TR, BR, BL] centers or null. */
    private fun findCorners(color: Mat): List<Point>? {
        val gray = Mat()
        Imgproc.cvtColor(color, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        var block = minOf(gray.cols(), gray.rows()) / 8
        if (block % 2 == 0) block += 1
        val bin = Mat()
        Imgproc.adaptiveThreshold(
            gray, bin, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, block, 12.0
        )
        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(bin, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val imgArea = gray.cols().toDouble() * gray.rows()
        val pts = ArrayList<Point>()
        for (c in contours) {
            val area = Imgproc.contourArea(c)
            if (area < imgArea * 0.0002 || area > imgArea * 0.004) continue
            val r = Imgproc.boundingRect(c)
            val ratio = r.width.toDouble() / r.height
            if (ratio < 0.7 || ratio > 1.4) continue
            if (area / (r.width.toDouble() * r.height) < 0.75) continue
            val c2f = MatOfPoint2f(*c.toArray())
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.03 * Imgproc.arcLength(c2f, true), true)
            if (approx.total() != 4L) continue
            val m = Imgproc.moments(c)
            if (m.m00 == 0.0) continue
            pts.add(Point(m.m10 / m.m00, m.m01 / m.m00))
        }
        gray.release(); bin.release()
        if (pts.size < 4) return null

        val tl = pts.minBy { it.x + it.y }
        val br = pts.maxBy { it.x + it.y }
        val tr = pts.maxBy { it.x - it.y }
        val bl = pts.minBy { it.x - it.y }
        val four = listOf(tl, tr, br, bl)
        for (i in four.indices) for (j in i + 1 until four.size) {
            if (hypot(four[i].x - four[j].x, four[i].y - four[j].y) < 50.0) return null
        }
        return four
    }

    private fun shrink(b: Bitmap, maxSide: Int): Bitmap {
        val m = max(b.width, b.height)
        if (m <= maxSide) return b
        val f = maxSide.toDouble() / m
        return Bitmap.createScaledBitmap(b, (b.width * f).roundToInt(), (b.height * f).roundToInt(), true)
    }
}
