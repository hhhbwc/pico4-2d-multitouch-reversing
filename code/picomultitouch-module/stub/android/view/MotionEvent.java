package android.view;

/** Minimal compile stub - real class provided by the system at runtime. */
public class MotionEvent extends InputEvent {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_HOVER_MOVE = 7;
    public static final int ACTION_POINTER_DOWN = 5;
    public static final int ACTION_POINTER_UP = 6;
    public static final int ACTION_POINTER_INDEX_SHIFT = 8;
    public static final int ACTION_POINTER_INDEX_MASK = 0xff00;
    public static final int SOURCE_TOUCHSCREEN = 0x00001002;
    public static final int TOOL_TYPE_FINGER = 1;
    public static final int SOURCE_MOUSE = 0x00002002;

    public static class PointerProperties {
        public int id;
        public int toolType;
    }

    public static class PointerCoords {
        public float x;
        public float y;
    }

    public int getActionMasked() { return 0; }
    public int getPointerCount() { return 0; }
    public float getX() { return 0; }
    public float getY() { return 0; }
    public float getX(int i) { return 0; }
    public float getY(int i) { return 0; }
    public int getPointerId(int i) { return 0; }
    public int getButtonState() { return 0; }
    public int getDeviceId() { return 0; }
    public int getSource() { return 0; }
    public int getDisplayId() { return 0; }

    // satisfied at runtime via reflection
    public static MotionEvent obtain(long downTime, long eventTime, int action,
                                     int pointerCount, PointerProperties[] pointerProperties,
                                     PointerCoords[] pointerCoords, int metaState, int buttonState,
                                     float xPrecision, float yPrecision, int deviceId, int edgeFlags,
                                     int source, int displayId, int flags) {
        return null;
    }

    public long getDownTime() { return 0; }
    public void recycle() {}
}
