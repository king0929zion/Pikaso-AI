package com.example.operit.markdown

import android.content.Context
import android.graphics.Color
import android.text.method.LinkMovementMethod
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.linkify.LinkifyPlugin

object MarkdownRenderer {

    @Volatile
    private var cached: Markwon? = null

    fun render(textView: TextView, markdown: String) {
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.linksClickable = true
        textView.highlightColor = Color.TRANSPARENT
        textView.setLinkTextColor(MaterialColors.getColor(textView, com.google.android.material.R.attr.colorPrimary))
        val markwon = get(textView.context.applicationContext)
        markwon.setMarkdown(textView, markdown)
    }

    private fun get(context: Context): Markwon {
        val existing = cached
        if (existing != null) return existing

        synchronized(this) {
            val again = cached
            if (again != null) return again

            val codeBg = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceVariant, Color.LTGRAY)
            val quoteColor =
                MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, Color.GRAY)

            val created =
                Markwon.builder(context)
                    .usePlugin(CorePlugin.create())
                    .usePlugin(LinkifyPlugin.create())
                    .usePlugin(
                        object : AbstractMarkwonPlugin() {
                            override fun configureTheme(builder: MarkwonTheme.Builder) {
                                builder
                                    .isLinkUnderlined(true)
                                    .codeBackgroundColor(codeBg)
                                    .blockQuoteColor(quoteColor)
                            }
                        },
                    )
                    .build()

            cached = created
            return created
        }
    }
}
