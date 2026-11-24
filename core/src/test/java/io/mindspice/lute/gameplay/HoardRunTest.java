package io.mindspice.lute.gameplay;

import java.util.ArrayList;
import java.util.Random;

public final class HoardRunTest {
    public static void main(String[] args) {
        for (int delay : new int[]{20, 30, 40}) {
            var random = new Random(1) {
                @Override public int nextInt(int origin, int bound) {
                    require(origin == 20 && bound == 41, "20–40 seconds inclusive");
                    return delay;
                }
            };
            var batches = new ArrayList<Integer>();
            var heals = new ArrayList<Integer>();
            var run = new HoardRun(random, () -> heals.add(batches.size()), batches::add, false);
            require(run.round() == 1 && run.remainingSeconds() == 180 && batches.equals(java.util.List.of(5)), "opening round");
            run.advance(delay - .25, false);
            require(batches.size() == 1 && run.remainingSeconds() == 181 - delay, "wait for batch; round time rounds up");
            run.advance(.25, false);
            require(batches.size() == 2 && batches.getLast() == 5 && heals.size() == 1, "first reinforcement does not heal");
            run.enemyKilled();
            run.advance(180 - delay, false);
            require(run.round() == 2 && run.remainingSeconds() == 180 && run.kills() == 1 && run.score() == 5, "round rollover");
            require(heals.equals(java.util.List.of(0, 1 + 179 / delay)), "opening batch takes precedence over simultaneous reinforcement");
            require(batches.getLast() == 5, "round two does not immediately ceil to six");
            // Verify both independent fractional budgets (including a day -> night crossing).
            for (int i = 2; i < 42; i++) { run.advance(180, i >= 12); }
            int starts = 0, extras = 0;
            long openingUnits = 0, extraUnits = 0;
            for (int r = 1; r <= 42; r++) {
                long opening = 5L * (99 + r) * (r >= 13 ? 115 : 100);
                starts += batches.get(heals.get(r - 1));
                openingUnits += opening;
                if (r < 42) {
                    int count = 179 / delay;
                    extras += batches.subList(heals.get(r - 1) + 1, heals.get(r)).stream().mapToInt(Integer::intValue).sum();
                    extraUnits += 5L * (99 + r) * (r >= 12 ? 115 : 100) * count;
                }
            }
            require(starts == openingUnits / 10000 && extras == extraUnits / 10000, "night + linear growth, no lost fractions");

            run.advance(179.75, false);
            require(!run.isGameOver() && run.remainingSeconds() == 1, "last second before rollover");
            int batchCount = batches.size();
            run.end();
            run.advance(.25, false);
            run.enemyKilled();
            run.end();
            run.advance(540, true);
            require(run.isGameOver() && run.round() == 42 && run.remainingSeconds() == 1
                && run.kills() == 1 && run.score() == 5, "terminal state freezes time and credits");
            require(batches.size() == batchCount && heals.size() == 42, "no reinforcements or round heals after death");
        }
        require(HoardRun.isNight(18 * 60) && HoardRun.isNight(0) && HoardRun.isNight(599.999)
            && !HoardRun.isNight(600) && !HoardRun.isNight(1079.999), "overnight [18:00,10:00)");
        System.out.println("HoardRunTest: schedule, rounding, night, scoring and game over passed");
    }

    private static void require(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }
}
