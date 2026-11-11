package io.mindspice.lute.engine.events;


import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.systems.EventSystem;

import java.util.function.Consumer;

public interface EventEmitter {
    Consumer<Event> eventEmitter = event ->
            GameCore.INSTANCE.getEngine().getSystem(EventSystem.class).emitEvent(event);

}
