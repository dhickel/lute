package io.mindspice.lute.engine.input;

public interface KeyListener {

    boolean isListening();

    void offer(InputAction inputAction, int keyAction);

    void setListening(boolean listening);

}
