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
            var batchRounds = new ArrayList<Integer>();
            var run = new HoardRun(random, round -> heals.add(batches.size()), (amount, round) -> {
                batches.add(amount);
                batchRounds.add(round);
            });
            require(run.round() == 1 && run.remainingSeconds() == 120 && batches.equals(java.util.List.of(5)), "opening round");
            run.advance(delay - .25);
            require(batches.size() == 1 && run.remainingSeconds() == 121 - delay, "wait for batch; round time rounds up");
            run.advance(.25);
            require(rolls.size() == 1 && batches.size() == 2 && batches.getLast() == Math.round(5 * rolls.getFirst() * .0105)
                && heals.size() == 1, "first reinforcement rolls a smaller batch and does not heal");
            run.enemyKilled(5);
            run.advance(119.75 - delay);
            require(run.round() == 1 && run.remainingSeconds() == 1, "still round one just before 120 seconds");
            run.advance(.25);
            require(run.round() == 2 && run.remainingSeconds() == 120 && run.kills() == 1 && run.score() == 5, "round rollover");
            require(heals.equals(java.util.List.of(0, 1 + 119 / delay)), "opening batch takes precedence over simultaneous reinforcement");
            require(batches.getLast() == 6, "round two opens with 5.5 rounded up to six");
            for (int i = 2; i < 10; i++) { run.advance(120); }
            require(run.round() == 10 && batches.getLast() == 9, "round ten opens with six scaled to nine");
            run.enemyKilled(10);
            require(run.kills() == 2 && run.score() == 15, "origin points do not depend on round");
            for (int i = 10; i < 42; i++) { run.advance(120); }
            int roll = 0;
            require(random.schedules == batches.size() && rolls.size() == batches.size() - heals.size(),
                "fresh interval after every batch; size rolled only for reinforcements");
            for (int r = 1; r <= 42; r++) {
                long roundPercent = 100 + 5L * r;
                int count = r < 6 ? 1 : r < 11 ? 2 : r < 16 ? 3 : 4;
                int baseline = 4 + count;
                require(batchRounds.get(heals.get(r - 1)) == r, "opening batch receives current round " + r);
                long opening = baseline * roundPercent;
                require(batches.get(heals.get(r - 1)) == (opening + 50) / 100,
                    "opening scaling and rounding at round " + r);
                if (r == 42) { break; }
                int firstExtra = heals.get(r - 1) + 1;
                require(heals.get(r) - firstExtra == 119 / delay, "scheduled reinforcement count");
                for (int i = firstExtra; i < heals.get(r); i++) {
                    require(batchRounds.get(i) == r, "reinforcement receives current round " + r);
                    long extra = (long) baseline * rolls.get(roll++) * roundPercent;
                    require(batches.get(i) == (extra + 5_000) / 10_000,
                        "reinforcement scaling and rounding at roll " + roll);
                }
            }

            run.advance(119.75);
            require(!run.isGameOver() && run.remainingSeconds() == 1, "last second before rollover");
            int batchCount = batches.size();
            run.end();
            run.advance(.25);
            run.enemyKilled(20);
            run.end();
            run.advance(540);
            require(run.isGameOver() && run.round() == 42 && run.remainingSeconds() == 1
                && run.kills() == 2 && run.score() == 15, "terminal state freezes time and credits");
            require(batches.size() == batchCount && heals.size() == 42, "no reinforcements or round heals after death");
        }
        System.out.println("HoardRunTest: schedule, rounding, scoring and game over passed");
    }

    private static void require(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }
}
