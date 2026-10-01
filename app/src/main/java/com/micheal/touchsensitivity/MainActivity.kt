package com.micheal.touchsensitivity

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private lateinit var status: TextView
    private lateinit var sensitivityValue: TextView
    private lateinit var sizeValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        sensitivityValue = findViewById(R.id.sensitivityValue)
        sizeValue = findViewById(R.id.sizeValue)

        findViewById<Button>(R.id.permissionButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.accessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.enableButton).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Allow overlay permission first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!TouchAccessibilityService.isEnabled(this)) {
                Toast.makeText(this, "Enable the Accessibility Service first.", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }
            startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START))
        }
        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }

        val sensitivity = findViewById<SeekBar>(R.id.sensitivity)
        val size = findViewById<SeekBar>(R.id.size)

        val savedSensitivity = prefs.getFloat("sensitivity", 1f)
        val savedSize = prefs.getInt("size", 72)
        sensitivity.progress = ((savedSensitivity - 0.25f) / 0.01f).toInt().coerceIn(0, 275)
        size.progress = (savedSize - 40).coerceIn(0, 120)

        sensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val value = 0.25f + p * 0.01f
                prefs.edit().putFloat("sensitivity", value).apply()
                sensitivityValue.text = String.format("%.2f×", value)
                OverlayService.instance?.refresh()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        size.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val value = 40 + p
                prefs.edit().putInt("size", value).apply()
                sizeValue.text = "$value px"
                OverlayService.instance?.refresh()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val s = prefs.getFloat("sensitivity", 1f)
        val z = prefs.getInt("size", 72)
        sensitivityValue.text = String.format("%.2f×", s)
        sizeValue.text = "$z px"
        status.text = "Overlay permission: ${if (Settings.canDrawOverlays(this)) "ON" else "OFF"}\nAccessibility service: ${if (TouchAccessibilityService.isEnabled(this)) "ON" else "OFF"}"
    }
}
