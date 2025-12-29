package com.example.operit

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
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.operit.accessibility.AccessibilityStatus
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import rikka.shizuku.Shizuku

class SettingsPermissionsFragment : Fragment() {
    private lateinit var chipAccessibility: Chip
    private lateinit var chipOverlay: Chip
    private lateinit var chipBattery: Chip
    private lateinit var chipNotifications: Chip
    private lateinit var chipStorage: Chip
    private lateinit var chipLocation: Chip
    private lateinit var chipShizuku: Chip

    private lateinit var btnAccessibility: MaterialButton
    private lateinit var btnOverlay: MaterialButton
    private lateinit var btnBattery: MaterialButton
    private lateinit var btnNotifications: MaterialButton
    private lateinit var btnStorage: MaterialButton
    private lateinit var btnLocation: MaterialButton
    private lateinit var btnShizukuOpen: MaterialButton
    private lateinit var btnShizukuRequest: MaterialButton

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

        chipAccessibility = view.findViewById(R.id.chipAccessibilityStatus)
        chipOverlay = view.findViewById(R.id.chipOverlayStatus)
        chipBattery = view.findViewById(R.id.chipBatteryStatus)
        chipShizuku = view.findViewById(R.id.chipShizukuStatus)
        chipNotifications = view.findViewById(R.id.chipNotificationsStatus)
        chipStorage = view.findViewById(R.id.chipStorageStatus)
        chipLocation = view.findViewById(R.id.chipLocationStatus)

        btnAccessibility = view.findViewById(R.id.btnAccessibility)
        btnOverlay = view.findViewById(R.id.btnOverlay)
        btnBattery = view.findViewById(R.id.btnBattery)
        btnShizukuOpen = view.findViewById(R.id.btnShizukuOpen)
        btnShizukuRequest = view.findViewById(R.id.btnShizukuRequest)
        btnNotifications = view.findViewById(R.id.btnNotifications)
        btnStorage = view.findViewById(R.id.btnStorage)
        btnLocation = view.findViewById(R.id.btnLocation)

        btnAccessibility.setOnClickListener { openAccessibilitySettings() }
        btnOverlay.setOnClickListener { openOverlaySettings() }
        btnBattery.setOnClickListener { requestIgnoreBatteryOptimizations() }
        btnStorage.setOnClickListener { requestStoragePermission() }
        btnLocation.setOnClickListener { requestLocationPermission() }
        btnNotifications.setOnClickListener { requestNotificationPermissionOrOpenSettings() }
        btnShizukuOpen.setOnClickListener { openShizukuAppOrSite() }
        btnShizukuRequest.setOnClickListener { requestShizukuPermission() }

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

    private fun refreshUi() {
        val ctx = context ?: return

        val accessibilityEnabled = AccessibilityStatus.isServiceEnabled(ctx)
        chipAccessibility.text = if (accessibilityEnabled) "已开启" else "未开启"
        btnAccessibility.text = if (accessibilityEnabled) "已开启" else "去开启"

        val overlayGranted = hasOverlayPermission(ctx)
        chipOverlay.text = if (overlayGranted) "已授权" else "未授权"
        btnOverlay.text = if (overlayGranted) "已授权" else "去设置"

        val batteryGranted = hasBatteryOptimizationExemption(ctx)
        chipBattery.text = if (batteryGranted) "已开启" else "未开启"
        btnBattery.text = if (batteryGranted) "已开启" else "去设置"

        refreshShizukuUi()

        val notificationsGranted = hasNotificationPermission(ctx)
        chipNotifications.text = if (notificationsGranted) "已授权" else "未授权"
        btnNotifications.text = if (notificationsGranted) "已授权" else "去授权"

        val storageGranted = hasStoragePermission(ctx)
        chipStorage.text = if (storageGranted) "已授权" else "未授权"
        btnStorage.text = if (storageGranted) "已授权" else "去授权"

        val locationGranted = hasLocationPermission(ctx)
        chipLocation.text = if (locationGranted) "已授权" else "未授权"
        btnLocation.text = if (locationGranted) "已授权" else "去授权"
    }

    private fun refreshShizukuUi() {
        val ctx = context ?: return
        val running =
            runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) {
            chipShizuku.text = "未启动"
            btnShizukuRequest.isEnabled = false
            return
        }

        val granted =
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        chipShizuku.text = if (granted) "已授权" else "未授权"
        btnShizukuRequest.isEnabled = !granted
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

    private fun requestStoragePermission() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) return
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${ctx.packageName}")
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
            return
        }

        requestPermissions(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ),
            REQ_STORAGE,
        )
    }

    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            REQ_LOCATION,
        )
    }

    private fun requestNotificationPermissionOrOpenSettings() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
            return
        }
        // Android 13 以下默认允许，但部分 ROM 可能关闭：直接引导到应用通知页
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            }
            startActivity(intent)
        } catch (_: Exception) {
            openAppDetails()
        }
    }

    private fun openAppDetails() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${requireContext().packageName}")
        }
        startActivity(intent)
    }

    private fun hasStoragePermission(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val read =
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
            val write =
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
            read && write
        }
    }

    private fun hasOverlayPermission(ctx: Context): Boolean {
        return Settings.canDrawOverlays(ctx)
    }

    private fun hasBatteryOptimizationExemption(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
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

    private fun hasNotificationPermission(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshUi()
    }

    private companion object {
        private const val REQ_STORAGE = 1001
        private const val REQ_LOCATION = 1002
        private const val REQ_NOTIFICATIONS = 1003
        private const val REQ_SHIZUKU = 2001
    }
}
