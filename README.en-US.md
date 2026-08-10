<div align="center">

# PICO 4 2D Floating-Window Touch: Reverse-Engineering Research

**pico4-2d-multitouch-reversing**

Complete reverse-engineering research into the **2D floating-window touch input** of **PICO 4 (PICOA8110, Android 10, firmware 5.13.x)**.

Goal: enable **multi-touch** in Pico 4's 2D floating windows (apps running in the separate virtual display `NS_APP`, e.g. browsers, mobile games), so that left/right controllers can perform two-finger phone-like gestures (e.g. MOBA move + cast).

🇨🇳 [中文](README.zh-CN.md) · 🇷🇺 [Русский](README.ru-RU.md)

</div>

---

## ⚠️ Important notice (read first)

**This is a *reverse-engineering research* repository, NOT a ready-to-install mod.**

- It documents the complete reverse engineering of Pico 4's 2D touch system: architecture, injection protocol, two proven low-level capabilities, and four proven dead-ends with evidence.
- **No working multi-touch mod was produced.** The conclusion is that Pico 4's 2D touch design currently has **no low-cost external injection path**.
- All code is reproducible experimental code (native injector, incomplete Xposed module) — **not written to be "install and done."**
- Do not re-flash your device following this; this repository is not responsible for any device damage.

---

## One-line conclusion

> Pico 4's 2D floating-window touch is served by the single-touch uinput device `virtual_input_device`, created by XRShell (`com.picoxr.xrshell`) via its native `create_device()`, and **dynamically routed to the currently-focused 2D window (the separate `NS_APP` virtual display) through a "route-registration" action inside the native flow**.
>
> Because that routing lives inside native code, and the device lacks a general inline-hook library (no Dobby; only LSPlant/`liboat_hook`), **all external injection methods (standard input, sendevent, custom uinput, Xposed device replacement, idc routing) fail to get touch into the 2D window**.

---

## Repository layout

```
├── README.zh-CN.md / .en-US.md / .ru-RU.md   # multi-language README
├── LICENSE
├── docs/                # research docs (Chinese)
│   ├── 01-背景与目标.md
│   ├── 02-触摸系统架构.md
│   ├── 03-真实触摸注入协议.md
│   ├── 04-四条失败路径与证据.md
│   └── 05-结论与未来方向.md
├── code/                # experimental code (C / Java)
│   ├── mtinject/        # custom MT multi-touch uinput injector
│   ├── mtnative/        # Xposed native module (usable MT device + injection pipeline)
│   └── picomultitouch-module/  # Xposed Java module (hook framework + diagnostics)
└── references/          # decompiled reference classes + raw captured evidence
```

---

## Touch architecture (summary)

```
Controller (Bluetooth / MCU)
  → libpxrcontrollerservice.so (reads trigger/rocker/touchpad)
  → XRShell (com.picoxr.xrshell, root)
      libxrshell.so:
      ├── create_device() creates uinput device "virtual_input_device" (single-touch)
      └── UInput.nativeSendMotionEvent(x,y,action,displayId,deviceId) writes touch
  → /dev/uinput → /dev/input/eventN (name=virtual_input_device)
  → system_server (private routing) → currently-focused 2D window (NS_APP virtual display)
  → 2D app
```

---

## Key findings

| Topic | Finding |
|---|---|
| Where 2D windows live | Each 2D app runs in its own virtual display `NS_APP[package]`, outside display-0's input routing |
| What serves 2D touch | XRShell (`com.picoxr.xrshell`) `libxrshell.so: create_device()` creates the uinput device `virtual_input_device` (single-touch ABS_X/Y, max=100000) |
| How touch is injected | `UInput.nativeSendMotionEvent(x,y,action,displayId,deviceId)` → write() to the uinput fd |
| Real touch protocol | `EV_MSC(code=0,value=<displayId>)` + `BTN_TOOL_FINGER(0x145)=1` + `ABS_X/ABS_Y` + `SYN` (NOT `BTN_TOUCH`) |
| Native multi-touch capability | Pico libs (`libvirtualinput*.so`) support multiple touchId in `PvrVirtualInput::Touch(touchId,...)`; `EvdevInjector` already configures `ABS_MT_SLOT max=9` ✓ |
| Multi-touch injection proven | `PvrVirtualInput::Touch(touchId=0/1)` yields `pointerCount=2/3` at the input layer ✓ |
| Preconditions to be "recognized" as the original device | `bus=BUS_VIRTUAL` (else isExternal=true); full KEY set (else Sources≠0x1703) |
| **The final dead-end** | "Routing touch to the 2D window" is an active action inside XRShell's native `create_device()` flow; hook-replacing the device inevitably bypasses it → touch stays on the main display |

---

## What IS verified (valuable assets)

Even though the final mod was not achieved, the following **were verified on-device and are reproducible**:

1. **Pico natively supports multi-touch at the bottom layer**
   - `libvirtualinput.so::EvdevInjector` already configures `ABS_MT_SLOT max=9` (`ConfigureMultiTouchXY` + `ConfigureAbsSlots`).
   - `libvirtualinputclient.so::PvrVirtualInput::Touch(touchId,...)` binder Parcel = `touchId + x + y + pressure + action`; touchId is packed independently → natively multi-touch.
   - Verified: `Touch(touchId=0/1)` produces `pointerCount=2/3` at the input layer.

2. **Real touch injection protocol** (`docs/03`)
   - `EV_MSC (code=0, value=<target displayId>)` for display association
   - `BTN_TOOL_FINGER (0x145)=1` signals touch-down (**NOT `BTN_TOUCH`** — common pitfall)
   - `ABS_X / ABS_Y` (0..100000) + `SYN_REPORT`

3. **Three root-causes to make a "custom device" look like the original** (`docs/04` path 3)
   - `bus = BUS_VIRTUAL (0x0006)`, otherwise `isExternal=true`
   - Complete the original 25-KEY set, otherwise `Sources` ≠ `0x1703`
   - Keep attributes (`INPUT_PROP_DIRECT`, etc.) identical to the original

4. **Complete uinput / Xposed / Magisk-overlay operation pipeline** (see `code/` + commands)

---

## Four dead-ends (evidence in `docs/04`)

1. **Standard Android injection** (`adb shell input tap` / `sendevent`) → cannot reach the 2D window
   > 2D apps live in separate virtual displays, outside display-0 input routing; external `sendevent` ABS events into `virtual_input_device` are not treated as 2D touch.

2. **Custom MT uinput device** (even same name/properties) → routed to main display-0, never to 2D
   > The system treats **only the device created by the XRShell process** as the 2D touch device; externally-created devices (even with identical name/id) never enter 2D routing.

3. **Xposed hook replacing XRShell's device** (deepest attempt)
   - Fixed to `isExternal=false`, `Sources=0x1703`, full KEY set, `bus=BUS_VIRTUAL` — **identical to the original**.
   - But **viewport stayed pinned to the main display**, not following 2D.
   - **Control-experiment evidence**: even a pure replica (single-touch, bus fixed, isExternal correct) does not route via hook; without the hook, the original device's viewport follows the focused 2D app.
   - → **Routing lives inside the native flow**; hook replacement inevitably bypasses it.

4. **idc-routing `pvr-virtual-input` to 2D** (Magisk overlay of `pvr-virtual-input-0.idc`, `touch.displayId` → `NS_APP[mark.via]`)
   - `AssociatedDisplay` indeed took effect (became Via's NS_APP).
   - But the device's **Touch Input Mapper is `disabled`** (viewport displayId=-1); `sendevent` injection produced no response in Via.
   - → These devices are reserved for vrshell/socialhome/main-display; their touch mapper is disabled otherwise.

---

## Environment / toolchain

| Item | Value |
|---|---|
| Device | PICO 4 (PICOA8110, Android 10 / API 29, firmware 5.13.x) |
| Root | Magisk 30.7 + Zygisk |
| Xposed framework | zygisk_vector (LSPosed) |
| Example modules | `com.picoxr.multitouch` (this project), `com.picoxr.winlimit` (working example) |
| NDK | Android NDK r27c, `aarch64-linux-android29-clang` (WSL) |
| Build | javac (--release 8 + stub) → D8 → apktool b → jarsigner (platform.keystore) |
| Decompilation | jadx |
| Symbols/disassembly | `llvm-readelf` / `llvm-objdump` (WSL) |
| Key device commands | `getevent -i` / `getevent -t` / `dumpsys input` / `dumpsys display` / `\`/data/adb/modules/zygisk_vector/cli\`` |

### Building the code (brief)

```bash
# native injector
aarch64-linux-android29-clang -O2 -static -o mtinject mtinject.c

# Xposed native module .so
aarch64-linux-android29-clang -shared -fPIC -O2 -o libmtinject_native.so MtNative.c -llog

# Xposed module APK (see code/picomultitouch-module, use the _build.bat flow)
```

See `code/README.md` and source-file comments for details.

---

## Possible future directions (not implemented, for reference)

1. **Native binary / ELF injection**: append `ABS_MT` registration inside `libxrshell.so: create_device()`, then repackage + Magisk overlay.
   ⚠️ Verified that the `.so` has **no usable code holes** in the whole `.text` section; needs ELF-section injection (append executable segment + patch program headers/dynamic tables). High risk.
2. **Find and replicate the "route-registration" call**: disassemble `InputManager::InjectMotionEvent` / `Renderer::setViewPort` / global `currentDisplayId`.
3. **Introduce a general inline-hook library (Dobby)**: the device has no Dobby yet, only LSPlant/`liboat_hook` (ART-oriented).
4. **`pvr-virtual-input` dynamic display binding**: research whether it can bind to an arbitrary NS_APP at runtime, and why Touch Mapper is disabled on non-host displays.

---

## License

[MIT](LICENSE) © 2026 Horizon
