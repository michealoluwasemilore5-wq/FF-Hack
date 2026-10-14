package com.venice.ffesp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.WindowManager

class OverlayService : Service() {

    private lateinit var view: EspView

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::view.isInitialized) {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            view = EspView(this)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            wm.addView(view, params)
        }
        startForeground(1, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val chId = "ffesp"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(chId, "FF ESP", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
        return Notification.Builder(this, chId)
            .setContentTitle("FF ESP running")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
