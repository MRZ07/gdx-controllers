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

package com.badlogic.gdx.controllers.android;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnGenericMotionListener;
import android.view.View.OnKeyListener;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.backends.android.AndroidInput;
import com.badlogic.gdx.controllers.AbstractControllerManager;
import com.badlogic.gdx.controllers.ControllerListener;
import com.badlogic.gdx.controllers.event.ControllerEvent;
import com.badlogic.gdx.controllers.event.ControllerEventQueue;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntMap.Entry;

public class AndroidControllers extends AbstractControllerManager implements LifecycleListener, OnKeyListener, OnGenericMotionListener {
	private final static String TAG = "AndroidControllers";
	public static boolean ignoreNoGamepadButtons = true;
	public static boolean useNewAxisLogic = true;
	private final IntMap<AndroidController> controllerMap = new IntMap<AndroidController>();
	private final Array<ControllerListener> listeners = new Array<ControllerListener>();
	private final ControllerEventQueue eventQueue = new ControllerEventQueue();
	private final Object dispatchLock = new Object();

	public AndroidControllers() {
		listeners.add(new ManageCurrentControllerListener());
		Gdx.app.addLifecycleListener(this);
		gatherControllers(false);
		setupEventQueue();
		((AndroidInput)Gdx.input).addKeyListener(this);
		((AndroidInput)Gdx.input).addGenericMotionListener(this);
		
		// use InputManager on Android +4.1 to receive (dis-)connect events
		if(Gdx.app.getVersion() >= 16) {
			try {
				String className = "com.badlogic.gdx.controllers.android.ControllerLifeCycleListener";
				Class.forName(className).getConstructor(AndroidControllers.class).newInstance(this);
			} catch(Exception e) {
				Gdx.app.log(TAG, "Couldn't register controller life-cycle listener");
			}
		}
	}
	
	private void setupEventQueue() {
		new Runnable() {
			@SuppressWarnings("synthetic-access")
			@Override
			public void run () {
				synchronized (dispatchLock) {
					eventQueue.drain(new ControllerEventQueue.ControllerEventConsumer() {
						@Override
						public void consume(ControllerEvent event) {
							switch(event.type) {
								case ControllerEvent.CONNECTED:
									controllers.add(event.controller);
									for(ControllerListener listener: listeners) {
										listener.connected(event.controller);
									}
									break;
								case ControllerEvent.DISCONNECTED:
									controllers.removeValue(event.controller, true);
									for(ControllerListener listener: listeners) {
										listener.disconnected(event.controller);
									}
									AndroidController disconnectedController = (AndroidController)event.controller;
									for(ControllerListener listener: disconnectedController.getListeners()) {
										listener.disconnected(disconnectedController);
									}
									break;
								case ControllerEvent.BUTTON_DOWN:
									AndroidController buttonDownController = (AndroidController)event.controller;
									buttonDownController.buttons.put(event.code, event.code);
									for(ControllerListener listener: listeners) {
										if(listener.buttonDown(buttonDownController, event.code)) break;
									}
									for(ControllerListener listener: buttonDownController.getListeners()) {
										if(listener.buttonDown(buttonDownController, event.code)) break;
									}
									break;
								case ControllerEvent.BUTTON_UP:
									AndroidController buttonUpController = (AndroidController)event.controller;
									buttonUpController.buttons.remove(event.code, 0);
									for(ControllerListener listener: listeners) {
										if(listener.buttonUp(buttonUpController, event.code)) break;
									}
									for(ControllerListener listener: buttonUpController.getListeners()) {
										if(listener.buttonUp(buttonUpController, event.code)) break;
									}
									break;
								case ControllerEvent.AXIS:
									AndroidController axisController = (AndroidController)event.controller;
									axisController.axes[event.code] = event.amount;
									for(ControllerListener listener: listeners) {
										if(listener.axisMoved(axisController, event.code, event.amount)) break;
									}
									for(ControllerListener listener: axisController.getListeners()) {
										if(listener.axisMoved(axisController, event.code, event.amount)) break;
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
	public boolean onGenericMotion (View view, MotionEvent motionEvent) {
		if((motionEvent.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) == 0) return false;
		AndroidController controller = controllerMap.get(motionEvent.getDeviceId());
		if(controller != null) {
				synchronized(dispatchLock) {
					if (controller.hasPovAxis()) {
						float povX = motionEvent.getAxisValue(MotionEvent.AXIS_HAT_X);
						float povY = motionEvent.getAxisValue(MotionEvent.AXIS_HAT_Y);
						// map axis movement to dpad buttons
						if (povX != controller.povX) {
							if (controller.povX == 1f) {
								eventQueue.enqueueButtonUp(controller, KeyEvent.KEYCODE_DPAD_RIGHT, 0f);
							} else if (controller.povX == -1f) {
								eventQueue.enqueueButtonUp(controller, KeyEvent.KEYCODE_DPAD_LEFT, 0f);
							}

							if (povX == 1f) {
								eventQueue.enqueueButtonDown(controller, KeyEvent.KEYCODE_DPAD_RIGHT, 1f);
							} else if (povX == -1f) {
								eventQueue.enqueueButtonDown(controller, KeyEvent.KEYCODE_DPAD_LEFT, 1f);
							}
							controller.povX = povX;
						}

						if (povY != controller.povY) {
							if (controller.povY == 1f) {
								eventQueue.enqueueButtonUp(controller, KeyEvent.KEYCODE_DPAD_DOWN, 0f);
							} else if (controller.povY == -1f) {
								eventQueue.enqueueButtonUp(controller, KeyEvent.KEYCODE_DPAD_UP, 0f);
							}

							if (povY == 1f) {
								eventQueue.enqueueButtonDown(controller, KeyEvent.KEYCODE_DPAD_DOWN, 1f);
							} else if (povY == -1f) {
								eventQueue.enqueueButtonDown(controller, KeyEvent.KEYCODE_DPAD_UP, 1f);
							}
							controller.povY = povY;

					}
				}

					if (controller.hasTriggerAxis()){
						float lTrigger = motionEvent.getAxisValue(MotionEvent.AXIS_LTRIGGER);
						float rTrigger = motionEvent.getAxisValue(MotionEvent.AXIS_RTRIGGER);
						//map axis movement to trigger buttons
						if (lTrigger != controller.lTrigger){
							if (lTrigger == 1){
								eventQueue.enqueueButtonDown(controller, KeyEvent.KEYCODE_BUTTON_L2, 1f);
							} else if (lTrigger == 0){
								eventQueue.enqueueButtonUp(controller, KeyEvent.KEYCODE_BUTTON_L2, 0f);
							}
							controller.lTrigger = lTrigger;

					}

						if (rTrigger != controller.rTrigger){
							if (rTrigger == 1){
								eventQueue.enqueueButtonDown(controller, KeyEvent.KEYCODE_BUTTON_R2, 1f);
							} else if (rTrigger == 0){
								eventQueue.enqueueButtonUp(controller, KeyEvent.KEYCODE_BUTTON_R2, 0f);
							}
							controller.rTrigger = rTrigger;

					}
				}

					int axisIndex = 0;
            	for (int axisId: controller.axesIds) {
						float axisValue = motionEvent.getAxisValue(axisId);
						if(controller.getAxis(axisIndex) == axisValue) {
							axisIndex++;
							continue;
						}
						eventQueue.enqueueAxis(controller, axisIndex, axisValue);
						axisIndex++;
					}
				}
			return true;
		}
		return false;
	}

	@Override
	public boolean onKey (View view, int keyCode, KeyEvent keyEvent) {
		if (ignoreNoGamepadButtons && !KeyEvent.isGamepadButton(keyCode)) {
			return false;
		}
		AndroidController controller = controllerMap.get(keyEvent.getDeviceId());
		if(controller != null) {
			if(controller.getButton(keyCode) && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
				return true;
			}
			//ignore any button trigger input if there is a trigger axis
			if (controller.hasTriggerAxis() && (keyCode == KeyEvent.KEYCODE_BUTTON_L2 || keyCode == KeyEvent.KEYCODE_BUTTON_R2)){
				return true;
			}
			synchronized(dispatchLock) {
				if(keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
					eventQueue.enqueueButtonDown(controller, keyCode, 1f);
				} else {
					eventQueue.enqueueButtonUp(controller, keyCode, 0f);
				}
			}
			return keyCode != KeyEvent.KEYCODE_BACK || Gdx.input.isCatchKey(keyCode);
		} else {
			return false;
		}
	}
	
	private void gatherControllers(boolean sendEvent) {
		// gather all joysticks and gamepads, remove any disconnected ones
		IntMap<AndroidController> removedControllers = new IntMap<AndroidController>();
		removedControllers.putAll(controllerMap);
		
		for(int deviceId: InputDevice.getDeviceIds()) {
			AndroidController controller = controllerMap.get(deviceId);
			if(controller != null) {
				removedControllers.remove(deviceId);
			} else {
				addController(deviceId, sendEvent);
			}
		}
		
		for(Entry<AndroidController> entry: removedControllers.entries()) {
			removeController(entry.key);
		}
	}
	
	protected void addController(int deviceId, boolean sendEvent) {
		try {
			InputDevice device = InputDevice.getDevice(deviceId);
			if (!isController(device)) return;
			String name = device.getName();
			AndroidController controller = new AndroidController(deviceId, name);
			controllerMap.put(deviceId, controller);
			if (sendEvent) {
				synchronized (dispatchLock) {
					eventQueue.enqueueConnected(controller);
				}
			} else {
				controllers.add(controller);
			}
			Gdx.app.log(TAG, "added controller '" + name + "'");
		} catch (RuntimeException e) {
			// this exception is sometimes thrown by getDevice().
			// we can't use this device anyway, so ignore it and move on
			Gdx.app.error(TAG, "Could not get information about " + deviceId +
							", ignoring the device.", e);
		}
	}
	
	protected void removeController(int deviceId) {
		AndroidController controller = controllerMap.remove(deviceId);
		if(controller != null) {
			synchronized(dispatchLock) {
				controller.connected = false;
				eventQueue.enqueueDisconnected(controller);
			}
			Gdx.app.log(TAG, "removed controller '" + controller.getName() + "'");
		}
	}
	
	private boolean isController(InputDevice device) {
		return ((device.getSources() & InputDevice.SOURCE_CLASS_JOYSTICK) == InputDevice.SOURCE_CLASS_JOYSTICK)
				&& (((device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
				|| (device.getKeyboardType() != InputDevice.KEYBOARD_TYPE_ALPHABETIC))
				&& !"uinput-fpc".equals(device.getName());
	}

	@Override
	public void addListener (ControllerListener listener) {
		synchronized(dispatchLock) {
			listeners.add(listener);
		}
	}

	@Override
	public void removeListener (ControllerListener listener) {
		synchronized(dispatchLock) {
			listeners.removeValue(listener, true);
		}
	}

	@Override
	public void pause () {
		Gdx.app.log(TAG, "controllers paused");
	}

	@Override
	public void resume () {
		gatherControllers(true);
		Gdx.app.log(TAG, "controllers resumed");		
	}

	@Override
	public void dispose () {
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
