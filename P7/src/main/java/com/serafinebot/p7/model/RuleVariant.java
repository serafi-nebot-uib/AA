package com.serafinebot.p7.model;

/**
 * Supported rule variants for optional empirical comparisons.
 *
 * <p>The mandatory assignment uses {@link #STANDARD}. The other constants are
 * small, controlled changes that make report experiments meaningful without
 * introducing a second rules engine. Each boolean toggles one family of special
 * squares inside {@link Game}.</p>
 */
public enum RuleVariant {

    STANDARD("Regles oficials", true, true, true, true, true, true),
    NO_PENALTIES("Sense penalitzacions", true, true, true, false, true, true),
    NO_GOOSE_EXTRA_ROLL("Oques sense tirada extra", true, false, true, true, true, true),
    NO_SPECIAL_CELLS("Sense caselles especials", false, false, false, false, false, false);

    private final String displayName;
    private final boolean gooseJumps;
    private final boolean gooseExtraRolls;
    private final boolean teleports;
    private final boolean penalties;
    private final boolean maze;
    private final boolean death;

    RuleVariant(String displayName, boolean gooseJumps, boolean gooseExtraRolls, boolean teleports,
                boolean penalties, boolean maze, boolean death) {
        this.displayName = displayName;
        this.gooseJumps = gooseJumps;
        this.gooseExtraRolls = gooseExtraRolls;
        this.teleports = teleports;
        this.penalties = penalties;
        this.maze = maze;
        this.death = death;
    }

    /**
     * Human-readable name used in the Swing interface and report CLI tables.
     */
    public String displayName() {
        return displayName;
    }

    boolean gooseJumps() {
        return gooseJumps;
    }

    boolean gooseExtraRolls() {
        return gooseExtraRolls;
    }

    boolean teleports() {
        return teleports;
    }

    boolean penalties() {
        return penalties;
    }

    boolean maze() {
        return maze;
    }

    boolean death() {
        return death;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
