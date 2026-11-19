package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.EntitySystem;

/** Advances the session clock once per simulation update. */
public final class WorldTimeSystem extends EntitySystem {
    private static final int PRIORITY = -100;
    private static final double SECONDS_PER_DAY = 24 * 60 * 60;
    private static final double IN_GAME_MINUTES_PER_DAY = 24 * 60;
    private static final double INITIAL_TOTAL_SECONDS = 6 * 60 * 60;

    private final double inGameSecondsPerSimulationSecond;
    private double totalInGameSeconds = INITIAL_TOTAL_SECONDS;

    public WorldTimeSystem(double dayLengthMinutes) {
        super(PRIORITY);
        inGameSecondsPerSimulationSecond = IN_GAME_MINUTES_PER_DAY / dayLengthMinutes;
    }

    @Override
    public void update(float deltaTime) {
        totalInGameSeconds += deltaTime * inGameSecondsPerSimulationSecond;
    }

    public double getTotalInGameSeconds() {
        return totalInGameSeconds;
    }

    public double getDayLengthMinutes() {
        return IN_GAME_MINUTES_PER_DAY / inGameSecondsPerSimulationSecond;
    }

    public long getDayIndex() {
        return (long) (totalInGameSeconds / SECONDS_PER_DAY);
    }

    public double getMinuteOfDay() {
        return (totalInGameSeconds % SECONDS_PER_DAY) / 60.0;
    }
}
