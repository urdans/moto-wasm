package moto

import kotlin.math.*
import kotlin.random.Random

/** Minimal complex number (replaces Python's built-in complex). */
data class Cx(val re: Double, val im: Double) {
    operator fun plus(o: Cx) = Cx(re + o.re, im + o.im)
    operator fun minus(o: Cx) = Cx(re - o.re, im - o.im)
    operator fun times(o: Cx) = Cx(re * o.re - im * o.im, re * o.im + im * o.re)
    operator fun div(o: Cx): Cx {
        val d = o.re * o.re + o.im * o.im
        return Cx((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d)
    }
    fun abs() = hypot(re, im)
    fun conj() = Cx(re, -im)
}

fun cx(re: Double, im: Double = 0.0) = Cx(re, im)

typealias Mat = Array<DoubleArray>

class SingularMatrixException : Exception("Singular matrix")

/** Determinant via LU with partial pivoting (np.linalg.det). */
fun det(a: Mat): Double {
    val n = a.size
    val m = Array(n) { a[it].copyOf() }
    var d = 1.0
    for (c in 0 until n) {
        var p = c
        for (r in c + 1 until n) if (abs(m[r][c]) > abs(m[p][c])) p = r
        if (m[p][c] == 0.0) return 0.0
        if (p != c) { val t = m[p]; m[p] = m[c]; m[c] = t; d = -d }
        d *= m[c][c]
        for (r in c + 1 until n) {
            val f = m[r][c] / m[c][c]
            for (k in c until n) m[r][k] -= f * m[c][k]
        }
    }
    return d
}

/** Matrix inverse via Gauss-Jordan (np.matrix.getI). Throws if singular, like LinAlgError. */
fun inverse(a: Mat): Mat {
    val n = a.size
    val m = Array(n) { r -> DoubleArray(2 * n) { c -> if (c < n) a[r][c] else if (c - n == r) 1.0 else 0.0 } }
    for (c in 0 until n) {
        var p = c
        for (r in c + 1 until n) if (abs(m[r][c]) > abs(m[p][c])) p = r
        if (m[p][c] == 0.0) throw SingularMatrixException()
        if (p != c) { val t = m[p]; m[p] = m[c]; m[c] = t }
        val pv = m[c][c]
        for (k in 0 until 2 * n) m[c][k] /= pv
        for (r in 0 until n) {
            if (r == c) continue
            val f = m[r][c]
            if (f != 0.0) for (k in 0 until 2 * n) m[r][k] -= f * m[c][k]
        }
    }
    return Array(n) { r -> DoubleArray(n) { c -> m[r][n + c] } }
}

fun matMul(a: Mat, b: Mat): Mat =
    Array(a.size) { i -> DoubleArray(b[0].size) { j -> var s = 0.0; for (k in b.indices) s += a[i][k] * b[k][j]; s } }

fun matVec(a: Mat, v: DoubleArray): DoubleArray =
    DoubleArray(a.size) { i -> var s = 0.0; for (k in v.indices) s += a[i][k] * v[k]; s }

fun transpose(a: Mat): Mat = Array(a[0].size) { j -> DoubleArray(a.size) { i -> a[i][j] } }

fun dot(y: DoubleArray): Double { var s = 0.0; for (v in y) s += v * v; return s }

/** Random numbers (np.random.rand / randn). */
object Rng {
    fun rand(): Double = Random.Default.nextDouble()
    fun randn(): Double {
        var u = rand()
        while (u == 0.0) u = rand()
        val v = rand()
        return sqrt(-2.0 * ln(u)) * cos(2.0 * PI * v)
    }
}

/** np.argsort: ascending, NaN last, stable. */
fun argsort(a: DoubleArray): List<Int> = a.indices.sortedWith { i, j ->
    val x = a[i]; val y = a[j]
    when {
        x.isNaN() && y.isNaN() -> 0
        x.isNaN() -> 1
        y.isNaN() -> -1
        else -> x.compareTo(y)
    }
}
