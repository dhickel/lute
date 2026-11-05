package io.mindspice.lute.engine.input;

import java.util.BitSet;
import java.util.List;


public class KeyCallBackListener implements KeyListener {
    private boolean isListening = true;
    private final CircularKeyQueue inputQueue;
    private final KeyActionConsumer consumer;
    private final BitSet listeningForInputs = new BitSet(InputAction.values().length);

    public KeyCallBackListener(List<InputAction> inputs, int queueSize, KeyActionConsumer consumer) {
        inputs.forEach(i -> listeningForInputs.set(i.ordinal()));
        inputQueue = new CircularKeyQueue(queueSize);
        this.consumer = consumer;

    }

    @Override
    public void offer(InputAction inputAction, int keyAction) {
        if (isListening && listeningForInputs.get(inputAction.ordinal())) {
            consumer.accept(inputAction, keyAction);
        }
    }

    @Override
    public void setListening(boolean isListening) {
        this.isListening = isListening;
    }

    @Override
    public boolean isListening() {
        return isListening;
    }

    public CircularKeyQueue getQueue() {
        return inputQueue;
    }
}
