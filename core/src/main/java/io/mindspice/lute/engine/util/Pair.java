package io.mindspice.lute.engine.util;

public record Pair<F, R>(
    F first,
    R second
) {
    public static <F, R> Pair<F, R> of(F f, R r) {
        return new Pair<>(f, r);
    }
}
