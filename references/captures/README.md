# references/captures · 原始证据

这里存放逆向过程中的**原始设备捕获数据**，作为文档结论的可验证证据。

> ⚠️ 部分文件较大且是 `dumpsys input` 的全量 dump，包含大量无关设备信息。重点看文档中指出的关键行。

| 文件 | 内容 | 佐证 |
|---|---|---|
| `event4_caps.txt` | 原 `virtual_input_device` 的能力（`getevent -i`） | 单点 ABS_X/Y、bus 0006、25 键、PROP_DIRECT |
| `cur_full_caps.txt` | 我们 Xposed 创建的 MT 设备能力 | bus=0006、25 键、ABS_MT_SLOT(0x2f)、POSITION、PROP_DIRECT —— 已与原设备一致 |
| `event5_capture2.txt` | pvr-virtual-input 触摸事件（getevent） | Pico 内部 MT 设备事件 |
| `input_during_hold.txt` | `dumpsys input`（注入双指 hold 时） | **pointerCount=3（touchId 0/1 双指）** —— 多指注入协议可行的铁证 |
| `input_mt_route.txt` | `dumpsys input`（外部自建 MT 设备时） | 自建 MT 设备被路由到主屏 display0 —— 路径 2 失败证据 |
| `input_keyfix.txt` | `dumpsys input`（属性修到 Sources=0x1703 时） | **设备属性完全匹配仍投主屏** —— 路径 3 失败铁证 |
| `dis_createdev*.txt` | `create_device()` 反汇编（llvm-objdump） | XRShell 原生创建设备逻辑、ioctl 序列 |
| `dis_updateid.txt` | `update_display_id` / `update_device_id` 反汇编 | EV_MSC display 关联机制 |

## 原始反向工程（每份的来源命令）

- 设备能力：`adb shell "su -c 'getevent -i'"`
- 触摸事件：`adb shell "su -c 'timeout N getevent -t /dev/input/eventX'"`
- 输入路由：`adb shell "su -c 'dumpsys input'"`
- 反汇编：WSL 中 `llvm-objdump -d --start-address=... --stop-address=... <libxrshell.so>`
