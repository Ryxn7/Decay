package com.decay.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class DecayApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Decay detection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while Decay's camera is actively watching for blinks."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "decay_detection"
    }
}
