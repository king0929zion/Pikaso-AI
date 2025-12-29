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
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class SettingsPermissionsFragment : Fragment() {
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvStorageStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var tvLocationStatus: TextView
    private lateinit var tvNotificationsStatus: TextView
    private lateinit var tvShizukuStatus: TextView

    private lateinit var btnAccessibility: MaterialButton
    private lateinit var btnStorage: MaterialButton
    private lateinit var btnOverlay: MaterialButton
    private lateinit var btnBattery: MaterialButton
    private lateinit var btnLocation: MaterialButton
    private lateinit var btnNotifications: MaterialButton
    private lateinit var btnShizuku: MaterialButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_settings_permissions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        tvAccessibilityStatus = view.findViewById(R.id.tvAccessibilityStatus)
        tvStorageStatus = view.findViewById(R.id.tvStorageStatus)
        tvOverlayStatus = view.findViewById(R.id.tvOverlayStatus)
        tvBatteryStatus = view.findViewById(R.id.tvBatteryStatus)
        tvLocationStatus = view.findViewById(R.id.tvLocationStatus)
        tvNotificationsStatus = view.findViewById(R.id.tvNotificationsStatus)
        tvShizukuStatus = view.findViewById(R.id.tvShizukuStatus)

        btnAccessibility = view.findViewById(R.id.btnAccessibility)
        btnStorage = view.findViewById(R.id.btnStorage)
        btnOverlay = view.findViewById(R.id.btnOverlay)
        btnBattery = view.findViewById(R.id.btnBattery)
        btnLocation = view.findViewById(R.id.btnLocation)
        btnNotifications = view.findViewById(R.id.btnNotifications)
        btnShizuku = view.findViewById(R.id.btnShizuku)

        btnAccessibility.setOnClickListener { openAccessibilitySettings() }
        btnOverlay.setOnClickListener { openOverlaySettings() }
        btnBattery.setOnClickListener { requestIgnoreBatteryOptimizations() }

        btnStorage.setOnClickListener { requestStoragePermission() }
        btnLocation.setOnClickListener { requestLocationPermission() }
        btnNotifications.setOnClickListener { requestNotificationPermissionOrOpenSettings() }

        btnShizuku.setOnClickListener { openUrl("https://shizuku.rikka.app/") }

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        val ctx = context ?: return

        val accessibilityEnabled = isAnyAccessibilityEnabledForThisApp(ctx)
        tvAccessibilityStatus.text = if (accessibilityEnabled) "已开启" else "未开启"

        val storageGranted = hasStoragePermission(ctx)
        tvStorageStatus.text = if (storageGranted) "已授权" else "未授权"
        btnStorage.text = if (storageGranted) "已授权" else "去授权"

        val overlayGranted = hasOverlayPermission(ctx)
        tvOverlayStatus.text = if (overlayGranted) "已授权" else "未授权"

        val batteryGranted = hasBatteryOptimizationExemption(ctx)
        tvBatteryStatus.text = if (batteryGranted) "已开启" else "未开启"

        val locationGranted = hasLocationPermission(ctx)
        tvLocationStatus.text = if (locationGranted) "已授权" else "未授权"
        btnLocation.text = if (locationGranted) "已授权" else "去授权"

        val notificationsGranted = hasNotificationPermission(ctx)
        tvNotificationsStatus.text = if (notificationsGranted) "已授权" else "未授权"
        btnNotifications.text = if (notificationsGranted) "已授权" else "去授权"

        tvShizukuStatus.text = "未集成"
    }

    private fun requestStoragePermission() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            openManageAllFilesAccess(ctx)
            return
        }

        val permissions = mutableListOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        requestPermissions(permissions.toTypedArray(), REQ_STORAGE)
    }

    private fun requestLocationPermission() {
        val permissions =
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        requestPermissions(permissions, REQ_LOCATION)
    }

    private fun requestNotificationPermissionOrOpenSettings() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        } else {
            openAppNotificationSettings(ctx)
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法打开无障碍设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openOverlaySettings() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法打开悬浮窗设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        try {
            val intent =
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openManageAllFilesAccess(ctx: Context) {
        try {
            val intent =
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                }
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "无法打开文件访问权限设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openAppNotificationSettings(ctx: Context) {
        try {
            val intent =
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法打开通知设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法打开链接：$url", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasStoragePermission(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val readGranted =
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
            val writeGranted =
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
            readGranted || writeGranted
        }
    }

    private fun hasOverlayPermission(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(ctx)
        } else {
            true
        }
    }

    private fun hasBatteryOptimizationExemption(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(ctx.packageName)
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

    private fun hasNotificationPermission(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun isAnyAccessibilityEnabledForThisApp(ctx: Context): Boolean {
        return try {
            val enabled =
                Settings.Secure.getString(
                    ctx.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ).orEmpty()
            enabled.contains(ctx.packageName, ignoreCase = true)
        } catch (_: Exception) {
            false
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
    }
}
