package moto

import kotlin.math.*

/** Builds the two stacked plots (torque-speed, current-speed) as an inline SVG string. */
object Plotter {
    private const val LEFT = 80.0
    private const val RIGHT = 780.0
    private const val PANEL_H = 320.0

    fun render(speed: DoubleArray, tm: DoubleArray, im: DoubleArray, sync: Double): String {
        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 800" style="width:100%;max-width:800px;background:#fff;font-family:sans-serif;font-size:12px">""")
        panel(sb, speed, tm, sync, 30.0, "Torque (T/Tn)", "#1f77b4")
        panel(sb, speed, im, sync, 430.0, "Current (pu)", "#ff0000")
        sb.append("</svg>")
        return sb.toString()
    }

    private fun f(v: Double) = pyRepr(npRound(v, 2))

    private fun tick(v: Double): String {
        val r = npRound(v, 6)
        return if (r == round(r) && abs(r) < 1e15) r.toLong().toString() else pyRepr(r)
    }

    private fun niceTicks(lo: Double, hi: Double): List<Double> {
        val span = hi - lo
        if (!(span > 0)) return listOf(lo)
        val raw = span / 6
        val mag = 10.0.pow(floor(log10(raw)))
        val norm = raw / mag
        val step = (if (norm < 1.5) 1.0 else if (norm < 3) 2.0 else if (norm < 7) 5.0 else 10.0) * mag
        val out = ArrayList<Double>()
        var v = ceil(lo / step - 1e-9) * step
        while (v <= hi + step * 1e-9) { out.add(v); v += step }
        return out
    }

    private fun panel(sb: StringBuilder, xs: DoubleArray, ys: DoubleArray, sync: Double, top: Double, ylabel: String, color: String) {
        val bottom = top + PANEL_H
        val fin = ys.filter { it.isFinite() }
        var lo = fin.minOrNull() ?: 0.0
        var hi = fin.maxOrNull() ?: 1.0
        if (hi == lo) { lo -= 1.0; hi += 1.0 }
        val pad = (hi - lo) * 0.05
        lo -= pad; hi += pad

        fun px(v: Double) = LEFT + v / sync * (RIGHT - LEFT)
        fun py(v: Double) = bottom - (v - lo) / (hi - lo) * (bottom - top)

        for (t in niceTicks(0.0, sync)) {
            val x = px(t)
            sb.append("""<line x1="${f(x)}" y1="$top" x2="${f(x)}" y2="$bottom" stroke="#bfbfbf" stroke-dasharray="4 3"/>""")
            sb.append("""<text x="${f(x)}" y="${bottom + 16}" text-anchor="middle">${tick(t)}</text>""")
        }
        for (t in niceTicks(lo, hi)) {
            val y = py(t)
            sb.append("""<line x1="$LEFT" y1="${f(y)}" x2="$RIGHT" y2="${f(y)}" stroke="#bfbfbf" stroke-dasharray="4 3"/>""")
            sb.append("""<text x="${LEFT - 6}" y="${f(y + 4)}" text-anchor="end">${tick(t)}</text>""")
        }
        sb.append("""<rect x="$LEFT" y="$top" width="${RIGHT - LEFT}" height="$PANEL_H" fill="none" stroke="#000"/>""")

        val path = StringBuilder()
        var pen = false
        for (i in xs.indices) {
            if (!ys[i].isFinite()) { pen = false; continue }
            path.append(if (pen) "L" else "M").append(f(px(xs[i]))).append(' ').append(f(py(ys[i]))).append(' ')
            pen = true
        }
        sb.append("""<path d="$path" fill="none" stroke="$color" stroke-width="1.5"/>""")
        sb.append("""<text x="${(LEFT + RIGHT) / 2}" y="${bottom + 36}" text-anchor="middle">Speed (rpm)</text>""")
        sb.append("""<text transform="translate(18 ${(top + bottom) / 2}) rotate(-90)" text-anchor="middle">$ylabel</text>""")
    }
}

/** Equivalent-circuit sketches (stand-ins for images/single_cage.png and images/dbl_cage.png). */
object Circuit {
    private fun box(x: Int, y: Int, w: Int, h: Int, label: String) =
        """<rect x="$x" y="$y" width="$w" height="$h" fill="#fff" stroke="#000"/><text x="${x + w / 2}" y="${y + h / 2 + 4}" text-anchor="middle">$label</text>"""

    private fun line(x1: Int, y1: Int, x2: Int, y2: Int) =
        """<line x1="$x1" y1="$y1" x2="$x2" y2="$y2" stroke="#000"/>"""

    private fun branch(x: Int, top: Int, bottom: Int, labels: List<String>): String {
        val sb = StringBuilder()
        sb.append(line(x, top, x, bottom))
        var y = top + 25
        for (l in labels) { sb.append(box(x - 28, y, 56, 28, l)); y += 55 }
        return sb.toString()
    }

    fun svg(double: Boolean): String {
        val top = 50; val bot = 220
        val branches = if (double)
            listOf(250 to listOf("Rc"), 330 to listOf("Xm"), 410 to listOf("Rr1/s", "Xr1"), 490 to listOf("Rr2/s", "Xr2"))
        else
            listOf(250 to listOf("Rc"), 330 to listOf("Xm"), 410 to listOf("Rr/s", "Xr"))
        val last = branches.last().first
        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 560 250" style="width:100%;max-width:560px;font-family:sans-serif;font-size:12px">""")
        sb.append(line(20, top, 60, top)).append(box(60, top - 14, 50, 28, "Rs"))
        sb.append(line(110, top, 140, top)).append(box(140, top - 14, 50, 28, "Xs"))
        sb.append(line(190, top, last, top))
        sb.append(line(20, bot, last, bot))
        for ((x, labels) in branches) sb.append(branch(x, top, bot, labels))
        sb.append("""<text x="14" y="${top - 20}">V</text>""")
        sb.append("</svg>")
        return sb.toString()
    }
}
