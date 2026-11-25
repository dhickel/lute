package io.mindspice.lute.gameplay;

import java.util.ArrayList;
import java.util.Random;

public final class HoardRunTest {
    public static void main(String[] args) {
        for (int delay : new int[]{20, 30, 40}) {
            var random = new Random(1) {
                int schedules;
                @Override public int nextInt(int origin, int bound) {
                    require(origin == 20 && bound == 41, "20–40 second interval inclusive; no size roll");
                    schedules++;
                    return delay;
                }
            };
            var batches = new ArrayList<Integer>();
            var heals = new ArrayList<Integer>();
            var batchRounds = new ArrayList<Integer>();
            var run = new HoardRun(random, round -> heals.add(batches.size()), (amount, round) -> {
                batches.add(amount);
                batchRounds.add(round);
            });
            require(run.round() == 1 && run.remainingSeconds() == 120 && batches.equals(java.util.List.of(10)), "opening round starts with ten enemies");
            run.advance(delay - .25);
            require(batches.size() == 1 && run.remainingSeconds() == 121 - delay, "wait for batch; round time rounds up");
            run.advance(.25);
            require(batches.size() == 2 && batches.getLast() == 5 && heals.size() == 1,
                "first refresh spawns five and does not heal");
            run.enemyKilled(5);
            run.advance(119.75 - delay);
            require(run.round() == 1 && run.remainingSeconds() == 1, "still round one just before 120 seconds");
            run.advance(.25);
            require(run.round() == 2 && run.remainingSeconds() == 120 && run.kills() == 1 && run.score() == 5, "round rollover");
            require(heals.equals(java.util.List.of(0, 1 + 119 / delay)), "opening batch takes precedence over simultaneous reinforcement");
            require(batches.getLast() == 5, "round two opens with five");
            for (int i = 2; i < 10; i++) { run.advance(120); }
            require(run.round() == 10 && batches.getLast() == 5, "round ten opens with five");
            run.enemyKilled(10);
            require(run.kills() == 2 && run.score() == 15, "origin points do not depend on round");
            for (int i = 10; i < 42; i++) { run.advance(120); }
            require(random.schedules == batches.size(), "fresh interval after every batch");
            for (int r = 1; r <= 42; r++) {
                require(batchRounds.get(heals.get(r - 1)) == r, "opening batch receives current round " + r);
                require(batches.get(heals.get(r - 1)) == (r == 1 ? 10 : 5), "opening batch size at round " + r);
                if (r == 42) { break; }
                int firstExtra = heals.get(r - 1) + 1;
                require(heals.get(r) - firstExtra == 119 / delay, "scheduled reinforcement count");
                for (int i = firstExtra; i < heals.get(r); i++) {
                    require(batchRounds.get(i) == r, "reinforcement receives current round " + r);
                    require(batches.get(i) == 5, "scheduled refresh size at round " + r);
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
        System.out.println("HoardRunTest: schedule, batch sizes, scoring and game over passed");
    }

    private static void require(boolean value, String message) {
        if (!value) { throw new AssertionError(message); }
    }
}
