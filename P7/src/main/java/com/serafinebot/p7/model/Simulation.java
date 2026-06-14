package com.serafinebot.p7.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs independent Oca games and stores batch-level observations.
 *
 * <p>The instance methods use one game runner sequentially and are kept for the
 * mandatory CLI. The static {@link #runBatch(int, long, RuleVariant, int)} method
 * is the report/GUI entry point: it can run sequentially for maximum
 * reproducibility or split work across deterministic worker-local game runners.</p>
 */
public final class Simulation {

    private final Game game;

    public Simulation(Game game) {
        this.game = Objects.requireNonNull(game);
    }

    /**
     * Runs {@code games} simulations and returns only the turn counts.
     */
    public int[] run(int games) {
        return runDetailed(games).turnCounts();
    }

    /**
     * Runs {@code games} simulations and returns turn counts plus visit counts.
     */
    public SimulationData runDetailed(int games) {
        return runDetailed(games, Game.MIN_PLAYERS);
    }

    /**
     * Runs {@code games} multiplayer simulations and returns turns, visits and wins.
     */
    public SimulationData runDetailed(int games, int playerCount) {
        if (games <= 0) {
            throw new IllegalArgumentException("The number of games must be positive.");
        }

        int[] turns = new int[games];
        long[] squareVisits = new long[Game.FINISH + 1];
        int[] winnerCounts = new int[playerCount];
        for (int i = 0; i < games; i++) {
            MatchResult result = game.playMatch(playerCount, squareVisits);
            turns[i] = result.turns();
            winnerCounts[result.winner()]++;
        }
        return new SimulationData(turns, squareVisits, winnerCounts);
    }

    /**
     * Runs a complete deterministic batch for a seed, variant and thread count.
     *
     * <p>When {@code threadCount <= 1}, all games use one seeded random stream.
     * When multiple threads are requested, each worker receives a seed derived
     * from the base seed and worker index. That keeps a parallel run repeatable
     * for the same thread count, while avoiding shared mutable random state.</p>
     */
    public static SimulationData runBatch(int games, long seed, RuleVariant variant, int threadCount) {
        return runBatch(games, seed, variant, threadCount, Game.MIN_PLAYERS);
    }

    /**
     * Runs a complete deterministic batch for 1 to 4 players.
     */
    public static SimulationData runBatch(int games, long seed, RuleVariant variant, int threadCount, int playerCount) {
        Objects.requireNonNull(variant);
        if (games <= 0) {
            throw new IllegalArgumentException("The number of games must be positive.");
        }
        if (threadCount <= 1 || games == 1) {
            return sequentialBatch(games, seed, variant, playerCount);
        }

        int workerCount = Math.min(games, threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        try {
            List<Callable<PartialSimulationData>> tasks = new ArrayList<>();
            int offset = 0;
            int baseSize = games / workerCount;
            int remainder = games % workerCount;
            for (int worker = 0; worker < workerCount; worker++) {
                // The first 'remainder' workers receive one extra game so the
                // chunks cover exactly [0, games) without gaps or overlap.
                int chunkSize = baseSize + (worker < remainder ? 1 : 0);
                int chunkOffset = offset;
                long workerSeed = mixSeed(seed, worker);
                tasks.add(() -> runChunk(chunkOffset, chunkSize, workerSeed, variant, playerCount));
                offset += chunkSize;
            }

            int[] turns = new int[games];
            long[] squareVisits = new long[Game.FINISH + 1];
            int[] winnerCounts = new int[playerCount];
            List<Future<PartialSimulationData>> futures = executor.invokeAll(tasks);
            for (Future<PartialSimulationData> future : futures) {
                PartialSimulationData partial = future.get();
                System.arraycopy(partial.turnCounts(), 0, turns, partial.offset(), partial.turnCounts().length);
                addVisits(squareVisits, partial.squareVisits());
                addWins(winnerCounts, partial.winnerCounts());
            }
            return new SimulationData(turns, squareVisits, winnerCounts);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Parallel simulation was interrupted.", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Parallel simulation failed.", ex.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private static SimulationData sequentialBatch(int games, long seed, RuleVariant variant, int playerCount) {
        return new Simulation(
                new Game(new Random(seed), variant)
        ).runDetailed(games, playerCount);
    }

    private static PartialSimulationData runChunk(int offset, int games, long seed, RuleVariant variant, int playerCount) {
        SimulationData data = sequentialBatch(games, seed, variant, playerCount);
        return new PartialSimulationData(offset, data.turnCounts(), data.squareVisits(), data.winnerCounts());
    }

    private static void addVisits(long[] target, long[] source) {
        for (int i = 0; i < target.length; i++) {
            target[i] += source[i];
        }
    }

    private static void addWins(int[] target, int[] source) {
        for (int i = 0; i < target.length; i++) {
            target[i] += source[i];
        }
    }

    /**
     * Mixes the base seed into independent-looking worker seeds.
     */
    private static long mixSeed(long seed, int worker) {
        long value = seed + 0x9E3779B97F4A7C15L * (worker + 1L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private record PartialSimulationData(int offset, int[] turnCounts, long[] squareVisits, int[] winnerCounts) {
    }
}
