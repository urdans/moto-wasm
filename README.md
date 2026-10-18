# Moto (Kotlin/Wasm)

Web port of Julius Susanto's *Moto* induction-motor parameter estimation tool
(original: Python + PyQt5 + NumPy/matplotlib). Written in Kotlin, compiled to WebAssembly (Kotlin/Wasm).

## Run

Requires JDK 17+ and a Wasm-GC capable browser (current Chrome/Edge/Firefox/Safari).

    gradle wrapper            # once, if you don't have gradlew yet (or just open in IntelliJ IDEA)
    ./gradlew wasmJsBrowserDevelopmentRun      # dev server with live reload
    ./gradlew wasmJsBrowserDistribution        # static site in build/dist/wasmJs/productionExecutable

Serve the contents of `build/dist/wasmJs/productionExecutable` from any static web server.

## Source map

| Python            | Kotlin (`src/wasmJsMain/kotlin/moto`) |
|-------------------|----------------------------------------|
| `common_calcs.py` | `CommonCalcs.kt` (+ `Numerics.kt`: complex, det, inverse, RNG) |
| `descent.py`      | `Descent.kt` (NR, LM, DNR, single-cage NR) |
| `genetic.py`      | `Genetic.kt` |
| `hybrid.py`       | `Hybrid.kt` |
| `globals.py`      | `Globals.kt` |
| `saveload.py`     | `SaveLoad.kt` (.mto files, same `key;value` format) |
| `main.py` (GUI)   | `Main.kt` + `resources/index.html`, `Plot.kt` |
