package com.example.quickgrade

/**
 * One layout definition shared by the PDF generator (SheetPdf) and the scanner (OmrScanner),
 * so what is printed is exactly what is read. All units are PDF points on an A4 page.
 */
object SheetLayout {
    const val PAGE_W = 595f
    const val PAGE_H = 842f

    const val MARKER_SIZE = 24f
    /** Centers of the four black corner squares: top-left, top-right, bottom-right, bottom-left. */
    val MARKERS = listOf(40f to 40f, 555f to 40f, 555f to 802f, 40f to 802f)

    const val MAX_Q = 100
    const val CHOICES = 5
    const val ROWS = 20              // questions per column; 5 columns -> 100 questions
    const val BUBBLE_R = 5.5f

    const val GRID_TOP = 250f
    const val ROW_GAP = 24f
    const val BLOCK_LEFT = 60f
    const val BLOCK_W = 100f
    const val BUBBLE_DX = 28f
    const val BUBBLE_GAP = 14f

    const val ID_DIGITS = 6
    const val ID_BUBBLE_R = 4.5f
    const val ID_LEFT = 390f
    const val ID_TOP = 90f
    const val ID_COL_GAP = 22f
    const val ID_ROW_GAP = 14f

    /** q is zero-based, choice 0..4 = A..E */
    fun answerCenter(q: Int, choice: Int): Pair<Float, Float> {
        val col = q / ROWS
        val row = q % ROWS
        return (BLOCK_LEFT + col * BLOCK_W + BUBBLE_DX + choice * BUBBLE_GAP) to (GRID_TOP + row * ROW_GAP)
    }

    fun idCenter(pos: Int, digit: Int): Pair<Float, Float> =
        (ID_LEFT + pos * ID_COL_GAP) to (ID_TOP + digit * ID_ROW_GAP)
}
