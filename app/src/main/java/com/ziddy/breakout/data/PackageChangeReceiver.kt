package com.ziddy.breakout.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED)) return
        val packageName = intent.data?.schemeSpecificPart ?: return
        val pendingResult = goAsync()
        Thread {
            try {
                PackageIntegrityMonitor.observePackageChange(context.applicationContext, packageName)
            } catch (error: Exception) {
                Log.e(TAG, "Could not update package integrity record for $packageName", error)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private companion object {
        const val TAG = "PackageChangeReceiver"
    }
}
