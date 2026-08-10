#include <jni.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <android/log.h>

#define TAG "MTNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

/* global: XRShell 2D 触摸设备 fd (由 createMTDevice 创建) */
static int g_ufd = -1;

#define VENDOR_ID   0x1234
#define PRODUCT_ID  0x5678

static int emit(int ufd, int t, int c, int v) {
    struct input_event ev; memset(&ev,0,sizeof(ev));
    ev.type=t; ev.code=c; ev.value=v;
    return write(ufd,&ev,sizeof(ev));
}
static int emit_syn(int ufd){ return emit(ufd, EV_SYN, SYN_REPORT, 0); }

static int abs_setup(int ufd, int code, int min, int max) {
    struct uinput_abs_setup a; memset(&a,0,sizeof(a));
    a.code = (unsigned short)code;
    a.absinfo.minimum = min;
    a.absinfo.maximum = max;
    if (ioctl(ufd, UI_ABS_SETUP, &a) < 0) {
        LOGE("UI_ABS_SETUP code=%d errno=%d", code, errno);
        return -1;
    }
    return 0;
}

/*
 * 创建 MT 多点 uinput 设备(设备名 virtual_input_device, 匹配 XRShell 路由)
 * XRShell 进程内创建 => 被 systemext 路由到 2D app
 * 完整复刻 XRShell create_device 的属性: INPUT_PROP_DIRECT + EV_KEY(DPAD等) + ABS_X/Y
 * 额外注册 ABS_MT_SLOT 等实现多指
 */
JNIEXPORT jint JNICALL
Java_com_picoxr_multitouch_MultiTouchMain_nativeCreateMTDevice(JNIEnv* env, jclass clazz) {
    if (g_ufd >= 0) return g_ufd;

    int ufd = open("/dev/uinput", O_WRONLY);
    if (ufd < 0) { LOGE("open uinput errno=%d", errno); return -1; }

    struct uinput_setup us; memset(&us,0,sizeof(us));
    strcpy(us.name, "virtual_input_device");
    us.id.bustype = BUS_VIRTUAL;  /* 0006: 原设备是 BUS_VIRTUAL(虚拟内部设备), 蓝牙(0005)会标external */
    us.id.vendor  = VENDOR_ID;
    us.id.product = PRODUCT_ID;
    us.id.version = 1;

    /* INPUT_PROP_DIRECT: 让系统识别为 touchScreen(direct) */
    ioctl(ufd, UI_SET_PROPBIT, INPUT_PROP_DIRECT);

    ioctl(ufd, UI_SET_EVBIT, EV_KEY);
    ioctl(ufd, UI_SET_EVBIT, EV_ABS);
    ioctl(ufd, UI_SET_EVBIT, EV_MSC);  /* 关键: 原设备注册了 EV_MSC, 用于 EV_MSC displayId 关联路由 */

    /* 按键/工具 (完整复刻 create_device 的 KEY 集合) */
    ioctl(ufd, UI_SET_KEYBIT, BTN_TOUCH);        /* 0x14a */
    ioctl(ufd, UI_SET_KEYBIT, BTN_TOOL_FINGER);  /* 0x145 */
    int keys[] = {0x1c,0x9e,0x67,0x6c,0x69,0x6a,  /* ENTER,BACK,UP,DOWN,LEFT,RIGHT */
                  0x72,0x73,0x74,0x8b,0xac,0xc9, 
                  0x130,0x131,0x133,0x134,0x161,0x244,
                  0x2c9,0x2ca,0x2cb,0x2cc,0x2cd};
    for (int i=0;i<(int)(sizeof(keys)/sizeof(keys[0]));i++) {
        ioctl(ufd, UI_SET_KEYBIT, keys[i]);
    }

    /* 老式单点 */
    if (abs_setup(ufd, ABS_X, 0, 100000) < 0) goto fail;
    if (abs_setup(ufd, ABS_Y, 0, 100000) < 0) goto fail;

    /* MT 多点 (需要保留: 这是多指能力来源) */
    if (abs_setup(ufd, ABS_MT_SLOT, 0, 9) < 0) goto fail;
    if (abs_setup(ufd, ABS_MT_POSITION_X, 0, 32767) < 0) goto fail;
    if (abs_setup(ufd, ABS_MT_POSITION_Y, 0, 32767) < 0) goto fail;
    if (abs_setup(ufd, ABS_MT_TRACKING_ID, 0, 65535) < 0) goto fail;
    if (abs_setup(ufd, ABS_MT_PRESSURE, 0, 255) < 0) goto fail;

    if (ioctl(ufd, UI_DEV_SETUP, &us) < 0) { LOGE("UI_DEV_SETUP errno=%d", errno); goto fail; }
    if (ioctl(ufd, UI_DEV_CREATE) < 0) { LOGE("UI_DEV_CREATE errno=%d", errno); goto fail; }

    g_ufd = ufd;
    LOGI("createMTDevice OK fd=%d", ufd);
    return ufd;
fail:
    close(ufd);
    return -1;
}

/*
 * MT 触摸注入. 复刻 update_display_id: 写 EV_MSC 关联 displayId 路由到 2D.
 * action: 0=DOWN 1=UP 2=MOVE; slot: 手指槽(0=左,1=右); x,y 归一化0~1
 */
JNIEXPORT jboolean JNICALL
Java_com_picoxr_multitouch_MultiTouchMain_nativeSendMTMotion(JNIEnv* env, jclass clazz,
        jint action, jint slot, jfloat x, jfloat y, jfloat pressure, jint displayId) {
    if (g_ufd < 0) return JNI_FALSE;
    int ix = (int)(x * 32767);
    int iy = (int)(y * 32767);
    int ip = (pressure > 0 ? (int)(pressure*255) : 0);
    if (ix < 0) ix=0; if (ix>32767) ix=32767;
    if (iy < 0) iy=0; if (iy>32767) iy=32767;

    /* 复刻 update_display_id: EV_MSC code=0 value=displayId (路由到 2D 显示) */
    struct input_event ev; memset(&ev,0,sizeof(ev));
    ev.type  = EV_MSC;
    ev.code  = 0;
    ev.value = displayId;
    write(g_ufd, &ev, sizeof(ev));

    emit(g_ufd, EV_ABS, ABS_MT_SLOT, slot);
    if (action == 1) { /* UP */
        emit(g_ufd, EV_ABS, ABS_MT_TRACKING_ID, -1);
        emit(g_ufd, EV_KEY, BTN_TOOL_FINGER, 0); /* 原协议用 BTN_TOOL_FINGER(0x145) */
    } else {
        emit(g_ufd, EV_ABS, ABS_MT_TRACKING_ID, slot + 1);
        emit(g_ufd, EV_ABS, ABS_MT_POSITION_X, ix);
        emit(g_ufd, EV_ABS, ABS_MT_POSITION_Y, iy);
        emit(g_ufd, EV_ABS, ABS_MT_PRESSURE, ip > 0 ? ip : 100);
        emit(g_ufd, EV_KEY, BTN_TOOL_FINGER, 1); /* 原协议用 BTN_TOOL_FINGER */
    }
    emit_syn(g_ufd);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_picoxr_multitouch_MultiTouchMain_nativeReleaseMTDevice(JNIEnv* env, jclass clazz) {
    if (g_ufd >= 0) {
        ioctl(g_ufd, UI_DEV_DESTROY);
        close(g_ufd);
        g_ufd = -1;
        return 0;
    }
    return -1;
}
