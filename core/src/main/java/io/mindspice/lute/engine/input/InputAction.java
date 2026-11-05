package io.mindspice.lute.engine.input;


public enum InputAction {
    RESIZE_WINDOW(ActionType.WINDOW),
    CLOSE_WINDOW(ActionType.WINDOW),
    MOVE_UP(ActionType.GAME_INPUT),
    MOVE_DOWN(ActionType.GAME_INPUT),
    MOVE_LEFT(ActionType.GAME_INPUT),
    MOVE_RIGHT(ActionType.GAME_INPUT),
    SCROLL_UP(ActionType.GAME_INPUT),
    SCROLL_DOWN(ActionType.GAME_INPUT),
    GUI_UP(ActionType.GUI),
    GUI_DOWN(ActionType.GUI),
    GUI_LEFT(ActionType.GUI),
    GUI_RIGHT(ActionType.GUI),
    GUI_TAB(ActionType.GUI),
    GUI_ENTER(ActionType.GUI),
    GUI_LEFT_CLICK(ActionType.GUI),
    GUI_RIGHT_CLICK(ActionType.GUI);

    public final ActionType actionType;

    InputAction(ActionType actionType) { this.actionType = actionType; }
}
