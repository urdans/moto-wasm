package moto

import kotlin.math.abs

/** GET_TORQUE - double cage torque and stator current. x = [Rs Xs Xm Rr1 Xr1 Rr2 Xr2] */
fun getTorque(slip: Double, x: DoubleArray): Pair<Double, Cx> {
    val ys = cx(1.0) / cx(x[0], x[1])
    val ym = cx(1.0) / cx(0.0, x[2])
    val yr1 = cx(1.0) / cx(x[3] / slip, x[4])
    val yr2 = cx(1.0) / cx(x[5] / slip, x[6])

    val u1 = ys / (ys + ym + yr1 + yr2)
    val ir1 = (u1 * yr1).abs()
    val ir2 = (u1 * yr2).abs()

    val torque = x[3] / slip * (ir1 * ir1) + x[5] / slip * (ir2 * ir2)
    val ist = (cx(1.0) - u1) * ys
    return Pair(torque, ist)
}

/** CALC_PQT - returns [Pm Q Tb Tlr |Ilr| eff]. x = [Rs Xs Xm Rr1 Xr1 Rr2 Xr2 Rc] */
fun calcPqt(sf: Double, xIn: DoubleArray): DoubleArray {
    val x = DoubleArray(xIn.size) { abs(xIn[it]) }

    val (tFl, iS) = getTorque(sf, x)
    val pm = tFl * (1 - sf)
    val sn = cx(1.0) * iS.conj()
    val qFl = abs(sn.im)

    val iC = cx(1.0) / cx(x[7], 0.0)
    val iIn = iS + iC
    val pIn = (cx(1.0) * iIn.conj()).re
    val effFl = pm / pIn

    var tB = 0.0
    for (n in 1..100) {
        val i = n.toDouble() / 100
        val (tI, _) = getTorque(i, x)
        if (tI > tB) tB = tI
    }

    val (tLr, iLr) = getTorque(1.0, x)
    return doubleArrayOf(pm, qFl, tB, tLr, (iLr + iC).abs(), effFl)
}

/** GET_TORQUE_SC - single cage. x = [Rs Xs Xm Rr1 Rc Xr1]  (note: index 5 is Xr1) */
fun getTorqueSc(slip: Double, x: DoubleArray): Pair<Double, Cx> {
    val ys = cx(1.0) / cx(x[0], x[1])
    val ym = cx(1.0) / cx(0.0, x[2])
    val yr1 = cx(1.0) / cx(x[3] / slip, x[5])

    val u1 = ys / (ys + ym + yr1)
    val t = yr1 * u1
    val torque = abs(x[3] / slip * (t * t).abs())
    val ist = (cx(1.0) - u1) * ys
    return Pair(torque, ist)
}

/** CALC_PQT_SC - returns [Pm Q Tb eff]. x = [Rs Xs Xm Rr1 Rc Xr1] */
fun calcPqtSc(sf: Double, xIn: DoubleArray): DoubleArray {
    val x = DoubleArray(xIn.size) { abs(xIn[it]) }

    val (tFl, iS) = getTorqueSc(sf, x)
    val pm = tFl * (1 - sf)
    val sn = cx(1.0) * iS.conj()
    val qFl = abs(sn.im)

    val iC = cx(1.0) / cx(x[4], 0.0)
    val iIn = iS + iC
    val pIn = (cx(1.0) * iIn.conj()).re
    val effFl = pm / pIn

    var tB = 0.0
    for (n in 1..100) {
        val i = n.toDouble() / 100
        val (tI, _) = getTorqueSc(i, x)
        if (tI > tB) tB = tI
    }
    return doubleArrayOf(pm, qFl, tB, effFl)
}
