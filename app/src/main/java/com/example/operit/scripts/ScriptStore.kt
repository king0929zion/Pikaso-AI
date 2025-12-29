package com.example.operit.scripts

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ScriptStore private constructor(private val context: Context) {
    data class ScriptMeta(
        val id: String,
        val name: String,
        val desc: String,
        val updatedAt: Long,
    )

    fun list(): List<ScriptMeta> {
        val arr = JSONArray(prefs.getString(KEY_INDEX, "[]") ?: "[]")
        val list =
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id")
                val name = obj.optString("name")
                val desc = obj.optString("desc")
                val updatedAt = obj.optLong("updatedAt", 0L)
                if (id.isBlank() || name.isBlank()) return@mapNotNull null
                ScriptMeta(id = id, name = name, desc = desc, updatedAt = updatedAt)
            }
        return list.sortedByDescending { it.updatedAt }
    }

    fun createNew(): ScriptMeta {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val defaultName = "script_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(now)) + ".js"
        val meta = ScriptMeta(id = id, name = defaultName, desc = "新脚本", updatedAt = now)
        saveMeta(meta)
        writeContent(id, defaultTemplate())
        return meta
    }

    fun getMeta(id: String): ScriptMeta? {
        return list().firstOrNull { it.id == id }
    }

    fun saveMeta(meta: ScriptMeta) {
        val list = list().filterNot { it.id == meta.id }.toMutableList()
        list.add(meta)
        val arr =
            JSONArray().apply {
                list.sortedByDescending { it.updatedAt }.forEach { m ->
                    put(
                        JSONObject()
                            .put("id", m.id)
                            .put("name", m.name)
                            .put("desc", m.desc)
                            .put("updatedAt", m.updatedAt),
                    )
                }
            }
        prefs.edit().putString(KEY_INDEX, arr.toString()).apply()
    }

    fun readContent(id: String): String {
        val file = contentFile(id)
        return if (file.exists()) file.readText(Charsets.UTF_8) else ""
    }

    fun writeContent(id: String, content: String) {
        val file = contentFile(id)
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
    }

    fun contentSizeBytes(id: String): Long {
        val file = contentFile(id)
        return if (file.exists()) file.length() else 0L
    }

    private fun contentFile(id: String): File {
        return File(context.filesDir, "scripts/$id.txt")
    }

    private fun defaultTemplate(): String {
        return """
// Pikaso Script (experimental)
// 这里先作为脚本“存储/编辑”能力的基础闭环；后续会接入真正的执行器。

async function main() {
  console.log("Hello, Pikaso!");
}

main();
""".trimIndent()
    }

    private val prefs = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val SP_NAME = "scripts"
        private const val KEY_INDEX = "index"

        fun get(context: Context): ScriptStore = ScriptStore(context.applicationContext)
    }
}

