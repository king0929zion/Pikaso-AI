package com.example.operit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ChatFragment : Fragment() {

    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.chatRecyclerView)
        emptyState = view.findViewById(R.id.emptyState)
        val input = view.findViewById<EditText>(R.id.chatInput)
        val btnSend = view.findViewById<FloatingActionButton>(R.id.btnSend)

        adapter = ChatAdapter()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        btnSend.setOnClickListener {
            val text = input.text.toString()
            if (text.isNotBlank()) {
                sendMessage(text)
                input.text.clear()
            }
        }
    }

    private fun sendMessage(text: String) {
        if (emptyState.visibility == View.VISIBLE) {
            emptyState.visibility = View.GONE
        }
        adapter.addMessage(ChatAdapter.Message(text, true))
        recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
        
        // Mock AI response
        view?.postDelayed({
            adapter.addMessage(ChatAdapter.Message("好的，正在为您执行该操作。", false))
            recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
        }, 1000)
    }
}
