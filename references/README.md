# references · 反编译参考

这里存放逆向过程中反编译出的 **systemext（`com.picovr.systemext`）关键 Java 类**，作为研究参考。

## 文件

| 文件 | 来源类 | 作用 |
|---|---|---|
| `EventDispatcher.java` | `com.bytedance.nativeshell.appmanager.input.EventDispatcher` | systemext 的触摸/按键事件分发；`toMotionEvent` 硬编码 `pointerCount=1`（单点根源之一） |
| `SurfacePanel.java` | `com.bytedance.nativeshell.appmanager.widget.SurfacePanel` | 2D 悬浮窗面板；`deliverToClient` 通过 Binder（`CODE_NS_DISPATCH_EVENT`）把 InputEvent 发给 2D app |
| `RemoteEngineManager.java` | `com.picovr.systemext.RemoteEngineManager` | engine 服务绑定；`RemoteCallback.onTransact` 处理 `CODE_INJECT_MOTION_EVENT=101` 等 |

## 说明与版权

- 这些是从 **PICO 4 设备 `/system/priv-app/SystemExt/SystemExt.apk`** 用 `jadx` 反编译得出的 **Pico 私有代码**。
- **仅存放在此作为研究参考**，版权归原厂商。**请勿将其中的私有代码直接用于商业发布**。
- 更多关键 native 符号（`libxrshell.so` 的 `create_device` / `InputManager` / `Renderer` 等）见 `docs/02` 和反汇编脚本（仓库未包含专有二进制 `.so`）。

## 为什么很有价值

`EventDispatcher.toMotionEvent` **硬编码单 pointer**、`SurfacePanel.deliverToClient` 走独立 Binder —— 这解释了为什么标准 Android 注入到不了 2D 窗口，也是本项目"2D 触摸是 Pico 私有链路"结论的直接证据。
