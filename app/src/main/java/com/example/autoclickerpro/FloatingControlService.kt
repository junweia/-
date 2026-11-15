package com.example.autoclickerpro

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class FloatingControlService : Service() {
    private lateinit var wm: WindowManager
    private var panel: LinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var points: List<Pair<Int, Int>> = emptyList()
    private var interval = 500L
    private var maxCount = 0
    private var completed = 0
    private var index = 0

    private val clickLoop = object : Runnable {
        override fun run() {
            if (!running) return
            val service = ClickAccessibilityService.instance
            if (service == null) {
                stopLoop()
                Toast.makeText(this@FloatingControlService, "请先启用本应用的无障碍服务", Toast.LENGTH_LONG).show()
                updateStatus("未启用无障碍")
                return
            }
            if (points.isEmpty()) { stopLoop(); return }
            val p = points[index]
            service.tap(p.first, p.second)
            completed++
            index = (index + 1) % points.size
            if (maxCount > 0 && completed >= maxCount) {
                stopLoop()
                updateStatus("已完成 $completed 次")
            } else {
                updateStatus("运行中：$completed")
                handler.postDelayed(this, interval)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val raw = intent?.getStringExtra("points") ?: "500,900"
        points = raw.split(";").mapNotNull {
            val xy = it.split(",")
            if (xy.size != 2) null else {
                val x = xy[0].toIntOrNull(); val y = xy[1].toIntOrNull()
                if (x == null || y == null || x < 0 || y < 0) null else x to y
            }
        }
        interval = (intent?.getLongExtra("interval", 500L) ?: 500L).coerceAtLeast(100L)
        maxCount = (intent?.getIntExtra("count", 0) ?: 0).coerceAtLeast(0)
        if (points.isEmpty()) { stopSelf(); return START_NOT_STICKY }
        showPanel()
        return START_NOT_STICKY
    }

    private fun showPanel() {
        if (panel != null) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 8, 10, 8)
            setBackgroundColor(0xEE111827.toInt())
        }
        val title = TextView(this).apply {
            text = "AUTO PRO  ·  拖这里移动"
            setTextColor(0xFF69A7FF.toInt())
            setPadding(8, 6, 8, 6)
        }
        title.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { dragX = event.rawX; dragY = event.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    params.x += (event.rawX - dragX).toInt()
                    params.y += (event.rawY - dragY).toInt()
                    dragX = event.rawX; dragY = event.rawY
                    wm.updateViewLayout(box, params); true
                }
                else -> true
            }
        }
        box.addView(title)
        val toggle = Button(this).apply {
            text = "开始"
            setOnClickListener {
                if (running) {
                    stopLoop(); text = "继续"; updateStatus("已暂停")
                } else if (ClickAccessibilityService.instance == null) {
                    Toast.makeText(this@FloatingControlService, "请先启用无障碍服务", Toast.LENGTH_LONG).show()
                } else {
                    running = true; completed = 0; index = 0
                    text = "暂停"; updateStatus("运行中"); handler.post(clickLoop)
                }
            }
        }
        box.addView(toggle)
        val status = TextView(this).apply {
            tag = "status"; text = "待机 · ${points.size} 个点"
            setTextColor(android.graphics.Color.WHITE); setPadding(8, 4, 8, 4)
        }
        box.addView(status)
        box.addView(Button(this).apply {
            text = "停止并关闭"; setOnClickListener { stopLoop(); stopSelf() }
        })
        panel = box
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 180 }
        wm.addView(box, params)
    }

    private var dragX = 0f
    private var dragY = 0f

    private fun stopLoop() {
        running = false
        handler.removeCallbacks(clickLoop)
        (panel?.getChildAt(1) as? Button)?.text = "开始"
    }

    private fun updateStatus(value: String) {
        panel?.findViewWithTag<TextView>("status")?.text = value
    }

    override fun onDestroy() {
        stopLoop()
        panel?.let { wm.removeView(it) }
        panel = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
