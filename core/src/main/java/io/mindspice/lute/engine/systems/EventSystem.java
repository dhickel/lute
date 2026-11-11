package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.Gdx;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.util.Pair;

import java.util.*;
import java.util.function.Consumer;

public class EventSystem extends EntitySystem {
    private final Map<Class<? extends Event>, List<Consumer<? extends Event>>> listeners;
    List<Pair<Class<? extends Event>, Consumer<? extends Event>>> subscriptions;
    List<Pair<Class<? extends Event>, Consumer<? extends Event>>> unSubscriptions;

    private final Event[] eventQueue;
    private final Event[] delayedEventQueue;
    private int eventCount = 0;
    private int delayedEventCount = 0;

    public EventSystem() {
        this.listeners = new HashMap<>(20);
        this.eventQueue = new Event[1000];
        this.delayedEventQueue = new Event[1000];
        this.subscriptions = new ArrayList<>(20);
        this.unSubscriptions = new ArrayList<>(20);
    }


    public <T extends Event> void subscribe(Class<T> eventType, Consumer<T> eventConsumer) {
        subscriptions.add(Pair.of(eventType, eventConsumer));
    }

    public <T extends Event> void unsubscribe(Class<T> eventType, Consumer<T> eventConsumer) {
        unSubscriptions.add(Pair.of(eventType, eventConsumer));
    }

    private void subscribeConsumer(Pair<Class<? extends Event>, Consumer<? extends Event>> sub) {
        var listenerList = listeners.computeIfAbsent(sub.first(), k -> new ArrayList<>());
        listenerList.add(sub.second());
    }

    private void unsubscribeConsumer(Pair<Class<? extends Event>, Consumer<? extends Event>> unsub) {
        var eventType = unsub.first();
        var eventConsumer = unsub.second();
        var listenerList = listeners.get(eventType);
        if (listenerList != null) {
            listenerList.remove(eventConsumer);

            if (listenerList.isEmpty()) {
                listeners.remove(eventType);
            }
        }
    }

    public void emitEvent(Event event) {
        if (eventCount == eventQueue.length) {
            Gdx.app.error("EventSystem.emitEvent", "Overflow detected, dropping event: " + event.getClass());
            return;
        }
        eventQueue[eventCount] = event;
        eventCount++;
    }

    public void emitDelayedEvent(Event event) {
        if (delayedEventCount == delayedEventQueue.length) {
            Gdx.app.error("EventSystem.emitDelayedEvent", "Overflow detected, dropping event: " + event.getClass());
            return;
        }
        delayedEventQueue[delayedEventCount] = event;
        delayedEventCount++;
    }


    @SuppressWarnings("unchecked")
    private <T extends Event> void invoke(T event, Consumer<? extends Event> consumer) {
        ((Consumer<T>) consumer).accept(event);
    }

    @Override
    public void update(float deltaTime) {
        subscriptions.forEach(this::subscribeConsumer);
        subscriptions.clear();

        unSubscriptions.forEach(this::unsubscribeConsumer);
        unSubscriptions.clear();

        for (int i = 0; i < eventCount; ++i) {
            Event event = eventQueue[i];

            var listenerList = listeners.get(event.getClass());
            if (listenerList == null || listenerList.isEmpty()) { continue; }
            Gdx.app.debug("EventQueue", "Dispatch Event: " + event +" to listeners: " + listenerList.size());
            listenerList.forEach(l -> invoke(event, l));
        }
        Arrays.fill(eventQueue, 0, eventCount, null);
        eventCount = 0;


    }
}

