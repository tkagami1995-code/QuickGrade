package com.example.quickgrade

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cvOk = OpenCVLoader.initLocal()
        val db = Db(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (cvOk) App(db)
                    else Text("Could not load the image-processing library (OpenCV).", Modifier.padding(24.dp))
                }
            }
        }
    }
}

sealed interface Screen {
    data object Home : Screen
    data class Editor(val key: AnswerKey?) : Screen
    data class Detail(val key: AnswerKey) : Screen
    data class Scan(val key: AnswerKey) : Screen
    data class Review(val key: AnswerKey, val scan: ScanResult) : Screen
}

@Composable
fun App(db: Db) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    BackHandler(enabled = screen !is Screen.Home) {
        screen = when (val s = screen) {
            is Screen.Review -> Screen.Scan(s.key)
            is Screen.Scan -> Screen.Detail(s.key)
            is Screen.Editor -> if (s.key != null) Screen.Detail(s.key) else Screen.Home
            else -> Screen.Home
        }
    }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(
            db,
            onNew = { screen = Screen.Editor(null) },
            onOpen = { screen = Screen.Detail(it) }
        )
        is Screen.Editor -> EditorScreen(
            existing = s.key,
            onSave = { name, answers ->
                val id = db.saveKey(s.key?.id, name, answers)
                screen = Screen.Detail(AnswerKey(id, name, answers))
            },
            onCancel = { screen = if (s.key != null) Screen.Detail(s.key) else Screen.Home }
        )
        is Screen.Detail -> DetailScreen(
            db, s.key,
            onScan = { screen = Screen.Scan(s.key) },
            onEdit = { screen = Screen.Editor(s.key) },
            onDeleted = { screen = Screen.Home }
        )
        is Screen.Scan -> ScanScreen(
            s.key,
            onScanned = { screen = Screen.Review(s.key, it) },
            onBack = { screen = Screen.Detail(s.key) }
        )
        is Screen.Review -> ReviewScreen(
            s.key, s.scan,
            onSave = { id, score ->
                db.saveResult(s.key.id, id, score, s.key.answers.length, s.scan.answers)
                screen = Screen.Scan(s.key)
            },
            onRescan = { screen = Screen.Scan(s.key) }
        )
    }
}
