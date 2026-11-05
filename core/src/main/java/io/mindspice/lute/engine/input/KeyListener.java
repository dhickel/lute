package io.mindspice.lute.engine.input;

public interface KeyListener {

    boolean isListening();

    boolean isListenerFor(ActionType actionType);

    ActionType getListeningFor();

    void offer(InputAction inputAction, int keyAction);

    void setListening(boolean listening);

}
