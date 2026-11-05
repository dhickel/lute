package io.mindspice.lute.engine.input;

// Circular BiInt Queue, used to queue primitives for a high performance queue with no GC impact


import java.util.Arrays;


public class CircularKeyQueue {
    private final InputAction[] inputActions;
    private final int[] keyStates;
    private final int capacity;
    private int head;
    private int tail;
    private volatile int size = 0;
    private boolean rejectIfFull;

    public CircularKeyQueue(int capacity) {
        if (capacity < 1) { throw new IllegalArgumentException("Queue capacity must be positive"); }
        this.capacity = capacity;
        this.inputActions = new InputAction[capacity];
        this.keyStates = new int[capacity];
        this.head = 0;
        this.tail = 0;
        this.size = 0;

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

    // Adds a new element to the queue if there is space available. Modulo operation is used to wrap around the tail
    // index when it reaches the end of the capacity. This allows it to wrap around for consumed values.



    public void add(InputAction inputAction, int keyState) {
        if (rejectIfFull && isFull()) { return; }
        inputActions[tail] = inputAction;
        keyStates[tail] = keyState;

        tail = (tail + 1) % capacity;
        if (size < capacity) {
            size++;
        } else {
            head = (head + 1) % capacity;
        }
    }

    public void poll(KeyActionConsumer consumer) {
        if (!isEmpty()) {
            InputAction action = inputActions[head];
            int state = keyStates[head];
            inputActions[head] = null;
            head = (head + 1) % capacity;
            size--;
            consumer.accept(action, state);
        }
    }



    public void consume(KeyActionConsumer consumer) {
        while (!isEmpty()) {
            poll(consumer);
        }
    }



    public void peek(KeyActionConsumer consumer) {
        if (!isEmpty()) {
            consumer.accept(inputActions[head], keyStates[head]);
        }
    }

    public String toString() {
        return Arrays.toString(inputActions);
    }
}
