package com.badlogic.gdx.controllers.event;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerListener;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.ControllerPowerLevel;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ControllerEventQueueTest {

    @Test
    public void keepsInsertionOrder() {
        ControllerEventQueue queue = new ControllerEventQueue();
        Controller controller = new StubController("pad-1");

        queue.enqueueConnected(controller);
        queue.enqueueButtonDown(controller, 4, 1f);

        List<EventSnapshot> events = drain(queue);

        Assert.assertEquals(2, events.size());
        Assert.assertEquals(ControllerEvent.CONNECTED, events.get(0).type);
        Assert.assertEquals(ControllerEvent.BUTTON_DOWN, events.get(1).type);
    }

    @Test
    public void canDrainRepeatedly() {
        ControllerEventQueue queue = new ControllerEventQueue();
        Controller controller = new StubController("pad-1");

        queue.enqueueConnected(controller);
        List<EventSnapshot> firstDrain = drain(queue);
        List<EventSnapshot> secondDrain = drain(queue);

        queue.enqueueDisconnected(controller);
        List<EventSnapshot> thirdDrain = drain(queue);

        Assert.assertEquals(1, firstDrain.size());
        Assert.assertEquals(ControllerEvent.CONNECTED, firstDrain.get(0).type);
        Assert.assertEquals(0, secondDrain.size());
        Assert.assertEquals(1, thirdDrain.size());
        Assert.assertEquals(ControllerEvent.DISCONNECTED, thirdDrain.get(0).type);
    }

    @Test
    public void keepsPayloadValues() {
        ControllerEventQueue queue = new ControllerEventQueue();
        Controller controller = new StubController("pad-1");

        queue.enqueueButtonUp(controller, 8, 0.5f);
        queue.enqueueAxis(controller, 2, -0.25f);

        List<EventSnapshot> events = drain(queue);

        Assert.assertEquals(2, events.size());
        assertEvent(events.get(0), ControllerEvent.BUTTON_UP, controller, 8, 0.5f);
        assertEvent(events.get(1), ControllerEvent.AXIS, controller, 2, -0.25f);
    }

    private static List<EventSnapshot> drain(ControllerEventQueue queue) {
        final List<EventSnapshot> snapshots = new ArrayList<EventSnapshot>();

        queue.drain(new ControllerEventQueue.ControllerEventConsumer() {
            @Override
            public void consume(ControllerEvent event) {
                EventSnapshot snapshot = new EventSnapshot();
                snapshot.type = event.type;
                snapshot.controller = event.controller;
                snapshot.code = event.code;
                snapshot.amount = event.amount;
                snapshots.add(snapshot);
            }
        });

        return snapshots;
    }

    private static void assertEvent(EventSnapshot event, int type, Controller controller, int code, float amount) {
        Assert.assertEquals(type, event.type);
        Assert.assertSame(controller, event.controller);
        Assert.assertEquals(code, event.code);
        Assert.assertEquals(amount, event.amount, 0.0001f);
    }

    private static class EventSnapshot {
        int type;
        Controller controller;
        int code;
        float amount;
    }

    private static class StubController implements Controller {
        private final String id;

        private StubController(String id) {
            this.id = id;
        }

        @Override
        public boolean getButton(int buttonCode) {
            return false;
        }

        @Override
        public float getAxis(int axisCode) {
            return 0;
        }

        @Override
        public String getName() {
            return id;
        }

        @Override
        public String getUniqueId() {
            return id;
        }

        @Override
        public int getMinButtonIndex() {
            return 0;
        }

        @Override
        public int getMaxButtonIndex() {
            return 0;
        }

        @Override
        public int getAxisCount() {
            return 0;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public boolean canVibrate() {
            return false;
        }

        @Override
        public boolean isVibrating() {
            return false;
        }

        @Override
        public void startVibration(int duration, float strength) {
        }

        @Override
        public void cancelVibration() {
        }

        @Override
        public boolean supportsPlayerIndex() {
            return false;
        }

        @Override
        public int getPlayerIndex() {
            return PLAYER_IDX_UNSET;
        }

        @Override
        public void setPlayerIndex(int index) {
        }

        @Override
        public ControllerMapping getMapping() {
            return null;
        }

        @Override
        public ControllerPowerLevel getPowerLevel() {
            return ControllerPowerLevel.POWER_UNKNOWN;
        }

        @Override
        public void addListener(ControllerListener listener) {
        }

        @Override
        public void removeListener(ControllerListener listener) {
        }
    }
}
