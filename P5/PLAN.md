# P5 Development Plan

## Current State

- Model layer exists with immutable game configuration/state, keypad validation, and memoized minimax solver.
- Source tests cover keypad move generation, the statement example, two-player solving, N-player solving, memoization, deterministic random keypads, and replay termination.
- `Main` launches a Swing GUI with automatic recalculation, standard/random keypad modes, structured results, and optimal-play replay navigation.

## Remaining Work

1. Add optional manual keypad editing if time permits.
2. Add optional result details: total possible game paths if implemented separately from minimax.
3. Extend tests around any path-counting or manual-layout logic added later.
4. Complete `memoria/memoria.tex` in Catalan with problem description, recurrence, complexity, MVC design, screenshots, tests, and conclusions.

## Suggested Architecture

- `model`: `Keypad`, `KeypadMode`, `GameConfig`, `GameState`, `GameInput`, `GameResult`, `ReplayStep`, `MinimaxSolver`, plus optional `GameAnalysis` result record.
- `controller`: `GameController` owns a `GameView`, implements `GameViewListener`, converts form input into model objects, and sends display-ready results back to the view.
- `view`: Swing classes only; no game rules, minimax logic, or concrete controller reference in the UI.

## Verification Checklist

- Run `mvn test` from `P5/` after model/controller changes.
- Run `mvn clean compile` from `P5/` before delivery.
- Run the GUI manually with representative configurations: statement example, first move with `lastPlayed = 0`, custom dimensions `[2x2]` and `[5x5]`, invalid inputs, and `N > 2` players.
- Compile the report twice with `pdflatex memoria.tex && pdflatex memoria.tex` from `P5/memoria/`.
