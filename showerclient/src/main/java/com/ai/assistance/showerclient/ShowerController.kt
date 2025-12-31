package com.ai.assistance.showerclient

import android.content.Context
import android.os.IBinder
import android.util.Log
import com.ai.assistance.shower.IShowerService
import com.ai.assistance.shower.IShowerVideoSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Lightweight controller to talk to the Shower server running locally on the device.
 *
 * Responsibilities:
 * - Maintain a single Binder connection to the Shower service
 * - Send simple commands: ensureDisplay, launchApp, tap, swipe, touch, key, screenshot
 * - Track the current virtual display id and video size
 */
object ShowerController {

    private const val TAG = "ShowerController"

    data class FrameStats(
        val receivedFrames: Long,
        val receivedBytes: Long,
        val bufferedFrames: Int,
        val lastFrameBytes: Int,
        val firstFrameAtMs: Long?,
        val lastFrameAtMs: Long?,
        val hasBinaryHandler: Boolean,
        val cachedConfigFrames: Int,
    )

    @Volatile
    private var binderService: IShowerService? = null

    @Volatile
    private var virtualDisplayId: Int? = null

    fun getDisplayId(): Int? = virtualDisplayId

    @Volatile
    private var videoWidth: Int = 0

    @Volatile
    private var videoHeight: Int = 0

    fun getVideoSize(): Pair<Int, Int>? =
        if (videoWidth > 0 && videoHeight > 0) Pair(videoWidth, videoHeight) else null

    private val binaryLock = Any()
    private val earlyBinaryFrames = ArrayDeque<ByteArray>()

    @Volatile
    private var receivedFrames: Long = 0

    @Volatile
    private var receivedBytes: Long = 0

    @Volatile
    private var lastFrameBytes: Int = 0

    @Volatile
    private var firstFrameAtMs: Long = 0

    @Volatile
    private var lastFrameAtMs: Long = 0

    @Volatile
    private var cachedConfig0: ByteArray? = null

    @Volatile
    private var cachedConfig1: ByteArray? = null

    @Volatile
    private var expectConfigFrames: Boolean = false

    @Volatile
    private var configFrameCount: Int = 0

    @Volatile
    private var binaryHandler: ((ByteArray) -> Unit)? = null

    fun getFrameStats(): FrameStats {
        val buffered: Int
        val handlerSet: Boolean
        val cachedCfg: Int
        synchronized(binaryLock) {
            buffered = earlyBinaryFrames.size
            handlerSet = binaryHandler != null
            cachedCfg = (if (cachedConfig0 != null) 1 else 0) + (if (cachedConfig1 != null) 1 else 0)
        }
        val first = firstFrameAtMs.takeIf { it > 0 }
        val last = lastFrameAtMs.takeIf { it > 0 }
        return FrameStats(
            receivedFrames = receivedFrames,
            receivedBytes = receivedBytes,
            bufferedFrames = buffered,
            lastFrameBytes = lastFrameBytes,
            firstFrameAtMs = first,
            lastFrameAtMs = last,
            hasBinaryHandler = handlerSet,
            cachedConfigFrames = cachedCfg,
        )
    }

    fun setBinaryHandler(handler: ((ByteArray) -> Unit)?) {
        val framesToReplay: List<ByteArray>
        synchronized(binaryLock) {
            binaryHandler = handler
            Log.d(TAG, "setBinaryHandler: handlerSet=${handler != null}, bufferedFrames=${earlyBinaryFrames.size}")
            framesToReplay =
                if (handler != null) {
                    val replay = ArrayList<ByteArray>(2 + earlyBinaryFrames.size)
                    cachedConfig0?.let { replay.add(it) }
                    cachedConfig1?.let { replay.add(it) }
                    if (earlyBinaryFrames.isNotEmpty()) {
                        replay.addAll(earlyBinaryFrames)
                        earlyBinaryFrames.clear()
                    }
                    replay
                } else {
                    emptyList()
                }
        }
        if (handler != null && framesToReplay.isNotEmpty()) {
            Log.d(TAG, "setBinaryHandler: replaying ${framesToReplay.size} buffered frames")
            framesToReplay.forEach { frame ->
                try {
                    handler(frame)
                } catch (_: Exception) {
                }
            }
        }
    }

    private val videoSink = object : IShowerVideoSink.Stub() {
        override fun onVideoFrame(data: ByteArray) {
            val handler: ((ByteArray) -> Unit)?
            synchronized(binaryLock) {
                if (firstFrameAtMs == 0L) {
                    firstFrameAtMs = System.currentTimeMillis()
                }
                lastFrameAtMs = System.currentTimeMillis()
                receivedFrames++
                receivedBytes += data.size.toLong()
                lastFrameBytes = data.size

                if (expectConfigFrames && configFrameCount < 2) {
                    // Shower server sends csd-0/csd-1 right after INFO_OUTPUT_FORMAT_CHANGED.
                    // Cache them so that UI can attach later without losing SPS/PPS.
                    if (configFrameCount == 0) {
                        cachedConfig0 = data
                    } else if (configFrameCount == 1) {
                        cachedConfig1 = data
                    }
                    configFrameCount++
                    if (configFrameCount >= 2) {
                        expectConfigFrames = false
                    }
                }

                handler = binaryHandler
                if (handler == null) {
                    if (earlyBinaryFrames.size >= 120) {
                        earlyBinaryFrames.removeFirst()
                    }
                    earlyBinaryFrames.addLast(data)
                }
            }
            handler?.invoke(data)
        }
    }

    private suspend fun ensureConnected(restartContext: Context? = null): Boolean =
        withContext(Dispatchers.IO) {
            if (binderService?.asBinder()?.isBinderAlive == true) {
                // Binder is alive, but the server-side video sink could have been cleared (e.g. binderDied).
                // Re-set it defensively to avoid "black screen" issues.
                runCatching { binderService?.setVideoSink(videoSink.asBinder()) }
                return@withContext true
            }

            fun clearDeadService() {
                binderService = null
                ShowerBinderRegistry.setService(null)
            }

            val maxAttempts = if (restartContext != null) 2 else 1
            var attempt = 0
            while (attempt < maxAttempts) {
                attempt++
                try {
                    val cachedService = ShowerBinderRegistry.getService()
                    val binder = cachedService?.asBinder()
                    val alive = binder?.isBinderAlive == true
                    Log.d(TAG, "ensureConnected: attempt=$attempt cachedService=$cachedService binder=$binder alive=$alive")
                    if (cachedService != null && alive) {
                        binderService = cachedService
                        binderService?.setVideoSink(videoSink.asBinder())
                        Log.d(TAG, "Connected to Shower Binder service on attempt=$attempt")
                        return@withContext true
                    } else {
                        Log.w(TAG, "No alive Shower Binder cached in ShowerBinderRegistry on attempt=$attempt")
                        clearDeadService()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect to Binder service on attempt=$attempt", e)
                    clearDeadService()
                }

                if (restartContext != null && attempt == 1) {
                    try {
                        val ctx = restartContext.applicationContext
                        Log.d(TAG, "ensureConnected: attempting to restart Shower server after connection failure")
                        val ok = ShowerServerManager.ensureServerStarted(ctx)
                        if (!ok) {
                            Log.e(TAG, "ensureConnected: failed to restart Shower server")
                            break
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "ensureConnected: exception while restarting Shower server", e)
                        break
                    }
                }
            }

            false
        }

    suspend fun requestScreenshot(timeoutMs: Long = 3000L): ByteArray? =
        withContext(Dispatchers.IO) {
            if (!ensureConnected()) return@withContext null
            try {
                withTimeout(timeoutMs) {
                    binderService?.requestScreenshot()
                }
            } catch (e: Exception) {
                Log.e(TAG, "requestScreenshot failed", e)
                null
            }
        }

    suspend fun ensureDisplay(
        context: Context,
        width: Int,
        height: Int,
        dpi: Int,
        bitrateKbps: Int? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!ensureConnected(context)) return@withContext false
        try {
            synchronized(binaryLock) {
                expectConfigFrames = true
                configFrameCount = 0
                cachedConfig0 = null
                cachedConfig1 = null
            }
            // Align size similar to WebSocket version
            val alignedWidth = width and -8
            val alignedHeight = height and -8
            videoWidth = if (alignedWidth > 0) alignedWidth else width
            videoHeight = if (alignedHeight > 0) alignedHeight else height

            binderService?.destroyDisplay() // Ensure clean state
            binderService?.ensureDisplay(videoWidth, videoHeight, dpi, bitrateKbps ?: 0)
            val id = binderService?.getDisplayId() ?: -1
            if (id < 0) {
                virtualDisplayId = null
                Log.e(TAG, "ensureDisplay: server reported invalid displayId=$id")
                return@withContext false
            }
            virtualDisplayId = id
            Log.d(TAG, "ensureDisplay complete, new displayId=$virtualDisplayId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "ensureDisplay failed", e)
            false
        }
    }

    suspend fun launchApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!ensureConnected() || packageName.isBlank()) return@withContext false
        try {
            binderService?.launchApp(packageName)
            true
        } catch (e: Exception) {
            Log.e(TAG, "launchApp failed for $packageName", e)
            false
        }
    }

    suspend fun tap(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        if (!ensureConnected()) return@withContext false
        try {
            binderService?.tap(x.toFloat(), y.toFloat())
            true
        } catch (e: Exception) {
            Log.e(TAG, "tap($x, $y) failed", e)
            false
        }
    }

    suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long = 300L,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!ensureConnected()) return@withContext false
        try {
            binderService?.swipe(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), durationMs)
            true
        } catch (e: Exception) {
            Log.e(TAG, "swipe failed", e)
            false
        }
    }

    suspend fun touchDown(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        if (!ensureConnected()) return@withContext false
        try {
            binderService?.touchDown(x.toFloat(), y.toFloat())
            true
        } catch (e: Exception) {
            Log.e(TAG, "touchDown($x, $y) failed", e)
            false
        }
    }

    suspend fun touchMove(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        if (!ensureConnected()) return@withContext false
        try {
            binderService?.touchMove(x.toFloat(), y.toFloat())
            true
        } catch (e: Exception) {
            Log.e(TAG, "touchMove($x, $y) failed", e)
            false
        }
    }

    suspend fun touchUp(x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        if (!ensureConnected()) return@withContext false
        try {
            binderService?.touchUp(x.toFloat(), y.toFloat())
            true
        } catch (e: Exception) {
            Log.e(TAG, "touchUp($x, $y) failed", e)
            false
        }
    }

    fun shutdown() {
        val service = binderService
        binderService = null
        virtualDisplayId = null
        videoWidth = 0
        videoHeight = 0
        synchronized(binaryLock) {
            binaryHandler = null
            earlyBinaryFrames.clear()
        }
        if (service?.asBinder()?.isBinderAlive == true) {
            try {
                service.destroyDisplay()
                service.setVideoSink(null)
            } catch (e: Exception) {
                Log.e(TAG, "shutdown: destroyDisplay failed", e)
            }
        }
    }

    suspend fun key(keyCode: Int): Boolean = withContext(Dispatchers.IO) {
        if (!ensureConnected()) return@withContext false
        try {
            binderService?.injectKey(keyCode)
            true
        } catch (e: Exception) {
            Log.e(TAG, "key($keyCode) failed", e)
            false
        }
    }
}
