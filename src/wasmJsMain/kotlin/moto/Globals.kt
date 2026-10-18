package moto

class MotorData(
    var description: String = "Weg 460V 150HP",
    var syncSpeed: Double = 3600.0,
    var ratedSpeed: Double = 3550.0,
    var ratedPf: Double = 0.88,
    var ratedEff: Double = 0.936,
    var tB: Double = 2.2,
    var tLr: Double = 2.0,
    var iLr: Double = 1106.0 / 168.0,
)

class AlgoData(
    var maxIter: Int = 30,
    var kR: Double = 1.0,
    var kX: Double = 0.5,
    var convErr: Double = 0.017,
    var nGen: Int = 30,
    var pop: Int = 20,
    var nR: Int = 15,
    var nE: Int = 2,
    var cF: Double = 0.8,
)

object Globals {
    var motor = MotorData()
    var algo = AlgoData()
    fun init() { motor = MotorData(); algo = AlgoData() }
}
