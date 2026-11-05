package io.mindspice.lute.engine.input;

@FunctionalInterface
public interface MouseEventConsumer {
    enum Type {
        BUTTON,
        SCROLL
    }

    void accept(Type type, InputAction action, int value, double x, double y);
}
