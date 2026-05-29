package com.badlogic.gdx.controllers.desktop.support;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.controllers.ControllerListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.controllers.desktop.JamepadControllerManager;
import com.studiohartman.jamepad.ControllerIndex;
import com.studiohartman.jamepad.ControllerManager;
import com.studiohartman.jamepad.ControllerUnpluggedException;

public class JamepadControllerMonitor implements Runnable {
    private static final long POLL_SLEEP_MS = 4L;

    private final ControllerManager controllerManager;
    private final ControllerListener listener;
    private final IntMap<Tuple> indexToController
        = new IntMap<>(JamepadControllerManager.jamepadConfiguration.maxNumControllers);
    // temporary array for delaying connect messages
    private final Array<JamepadController> connectedControllers = new Array<JamepadController>();
    private final Object stateLock = new Object();

    private volatile boolean running = true;
    private volatile boolean pendingReconcile = false;
    private Thread pollingThread;

    public JamepadControllerMonitor(ControllerManager controllerManager, ControllerListener listener) {
        this.controllerManager = controllerManager;
        this.listener = listener;

        reconcileControllers();
    }

    public void start() {
        pollingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    boolean controllersChanged;

                    synchronized (stateLock) {
                        controllersChanged = controllerManager.update();
                    }

                    if (controllersChanged) {
                        pendingReconcile = true;
                    }

                    try {
                        Thread.sleep(POLL_SLEEP_MS);
                    } catch (InterruptedException ignored) {
                        // stop requested
                    }
                }
            }
        }, "gdx-jamepad-monitor");
        pollingThread.setDaemon(true);
        pollingThread.start();

        Gdx.app.postRunnable(this);
    }

    public void stop() {
        running = false;

        if (pollingThread != null) {
            pollingThread.interrupt();
            try {
                pollingThread.join();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            pollingThread = null;
        }
    }

    @Override
    public void run() {
        if (!running) {
            return;
        }

        if (pendingReconcile) {
            synchronized (stateLock) {
                reconcileControllers();
                pendingReconcile = false;
            }
        }

        update();

        if (running) {
            Gdx.app.postRunnable(this);
        }
    }

    private void reconcileControllers() {
        // Break old connections to help detect disconnected objects later.
        for (Tuple tuple : indexToController.values()) {
            JamepadController controller = tuple.controller;
            tuple.index = null;
            controller.setControllerIndex(null);
        }

        // Get already-connected controllers paired with their existing objects.
        // Create objects for new controllers, but don't send connect messages yet.
        connectedControllers.clear();
        int numControllers = JamepadControllerManager.jamepadConfiguration.maxNumControllers;
        for (int i = 0; i < numControllers; i++) try {
            ControllerIndex controllerIndex = controllerManager.getControllerIndex(i);
            try {
                int instanceID = controllerIndex.getDeviceInstanceID();
                if (indexToController.containsKey(instanceID)) {
                    // Pre-existing controller, pair with existing object.
                    Tuple tuple1 = indexToController.get(instanceID);
                    tuple1.index = controllerIndex;
                    tuple1.controller.setControllerIndex(controllerIndex);
                } else {
                    // New controller. Create new object, and store it for connect message later.
                    Tuple tuple1 = new Tuple(controllerIndex);
                    indexToController.put(instanceID, tuple1);
                    connectedControllers.add(tuple1.controller);
                }
            } catch (ControllerUnpluggedException e) {
                // controller not connected, no need to pair it
            }
        } catch (ArrayIndexOutOfBoundsException t) {
            // more controllers connected than we can handle according to our config
        }

        // Remove disconnected objects.
        IntMap.Values<Tuple> values = indexToController.values();
        while (values.hasNext()) {
            Tuple tuple = values.next();
            if (tuple.index == null) {
                tuple.controller.setDisconnected();
                values.remove();
            }
        }

        // Set up listeners for new controllers and send connect messages.
        for (JamepadController controller: connectedControllers) {
            controller.addListener(listener);
            listener.connected(controller);
        }
    }

    private void update() {
        IntMap.Values<Tuple> values = indexToController.values();
        while (values.hasNext()) {
            Tuple tuple = values.next();
            JamepadController controller = tuple.controller;
            boolean connected = controller.update();

            if (!connected) {
                values.remove();
            }
        }
    }

    private class Tuple {
        public ControllerIndex index;
        public final JamepadController controller;

        public Tuple(ControllerIndex index) {
            this.index = index;
            this.controller = new JamepadController(index);
        }
    }
}
