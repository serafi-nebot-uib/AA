# P5 Development Plan

## Current State

- Model layer exists with immutable game configuration/state, keypad validation, shared `Solver` API, top-down DP solver, and bottom-up DP solver.
- `Main` launches a Swing GUI with automatic recalculation, standard/random keypad modes, top-down/bottom-up DP solver selection, structured results, and optimal-play replay navigation.
- `BenchmarkMain` and `analysis/SolverBenchmark` generate CSV data for comparing solver CPU time, wall time, heap trend, and computed state counts.
- `memoria/memoria.tex` contains the Catalan report with problem statement, recurrence, complexity, MVC design, benchmark methodology, solver comparison tables, discussion, graphs, and conclusions.
- No unit-test suite is included in the final project; verification is done with compilation, report generation, benchmark runs, and representative manual GUI checks.

## Remaining Work

1. Add optional manual keypad editing if time permits.
2. Add optional result details: total possible game paths if implemented separately from winner computation.
3. Optionally add GUI screenshots to the report if required by the instructor.
4. Optionally extend the benchmark with larger limits or more players.

## Suggested Architecture

- `model`: `Keypad`, `KeypadMode`, `SolverMode`, `GameConfig`, `GameState`, `GameInput`, `GameResult`, `ReplayStep`, `Solver`, `TopDownDPSolver`, `BottomUpDPSolver`, plus optional `GameAnalysis` result record.
- `controller`: `GameController` owns a `GameView`, implements `GameViewListener`, converts form input into model objects, and sends display-ready results back to the view.
- `view`: Swing classes only; no game rules, solver logic, or concrete controller reference in the UI.
- `analysis`: `SolverBenchmark` and `SolverMeasurement` provide report-only measurements without coupling the GUI to benchmarking code.

## Verification Checklist

- Run `mvn clean compile` from `P5/` before delivery.
- Run the GUI manually with representative configurations: statement example, first move with `lastPlayed = 0`, custom dimensions `[2x2]` and `[5x5]`, invalid inputs, `N > 2` players, and both solver modes.
- Run `mvn exec:java -Dexec.mainClass="com.serafinebot.p5.BenchmarkMain"` from `P5/` to produce solver-comparison CSV output.
- Compile the report twice with `pdflatex memoria.tex && pdflatex memoria.tex` from `P5/memoria/`.
