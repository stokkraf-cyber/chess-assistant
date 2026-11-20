package com.example.chessassistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var infoText: TextView

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, CaptureService::class.java).apply {
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("DATA", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Layar mengambang aktif! Buka game catur Anda.", Toast.LENGTH_LONG).show()
            finish()
        } else {
            Toast.makeText(this, "Izin tangkap layar dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            requestScreenCapture()
        } else {
            Toast.makeText(this, "Aktifkan izin tampil di atas aplikasi lain terlebih dahulu", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(56, 56, 56, 56)
            setBackgroundColor(Color.parseColor("#0B1120"))
        }

        val badge = TextView(this).apply {
            text = "OVERLAY + SCREEN CAPTURE"
            setTextColor(Color.parseColor("#34D399"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }

        val title = TextView(this).apply {
            text = "Chess Assistant"
            setTextColor(Color.WHITE)
            textSize = 26f
            gravity = Gravity.CENTER
        }

        infoText = TextView(this).apply {
            text = "Tekan tombol di bawah untuk memunculkan layar mengambang.\nSetelah aktif, buka game catur favorit Anda."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 36)
        }

        val btnStart = Button(this).apply {
            text = "Mulai Layar Mengambang"
            setTextColor(Color.parseColor("#06281E"))
            textSize = 15f
            setPadding(36, 28, 36, 28)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10B981"))
                cornerRadius = 28f
            }
            setOnClickListener { ensurePermissionsAndStart() }
        }

        val btnStop = Button(this).apply {
            text = "Matikan Overlay"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 14f
            setPadding(36, 24, 36, 24)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 24 }
            layoutParams = lp
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 28f
            }
            setOnClickListener {
                stopService(Intent(this@MainActivity, CaptureService::class.java))
                Toast.makeText(this@MainActivity, "Layar mengambang dimatikan", Toast.LENGTH_SHORT).show()
            }
        }

        root.addView(badge)
        root.addView(title)
        root.addView(infoText)
        root.addView(btnStart)
        root.addView(btnStop)
        setContentView(root)
    }

    private fun ensurePermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            requestScreenCapture()
        }
    }

    private fun requestScreenCapture() {
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
