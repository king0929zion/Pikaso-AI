package com.example.operit.virtualdisplay

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.widget.TextView
import com.example.operit.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VirtualScreenPresentation(
    outerContext: Context,
    display: Display,
) : Presentation(outerContext, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.presentation_virtual_screen)

        val tv = findViewById<TextView>(R.id.tvPresentation)
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        tv.text = "Pikaso 虚拟屏幕（测试渲染）\n$time"
    }
}
