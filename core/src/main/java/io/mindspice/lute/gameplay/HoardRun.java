package io.mindspice.lute.gameplay;

import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.random.RandomGenerator;

/** The rules and counters for one visit to the arena. Times are simulation seconds. */
public final class HoardRun {
    public static final int PLAYER_HEALTH = 1000;
    private static final int ROUND_SECONDS = 120;
    private static final int BASE_BATCH = 5;
    private static final int ROUND_BONUS_PERCENT = 5;
    private static final int BATCH_UNITS = 100 * 100; // size and round percentages

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
                spawnBatch(false);
                nextBatch += random.nextInt(20, 41);
            }
        }
    }

    private void startRound() {
        round++;
        roundStarted.accept(round);
        spawnBatch(true);
        nextBatch = random.nextInt(20, 41);
    }

    private void spawnBatch(boolean opening) {
        int sizePercent = opening ? 100 : random.nextInt(25, 51);
        int activeAreas = Math.min(4, 1 + (round - 1) / 5);
        int baseline = BASE_BATCH + activeAreas - 1;
        long units = (long) baseline * sizePercent * roundPercent();
        spawn.accept((int) ((units + BATCH_UNITS / 2) / BATCH_UNITS), activeAreas);
    }

    private long roundPercent() { return 100L + ROUND_BONUS_PERCENT * (long) round; }

    public void end() { gameOver = true; }
    public boolean isGameOver() { return gameOver; }

    // Credit is committed on delivery. Even a kill queued before death cannot revise the final score.
    public void enemyKilled(int points) {
        if (gameOver) { return; }
        kills++;
        score += points;
    }

    public long aliveSeconds() { return (round - 1L) * ROUND_SECONDS + (long) elapsed; }
    public long kills() { return kills; }
    public long score() { return score; }
    public int round() { return round; }
    public int remainingSeconds() { return (int) Math.ceil(ROUND_SECONDS - elapsed); }
}
