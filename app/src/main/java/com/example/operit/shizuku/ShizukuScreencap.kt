package com.example.operit.shizuku

import android.util.Base64
import java.io.ByteArrayOutputStream
import rikka.shizuku.Shizuku

object ShizukuScreencap {
    data class CaptureResult(
        val pngBytes: ByteArray,
        val dataUrl: String,
    )

    fun isReady(): Boolean {
        return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun capture(timeoutMs: Long = 10_000L): Result<CaptureResult> {
        return runCatching {
            if (!isReady()) error("Shizuku 未授权或未运行")

            val proc = Shizuku.newProcess(arrayOf("sh", "-c", "screencap -p"), null, null)
            val baos = ByteArrayOutputStream()
            proc.inputStream.use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    baos.write(buf, 0, read)
                }
            }
            // ShizukuRemoteProcess implements java.lang.Process, but we avoid timeout API to keep compatibility simple.
            proc.waitFor()

            val bytes = baos.toByteArray()
            if (bytes.isEmpty()) {
                val err = runCatching { proc.errorStream.bufferedReader().use { it.readText().trim() } }.getOrNull().orEmpty()
                error("screencap 输出为空：$err")
            }

            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val dataUrl = "data:image/png;base64,$b64"
            CaptureResult(pngBytes = bytes, dataUrl = dataUrl)
        }
    }
}
