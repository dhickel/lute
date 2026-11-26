package io.mindspice.lute.engine.screen;

import com.badlogic.gdx.Preferences;
import io.mindspice.lute.gameplay.HoardRun;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

final class HoardScores {
    static final int NAME_LIMIT = 16;
    static final int BOARD_SIZE = 5;
    private static final String BOARD = "topFive.v1";
    record Result(String name, long score, long kills, int round, long seconds) {
        Result {
            if (!name.matches("[a-zA-Z0-9 _-]{1,16}") || name.isBlank() || !name.equals(name.strip())) {
                throw new IllegalArgumentException("Invalid player name");
            }
            if (score < 0 || kills < 0 || round < 1 || seconds < 0) { throw new IllegalArgumentException("Invalid run totals"); }
        }
        static Result snapshot(String name, HoardRun run) {
            return new Result(name, run.score(), run.kills(), run.round(), run.aliveSeconds());
        }
        String encode() { return String.join("\t", name, "" + score, "" + kills, "" + round, "" + seconds); }
    }
    private final Preferences prefs;
    HoardScores(Preferences prefs) { this.prefs = prefs; }

    List<Result> record(Result result) {
        String old = prefs.getString(BOARD, "");
        List<Result> board = new ArrayList<>();
        try {
            if (old.length() > 1024) { throw new IllegalArgumentException(); }
            if (!old.isEmpty()) {
                for (String row : old.split("\n", -1)) {
                    var fields = row.split("\t", -1);
                    if (fields.length != 5 || board.size() == BOARD_SIZE) { throw new IllegalArgumentException(); }
                    board.add(new Result(fields[0], Long.parseLong(fields[1]), Long.parseLong(fields[2]),
                        Integer.parseInt(fields[3]), Long.parseLong(fields[4])));
                }
            }
        } catch (IllegalArgumentException e) { throw new IllegalStateException("Invalid local leaderboard; scores left unchanged", e); }
        board.add(result);
        // Stable sort: earlier saved runs win ties.
        board.sort(Comparator.comparingLong(Result::score).reversed());
        if (board.size() > BOARD_SIZE) { board.removeLast(); }
        try {
            prefs.putString(BOARD, board.stream().map(Result::encode).collect(Collectors.joining("\n")));
            prefs.flush();
        } catch (RuntimeException failure) {
            prefs.putString(BOARD, old);
            throw new IllegalStateException("Could not save local leaderboard", failure);
        }
        return List.copyOf(board);
    }
}
