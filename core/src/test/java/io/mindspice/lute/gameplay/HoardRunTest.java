package io.mindspice.lute.gameplay;

import java.util.ArrayList;
import java.util.Random;

public final class HoardRunTest {
    public static void main(String[] args) {
        for (int delay : new int[]{20, 30, 40}) {
            var rolls = new ArrayList<Integer>();
            var random = new Random(1) {
                int schedules;
                @Override public int nextInt(int origin, int bound) {
                    if (origin == 20 && bound == 41) {
                        schedules++;
                        return delay;
                    }
                    require(origin == 25 && bound == 51, "25–50 percent inclusive");
                    int percent = switch (delay) {
                        case 20 -> 25;
                        case 40 -> 50;
                        default -> super.nextInt(origin, bound);
                    };
                    rolls.add(percent);
                    return percent;
                }
            };
            var batches = new ArrayList<Integer>();
            var heals = new ArrayList<Integer>();
            var run = new HoardRun(random, () -> heals.add(batches.size()), batches::add, false);
            require(run.round() == 1 && run.remainingSeconds() == 180 && batches.equals(java.util.List.of(5)), "opening round");
            run.advance(delay - .25, false);
            require(batches.size() == 1 && run.remainingSeconds() == 181 - delay, "wait for batch; round time rounds up");
            run.advance(.25, false);
            require(rolls.size() == 1 && batches.size() == 2 && batches.getLast() == 5 * rolls.getFirst() / 100
                && heals.size() == 1, "first reinforcement rolls a smaller batch and does not heal");
            run.enemyKilled();
            run.advance(180 - delay, false);
            require(run.round() == 2 && run.remainingSeconds() == 180 && run.kills() == 1 && run.score() == 5, "round rollover");
            require(heals.equals(java.util.List.of(0, 1 + 179 / delay)), "opening batch takes precedence over simultaneous reinforcement");
            require(batches.getLast() == 5, "round two does not immediately ceil to six");
            // Verify both independent fractional budgets (including a day -> night crossing).
            for (int i = 2; i < 42; i++) { run.advance(180, i >= 12); }
            int starts = 0, extras = 0, roll = 0;
            require(random.schedules == batches.size() && rolls.size() == batches.size() - heals.size(),
                "fresh interval after every batch; size rolled only for reinforcements");
            long openingUnits = 0, extraUnits = 0;
            for (int r = 1; r <= 42; r++) {
                long opening = 5L * (99 + r) * (r >= 13 ? 115 : 100);
                starts += batches.get(heals.get(r - 1));
                openingUnits += opening;
                require(starts == openingUnits / 10_000, "opening scaling and carry at round " + r);
                if (r == 42) { break; }
                int firstExtra = heals.get(r - 1) + 1;
                require(heals.get(r) - firstExtra == 179 / delay, "scheduled reinforcement count");
                for (int i = firstExtra; i < heals.get(r); i++) {
                    extras += batches.get(i);
                    extraUnits += 5L * rolls.get(roll++) * (99 + r) * (r >= 12 ? 115 : 100);
                    require(extras == extraUnits / 1_000_000, "reinforcement scaling and carry at roll " + roll);
                }
            }

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
        require(!HoardRun.isNight(Math.nextDown(20 * 60.0)) && HoardRun.isNight(20 * 60)
            && HoardRun.isNight(0) && HoardRun.isNight(Math.nextDown(6 * 60.0))
            && !HoardRun.isNight(6 * 60), "overnight [20:00,06:00)");
        System.out.println("HoardRunTest: schedule, rounding, night, scoring and game over passed");
    }

    private static void require(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }
}
