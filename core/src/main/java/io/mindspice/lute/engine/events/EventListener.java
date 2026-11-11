package io.mindspice.lute.engine.events;

import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.systems.EventSystem;
import java.util.function.Consumer;

public interface EventListener {
    SubscriptionConsumer eventSubscriber = new SubscriptionConsumer() {
        @Override
        public <T extends Event> void accept(Class<T> eventType, Consumer<T> eventConsumer) {
            GameCore.INSTANCE.getEngine().getSystem(EventSystem.class).subscribe(eventType, eventConsumer);
        }
    };
    SubscriptionConsumer eventUnSubscriber = new SubscriptionConsumer() {
        @Override
        public <T extends Event> void accept(Class<T> eventType, Consumer<T> eventConsumer) {
            GameCore.INSTANCE.getEngine().getSystem(EventSystem.class).unsubscribe(eventType, eventConsumer);
        }
    };

    @FunctionalInterface
    interface SubscriptionConsumer {
        <T extends Event> void accept(Class<T> eventType, Consumer<T> eventConsumer);
    }
}
