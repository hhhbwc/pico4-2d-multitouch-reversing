# code/ · 实验性代码

> 这些是可复现的实验性代码，**不是开箱即用的 Mod**。用于展示逆向过程中实际动手的部分。

| 目录 | 内容 | 说明 |
|---|---|---|
| `mtinject/` | 自建 MT 多点 uinput 注入器（C） | `mtinject.c` 自建 `ABS_MT_SLOT` 多点设备；`mtinject_mt.c` 创建设备名 `virtual_input_device` 的 MT 设备；`touch2_test.c` 最小 native 多指测试（dlopen libvirtualinputclient + `PvrVirtualInput::Touch` 双 touchId） |
| `mtnative/` | Xposed native 模块（C/JNI） | `MtNative.c`：`nativeCreateMTDevice`（创建 bus=BUS_VIRTUAL + 25 KEY + ABS_MT + EV_MSC + BTN_TOOL_FINGER 的 MT 设备）、`nativeSendMTMotion`（MT 触摸注入）、`nativeReleaseMTDevice` |
| `picomultitouch-module/` | Xposed Java 模块 | hook XRShell `UInput.nativeCreateDevice` / `nativeSendMotionEvent`，加 Via 诊断 hook |

## 关键经验（代码里体现了）

1. **uinput 设备"看起来像原设备"的三个根因**：
   - `bus = BUS_VIRTUAL (0x0006)`，否则 `isExternal=true`
   - 补全原设备全部 25 个 KEY，否则 `Sources` ≠ `0x1703`
   - 触摸键用 **`BTN_TOOL_FINGER (0x145)`** 而非 `BTN_TOUCH`，并写 `EV_MSC (code=0, value=<displayId>)` 做 display 关联
2. `PvrVirtualInput::Touch(touchId, x, y, pressure, action)` 的 **touchId 独立打包，天然支持多指**（Pico 原生能力）。
   ⚠️ 注意：`pvrVirtualInputCreate()` 返回的是 impl 对象，`PvrVirtualInput::Touch` 需要 wrapper（`wrapper[0]=impl`）作为 this，直接传 create 返回值会二次解引用导致 SIGSEGV。
3. XRShell 是 priv-app，`/system` 只读（dm-verity），替换其文件需 **Magisk overlay**；安装需保留原路径（PMS 不重新校验签名）。

## 构建（简要）

- **native（mtinject / MtNative.so）**：Android NDK r27c，`aarch64-linux-android29-clang`。
  - mtinject：`-O2 -static`
  - MtNative.so：`-shared -fPIC -llog`
- **Xposed 模块 APK**：javac（--release 8 + stub）→ D8 → apktool b → jarsigner（platform.keystore）
- 完整命令见各源码顶部注释。

## 为什么"能注入但不生效"

`MtNative` + `picomultitouch-module` 已能在 XRShell 进程内创建**属性完全匹配原设备的 MT 多点设备**，并注入多指，但**触摸仍落在主屏、到不了 2D**——因为"路由到 2D"在 XRShell 原生 `create_device()` 流程内，hook 替换必然绕过。详见 `../docs/04` 和 `../docs/05`。
