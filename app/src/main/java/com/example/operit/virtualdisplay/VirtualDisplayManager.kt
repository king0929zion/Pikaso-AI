package com.example.operit.virtualdisplay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import com.example.operit.logging.AppLog
import java.io.File
import java.io.FileOutputStream

class VirtualDisplayManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "VirtualDisplayManager"

        @Volatile private var instance: VirtualDisplayManager? = null

        fun getInstance(context: Context): VirtualDisplayManager {
            return instance ?: synchronized(this) {
                instance ?: VirtualDisplayManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var displayId: Int? = null

    fun ensureVirtualDisplay(): Int? {
        if (virtualDisplay != null && displayId != null) return displayId
        return createVirtualDisplay()
    }

    fun getDisplayId(): Int? = displayId

    fun getDisplay(): Display? = virtualDisplay?.display

    fun release() {
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            AppLog.e(TAG, "释放虚拟屏幕失败", e)
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (e: Exception) {
            AppLog.e(TAG, "关闭 ImageReader 失败", e)
        }
        imageReader = null
        displayId = null
    }

    fun captureLatestFrameToFile(file: File): Boolean {
        val reader = imageReader ?: return false
        var image: Image? = null
        return try {
            image = reader.acquireLatestImage() ?: return false
            val width = image.width
            val height = image.height
            if (width <= 0 || height <= 0) return false

            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap =
                Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888,
                )
            bitmap.copyPixelsFromBuffer(buffer)

            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            FileOutputStream(file).use { out ->
                if (!cropped.compress(Bitmap.CompressFormat.PNG, 100, out)) return false
            }
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "截图虚拟屏幕帧失败", e)
            false
        } finally {
            try {
                image?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun createVirtualDisplay(): Int? {
        return try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels.coerceAtLeast(1)
            val height = metrics.heightPixels.coerceAtLeast(1)
            val densityDpi = metrics.densityDpi

            // 注意：PUBLIC flag 在普通应用上可能触发创建失败（权限限制），这里仅保留 PRESENTATION 以便展示/调试。
            val reader =
                try {
                    ImageReader.newInstance(width, height, ImageFormat.FLEX_RGBA_8888, 2)
                } catch (_: Throwable) {
                    ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                }
            imageReader = reader
            val surface: Surface = reader.surface

            val flags =
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION

            val vd =
                displayManager.createVirtualDisplay(
                    "PikasoVirtualDisplay",
                    width,
                    height,
                    densityDpi,
                    surface,
                    flags,
                )
            virtualDisplay = vd

            val id = vd.display?.displayId
            displayId = id

            AppLog.i(TAG, "已创建虚拟屏幕 id=$id, size=${width}x$height, density=$densityDpi")
            id
        } catch (e: Exception) {
            AppLog.e(TAG, "创建虚拟屏幕失败", e)
            null
        }
    }
}
