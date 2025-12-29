package com.example.operit.virtualdisplay

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.operit.R
import com.google.android.material.button.MaterialButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VirtualScreenFragment : Fragment() {
    private val manager by lazy { VirtualDisplayManager.getInstance(requireContext()) }

    private lateinit var tvStatus: TextView
    private lateinit var tvLastCapture: TextView
    private lateinit var ivPreview: ImageView
    private lateinit var btnCreate: MaterialButton
    private lateinit var btnRelease: MaterialButton
    private lateinit var btnCapture: MaterialButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_virtual_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        tvStatus = view.findViewById(R.id.tvStatus)
        tvLastCapture = view.findViewById(R.id.tvLastCapture)
        ivPreview = view.findViewById(R.id.ivPreview)
        btnCreate = view.findViewById(R.id.btnCreate)
        btnRelease = view.findViewById(R.id.btnRelease)
        btnCapture = view.findViewById(R.id.btnCapture)

        btnCreate.setOnClickListener {
            val id = manager.ensureVirtualDisplay()
            if (id == null) {
                Toast.makeText(requireContext(), "创建虚拟屏幕失败", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "已创建虚拟屏幕：id=$id", Toast.LENGTH_SHORT).show()
            }
            refreshUi()
        }

        btnRelease.setOnClickListener {
            manager.release()
            Toast.makeText(requireContext(), "已释放虚拟屏幕", Toast.LENGTH_SHORT).show()
            refreshUi()
        }

        btnCapture.setOnClickListener {
            captureFrame()
        }

        refreshUi()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 不在这里自动 release：便于后续被 AutoGLM/工具复用
    }

    private fun refreshUi() {
        val id = manager.getDisplayId()
        tvStatus.text = if (id == null) "状态：未创建" else "状态：已创建（displayId=$id）"
        btnRelease.isEnabled = id != null
        btnCapture.isEnabled = id != null
    }

    private fun captureFrame() {
        val ctx = context ?: return
        val id = manager.ensureVirtualDisplay()
        if (id == null) {
            Toast.makeText(ctx, "虚拟屏幕未就绪", Toast.LENGTH_SHORT).show()
            refreshUi()
            return
        }

        val file = File(ctx.cacheDir, "virtual_display_latest.png")
        btnCapture.isEnabled = false
        tvLastCapture.text = "正在截图..."

        Thread {
            val ok = manager.captureLatestFrameToFile(file)
            val bmp = if (ok && file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null

            activity?.runOnUiThread {
                btnCapture.isEnabled = true
                if (!ok || bmp == null) {
                    tvLastCapture.text = "截图失败：暂无帧（可先创建后等待 1-2 秒再试）"
                    Toast.makeText(ctx, "截图失败（暂无帧）", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                ivPreview.setImageBitmap(bmp)
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                tvLastCapture.text = "最新截图：$time\n${file.absolutePath}"
            }
        }.start()
    }
}

