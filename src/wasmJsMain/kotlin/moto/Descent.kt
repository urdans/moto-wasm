package moto

import kotlin.math.*

class SolverResult(val z: DoubleArray, val iter: Int, val err: Double, val conv: Int)

private const val H = 0.00001
private const val HN_MIN = 0.0000001
private const val STALL_LIMIT = 200_000 // safety net against browser freeze; never reached in normal runs

/** Shared set-up for the double-cage solvers (initial point, change of variables, Jacobian). */
private class Dc(p: DoubleArray, val mode: Int, val kx: Double, val kr: Double) {
    val sf = p[0]
    private val eff = p[1]
    private val pf = p[2]
    private val tFl = pf * eff / (1 - sf)
    private val tB = p[3] * tFl
    private val tLr = p[4] * tFl
    private val iLr = p[5]
    private val pmFl = pf * eff
    private val qFl = sin(acos(pf))
    val pqt = doubleArrayOf(pmFl, qFl, tB, tLr, iLr, eff)

    val z = DoubleArray(8)
    var x = DoubleArray(6)

    init {
        z[2] = 1 / qFl
        z[1] = 0.05 * z[2]
        z[3] = 1 / pmFl * sf
        z[4] = 1.2 * z[1]
        z[5] = 5 * z[3]
        z[7] = 12.0
        if (mode == 0) { z[0] = kr * z[3]; z[6] = kx * z[1] } else { z[0] = kr; z[6] = kx }

        x[0] = z[3]
        x[1] = z[5] - z[3]
        x[2] = z[2]
        x[3] = z[1]
        x[4] = z[4] - z[6]
        x[5] = z[7]
    }

    /** Change of variables back to equivalent-circuit parameters. */
    fun applyX() {
        z[1] = x[3]
        z[2] = x[2]
        z[3] = x[0]
        z[4] = if (mode == 0) kx * x[3] + x[4] else z[6] + x[4]
        z[5] = x[0] + x[1]
        z[7] = x[5]
        if (mode == 0) { z[0] = kr * z[3]; z[6] = kx * z[1] }
    }

    fun resid(): DoubleArray {
        val c = calcPqt(sf, z)
        return DoubleArray(6) { (pqt[it] - c[it]) / pqt[it] }
    }

    /** Finite-difference Jacobian. Perturbs x in place (as the original does) and leaves z perturbed. */
    fun jacobian(y: DoubleArray): Mat {
        val j = Array(6) { DoubleArray(6) }
        for (i in 0 until 6) {
            x[i] = x[i] + H
            applyX()
            val yy = resid()
            for (r in 0 until 6) j[r][i] = (yy[r] - y[r]) / H
            x[i] = x[i] - H
        }
        return j
    }

    fun result(iter: Int, err: Double, errTol: Double, forceConvZero: Boolean = false) =
        SolverResult(z.copyOf(), iter, err, if (!forceConvZero && err < errTol) 1 else 0)
}

/** NR_SOLVER - Newton-Raphson, double cage with core losses. */
fun nrSolver(p: DoubleArray, mode: Int, kx: Double, kr: Double, maxIter: Int, errTol: Double): SolverResult {
    val d = Dc(p, mode, kx, kr)
    var n = 0
    var hn = 1.0
    var err = 1.0
    var iter = 0
    var stall = 0

    while (err > errTol && iter < maxIter) {
        if (++stall > STALL_LIMIT) break
        var y = d.resid()
        val err0 = dot(y)
        val j = d.jacobian(y)

        if (det(j) == 0.0) { println("Jacobian matrix is singular"); break }

        val xReset = d.x
        val yReset = y
        val iter0 = iter
        var singular = false

        while (iter == iter0) {
            val dx = try { matVec(inverse(j), y) } catch (e: SingularMatrixException) { singular = true; break }
            val xo = d.x
            d.x = DoubleArray(6) { abs(xo[it] - hn * dx[it]) }
            d.applyX()

            y = d.resid()
            err = dot(y)

            if (abs(err) >= abs(err0)) {
                n += 1
                hn = 2.0.pow(-n)
                d.x = xReset
                y = yReset
            } else {
                n = 0
                iter += 1
            }
            if (hn < HN_MIN) break
        }
        if (singular) break
    }
    return d.result(iter, err, errTol)
}

/** LM_SOLVER - Levenberg-Marquardt, double cage with core losses. */
fun lmSolver(
    p: DoubleArray, mode: Int, kx: Double, kr: Double,
    lambda0: Double, lambdaMax: Double, maxIter: Int, errTol: Double,
): SolverResult {
    val d = Dc(p, mode, kx, kr)
    var lambda = lambda0
    var err = 1.0
    var iter = 0
    val beta = 3.0
    val gamma = 3.0
    var stall = 0

    while (err > errTol && iter < maxIter) {
        if (++stall > STALL_LIMIT) break
        var y = d.resid()
        val err0 = dot(y)
        val j = d.jacobian(y)

        if (det(j) == 0.0) { println("Jacobian matrix is singular"); break }

        val xReset = d.x
        val yReset = y
        val iter0 = iter
        var singular = false

        while (iter == iter0) {
            // delta_x = inv(J'J + lambda*diag(diag(J'J))) * J' * y
            val jt = transpose(j)
            val jblock = matMul(jt, j)
            val j1 = Array(6) { r -> DoubleArray(6) { c -> jblock[r][c] + if (r == c) lambda * jblock[r][c] else 0.0 } }
            val dx = try { matVec(matMul(inverse(j1), jt), y) } catch (e: SingularMatrixException) { singular = true; break }
            val xo = d.x
            d.x = DoubleArray(6) { abs(xo[it] - dx[it]) }
            d.applyX()

            y = d.resid()
            err = dot(y)

            if (abs(err) >= abs(err0) && iter > 0) {
                lambda *= beta
                d.x = xReset
                y = yReset
            } else {
                lambda /= gamma
                iter += 1
            }
            if (lambda > lambdaMax) break
        }
        if (singular) break
    }
    return d.result(iter, err, errTol)
}

/** DNR_SOLVER - Damped Newton-Raphson, double cage with core losses. */
fun dnrSolver(
    p: DoubleArray, mode: Int, kx: Double, kr: Double,
    lambdaInit: Double, maxIter: Int, errTol: Double,
): SolverResult {
    val d = Dc(p, mode, kx, kr)
    var lambda = lambdaInit
    var n = 0
    var hn = 1.0
    var err = 1.0
    var iter = 0
    val gamma = 3.0
    val beta = 3.0
    var stall = 0

    while (err > errTol && iter < maxIter) {
        if (++stall > STALL_LIMIT) break
        var y = d.resid()
        val err0 = dot(y)
        val j = d.jacobian(y)

        if (det(j) == 0.0) { println("Jacobian matrix is singular"); break }

        val xReset = d.x
        val yReset = y
        val iter0 = iter
        var singular = false

        while (iter == iter0) {
            val jm = Array(6) { r -> DoubleArray(6) { c -> j[r][c] - if (r == c) lambda else 0.0 } }
            val dx = try { matVec(inverse(jm), y) } catch (e: SingularMatrixException) { singular = true; break }
            val xo = d.x
            d.x = DoubleArray(6) { abs(xo[it] - hn * dx[it]) }
            d.applyX()

            y = d.resid()
            err = dot(y)

            if (abs(err) >= abs(err0)) {
                n += 1
                hn = 2.0.pow(-n)
                lambda *= beta
                d.x = xReset
                y = yReset
            } else {
                n = 0
                lambda /= gamma
                iter += 1
            }
            // original returns immediately with conv = 0 here
            if (hn < HN_MIN) return d.result(iter, err, errTol, forceConvZero = true)
        }
        if (singular) break
    }
    return d.result(iter, err, errTol)
}

/**
 * NR_SOLVER_SC - Newton-Raphson, single cage with core losses. z = [Rs Xs Xm Rr1 Rc Xr1].
 *
 * Faithful to the original, including two quirks: the Jacobian perturbation does not refresh the
 * dependent Rs / Xr1 terms, and `z = z_reset` in Python aliases the same array (so the "reset" is a
 * no-op and the full step is simply applied again until the error drops or hn < hn_min).
 */
fun nrSolverSc(p: DoubleArray, mode: Int, kx: Double, kr: Double, maxIter: Int, errTol: Double): SolverResult {
    val sf = p[0]
    val eff = p[1]
    val pf = p[2]
    val tFl = pf * eff / (1 - sf)
    val tB = p[3] * tFl
    val pmFl = pf * eff
    val qFl = sin(acos(pf))

    val z = DoubleArray(6)
    z[2] = 1 / qFl
    z[1] = 0.05 * z[2]
    z[3] = 1 / pmFl * sf
    z[4] = 12.0
    if (mode == 0) { z[0] = kr * z[3]; z[5] = kx * z[1] } else { z[0] = kr; z[5] = kx }

    val pqt = doubleArrayOf(pmFl, qFl, tB, eff)
    fun resid(): DoubleArray {
        val c = calcPqtSc(sf, z)
        return DoubleArray(4) { (pqt[it] - c[it]) / pqt[it] }
    }

    var n = 0
    var hn = 1.0
    var err = 1.0
    var iter = 0
    var stall = 0

    while (err > errTol && iter < maxIter) {
        if (++stall > STALL_LIMIT) break
        var y = resid()
        val err0 = dot(y)

        val j = Array(4) { DoubleArray(4) }
        for (i in 1..4) {
            z[i] = z[i] + H
            val yy = resid()
            for (r in 0 until 4) j[r][i - 1] = (yy[r] - y[r]) / H
            z[i] = z[i] - H
        }

        if (det(j) == 0.0) { println("Jacobian matrix is singular"); break }

        val yReset = y
        val iter0 = iter
        var singular = false

        while (iter == iter0) {
            val dz = try { matVec(inverse(j), y) } catch (e: SingularMatrixException) { singular = true; break }
            z[1] = abs(z[1] - dz[0])
            z[2] = abs(z[2] - dz[1])
            z[3] = abs(z[3] - dz[2])
            z[4] = abs(z[4] - dz[3])
            if (mode == 0) { z[0] = kr * z[3]; z[5] = kx * z[1] } else { z[0] = kr; z[5] = kx }

            y = resid()
            err = dot(y)

            if (abs(err) >= abs(err0)) {
                n += 1
                hn = 2.0.pow(-n)
                // z = z_reset is a no-op in the original (same array object)
                y = yReset
            } else {
                n = 0
                iter += 1
            }
            if (hn < HN_MIN) break
        }
        if (singular) break
    }
    return SolverResult(z.copyOf(), iter, err, if (err < errTol) 1 else 0)
}
