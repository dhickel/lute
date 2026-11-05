package io.mindspice.lute.engine.input;

import com.badlogic.gdx.math.Vector2;
import io.mindspice.lute.engine.input.MouseEventConsumer.Type;

import java.util.BitSet;
import java.util.List;


public class MouseCacheListener implements MouseListener {
    private boolean isListening = true;
    private final CircularMouseQueue inputQueue;
    private final ActionType listeningFor;
    private final BitSet listeningForInputs = new BitSet(InputAction.values().length);
    private final Vector2 pos = new Vector2();

    public MouseCacheListener(ActionType listeningFor, int queueSize, List<InputAction> inputs) {
        this.listeningFor = listeningFor;
        inputQueue = new CircularMouseQueue(queueSize);
        inputs.forEach(i -> listeningForInputs.set(i.ordinal()));
    }

    @Override
    public boolean isListenerFor(ActionType actionType) {
        return listeningFor == actionType;
    }

    @Override
    public void offerPos(double x, double y) {
        if (isListening) {
            pos.set((float) x, (float) y);
        }
    }

    @Override
    public void offerButton(InputAction inputAction, int keyAction) {
        if (isListening && inputAction != null && listeningForInputs.get(inputAction.ordinal())) {
            inputQueue.add(Type.BUTTON, inputAction, keyAction, pos.x, pos.y);
        }
    }

    @Override
    public void offerScroll(int up) {
        if (isListening) {
            inputQueue.add(Type.SCROLL, null, up, pos.x, pos.y);
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

    @Override
    public ActionType getListeningFor() {
        return listeningFor;
    }

    public void consume(MouseEventConsumer consumer) {
        inputQueue.consume(consumer);
    }

    public boolean hasInputs() {
        return !inputQueue.isEmpty();
    }

    public CircularMouseQueue getQueue() {
        return inputQueue;
    }

    public Vector2 getPos() {
        return pos;
    }
}
