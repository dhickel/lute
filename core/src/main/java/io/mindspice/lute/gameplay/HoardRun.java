package io.mindspice.lute.gameplay;

import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.random.RandomGenerator;

/** The rules and counters for one visit to the arena. Times are simulation seconds. */
public final class HoardRun {
    public static final int PLAYER_HEALTH = 1000;
    private static final int ROUND_SECONDS = 120;
    private static final int STARTING_BATCH = 10;
    private static final int ROUNDS_PER_BASE_BONUS = 20; // +5% of base per round

    private final RandomGenerator random;
    private final IntConsumer roundStarted;
    private final BiConsumer<Integer, Integer> spawn;
    private boolean gameOver;
    private int round;
    private long kills;
    private long score;
    private double elapsed;
    private int nextBatch;

    public HoardRun(RandomGenerator random, IntConsumer roundStarted, BiConsumer<Integer, Integer> spawn) {
        this.random = random;
        this.roundStarted = roundStarted;
        this.spawn = spawn;
        startRound();
    }

    public void advance(double seconds) {
        if (gameOver) { return; }
        elapsed += seconds;
        while (elapsed >= Math.min(nextBatch, ROUND_SECONDS)) {
            if (nextBatch >= ROUND_SECONDS) {
                elapsed -= ROUND_SECONDS;
                startRound();
            } else {
                int base = random.nextInt(4, 9);
                spawn.accept((int) scaleForRound(base, round), round);
                nextBatch += random.nextInt(30, 51);
            }
        }
    }

    private void startRound() {
        round++;
        roundStarted.accept(round);
        spawn.accept(round == 1 ? STARTING_BATCH : (int) scaleForRound(STARTING_BATCH, round), round);
        nextBatch = random.nextInt(30, 51);
    }

    /** Apply a linear round bonus and round to the nearest whole number, with ties up. */
    public static long scaleForRound(int base, int round) {
        return base + ((long) base * round + ROUNDS_PER_BASE_BONUS / 2) / ROUNDS_PER_BASE_BONUS;
    }

    public void end() { gameOver = true; }
    public boolean isGameOver() { return gameOver; }

    // Credit is committed on delivery. Even a kill queued before death cannot revise the final score.
    public void enemyKilled(int baseXp) {
        if (gameOver) { return; }
        kills++;
        score += scaleForRound(baseXp, round);
    }

    public long aliveSeconds() { return (round - 1L) * ROUND_SECONDS + (long) elapsed; }
    public long kills() { return kills; }
    public long score() { return score; }
    public int round() { return round; }
    public int remainingSeconds() { return (int) Math.ceil(ROUND_SECONDS - elapsed); }
}
