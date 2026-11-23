package com.example.quickgrade

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@Composable
fun HomeScreen(db: Db, onNew: () -> Unit, onOpen: (AnswerKey) -> Unit) {
    val keys = remember { db.keys() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("QuickGrade", style = MaterialTheme.typography.headlineMedium)
        Text("Offline answer-sheet grading")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("New answer key") }
        Spacer(Modifier.height(12.dp))
        if (keys.isEmpty()) Text("No answer keys yet. Create one, print the sheet, then scan the papers.")
        LazyColumn {
            items(keys) { k ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpen(k) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(k.name, style = MaterialTheme.typography.titleMedium)
                        Text("${k.answers.length} questions")
                    }
                }
            }
        }
    }
}

@Composable
fun EditorScreen(existing: AnswerKey?, onSave: (String, String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var n by remember { mutableStateOf(existing?.answers?.length ?: 20) }
    var answers by remember { mutableStateOf((existing?.answers ?: "").padEnd(SheetLayout.MAX_Q, 'A')) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Quiz / exam name") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Text("Questions: $n", Modifier.padding(top = 8.dp))
        Slider(
            value = n.toFloat(), onValueChange = { n = it.roundToInt() },
            valueRange = 5f..SheetLayout.MAX_Q.toFloat()
        )
        LazyColumn(Modifier.weight(1f)) {
            items(n) { q ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${q + 1}", Modifier.width(36.dp))
                    "ABCDE".forEach { ch ->
                        FilterChip(
                            selected = answers[q] == ch,
                            onClick = { answers = answers.substring(0, q) + ch + answers.substring(q + 1) },
                            label = { Text(ch.toString()) }
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = { onSave(name.trim(), answers.take(n)) },
                enabled = name.isNotBlank(), modifier = Modifier.weight(1f)
            ) { Text("Save key") }
        }
    }
}

private fun buildCsv(key: AnswerKey, rs: List<StudentResult>): String {
    val sb = StringBuilder("Student,Score,Total,Percent")
    for (q in 1..key.answers.length) sb.append(",Q").append(q)
    sb.append("\nANSWER KEY,,,")
    key.answers.forEach { sb.append(',').append(it) }
    sb.append('\n')
    for (r in rs.sortedBy { it.student }) {
        sb.append('"').append(r.student.replace("\"", "\"\"")).append("\",")
        sb.append(r.score).append(',').append(r.total).append(',')
            .append((r.score * 100.0 / r.total).roundToInt())
        r.answers.forEach { sb.append(',').append(it) }
        sb.append('\n')
    }
    return sb.toString()
}

@Composable
fun DetailScreen(db: Db, key: AnswerKey, onScan: () -> Unit, onEdit: () -> Unit, onDeleted: () -> Unit) {
    val ctx = LocalContext.current
    var results by remember { mutableStateOf(db.results(key.id)) }
    var confirmDelete by remember { mutableStateOf(false) }

    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) ctx.contentResolver.openOutputStream(uri)?.use {
            SheetPdf.write(it, key.answers.length, key.name)
        }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) ctx.contentResolver.openOutputStream(uri)?.use {
            it.write(buildCsv(key, results).toByteArray())
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(key.name, style = MaterialTheme.typography.headlineSmall)
        val avg = if (results.isEmpty()) "" else
            " · average ${(results.sumOf { it.score * 100.0 / it.total } / results.size).roundToInt()}%"
        Text("${key.answers.length} questions · ${results.size} papers$avg")
        Spacer(Modifier.height(12.dp))
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("Scan papers") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pdf.launch("${key.name} sheet.pdf") }, modifier = Modifier.weight(1f)) { Text("Print sheet") }
            OutlinedButton(onClick = { csv.launch("${key.name} results.csv") }, modifier = Modifier.weight(1f)) { Text("Export CSV") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit key") }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Text("Delete") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(results) { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.student.ifBlank { "(no ID)" }, Modifier.weight(1f))
                    Text("${r.score}/${r.total}")
                    TextButton(onClick = { db.deleteResult(r.id); results = db.results(key.id) }) { Text("✕") }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${key.name}\"?") },
            text = { Text("The key and all its scanned results will be removed.") },
            confirmButton = { TextButton(onClick = { db.deleteKey(key.id); onDeleted() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ScanScreen(key: AnswerKey, onScanned: (ScanResult) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Camera permission is needed to scan answer sheets.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        }
        return
    }

    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            try { ProcessCameraProvider.getInstance(ctx).get().unbindAll() } catch (_: Exception) {}
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                val view = PreviewView(c)
                val future = ProcessCameraProvider.getInstance(c)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                    provider.unbindAll()
                    provider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                }, ContextCompat.getMainExecutor(c))
                view
            }
        )
        Text(
            "Hold the phone upright. All 4 black corner squares must be visible.",
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
        )
        Column(
            Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            error?.let { Text(it, color = Color.White, modifier = Modifier.padding(bottom = 8.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack) { Text("Done") }
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true; error = null
                        val main = Handler(Looper.getMainLooper())
                        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                var res: ScanResult? = null
                                try {
                                    val rot = image.imageInfo.rotationDegrees
                                    var bmp = image.toBitmap()
                                    if (rot != 0) {
                                        bmp = Bitmap.createBitmap(
                                            bmp, 0, 0, bmp.width, bmp.height,
                                            Matrix().apply { postRotate(rot.toFloat()) }, true
                                        )
                                    }
                                    res = OmrScanner.scan(bmp, key.answers)
                                } catch (_: Throwable) {
                                } finally {
                                    image.close()
                                }
                                main.post {
                                    busy = false
                                    val r = res
                                    if (r == null) error = "Couldn't find the 4 corner squares. Try better light and keep the whole sheet in view."
                                    else onScanned(r)
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                main.post { busy = false; error = "Camera error: ${exception.message}" }
                            }
                        })
                    }
                ) { Text(if (busy) "Reading…" else "Scan") }
            }
        }
    }
}

@Composable
fun ReviewScreen(key: AnswerKey, scan: ScanResult, onSave: (String, Int) -> Unit, onRescan: () -> Unit) {
    val score = remember(scan) { gradeScore(key.answers, scan.answers) }
    var id by remember(scan) { mutableStateOf(scan.studentId) }
    val blanks = scan.answers.count { it == '-' }
    val multi = scan.answers.count { it == '*' }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Score: $score / ${key.answers.length}", style = MaterialTheme.typography.headlineMedium)
        if (blanks + multi > 0) Text("$blanks blank, $multi with more than one mark")
        Text("Green ring = correct answer, red ring = wrong mark.")
        OutlinedTextField(
            value = id, onValueChange = { id = it }, label = { Text("Student ID (fix any ?)") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRescan, modifier = Modifier.weight(1f)) { Text("Discard") }
            Button(onClick = { onSave(id.trim(), score) }, modifier = Modifier.weight(1f)) { Text("Save & next") }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Image(
                bitmap = scan.overlay.asImageBitmap(), contentDescription = "Scanned sheet",
                modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth
            )
        }
    }
}
