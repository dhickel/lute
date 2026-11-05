package io.mindspice.lute.engine.input;

public interface MouseListener {
    boolean isListening();

    boolean isListenerFor(ActionType actionType);

    ActionType getListeningFor();

    void offerPos(double x, double y);

    void offerButton(InputAction inputAction, int keyAction);

    void offerScroll(int up);

    public void setListening(boolean listening);
}
