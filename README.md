# QuickGrade — offline answer-sheet grader for Android (Kotlin + OpenCV)

Works like ZipGrade: print a bubble sheet, students fill it in, you photograph each paper and
get the score instantly. No internet is needed at any point after the first Gradle sync.

## Build
1. Install Android Studio (Koala or newer). Open this folder (`QuickGrade`).
2. Let Gradle sync (needs internet once, to download Compose, CameraX and OpenCV).
3. Plug in an Android phone (USB debugging on) and press Run. Or Build > Build APK(s) and copy
   `app/build/outputs/apk/debug/app-debug.apk` to the phone.

## Use
1. New answer key: name it, set the number of questions (5-100), tap the correct letter for each.
2. Open the key > **Print sheet** saves an A4 PDF. Print one per student.
3. Students fill in the 6-digit Student ID and their answers (dark pen/pencil, fill completely).
4. **Scan papers**: hold the phone upright over one sheet so all 4 black corner squares are
   visible, tap Scan. Check the result (green ring = correct answer, red ring = wrong mark),
   fix the ID if it shows `?`, tap **Save & next**.
5. **Export CSV** saves a spreadsheet with every student's score and answers.
   Editing a key re-grades every saved paper automatically.

## Files
- `SheetLayout.kt` — one layout shared by the printed sheet and the scanner
- `SheetPdf.kt` — draws the printable answer sheet
- `OmrScanner.kt` — finds the corner squares, straightens the photo, reads the bubbles
- `Db.kt` — SQLite storage for keys and results
- `MainActivity.kt`, `Screens.kt` — Jetpack Compose UI

## Tuning
In `OmrScanner.kt`, `MARK_THRESHOLD` (default 0.30) is how dark a bubble must be to count as marked.
Lower it if light pencil marks are being missed; raise it if smudges are counted.
