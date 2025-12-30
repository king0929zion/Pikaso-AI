package com.example.operit.virtualdisplay.shower

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.assistance.shower.IShowerService
import com.ai.assistance.shower.ShowerBinderContainer
import com.ai.assistance.showerclient.ShowerBinderRegistry as CoreRegistry
import com.example.operit.logging.AppLog

class ShowerBinderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()
        if (action != ACTION_SHOWER_BINDER_READY && action != ACTION_SHOWER_BINDER_READY_COMPAT) return

        val container = intent.getParcelableExtra<ShowerBinderContainer>(EXTRA_BINDER_CONTAINER)
        val binder = container?.binder
        val service = binder?.let { IShowerService.Stub.asInterface(it) }
        val alive = service?.asBinder()?.isBinderAlive == true
        AppLog.i(TAG, "onReceive: action=$action service=$service alive=$alive")
        CoreRegistry.setService(service)
    }

    companion object {
        private const val TAG = "ShowerBinderReceiver"

        // server.jar 目前使用 Operit 的 action，这里兼容监听两种
        const val ACTION_SHOWER_BINDER_READY = "com.ai.assistance.operit.action.SHOWER_BINDER_READY"
        const val ACTION_SHOWER_BINDER_READY_COMPAT = "com.example.operit.action.SHOWER_BINDER_READY"
        const val EXTRA_BINDER_CONTAINER = "binder_container"
    }
}

