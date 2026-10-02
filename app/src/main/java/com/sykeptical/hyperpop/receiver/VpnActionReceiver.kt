package com.sykeptical.hyperpop.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sykeptical.hyperpop.service.vpn.VpnControlRegistry

class VpnActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISCONNECT) return
        intent.getStringExtra(EXTRA_LOGICAL_ID)?.let(VpnControlRegistry::requestDisconnect)
    }

    companion object {
        const val ACTION_DISCONNECT = "com.sykeptical.hyperpop.action.DISCONNECT_VPN"
        const val EXTRA_LOGICAL_ID = "vpn_logical_id"
    }
}

