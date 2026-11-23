package com.example.quickgrade

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

object SheetPdf {
    fun write(out: OutputStream, numQuestions: Int, title: String) {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        draw(page.canvas, numQuestions.coerceIn(1, SheetLayout.MAX_Q), title.take(30))
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    private fun draw(c: Canvas, n: Int, title: String) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 0.8f
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 10f }

        // Corner markers
        val half = SheetLayout.MARKER_SIZE / 2
        for ((x, y) in SheetLayout.MARKERS) c.drawRect(x - half, y - half, x + half, y + half, fill)

        // Header
        text.textSize = 16f; text.isFakeBoldText = true
        c.drawText(title, 75f, 62f, text)
        text.textSize = 10f; text.isFakeBoldText = false
        listOf("Name", "Class", "Date").forEachIndexed { i, label ->
            val y = 100f + i * 30f
            c.drawText("$label:", 75f, y, text)
            c.drawLine(115f, y + 2f, 350f, y + 2f, ring)
        }

        // Student ID grid
        text.textSize = 8f
        c.drawText("STUDENT ID", SheetLayout.ID_LEFT - 12f, SheetLayout.ID_TOP - 14f, text)
        for (d in 0..9) {
            c.drawText("$d", SheetLayout.ID_LEFT - 18f, SheetLayout.ID_TOP + d * SheetLayout.ID_ROW_GAP + 3f, text)
        }
        for (p in 0 until SheetLayout.ID_DIGITS) for (d in 0..9) {
            val (x, y) = SheetLayout.idCenter(p, d)
            c.drawCircle(x, y, SheetLayout.ID_BUBBLE_R, ring)
        }

        // Answer grid: column headers (A-E), question numbers, bubbles
        val letters = "ABCDE"
        for (col in 0..(n - 1) / SheetLayout.ROWS) {
            text.textAlign = Paint.Align.CENTER
            for (ch in 0 until SheetLayout.CHOICES) {
                val (x, _) = SheetLayout.answerCenter(col * SheetLayout.ROWS, ch)
                c.drawText(letters[ch].toString(), x, SheetLayout.GRID_TOP - 14f, text)
            }
        }
        for (q in 0 until n) {
            val col = q / SheetLayout.ROWS
            val (_, y) = SheetLayout.answerCenter(q, 0)
            text.textAlign = Paint.Align.RIGHT
            c.drawText("${q + 1}", SheetLayout.BLOCK_LEFT + col * SheetLayout.BLOCK_W + 20f, y + 3f, text)
            for (ch in 0 until SheetLayout.CHOICES) {
                val (x, yy) = SheetLayout.answerCenter(q, ch)
                c.drawCircle(x, yy, SheetLayout.BUBBLE_R, ring)
            }
        }

        text.textAlign = Paint.Align.LEFT
        text.textSize = 8f
        c.drawText("Fill circles completely with a dark pen or pencil. Keep the sheet flat; do not mark near the black squares.", 75f, 745f, text)
    }
}
