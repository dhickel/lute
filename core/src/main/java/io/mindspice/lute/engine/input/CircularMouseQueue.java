package io.mindspice.lute.engine.input;

import io.mindspice.lute.engine.input.MouseEventConsumer.Type;

import java.util.Objects;


public class CircularMouseQueue {
    private final Type[] eventTypes;
    private final InputAction[] inputActions;
    private final int[] values;
    private final double[] positionsX;
    private final double[] positionsY;
    private final int capacity;
    private int head;
    private int tail;
    private int size = 0;
    private boolean rejectIfFull;

    public CircularMouseQueue(int capacity) {
        if (capacity < 1) { throw new IllegalArgumentException("Queue capacity must be positive"); }
        this.capacity = capacity;
        eventTypes = new Type[capacity];
        inputActions = new InputAction[capacity];
        values = new int[capacity];
        positionsX = new double[capacity];
        positionsY = new double[capacity];
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean isFull() {
        return size == capacity;
    }

    public void setRejectIfFull(boolean doReject) {
        rejectIfFull = doReject;
    }

    public void add(Type type, InputAction action, int value, double x, double y) {
        Objects.requireNonNull(type);
        if (type == Type.BUTTON) { Objects.requireNonNull(action); }
        if (rejectIfFull && isFull()) { return; }
        eventTypes[tail] = type;
        inputActions[tail] = action;
        values[tail] = value;
        positionsX[tail] = x;
        positionsY[tail] = y;
        tail = (tail + 1) % capacity;
        if (size < capacity) {
            size++;
        } else {
            head = (head + 1) % capacity;
        }
    }

    public void poll(MouseEventConsumer consumer) {
        if (isEmpty()) { return; }
        Type type = eventTypes[head];
        InputAction action = inputActions[head];
        int value = values[head];
        double x = positionsX[head];
        double y = positionsY[head];
        eventTypes[head] = null;
        inputActions[head] = null;
        head = (head + 1) % capacity;
        size--;
        consumer.accept(type, action, value, x, y);
    }

    public void consume(MouseEventConsumer consumer) {
        while (!isEmpty()) {
            poll(consumer);
        }
    }

    public void peek(MouseEventConsumer consumer) {
        if (!isEmpty()) {
            consumer.accept(eventTypes[head], inputActions[head], values[head], positionsX[head], positionsY[head]);
        }
    }
}
