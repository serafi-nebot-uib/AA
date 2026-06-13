package com.serafinebot.p7;

import com.serafinebot.p7.model.Game;
import com.serafinebot.p7.model.Simulation;
import com.serafinebot.p7.model.SimulationStats;

import java.util.Random;
import java.util.Scanner;

/**
 * Mandatory console entry point for the assignment statement.
 *
 * <p>This class intentionally stays minimal and does not expose optional report
 * features. It reads exactly one positive integer {@code N} from standard input,
 * runs the official-rule simulation and prints the exact labels required by the
 * PDF statement. More configurable, deterministic experiments belong to
 * {@link CLI}.</p>
 */
public final class Main {

    private Main() {}

    /**
     * Reads {@code N}, runs {@code N} games and prints the required summary.
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        if (!scanner.hasNextInt()) {
            System.err.println("Entrada no valida: se esperaba un entero positivo.");
            return;
        }

        int games = scanner.nextInt();
        if (games <= 0) {
            System.err.println("Entrada no valida: N debe ser positivo.");
            return;
        }

        Simulation simulation = new Simulation(
                new Game(new Random())
        );
        int[] observations = simulation.run(games);
        System.out.print(SimulationStats.from(observations).formatRequiredOutput());
    }
}
