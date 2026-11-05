package io.mindspice.lute.engine.input;

import java.util.BitSet;
import java.util.List;


public class KeyListener {
    private boolean isListening = true;
    private final CircularKeyQueue inputQueue;
    private final BitSet listeningForInputs = new BitSet(InputAction.values().length);

    public KeyListener(int queueSize, List<InputAction> inputs) {
        inputQueue = new CircularKeyQueue(queueSize);
        inputs.forEach(i -> listeningForInputs.set(i.ordinal()));
    }

    public void offer(InputAction inputAction, int keyAction) {
        if (isListening && listeningForInputs.get(inputAction.ordinal())) {
            inputQueue.add(inputAction, keyAction);
        }
    }

    public void setListening(boolean isListening) {
        this.isListening = isListening;
    }

    public boolean isListening() {
        return isListening;
    }

    public void consume(KeyActionConsumer consumer) {
        inputQueue.consume(consumer);
    }

    public boolean hasInputs() {
        return !inputQueue.isEmpty();
    }

    public CircularKeyQueue getQueue() {
        return inputQueue;
    }
}
