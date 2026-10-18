package moto

import kotlin.math.abs
import kotlin.math.round

/** np.round(x, decimals): round half to even. */
fun npRound(x: Double, decimals: Int): Double {
    var f = 1.0
    repeat(decimals) { f *= 10.0 }
    return round(x * f) / f
}

/** Mimics Python's str(float) / repr(float). */
fun pyRepr(d: Double): String {
    if (d.isNaN()) return "nan"
    if (d.isInfinite()) return if (d > 0) "inf" else "-inf"
    if (d == 0.0) return if (1.0 / d < 0) "-0.0" else "0.0"
    val s = d.toString()
    val neg = s.startsWith("-")
    val body = s.removePrefix("-")
    var mant = body
    var exp = 0
    val ei = body.indexOfFirst { it == 'E' || it == 'e' }
    if (ei >= 0) { mant = body.substring(0, ei); exp = body.substring(ei + 1).removePrefix("+").toInt() }
    val dot = mant.indexOf('.')
    val intPart = if (dot >= 0) mant.substring(0, dot) else mant
    val frac = if (dot >= 0) mant.substring(dot + 1) else ""
    var digits = intPart + frac
    var pointPos = intPart.length + exp
    val lead = digits.indexOfFirst { it != '0' }
    digits = digits.substring(lead)
    pointPos -= lead
    digits = digits.trimEnd('0')
    val decExp = pointPos - 1
    val out = when {
        decExp < -4 || decExp >= 16 -> {
            val m = digits.substring(0, 1) + (if (digits.length > 1) "." + digits.substring(1) else "")
            m + "e" + (if (decExp < 0) "-" else "+") + abs(decExp).toString().padStart(2, '0')
        }
        pointPos <= 0 -> "0." + "0".repeat(-pointPos) + digits
        pointPos >= digits.length -> digits + "0".repeat(pointPos - digits.length) + ".0"
        else -> digits.substring(0, pointPos) + "." + digits.substring(pointPos)
    }
    return (if (neg) "-" else "") + out
}

/** Mimics "%f" (6 decimals). */
fun fmtF(d: Double): String {
    if (d.isNaN()) return "nan"
    if (d.isInfinite()) return if (d > 0) "inf" else "-inf"
    val a = abs(d)
    if (a >= 9.0e12) return pyRepr(d)
    val scaled = round(a * 1_000_000.0).toLong()
    val s = (scaled / 1_000_000).toString() + "." + (scaled % 1_000_000).toString().padStart(6, '0')
    return if (d < 0 && scaled != 0L) "-$s" else s
}
