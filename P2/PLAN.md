# P2: Hamiltonian Path with Backtracking - Implementation Plan

**Course:** Advanced Algorithms (Algorismes Avançats)  
**Institution:** Universitat de les Illes Balears (UIB)  
**Academic Year:** 2025-2026  
**Date:** March 9, 2026  
**Version:** 1.1 (ENUM approach)

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Requirements](#requirements)
3. [Technical Specifications](#technical-specifications)
4. [Architecture](#architecture)
5. [File Structure](#file-structure)
6. [Implementation Phases](#implementation-phases)
7. [Component Specifications](#component-specifications)
8. [Testing Strategy](#testing-strategy)
9. [Time Estimation](#time-estimation)
10. [Final Checklist](#final-checklist)

---

## Project Overview

### Problem Statement

Implement a program that uses **recursive backtracking** to find a **Hamiltonian path** on a chess board, shared by **two pieces taking turns**. The constraint is that at no point during the path can either piece be in a position to capture the other.

### Key Features

**Mandatory:**
- ✅ Hamiltonian path with 2 pieces alternating turns
- ✅ Constraint: pieces cannot capture each other at any moment
- ✅ GUI with all configurable parameters
- ✅ 6 piece types: Knight, Queen, Rook, Bishop, Owl (custom), Dragon (custom)
- ✅ Execution time measurement and display
- ✅ Start/Stop controls from GUI
- ✅ Visual solution display

**Voluntary (to implement):**
- ✅ Step-by-step visualization (<<, >>)
- ✅ Automatic animation (Play button)
- ✅ **Execution time prediction**
- ✅ **Difficulty level estimation**

**Deferred:**
- 🎨 Dragon piece image (placeholder now, real image later)

### Design Decisions

1. **ENUM for pieces:** All pieces defined in single `PieceType` enum (cleaner, less boilerplate)
2. **Strict turn alternation:** Each move alternates between Piece 1 and Piece 2
3. **Warnsdorff's heuristic:** Sort moves by number of available exits (fewer = higher priority)
4. **SwingWorker threading:** Prevents blocking EDT, allows periodic updates and graceful stopping
5. **Exponential prediction model:** `T(n) = k * e^(α*n) * piece_complexity`
6. **Sequence number visualization:** Each visited cell shows its order number (1, 2, 3...)

### Language Policy

- **Code & Comments:** English
- **GUI Labels/Text:** Catalan
- **Documentation:** Catalan (memoria.tex)

---

## Requirements

### Mandatory Features

1. **Hamiltonian Path Algorithm**
   - Backtracking with recursion
   - Two pieces alternating turns
   - Visit all cells exactly once
   - Mutual non-capture constraint

2. **GUI Parameters (all configurable)**
   - Board size (rows × cols, 4-12)
   - Piece 1 type & initial position
   - Piece 2 type & initial position
   - Non-square boards supported (e.g., 6×8)

3. **Piece Selection**
   - Classical: Knight, Queen, Rook, Bishop
   - Custom: Owl (±2 cross movement), Dragon (extended L + King)

4. **Time Measurement**
   - Start time on algorithm begin
   - Real-time elapsed time display (updated every second)
   - Final time on completion

5. **Controls**
   - Start button (launches algorithm)
   - Stop button (graceful termination at any moment)
   - Reset button (clear board)

6. **Solution Visualization**
   - Display complete path on board
   - Show sequence numbers on cells

### Voluntary Features (Implemented)

7. **Step-by-Step Visualization**
   - Previous button (<<)
   - Next button (>>)
   - Current step highlighting

8. **Automatic Animation**
   - Play button
   - Configurable speed (optional)

9. **Time Prediction**
   - Estimated time before execution
   - Based on board size and piece types
   - Difficulty level indicator

### Non-Requirements

- ❌ Support for >2 pieces (not implemented)
- ❌ Multiplicative constants computation (basic prediction only)
- ❌ Save/load configurations
- ❌ Internationalization (only Catalan GUI)

---

## Technical Specifications

### Technology Stack

- **Language:** Java 16
- **Build Tool:** Maven
- **GUI Framework:** Swing
- **Concurrency:** SwingWorker
- **Design Pattern:** MVC (Model-View-Controller)

### Dependencies

None (pure Java + Swing)

### Performance Targets

| Board Size | Expected Time | Difficulty |
|------------|---------------|------------|
| 4×4 (16)   | < 1 second    | Very Easy  |
| 5×5 (25)   | < 10 seconds  | Easy       |
| 6×6 (36)   | < 1 minute    | Moderate   |
| 7×7 (49)   | < 5 minutes   | Difficult  |
| 8×8 (64)   | > 10 minutes  | Very Difficult |

**Note:** Times vary significantly based on piece types and initial positions.

---

## Architecture

### MVC Pattern

```
┌─────────────────────────────────────────────────────────────┐
│                         VIEW LAYER                          │
│  ┌────────────┐  ┌──────────────┐  ┌────────────────────┐  │
│  │ MainFrame  │  │ ConfigPanel  │  │ BoardPanel         │  │
│  │            │  │ ControlPanel │  │ StatsPanel         │  │
│  │            │  │ PieceIcon    │  │                    │  │
│  └────────────┘  └──────────────┘  └────────────────────┘  │
└─────────────────────────┬───────────────────────────────────┘
                          │ (user events)
┌─────────────────────────▼───────────────────────────────────┐
│                    CONTROLLER LAYER                         │
│  ┌────────────────┐  ┌────────────────────────────────┐    │
│  │ SolverController│ │ SolverTask (SwingWorker)      │    │
│  │                 │ │ TimePredictor                  │    │
│  └────────────────┘  └────────────────────────────────┘    │
└─────────────────────────┬───────────────────────────────────┘
                          │ (business logic calls)
┌─────────────────────────▼───────────────────────────────────┐
│                      MODEL LAYER                            │
│  ┌────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │ Board      │  │ Backtracking │  │ HamiltonianPath  │   │
│  │ Position   │  │ Solver       │  │ SolverMetrics    │   │
│  └────────────┘  └──────────────┘  └──────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              PIECE REPRESENTATION                    │  │
│  │  PieceType (ENUM)                                    │  │
│  │    ├─ KNIGHT                                         │  │
│  │    ├─ QUEEN                                          │  │
│  │    ├─ ROOK                                           │  │
│  │    ├─ BISHOP                                         │  │
│  │    ├─ OWL (custom)                                   │  │
│  │    └─ DRAGON (custom)                                │  │
│  │                                                       │  │
│  │  MovementType (ENUM)                                 │  │
│  │    ├─ STATIC                                         │  │
│  │    └─ CONTINUOUS                                     │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

#### Model
- **Board:** N×M grid management, visited cells tracking, move validation
- **Position:** Immutable coordinate representation (record class)
- **HamiltonianPath:** Solution path storage (sequence of positions)
- **BacktrackingSolver:** Core recursive backtracking algorithm
- **SolverMetrics:** Statistics collection (iterations, backtracks, time)
- **PieceType:** ENUM defining all 6 piece types with movements
- **MovementType:** ENUM for STATIC vs CONTINUOUS movement

#### Controller
- **SolverController:** Orchestrates solving process, manages state
- **SolverTask:** Background thread execution (SwingWorker)
- **TimePredictor:** Execution time estimation based on empirical model

#### View
- **MainFrame:** Main window container (BorderLayout)
- **ConfigPanel:** Configuration form (board size, pieces, positions)
- **BoardPanel:** Chess board visualization with sequence numbers
- **ControlPanel:** Action buttons (Start, Stop, Reset, <<, >>, Play)
- **StatsPanel:** Real-time statistics display
- **PieceIcon:** Reusable component for piece images

---

## File Structure

```
P2/
├── pom.xml                                    # Maven configuration
├── .gitignore                                 # Git ignore rules
├── PLAN.md                                    # This file
│
├── src/
│   └── main/
│       ├── java/com/serafinebot/p2/
│       │   │
│       │   ├── Main.java                     # [30 lines] Entry point
│       │   │
│       │   ├── model/
│       │   │   ├── Board.java                # [310 lines] Board logic
│       │   │   ├── Position.java             # [60 lines] Position record
│       │   │   ├── HamiltonianPath.java      # [80 lines] Solution path
│       │   │   ├── BacktrackingSolver.java   # [250 lines] Main algorithm
│       │   │   ├── SolverMetrics.java        # [90 lines] Statistics
│       │   │   ├── PieceType.java            # [120 lines] ⭐ ENUM for all pieces
│       │   │   └── MovementType.java         # [10 lines] ENUM for movement
│       │   │
│       │   ├── controller/
│       │   │   ├── SolverController.java     # [200 lines] Main controller
│       │   │   ├── SolverTask.java           # [150 lines] SwingWorker
│       │   │   └── TimePredictor.java        # [150 lines] Time estimation
│       │   │
│       │   └── view/
│       │       ├── MainFrame.java            # [180 lines] Main window
│       │       ├── ConfigPanel.java          # [350 lines] Configuration UI
│       │       ├── BoardPanel.java           # [280 lines] Board display
│       │       ├── ControlPanel.java         # [150 lines] Control buttons
│       │       ├── StatsPanel.java           # [180 lines] Statistics
│       │       └── PieceIcon.java            # [80 lines] Icon component
│       │
│       └── resources/
│           ├── bishop-black.png              # From PFinal
│           ├── bishop-white.png
│           ├── knight-black.png
│           ├── knight-white.png
│           ├── queen-black.png
│           ├── queen-white.png
│           ├── rook-black.png
│           ├── rook-white.png
│           ├── owl-black.png                 # From PFinal
│           ├── dragon-black.png              # PLACEHOLDER (text "D")
│           └── dragon-white.png              # PLACEHOLDER (text "D")
│
└── memoria/
    ├── memoria.tex                            # IEEE template report
    ├── memoria.pdf                            # Compiled PDF
    ├── IEEEtran.cls                          # IEEE document class
    └── figures/
        ├── gui-screenshot.png
        ├── backtracking-diagram.png
        ├── architecture-diagram.png
        └── results-graph.png
```

**Estimated total:** ~2,150 lines of Java code (350 lines saved vs class hierarchy!)

---

## Implementation Phases

### Phase 1: Setup (30 min)

**Tasks:**
1. ✅ Create P2 directory structure with Maven
2. Configure `pom.xml` (copy from P1, adapt groupId/artifactId)
3. Create package directories: `model`, `controller`, `view`
4. Create `src/main/resources` directory
5. Copy images from `/Users/serafi/Documents/uib/CS/Y2/ALG/PFinal/PFinal/resources/`
6. Create placeholder images for Dragon (text "D")
7. Copy `.gitignore` from P1
8. Create skeleton `Main.java`

**Verification:** `mvn compile` succeeds without errors

**Deliverables:**
- Complete directory structure
- Compiled empty project

---

### Phase 2: Model - PieceType ENUM (30 min) ⭐

**Tasks:**
9. Implement `MovementType.java` (enum)
   - Values: `STATIC`, `CONTINUOUS`

10. Implement `PieceType.java` (enum) with all 6 pieces:
    - KNIGHT: L-shaped movement (8 positions)
    - QUEEN: 8 directions continuous
    - ROOK: Horizontal/vertical continuous
    - BISHOP: Diagonal continuous
    - OWL: ±2 cross (4 positions)
    - DRAGON: Extended L (±3,±1) + King (16 total positions)
    
    Each enum constant defines:
    - Catalan name (for GUI)
    - Short name (abbreviation)
    - Movement type (STATIC/CONTINUOUS)
    - Movement vectors (int[][])
    - Image path generation method
    - Complexity factor (for time prediction)

11. Implement `Position.java` (record class)
    - Fields: `int row`, `int col`
    - Methods: `manhattanDistance()`, `isAdjacentTo()`
    - Validation in constructor

**Testing:** 
- Create simple test to verify piece movements
- Print all PieceType values
- Test `PieceType.values()` for ComboBox

**Deliverables:**
- `PieceType` enum with all 6 pieces
- `MovementType` enum
- `Position` record class

**Code savings:** ~350 lines vs class hierarchy! 🎉

---

### Phase 3: Model - Board (1h 30min)

**Tasks:**
12. Implement `Board.java`:
    - Constructor: `Board(int rows, int cols)`
    - Fields: `boolean[][] visited`, `int visitedCount`
    - Validation methods:
      - `isValidPosition(Position pos)`
      - `isVisited(Position pos)`
    - State management:
      - `markVisited(Position pos)`
      - `unmarkVisited(Position pos)`
    - Move generation:
      - `getValidMoves(Position from, PieceType piece)` for STATIC
      - `getValidMoves(Position from, PieceType piece)` for CONTINUOUS
    - Capture validation:
      - `canCapture(Position pos1, PieceType piece1, Position pos2, PieceType piece2)`
    - Utility:
      - `reset()`, `clone()`

**Testing:**
- Create 5×5 board
- Test mark/unmark operations
- Validate move generation for different PieceType values
- Test capture detection

**Deliverables:**
- Fully functional Board class
- Validated movement and capture logic

---

### Phase 4: Model - Path & Metrics (45min)

**Tasks:**
13. Implement `HamiltonianPath.java`:
    - Fields: `List<Position> positions`, `List<Integer> pieceIndices`
    - Methods: `addMove()`, `removeLastMove()`, `getPosition()`, `getPieceAtStep()`, `getLength()`, `clear()`

14. Implement `SolverMetrics.java`:
    - Fields: `long iterations`, `long backtracks`, `int maxDepth`, `long startTime`, `long endTime`, `boolean solutionFound`
    - Methods: `startTimer()`, `stopTimer()`, `getElapsedTimeMillis()`, `incrementIterations()`, `incrementBacktracks()`, `updateMaxDepth()`, `reset()`

**Testing:**
- Create instances
- Add data
- Verify getters

**Deliverables:**
- HamiltonianPath class
- SolverMetrics class

---

### Phase 5: Model - BacktrackingSolver (2h)

**Tasks:**
15. Implement `BacktrackingSolver.java` structure:
    - Fields: `Board`, `PieceType` (×2), `Position` (×2), `HamiltonianPath`, `SolverMetrics`, `volatile boolean stopped`
    - Constructor

16. Implement `solve()` method:
    - Reset board and solution
    - Start timer
    - Mark initial positions
    - Call recursive backtracking
    - Stop timer
    - Return success/failure

17. Implement recursive `backtrack()` method:
    - **Base case:** All cells visited → return true
    - **Stop check:** If stopped → return false
    - **Metrics:** Increment iterations, update max depth
    - **Progress callback:** Every 1000 iterations
    - **Move generation:** Get valid moves for current piece
    - **Heuristic:** Sort by Warnsdorff's rule (fewer exits first)
    - **Validation loop:**
      - For each potential move:
        - Check: new position doesn't capture other piece
        - Check: other piece doesn't capture new position
        - Apply move (mark visited, add to path)
        - **Recursion:** Call backtrack with pieces swapped
        - If success → return true
        - **Backtrack:** Unmark visited, remove from path
    - Return false if no valid moves

18. Implement `stop()` method

**Algorithm pseudocode:**
```
backtrack(currentPos, currentPiece, currentIndex, otherPos, otherPiece, otherIndex, depth):
    if board.visitedCount == board.totalCells:
        return true  // Solution found!
    
    if stopped:
        return false
    
    metrics.incrementIterations()
    metrics.updateMaxDepth(depth)
    
    validMoves = board.getValidMoves(currentPos, currentPiece)
    
    // Warnsdorff's heuristic: sort by number of exits
    sort validMoves by board.getValidMoves(move, currentPiece).size()
    
    for each nextPos in validMoves:
        // Validate: mutual non-capture
        if board.canCapture(nextPos, currentPiece, otherPos, otherPiece):
            continue
        if board.canCapture(otherPos, otherPiece, nextPos, currentPiece):
            continue
        
        // Apply move
        board.markVisited(nextPos)
        solution.addMove(nextPos, currentIndex)
        
        // Recurse with swapped pieces (alternate turn)
        if backtrack(otherPos, otherPiece, otherIndex, nextPos, currentPiece, currentIndex, depth+1):
            return true
        
        // Backtrack
        metrics.incrementBacktracks()
        board.unmarkVisited(nextPos)
        solution.removeLastMove()
    
    return false
```

**Testing (CRITICAL):**
- Test 4×4 board with 2 Knights
- Test 5×5 board with Knight + Rook
- Verify solution correctness (all cells visited, no captures)
- Verify `stop()` works gracefully

**Deliverables:**
- Fully functional backtracking solver
- Validated on small boards

---

### Phase 6: Controller - TimePredictor (1h 30min)

**Tasks:**
19. Implement `TimePredictor.java`:
    - Constants: `BASE_CONSTANT = 0.001`, `GROWTH_FACTOR = 0.35`
    - Implement `predictTime(rows, cols, piece1, piece2)`:
      - Formula: `T(n) = k * e^(α*n) * (complexity1 + complexity2) / 2`
      - Where n = rows × cols
      - Use `piece1.getComplexity()` and `piece2.getComplexity()`
    - Implement `predictTimeFormatted(...)`:
      - Format: "< 1 segon", "X.X segons", "X.X minuts", "X.X hores"
    - Implement `getDifficultyLevel(...)`:
      - Levels: "Molt fàcil", "Fàcil", "Moderat", "Difícil", "Molt difícil"

20. **CALIBRATION:** Run tests and adjust constants
    - Create calibration table:
      ```
      | Board  | Pieces          | Real Time | Predicted | Error |
      |--------|-----------------|-----------|-----------|-------|
      | 4×4    | KNIGHT+KNIGHT   | ???       | ???       | ???   |
      | 5×5    | KNIGHT+KNIGHT   | ???       | ???       | ???   |
      | 6×6    | KNIGHT+ROOK     | ???       | ???       | ???   |
      ```
    - Adjust `BASE_CONSTANT` and `GROWTH_FACTOR` to minimize error

**Testing:**
- Run solver on known configurations
- Measure actual times
- Compare with predictions
- Iterate until reasonable accuracy

**Deliverables:**
- TimePredictor class
- Calibrated constants
- Calibration data table (for memoria)

---

### Phase 7: Controller - SolverTask (1h)

**Tasks:**
21. Implement `SolverTask.java` (extends `SwingWorker<HamiltonianPath, Integer>`):
    - Constructor: takes solver, progress listener, metrics listener
    - Override `doInBackground()`:
      - Call `solver.solve()`
      - Return solution or null
    - Override `process(List<Integer> chunks)`:
      - Update GUI with latest progress value
      - Update metrics display
    - Override `done()`:
      - Get result with `get()`
      - Handle success/failure
    - Method `stopSolver()`:
      - Call `solver.stop()`
      - Cancel task

22. Configure solver callback:
    - Set progress callback: `solver.setProgressCallback(visited -> publish(visited))`

**Testing:**
- Create simple test with mock solver
- Verify progress updates
- Verify cancellation works

**Deliverables:**
- SolverTask class
- Verified threading behavior

---

### Phase 8: Controller - SolverController (1h)

**Tasks:**
23. Implement `SolverController.java`:
    - Fields: `Board board`, `BacktrackingSolver solver`, `SolverTask currentTask`, `MainFrame view`
    - Method `startSolving(Config config)`:
      - Create board from config
      - Create solver with pieces and positions
      - Create SolverTask
      - Execute task
    - Method `stopSolving()`:
      - Stop current task
      - Update GUI state
    - Callbacks:
      - `onSolutionFound(HamiltonianPath path)`
      - `onSolutionNotFound()`
      - `updateProgress(int visitedCells)`

24. Integrate with model

**Testing:**
- Test full cycle: start → execute → stop
- Test completion handling

**Deliverables:**
- SolverController class
- Integrated model + controller

---

### Phase 9: View - Basic Components (1h)

**Tasks:**
25. Implement `PieceIcon.java`:
    - Copy from PFinal
    - Adapt image paths to use `getClass().getResourceAsStream()`
    - JPanel that renders scaled image with padding
    - Handle missing images gracefully

**Testing:**
- Create simple JFrame
- Display all piece icons using `PieceType.values()`
- Verify images load correctly

**Deliverables:**
- PieceIcon component
- Verified image loading

---

### Phase 10: View - ConfigPanel (2h 30min)

**Tasks:**
26. Create layout with `GridBagLayout`:
    ```
    Row 0: Label "Mida del tauler"
    Row 1: Label "Files:" + JSpinner (4-12, default 8)
    Row 2: Label "Columnes:" + JSpinner (4-12, default 8)
    Row 3: Separator
    Row 4: Label "Peça 1"
    Row 5: Label "Tipus:" + JComboBox<PieceType> (auto-populated from enum!)
    Row 6: PieceIcon for selected piece
    Row 7: Label "Posició inicial:" + JSpinner (Row) + JSpinner (Col)
    Row 8: Separator
    Row 9: Label "Peça 2"
    Row 10: Label "Tipus:" + JComboBox<PieceType>
    Row 11: PieceIcon for selected piece
    Row 12: Label "Posició inicial:" + JSpinner (Row) + JSpinner (Col)
    Row 13: Separator
    Row 14: Label "Predicció de temps"
    Row 15: Label "Temps estimat:" + Dynamic label
    Row 16: Label "Dificultat:" + Dynamic label
    ```

27. Add listeners:
    - Board size change → update position spinner limits
    - Piece selection change → update icon + recalculate prediction
    - Position change → recalculate prediction
    - Any parameter change → validate and update prediction

28. Implement validation:
    - Initial positions within board bounds
    - Initial positions different from each other
    - Show error dialog if invalid

29. Implement `getConfig()` method:
    - Returns configuration object with all parameters

30. Catalan labels:
    - "Mida del tauler"
    - "Files", "Columnes"
    - "Peça 1", "Peça 2"
    - "Tipus"
    - "Posició inicial"
    - "Predicció de temps"
    - "Temps estimat"
    - "Dificultat"

**ComboBox setup (super easy with enum!):**
```java
JComboBox<PieceType> piece1Combo = new JComboBox<>(PieceType.values());
// Automatically uses PieceType.toString() which returns Catalan name!
```

**Testing:**
- Test all controls
- Verify validation logic
- Test prediction updates
- Verify ComboBox shows Catalan names

**Deliverables:**
- ConfigPanel class
- Complete configuration form

---

### Phase 11: View - BoardPanel (2h)

**Tasks:**
31. Implement base structure:
    - Extends JPanel
    - Uses GridLayout (rows × cols)
    - Dynamic resizing based on board dimensions

32. Create inner class `CellPanel extends JPanel`:
    - Fields: `int row`, `int col`, `boolean visited`, `int sequenceNumber`, `boolean highlighted`, `PieceType piece`
    - Override `paintComponent(Graphics g)`:
      - Draw background (checkerboard pattern):
        - Light: `Color.WHITE` (#FFFFFF)
        - Dark: `new Color(139, 69, 19)` (brown)
        - Visited light: `new Color(144, 238, 144)` (light green)
        - Visited dark: `new Color(34, 139, 34)` (dark green)
      - Draw sequence number (if visited):
        - Font: small, top-left corner
        - Color: black
      - Draw piece icon (if present):
        - Load using `piece.getImagePath(false)`
        - Centered, scaled to fit
      - Draw highlight border (if current step):
        - Color: yellow, thick border

33. Implement public methods:
    - `updateBoard(HamiltonianPath path, int currentStep)`:
      - Mark all cells up to currentStep as visited
      - Set sequence numbers
      - Highlight cell at currentStep
      - Place pieces at their positions in that step
      - Repaint
    - `showFullSolution(HamiltonianPath path)`:
      - Mark all cells as visited
      - Set all sequence numbers
      - No highlight
      - Show final piece positions
      - Repaint
    - `reset()`:
      - Clear all visited marks
      - Clear sequence numbers
      - Clear highlight
      - Remove pieces
      - Repaint
    - `setBoard(Board board)`:
      - Recreate grid with new dimensions
      - Initialize cells

**Testing:**
- Create mock solution
- Test `updateBoard()` with different steps
- Test `showFullSolution()`
- Verify visual appearance

**Deliverables:**
- BoardPanel class
- Fully functional board visualization

---

### Phase 12: View - ControlPanel (1h)

**Tasks:**
34. Create buttons (Catalan):
    - "Iniciar" (Start) - JButton
    - "Aturar" (Stop) - JButton
    - "Reiniciar" (Reset) - JButton
    - "<<" (Previous) - JButton
    - ">>" (Next) - JButton
    - "Reproducir" (Play) - JButton

35. Layout: FlowLayout or BoxLayout horizontal

36. Add action listeners (empty for now, wire in Phase 15)

37. Implement enable/disable logic:
    - Method `setExecutionState(boolean executing)`:
      - If executing:
        - Disable: Iniciar, <<, >>, Reproducir
        - Enable: Aturar
      - If not executing:
        - Enable: Iniciar
        - Disable: Aturar
        - Enable <<, >>: only if solution exists
        - Enable Reproducir: only if solution exists
    - Method `setSolutionState(boolean hasSolution)`:
      - Enable/disable step navigation and play buttons

**Testing:**
- Test button states
- Verify layout

**Deliverables:**
- ControlPanel class
- All buttons with proper states

---

### Phase 13: View - StatsPanel (1h)

**Tasks:**
38. Create labels with values (Catalan):
    - "Temps transcorregut:" + dynamic value (mm:ss)
    - "Iteracions:" + dynamic value
    - "Backtracks:" + dynamic value
    - "Profunditat màxima:" + dynamic value
    - "Estat:" + dynamic text with color

39. Layout: GridLayout (5 rows, 2 columns) or GridBagLayout

40. Implement update methods:
    - `updateTime(long milliseconds)`:
      - Format as MM:SS or HH:MM:SS
      - Update label
    - `updateMetrics(SolverMetrics metrics)`:
      - Update iterations label
      - Update backtracks label
      - Update max depth label
    - `setState(String state, Color color)`:
      - Update state label text
      - Set foreground color
      - States: "Esperant", "Buscant...", "Solució trobada!", "No hi ha solució", "Aturat"

41. Implement Timer for real-time updates:
    - `javax.swing.Timer` that fires every second
    - Updates elapsed time label
    - Only active during execution

**Testing:**
- Test with mock metrics
- Verify timer updates

**Deliverables:**
- StatsPanel class
- Real-time statistics display

---

### Phase 14: View - MainFrame (1h 30min)

**Tasks:**
42. Create main window:
    - Extends JFrame
    - Title: "P2 - Recorregut Hamiltonià amb Backtracking"
    - Size: 1000×800
    - Default close operation: EXIT_ON_CLOSE
    - Resizable

43. Layout: BorderLayout
    - NORTH: ConfigPanel
    - CENTER: JScrollPane containing BoardPanel
    - SOUTH: Container panel with:
      - ControlPanel (left/center)
      - StatsPanel (right)

44. Initialize components:
    - Create all panels
    - Set up layout
    - Configure scroll pane

45. Implement communication methods:
    - `getConfiguration()` → delegate to ConfigPanel
    - `updateBoard(...)` → delegate to BoardPanel
    - `updateStats(...)` → delegate to StatsPanel
    - `setExecutionState(...)` → delegate to ControlPanel
    - Methods for controller to call

**Testing:**
- Launch window
- Verify layout
- Test resizing

**Deliverables:**
- MainFrame class
- Complete GUI structure

---

### Phase 15: Integration - Controller ↔ View (2h)

**Tasks:**
46. Wire ControlPanel buttons to SolverController:
    - "Iniciar" button:
      - Get configuration from ConfigPanel
      - Validate configuration
      - Call `controller.startSolving(config)`
      - Update button states
    - "Aturar" button:
      - Call `controller.stopSolving()`
      - Update button states
    - "Reiniciar" button:
      - Reset board
      - Clear solution
      - Reset stats
      - Update view

47. Wire controller callbacks to view:
    - On progress update:
      - Update StatsPanel with metrics
      - Update BoardPanel (optional: show progress)
    - On solution found:
      - Show solution in BoardPanel
      - Update state: "Solució trobada!"
      - Enable step navigation buttons
    - On solution not found:
      - Show message: "No hi ha solució"
      - Update state
    - On stopped:
      - Update state: "Aturat"

48. Test complete cycle:
    - Configure parameters
    - Click Iniciar
    - Watch progress
    - Solution displays
    - Stats update

**Testing (CRITICAL):**
- Full execution: config → start → complete → display
- Stop mid-execution
- Reset after solution
- Multiple executions

**Deliverables:**
- Fully integrated MVC application
- Working Start/Stop/Reset functionality

---

### Phase 16: Step-by-Step Visualization (2h)

**Tasks:**
49. Add state management to MainFrame:
    - Field: `int currentStep = 0`
    - Field: `HamiltonianPath currentSolution = null`

50. Implement navigation:
    - "<<" button listener:
      - `if (currentStep > 0):`
        - `currentStep--`
        - `boardPanel.updateBoard(currentSolution, currentStep)`
    - ">>" button listener:
      - `if (currentStep < currentSolution.getLength() - 1):`
        - `currentStep++`
        - `boardPanel.updateBoard(currentSolution, currentStep)`

51. Implement automatic animation:
    - "Reproducir" button toggles play/pause
    - When playing:
      - `Timer` (500ms interval)
      - Each tick: increment `currentStep`, update board
      - Stop when reaching end
      - Button text: "Pausar" during play, "Reproducir" when stopped

52. Optional: Add speed control
    - JSlider for animation speed (100ms - 2000ms)

**Testing:**
- Load solution
- Navigate with << >>
- Test play/pause
- Verify highlighting

**Deliverables:**
- Step-by-step navigation
- Automatic animation

---

### Phase 17: Main.java & Final Testing (1h)

**Tasks:**
53. Implement `Main.java`:
    ```java
    public class Main {
        public static void main(String[] args) {
            SwingUtilities.invokeLater(() -> {
                MainFrame frame = new MainFrame();
                frame.setVisible(true);
            });
        }
    }
    ```

54. Comprehensive testing:
    - Small boards (4×4, 5×5)
    - Large boards (7×7, 8×8)
    - Rectangular boards (6×8, 5×7)
    - All piece combinations using PieceType.values():
      - KNIGHT + KNIGHT
      - KNIGHT + ROOK
      - QUEEN + BISHOP
      - OWL + DRAGON
      - etc.
    - Edge cases:
      - Initial positions at corners
      - Initial positions adjacent
      - Very small board (4×4)

55. Verify:
    - Predictions are reasonable
    - Stop works correctly
    - GUI doesn't freeze
    - Solutions are valid (no captures, all cells visited)

**Deliverables:**
- Main class
- Validated application

---

### Phase 18: Polish & Error Handling (1h 30min)

**Tasks:**
56. Add error handling:
    - Try-catch for image loading (use `PieceType.getImagePath()`)
    - Validation with error dialogs
    - Graceful handling of missing resources
    - Optional timeout (e.g., 10 minutes)

57. Improve UX:
    - Add tooltips to all controls:
      - "Mida del tauler: Files i columnes (4-12)"
      - "Selecciona el tipus de peça"
      - etc.
    - Add informative messages:
      - JOptionPane on solution found
      - JOptionPane on no solution
    - Optional: Progress bar during execution

58. Optimize performance:
    - Cache loaded images
    - Selective repaint (only changed cells)
    - Consider double buffering if flicker occurs

**Testing:**
- Test with missing images
- Test with invalid inputs
- Test long-running executions

**Deliverables:**
- Polished application
- Robust error handling

---

### Phase 19: Code Documentation (1h)

**Tasks:**
59. Add Javadoc to all public classes:
    - Class-level documentation
    - Constructor documentation
    - Method documentation with @param, @return, @throws

60. Add inline comments:
    - Complex algorithms (backtracking)
    - Non-obvious logic
    - Important invariants

61. Create README.md (optional but recommended):
    - Project description
    - How to compile: `mvn compile`
    - How to run: `mvn exec:java -Dexec.mainClass="com.serafinebot.p2.Main"`
    - Requirements: Java 16+, Maven

**Deliverables:**
- Fully documented code
- README.md

---

### Phase 20: Memoria (LaTeX Report) (4-6h)

**Tasks:**
62. Setup report structure:
    - Copy `ieee-template/IEEEtran.cls` to `P2/memoria/`
    - Copy `ieee-template/uib-aa-template.tex` to `P2/memoria/memoria.tex`
    - Create `figures/` subdirectory
    - Edit title, name, practice number

63. Capture screenshots:
    - Initial GUI (configuration panel)
    - GUI with solution (full board with sequence numbers)
    - GUI during step-by-step navigation (highlighted cell)
    - Statistics panel with metrics
    - Time prediction display

64. Create diagrams:
    - **Architecture diagram:**
      - MVC layers with components
      - Show PieceType ENUM
      - Use draw.io or similar
      - Export as PNG
    - **Backtracking flow diagram:**
      - Flowchart of recursive algorithm
      - Show decision points (capture check, base case, backtrack)
    - **PieceType ENUM diagram:**
      - Show all 6 enum constants
      - Show methods (getName(), getMovements(), etc.)

65. Write sections:

    **Introducció (1 page):**
    - Context: Hamiltonian path problem in chess
    - Objectives: Implement backtracking solver with GUI
    - Contributions: Two-piece alternation, mutual non-capture constraint

    **Fonaments Teòrics (2-3 pages):**
    - **Backtracking:**
      - Definition, characteristics
      - State space tree
      - Pruning strategies
    - **Hamiltonian Path:**
      - Definition (graph theory)
      - NP-complete complexity
      - Applications
    - **Computational Complexity:**
      - Time complexity: O(b^n) worst case
      - Space complexity: O(n) for recursion stack
      - Exponential growth analysis

    **Disseny i Implementació (4-5 pages):**
    - **Architecture:**
      - MVC pattern justification
      - Component diagram
      - Separation of concerns
    - **PieceType ENUM Design:**
      - Why ENUM instead of class hierarchy
      - Benefits: less boilerplate, type-safe, easy to extend
      - Movement types (STATIC vs CONTINUOUS)
      - All 6 piece definitions
      - Custom pieces (Owl, Dragon)
    - **Backtracking Algorithm:**
      - Pseudocode with explanation
      - Turn alternation mechanism
      - Mutual non-capture validation
      - State management (mark/unmark visited)
    - **Heuristics:**
      - Warnsdorff's rule
      - Impact on performance
    - **Time Prediction:**
      - Exponential model: T(n) = k * e^(α*n) * complexity
      - Piece complexity factors (from `PieceType.getComplexity()`)
      - Calibration methodology
    - **GUI Components:**
      - Configuration panel
      - Board visualization
      - Real-time statistics
      - Step-by-step controls

    **Resultats i Discussió (3-4 pages):**
    - **Execution time table:**
      ```
      | Board | Pieces          | Time (s) | Iterations | Backtracks |
      |-------|-----------------|----------|------------|------------|
      | 4×4   | KNIGHT+KNIGHT   | 0.523    | 1,247      | 856        |
      | 5×5   | KNIGHT+KNIGHT   | 8.341    | 18,532     | 12,409     |
      | 6×6   | KNIGHT+ROOK     | 47.218   | 124,863    | 89,234     |
      | ...   | ...             | ...      | ...        | ...        |
      ```
    - **Growth graph:**
      - X-axis: Board size (n = rows × cols)
      - Y-axis: Time (seconds, log scale)
      - Multiple curves for different piece combinations
    - **Prediction accuracy:**
      - Table comparing predicted vs actual times
      - Error percentage
      - Discussion of model limitations
    - **Complexity analysis:**
      - Empirical growth rate
      - Comparison with theoretical O(b^n)
    - **Screenshots with explanations:**
      - Fig. 1: Initial configuration
      - Fig. 2: Solution with sequence numbers
      - Fig. 3: Step-by-step visualization
      - Fig. 4: Statistics display

    **Conclusions (1 page):**
    - Summary of achievements:
      - Functional Hamiltonian path solver
      - All mandatory + voluntary features implemented
      - Accurate time prediction
      - Clean ENUM-based design
    - Challenges encountered:
      - Optimization of backtracking (pruning)
      - Calibration of prediction model
      - GUI responsiveness with threading
    - Future work:
      - Support for >2 pieces
      - Better heuristics (machine learning?)
      - Parallelization of search
      - Interactive board editing

    **Bibliografia:**
    - Cormen et al., Introduction to Algorithms
    - Sedgewick & Wayne, Algorithms
    - Knuth, The Art of Computer Programming
    - Online resources (Java Swing, backtracking tutorials)

66. Compile PDF:
    ```bash
    cd P2/memoria/
    pdflatex memoria.tex
    pdflatex memoria.tex  # Run twice for references
    ```

**Testing:**
- Verify all figures display correctly
- Check references
- Proofread text

**Deliverables:**
- Complete memoria.tex
- Compiled memoria.pdf
- All figures in figures/

---

## Component Specifications

### PieceType ENUM (Complete Implementation)

```java
package com.serafinebot.p2.model;

/**
 * Enum representing all available piece types with their movement patterns.
 * Each piece defines:
 * - Catalan name for GUI display
 * - Short name (abbreviation)
 * - Movement type (STATIC or CONTINUOUS)
 * - Movement vectors
 * - Complexity factor for time prediction
 */
public enum PieceType {
    
    KNIGHT("Cavall", "C", MovementType.STATIC, new int[][] {
        {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
        {1, -2}, {1, 2}, {2, -1}, {2, 1}
    }),
    
    QUEEN("Reina", "Q", MovementType.CONTINUOUS, new int[][] {
        {-1, -1}, {-1, 0}, {-1, 1},
        {0, -1},           {0, 1},
        {1, -1},  {1, 0},  {1, 1}
    }),
    
    ROOK("Torre", "T", MovementType.CONTINUOUS, new int[][] {
        {-1, 0}, {1, 0}, {0, -1}, {0, 1}
    }),
    
    BISHOP("Alfil", "A", MovementType.CONTINUOUS, new int[][] {
        {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
    }),
    
    OWL("Mussol", "M", MovementType.STATIC, new int[][] {
        {-2, 0}, {2, 0}, {0, -2}, {0, 2}
    }),
    
    DRAGON("Drac", "D", MovementType.STATIC, new int[][] {
        // Extended L (8 positions)
        {-3, -1}, {-3, 1}, {-1, -3}, {-1, 3},
        {1, -3}, {1, 3}, {3, -1}, {3, 1},
        // King movements (8 positions)
        {-1, -1}, {-1, 0}, {-1, 1},
        {0, -1},           {0, 1},
        {1, -1},  {1, 0},  {1, 1}
    });
    
    // Fields
    private final String name;           // Catalan name for GUI
    private final String shortName;      // Abbreviation (C, Q, T, A, M, D)
    private final MovementType movementType;
    private final int[][] movements;
    
    /**
     * Constructor for each enum constant.
     */
    PieceType(String name, String shortName, MovementType movementType, int[][] movements) {
        this.name = name;
        this.shortName = shortName;
        this.movementType = movementType;
        this.movements = movements;
    }
    
    // Getters
    
    /**
     * Get Catalan name for GUI display.
     * @return Catalan name (e.g., "Cavall", "Reina")
     */
    public String getName() { 
        return name; 
    }
    
    /**
     * Get short abbreviation.
     * @return Single letter (e.g., "C", "Q")
     */
    public String getShortName() { 
        return shortName; 
    }
    
    /**
     * Get movement type.
     * @return STATIC or CONTINUOUS
     */
    public MovementType getMovementType() { 
        return movementType; 
    }
    
    /**
     * Get movement vectors.
     * @return 2D array of [deltaRow, deltaCol] offsets
     */
    public int[][] getMovements() { 
        return movements; 
    }
    
    /**
     * Get image path for this piece.
     * @param isWhite true for white piece, false for black
     * @return resource path (e.g., "/resources/knight-white.png")
     */
    public String getImagePath(boolean isWhite) {
        String color = isWhite ? "white" : "black";
        // Use enum constant name in lowercase (KNIGHT -> knight)
        return String.format("/resources/%s-%s.png", 
                           name().toLowerCase(), color);
    }
    
    /**
     * Get complexity factor for time prediction.
     * Higher values indicate more backtracking expected.
     * @return complexity multiplier (0.7 to 1.5)
     */
    public double getComplexity() {
        return switch (this) {
            case KNIGHT -> 1.0;   // Baseline
            case ROOK -> 0.7;     // Fewer dead ends
            case BISHOP -> 0.8;   // Similar to Rook
            case QUEEN -> 1.5;    // Many options, more backtracking
            case OWL -> 1.2;      // Limited range
            case DRAGON -> 1.4;   // Powerful but controlled
        };
    }
    
    /**
     * String representation (Catalan name for GUI ComboBox).
     * @return Catalan name
     */
    @Override
    public String toString() {
        return name;  // Returns Catalan name for GUI ComboBox
    }
}
```

**Usage examples:**
```java
// In ConfigPanel:
JComboBox<PieceType> combo = new JComboBox<>(PieceType.values());
// Displays: Cavall, Reina, Torre, Alfil, Mussol, Drac

// Get selected piece:
PieceType selected = (PieceType) combo.getSelectedItem();

// In Board:
List<Position> moves = board.getValidMoves(pos, PieceType.KNIGHT);

// In TimePredictor:
double complexity = PieceType.DRAGON.getComplexity(); // Returns 1.4
```

---

### MovementType ENUM

```java
package com.serafinebot.p2.model;

/**
 * Type of movement for a piece.
 * STATIC: Single-step moves (Knight, King-like)
 * CONTINUOUS: Sliding moves until obstacle (Queen, Rook, Bishop)
 */
public enum MovementType {
    /**
     * Single-step movement (e.g., Knight, Owl, Dragon).
     * Each movement vector is applied once.
     */
    STATIC,
    
    /**
     * Continuous movement (e.g., Queen, Rook, Bishop).
     * Each movement vector is repeated until board edge or obstacle.
     */
    CONTINUOUS
}
```

---

### Position (Record Class)

```java
package com.serafinebot.p2.model;

/**
 * Immutable record representing a position on the board.
 * Automatically generates equals(), hashCode(), and toString().
 */
public record Position(int row, int col) {
    
    /**
     * Compact constructor with validation.
     */
    public Position {
        if (row < 0 || col < 0) {
            throw new IllegalArgumentException(
                "Position coordinates must be non-negative: (" + row + "," + col + ")"
            );
        }
    }
    
    /**
     * Calculate Manhattan distance to another position.
     * @param other The other position
     * @return Manhattan distance (|Δrow| + |Δcol|)
     */
    public int manhattanDistance(Position other) {
        return Math.abs(this.row - other.row) + Math.abs(this.col - other.col);
    }
    
    /**
     * Check if this position is adjacent (including diagonals) to another.
     * @param other The other position
     * @return true if adjacent (8 directions), false otherwise
     */
    public boolean isAdjacentTo(Position other) {
        int dr = Math.abs(this.row - other.row);
        int dc = Math.abs(this.col - other.col);
        return (dr <= 1 && dc <= 1) && (dr + dc > 0);
    }
}
```

---

### Board (Key Methods)

```java
/**
 * Get all valid moves from a position for a given piece.
 * Excludes: out of bounds, already visited.
 * 
 * @param from The starting position
 * @param piece The piece type to move
 * @return List of valid destination positions
 */
public List<Position> getValidMoves(Position from, PieceType piece) {
    List<Position> validMoves = new ArrayList<>();
    int[][] movements = piece.getMovements();
    
    if (piece.getMovementType() == MovementType.STATIC) {
        // Single-step movements (Knight, Owl, Dragon)
        for (int[] move : movements) {
            Position newPos = new Position(from.row() + move[0], 
                                           from.col() + move[1]);
            if (isValidPosition(newPos) && !isVisited(newPos)) {
                validMoves.add(newPos);
            }
        }
    } else {
        // Continuous movements (Queen, Rook, Bishop)
        for (int[] direction : movements) {
            int steps = 1;
            while (true) {
                Position newPos = new Position(
                    from.row() + direction[0] * steps,
                    from.col() + direction[1] * steps
                );
                
                if (!isValidPosition(newPos)) break;
                if (isVisited(newPos)) break;
                
                validMoves.add(newPos);
                steps++;
            }
        }
    }
    
    return validMoves;
}

/**
 * Check if piece1 at pos1 can capture piece2 at pos2.
 * Returns true if pos2 is in the attack range of piece1.
 * 
 * @param pos1 Position of first piece
 * @param piece1 Type of first piece
 * @param pos2 Position of second piece
 * @param piece2 Type of second piece (not used, but kept for extensibility)
 * @return true if piece1 can capture piece2
 */
public boolean canCapture(Position pos1, PieceType piece1, 
                         Position pos2, PieceType piece2) {
    if (!isValidPosition(pos1) || !isValidPosition(pos2)) return false;
    if (pos1.equals(pos2)) return true;  // Same position
    
    int[][] movements = piece1.getMovements();
    
    if (piece1.getMovementType() == MovementType.STATIC) {
        // Check if pos2 is reachable in one move
        for (int[] move : movements) {
            Position target = new Position(pos1.row() + move[0], 
                                          pos1.col() + move[1]);
            if (target.equals(pos2)) return true;
        }
    } else {
        // Check if pos2 is in any continuous line
        for (int[] direction : movements) {
            int steps = 1;
            while (true) {
                Position target = new Position(
                    pos1.row() + direction[0] * steps,
                    pos1.col() + direction[1] * steps
                );
                
                if (!isValidPosition(target)) break;
                if (target.equals(pos2)) return true;
                
                steps++;
            }
        }
    }
    
    return false;
}
```

---

### TimePredictor (Simplified with ENUM)

```java
package com.serafinebot.p2.controller;

/**
 * Time prediction based on empirical exponential model.
 * Formula: T(n) = k * e^(α*n) * avgComplexity
 * Where n = rows * cols, and complexity comes from PieceType.getComplexity()
 */
public class TimePredictor {
    
    // Empirical constants (calibrated in Phase 6)
    private static final double BASE_CONSTANT = 0.001;      // k (in seconds)
    private static final double GROWTH_FACTOR = 0.35;       // α (exponential factor)
    
    /**
     * Predict execution time in seconds.
     * 
     * @param rows Board height
     * @param cols Board width
     * @param piece1 First piece type
     * @param piece2 Second piece type
     * @return Estimated time in seconds
     */
    public static double predictTime(int rows, int cols, 
                                     PieceType piece1, PieceType piece2) {
        int totalCells = rows * cols;
        
        // Get complexities directly from enum!
        double complexity1 = piece1.getComplexity();
        double complexity2 = piece2.getComplexity();
        double avgComplexity = (complexity1 + complexity2) / 2.0;
        
        // Exponential formula
        double predictedTime = BASE_CONSTANT * 
                              Math.exp(GROWTH_FACTOR * totalCells) * 
                              avgComplexity;
        
        return predictedTime;
    }
    
    /**
     * Get formatted time estimate (Catalan).
     * 
     * @return String like "< 1 segon", "5.3 segons", "2.1 minuts"
     */
    public static String predictTimeFormatted(int rows, int cols,
                                             PieceType piece1, PieceType piece2) {
        double seconds = predictTime(rows, cols, piece1, piece2);
        
        if (seconds < 1) {
            return "< 1 segon";
        } else if (seconds < 60) {
            return String.format("%.1f segons", seconds);
        } else if (seconds < 3600) {
            return String.format("%.1f minuts", seconds / 60);
        } else {
            return String.format("%.1f hores", seconds / 3600);
        }
    }
    
    /**
     * Get difficulty level (Catalan).
     * 
     * @return String like "Molt fàcil", "Fàcil", "Moderat", "Difícil", "Molt difícil"
     */
    public static String getDifficultyLevel(int rows, int cols,
                                           PieceType piece1, PieceType piece2) {
        double seconds = predictTime(rows, cols, piece1, piece2);
        
        if (seconds < 1) {
            return "Molt fàcil";
        } else if (seconds < 10) {
            return "Fàcil";
        } else if (seconds < 60) {
            return "Moderat";
        } else if (seconds < 300) {
            return "Difícil";
        } else {
            return "Molt difícil";
        }
    }
}
```

**Much simpler than with class hierarchy!** No need for `Map<String, Double>` lookup.

---

## Testing Strategy

### Unit Testing

**Model classes:**
- `Position`: Test validation, distance calculations
- `PieceType`: Verify all enum constants have correct data
  - Test `PieceType.values()` returns 6 pieces
  - Test each piece's movement count
  - Test `getComplexity()` for all pieces
  - Test `getImagePath()` for both colors
- `Board`: Test mark/unmark, move generation, capture detection
- `HamiltonianPath`: Test add/remove operations
- `SolverMetrics`: Test timer, counters

**Controller classes:**
- `TimePredictor`: Verify predictions with known values
- `BacktrackingSolver`: Test on small boards with known solutions

**Example unit test:**
```java
@Test
public void testPieceTypeEnum() {
    assertEquals(6, PieceType.values().length);
    assertEquals("Cavall", PieceType.KNIGHT.getName());
    assertEquals(8, PieceType.KNIGHT.getMovements().length);
    assertEquals(MovementType.STATIC, PieceType.KNIGHT.getMovementType());
    assertEquals(1.0, PieceType.KNIGHT.getComplexity(), 0.01);
    assertTrue(PieceType.DRAGON.getImagePath(true).contains("dragon-white"));
}
```

### Integration Testing

**Model + Controller:**
- Full solve cycle on 4×4 board
- Verify solution validity
- Test stop mechanism

**Controller + View:**
- Test button actions trigger controller methods
- Test controller callbacks update view
- Test ComboBox with `PieceType.values()`

### System Testing

**Complete application:**
- Test all piece combinations (easy with `PieceType.values()`)
- Test different board sizes
- Test edge cases:
  - Adjacent initial positions
  - Corner positions
  - Maximum board size (12×12)
- Performance testing:
  - Measure actual times for calibration
  - Verify no GUI freezing

### User Acceptance Testing

**Usability:**
- Configuration is intuitive
- Prediction is visible and helpful
- Solution visualization is clear
- Step-by-step navigation works smoothly
- Animation speed is appropriate
- Catalan labels are correct

---

## Time Estimation

| Phase | Description | Time |
|-------|-------------|------|
| 1 | Setup | 30 min |
| 2 | Model - PieceType ENUM ⭐ | **30 min** (was 1h) |
| 3 | Model - Board | 1h 30min |
| 4 | Model - Path & Metrics | 45min |
| 5 | Model - BacktrackingSolver | 2h |
| 6 | Controller - TimePredictor | 1h 30min |
| 7 | Controller - SolverTask | 1h |
| 8 | Controller - SolverController | 1h |
| 9 | View - Basic Components | 1h |
| 10 | View - ConfigPanel | 2h 30min |
| 11 | View - BoardPanel | 2h |
| 12 | View - ControlPanel | 1h |
| 13 | View - StatsPanel | 1h |
| 14 | View - MainFrame | 1h 30min |
| 15 | Integration (Controller ↔ View) | 2h |
| 16 | Step-by-Step Visualization | 2h |
| 17 | Main & Final Testing | 1h |
| 18 | Polish & Error Handling | 1h 30min |
| 19 | Code Documentation | 1h |
| 20 | Memoria (LaTeX Report) | 5h |
| **TOTAL** | | **~29.5 hours** |

**Savings with ENUM approach:** 30 minutes + less cognitive overhead! 🎉

---

## Final Checklist

### Mandatory Features
- [ ] Hamiltonian path with 2 pieces alternating
- [ ] Mutual non-capture constraint validated
- [ ] GUI with all configurable parameters:
  - [ ] Board size (rows × cols, 4-12)
  - [ ] Piece 1 type & initial position (using `PieceType` enum)
  - [ ] Piece 2 type & initial position (using `PieceType` enum)
- [ ] 6 piece types: KNIGHT, QUEEN, ROOK, BISHOP, OWL, DRAGON
- [ ] Execution time measurement
- [ ] Start/Stop buttons functional
- [ ] Solution visualization with sequence numbers

### Voluntary Features
- [ ] Step-by-step navigation (<<, >>)
- [ ] Automatic animation (Play/Pause)
- [ ] **Time prediction displayed**
- [ ] **Difficulty level shown**

### Code Quality
- [ ] MVC architecture clearly separated
- [ ] **PieceType ENUM with all 6 pieces**
- [ ] **MovementType ENUM for movement types**
- [ ] Backtracking with pruning (Warnsdorff's heuristic)
- [ ] Thread-safe execution (SwingWorker)
- [ ] Robust error handling
- [ ] Javadoc on all public classes
- [ ] Inline comments on complex logic

### GUI (Catalan)
- [ ] All labels in Catalan
- [ ] All buttons in Catalan
- [ ] All messages in Catalan
- [ ] Piece names in Catalan (from `PieceType.getName()`)
- [ ] State messages in Catalan
- [ ] ComboBox displays: Cavall, Reina, Torre, Alfil, Mussol, Drac

### Testing
- [ ] Unit tests for PieceType enum
- [ ] Unit tests for key model classes
- [ ] Integration test (full solve cycle)
- [ ] System test on various configurations
- [ ] Performance validation (times match predictions)
- [ ] Edge case testing (corners, adjacent, max size)

### Report (Memoria)
- [ ] All sections complete:
  - [ ] Introducció
  - [ ] Fonaments Teòrics
  - [ ] Disseny i Implementació (include ENUM design rationale)
  - [ ] Resultats i Discussió
  - [ ] Conclusions
  - [ ] Bibliografia
- [ ] Screenshots (at least 4):
  - [ ] Initial configuration
  - [ ] Solution with sequence numbers
  - [ ] Step-by-step visualization
  - [ ] Statistics display
- [ ] Diagrams (at least 3):
  - [ ] Architecture (MVC with ENUM)
  - [ ] Backtracking flow
  - [ ] PieceType ENUM diagram
- [ ] Results table (execution times)
- [ ] Growth graph (time vs. board size)
- [ ] Prediction accuracy analysis
- [ ] PDF compiled correctly (run pdflatex twice)

### Final Validation
- [ ] `mvn compile` succeeds
- [ ] `mvn exec:java` launches GUI
- [ ] All images load correctly
- [ ] Application runs without crashes
- [ ] Stop button works gracefully
- [ ] Solutions are valid (verified manually on small board)
- [ ] memoria.pdf opens and displays correctly

---

## Appendix A: Dragon Piece Definition

**Movement pattern:**
```
Extended L (8 positions):          King (8 positions):
    .  .  D  .  .                      D  D  D
    .  .  .  .  .                      D  X  D
    D  .  X  .  D                      D  D  D
    .  .  .  .  .
    .  .  D  .  .

Combined (16 unique positions):
    .  .  D  .  .
    .  D  D  D  .
    D  D  X  D  D
    .  D  D  D  .
    .  .  D  .  .
```

**In PieceType enum:**
```java
DRAGON("Drac", "D", MovementType.STATIC, new int[][] {
    // Extended L
    {-3, -1}, {-3, 1}, {-1, -3}, {-1, 3},
    {1, -3}, {1, 3}, {3, -1}, {3, 1},
    // King
    {-1, -1}, {-1, 0}, {-1, 1},
    {0, -1},           {0, 1},
    {1, -1},  {1, 0},  {1, 1}
})
```

**Complexity:** 1.4 (powerful but not as overwhelming as Queen)

---

## Appendix B: Calibration Data Template

**Fill this table during Phase 6 (TimePredictor calibration):**

| Board | Rows | Cols | Cells | Piece 1 | Piece 2 | Real Time (s) | Predicted Time (s) | Error (%) | Iterations | Backtracks |
|-------|------|------|-------|---------|---------|---------------|-------------------|-----------|------------|------------|
| 1     | 4    | 4    | 16    | KNIGHT  | KNIGHT  | ???           | ???               | ???       | ???        | ???        |
| 2     | 5    | 5    | 25    | KNIGHT  | KNIGHT  | ???           | ???               | ???       | ???        | ???        |
| 3     | 5    | 5    | 25    | KNIGHT  | ROOK    | ???           | ???               | ???       | ???        | ???        |
| 4     | 6    | 6    | 36    | KNIGHT  | ROOK    | ???           | ???               | ???       | ???        | ???        |
| 5     | 6    | 6    | 36    | QUEEN   | BISHOP  | ???           | ???               | ???       | ???        | ???        |
| 6     | 6    | 8    | 48    | OWL     | DRAGON  | ???           | ???               | ???       | ???        | ???        |

**Adjust constants to minimize average error:**
- Modify `BASE_CONSTANT` (affects magnitude)
- Modify `GROWTH_FACTOR` (affects exponential rate)
- Iterate until error < 20% for most cases

---

## Appendix C: Benefits of ENUM Approach

### Comparison: Class Hierarchy vs ENUM

| Aspect | Class Hierarchy | ENUM Approach | Winner |
|--------|----------------|---------------|--------|
| **Lines of code** | ~470 lines (7 files) | ~120 lines (1 file) | ✅ ENUM |
| **Files to maintain** | 7 (Piece.java + 6 subclasses) | 2 (PieceType + MovementType) | ✅ ENUM |
| **Adding new piece** | Create new class, extend Piece | Add enum constant | ✅ ENUM |
| **Type safety** | ✅ Yes (compile-time) | ✅ Yes (compile-time) | ✅ Tie |
| **ComboBox setup** | Need factory/list | `new JComboBox<>(PieceType.values())` | ✅ ENUM |
| **Complexity lookup** | `Map<String, Double>` | `piece.getComplexity()` | ✅ ENUM |
| **Polymorphic behavior** | ✅ Easy | ❌ Not possible | ⚠️ Classes (if needed) |
| **Runtime extensibility** | ✅ Possible | ❌ Fixed at compile-time | ⚠️ Classes (if needed) |
| **Testability** | Need to test each class | Test enum constants | ✅ ENUM |
| **Memory footprint** | 6 objects | 6 enum constants (lighter) | ✅ ENUM |

### When to use each approach?

**Use ENUM when:**
- ✅ Fixed set of types known at compile-time (our case!)
- ✅ No polymorphic behavior needed
- ✅ Simple data + behavior
- ✅ Want less boilerplate

**Use Class Hierarchy when:**
- ⚠️ Need runtime extensibility (load pieces from plugins)
- ⚠️ Complex piece-specific behavior (different capture rules, state)
- ⚠️ Want open/closed principle (easy to add new types without modifying existing code)

**For this project:** ENUM is the clear winner! 🏆

---

## Appendix D: pom.xml Configuration

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.serafinebot.p2</groupId>
    <artifactId>P2</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>P2 - Hamiltonian Path Backtracking</name>

    <properties>
        <maven.compiler.source>16</maven.compiler.source>
        <maven.compiler.target>16</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.0.0</version>
                <configuration>
                    <mainClass>com.serafinebot.p2.Main</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## Appendix E: .gitignore

```gitignore
# Compiled class files
*.class
target/

# IntelliJ IDEA
.idea/
*.iml
*.iws

# LaTeX auxiliary files
*.aux
*.log
*.out
*.synctex.gz
*.toc
*.bbl
*.blg

# OS files
.DS_Store
Thumbs.db

# Backup files
*~
*.bak
*.swp
```

---

## Appendix F: Quick Reference - ENUM Usage

### Creating ComboBox (trivial!)
```java
JComboBox<PieceType> combo = new JComboBox<>(PieceType.values());
// Automatically shows: Cavall, Reina, Torre, Alfil, Mussol, Drac
```

### Getting selected piece
```java
PieceType selected = (PieceType) combo.getSelectedItem();
```

### Iterating all pieces
```java
for (PieceType piece : PieceType.values()) {
    System.out.println(piece.getName() + ": " + piece.getComplexity());
}
```

### Using in Board methods
```java
List<Position> moves = board.getValidMoves(currentPos, PieceType.KNIGHT);
boolean canCapture = board.canCapture(pos1, PieceType.QUEEN, pos2, PieceType.BISHOP);
```

### Getting piece info
```java
PieceType dragon = PieceType.DRAGON;
String name = dragon.getName();              // "Drac"
String shortName = dragon.getShortName();    // "D"
MovementType type = dragon.getMovementType();// STATIC
int[][] moves = dragon.getMovements();       // 16 movement vectors
double complexity = dragon.getComplexity();  // 1.4
String imagePath = dragon.getImagePath(true);// "/resources/dragon-white.png"
```

**So much cleaner!** 🎉

---

**END OF PLAN**

---

**Next Steps:**
1. ✅ PLAN.md created with ENUM approach
2. Setup pom.xml (Phase 1 continuation)
3. Copy resources from PFinal
4. Begin Phase 2: Implement PieceType ENUM

**Ready to continue with implementation?** 🚀
