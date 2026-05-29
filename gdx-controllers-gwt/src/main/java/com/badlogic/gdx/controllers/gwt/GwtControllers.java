/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.controllers.gwt;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.controllers.AbstractControllerManager;
import com.badlogic.gdx.controllers.ControllerListener;
import com.badlogic.gdx.controllers.event.ControllerEvent;
import com.badlogic.gdx.controllers.event.ControllerEventQueue;
import com.badlogic.gdx.controllers.gwt.support.Gamepad;
import com.badlogic.gdx.controllers.gwt.support.GamepadButton;
import com.badlogic.gdx.controllers.gwt.support.GamepadSupport;
import com.badlogic.gdx.controllers.gwt.support.GamepadSupportListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayNumber;

public class GwtControllers extends AbstractControllerManager implements GamepadSupportListener {

	private final IntMap<GwtController> controllerMap = new IntMap<GwtController>();
	private final Array<ControllerListener> listeners = new Array<ControllerListener>();
	private final ControllerEventQueue eventQueue = new ControllerEventQueue();
	private final Object dispatchLock = new Object();

	public GwtControllers () {
		GamepadSupport.init(this);
		listeners.add(new ManageCurrentControllerListener());
		setupEventQueue();
	}

	public void setupEventQueue () {
		new Runnable() {
			@SuppressWarnings("synthetic-access")
			@Override
			public void run () {
				synchronized (dispatchLock) {
					eventQueue.drain(new ControllerEventQueue.ControllerEventConsumer() {
						@Override
						public void consume(ControllerEvent event) {
							switch (event.type) {
								case ControllerEvent.CONNECTED:
									controllers.add(event.controller);
									for (ControllerListener listener : listeners) {
										listener.connected(event.controller);
									}
									break;
								case ControllerEvent.DISCONNECTED:
									controllers.removeValue(event.controller, true);
									for (ControllerListener listener : listeners) {
										listener.disconnected(event.controller);
									}
									GwtController disconnectedController = (GwtController)event.controller;
									for (ControllerListener listener : disconnectedController.getListeners()) {
										listener.disconnected(disconnectedController);
									}
									break;
								case ControllerEvent.BUTTON_DOWN:
									GwtController buttonDownController = (GwtController)event.controller;
									buttonDownController.buttons.put(event.code, event.amount);
									for (ControllerListener listener : listeners) {
										if (listener.buttonDown(buttonDownController, event.code)) break;
									}
									for (ControllerListener listener : buttonDownController.getListeners()) {
										if (listener.buttonDown(buttonDownController, event.code)) break;
									}
									break;
								case ControllerEvent.BUTTON_UP:
									GwtController buttonUpController = (GwtController)event.controller;
									buttonUpController.buttons.remove(event.code, event.amount);
									for (ControllerListener listener : listeners) {
										if (listener.buttonUp(buttonUpController, event.code)) break;
									}
									for (ControllerListener listener : buttonUpController.getListeners()) {
										if (listener.buttonUp(buttonUpController, event.code)) break;
									}
									break;
								case ControllerEvent.AXIS:
									GwtController axisController = (GwtController)event.controller;
									axisController.axes[event.code] = event.amount;
									for (ControllerListener listener : listeners) {
										if (listener.axisMoved(axisController, event.code, event.amount)) break;
									}
									for (ControllerListener listener : axisController.getListeners()) {
										if (listener.axisMoved(axisController, event.code, event.amount)) break;
									}
									break;
								default:
							}
						}
					});
				}
				Gdx.app.postRunnable(this);
			}
		}.run();
	}

	@Override
	public void addListener (ControllerListener listener) {
		synchronized (dispatchLock) {
			listeners.add(listener);
		}
	}

	@Override
	public void removeListener (ControllerListener listener) {
		synchronized (dispatchLock) {
			listeners.removeValue(listener, true);
		}
	}

	@Override
	public void onGamepadConnected (int index) {
		Gamepad gamepad = Gamepad.getGamepad(index);
		GwtController controller = new GwtController(gamepad.getIndex(), gamepad.getId());
		controllerMap.put(index, controller);
		synchronized (dispatchLock) {
			eventQueue.enqueueConnected(controller);
		}
	}

	@Override
	public void onGamepadDisconnected (int index) {
		GwtController controller = controllerMap.remove(index);
		if (controller != null) {
			synchronized (dispatchLock) {
			    controller.connected = false;
				eventQueue.enqueueDisconnected(controller);
			}
		}
	}

	@Override
	public void onGamepadUpdated (int index) {
		Gamepad gamepad = Gamepad.getGamepad(index);
		GwtController controller = controllerMap.get(index);
		if (gamepad != null && controller != null) {
			// Determine what changed
			JsArrayNumber axes = gamepad.getAxes();
			JsArray<GamepadButton> buttons = gamepad.getButtons();
			synchronized (dispatchLock) {
				for (int i = 0, j = axes.length(); i < j; i++) {
					float oldAxis = controller.getAxis(i);
					float newAxis = (float)axes.get(i);
					if (oldAxis != newAxis) {
						eventQueue.enqueueAxis(controller, i, newAxis);
					}
				}
				for (int i = 0, j = buttons.length(); i < j; i++) {
					float newButton = (float)buttons.get(i).getValue();
					float oldButton = controller.getButtonValue(i);
					if (oldButton != newButton) {
						if ((oldButton < 0.5f && newButton < 0.5f) || (oldButton >= 0.5f && newButton >= 0.5f)) {
							controller.buttons.put(i, newButton);
							continue;
						}

						if (newButton >= 0.5f) {
							eventQueue.enqueueButtonDown(controller, i, newButton);
						} else {
							eventQueue.enqueueButtonUp(controller, i, newButton);
						}
					}
				}
			}
		}
	}

	@Override
	public void clearListeners () {
		listeners.clear();
		listeners.add(new ManageCurrentControllerListener());
	}

	@Override
	public Array<ControllerListener> getListeners () {
		return listeners;
	}
}
