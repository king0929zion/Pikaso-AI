package com.example.operit.settings.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.operit.R
import com.example.operit.accessibility.AccessibilityStatus
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import rikka.shizuku.Shizuku

class SettingsPermissionsFragment : Fragment() {

    private lateinit var switchAccessibility: SwitchMaterial
    private lateinit var switchOverlay: SwitchMaterial
    private lateinit var switchBattery: SwitchMaterial
    private lateinit var switchShizuku: SwitchMaterial
    private lateinit var switchNotifications: SwitchMaterial
    private lateinit var switchLocation: SwitchMaterial
    private lateinit var switchStorageRead: SwitchMaterial
    private lateinit var switchAllFiles: SwitchMaterial

    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var tvShizukuStatus: TextView
    private lateinit var tvNotificationsStatus: TextView
    private lateinit var tvLocationStatus: TextView
    private lateinit var tvStorageReadStatus: TextView
    private lateinit var tvAllFilesStatus: TextView

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == REQ_SHIZUKU) refreshUi()
        }

    private val shizukuBinderListener =
        Shizuku.OnBinderReceivedListener {
            refreshUi()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_settings_permissions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        switchAccessibility = view.findViewById(R.id.switchAccessibility)
        switchOverlay = view.findViewById(R.id.switchOverlay)
        switchBattery = view.findViewById(R.id.switchBattery)
        switchShizuku = view.findViewById(R.id.switchShizuku)
        switchNotifications = view.findViewById(R.id.switchNotifications)
        switchLocation = view.findViewById(R.id.switchLocation)
        switchStorageRead = view.findViewById(R.id.switchStorageRead)
        switchAllFiles = view.findViewById(R.id.switchAllFiles)

        tvAccessibilityStatus = view.findViewById(R.id.tvAccessibilityStatus)
        tvOverlayStatus = view.findViewById(R.id.tvOverlayStatus)
        tvBatteryStatus = view.findViewById(R.id.tvBatteryStatus)
        tvShizukuStatus = view.findViewById(R.id.tvShizukuStatus)
        tvNotificationsStatus = view.findViewById(R.id.tvNotificationsStatus)
        tvLocationStatus = view.findViewById(R.id.tvLocationStatus)
        tvStorageReadStatus = view.findViewById(R.id.tvStorageReadStatus)
        tvAllFilesStatus = view.findViewById(R.id.tvAllFilesStatus)

        // Accessibility
        view.findViewById<View>(R.id.itemAccessibility).setOnClickListener { openAccessibilitySettings() }
        switchAccessibility.setOnClickListener { openAccessibilitySettings() }

        // Overlay
        view.findViewById<View>(R.id.itemOverlay).setOnClickListener { openOverlaySettings() }
        switchOverlay.setOnClickListener { openOverlaySettings() }

        // Battery
        view.findViewById<View>(R.id.itemBattery).setOnClickListener { requestIgnoreBatteryOptimizations() }
        switchBattery.setOnClickListener { requestIgnoreBatteryOptimizations() }

        // Shizuku
        view.findViewById<View>(R.id.itemShizuku).setOnClickListener { handleShizukuClick() }
        switchShizuku.setOnClickListener { handleShizukuClick() }

        // Notifications
        view.findViewById<View>(R.id.itemNotifications).setOnClickListener { requestNotificationsPermission() }
        switchNotifications.setOnClickListener { requestNotificationsPermission() }

        // Location
        view.findViewById<View>(R.id.itemLocation).setOnClickListener { requestLocationPermission() }
        switchLocation.setOnClickListener { requestLocationPermission() }

        // Storage read (images / external)
        view.findViewById<View>(R.id.itemStorageRead).setOnClickListener { requestStorageReadPermission() }
        switchStorageRead.setOnClickListener { requestStorageReadPermission() }

        // All files access (special access)
        view.findViewById<View>(R.id.itemAllFiles).setOnClickListener { openAllFilesAccessSettings() }
        switchAllFiles.setOnClickListener { openAllFilesAccessSettings() }

        Shizuku.addBinderReceivedListener(shizukuBinderListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onDestroyView() {
        runCatching { Shizuku.removeBinderReceivedListener(shizukuBinderListener) }
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
        super.onDestroyView()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshUi()
    }

    private fun refreshUi() {
        val ctx = context ?: return

        // Accessibility
        val accessibilityEnabled = AccessibilityStatus.isServiceEnabled(ctx)
        switchAccessibility.isChecked = accessibilityEnabled
        tvAccessibilityStatus.text = if (accessibilityEnabled) "已开启" else "模拟点击和滑动"

        // Overlay
        val overlayGranted = hasOverlayPermission(ctx)
        switchOverlay.isChecked = overlayGranted
        tvOverlayStatus.text = if (overlayGranted) "已授权" else "显示虚拟屏幕"

        // Battery
        val batteryGranted = hasBatteryOptimizationExemption(ctx)
        switchBattery.isChecked = batteryGranted
        tvBatteryStatus.text = if (batteryGranted) "已忽略优化" else "防止后台服务被杀"

        // Shizuku
        refreshShizukuUi()

        // Notifications
        val notificationsGranted = hasNotificationPermission(ctx)
        switchNotifications.isChecked = notificationsGranted
        switchNotifications.isEnabled = Build.VERSION.SDK_INT >= 33 && !notificationsGranted
        tvNotificationsStatus.text =
            when {
                Build.VERSION.SDK_INT < 33 -> "Android 13 以下无需授权"
                notificationsGranted -> "已授权"
                else -> "未授权（点击授权）"
            }

        // Location
        val locationGranted = hasLocationPermission(ctx)
        switchLocation.isChecked = locationGranted
        switchLocation.isEnabled = !locationGranted
        tvLocationStatus.text = if (locationGranted) "已授权" else "未授权（点击授权）"

        // Storage read
        val storageReadGranted = hasStorageReadPermission(ctx)
        switchStorageRead.isChecked = storageReadGranted
        switchStorageRead.isEnabled = !storageReadGranted
        tvStorageReadStatus.text =
            when {
                storageReadGranted -> "已授权"
                Build.VERSION.SDK_INT >= 33 -> "未授权（读取图片权限）"
                else -> "未授权（读取存储权限）"
            }

        // All files access
        val allFilesGranted = hasAllFilesAccess(ctx)
        switchAllFiles.isChecked = allFilesGranted
        switchAllFiles.isEnabled = !allFilesGranted && Build.VERSION.SDK_INT >= 30
        tvAllFilesStatus.text =
            when {
                Build.VERSION.SDK_INT < 30 -> "Android 11 以下无需授权"
                allFilesGranted -> "已授权"
                else -> "未授权（点击跳转设置）"
            }
    }

    private fun handleShizukuClick() {
        if (!switchShizuku.isChecked) {
            // User wants to turn it ON (request permission)
            requestShizukuPermission()
        } else {
            // User tapped, but permission is already granted. Maybe show version?
            // Or if service not running, try to open app.
             requestShizukuPermission()
        }
    }

    private fun refreshShizukuUi() {
        val running =
            runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) {
            switchShizuku.isChecked = false
            switchShizuku.isEnabled = true // Allow clicking to open app/instructions
            tvShizukuStatus.text = "服务未运行 (点击启动)"
            return
        }

        val granted =
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        switchShizuku.isChecked = granted
        switchShizuku.isEnabled = true
        tvShizukuStatus.text = if (granted) "服务运行正常" else "点击授权"
    }

    private fun openShizukuAppOrSite() {
        val ctx = context ?: return
        val pkg = "moe.shizuku.privileged.api"
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            startActivity(intent)
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
        } catch (e: Exception) {
            Toast.makeText(ctx, "无法打开 Shizuku：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestShizukuPermission() {
        val ctx = context ?: return
        val running =
            runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) {
            Toast.makeText(ctx, "请先启动 Shizuku 服务", Toast.LENGTH_SHORT).show()
            openShizukuAppOrSite()
            return
        }

        val granted =
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        if (granted) {
            Toast.makeText(ctx, "Shizuku 权限已授权", Toast.LENGTH_SHORT).show()
            refreshUi()
            return
        }

        try {
            Shizuku.requestPermission(REQ_SHIZUKU)
        } catch (e: Exception) {
            Toast.makeText(ctx, "请求 Shizuku 权限失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationsPermission() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(ctx, "Android 13 以下无需通知权限", Toast.LENGTH_SHORT).show()
            refreshUi()
            return
        }
        val granted = hasNotificationPermission(ctx)
        if (granted) {
            Toast.makeText(ctx, "通知权限已授权", Toast.LENGTH_SHORT).show()
            refreshUi()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
    }

    private fun requestLocationPermission() {
        val ctx = context ?: return
        val granted = hasLocationPermission(ctx)
        if (granted) {
            Toast.makeText(ctx, "位置权限已授权", Toast.LENGTH_SHORT).show()
            refreshUi()
            return
        }
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            REQ_LOCATION,
        )
    }

    private fun requestStorageReadPermission() {
        val ctx = context ?: return
        val granted = hasStorageReadPermission(ctx)
        if (granted) {
            Toast.makeText(ctx, "读取权限已授权", Toast.LENGTH_SHORT).show()
            refreshUi()
            return
        }
        val perms =
            if (Build.VERSION.SDK_INT >= 33) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        requestPermissions(perms, REQ_STORAGE)
    }

    private fun openAllFilesAccessSettings() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT < 30) {
            Toast.makeText(ctx, "Android 11 以下无需“所有文件访问”权限", Toast.LENGTH_SHORT).show()
            refreshUi()
            return
        }
        try {
            val intent =
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${ctx.packageName}"),
                )
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, "无法打开所有文件访问设置：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法打开无障碍设置：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openOverlaySettings() {
        try {
            val intent =
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}"),
                )
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法打开悬浮窗设置：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        val ctx = context ?: return
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val pkg = ctx.packageName
            if (pm.isIgnoringBatteryOptimizations(pkg)) {
                Toast.makeText(ctx, "已在电池优化白名单", Toast.LENGTH_SHORT).show()
                return
            }
            val intent =
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$pkg")
                }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法打开电池优化设置：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasOverlayPermission(ctx: Context): Boolean {
        return Settings.canDrawOverlays(ctx)
    }

    private fun hasBatteryOptimizationExemption(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    private fun hasNotificationPermission(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun hasLocationPermission(ctx: Context): Boolean {
        val fine =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun hasStorageReadPermission(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasAllFilesAccess(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private companion object {
        private const val REQ_STORAGE = 1001
        private const val REQ_LOCATION = 1002
        private const val REQ_NOTIFICATIONS = 1003
        private const val REQ_SHIZUKU = 2001
    }
}
