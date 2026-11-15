package com.example.autoclickerpro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var points: EditText
    private lateinit var interval: EditText
    private lateinit var repetitions: EditText
    private val prefs by lazy { getSharedPreferences("clicker", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(10, 14, 25)
        window.navigationBarColor = Color.rgb(10, 14, 25)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(Color.rgb(10, 14, 25))
        }
        fun label(s: String, size: Float = 15f) = TextView(this).apply {
            text = s; textSize = size; setTextColor(Color.WHITE)
            setPadding(0, 10, 0, 5)
        }
        fun input(hintText: String, value: String, multi: Boolean = false) =
            EditText(this).apply {
                hint = hintText; setText(value); textSize = 16f
                setTextColor(Color.WHITE); setHintTextColor(0xFF8A96AA.toInt())
                setPadding(12, 10, 12, 10)
                setBackgroundColor(0xFF1B2638.toInt())
                if (multi) { minLines = 3; gravity = android.view.Gravity.TOP }
                else setSingleLine(true)
            }
        fun button(caption: String, action: () -> Unit) = Button(this).apply {
            text = caption; isAllCaps = false; setTextColor(Color.WHITE)
            setBackgroundColor(0xFF1769E0.toInt())
            setOnClickListener { action() }
        }

        root.addView(label("AUTO CLICKER  /  PRO", 22f))
        root.addView(label("多点坐标：每行一个，格式为 X,Y"))
        points = input("例如：500,900", prefs.getString("points", "500,900\n500,1100")!!, true)
        root.addView(points, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(label("点击间隔（毫秒，最小 100）"))
        interval = input("500", prefs.getString("interval", "500")!!)
        root.addView(interval)
        root.addView(label("循环次数（0 = 持续运行，直到手动停止）"))
        repetitions = input("0", prefs.getString("count", "0")!!)
        root.addView(repetitions)

        root.addView(button("保存当前脚本") {
            prefs.edit().putString("points", points.text.toString())
                .putString("interval", interval.text.toString())
                .putString("count", repetitions.text.toString()).apply()
            toast("脚本已保存在本机")
        })
        root.addView(button("打开无障碍服务设置") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("授予悬浮窗权限") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        root.addView(button("启动悬浮控制面板") {
            val parsed = parsePoints(points.text.toString())
            if (parsed.isEmpty()) { toast("请至少填写一个有效坐标，例如 500,900"); return@button }
            val ms = (interval.text.toString().toLongOrNull() ?: 500L).coerceAtLeast(100L)
            val count = (repetitions.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)
            prefs.edit().putString("points", points.text.toString())
                .putString("interval", ms.toString()).putString("count", count.toString()).apply()
            if (!Settings.canDrawOverlays(this)) {
                toast("请先授予悬浮窗权限"); return@button
            }
            val encoded = parsed.joinToString(";") { "${it.first},${it.second}" }
            startService(Intent(this, FloatingControlService::class.java).apply {
                putExtra("points", encoded); putExtra("interval", ms); putExtra("count", count)
            })
        })
        root.addView(label("提示：坐标原点为屏幕左上角。可先用单点测试坐标，再添加多行。无障碍与悬浮窗权限均需由你在系统设置中主动开启。", 13f))
        root.addView(label("隐私：本应用不读取屏幕内容、不联网、不上传点击记录。请只在允许自动化操作的场景中使用。", 13f))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun parsePoints(raw: String): List<Pair<Int, Int>> =
        raw.lines().mapNotNull { line ->
            val p = line.trim().split(",")
            if (p.size != 2) null else {
                val x = p[0].trim().toIntOrNull()
                val y = p[1].trim().toIntOrNull()
                if (x == null || y == null || x < 0 || y < 0) null else x to y
            }
        }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
