package com.example.quickgrade

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class AnswerKey(val id: Long, val name: String, val answers: String)

data class StudentResult(
    val id: Long, val keyId: Long, val student: String,
    val score: Int, val total: Int, val answers: String, val ts: Long
)

class Db(ctx: Context) : SQLiteOpenHelper(ctx, "quickgrade.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE keys(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, answers TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE results(id INTEGER PRIMARY KEY AUTOINCREMENT, key_id INTEGER NOT NULL, " +
                "student TEXT NOT NULL, score INTEGER NOT NULL, total INTEGER NOT NULL, " +
                "answers TEXT NOT NULL, ts INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun keys(): List<AnswerKey> {
        val out = ArrayList<AnswerKey>()
        readableDatabase.rawQuery("SELECT id,name,answers FROM keys ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) out.add(AnswerKey(c.getLong(0), c.getString(1), c.getString(2)))
        }
        return out
    }

    /** Creates (id == null) or updates a key. Updating re-grades every saved paper against the new key. */
    fun saveKey(id: Long?, name: String, answers: String): Long {
        val db = writableDatabase
        val cv = ContentValues().apply { put("name", name); put("answers", answers) }
        if (id == null) return db.insert("keys", null, cv)
        db.update("keys", cv, "id=?", arrayOf(id.toString()))
        for (r in results(id)) {
            val u = ContentValues().apply {
                put("score", gradeScore(answers, r.answers))
                put("total", answers.length)
            }
            db.update("results", u, "id=?", arrayOf(r.id.toString()))
        }
        return id
    }

    fun deleteKey(id: Long) {
        writableDatabase.delete("results", "key_id=?", arrayOf(id.toString()))
        writableDatabase.delete("keys", "id=?", arrayOf(id.toString()))
    }

    fun results(keyId: Long): List<StudentResult> {
        val out = ArrayList<StudentResult>()
        readableDatabase.rawQuery(
            "SELECT id,key_id,student,score,total,answers,ts FROM results WHERE key_id=? ORDER BY ts DESC",
            arrayOf(keyId.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(
                StudentResult(c.getLong(0), c.getLong(1), c.getString(2), c.getInt(3), c.getInt(4), c.getString(5), c.getLong(6))
            )
        }
        return out
    }

    /** A paper with the same non-blank student ID replaces the earlier one. */
    fun saveResult(keyId: Long, student: String, score: Int, total: Int, answers: String) {
        val db = writableDatabase
        if (student.isNotBlank()) {
            db.delete("results", "key_id=? AND student=?", arrayOf(keyId.toString(), student))
        }
        val cv = ContentValues().apply {
            put("key_id", keyId); put("student", student); put("score", score)
            put("total", total); put("answers", answers); put("ts", System.currentTimeMillis())
        }
        db.insert("results", null, cv)
    }

    fun deleteResult(id: Long) {
        writableDatabase.delete("results", "id=?", arrayOf(id.toString()))
    }
}
