package io.mindspice.lute.engine.input;



@FunctionalInterface
public interface KeyActionConsumer {
    void accept(InputAction input, int value);
}
