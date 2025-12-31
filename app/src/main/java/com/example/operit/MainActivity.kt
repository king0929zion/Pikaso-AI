package com.example.operit

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import com.example.operit.ai.AiPreferences
import com.example.operit.chat.ChatStore

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var header: View
    private lateinit var tvModelName: TextView
    private var currentFragmentTag: String? = null
    private val chatStore by lazy { ChatStore.get(this) }
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        header = findViewById(R.id.header)
        tvModelName = findViewById(R.id.tvModelName)

        supportFragmentManager.addOnBackStackChangedListener {
            val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (current != null) updateHeaderVisibility(current)
        }

        // Handle Back Press
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    if (supportFragmentManager.backStackEntryCount > 0) {
                        supportFragmentManager.popBackStack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        // Header interactions
        findViewById<View>(R.id.btnMenu).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        findViewById<View>(R.id.btnHeaderAction).setOnClickListener {
            openNewChat()
        }
        findViewById<View>(R.id.modelSelector).setOnClickListener {
            loadFragment(SettingsAiFragment(), "settings_ai", true, addToBackStack = true)
            updateSidebarActiveState(R.id.navSettings)
        }

        // Sidebar interactions
        setupSidebar()

        // Initial fragment
        if (savedInstanceState == null) {
            openNewChat(animate = false)
            updateSidebarActiveState(R.id.navChat)
        }
    }

    private fun setupSidebar() {
        // Nav Items
        val navItems = mapOf(
            R.id.navChat to ChatFragment(),
            R.id.navTools to ToolsFragment(),
            R.id.navScripts to ScriptsFragment(),
            R.id.navSettings to SettingsFragment()
        )

        navItems.forEach { (id, fragment) ->
            findViewById<View>(id).setOnClickListener {
                if (currentFragmentTag != id.toString()) {
                    val addToBackStack = id != R.id.navChat
                    loadFragment(fragment, id.toString(), true, addToBackStack = addToBackStack)
                    updateSidebarActiveState(id)
                }
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        findViewById<View>(R.id.btnNewChat).setOnClickListener {
            openNewChat()
        }

        // Setup History List
        val historyList = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.historyList)
        historyList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        historyAdapter =
            HistoryAdapter(
                items = chatStore.listSessions(),
                formatTime = { ts -> chatStore.formatTime(ts) },
                onClick = { meta ->
                    openChat(meta.id)
                },
            )
        historyList.adapter = historyAdapter
    }

    private fun updateSidebarActiveState(activeId: Int) {
        val ids = listOf(R.id.navChat, R.id.navTools, R.id.navScripts, R.id.navSettings)
        ids.forEach { id ->
            val view = findViewById<TextView>(id)
            if (id == activeId) {
                // Apply Active Style - pill shape
                view.setBackgroundResource(R.drawable.bg_nav_item_active)
                view.setTextColor(MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSecondaryContainer))
            } else {
                val outValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                view.setBackgroundResource(outValue.resourceId)
                view.setTextColor(MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface))
            }
        }
    }

    private fun loadFragment(fragment: Fragment, tag: String, animate: Boolean, addToBackStack: Boolean = false) {
        val existingFragment =
            if (tag.startsWith("chat:")) {
                null
            } else {
                supportFragmentManager.findFragmentByTag(tag)
            }

        val transaction = supportFragmentManager.beginTransaction()

        // Set custom animation
        if (animate) {
            transaction.setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        }

        if (existingFragment != null) {
            // If fragment already exists, show it
            transaction.replace(R.id.fragmentContainer, existingFragment, tag)
        } else {
            transaction.replace(R.id.fragmentContainer, fragment, tag)
        }

        if (addToBackStack) {
            transaction.addToBackStack(tag)
        }
        transaction.commit()
        currentFragmentTag = tag

        updateHeaderVisibility(existingFragment ?: fragment)
    }

    private fun updateHeaderVisibility(fragment: Fragment) {
        // 设置页及其子页面不需要顶部“模型选择/新对话”栏
        val name = fragment.javaClass.simpleName
        val show = !name.startsWith("Settings")
        header.isVisible = show
        drawerLayout.setDrawerLockMode(
            if (name.startsWith("Settings")) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED,
        )
        if (show) updateHeaderModelName()
    }

    private fun updateHeaderModelName() {
        val settings = AiPreferences.get(this).load()
        val model = settings.model.trim()
        tvModelName.text = if (model.isNotBlank()) model else "Model"
    }

    fun refreshHeaderModelName() {
        updateHeaderModelName()
    }

    fun refreshHistory() {
        if (!::historyAdapter.isInitialized) return
        historyAdapter.submitList(chatStore.listSessions())
    }

    private fun openNewChat(animate: Boolean = true) {
        val session = chatStore.createSession()
        openChat(session.id, animate)
    }

    private fun openChat(sessionId: String, animate: Boolean = true) {
        val fragment = ChatFragment().apply {
            arguments = Bundle().apply { putString(ChatFragment.ARG_SESSION_ID, sessionId) }
        }
        loadFragment(fragment, "chat:$sessionId", animate)
        updateSidebarActiveState(R.id.navChat)
        drawerLayout.closeDrawer(GravityCompat.START)
        refreshHistory()
    }
}
