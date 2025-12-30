package com.example.operit.shizuku

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

object ShizukuScreencap {
    data class CaptureResult(
        val pngFile: File,
        val pngBytes: ByteArray,
        val dataUrl: String,
        val width: Int?,
        val height: Int?,
    )

    fun isReady(): Boolean {
        return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun capture(context: Context, timeoutMs: Long = 10_000L): Result<CaptureResult> {
        return runCatching {
            if (!isReady()) error("Shizuku 未授权或未运行")

            val baseDir = context.getExternalFilesDir(null) ?: error("外部存储不可用")
            val screenshotDir = File(baseDir, "autoglm/cleanOnExit")
            if (!screenshotDir.exists() && !screenshotDir.mkdirs()) {
                error("创建截图目录失败：${screenshotDir.absolutePath}")
            }

            val shortName = System.currentTimeMillis().toString().takeLast(6)
            val file = File(screenshotDir, "$shortName.png")
            if (file.exists()) {
                runCatching { file.delete() }
            }

            val cmd = "screencap -p ${escapeShellArg(file.absolutePath)}"
            val proc = newProcess(arrayOf("sh", "-c", cmd))
            val finished = waitFor(proc, timeoutMs)
            if (!finished) {
                runCatching { proc.destroy() }
                error("screencap 超时（>${timeoutMs}ms）")
            }

            val exitCode = runCatching { proc.exitValue() }.getOrNull() ?: 0
            if (exitCode != 0) {
                val err = runCatching { proc.errorStream.bufferedReader().use { it.readText().trim() } }.getOrNull().orEmpty()
                error("screencap 失败：exit=$exitCode${if (err.isBlank()) "" else ", err=$err"}")
            }
            if (!file.exists() || file.length() <= 0L) {
                error("screencap 未生成有效文件：${file.absolutePath}")
            }

            val bytes = file.readBytes()

            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val dataUrl = "data:image/png;base64,$b64"

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val width = options.outWidth.takeIf { it > 0 }
            val height = options.outHeight.takeIf { it > 0 }

            CaptureResult(pngFile = file, pngBytes = bytes, dataUrl = dataUrl, width = width, height = height)
        }
    }

    private fun waitFor(proc: Process, timeoutMs: Long): Boolean {
        val latch = CountDownLatch(1)
        Thread {
            runCatching { proc.waitFor() }
            latch.countDown()
        }.start()
        return latch.await(timeoutMs.coerceAtLeast(100L), TimeUnit.MILLISECONDS)
    }

    private fun escapeShellArg(value: String): String {
        // 单引号安全转义：' -> '"'"'
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun newProcess(cmd: Array<String>): Process {
        // Shizuku 13+ 将 newProcess 设为 private，但仍可通过反射调用（返回 Process）。
        val m =
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
        m.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return m.invoke(null, cmd, null, null) as Process
    }
}
