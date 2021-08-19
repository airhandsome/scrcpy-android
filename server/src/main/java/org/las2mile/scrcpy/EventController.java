package org.las2mile.scrcpy;

import android.graphics.Point;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import org.las2mile.scrcpy.wrappers.InputManager;
import java.io.IOException;

public class EventController {

    private final Device device;
    private final DroidConnection connection;
    public static final int MAX_POINTERS = 5;
    private final MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[MAX_POINTERS];
    private final MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[MAX_POINTERS];
    private long lastTouchDown;
    private float then;
    private boolean hit = false;
    private boolean proximity = false;

    public EventController(Device device, DroidConnection connection) {
        this.device = device;
        this.connection = connection;
        initPointer();
    }

    private void initPointer() {
        for(int i = 0; i < MAX_POINTERS; i++){
            pointerProperties[i] = new MotionEvent.PointerProperties();
            pointerProperties[i].id = i;
            pointerProperties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;

            pointerCoords[i] = new MotionEvent.PointerCoords();
            pointerCoords[i].orientation = 0;
            pointerCoords[i].pressure = 1;
            pointerCoords[i].size = 1;
        }

    }

    private void setPointerCoords(Point point, int index) {
        if (index >= MAX_POINTERS)
            index = MAX_POINTERS - 1;
        pointerCoords[index].x = point.x;
        pointerCoords[index].y = point.y;
    }

    private void setScroll(int hScroll, int vScroll) {
        MotionEvent.PointerCoords coords = pointerCoords[0];
        coords.setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll);
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll);
    }

    public void control() throws IOException {
        // on start, turn screen on
        turnScreenOn();

        while (true) {
            //           handleEvent();
            int[] buffer = connection.NewreceiveControlEvent();
            // 0 action 1 pointCount 2 pointIndex 3 button 4 x  5 y
            if (buffer != null) {
                long now = SystemClock.uptimeMillis();
                if (buffer[4] == 0 && buffer[5] == 0) {
                    if (buffer[0] == 28) {
                        proximity = true;           // Proximity event
                    } else if (buffer[0] == 29) {
                        proximity = false;
                    } else {
                        injectKeycode(buffer[0]);
                    }
                } else {
                    int action = buffer[0];

                    //判断是否电源键没开
                    if (action == MotionEvent.ACTION_UP && (!device.isScreenOn() || proximity)) {
                        if (hit) {
                            if (now - then < 250) {
                                then = 0;
                                hit = false;
                                injectKeycode(KeyEvent.KEYCODE_POWER);
                            } else {
                                then = now;
                            }
                        } else {
                            hit = true;
                            then = now;
                        }

                    } else {
                        int pointCount = buffer[1];
                        int pointIndex = buffer[2];
                        if (pointCount == 1){
                            lastTouchDown = now;
                        }
                        int button = buffer[3];
                        int X = buffer[4];
                        int Y = buffer[5];
                        Point point = new Point(X, Y);
                        Point newpoint = device.NewgetPhysicalPoint(point);
                        setPointerCoords(newpoint, pointIndex);
                        MotionEvent event = MotionEvent.obtain(lastTouchDown, now, action, pointCount, pointerProperties, pointerCoords, 0, button, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
                        injectEvent(event);
                    }
                }


            }
        }
    }


    private boolean injectKeyEvent(int action, int keyCode, int repeat, int metaState) {
        long now = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(now, now, action, keyCode, repeat, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                InputDevice.SOURCE_KEYBOARD);
        return injectEvent(event);
    }

    private boolean injectKeycode(int keyCode) {
        return injectKeyEvent(KeyEvent.ACTION_DOWN, keyCode, 0, 0)
                && injectKeyEvent(KeyEvent.ACTION_UP, keyCode, 0, 0);
    }

    private boolean injectEvent(InputEvent event) {
        return device.injectInputEvent(event, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    private boolean turnScreenOn() {
        return device.isScreenOn() || injectKeycode(KeyEvent.KEYCODE_POWER);
    }

}
