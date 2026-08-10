package com.bytedance.nativeshell.appmanager.input;

import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import androidx.core.view.InputDeviceCompat;
import com.bytedance.nativeshell.LOG;
import com.bytedance.nativeshell.appmanager.AppContainer;
import com.bytedance.nativeshell.appmanager.AppManagerService;
import com.bytedance.nativeshell.appmanager.AppManagerUtils;
import com.bytedance.nativeshell.appmanager.AppRecord;
import com.bytedance.nativeshell.appmanager.DataModule;
import com.bytedance.nativeshell.appmanager.FlagManager;
import com.bytedance.nativeshell.appmanager.util.UiThread;
import com.bytedance.nativeshell.appmanager.widget.InputMethodWindow;
import com.bytedance.nativeshell.appmanager.widget.SurfacePanel;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/* loaded from: classes.dex */
public class EventDispatcher {
    public static final int ACTION_CAPTIONBAR_GRAB_HIDE = -3003;
    public static final int ACTION_CAPTIONBAR_GRAB_SHOW = -3002;
    public static final int ACTION_CAPTIONBAR_HIDE = -3001;
    public static final int ACTION_CAPTIONBAR_SHOW = -3000;
    public static final int ACTION_CAPTION_BAR_FORCE_HIDE = -3005;
    private static final int ACTION_CLICK_BLANK_AREA = -1000;
    public static final int ACTION_GRAB_GUIDE_TOAST_SHOW = -3004;
    public static final int ACTION_TOUCHBAR_CLICK = -2000;
    public static final int ACTION_TOUCHBAR_DOUBLECLICK = -2001;
    private static final int BUTTON_BACK = 10001;
    private static final int BUTTON_CONFIRM = 10000;
    private static final int BUTTON_GESTURE_LEFT_HAND = 20001;
    private static final int BUTTON_GESTURE_RIGHT_HAND = 20002;
    private static final int BUTTON_GRIP = 10005;
    private static final int BUTTON_JOYSTICK = 10003;
    private static final int BUTTON_MENU = 10002;
    private static final int BUTTON_TRIGGER = 10004;
    public static final int DEVICE_GESTURE_LEFT = 3;
    public static final int DEVICE_GESTURE_RIGHT = 4;
    public static final int DEVICE_HMD = 0;
    private static final int DEVICE_ID_BASE = 100000;
    public static final int DEVICE_LEFT_HANDLE = 1;
    public static final int DEVICE_RIGHT_HANDLE = 2;
    public static final int KEY_GESTURE_HOME = -1001;
    public static final int KEY_GESTURE_RESET = -1002;
    public static final int KEY_HEAD_POS_CHANGED = -4000;
    public static final int KEY_SEETHROUGH_STATE = -1000;
    public static final int KEY_SHOW_TELEPORT_TOAST = -1003;
    public static final int LEFT_HAND_CONTROLLER_PANEL_ID = -1001;
    public static final int RIGHT_HAND_CONTROLLER_PANEL_ID = -1002;
    private static final int TOUCH_SLOP = 30;
    InputManager inputManager;
    private final List<EventData> mEventList;
    private final Handler mH;
    private final MotionEvent.PointerCoords mPointerCoords;
    private final MotionEvent.PointerProperties mPointerProperties;
    private final Runnable mTask;
    Method obtainKeyEventMethod;
    Method obtainMotionEventMethod;

    private static class EventDispatcherHolder {
        private static final EventDispatcher sInstance = new EventDispatcher();

        private EventDispatcherHolder() {
        }
    }

    private EventDispatcher() {
        this.inputManager = null;
        this.obtainMotionEventMethod = null;
        this.obtainKeyEventMethod = null;
        this.mEventList = new ArrayList();
        this.mPointerProperties = new MotionEvent.PointerProperties();
        this.mPointerCoords = new MotionEvent.PointerCoords();
        this.mH = new Handler(Looper.getMainLooper());
        this.mTask = new Runnable() { // from class: com.bytedance.nativeshell.appmanager.input.-$$Lambda$EventDispatcher$w_xujP4FObfyRd9wcj9IhqoY5Hk
            @Override // java.lang.Runnable
            public final void run() {
                EventDispatcher.this.lambda$new$0$EventDispatcher();
            }
        };
        try {
            this.inputManager = (InputManager) InputManager.class.getDeclaredMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            this.obtainMotionEventMethod = MotionEvent.class.getDeclaredMethod("obtain", Long.TYPE, Long.TYPE, Integer.TYPE, Integer.TYPE, MotionEvent.PointerProperties[].class, MotionEvent.PointerCoords[].class, Integer.TYPE, Integer.TYPE, Float.TYPE, Float.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE);
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        try {
            this.obtainKeyEventMethod = KeyEvent.class.getDeclaredMethod("obtain", Long.TYPE, Long.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE, String.class);
        } catch (Exception e3) {
            e3.printStackTrace();
        }
    }

    public static EventDispatcher getInstance() {
        return EventDispatcherHolder.sInstance;
    }

    public void injectKeyEvent(int clientId, int keyCode, int action, int deviceId, int buttonId) {
        if (AppManagerService.getInstance().getRootContainer().handleKeyEventChanged(clientId, keyCode, action, deviceId, buttonId)) {
            return;
        }
        if (keyCode == 3) {
            if (deviceId == 3) {
                buttonId = BUTTON_GESTURE_LEFT_HAND;
            } else if (deviceId == 4) {
                buttonId = BUTTON_GESTURE_RIGHT_HAND;
            }
        }
        EventData eventData = new EventData();
        eventData.isMotionEvent = false;
        eventData.clientId = clientId;
        eventData.keyCode = keyCode;
        eventData.action = action;
        eventData.deviceId = deviceId;
        eventData.buttonId = buttonId;
        if (deviceId == 2 && buttonId == BUTTON_MENU && AppManagerUtils.PRODUCT_PHOENIX.equals(AppManagerUtils.getProductName())) {
            Log.w("SYSTEM_EXT", "abandon injectKeyEvent by phx right handle menu key");
            return;
        }
        SurfacePanel surfacePanel = DataModule.getInstance().getSurfacePanel(clientId);
        if (surfacePanel != null) {
            debugEvent(eventData);
            surfacePanel.deliverToClient(toKeyEvent(eventData));
        } else {
            if (keyCode < 0) {
                return;
            }
            synchronized (this.mEventList) {
                this.mEventList.add(eventData);
            }
            this.mH.removeCallbacks(this.mTask);
            this.mH.post(this.mTask);
        }
    }

    public void injectMotionEvent(int clientId, int width, int height, float x, float y, int action, int deviceId, int buttonId) {
        int i = (int) (width * x);
        int i2 = (int) (height * y);
        if (FlagManager.getInstance().hasFlag(256) && (action == 9 || action == 10 || action == 7)) {
            return;
        }
        injectMotionEvent(clientId, i, i2, action, deviceId, buttonId);
    }

    public void injectMotionEvent(int clientId, int x, int y, int action, int deviceId, int buttonId) {
        if (processLocalEvent(action, clientId)) {
            return;
        }
        EventData eventData = new EventData();
        eventData.clientId = clientId;
        eventData.action = action;
        eventData.x = x;
        eventData.y = y;
        eventData.deviceId = deviceId;
        eventData.buttonId = buttonId;
        SurfacePanel surfacePanel = DataModule.getInstance().getSurfacePanel(clientId);
        if (surfacePanel != null) {
            debugEvent(eventData);
            surfacePanel.deliverToClient(toMotionEvent(eventData));
        } else {
            if (action < 0) {
                return;
            }
            synchronized (this.mEventList) {
                this.mEventList.add(eventData);
            }
            this.mH.removeCallbacks(this.mTask);
            this.mH.post(this.mTask);
        }
    }

    public /* synthetic */ void lambda$new$0$EventDispatcher() {
        ArrayList<EventData> arrayList;
        Object keyEvent;
        synchronized (this.mEventList) {
            arrayList = new ArrayList(this.mEventList);
            this.mEventList.clear();
        }
        if (arrayList.size() > 0) {
            for (EventData eventData : arrayList) {
                if (eventData.isMotionEvent) {
                    keyEvent = toMotionEvent(eventData);
                } else {
                    keyEvent = toKeyEvent(eventData);
                }
                if (keyEvent != null) {
                    debugEvent(eventData);
                    try {
                        try {
                            this.inputManager.getClass().getDeclaredMethod("injectInputEvent", InputEvent.class, Integer.TYPE).invoke(this.inputManager, keyEvent, 0);
                        } catch (Exception e) {
                            e.printStackTrace();
                            if (keyEvent instanceof MotionEvent) {
                            }
                        }
                        if (keyEvent instanceof MotionEvent) {
                            ((MotionEvent) keyEvent).recycle();
                        }
                    } catch (Throwable th) {
                        if (keyEvent instanceof MotionEvent) {
                            ((MotionEvent) keyEvent).recycle();
                        }
                        throw th;
                    }
                }
            }
        }
    }

    public static class EventData {
        public int action;
        public int buttonId;
        public int clientId;
        public int deviceId;
        public int displayId;
        public boolean isMotionEvent = true;
        public int keyCode;
        public DataModule.VrDisplay vrDisplay;
        public int x;
        public int y;

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("EventData {clientId [");
            sb.append(this.clientId);
            sb.append("], display [");
            sb.append(this.displayId);
            sb.append("], device [");
            sb.append(this.deviceId);
            sb.append("], button [");
            sb.append(this.buttonId);
            sb.append("], action : ");
            sb.append(MotionEvent.actionToString(this.action));
            if (this.isMotionEvent) {
                sb.append(", x : ");
                sb.append(this.x);
                sb.append(", y : ");
                sb.append(this.y);
                sb.append("}");
            } else {
                sb.append(", keyCode : ");
                sb.append(this.keyCode);
                sb.append("}");
            }
            return sb.toString();
        }
    }

    private boolean processLocalEvent(final int action, final int clientId) {
        if (action != -2001 && action != -2000) {
            if (action != -1000) {
                switch (action) {
                }
                return true;
            }
            UiThread.post(new Runnable() { // from class: com.bytedance.nativeshell.appmanager.input.-$$Lambda$EventDispatcher$3YQ43IxuQiKlPDa8z8hwtVXl23o
                @Override // java.lang.Runnable
                public final void run() {
                    AppManagerService.getInstance().handleClickBlankArea();
                }
            });
            return true;
        }
        UiThread.post(new Runnable() { // from class: com.bytedance.nativeshell.appmanager.input.-$$Lambda$EventDispatcher$4iDDvhJPQ2P9QAnOBRuZS1okIqg
            @Override // java.lang.Runnable
            public final void run() {
                EventDispatcher.lambda$processLocalEvent$2(clientId, action);
            }
        });
        return true;
    }

    static /* synthetic */ void lambda$processLocalEvent$2(final int clientId, final int action) {
        AppContainer resolveContainerByClientId = AppManagerService.getInstance().getRootContainer().resolveContainerByClientId(clientId);
        if (resolveContainerByClientId == null) {
            Log.e("SYSTEM_EXT", "processLocalEvent by clientId " + clientId + " get AppContainer is null");
            return;
        }
        if (action == -2000) {
            resolveContainerByClientId.reportClickTouchBarEvent();
        }
        Log.d("SYSTEM_EXT", "processLocalEvent action=" + action + "clientId=" + clientId + ", appContainer=" + resolveContainerByClientId.toString());
        if (resolveContainerByClientId.getType() == 2003) {
            AppManagerService.getInstance().handleHomeAction();
        } else if (resolveContainerByClientId instanceof AppRecord) {
            ((AppRecord) resolveContainerByClientId).handleEventAction(action);
        } else if (resolveContainerByClientId instanceof InputMethodWindow) {
            AppManagerService.getInstance().getAppManagerInternal().forceHideSoftInputMethod("click touch bar");
        }
    }

    private synchronized MotionEvent toMotionEvent(EventData data) {
        fillDisplayId(data);
        if (!adjustTouchEvent(data)) {
            return null;
        }
        long uptimeMillis = SystemClock.uptimeMillis();
        int i = data.action;
        if (i == 0) {
            data.vrDisplay.mDownTime = uptimeMillis;
            data.vrDisplay.mDownX = data.x;
            data.vrDisplay.mDownY = data.y;
            data.vrDisplay.mNeedSendMove = false;
        } else if (i == 2) {
            if (!data.vrDisplay.mNeedSendMove && (Math.abs(data.vrDisplay.mDownX - data.x) > 30 || Math.abs(data.vrDisplay.mDownY - data.y) > 30)) {
                data.vrDisplay.mNeedSendMove = true;
            }
            if (!data.vrDisplay.mNeedSendMove) {
                return null;
            }
        } else if (i == 7 || i == 9 || i == 10) {
            data.vrDisplay.mDownTime = 0L;
            data.x = Math.max(0, Math.min(data.vrDisplay.mWidth - 1, data.x));
            data.y = Math.max(0, Math.min(data.vrDisplay.mHeight - 1, data.y));
        }
        long j = data.vrDisplay.mDownTime;
        this.mPointerProperties.id = 0;
        this.mPointerProperties.toolType = 1;
        MotionEvent.PointerProperties[] pointerPropertiesArr = {this.mPointerProperties};
        this.mPointerCoords.x = data.x;
        this.mPointerCoords.y = data.y;
        MotionEvent.PointerCoords[] pointerCoordsArr = {this.mPointerCoords};
        int i2 = data.buttonId;
        int i3 = BUTTON_JOYSTICK;
        if (i2 != BUTTON_JOYSTICK) {
            i3 = 100000 + data.deviceId;
        }
        try {
            return (MotionEvent) this.obtainMotionEventMethod.invoke(null, Long.valueOf(j), Long.valueOf(uptimeMillis), Integer.valueOf(data.action), 1, pointerPropertiesArr, pointerCoordsArr, 0, 0, Float.valueOf(1.0f), Float.valueOf(1.0f), Integer.valueOf(i3), 0, Integer.valueOf(InputDeviceCompat.SOURCE_TOUCHSCREEN), Integer.valueOf(data.displayId), 0);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean adjustTouchEvent(EventData data) {
        if (data.action == 0 || data.action == 2 || data.action == 1 || data.action == 3) {
            if (data.action == 3 && isEventDataOutBounds(data)) {
                data.action = 1;
                data.x = data.vrDisplay.mLastX;
                data.y = data.vrDisplay.mLastY;
            }
            if (data.buttonId != BUTTON_JOYSTICK) {
                if (isEventDataOutBounds(data)) {
                    if (data.vrDisplay.mLastAction == 1) {
                        return false;
                    }
                    data.action = 1;
                } else if (data.vrDisplay.mLastAction == 1) {
                    data.action = 0;
                }
            }
            if (data.action != 2) {
                data.x = Math.max(0, Math.min(data.vrDisplay.mWidth, data.x));
                data.y = Math.max(0, Math.min(data.vrDisplay.mHeight, data.y));
            }
            data.vrDisplay.mLastAction = data.action;
            data.vrDisplay.mLastX = data.x;
            data.vrDisplay.mLastY = data.y;
        }
        return true;
    }

    private boolean isEventDataOutBounds(EventData data) {
        return data.x < 0 || data.y < 0 || data.x > data.vrDisplay.mWidth || data.y > data.vrDisplay.mHeight;
    }

    private KeyEvent toKeyEvent(EventData data) {
        int i;
        int i2;
        if (data.action == 4 && data.vrDisplay != null && ((i2 = data.vrDisplay.mType) == 1007 || i2 == 1008)) {
            return null;
        }
        fillDisplayId(data);
        long uptimeMillis = SystemClock.uptimeMillis();
        try {
            return (KeyEvent) this.obtainKeyEventMethod.invoke(null, Long.valueOf(uptimeMillis), Long.valueOf(uptimeMillis), Integer.valueOf(data.action), Integer.valueOf(data.keyCode), 0, 0, Integer.valueOf((data.keyCode == 3 && ((i = data.buttonId) == BUTTON_GESTURE_LEFT_HAND || i == BUTTON_GESTURE_RIGHT_HAND)) ? data.buttonId : 0), Integer.valueOf(data.keyCode == 4 ? data.buttonId : 0), 0, 0, Integer.valueOf(data.displayId), null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void fillDisplayId(EventData eventData) {
        DataModule.VrDisplay displayByClientId = DataModule.getInstance().getDisplayByClientId(eventData.clientId);
        if (displayByClientId == null) {
            displayByClientId = DataModule.DEFAULT_VR_DISPLAY;
        }
        eventData.vrDisplay = displayByClientId;
        eventData.displayId = displayByClientId.mDisplayId;
    }

    private void debugEvent(EventData data) {
        if (LOG.sOpenLog) {
            Log.w("SYSTEM_EXT", "EventDispatcher : " + data.toString());
            return;
        }
        int i = data.action;
        if (i == 0 || i == 1 || i == 3) {
            if (data.isMotionEvent || data.keyCode > 0) {
                Log.w("SYSTEM_EXT", "EventDispatcher : " + data.toString());
            }
        }
    }
}
