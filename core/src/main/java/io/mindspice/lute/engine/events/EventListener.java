package io.mindspice.lute.engine.events;

import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.systems.EventSystem;
import java.util.function.Consumer;

public interface EventListener {
    SubscriptionConsumer subscribeConsumer = GameCore.INSTANCE.getEngine().getSystem(EventSystem.class)::subscribe;
    SubscriptionConsumer unsubscribeConsumer = GameCore.INSTANCE.getEngine().getSystem(EventSystem.class)::unsubscribe;

    @FunctionalInterface
    interface SubscriptionConsumer {
        <T extends Event> void accept(Class<T> eventType, Consumer<T> eventConsumer);
    }
}
