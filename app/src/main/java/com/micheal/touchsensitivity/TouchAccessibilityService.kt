package com.micheal.touchsensitivity

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityServiceInfo

class TouchAccessibilityService : AccessibilityService() {
    companion object {
        var instance: TouchAccessibilityService? = null
        fun isEnabled(context: android.content.Context): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { instance = null }

    fun dispatchScaledDrag(x1: Float, y1: Float, dx: Float, dy: Float, duration: Long = 350L) {
        val sensitivity = getSharedPreferences("settings", MODE_PRIVATE).getFloat("sensitivity", 1f)
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x1 + dx * sensitivity, y1 + dy * sensitivity)
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
