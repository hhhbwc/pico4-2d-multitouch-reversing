<div align="center">

# PICO 4 2D Floating-Window Touch: Reverse-Engineering Research

**pico4-2d-multitouch-reversing**

Complete reverse-engineering research into the **2D floating-window touch input** of **PICO 4 (PICOA8110, Android 10, firmware 5.13.x)**.

Goal: enable **multi-touch** in Pico 4's 2D floating windows (apps running in the separate virtual display `NS_APP`, e.g. browsers, mobile games), so that left/right controllers can be used for two-finger phone-like gestures (e.g. MOBA move + cast).

🇨🇳 [中文](README.zh-CN.md) · 🇷🇺 [Русский](README.ru-RU.md)

</div>

---

## ⚠️ Important notice (read first)

**This is a *reverse-engineering research* repository, NOT a ready-to-install mod.**

- It documents the complete reverse engineering of Pico 4's 2D touch system: architecture, injection protocol, and four proven dead-ends with evidence.
- **No working multi-touch mod was produced.** The conclusion is that Pico 4's 2D touch design currently has **no low-cost external injection path**.
- All code is reproducible experimental code (native injector, incomplete Xposed module) — **not written to be "install and done."**
- Do not follow this to re-flash your device; this repository is not responsible for any device damage from flashing/modding.

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
├── code/                # experimental code (C / Java)
│   ├── mtinject/        # custom MT multi-touch uinput injector
│   ├── mtnative/        # Xposed native module (usable MT device + injection pipeline)
│   └── picomultitouch-module/  # Xposed Java module (hook framework + diagnostics)
└── references/          # decompiled reference classes + raw captured evidence
```

---

## Key findings

| Topic | Finding |
|---|---|
| Where 2D windows live | Each 2D app runs in its own virtual display `NS_APP[package]`, outside display-0's input routing |
| What serves 2D touch | XRShell (`com.picoxr.xrshell`) `libxrshell.so: create_device()` creates the uinput device `virtual_input_device` (single-touch ABS_X/Y, max=100000) |
| How touch is injected | `UInput.nativeSendMotionEvent(x,y,action,displayId,deviceId)` → write() to the uinput fd |
| Real touch protocol | `EV_MSC(code=0,value=<displayId>)` + `BTN_TOOL_FINGER(0x145)=1` + `ABS_X/ABS_Y` + `SYN` (NOT `BTN_TOUCH`) |
| Native multi-touch capability | Pico libs (`libvirtualinput*.so`) support multi touchId in `PvrVirtualInput::Touch(touchId,...)`; `EvdevInjector` already configures `ABS_MT_SLOT max=9` ✓ |
| Multi-touch injection proven | `PvrVirtualInput::Touch(touchId=0/1)` yields `pointerCount=2/3` at the input layer ✓ |
| Preconditions to be "recognized" as the original device | `bus=BUS_VIRTUAL` (else isExternal=true); full KEY set (else Sources≠0x1703) |
| **The final dead-end** | "Routing touch to the 2D window" is an active action inside XRShell's native `create_device()` flow; hook-replacing the device inevitably bypasses it → touch stays on the main display |

---

## Four dead-ends (evidence in `docs/04`)

1. **Standard Android injection** (`adb shell input tap` / `sendevent`) → cannot reach the 2D window
2. **Custom MT uinput device** (even same name/properties) → routed to main display-0, never to 2D
3. **Xposed hook replacing XRShell's device** (fixed to isExternal=false, Sources=0x1703, identical to original) → viewport still pinned to the main display (routing is inside the native flow)
4. **idc-routing `pvr-virtual-input` to 2D** (`touch.displayId` → NS_APP, Magisk overlay verified) → but the device's Touch Input Mapper is `disabled`, so touch isn't delivered

---

## Environment / toolchain

- Device: PICO 4 (PICOA8110, Android 10/API29, firmware 5.13.x), rooted (Magisk 30.7 + Zygisk)
- Framework: zygisk_vector (LSPosed)
- NDK: Android NDK r27c, `aarch64-linux-android29-clang` (WSL)
- Decompilation: jadx; symbols/disassembly: `llvm-readelf` / `llvm-objdump`
- Details: `docs/05` and code comments

---

## License

[MIT](LICENSE)
