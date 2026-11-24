package io.mindspice.lute.gameplay;

import java.util.function.IntConsumer;
import java.util.random.RandomGenerator;

/** The rules and counters for one visit to the arena. Times are simulation seconds. */
public final class HoardRun {
    public static final int PLAYER_HEALTH = 1000;
    private static final int ROUND_SECONDS = 180;
    private static final int BASE_BATCH = 5;
    private static final int NIGHT_START_MINUTE = 20 * 60;
    private static final int NIGHT_END_MINUTE = 6 * 60;
    private static final int BATCH_UNITS = 100 * 100 * 100; // size, round and night percentages

    private final RandomGenerator random;
    private final Runnable healPlayer;
    private final IntConsumer spawn;
    private boolean gameOver;
    private int round;
    private long kills;
    private double elapsed;
    private int nextBatch;
    private int openingRemainder, extraRemainder;

    public HoardRun(RandomGenerator random, Runnable healPlayer, IntConsumer spawn, boolean night) {
        this.random = random;
        this.healPlayer = healPlayer;
        this.spawn = spawn;
        startRound(night);
    }

    public static boolean isNight(double minuteOfDay) {
        return minuteOfDay >= NIGHT_START_MINUTE || minuteOfDay < NIGHT_END_MINUTE;
    }

    public void advance(double seconds, boolean night) {
        if (gameOver) { return; }
        elapsed += seconds;
        while (elapsed >= Math.min(nextBatch, ROUND_SECONDS)) {
            if (nextBatch >= ROUND_SECONDS) {
                elapsed -= ROUND_SECONDS;
                startRound(night);
            } else {
                spawnBatch(false, night);
                nextBatch += random.nextInt(20, 41);
            }
        }
    }

    private void startRound(boolean night) {
        round++;
        healPlayer.run();
        spawnBatch(true, night);
        nextBatch = random.nextInt(20, 41);
    }

    private void spawnBatch(boolean opening, boolean night) {
        // Linear percentage points on the baseline. Multiply that budget by 1.15 at night.
        // Carry fixed-point fractions separately so neither batch type loses its growth to rounding.
        int sizePercent = opening ? 100 : random.nextInt(25, 51);
        long units = (long) BASE_BATCH * sizePercent * (100L + round - 1) * (night ? 115 : 100);
        units += opening ? openingRemainder : extraRemainder;
        int remainder = (int) (units % BATCH_UNITS);
        if (opening) { openingRemainder = remainder; }
        else { extraRemainder = remainder; }
        spawn.accept((int) (units / BATCH_UNITS));
    }

    public void end() { gameOver = true; }
    public boolean isGameOver() { return gameOver; }

    // Credit is committed on delivery. Even a kill queued before death cannot revise the final score.
    public void enemyKilled() { if (!gameOver) { kills++; } }
    public long kills() { return kills; }
    public long score() { return 5L * kills; }
    public int round() { return round; }
    public int remainingSeconds() { return (int) Math.ceil(ROUND_SECONDS - elapsed); }
}
