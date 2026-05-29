package com.badlogic.gdx.controllers.desktop.support;

import com.badlogic.gdx.LifecycleListener;
import com.studiohartman.jamepad.ControllerManager;

public class JamepadShutdownHook implements LifecycleListener {
    private final ControllerManager controllerManager;
    private final JamepadControllerMonitor monitor;

    public JamepadShutdownHook(ControllerManager controllerManager, JamepadControllerMonitor monitor) {
        this.controllerManager = controllerManager;
        this.monitor = monitor;
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void dispose() {
        monitor.stop();
        controllerManager.quitSDLGamepad();
    }
}
