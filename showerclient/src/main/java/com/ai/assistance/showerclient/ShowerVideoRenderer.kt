package com.ai.assistance.showerclient

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Simple H.264 decoder that renders the Shower video stream onto a Surface.
 *
 * This decoder assumes that the first two binary frames received from the Shower server
 * are the codec configuration buffers (csd-0 and csd-1), followed by regular access units.
 */
object ShowerVideoRenderer {

    private const val TAG = "ShowerVideoRenderer"

    private val lock = Any()

    @Volatile
    private var decoder: MediaCodec? = null

    @Volatile
    private var surface: Surface? = null

    @Volatile
    private var csd0: ByteArray? = null

    @Volatile
    private var csd1: ByteArray? = null

    private val pendingFrames = mutableListOf<ByteArray>()

    @Volatile
    private var width: Int = 0

    @Volatile
    private var height: Int = 0

    @Volatile
    private var warnedNoSurface: Boolean = false

    fun attach(surface: Surface, videoWidth: Int, videoHeight: Int) {
        synchronized(lock) {
            this.surface = surface
            this.width = videoWidth
            this.height = videoHeight
            warnedNoSurface = false
            // Decoder will be (re)initialized lazily when the first csd buffers arrive.
            // 注意：不要在这里清空 csd0/csd1，否则当 Surface 重新创建但服务器
            // 不再发送 SPS/PPS 时，解码器将永远无法重新初始化，导致黑屏。
            // 仅在此处释放旧 decoder，并清空待处理帧。
            releaseDecoderLocked()
            pendingFrames.clear()
        }
    }

    fun detach() {
        synchronized(lock) {
            releaseDecoderLocked()
            surface = null
            // 不要清空 csd0/csd1，让后续重新 attach 时仍可用之前的 SPS/PPS，避免黑屏
            pendingFrames.clear()
            warnedNoSurface = false
        }
    }

    private fun releaseDecoderLocked() {
        val dec = decoder
        decoder = null
        if (dec != null) {
            try {
                dec.stop()
            } catch (_: Exception) {
            }
            try {
                dec.release()
            } catch (_: Exception) {
            }
        }
    }

    /** Called from the WebSocket binary handler for each H.264 packet. */
    fun onFrame(data: ByteArray) {
        synchronized(lock) {
            if (surface == null || width <= 0 || height <= 0) {
                if (!warnedNoSurface) {
                    Log.w(TAG, "onFrame: no surface or invalid size (surface=$surface, width=$width, height=$height); dropping frames until attached")
                    warnedNoSurface = true
                }
                return
            }

            val packet = maybeAvccToAnnexb(data)

            // If the decoder is not yet initialized, we need to find the SPS and PPS packets (csd-0, csd-1)
            // from the stream, just like the robust Python client does.
            if (decoder == null) {
                val nalType = findNalUnitType(packet)
                if (nalType == 7) { // SPS
                    if (csd0 == null) {
                        csd0 = packet
                        Log.d(TAG, "Captured SPS (csd-0) from stream, size=${packet.size}")
                    }
                } else if (nalType == 8) { // PPS
                    if (csd1 == null) {
                        csd1 = packet
                        Log.d(TAG, "Captured PPS (csd-1) from stream, size=${packet.size}")
                    }
                } else {
                    // Buffer other frames until decoder is ready
                    pendingFrames.add(packet)
                }

                if (csd0 != null && csd1 != null) {
                    initDecoderLocked()
                    // After initializing, process any buffered frames.
                    val framesToProcess = pendingFrames.toList()
                    pendingFrames.clear()
                    Log.d(TAG, "Processing ${framesToProcess.size} pending frames after decoder init")
                    framesToProcess.forEach { frame -> queueFrameToDecoder(frame) }
                }
                return // Wait for more packets to find both SPS and PPS
            }

            // Once decoder is initialized, just queue frames.
            queueFrameToDecoder(packet)
        }
    }

    private fun findNalUnitType(packet: ByteArray): Int {
        var offset = -1
        // Find start code 00 00 01 or 00 00 00 01
        for (i in 0 until packet.size - 3) {
            if (packet[i] == 0.toByte() && packet[i + 1] == 0.toByte()) {
                if (packet[i + 2] == 1.toByte()) {
                    offset = i + 3
                    break
                } else if (i + 3 < packet.size && packet[i + 2] == 0.toByte() && packet[i + 3] == 1.toByte()) {
                    offset = i + 4
                    break
                }
            }
        }

        if (offset != -1 && offset < packet.size) {
            return (packet[offset].toInt() and 0x1F)
        }
        return -1 // Not found or invalid
    }

    private fun queueFrameToDecoder(packet: ByteArray) {
        synchronized(lock) {
            val dec = decoder ?: return
            try {
                val inIndex = dec.dequeueInputBuffer(10000) // Use a small timeout
                if (inIndex >= 0) {
                    val inputBuffer: ByteBuffer? = dec.getInputBuffer(inIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        inputBuffer.put(packet)
                        dec.queueInputBuffer(inIndex, 0, packet.size, System.nanoTime() / 1000, 0)
                    }
                }

                val bufferInfo = BufferInfo()
                var outIndex = dec.dequeueOutputBuffer(bufferInfo, 0)
                while (outIndex >= 0) {
                    dec.releaseOutputBuffer(outIndex, true) // Render to surface
                    outIndex = dec.dequeueOutputBuffer(bufferInfo, 0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Decoder error on frame", e)
                // 仅重置 decoder 与待处理帧，保留已捕获的 SPS/PPS（csd0/csd1），
                // 这样后续新帧到来时可以重新初始化解码器，避免进入永久黑屏状态。
                releaseDecoderLocked()
                pendingFrames.clear()
            }
        }
    }

    /**
     * Convert a length-prefixed (AVCC) packet to Annex-B if necessary.
     *
     * If the packet already starts with 0x00000001 or 0x000001??, it is returned unchanged.
     */
    private fun maybeAvccToAnnexb(packet: ByteArray): ByteArray {
        if (packet.size >= 4) {
            val b0 = packet[0].toInt() and 0xFF
            val b1 = packet[1].toInt() and 0xFF
            val b2 = packet[2].toInt() and 0xFF
            val b3 = packet[3].toInt() and 0xFF
            // 0x00000001 or 0x000001?? pattern
            if (b0 == 0 && b1 == 0 && ((b2 == 0 && b3 == 1) || b2 == 1)) {
                return packet
            }
        }

        val out = ByteArrayOutputStream()
        var i = 0
        val n = packet.size
        while (i + 4 <= n) {
            val nalLen =
                ((packet[i].toInt() and 0xFF) shl 24) or
                    ((packet[i + 1].toInt() and 0xFF) shl 16) or
                    ((packet[i + 2].toInt() and 0xFF) shl 8) or
                    (packet[i + 3].toInt() and 0xFF)
            i += 4
            if (nalLen <= 0 || i + nalLen > n) {
                // Not a valid AVCC packet, return original
                return packet
            }
            out.write(byteArrayOf(0, 0, 0, 1))
            out.write(packet, i, nalLen)
            i += nalLen
        }

        val result = out.toByteArray()
        return if (result.isNotEmpty()) result else packet
    }

    /**
     * Capture the current video frame rendered on the decoder surface as a PNG.
     *
     * This uses PixelCopy on the decoder output surface so that screenshots are
     * always consistent with the live video stream shown in the overlay.
     */
    suspend fun captureCurrentFramePng(): ByteArray? {
        val s: Surface
        val w: Int
        val h: Int
        synchronized(lock) {
            val localSurface = surface
            if (localSurface == null || width <= 0 || height <= 0) {
                Log.w(TAG, "captureCurrentFramePng: no surface or invalid size")
                return null
            }
            s = localSurface
            w = width
            h = height
        }

        if (Build.VERSION.SDK_INT < 26) {
            Log.w(TAG, "captureCurrentFramePng: PixelCopy requires API 26")
            return null
        }

        return withContext(Dispatchers.Main) {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            suspendCancellableCoroutine { cont ->
                val handler = Handler(Looper.getMainLooper())
                try {
                    // 兼容 minSdk < 26：避免直接引用 PixelCopy 导致老版本类验证失败。
                    val pixelCopyClass = Class.forName("android.view.PixelCopy")
                    val successCode = pixelCopyClass.getField("SUCCESS").getInt(null)
                    val listenerClass = Class.forName("android.view.PixelCopy\$OnPixelCopyFinishedListener")
                    val requestMethod =
                        pixelCopyClass.getMethod(
                            "request",
                            Surface::class.java,
                            Bitmap::class.java,
                            listenerClass,
                            Handler::class.java,
                        )

                    val listener =
                        Proxy.newProxyInstance(
                            listenerClass.classLoader,
                            arrayOf(listenerClass),
                        ) { _, method, args ->
                            if (method.name == "onPixelCopyFinished") {
                                val result = (args?.getOrNull(0) as? Int) ?: -1
                                if (result == successCode) {
                                    try {
                                        val baos = ByteArrayOutputStream()
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                                        cont.resume(baos.toByteArray())
                                    } catch (e: Exception) {
                                        Log.e(TAG, "captureCurrentFramePng: compression error", e)
                                        cont.resume(null)
                                    } finally {
                                        bitmap.recycle()
                                    }
                                } else {
                                    Log.w(TAG, "captureCurrentFramePng: PixelCopy failed with code=$result")
                                    bitmap.recycle()
                                    cont.resume(null)
                                }
                            }
                            null
                        }

                    requestMethod.invoke(null, s, bitmap, listener, handler)
                } catch (e: Exception) {
                    Log.e(TAG, "captureCurrentFramePng: reflection PixelCopy failed", e)
                    runCatching { bitmap.recycle() }
                    cont.resume(null)
                }
            }
        }
    }

    private fun initDecoderLocked() {
        val s = surface ?: return
        val localCsd0 = csd0 ?: return
        val localCsd1 = csd1 ?: return
        if (width <= 0 || height <= 0) return

        try {
            // Mirror Python client's behavior: ensure csd-0 / csd-1 are in Annex-B form
            // in case the encoder produced AVCC-style length-prefixed buffers.
            val csd0Annexb = maybeAvccToAnnexb(localCsd0)
            val csd1Annexb = maybeAvccToAnnexb(localCsd1)

            val format = MediaFormat.createVideoFormat("video/avc", width, height)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0Annexb))
            format.setByteBuffer("csd-1", ByteBuffer.wrap(csd1Annexb))

            val dec = MediaCodec.createDecoderByType("video/avc")
            dec.configure(format, s, null, 0)
            dec.start()
            decoder = dec
            Log.d(TAG, "MediaCodec decoder initialized for ${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init decoder", e)
            releaseDecoderLocked()
        }
    }
}
