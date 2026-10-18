package moto

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.math.abs

@JsFun("(name, text) => { const b = new Blob([text], {type: 'text/plain'}); const a = document.createElement('a'); a.href = URL.createObjectURL(b); a.download = name; document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 1000); }")
internal external fun downloadText(name: String, text: String)

@JsFun("(input, cb) => { const f = input.files && input.files[0]; if (!f) return; const r = new FileReader(); r.onload = () => cb(r.result); r.readAsText(f); input.value = ''; }")
internal external fun readSelectedFile(input: HTMLInputElement, cb: (JsString) -> Unit)

private fun el(id: String): HTMLElement = document.getElementById(id) as? HTMLElement ?: error("Missing element #$id")
private fun input(id: String) = el(id) as HTMLInputElement
private fun select(id: String) = el(id) as HTMLSelectElement
private fun button(id: String) = el(id) as HTMLButtonElement

private val scope = MainScope()

private val ALGOS_SINGLE = listOf("Newton-Raphson")
private val ALGOS_DOUBLE = listOf(
    "Newton-Raphson", "Levenberg-Marquardt", "Damped Newton-Raphson", "Genetic Algorithm",
    "Hybrid GA-NR", "Hybrid GA-LM", "Hybrid GA-DNR",
)
private val GA_ALGOS = setOf("Genetic Algorithm", "Hybrid GA-NR", "Hybrid GA-LM", "Hybrid GA-DNR")

private fun status(msg: String) { el("status").textContent = msg }

private fun num(id: String, label: String): Double =
    input(id).value.trim().toDoubleOrNull() ?: throw InputError("Invalid number for $label: '${input(id).value}'")

private fun int(id: String, label: String): Int =
    input(id).value.trim().toIntOrNull() ?: throw InputError("Invalid integer for $label: '${input(id).value}'")

/** update_data(): copy the form into the global objects (atomically - nothing changes on a bad value). */
private fun updateData() {
    val desc = input("description").value
    val sync = num("syncSpeed", "Synchronous speed")
    val rated = num("ratedSpeed", "Rated speed")
    val pf = num("ratedPf", "Rated power factor")
    val eff = num("ratedEff", "Rated efficiency")
    val tb = num("tb", "Breakdown torque")
    val tlr = num("tlr", "Locked rotor torque")
    val ilr = num("ilr", "Locked rotor current")
    val maxIter = int("maxIter", "Maximum # iterations")
    val convErr = num("convErr", "Convergence criterion")
    val kr = num("kr", "Linear constraint k_r")
    val kx = num("kx", "Linear constraint k_x")
    val nGen = int("nGen", "Maximum # generations")
    val pop = int("pop", "Members in population")
    val nR = int("nR", "Members in mating pool")
    val nE = int("nE", "Elite children")
    val cF = num("cF", "Crossover fraction")

    Globals.motor.apply {
        description = desc; syncSpeed = sync; ratedSpeed = rated; ratedPf = pf; ratedEff = eff
        tB = tb; tLr = tlr; iLr = ilr
    }
    Globals.algo.apply {
        this.maxIter = maxIter; this.convErr = convErr; kR = kr; kX = kx
        this.nGen = nGen; this.pop = pop; this.nR = nR; this.nE = nE; this.cF = cF
    }
}

/** update_window(): copy the global objects into the form. */
private fun updateWindow() {
    val m = Globals.motor
    val a = Globals.algo
    input("description").value = m.description
    input("syncSpeed").value = pyRepr(m.syncSpeed)
    input("ratedSpeed").value = pyRepr(m.ratedSpeed)
    input("ratedPf").value = pyRepr(m.ratedPf)
    input("ratedEff").value = pyRepr(m.ratedEff)
    input("tb").value = pyRepr(m.tB)
    input("tlr").value = pyRepr(m.tLr)
    input("ilr").value = pyRepr(m.iLr)
    input("maxIter").value = a.maxIter.toString()
    input("convErr").value = pyRepr(a.convErr)
    input("kr").value = pyRepr(a.kR)
    input("kx").value = pyRepr(a.kX)
    input("nGen").value = a.nGen.toString()
    input("pop").value = a.pop.toString()
    input("nR").value = a.nR.toString()
    input("nE").value = a.nE.toString()
    input("cF").value = pyRepr(a.cF)
}

private fun isSingle() = select("model").selectedIndex == 0

/** update_algo(): show GA fields or the linear-constraint fields. */
private fun updateAlgo() {
    val ga = select("algo").value in GA_ALGOS
    el("grpK").hidden = ga
    el("grpGa").hidden = !ga
}

/** update_model(): single vs double cage. */
private fun updateModel() {
    val single = isSingle()
    el("circuit").innerHTML = Circuit.svg(!single)
    val names = if (single) ALGOS_SINGLE else ALGOS_DOUBLE
    select("algo").innerHTML = names.joinToString("") { "<option>$it</option>" }
    el("lblXr2").hidden = single
    el("lblRr2").hidden = single
    input("Xr2").hidden = single
    input("Rr2").hidden = single
    updateAlgo()
}

private fun validateGa(a: AlgoData) {
    if (a.pop < 1) throw InputError("Members in population must be at least 1.")
    if (a.nR < 1 || a.nR > a.pop) throw InputError("Members in mating pool must be between 1 and the population size.")
    if (a.nE < 0 || a.nE > a.pop) throw InputError("Elite children must be between 0 and the population size.")
    if (a.cF < 0.0 || a.cF > 1.0) throw InputError("Crossover fraction must be between 0 and 1.")
}

/** calculate(): run the selected estimator. */
private suspend fun calculate() {
    updateData()
    status("Calculating...")
    val m = Globals.motor
    val a = Globals.algo
    val sf = (m.syncSpeed - m.ratedSpeed) / m.syncSpeed
    val single = isSingle()
    val yielder = Yielder()

    val res: SolverResult
    if (single) {
        val p = doubleArrayOf(sf, m.ratedEff, m.ratedPf, m.tB)
        res = nrSolverSc(p, 0, a.kX, a.kR, a.maxIter, a.convErr)
    } else {
        val p = doubleArrayOf(sf, m.ratedEff, m.ratedPf, m.tB, m.tLr, m.iLr)
        val alg = select("algo").value
        if (alg in GA_ALGOS) validateGa(a)
        res = when (alg) {
            "Newton-Raphson" -> nrSolver(p, 0, a.kX, a.kR, a.maxIter, a.convErr)
            "Levenberg-Marquardt" -> lmSolver(p, 0, a.kX, a.kR, 1e-7, 5.0, a.maxIter, a.convErr)
            "Damped Newton-Raphson" -> dnrSolver(p, 0, a.kX, a.kR, 1e-7, a.maxIter, a.convErr)
            "Genetic Algorithm" -> gaSolver(p, a.pop, a.nR, a.nE, a.cF, a.nGen, a.convErr, ::status, yielder)
            "Hybrid GA-NR" -> hySolver("NR", p, a.pop, a.nR, a.nE, a.cF, a.nGen, a.convErr, a, ::status, yielder)
            "Hybrid GA-LM" -> hySolver("LM", p, a.pop, a.nR, a.nE, a.cF, a.nGen, a.convErr, a, ::status, yielder)
            "Hybrid GA-DNR" -> hySolver("DNR", p, a.pop, a.nR, a.nE, a.cF, a.nGen, a.convErr, a, ::status, yielder)
            else -> throw InputError("Unknown algorithm: $alg")
        }
    }

    val z = res.z
    fun r5(v: Double) = pyRepr(npRound(v, 5))
    input("Rs").value = r5(z[0])
    input("Xs").value = r5(z[1])
    input("Xm").value = r5(z[2])
    input("Rr1").value = r5(z[3])
    if (!single) {
        input("Xr1").value = r5(z[4])
        input("Rr2").value = r5(z[5])
        input("Xr2").value = r5(z[6])
        input("Rc").value = r5(z[7])
    } else {
        input("Rc").value = r5(z[4])
        input("Xr1").value = r5(z[5])
    }

    if (res.conv == 1) {
        input("conv").value = "Yes"
    } else {
        window.alert("Algorithm did not converge.")
        input("conv").value = "No"
    }
    input("sqErr").value = pyRepr(npRound(res.err, 9))
    input("iters").value = res.iter.toString()

    button("plotBtn").disabled = !(res.err < 1)
    status("Ready")
}

/** plot_curves(): torque-speed and current-speed curves from the (editable) result fields. */
private fun plotCurves() {
    val m = Globals.motor
    val single = isSingle()
    val sf = (m.syncSpeed - m.ratedSpeed) / m.syncSpeed
    val x = if (single) {
        doubleArrayOf(num("Rs", "R_s"), num("Xs", "X_s"), num("Xm", "X_m"), num("Rr1", "R_r1"), num("Rc", "R_c"), num("Xr1", "X_r1"))
    } else {
        doubleArrayOf(
            num("Rs", "R_s"), num("Xs", "X_s"), num("Xm", "X_m"), num("Rr1", "R_r1"),
            num("Xr1", "X_r1"), num("Rr2", "R_r2"), num("Xr2", "X_r2"), num("Rc", "R_c"),
        )
    }
    val tRtd = m.ratedEff * m.ratedPf / (1 - sf)

    val tm = DoubleArray(1001)
    val im = DoubleArray(1001)
    val speed = DoubleArray(1001)
    speed[1000] = m.syncSpeed
    for (n in 0 until 1000) {
        speed[n] = n.toDouble() / 1000 * m.syncSpeed
        val i = 1 - n.toDouble() / 1000
        val (ti, ii) = if (single) getTorqueSc(i, x) else getTorque(i, x)
        tm[n] = ti / tRtd
        im[n] = ii.abs()
    }
    el("plotSvg").innerHTML = Plotter.render(speed, tm, im, m.syncSpeed)
    el("plotPanel").hidden = false
}

private fun guarded(action: () -> Unit) {
    try { action() } catch (e: InputError) { window.alert(e.message ?: "Invalid input"); status("Ready") }
}

fun main() {
    Globals.init()
    updateWindow()
    select("model").selectedIndex = 1
    updateModel()
    status("Ready")

    // Editing fields -> globals (editingFinished)
    for (id in listOf("description", "syncSpeed", "ratedSpeed", "ratedPf", "ratedEff", "tb", "tlr", "ilr",
        "maxIter", "convErr", "kr", "kx", "nGen", "pop", "nR", "nE", "cF")) {
        input(id).addEventListener("change", { _: Event -> guarded { updateData() } })
    }
    select("algo").addEventListener("change", { _: Event -> updateAlgo() })
    select("model").addEventListener("change", { _: Event -> updateModel() })

    button("calcBtn").addEventListener("click", { _: Event ->
        val calc = button("calcBtn")
        calc.disabled = true
        scope.launch {
            try {
                calculate()
            } catch (e: InputError) {
                window.alert(e.message ?: "Invalid input")
                status("Ready")
            } finally {
                calc.disabled = false
            }
        }
    })
    button("plotBtn").addEventListener("click", { _: Event -> guarded { plotCurves() } })
    button("closePlot").addEventListener("click", { _: Event -> el("plotPanel").hidden = true })

    // File menu
    button("saveBtn").addEventListener("click", { _: Event ->
        guarded { updateData(); downloadText("motor.mto", SaveLoad.save()) }
    })
    button("openBtn").addEventListener("click", { _: Event -> input("fileInput").click() })
    input("fileInput").addEventListener("change", { _: Event ->
        readSelectedFile(input("fileInput")) { text ->
            guarded { SaveLoad.load(text.toString()); updateWindow() }
        }
    })
    button("aboutBtn").addEventListener("click", { _: Event ->
        window.alert(
            "Moto is a parameter estimation tool that can be used to determine the equivalent circuit " +
                "parameters of induction machines. The tool is intended for use in dynamic time-domain " +
                "simulations such as stability and motor starting studies.\n\nVersion: v0.2\n" +
                "Website: www.sigmapower.com.au/moto.html\n\n(c) 2014 Sigma Power Engineering Pty Ltd\nAll rights reserved."
        )
    })
}
