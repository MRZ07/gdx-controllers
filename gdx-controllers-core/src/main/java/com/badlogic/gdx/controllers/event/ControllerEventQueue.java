package com.badlogic.gdx.controllers.event;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

public final class ControllerEventQueue {
    private final Array<ControllerEvent> queue = new Array<ControllerEvent>();
    private final Pool<ControllerEvent> eventPool = new Pool<ControllerEvent>() {
        @Override
        protected ControllerEvent newObject() {
            return new ControllerEvent();
        }
    };

    public void enqueueConnected(Controller controller) {
        enqueue(controller, ControllerEvent.CONNECTED, 0, 0f);
    }

    public void enqueueDisconnected(Controller controller) {
        enqueue(controller, ControllerEvent.DISCONNECTED, 0, 0f);
    }

    public void enqueueButtonDown(Controller controller, int code, float amount) {
        enqueue(controller, ControllerEvent.BUTTON_DOWN, code, amount);
    }

    public void enqueueButtonUp(Controller controller, int code, float amount) {
        enqueue(controller, ControllerEvent.BUTTON_UP, code, amount);
    }

    public void enqueueAxis(Controller controller, int axis, float amount) {
        enqueue(controller, ControllerEvent.AXIS, axis, amount);
    }

    public void drain(ControllerEventConsumer consumer) {
        synchronized (queue) {
            for (ControllerEvent event : queue) {
                consumer.consume(event);
            }
            eventPool.freeAll(queue);
            queue.clear();
        }
    }

    private void enqueue(Controller controller, int type, int code, float amount) {
        synchronized (queue) {
            ControllerEvent event = eventPool.obtain();
            event.controller = controller;
            event.type = type;
            event.code = code;
            event.amount = amount;
            queue.add(event);
        }
    }

    public interface ControllerEventConsumer {
        void consume(ControllerEvent event);
    }
}
