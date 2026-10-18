package moto

import kotlin.math.*

private fun runDescent(desc: String, p: DoubleArray, kx: Double, kr: Double, a: AlgoData): SolverResult =
    when (desc) {
        "NR" -> nrSolver(p, 1, kx, kr, a.maxIter, a.convErr)
        "LM" -> lmSolver(p, 1, kx, kr, 1e-7, 5.0, a.maxIter, a.convErr)
        "DNR" -> dnrSolver(p, 1, kx, kr, 1e-7, a.maxIter, a.convErr)
        else -> error("Unknown descent algorithm: $desc")
    }

/**
 * HY_SOLVER - GA over the two fixed parameters (RX[0] -> kx/Xr2, RX[1] -> kr/Rs), each member
 * refined by a descent solver in "fixed Rs and Xr2" mode (mode = 1).
 */
suspend fun hySolver(
    desc: String, p: DoubleArray, pop: Int, nR: Int, nE: Int, cF: Double, nGen: Int, errTol: Double,
    algo: AlgoData, status: (String) -> Unit, yielder: Yielder,
): SolverResult {
    val sigma = 0.01
    var rx = Array(pop) { DoubleArray(2) { 0.15 * Rng.rand() } }
    val x = Array(pop) { DoubleArray(8) }
    val err = DoubleArray(pop)

    for (i in 0 until pop) {
        status("Calculating generation 1, member ${i + 1}...")
        yielder.maybeYield()
        val r = runDescent(desc, p, rx[i][0], rx[i][1], algo)
        x[i] = r.z; err[i] = r.err
        if (err[i] < errTol) return SolverResult(x[i].copyOf(), 1, err[i], 1)
    }

    for (gen in 2..nGen) {
        val index = argsort(err)
        val rxNew = Array(pop) { DoubleArray(2) }
        val rxMate = index.take(nR).map { rx[it] }

        for (k in 0 until min(nE, pop)) rxNew[k] = rx[index[k]].copyOf()

        val nC = round((pop - nE) * cF).toInt()
        fun pick(): Int {
            var i = ceil(nR * Rng.rand()).toInt() - 1
            if (i < 0) i += rxMate.size
            return i.coerceAtMost(rxMate.size - 1)
        }
        for (j in 0 until nC) {
            val a = pick()
            val b = pick()
            val weight = DoubleArray(2) { Rng.rand() }
            rxNew[nE + j] = DoubleArray(2) { weight[it] * rxMate[a][it] + (1 - weight[it]) * rxMate[b][it] }
        }

        val nM = pop - nE - nC
        for (k in 0 until nM) {
            val parent = rxMate[pick()]
            rxNew[nE + nC + k] = DoubleArray(2) { abs(parent[it] + sigma * Rng.randn()) }
        }
        rx = rxNew

        for (i in 0 until pop) {
            status("Calculating generation $gen, member ${i + 1}...")
            yielder.maybeYield()
            val r = runDescent(desc, p, rx[i][0], rx[i][1], algo)
            x[i] = r.z; err[i] = r.err
            if (err[i] < errTol) return SolverResult(x[i].copyOf(), gen, err[i], 1)
        }

        if (gen == nGen) {
            val best = argsort(err)[0]
            return SolverResult(x[best].copyOf(), gen, err[best], 0)
        }
    }

    val best = argsort(err)[0]
    return SolverResult(x[best].copyOf(), 1, err[best], 0)
}
