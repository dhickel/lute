package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
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
    private int eventCount = 0;
    private boolean dispatching;
    private boolean retired;

    public EventSystem() {
        this.listeners = new HashMap<>(20);
        this.eventQueue = new Event[1000];
        this.subscriptions = new ArrayList<>(20);
        this.unSubscriptions = new ArrayList<>(20);
    }


    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        retired = false;
        var audio = engine.getSystem(AudioSystem.class);
        if (audio != null) { audio.attachEvents(this); }
    }

    @Override
    public void removedFromEngine(Engine engine) {
        retired = true;
        var audio = engine.getSystem(AudioSystem.class);
        if (audio != null) { audio.detachEvents(this); }
        // A discarded bus may never update again. In-flight dispatch finishes cleanup in finally.
        if (!dispatching) {
            Arrays.fill(eventQueue, 0, eventCount, null);
            eventCount = 0;
            applyQueuedChanges();
        }
        super.removedFromEngine(engine);
    }

    public <T extends Event> void subscribe(Class<T> eventType, Consumer<T> eventConsumer) {
        subscriptions.add(Pair.of(eventType, eventConsumer));
    }

    public <T extends Event> void unsubscribe(Class<T> eventType, Consumer<T> eventConsumer) {
        // Cancel only earlier pending registrations; a later subscribe must survive this removal.
        subscriptions.removeIf(sub -> sub.first() == eventType && sub.second() == eventConsumer);
        unSubscriptions.add(Pair.of(eventType, eventConsumer));
    }

    private void applyQueuedChanges() {
        unSubscriptions.forEach(this::unsubscribeConsumer);
        unSubscriptions.clear();
        subscriptions.forEach(this::subscribeConsumer);
        subscriptions.clear();
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
            listenerList.removeIf(consumer -> consumer == eventConsumer);

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


    @SuppressWarnings("unchecked")
    private <T extends Event> void invoke(T event, Consumer<? extends Event> consumer) {
        ((Consumer<T>) consumer).accept(event);
    }

    @Override
    public void update(float deltaTime) {
        applyQueuedChanges();
        dispatching = true;
        try {
            for (int i = 0; i < eventCount && !retired; ++i) {
                Event event = eventQueue[i];
                if (event instanceof Event.Sensor.Enter || event instanceof Event.Sensor.Exit) {
                    Gdx.app.debug("Sensor", event.toString());
                }

                var listenerList = listeners.get(event.getClass());
                if (listenerList == null || listenerList.isEmpty()) { continue; }
                Gdx.app.debug("EventQueue", "Dispatch Event: " + event +" to listeners: " + listenerList.size());
                for (var consumer : listenerList) {
                    if (retired) { break; }
                    invoke(event, consumer);
                }
            }
        } finally {
            Arrays.fill(eventQueue, 0, eventCount, null);
            eventCount = 0;
            dispatching = false;
            applyQueuedChanges();
        }
    }
}

