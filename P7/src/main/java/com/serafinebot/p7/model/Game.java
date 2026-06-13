package com.serafinebot.p7.model;

import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Simulates complete one-player Oca games.
 *
 * <p>This class is the model component that understands turn semantics: penalty
 * turns count but do not roll, ordinary turns roll once, and an oca can trigger
 * additional rolls inside the same counted turn. The board-specific rule table is
 * kept here as private static data because it is used only to advance this game.</p>
 */
public final class Game {

    public static final int START = 0;
    public static final int FINISH = 63;

    private static final int DIE_SIDES = 6;
    private static final int INN = 19;
    private static final int WELL = 31;
    private static final int MAZE = 42;
    private static final int JAIL = 52;
    private static final int DEATH = 58;

    private static final Map<Integer, Integer> GOOSE_JUMPS = Map.ofEntries(
            Map.entry(5, 9),
            Map.entry(9, 14),
            Map.entry(14, 18),
            Map.entry(18, 23),
            Map.entry(23, 27),
            Map.entry(27, 32),
            Map.entry(32, 36),
            Map.entry(36, 41),
            Map.entry(41, 45),
            Map.entry(45, 50),
            Map.entry(50, 54),
            Map.entry(54, 59),
            Map.entry(59, FINISH)
    );

    private static final Map<Integer, Integer> TELEPORTS = Map.ofEntries(
            Map.entry(6, 12),
            Map.entry(12, 6),
            Map.entry(26, 53),
            Map.entry(53, 26)
    );

    private final Random random;
    private final RuleVariant variant;

    public Game(Random random) {
        this(random, RuleVariant.STANDARD);
    }

    public Game(Random random, RuleVariant variant) {
        this.random = Objects.requireNonNull(random);
        this.variant = Objects.requireNonNull(variant);
    }

    /**
     * Plays one complete game and returns the number of counted turns.
     */
    public int play() {
        return play(null);
    }

    /**
     * Plays one complete game and optionally accumulates square visit counts.
     *
     * <p>The visit counter records the initial square, every physical landing
     * square after a die roll, every special-effect destination, the final square,
     * and occupied penalty turns. The array is owned by the caller so many games
     * can accumulate into one frequency table.</p>
     */
    public int play(long[] squareVisits) {
        validateVisits(squareVisits);
        int turns = 0;
        GameSnapshot state = new GameSnapshot(START, 0);
        recordVisit(squareVisits, state.position());
        while (!state.isFinished()) {
            state = advanceTurn(state, squareVisits);
            turns++;
        }
        return turns;
    }

    GameSnapshot advanceTurn(GameSnapshot state) {
        return advanceTurn(state, null);
    }

    /**
     * Advances the game by one counted turn.
     */
    GameSnapshot advanceTurn(GameSnapshot state, long[] squareVisits) {
        Objects.requireNonNull(state);
        validateVisits(squareVisits);
        if (state.isFinished()) {
            return state;
        }
        if (state.pendingPenaltyTurns() > 0) {
            recordVisit(squareVisits, state.position());
            return new GameSnapshot(state.position(), state.pendingPenaltyTurns() - 1);
        }

        int position = state.position();
        int pendingPenaltyTurns = 0;
        boolean extraRoll;
        do {
            MoveResult result = applyRoll(position, rollDie());
            recordVisit(squareVisits, result.landedPosition());
            // For teleports and other special movements, count both the square
            // that triggered the effect and the square where the player ends up.
            if (result.position() != result.landedPosition()) {
                recordVisit(squareVisits, result.position());
            }
            position = result.position();
            if (result.finished()) {
                return new GameSnapshot(position, 0);
            }
            pendingPenaltyTurns = result.penaltyTurns();
            extraRoll = result.extraRoll();
        } while (extraRoll);

        return new GameSnapshot(position, pendingPenaltyTurns);
    }

    /**
     * Applies one roll and, after movement/rebound, at most one special effect.
     *
     * <p>If the player reaches square 63 either by normal movement or by the last
     * goose, the returned result is marked as finished immediately. When a special
     * square moves the player to another special square, this method does not
     * cascade into the second effect; that rule is represented by returning both
     * the landing square and the final occupied square.</p>
     */
    private MoveResult applyRoll(int position, int dieValue) {
        validateRoll(position, dieValue);

        int moved = moveWithBounce(position, dieValue);
        if (moved == FINISH) {
            return new MoveResult(FINISH, FINISH, 0, false, true);
        }

        // Oca is checked before the other effects because the assignment lists it
        // as an automatic movement with an optional same-turn extra roll.
        Integer gooseDestination = variant.gooseJumps() ? GOOSE_JUMPS.get(moved) : null;
        if (gooseDestination != null) {
            boolean finished = gooseDestination == FINISH;
            boolean extraRoll = variant.gooseExtraRolls() && !finished;
            return new MoveResult(moved, gooseDestination, 0, extraRoll, finished);
        }

        Integer teleportDestination = variant.teleports() ? TELEPORTS.get(moved) : null;
        if (teleportDestination != null) {
            return new MoveResult(moved, teleportDestination, 0, false, teleportDestination == FINISH);
        }

        int penalty = variant.penalties() ? penaltyFor(moved) : 0;
        if (penalty > 0) {
            return new MoveResult(moved, moved, penalty, false, false);
        }

        if (variant.maze() && moved == MAZE) {
            return new MoveResult(moved, 30, 0, false, false);
        }
        if (variant.death() && moved == DEATH) {
            return new MoveResult(moved, START, 0, false, false);
        }

        return new MoveResult(moved, moved, 0, false, false);
    }

    /**
     * Applies the board-end rebound: positions beyond 63 are mirrored backwards.
     */
    private static int moveWithBounce(int position, int dieValue) {
        int raw = position + dieValue;
        if (raw <= FINISH) {
            return raw;
        }
        return FINISH - (raw - FINISH);
    }

    /**
     * Returns how many future counted turns are skipped by a penalty square.
     */
    private static int penaltyFor(int position) {
        return switch (position) {
            case INN -> 1;
            case WELL -> 2;
            case JAIL -> 3;
            default -> 0;
        };
    }

    /**
     * Rejects impossible game states before a roll is evaluated.
     */
    private static void validateRoll(int position, int dieValue) {
        if (position < START || position >= FINISH) {
            throw new IllegalArgumentException("Position must be between 0 and 62 before rolling.");
        }
        if (dieValue < 1 || dieValue > DIE_SIDES) {
            throw new IllegalArgumentException("Die value must be between 1 and 6.");
        }
    }

    private int rollDie() {
        return random.nextInt(DIE_SIDES) + 1;
    }

    private static void validateVisits(long[] squareVisits) {
        if (squareVisits != null && squareVisits.length < FINISH + 1) {
            throw new IllegalArgumentException("Visit counter must have at least 64 entries.");
        }
    }

    private static void recordVisit(long[] squareVisits, int position) {
        if (squareVisits != null) {
            squareVisits[position]++;
        }
    }
}
