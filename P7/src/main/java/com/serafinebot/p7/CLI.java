package com.serafinebot.p7;

import com.serafinebot.p7.model.RuleVariant;
import com.serafinebot.p7.model.Game;
import com.serafinebot.p7.model.Simulation;
import com.serafinebot.p7.model.SimulationData;
import com.serafinebot.p7.model.SimulationStats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic experiment runner intended for report tables.
 *
 * <p>This entry point is intentionally separate from {@link Main}. The assignment
 * console program must keep its exact stdin/stdout contract, while this tool can
 * expose additional options needed for the report: fixed seeds, multiple values
 * of {@code N}, player counts, rule variants, repetitions, CSV/Markdown output,
 * elapsed time and top visited squares.</p>
 *
 * <p>By default the tool uses one worker thread. This makes report numbers
 * machine-independent for a given Java {@link java.util.Random} implementation.
 * Parallel execution is still available through {@code --threads}, but the thread
 * count is included in every output row because it changes how the base seed is
 * split between workers.</p>
 */
public final class CLI {

    private static final long DEFAULT_SEED = 20260609L;

    private CLI() {}

    /**
     * Parses command-line options, runs all requested experiments and prints a
     * table suitable for pasting into the report source.
     */
    public static void main(String[] args) {
        try {
            ReportConfig config = parse(args);
            if (config.help()) {
                System.out.print(usage());
                return;
            }
            List<ExperimentRow> rows = run(config);
            System.out.print(formatRows(rows, config));
            if (config.topVisits() > 0) {
                System.out.print(formatTopVisits(rows, config));
            }
        } catch (IllegalArgumentException ex) {
            System.err.println("Error: " + ex.getMessage());
            System.err.println("Use --help to see available options.");
            System.exit(1);
        }
    }

    /**
     * Converts command-line arguments into a typed configuration object.
     *
     * <p>The parser is deliberately small and dependency-free so the project stays
     * self-contained. Options that expect a value reject missing values and invalid
     * numbers with an {@link IllegalArgumentException}; {@link #main(String[])}
     * translates those exceptions into user-facing error messages.</p>
     */
    static ReportConfig parse(String[] args) {
        List<Integer> games = List.of(1_000, 10_000, 100_000);
        List<Integer> players = List.of(Game.MIN_PLAYERS);
        long seed = DEFAULT_SEED;
        List<RuleVariant> variants = List.of(RuleVariant.STANDARD);
        int threads = 1;
        int repetitions = 1;
        OutputFormat format = OutputFormat.MARKDOWN;
        boolean includeTime = false;
        int topVisits = 0;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> {
                    return new ReportConfig(games, players, seed, variants, threads, repetitions, format, includeTime, topVisits, true);
                }
                case "--games", "--n" -> games = parseGames(value(args, ++i, arg));
                case "--players" -> players = parsePlayers(value(args, ++i, arg));
                case "--seed" -> seed = parseLong(value(args, ++i, arg), "seed");
                case "--variants" -> variants = parseVariants(value(args, ++i, arg));
                case "--threads" -> threads = parsePositiveInt(value(args, ++i, arg), "threads");
                case "--repetitions", "--repeat" -> repetitions = parsePositiveInt(value(args, ++i, arg), "repetitions");
                case "--format" -> format = parseFormat(value(args, ++i, arg));
                case "--time" -> includeTime = true;
                case "--top-visits" -> topVisits = parseNonNegativeInt(value(args, ++i, arg), "top-visits");
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }

        return new ReportConfig(games, players, seed, variants, threads, repetitions, format, includeTime, topVisits, false);
    }

    /**
     * Executes every requested {@code (variant, N, repetition)} combination.
     */
    static List<ExperimentRow> run(ReportConfig config) {
        List<ExperimentRow> rows = new ArrayList<>();
        for (RuleVariant variant : config.variants()) {
            for (int playerCount : config.players()) {
                for (int games : config.games()) {
                    for (int repetition = 1; repetition <= config.repetitions(); repetition++) {
                        long runSeed = seedFor(config.seed(), variant, games, repetition, playerCount);
                        long start = System.nanoTime();
                        SimulationData data = Simulation.runBatch(games, runSeed, variant, config.threads(), playerCount);
                        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
                        rows.add(new ExperimentRow(variant, playerCount, games, repetition, runSeed, config.threads(),
                                SimulationStats.from(data.turnCounts()), data.squareVisits(), data.winnerCounts(), elapsedMillis));
                    }
                }
            }
        }
        return rows;
    }

    /**
     * Derives a stable run seed from the base seed and experiment coordinates.
     *
     * <p>Using a different derived seed for each row prevents larger experiments
     * from simply extending smaller experiments. It also makes repetitions truly
     * independent while keeping every row reproducible from the printed seed.</p>
     */
    static long seedFor(long baseSeed, RuleVariant variant, int games, int repetition) {
        return seedFor(baseSeed, variant, games, repetition, Game.MIN_PLAYERS);
    }

    static long seedFor(long baseSeed, RuleVariant variant, int games, int repetition, int players) {
        long value = baseSeed;
        value ^= 0x9E3779B97F4A7C15L * (variant.ordinal() + 1L);
        value ^= 0xBF58476D1CE4E5B9L * games;
        value ^= 0x94D049BB133111EBL * repetition;
        value ^= 0xD6E8FEB86659FD93L * players;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    /** Selects Markdown or CSV formatting for the main statistics table. */
    private static String formatRows(List<ExperimentRow> rows, ReportConfig config) {
        return switch (config.format()) {
            case MARKDOWN -> formatMarkdownRows(rows, config.includeTime());
            case CSV -> formatCsvRows(rows, config.includeTime());
        };
    }

    /** Formats the main statistics table in GitHub-flavored Markdown. */
    private static String formatMarkdownRows(List<ExperimentRow> rows, boolean includeTime) {
        StringBuilder builder = new StringBuilder();
        builder.append("| Variant | Players | N | Rep | Seed | Threads | Min | P1 | P2 | P3 | P4 | Mediana | P6 | P7 | P8 | P9 | Max | Media | Win 1 | Win 2 | Win 3 | Win 4 |");
        if (includeTime) {
            builder.append(" Temps ms |");
        }
        builder.append(System.lineSeparator());
        builder.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
        if (includeTime) {
            builder.append("---:|");
        }
        builder.append(System.lineSeparator());
        for (ExperimentRow row : rows) {
            appendMarkdownRow(builder, row, includeTime);
        }
        return builder.toString();
    }

    /** Appends one Markdown table row using locale-stable decimal formatting. */
    private static void appendMarkdownRow(StringBuilder builder, ExperimentRow row, boolean includeTime) {
        SimulationStats stats = row.stats();
        builder.append(String.format(Locale.US,
                "| %s | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d | %d | %.6f |",
                row.variant().displayName(), row.players(), row.games(), row.repetition(), row.seed(), row.threads(),
                stats.minimum(), stats.p1(), stats.p2(), stats.p3(), stats.p4(), stats.median(),
                stats.p6(), stats.p7(), stats.p8(), stats.p9(), stats.maximum(), stats.mean()));
        appendMarkdownWinProbabilities(builder, row);
        if (includeTime) {
            builder.append(String.format(Locale.US, " %d |", row.elapsedMillis()));
        }
        builder.append(System.lineSeparator());
    }

    /** Formats the main statistics table as CSV for spreadsheets or scripts. */
    private static String formatCsvRows(List<ExperimentRow> rows, boolean includeTime) {
        StringBuilder builder = new StringBuilder();
        builder.append("variant,players,n,rep,seed,threads,min,p1,p2,p3,p4,median,p6,p7,p8,p9,max,mean,win1,win2,win3,win4");
        if (includeTime) {
            builder.append(",elapsed_ms");
        }
        builder.append(System.lineSeparator());
        for (ExperimentRow row : rows) {
            SimulationStats stats = row.stats();
            builder.append(String.format(Locale.US,
                    "%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.6f",
                    csv(row.variant().name()), row.players(), row.games(), row.repetition(), row.seed(), row.threads(),
                    stats.minimum(), stats.p1(), stats.p2(), stats.p3(), stats.p4(), stats.median(),
                    stats.p6(), stats.p7(), stats.p8(), stats.p9(), stats.maximum(), stats.mean()));
            appendCsvWinProbabilities(builder, row);
            if (includeTime) {
                builder.append(',').append(row.elapsedMillis());
            }
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    /** Selects Markdown or CSV formatting for the optional visit-frequency table. */
    private static String formatTopVisits(List<ExperimentRow> rows, ReportConfig config) {
        return switch (config.format()) {
            case MARKDOWN -> formatMarkdownTopVisits(rows, config.topVisits());
            case CSV -> formatCsvTopVisits(rows, config.topVisits());
        };
    }

    /** Formats the most visited squares per run in Markdown. */
    private static String formatMarkdownTopVisits(List<ExperimentRow> rows, int limit) {
        StringBuilder builder = new StringBuilder(System.lineSeparator());
        builder.append("| Variant | Players | N | Rep | Rank | Square | Visits | Percent |")
                .append(System.lineSeparator());
        builder.append("|---|---:|---:|---:|---:|---:|---:|---:|")
                .append(System.lineSeparator());
        for (ExperimentRow row : rows) {
            for (VisitRank visit : topVisits(row.squareVisits(), limit)) {
                builder.append(String.format(Locale.US, "| %s | %d | %d | %d | %d | %d | %d | %.4f |%n",
                        row.variant().displayName(), row.players(), row.games(), row.repetition(), visit.rank(),
                        visit.square(), visit.visits(), visit.percent()));
            }
        }
        return builder.toString();
    }

    /** Formats the most visited squares per run as CSV. */
    private static String formatCsvTopVisits(List<ExperimentRow> rows, int limit) {
        StringBuilder builder = new StringBuilder(System.lineSeparator());
        builder.append("visit_variant,visit_players,visit_n,visit_rep,rank,square,visits,percent")
                .append(System.lineSeparator());
        for (ExperimentRow row : rows) {
            for (VisitRank visit : topVisits(row.squareVisits(), limit)) {
                builder.append(String.format(Locale.US, "%s,%d,%d,%d,%d,%d,%d,%.4f%n",
                        csv(row.variant().name()), row.players(), row.games(), row.repetition(), visit.rank(),
                        visit.square(), visit.visits(), visit.percent()));
            }
        }
        return builder.toString();
    }

    /**
     * Returns the {@code limit} most visited squares, ordered by frequency and
     * then by square number to make ties deterministic.
     */
    private static List<VisitRank> topVisits(long[] visits, int limit) {
        long total = Arrays.stream(visits).sum();
        List<VisitRank> ranked = new ArrayList<>();
        for (int square = 0; square < visits.length; square++) {
            double percent = total == 0L ? 0.0 : visits[square] * 100.0 / total;
            ranked.add(new VisitRank(0, square, visits[square], percent));
        }
        ranked.sort(Comparator.comparingLong(VisitRank::visits).reversed()
                .thenComparingInt(VisitRank::square));

        List<VisitRank> limited = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, ranked.size()); i++) {
            VisitRank visit = ranked.get(i);
            limited.add(new VisitRank(i + 1, visit.square(), visit.visits(), visit.percent()));
        }
        return limited;
    }

    /** Parses a comma-separated list of positive values for {@code N}. */
    private static List<Integer> parseGames(String value) {
        String[] parts = value.split(",");
        List<Integer> parsed = new ArrayList<>();
        for (String part : parts) {
            parsed.add(parsePositiveInt(part.trim(), "games"));
        }
        return List.copyOf(parsed);
    }

    /** Parses a comma-separated list of player counts, or {@code all}. */
    private static List<Integer> parsePlayers(String value) {
        if (value.equalsIgnoreCase("all")) {
            List<Integer> all = new ArrayList<>();
            for (int players = Game.MIN_PLAYERS; players <= Game.MAX_PLAYERS; players++) {
                all.add(players);
            }
            return List.copyOf(all);
        }

        String[] parts = value.split(",");
        List<Integer> parsed = new ArrayList<>();
        for (String part : parts) {
            int playerCount = parsePositiveInt(part.trim(), "players");
            if (playerCount < Game.MIN_PLAYERS || playerCount > Game.MAX_PLAYERS) {
                throw new IllegalArgumentException("players must be between 1 and 4.");
            }
            parsed.add(playerCount);
        }
        return List.copyOf(parsed);
    }

    /** Parses a comma-separated list of variants, or {@code all}. */
    private static List<RuleVariant> parseVariants(String value) {
        if (value.equalsIgnoreCase("all")) {
            return List.of(RuleVariant.values());
        }

        String[] parts = value.split(",");
        List<RuleVariant> parsed = new ArrayList<>();
        for (String part : parts) {
            parsed.add(parseVariant(part));
        }
        return List.copyOf(parsed);
    }

    /** Parses one variant name using forgiving separators and aliases. */
    private static RuleVariant parseVariant(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if (normalized.equals("OFFICIAL") || normalized.equals("OFFICIALS") || normalized.equals("STD")) {
            return RuleVariant.STANDARD;
        }
        if (normalized.equals("NO_SPECIAL_SQUARES")) {
            return RuleVariant.NO_SPECIAL_CELLS;
        }
        for (RuleVariant variant : RuleVariant.values()) {
            if (variant.name().equals(normalized)) {
                return variant;
            }
        }
        throw new IllegalArgumentException("Unknown variant: " + value);
    }

    /** Parses the requested output format. */
    private static OutputFormat parseFormat(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MARKDOWN", "MD" -> OutputFormat.MARKDOWN;
            case "CSV" -> OutputFormat.CSV;
            default -> throw new IllegalArgumentException("Unknown format: " + value);
        };
    }

    /** Parses a strictly positive integer option. */
    private static int parsePositiveInt(String value, String name) {
        int parsed = parseInt(value, name);
        if (parsed <= 0) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
        return parsed;
    }

    /** Parses a non-negative integer option. */
    private static int parseNonNegativeInt(String value, String name) {
        int parsed = parseInt(value, name);
        if (parsed < 0) {
            throw new IllegalArgumentException(name + " cannot be negative.");
        }
        return parsed;
    }

    /** Parses an integer and produces option-specific error text. */
    private static int parseInt(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be an integer.");
        }
    }

    /** Parses a 64-bit integer and produces option-specific error text. */
    private static long parseLong(String value, String name) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be a 64-bit integer.");
        }
    }

    /** Reads the value following an option and rejects missing option arguments. */
    private static String value(String[] args, int index, String option) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException(option + " requires a value.");
        }
        return args[index];
    }

    /** Escapes one CSV field only when required by RFC-style CSV syntax. */
    private static String csv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Returns the user-facing help text. */
    private static String usage() {
        return "Usage: mvn exec:java -Dexec.mainClass=\"com.serafinebot.p7.CLI\" "
                + "-Dexec.args=\"[options]\"\n\n"
                + "Options:\n"
                + "  --games, --n <list>       Comma-separated game counts. Default: 1000,10000,100000\n"
                + "  --players <list|all>      Player counts from 1 to 4. Default: 1\n"
                + "  --seed <long>            Base seed. Default: 20260609\n"
                + "  --variants <list|all>    standard,no_penalties,no_goose_extra_roll,no_special_squares. Default: standard\n"
                + "  --threads <int>          Worker threads. Default: 1 for reproducibility\n"
                + "  --repetitions <int>      Runs per (variant,N). Default: 1\n"
                + "  --format <markdown|csv>  Output format. Default: markdown\n"
                + "  --time                   Include elapsed milliseconds column\n"
                + "  --top-visits <int>       Append top visited squares per run. Default: 0\n"
                + "  --help                   Show this message\n\n"
                + "Examples:\n"
                + "  --games 1000,10000 --seed 42 --variants standard --format markdown\n"
                + "  --games 100000 --players all --variants standard --format csv\n"
                + "  --games 100000 --variants all --threads 4 --time --top-visits 8\n";
    }

    private static void appendMarkdownWinProbabilities(StringBuilder builder, ExperimentRow row) {
        for (int player = 0; player < Game.MAX_PLAYERS; player++) {
            if (player < row.winnerCounts().length) {
                builder.append(String.format(Locale.US, " %.6f |", row.winnerCounts()[player] / (double) row.games()));
            } else {
                builder.append(" - |");
            }
        }
    }

    private static void appendCsvWinProbabilities(StringBuilder builder, ExperimentRow row) {
        for (int player = 0; player < Game.MAX_PLAYERS; player++) {
            builder.append(',');
            if (player < row.winnerCounts().length) {
                builder.append(String.format(Locale.US, "%.6f", row.winnerCounts()[player] / (double) row.games()));
            }
        }
    }

    /** Output formats supported by the report CLI. */
    enum OutputFormat {
        MARKDOWN,
        CSV
    }

    /** Parsed command-line configuration. */
    record ReportConfig(List<Integer> games, List<Integer> players, long seed, List<RuleVariant> variants, int threads,
                        int repetitions, OutputFormat format, boolean includeTime, int topVisits,
                        boolean help) {
    }

    /** One completed experiment row and the raw data needed for optional tables. */
    record ExperimentRow(RuleVariant variant, int players, int games, int repetition, long seed, int threads,
                         SimulationStats stats, long[] squareVisits, int[] winnerCounts, long elapsedMillis) {
    }

    /** Ranked visit-frequency row for the optional top-squares table. */
    private record VisitRank(int rank, int square, long visits, double percent) {
    }
}
