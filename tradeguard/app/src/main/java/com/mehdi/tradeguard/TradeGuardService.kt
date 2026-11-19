package com.mehdi.tradeguard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView

/**
 * قانون: در هر لحظه فقط یک معامله باز.
 * لایه‌ی شفاف روی ردیف SELL | BUY قرار می‌گیرد. اولین لمس مجاز، با dispatchGesture به بروکر
 * منتقل می‌شود و بعد تا پایان زمان قفل، همه‌ی لمس‌ها بلاک می‌شوند.
 */
class TradeGuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var root: FrameLayout
    private lateinit var label: TextView
    private lateinit var lp: WindowManager.LayoutParams

    private var attached = false
    private var lockedUntil = 0L          // elapsedRealtime
    private var tradesDone = 0
    private var lastTradeWall = 0L        // currentTimeMillis

    private val ignoredPkgs = setOf("com.android.systemui", "com.samsung.android.honeyboard")

    override fun onServiceConnected() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        root = FrameLayout(this).apply { setBackgroundColor(0x01000000) }
        label = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER
        }
        root.addView(label, FrameLayout.LayoutParams(-1, -1))
        root.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) onTap(e.rawX, e.rawY)
            true
        }
        lp = WindowManager.LayoutParams(
            0, 0,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    override fun onAccessibilityEvent(ev: AccessibilityEvent) {
        if (ev.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = ev.packageName?.toString() ?: return
        if (pkg in ignoredPkgs) return
        if (pkg == Prefs.brokerPkg(this)) attach() else detach()
    }

    override fun onInterrupt() {}

    override fun onDestroy() { detach(); super.onDestroy() }

    // ---------- overlay ----------
    private fun attach() {
        if (attached) return
        lp.x = Prefs.x(this); lp.y = Prefs.y(this)
        lp.width = Prefs.w(this); lp.height = Prefs.h(this)
        wm.addView(root, lp)
        attached = true
        refreshUi()
    }

    private fun detach() {
        if (!attached) return
        wm.removeView(root)
        attached = false
    }

    private fun setTouchable(touchable: Boolean) {
        if (!attached) return
        lp.flags = if (touchable) lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        wm.updateViewLayout(root, lp)
    }

    // ---------- منطق اصلی ----------
    private fun onTap(x: Float, y: Float) {
        val now = SystemClock.elapsedRealtime()

        // استراحت کافی => شمارنده صفر
        val restMs = Prefs.restMin(this) * 60_000L
        if (tradesDone > 0 && System.currentTimeMillis() - lastTradeWall > restMs) tradesDone = 0

        if (now < lockedUntil) { buzz(); return }                  // معامله باز است
        if (tradesDone >= Prefs.maxTrades(this)) { buzz(); refreshUi(); return } // سقف جلسه

        lockedUntil = now + Prefs.lockSec(this) * 1000L
        tradesDone++
        lastTradeWall = System.currentTimeMillis()
        setTouchable(false)                      // لمس از لایه رد شود
        handler.postDelayed({ injectTap(x, y) }, 60)
        refreshUi()
    }

    private fun injectTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build()
        val ok = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { setTouchable(true) }
            override fun onCancelled(d: GestureDescription?) {
                // کلیک نرسید؛ قفل را برنگردان که معامله‌ی ناخواسته‌ی دوم ممکن نشود، فقط لایه را فعال کن
                lockedUntil = 0L; tradesDone = maxOf(0, tradesDone - 1); setTouchable(true); refreshUi()
            }
        }, null)
        if (!ok) { lockedUntil = 0L; tradesDone = maxOf(0, tradesDone - 1); setTouchable(true); refreshUi() }
    }

    private val ticker = object : Runnable {
        override fun run() { refreshUi() }
    }

    private fun refreshUi() {
        if (!attached) return
        handler.removeCallbacks(ticker)
        val now = SystemClock.elapsedRealtime()
        val max = Prefs.maxTrades(this)
        when {
            now < lockedUntil -> {
                root.setBackgroundColor(0xCCB00020.toInt())
                label.text = "معامله باز است — ${(lockedUntil - now) / 1000 + 1} ثانیه صبر کن"
                handler.postDelayed(ticker, 250)
            }
            tradesDone >= max -> {
                root.setBackgroundColor(0xCC37474F.toInt())
                label.text = "سقف $max معامله پر شد — استراحت کن (${Prefs.restMin(this)} دقیقه)"
            }
            else -> {
                root.setBackgroundColor(0x01000000)
                label.text = ""
            }
        }
    }

    private fun buzz() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
