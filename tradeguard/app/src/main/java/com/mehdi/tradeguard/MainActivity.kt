package com.mehdi.tradeguard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val fields = LinkedHashMap<String, EditText>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 40)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        col.addView(TextView(this).apply { text = "TradeGuard — فقط یک معامله در هر زمان"; textSize = 20f })

        fun add(key: String, title: String, value: String, number: Boolean = true) {
            col.addView(TextView(this).apply { text = title; setPadding(0, 24, 0, 0) })
            val et = EditText(this).apply {
                setText(value)
                if (number) inputType = InputType.TYPE_CLASS_NUMBER
            }
            fields[key] = et; col.addView(et)
        }
        add("pkg", "پکیج اپ بروکر (Package name)", Prefs.brokerPkg(this), false)
        add("lockSec", "مدت قفل بعد از هر معامله (ثانیه)", Prefs.lockSec(this).toString())
        add("maxTrades", "سقف معامله در هر جلسه", Prefs.maxTrades(this).toString())
        add("restMin", "استراحت لازم برای صفر شدن شمارنده (دقیقه)", Prefs.restMin(this).toString())
        add("x", "x گوشه‌ی چپ ردیف SELL|BUY (پیکسل)", Prefs.x(this).toString())
        add("y", "y بالای ردیف (پیکسل)", Prefs.y(this).toString())
        add("w", "عرض ردیف (پیکسل)", Prefs.w(this).toString())
        add("h", "ارتفاع ردیف (پیکسل)", Prefs.h(this).toString())

        col.addView(Button(this).apply {
            text = "ذخیره"
            setOnClickListener {
                val m = HashMap<String, Any>()
                fields.forEach { (k, v) ->
                    val t = v.text.toString().trim()
                    m[k] = if (k == "pkg") t else (t.toIntOrNull() ?: return@setOnClickListener)
                }
                Prefs.save(this@MainActivity, m)
                Toast.makeText(this@MainActivity, "ذخیره شد. بروکر را یک‌بار ببند و باز کن.", Toast.LENGTH_LONG).show()
            }
        })
        col.addView(Button(this).apply {
            text = "فعال‌سازی سرویس (Accessibility)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        setContentView(android.widget.ScrollView(this).apply { addView(col) })
    }
}
