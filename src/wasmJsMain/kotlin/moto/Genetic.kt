package moto

import kotlin.math.*

/** GA_SOLVER - genetic algorithm for the double cage model. Searches all 8 parameters directly. */
suspend fun gaSolver(
    p: DoubleArray, pop: Int, nR: Int, nE: Int, cF: Double, nGen: Int, errTol: Double,
    status: (String) -> Unit, yielder: Yielder,
): SolverResult {
    val sigma = doubleArrayOf(0.01, 0.01, 0.33, 0.01, 0.01, 0.01, 0.01, 6.67)
    val w = doubleArrayOf(0.15, 0.15, 5.0, 0.15, 0.3, 0.15, 0.15, 100.0)

    val sf = p[0]
    val eff = p[1]
    val pf = p[2]
    val tFl = pf * eff / (1 - sf)
    val tB = p[3] * tFl
    val tLr = p[4] * tFl
    val iLr = p[5]
    val pmFl = pf * eff
    val qFl = sin(acos(pf))
    val pqt = doubleArrayOf(pmFl, qFl, tB, tLr, iLr, eff)

    fun errOf(row: DoubleArray): Double {
        val c = calcPqt(sf, row)
        return dot(DoubleArray(6) { (pqt[it] - c[it]) / pqt[it] })
    }

    var x = Array(pop) { DoubleArray(8) { c -> w[c] * Rng.rand() } }
    val err = DoubleArray(pop)

    for (i in 0 until pop) {
        err[i] = errOf(x[i])
        if (err[i] < errTol) return SolverResult(x[i].copyOf(), 1, err[i], 1)
    }

    for (gen in 2..nGen) {
        status("Calculating generation $gen...")
        yielder.maybeYield()

        val index = argsort(err)
        val xNew = Array(pop) { DoubleArray(8) }
        val xMate = index.take(nR).map { x[it] }

        // Elite children
        for (k in 0 until min(nE, pop)) xNew[k] = x[index[k]].copyOf()

        // Crossover
        val nC = round((pop - nE) * cF).toInt()
        fun pick(): Int {
            var i = ceil(nR * Rng.rand()).toInt() - 1
            if (i < 0) i += xMate.size
            return i.coerceAtMost(xMate.size - 1)
        }
        for (j in 0 until nC) {
            val a = pick()
            val b = pick()
            val weight = DoubleArray(8) { Rng.rand() }
            xNew[nE + j] = DoubleArray(8) { weight[it] * xMate[a][it] + (1 - weight[it]) * xMate[b][it] }
        }

        // Mutation
        val nM = pop - nE - nC
        for (k in 0 until nM) {
            val parent = xMate[pick()]
            xNew[nE + nC + k] = DoubleArray(8) { abs(parent[it] + sigma[it] * Rng.randn()) }
        }
        x = xNew

        for (i in 0 until pop) {
            err[i] = errOf(x[i])
            if (err[i] < errTol) return SolverResult(x[i].copyOf(), gen, err[i], 1)
        }
        yielder.maybeYield()

        if (gen == nGen) {
            val best = argsort(err)[0]
            return SolverResult(x[best].copyOf(), gen, err[best], 0)
        }
    }

    // n_gen < 2: the Python original returns None here; return the best initial member instead
    val best = argsort(err)[0]
    return SolverResult(x[best].copyOf(), 1, err[best], 0)
}
