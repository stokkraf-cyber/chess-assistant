package com.example.chessassistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class CaptureService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var arrowView: ArrowOverlayView
    private lateinit var hudView: TextView
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private var stockfishProcess: Process? = null
    private var sfReader: BufferedReader? = null
    private var sfWriter: OutputStreamWriter? = null

    private val sampleMoves = listOf("e2e4", "g1f3", "f1c4", "d2d4", "b1c3")
    private var moveIndex = 0

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        setupOverlays()
        initStockfishIfAvailable()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "chess_assistant_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chess Assistant Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Chess Assistant Aktif")
            .setContentText("Layar mengambang berjalan di atas layar")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                101,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(101, notification)
        }
    }

    private fun setupOverlays() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Lapisan 1: Panah transparan tembus sentuhan jari
        arrowView = ArrowOverlayView(this)
        val arrowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(arrowView, arrowParams)

        // Lapisan 2: Kartu layar mengambang yang bisa digeser & ditekan lama untuk tutup
        hudView = TextView(this).apply {
            text = "♞ Saran: e2e4 • Tahan untuk tutup"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(28, 16, 28, 16)
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 15, 23, 42))
                cornerRadius = 36f
                setStroke(2, Color.argb(200, 16, 185, 129))
            }
        }

        val hudParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 32
            y = 180
        }

        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        var downTime = 0L

        hudView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = hudParams.x
                    startY = hudParams.y
                    downX = event.rawX
                    downY = event.rawY
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    hudParams.x = startX + (event.rawX - downX).toInt()
                    hudParams.y = startY + (event.rawY - downY).toInt()
                    windowManager.updateViewLayout(hudView, hudParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (System.currentTimeMillis() - downTime > 700) {
                        stopSelf()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(hudView, hudParams)
    }

    private fun initStockfishIfAvailable() {
        val nativeBin = File("${applicationInfo.nativeLibraryDir}/libstockfish.so")
        if (nativeBin.exists() && nativeBin.canExecute()) {
            try {
                stockfishProcess = ProcessBuilder(nativeBin.absolutePath).start()
                sfReader = BufferedReader(InputStreamReader(stockfishProcess!!.inputStream))
                sfWriter = OutputStreamWriter(stockfishProcess!!.outputStream).apply {
                    write("uci\nsetoption name Threads value 2\n")
                    flush()
                }
            } catch (_: Exception) {
                stockfishProcess = null
            }
        }
    }

    private fun queryStockfishBestMove(fen: String): String {
        val writer = sfWriter ?: return sampleMoves[moveIndex++ % sampleMoves.size]
        val reader = sfReader ?: return sampleMoves[moveIndex++ % sampleMoves.size]
        return try {
            writer.write("position fen $fen\ngo movetime 250\n")
            writer.flush()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.startsWith("bestmove")) {
                    val move = line!!.split(" ").getOrElse(1) { "" }
                    if (move.length >= 4) return move
                }
            }
            sampleMoves[moveIndex++ % sampleMoves.size]
        } catch (_: Exception) {
            sampleMoves[moveIndex++ % sampleMoves.size]
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra("RESULT_CODE", -1) ?: -1
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("DATA", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("DATA")
        }

        if (code != -1 && data != null) {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mgr.getMediaProjection(code, data)?.apply {
                registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        super.onStop()
                        stopSelf()
                    }
                }, Handler(Looper.getMainLooper()))
            }
            startCaptureLoop()
        }
        return START_NOT_STICKY
    }

    private fun startCaptureLoop() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        mediaProjection?.createVirtualDisplay(
            "ChessScreenCapture",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        serviceScope.launch {
            while (isActive) {
                delay(1200)
                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    val plane = image.planes[0]
                    val rowPadding = plane.rowStride - plane.pixelStride * width
                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / plane.pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(plane.buffer)
                    image.close()
                    bitmap.recycle()
                }

                val boardSize = width
                val top = ((height - boardSize) / 2).coerceAtLeast(0)
                val boardRect = Rect(0, top, boardSize, (top + boardSize).coerceAtMost(height))
                val bestMove = queryStockfishBestMove("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")

                withContext(Dispatchers.Main) {
                    arrowView.setMove(bestMove, boardRect)
                    hudView.text = "♞ Saran: $bestMove • Tahan untuk tutup"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            sfWriter?.write("quit\n")
            sfWriter?.flush()
            stockfishProcess?.destroy()
        } catch (_: Exception) {
        }
        imageReader?.close()
        mediaProjection?.stop()
        if (::arrowView.isInitialized) windowManager.removeView(arrowView)
        if (::hudView.isInitialized) windowManager.removeView(hudView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class ArrowOverlayView(context: Context) : View(context) {
    private var moveUci: String = ""
    private var boardRect: Rect? = null

    private val paint = Paint().apply {
        color = Color.argb(220, 16, 185, 129)
        strokeWidth = 16f
        style = Paint.Style.FILL_AND_STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    fun setMove(uci: String, rect: Rect) {
        moveUci = uci
        boardRect = rect
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect = boardRect ?: return
        if (moveUci.length < 4) return

        val fromCol = moveUci[0] - 'a'
        val fromRow = 8 - (moveUci[1] - '0')
        val toCol = moveUci[2] - 'a'
        val toRow = 8 - (moveUci[3] - '0')
        if (fromCol !in 0..7 || fromRow !in 0..7 || toCol !in 0..7 || toRow !in 0..7) return

        val sq = rect.width() / 8f
        val x1 = rect.left + (fromCol + 0.5f) * sq
        val y1 = rect.top + (fromRow + 0.5f) * sq
        val x2 = rect.left + (toCol + 0.5f) * sq
        val y2 = rect.top + (toRow + 0.5f) * sq

        canvas.drawLine(x1, y1, x2, y2, paint)
        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val headLen = 40f
        canvas.drawLine(
            x2, y2,
            (x2 - headLen * cos(angle - 0.5)).toFloat(),
            (y2 - headLen * sin(angle - 0.5)).toFloat(),
            paint
        )
        canvas.drawLine(
            x2, y2,
            (x2 - headLen * cos(angle + 0.5)).toFloat(),
            (y2 - headLen * sin(angle + 0.5)).toFloat(),
            paint
        )
    }
}
