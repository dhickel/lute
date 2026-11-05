package io.mindspice.lute.engine.input;

import com.badlogic.gdx.math.Vector2;

import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.IntConsumer;


public class MouseCallBackListener implements MouseListener {
    private final Vector2 pos = new Vector2();
    private boolean isListening = true;
    private KeyActionConsumer buttonConsumer;
    private BiDoubleConsumer posConsumer;
    private IntConsumer scrollConsumer;

    public boolean isListening() {
        return isListening;
    }

    public void setPosConsumer(BiDoubleConsumer posConsumer) {
        this.posConsumer = posConsumer;
    }

    public void setScrollConsumer(IntConsumer scrollConsumer) {
        this.scrollConsumer = scrollConsumer;
    }

    public void setButtonConsumer(KeyActionConsumer buttonConsumer) {
        this.buttonConsumer = buttonConsumer;
    }

    @Override
    public void offerPos(double x, double y) {
        if (!isListening) { return; }
        pos.x = (float) x;
        pos.y = (float) y;
        if (posConsumer != null) {
            posConsumer.accept(x, y);
        }
    }

    @Override
    public void offerButton(InputAction inputAction, int keyAction) {
        if (isListening && buttonConsumer != null) {
            buttonConsumer.accept(inputAction, keyAction);
        }
    }

    @Override
    public void offerScroll(int val) {
        if (isListening && scrollConsumer != null) {
            scrollConsumer.accept(val);
        }
    }

    @Override
    public void setListening(boolean isListening) {
        this.isListening = isListening;
    }

    public void consume(Consumer<Vector2> consumer) {
        consumer.accept(pos);
    }

    public Vector2 getPos() {
        return pos;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", MouseCallBackListener.class.getSimpleName() + "[", "]")
                .add("pos=" + pos)
                .add("isListening=" + isListening)
                .add("buttonConsumer=" + buttonConsumer)
                .add("posConsumer=" + posConsumer)
                .add("scrollConsumer=" + scrollConsumer)
                .toString();
    }
}
