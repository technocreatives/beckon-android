package com.technocreatives.example.bond

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import timber.log.Timber


class AutoStart : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("Boot onReceive")
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val startIntent = Intent(context, BondService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(startIntent)
                } else {
                    context.startService(startIntent)
                }
            }
        }
    }
}