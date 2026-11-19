package com.mehdi.tradeguard

import android.content.Context

/** مقادیر پیش‌فرض بر اساس اسکرین‌شات 1080x2400 (Galaxy A51): ردیف SELL|BUY از x=63..1015 و y=2075..2208 */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun brokerPkg(c: Context) = sp(c).getString("pkg", "com.expertoption")!!
    fun lockSec(c: Context) = sp(c).getInt("lockSec", 35)     // 30 ثانیه معامله + 5 ثانیه حاشیه
    fun maxTrades(c: Context) = sp(c).getInt("maxTrades", 20) // سقف معامله در هر جلسه
    fun restMin(c: Context) = sp(c).getInt("restMin", 15)     // استراحت لازم برای صفر شدن شمارنده
    fun x(c: Context) = sp(c).getInt("x", 63)
    fun y(c: Context) = sp(c).getInt("y", 2075)
    fun w(c: Context) = sp(c).getInt("w", 952)
    fun h(c: Context) = sp(c).getInt("h", 133)

    fun save(c: Context, m: Map<String, Any>) {
        val e = sp(c).edit()
        m.forEach { (k, v) -> if (v is Int) e.putInt(k, v) else e.putString(k, v.toString()) }
        e.apply()
    }
}
