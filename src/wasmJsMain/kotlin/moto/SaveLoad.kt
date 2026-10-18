package moto

class InputError(msg: String) : Exception(msg)

object SaveLoad {
    /** Parses the ';'-separated .mto format into Globals (same keys as the original). */
    fun load(text: String) {
        val m = Globals.motor
        val a = Globals.algo
        for (raw in text.lines()) {
            val line = raw.trimEnd('\r', '\n')
            if (line.isEmpty()) continue
            val cut = line.indexOf(';')
            if (cut < 0) throw InputError("Bad line in file: '$line'")
            val key = line.substring(0, cut)
            val item = line.substring(cut + 1)
            fun d() = item.trim().toDoubleOrNull() ?: throw InputError("Bad number for '$key': '$item'")
            fun i() = item.trim().toIntOrNull() ?: throw InputError("Bad integer for '$key': '$item'")
            when (key) {
                "description" -> m.description = item
                "sync_speed" -> m.syncSpeed = d()
                "rated_speed" -> m.ratedSpeed = d()
                "rated_pf" -> m.ratedPf = d()
                "rated_eff" -> m.ratedEff = d()
                "T_b" -> m.tB = d()
                "T_lr" -> m.tLr = d()
                "I_lr" -> m.iLr = d()
                "max_iter" -> a.maxIter = i()
                "k_r" -> a.kR = d()
                "k_x" -> a.kX = d()
                "conv_err" -> a.convErr = d()
                "n_gen" -> a.nGen = i()
                "pop" -> a.pop = i()
                "n_r" -> a.nR = i()
                "n_e" -> a.nE = i()
                "c_f" -> a.cF = d()
            }
        }
    }

    fun save(): String {
        val m = Globals.motor
        val a = Globals.algo
        return buildString {
            append("description;${m.description}\n")
            append("sync_speed;${fmtF(m.syncSpeed)}\n")
            append("rated_speed;${fmtF(m.ratedSpeed)}\n")
            append("rated_pf;${fmtF(m.ratedPf)}\n")
            append("rated_eff;${fmtF(m.ratedEff)}\n")
            append("T_b;${fmtF(m.tB)}\n")
            append("T_lr;${fmtF(m.tLr)}\n")
            append("I_lr;${fmtF(m.iLr)}\n")
            append("max_iter;${a.maxIter}\n")
            append("k_r;${fmtF(a.kR)}\n")
            append("k_x;${fmtF(a.kX)}\n")
            append("conv_err;${fmtF(a.convErr)}\n")
            append("n_gen;${a.nGen}\n")
            append("pop;${a.pop}\n")
            append("n_r;${a.nR}\n")
            append("n_e;${a.nE}\n")
            append("c_f;${fmtF(a.cF)}\n")
        }
    }
}
