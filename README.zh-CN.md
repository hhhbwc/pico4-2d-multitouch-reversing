<div align="center">

# Pico 4 2D 悬浮窗多点触控逆向研究

**pico4-2d-multitouch-reversing**

针对 **PICO 4 (PICOA8110, Android 10, 固件 5.13.x)** 的 **2D 悬浮窗触摸链路** 的完整逆向研究。

目标：让 Pico 4 的 2D 悬浮窗（运行在独立虚拟 display `NS_APP` 上的应用，如浏览器、手游）支持**多点触控**，从而用左右手柄实现类似手机的双手指操作（如 MOBA 游戏移动 + 放技能）。

🌐 [English](README.en-US.md) · [Русский](README.ru-RU.md)

</div>

---

## ⚠️ 重要声明（必读）

**这是一个"逆向研究"仓库，不是一个可直接安装的 Mod。**

- 本仓库记录了对 Pico 4 2D 触摸系统的**完整逆向过程**：架构、注入协议、四条已被证实的失败路径及证据。
- 项目**没有产出可用的多点触控 Mod**。最终结论：Pico 4 这套 2D 触摸设计**目前没有低成本的外部注入方案**。
- 所有代码是可复现的实验性代码（原生注入器、Xposed 模块半成品），**不是为了"装上就能用"而写的**。
- 请勿照做后拿去刷写设备；本仓库不承担任何因刷机/改造导致的设备问题。

---

## 一句话结论

> Pico 4 的 2D 悬浮窗触摸由 XRShell（`com.picoxr.xrshell`）通过其原生 `create_device()` 创建的单点 uinput 设备 `virtual_input_device` 承载，**并通过 XRShell 原生流程内的"路由注册"动作**动态投递到当前聚焦的 2D 窗口（`NS_APP` 独立虚拟 display）。
>
> 由于这个"路由"发生在原生代码流程内部、且设备缺少通用 inline-hook 库（无 Dobby，仅有 LSPlant/`liboat_hook`），**所有外部注入手段（标准 input、sendevent、自建 uinput、Xposed 替换设备、idc 路由）都无法让触摸进入 2D 窗口**。

---

## 目录结构

```
├── README.zh-CN.md / .en-US.md / .ru-RU.md   # 三语 README
├── LICENSE
├── docs/
│   ├── 01-背景与目标.md          # 为什么做、2D 悬浮窗的多点触控需求
│   ├── 02-触摸系统架构.md        # 完整链路：手柄→XRShell→uinput→systemext→2D app
│   ├── 03-真实触摸注入协议.md    # getevent 抓到的精确协议（EV_MSC/BTN_TOOL_FINGER/ABS）
│   ├── 04-四条失败路径与证据.md  # 已实测堵死的路 + 每种为何无效
│   └── 05-结论与未来方向.md      # 最终结论、已排除项、剩余可能方向
├── code/
│   ├── mtinject/               # 自建 MT 多点 uinput 注入器（C，NDK 交叉编译）
│   ├── mtnative/               # Xposed native 模块（生成了可用的 MT 设备 + 注入管道）
│   └── picomultitouch-module/  # Xposed Java 模块（hook 框架，含诊断 hook）
└── references/                # 反编译参考类 + 原始捕获证据数据
```

---

## 关键发现速览

| 主题 | 结论 |
|---|---|
| 2D 窗口在哪 | 每个 2D app 跑在独立虚拟 display `NS_APP[包名]`，不在主屏 display0 的 input 路由 |
| 谁承载 2D 触摸 | XRShell（`com.picoxr.xrshell`）的 `libxrshell.so: create_device()` 创建 uinput 设备 `virtual_input_device`（单点 ABS_X/Y，max=100000） |
| 触摸怎么注入 | `UInput.nativeSendMotionEvent(x,y,action,displayId,deviceId)` → write() 到 uinput fd |
| 真实触摸协议 | `EV_MSC(code=0,value=<displayId>)` + `BTN_TOOL_FINGER(0x145)=1` + `ABS_X/ABS_Y` + `SYN`（不是 BTN_TOUCH） |
| 底层多指能力 | Pico 原生库 `libvirtualinput*.so` 的 `PvrVirtualInput::Touch(touchId,...)` 支持多 touchId，`EvdevInjector` 原生配了 `ABS_MT_SLOT max=9` ✓ |
| 多指注入实测 | 通过 `PvrVirtualInput::Touch(touchId=0/1)` 在 input 层成功产生 `pointerCount=2/3` ✓ |
| MT 设备正确识别的前提 | `bus=BUS_VIRTUAL`（否则 isExternal=true）；补全 KEY 集合（否则 Sources≠0x1703） |
| **最终死结** | 触摸"路由到 2D 窗口"是 XRShell 原生 `create_device()` 流程内的主动动作，hook 替换设备必然绕过 → 触摸固定在主屏 |

---

## 四条失败路径（详细证据见 `docs/04`）

1. **标准 Android 注入**（`adb shell input tap` / `sendevent`）→ 到不了 2D 窗口
2. **自建 MT 多点 uinput 设备**（即使同名/同属性）→ 被系统路由到主屏 display0，不路由 2D
3. **Xposed hook 替换 XRShell 设备**（已修到 isExternal=false、Sources=0x1703 与原设备完全一致）→ viewport 仍固定主屏（路由在原生流程内）
4. **idc 路由 pvr-virtual-input 到 2D**（`touch.displayId` 指向 NS_APP，Magisk overlay 实测生效）→ 但设备 Touch Input Mapper 为 disabled，触摸不投递

---

## 环境 / 工具链

- 设备：PICO 4 (PICOA8110, Android 10/API29, 固件 5.13.x)，已 root（Magisk 30.7 + Zygisk）
- 框架：zygisk_vector (LSPosed)
- NDK：Android NDK r27c，`aarch64-linux-android29-clang`（WSL）
- 反编译：jadx；符号/反汇编：`llvm-readelf` / `llvm-objdump`
- 详细命令见 `docs/05` 和代码注释

---

## License

[MIT](LICENSE)
