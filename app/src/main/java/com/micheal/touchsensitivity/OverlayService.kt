package com.micheal.touchsensitivity

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Path
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import kotlin.math.roundToInt

class OverlayService : Service() {
    companion object {
        const val ACTION_START = "START"
        var instance: OverlayService? = null
    }

    private lateinit var wm: WindowManager
    private lateinit var bubble: TextView
    private lateinit var params: WindowManager.LayoutParams
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moveMode = false
    private var longPressTriggered = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForeground(42, notification())
        createBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun notification(): Notification {
        val channelId = "overlay"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Touch overlay", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, channelId)
            .setContentTitle("Touch Sensitivity Overlay")
            .setContentText("Floating touch control is active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    private fun createBubble() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        bubble = TextView(this).apply {
            text = "↕"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(205, 20, 180, 120))
            elevation = 20f
        }

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val size = prefs.getInt("size", 72)

        params = WindowManager.LayoutParams(
            size, size,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", 300)
            y = prefs.getInt("y", 500)
        }

        bubble.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    lastX = e.rawX
                    lastY = e.rawY
                    longPressTriggered = false
                    handler.postDelayed({
                        longPressTriggered = true
                        moveMode = !moveMode
                        bubble.text = if (moveMode) "✥" else "↕"
                        Toast.makeText(
                            this,
                            if (moveMode) "Move mode ON — drag the circle"
                            else "Sensitivity mode ON — drag to control the underlying app",
                            Toast.LENGTH_SHORT
                        ).show()
                    }, 600)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - lastX
                    val dy = e.rawY - lastY

                    if (moveMode) {
                        params.x = (params.x + dx).roundToInt()
                        params.y = (params.y + dy).roundToInt()
                        wm.updateViewLayout(bubble, params)
                        savePosition()
                    } else if (!longPressTriggered) {
                        TouchAccessibilityService.instance?.dispatchScaledDrag(
                            lastX, lastY, dx, dy, 80L
                        )
                    }

                    lastX = e.rawX
                    lastY = e.rawY
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacksAndMessages(null)
                    true
                }

                else -> true
            }
        }

        wm.addView(bubble, params)
    }

    fun refresh() {
        if (!::bubble.isInitialized) return
        val newSize = getSharedPreferences("settings", MODE_PRIVATE).getInt("size", 72)
        params.width = newSize
        params.height = newSize
        wm.updateViewLayout(bubble, params)
    }

    private fun savePosition() {
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putInt("x", params.x)
            .putInt("y", params.y)
            .apply()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::bubble.isInitialized) wm.removeView(bubble)
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
