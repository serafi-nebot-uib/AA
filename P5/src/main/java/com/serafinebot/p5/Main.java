package com.serafinebot.p5;

import com.serafinebot.p5.model.GameConfig;
import com.serafinebot.p5.model.Keypad;
import com.serafinebot.p5.model.MinimaxSolver;

import java.util.Arrays;

/**
 * Temporary entry point: runs a couple of representative solves until the GUI
 * is added. Run with {@code mvn exec:java -Dexec.mainClass="com.serafinebot.p5.Main"}.
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        runDemo("Forced loss (problem-statement example)", 28, 31, 6);
        runDemo("Fresh game on default 3x3, limit 31",      0, 31, 0);
    }

    private static void runDemo(String title, int initialTotal, int limit, int lastPlayed) {
        GameConfig config = new GameConfig(
                Keypad.standard(3, 3),
                initialTotal,
                limit,
                lastPlayed,
                /* playerCount    */ 2,
                /* startingPlayer */ 0
        );
        MinimaxSolver solver = new MinimaxSolver(config);
        int loser = solver.losingPlayer();
        System.out.printf("%s%n", title);
        System.out.printf("  initial=%d, limit=%d, lastPlayed=%d, players=2, starting=0%n",
                initialTotal, limit, lastPlayed);
        System.out.printf("  loser : player %d%n", loser);
        System.out.printf("  memo  : %d entries%n%n", solver.memo().size());
    }
}
