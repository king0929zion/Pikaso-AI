package com.example.operit

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.example.operit.logging.AppLog

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private var currentFragmentTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AppLog.init(applicationContext)

        drawerLayout = findViewById(R.id.drawerLayout)

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

        // Sidebar interactions
        setupSidebar()

        // Initial fragment
        if (savedInstanceState == null) {
            loadFragment(ChatFragment(), "chat", false)
            updateSidebarActiveState(R.id.navChat)
        }
    }

    private fun setupSidebar() {
        // Nav Items
        val navItems = mapOf(
            R.id.navChat to ChatFragment(),
            R.id.navTools to ToolsFragment(),
            R.id.navScripts to ScriptsFragment(),
            R.id.navLogs to LogsFragment(),
            R.id.navHelp to HelpFragment(),
            R.id.navSettings to SettingsFragment()
        )

        navItems.forEach { (id, fragment) ->
            findViewById<View>(id).setOnClickListener {
                if (currentFragmentTag != id.toString()) {
                    loadFragment(fragment, id.toString(), true)
                    updateSidebarActiveState(id)
                }
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        findViewById<View>(R.id.btnNewChat).setOnClickListener {
            // Reset chat logic
            loadFragment(ChatFragment(), "chat", true)
            updateSidebarActiveState(R.id.navChat)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Setup History List
        val historyList = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.historyList)
        historyList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        historyList.adapter = HistoryAdapter()
    }

    private fun updateSidebarActiveState(activeId: Int) {
        val ids = listOf(R.id.navChat, R.id.navTools, R.id.navScripts, R.id.navLogs, R.id.navHelp, R.id.navSettings)
        ids.forEach { id ->
            val view = findViewById<TextView>(id)
            if (id == activeId) {
                // Apply Active Style - pill shape
                view.setBackgroundResource(R.drawable.bg_nav_item_active)
                view.setTextColor(getColor(R.color.md_theme_light_onSecondaryContainer))
            } else {
                val outValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                view.setBackgroundResource(outValue.resourceId)
                view.setTextColor(getColor(R.color.md_theme_light_onSurface))
            }
        }
    }

    private fun loadFragment(fragment: Fragment, tag: String, animate: Boolean) {
        val existingFragment = supportFragmentManager.findFragmentByTag(tag)

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

        transaction.commit()
        currentFragmentTag = tag
    }
}
