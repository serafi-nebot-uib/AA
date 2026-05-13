package com.serafinebot.p6.model;

public record GameResult(GameStatus status, Player winner) {

    public static GameResult inProgress() {
        return new GameResult(GameStatus.IN_PROGRESS, null);
    }

    public static GameResult draw() {
        return new GameResult(GameStatus.DRAW, null);
    }

    public static GameResult win(Player winner) {
        return new GameResult(winner == Player.RED ? GameStatus.RED_WINS : GameStatus.YELLOW_WINS, winner);
    }

    public boolean isFinished() {
        return status != GameStatus.IN_PROGRESS;
    }
}
