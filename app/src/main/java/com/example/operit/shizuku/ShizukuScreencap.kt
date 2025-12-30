package com.example.operit.shizuku

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

object ShizukuScreencap {
    enum class CaptureMode {
        /** AutoGLM 官方链路：`screencap -p` 生成 PNG，并以 Base64 形式传给模型。 */
        AUTOGLM_PNG,

        /** 通用预览/日志：压缩为 JPEG，尽量减少体积。 */
        COMPACT_JPEG,
    }

    data class CaptureResult(
        val screenshotFile: File,
        val imageBytes: ByteArray,
        val dataUrl: String,
        val width: Int?,
        val height: Int?,
        val mimeType: String,
    )

    fun isReady(): Boolean {
        return Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun capture(
        context: Context,
        timeoutMs: Long = 10_000L,
        mode: CaptureMode = CaptureMode.COMPACT_JPEG,
    ): Result<CaptureResult> {
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
                error("screencap 超时：${timeoutMs}ms")
            }

            val exitCode = runCatching { proc.exitValue() }.getOrNull() ?: 0
            if (exitCode != 0) {
                val err =
                    runCatching { proc.errorStream.bufferedReader().use { it.readText().trim() } }
                        .getOrNull()
                        .orEmpty()
                error("screencap 失败：exit=$exitCode${if (err.isBlank()) "" else ", err=$err"}")
            }
            if (!file.exists() || file.length() <= 0L) {
                error("screencap 未生成有效文件：${file.absolutePath}")
            }

            val rawBytes = file.readBytes()

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val width = options.outWidth.takeIf { it > 0 }
            val height = options.outHeight.takeIf { it > 0 }

            val (dataUrl, imageBytes, mimeType) =
                when (mode) {
                    CaptureMode.AUTOGLM_PNG -> buildAutoGlmDataUrl(rawBytes)
                    CaptureMode.COMPACT_JPEG -> buildCompactDataUrl(file, rawBytes)
                }

            CaptureResult(
                screenshotFile = file,
                imageBytes = imageBytes,
                dataUrl = dataUrl,
                width = width,
                height = height,
                mimeType = mimeType,
            )
        }
    }

    private fun buildAutoGlmDataUrl(rawPngBytes: ByteArray): Triple<String, ByteArray, String> {
        // 尽量保持与 OpenAI 兼容形式：data:image/png;base64,<...>
        // 同时对极端大图做一次“缩放后仍为 PNG”的压缩，避免触发服务端大小限制导致 400/1210。
        val targetMaxBytes = 3_200_000
        var bytes = rawPngBytes

        if (bytes.size > targetMaxBytes) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val srcW = bounds.outWidth
            val srcH = bounds.outHeight
            if (srcW > 0 && srcH > 0) {
                fun calcInSampleSize(maxEdge: Int): Int {
                    var sample = 1
                    while (srcW / sample > maxEdge || srcH / sample > maxEdge) {
                        sample *= 2
                    }
                    return sample.coerceAtLeast(1)
                }

                val decodeOpts =
                    BitmapFactory.Options().apply {
                        inSampleSize = calcInSampleSize(1280)
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                if (bmp != null) {
                    val baos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    bytes = baos.toByteArray()
                    runCatching { bmp.recycle() }
                }
            }
        }

        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return Triple("data:image/png;base64,$b64", bytes, "image/png")
    }

    private fun buildCompactDataUrl(file: File, rawBytes: ByteArray): Triple<String, ByteArray, String> {
        // base64 会膨胀约 33%，这里控制原始 bytes 尽量 < 2MB，避免触发服务端参数/大小限制
        val targetMaxBytes = 1_800_000

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) {
            val b64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
            return Triple("data:image/png;base64,$b64", rawBytes, "image/png")
        }

        fun calcInSampleSize(maxEdge: Int): Int {
            var sample = 1
            while (srcW / sample > maxEdge || srcH / sample > maxEdge) {
                sample *= 2
            }
            return sample.coerceAtLeast(1)
        }

        val decodeOpts =
            BitmapFactory.Options().apply {
                inSampleSize = calcInSampleSize(1280)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        if (bitmap == null) {
            val b64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
            return Triple("data:image/png;base64,$b64", rawBytes, "image/png")
        }

        fun encodeJpegBytes(bmp: Bitmap, quality: Int): ByteArray {
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(30, 95), baos)
            return baos.toByteArray()
        }

        var working = bitmap
        var outBytes = encodeJpegBytes(working, 75)
        if (outBytes.size > targetMaxBytes) {
            outBytes = encodeJpegBytes(working, 60)
        }
        if (outBytes.size > targetMaxBytes) {
            val maxEdge = 960
            val longer = maxOf(working.width, working.height).coerceAtLeast(1)
            val scale = (maxEdge.toDouble() / longer).coerceIn(0.1, 1.0)
            val newW = (working.width * scale).toInt().coerceAtLeast(1)
            val newH = (working.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(working, newW, newH, true)
            if (scaled !== working) {
                working.recycle()
                working = scaled
            }
            outBytes = encodeJpegBytes(working, 60)
        }

        runCatching { working.recycle() }

        val b64 = Base64.encodeToString(outBytes, Base64.NO_WRAP)
        return Triple("data:image/jpeg;base64,$b64", outBytes, "image/jpeg")
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
        // Shizuku 13+ 将 newProcess 设为 private，但仍可通过反射调用（返回 Process）
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
