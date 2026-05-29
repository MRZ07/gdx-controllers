package com.badlogic.gdx.controllers.event;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.utils.Pool;

public final class ControllerEvent implements Pool.Poolable {
    public static final int CONNECTED = 1;
    public static final int DISCONNECTED = 2;
    public static final int BUTTON_DOWN = 3;
    public static final int BUTTON_UP = 4;
    public static final int AXIS = 5;

    public Controller controller;
    public int type;
    public int code;
    public float amount;

    @Override
    public void reset() {
        controller = null;
        type = 0;
        code = 0;
        amount = 0f;
    }
}
