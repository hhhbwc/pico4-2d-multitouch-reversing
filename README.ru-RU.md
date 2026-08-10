<div align="center">

# PICO 4: исследование обратной разработки сенсорного ввода 2D-окон

**pico4-2d-multitouch-reversing**

Полное исследование обратной разработки **сенсорного ввода 2D-плавающих окон** на **PICO 4 (PICOA8110, Android 10, прошивка 5.13.x)**.

Цель: включить **мультитач** в 2D-плавающих окнах Pico 4 (приложения, работающие в отдельном виртуальном дисплее `NS_APP`, например браузеры, мобильные игры), чтобы левым/правым контроллером можно было выполнять двухпальцевые жесты, как на телефоне (например, в MOBA — движение + каст навыка).

🇨🇳 [中文](README.zh-CN.md) · 🇬🇧 [English](README.en-US.md)

</div>

---

## ⚠️ Важно (прочитайте)

**Это репозиторий *исследования*, а НЕ готовый к установке мод.**

- Здесь задокументирован полный процесс обратной разработки сенсорной системы 2D-окон Pico 4: архитектура, протокол ввода, две доказанных низкоуровневых возможности и четыре доказанных тупика с уликами.
- **Рабочий мод мультитача НЕ создан.** Вывод: текущая конструкция 2D-сенсора Pico 4 **не имеет дешёвого внешнего пути внедрения**.
- Весь код — воспроизводимые эксперименты (нативный инжектор, незавершённый Xposed-модуль) — **не предназначен для «установил и работает»**.
- Не перепрошивайте устройство по этому материалу; репозиторий не несёт ответственности за повреждение устройства.

---

## Вывод в одну строку

> Сенсорным вводом 2D-окон Pico 4 управляет одноточечное uinput-устройство `virtual_input_device`, создаваемое XRShell (`com.picoxr.xrshell`) через `create_device()`, и **динамически направляемое в активное 2D-окно (отдельный виртуальный дисплей `NS_APP`) действием «регистрации маршрута» внутри нативного потока**.
>
> Поскольку эта маршрутизация живёт внутри нативного кода, а на устройстве нет общей inline-hook библиотеки (нет Dobby; только LSPlant/`liboat_hook`), **все внешние методы ввода (стандартный input, sendevent, своё uinput-устройство, замена устройства через Xposed, маршрутизация через idc) не могут доставить сенсор в 2D-окно**.

---

## Структура репозитория

```
├── README.zh-CN.md / .en-US.md / .ru-RU.md   # README на трёх языках
├── LICENSE
├── docs/                # документация исследования (китайский)
│   ├── 01-背景与目标.md
│   ├── 02-触摸系统架构.md
│   ├── 03-真实触摸注入协议.md
│   ├── 04-四条失败路径与证据.md
│   └── 05-结论与未来方向.md
├── code/                # экспериментальный код (C / Java)
│   ├── mtinject/        # свой MT-инжектор (многоточечный uinput)
│   ├── mtnative/        # нативный Xposed-модуль (рабочее MT-устройство + конвейер ввода)
│   └── picomultitouch-module/  # Java Xposed-модуль (фреймворк хуков + диагностика)
└── references/          # декомпилированные классы + сырые данные захвата
```

---

## Архитектура сенсорного ввода (кратко)

```
Контроллер (Bluetooth / MCU)
  → libpxrcontrollerservice.so (читает trigger/rocker/touchpad)
  → XRShell (com.picoxr.xrshell, root)
      libxrshell.so:
      ├── create_device() создаёт uinput-устройство "virtual_input_device" (одноточечное)
      └── UInput.nativeSendMotionEvent(x,y,action,displayId,deviceId) пишет сенсор
  → /dev/uinput → /dev/input/eventN (name=virtual_input_device)
  → system_server (частная маршрутизация) → активное 2D-окно (виртуальный дисплей NS_APP)
  → 2D-приложение
```

---

## Ключевые находки

| Тема | Вывод |
|---|---|
| Где живут 2D-окна | Каждое 2D-приложение — в отдельном виртуальном дисплее `NS_APP[пакет]`, вне маршрутизации ввода display-0 |
| Что обслуживает 2D-сенсор | XRShell (`com.picoxr.xrshell`), `libxrshell.so: create_device()` создаёт uinput-устройство `virtual_input_device` (одноточечное ABS_X/Y, max=100000) |
| Как вводится сенсор | `UInput.nativeSendMotionEvent(x,y,action,displayId,deviceId)` → write() в fd uinput |
| Реальный протокол ввода | `EV_MSC(code=0,value=<displayId>)` + `BTN_TOOL_FINGER(0x145)=1` + `ABS_X/ABS_Y` + `SYN` (НЕ `BTN_TOUCH`) |
| Нативная мультитач-возможность | Библиотеки Pico (`libvirtualinput*.so`) поддерживают несколько touchId в `PvrVirtualInput::Touch(touchId,...)`; `EvdevInjector` уже настраивает `ABS_MT_SLOT max=9` ✓ |
| Мультитач-ввод доказан | `PvrVirtualInput::Touch(touchId=0/1)` даёт `pointerCount=2/3` на уровне input ✓ |
| Условия «распознавания» как оригинального устройства | `bus=BUS_VIRTUAL` (иначе isExternal=true); полный набор KEY (иначе Sources≠0x1703) |
| **Финальный тупик** | «Маршрутизация сенсора в 2D-окно» — активное действие внутри нативного потока `create_device()` XRShell; замена устройства хуком неизбежно обходит её → сенсор остаётся на главном дисплее |

---

## Что уже ПОЛУЧЕНО (ценные наработки)

Хотя финальный мод не достигнут, следующее **подтверждено на устройстве и воспроизводимо**:

1. **Pico нативно поддерживает мультитач на нижнем уровне**
   - `libvirtualinput.so::EvdevInjector` уже настраивает `ABS_MT_SLOT max=9` (`ConfigureMultiTouchXY` + `ConfigureAbsSlots`).
   - `libvirtualinputclient.so::PvrVirtualInput::Touch(touchId,...))` — Parcel `touchId + x + y + pressure + action`; touchId упаковывается независимо, поэтому нативный мультитач.
   - Проверено: `Touch(touchId=0/1)` даёт `pointerCount=2/3` на уровне input.

2. **Реальный протокол ввода** (`docs/03`)
   - `EV_MSC (code=0, value=<целевой displayId>)` для привязки дисплея
   - `BTN_TOOL_FINGER (0x145)=1` означает нажатие (**НЕ `BTN_TOUCH`** — частая ошибка)
   - `ABS_X / ABS_Y` (0..100000) + `SYN_REPORT`

3. **Три первопричины, чтобы "своё устройство" выглядело как оригинал** (`docs/04` путь 3)
   - `bus = BUS_VIRTUAL (0x0006)`, иначе `isExternal=true`
   - Полный набор из 25 KEY, иначе `Sources` ≠ `0x1703`
   - Идентичность атрибутов (`INPUT_PROP_DIRECT` и т.д.)

4. **Полный конвейер uinput / Xposed / Magisk-overlay** (см. `code/` и команды)

---

## Четыре тупика (улики в `docs/04`)

1. **Стандартный Android-ввод** (`adb shell input tap` / `sendevent`) → не попадает в 2D-окно
   > 2D-приложения живут в отдельных виртуальных дисплеях, вне маршрутизации display-0; внешние `sendevent`-ABS в `virtual_input_device` не считаются 2D-сенсором.

2. **Своё MT uinput-устройство** (даже с тем же именем/свойствами) → направляется на главный display-0, не в 2D
   > Система считает **только устройство, созданное процессом XRShell**, устройством 2D-сенсора; внешние устройства (даже с тем же именем/id) никогда не входят в 2D-маршрутизацию.

3. **Замена устройства XRShell хуком Xposed** (самая глубокая попытка)
   - Исправлено до `isExternal=false`, `Sources=0x1703`, полный KEY, `bus=BUS_VIRTUAL` — **идентично оригиналу**.
   - Но **viewport остался на главном дисплее**, не следует за 2D.
   - **Улика контрольного эксперимента**: даже чистая копия (одноточечная, bus исправлен, isExternal корректен) не маршрутизируется хуком; без хука viewport оригинала следует за активным 2D-приложением.
   - → **Маршрутизация внутри нативного потока**; замена хуком неизбежно обходит её.

4. **idc-маршрутизация `pvr-virtual-input` на 2D** (Magisk overlay `pvr-virtual-input-0.idc`, `touch.displayId` → `NS_APP[mark.via]`)
   - `AssociatedDisplay` действительно сработал (стал NS_APP Via).
   - Но **Touch Input Mapper устройства `отключён`** (viewport displayId=-1); `sendevent` не вызвал реакции в Via.
   - → Эти устройства зарезервированы для vrshell/socialhome/главного дисплея; иначе их сенсор-mapper отключён.

---

## Окружение / инструменты

| Пункт | Значение |
|---|---|
| Устройство | PICO 4 (PICOA8110, Android 10 / API 29, прошивка 5.13.x) |
| Root | Magisk 30.7 + Zygisk |
| Xposed-фреймворк | zygisk_vector (LSPosed) |
| Примеры модулей | `com.picoxr.multitouch` (этот проект), `com.picoxr.winlimit` (рабочий пример) |
| NDK | Android NDK r27c, `aarch64-linux-android29-clang` (WSL) |
| Сборка | javac (--release 8 + stub) → D8 → apktool b → jarsigner (platform.keystore) |
| Декомпиляция | jadx |
| Символы/дизассемблирование | `llvm-readelf` / `llvm-objdump` (WSL) |
| Ключевые команды устройства | `getevent -i` / `getevent -t` / `dumpsys input` / `dumpsys display` / `\`/data/adb/modules/zygisk_vector/cli\`` |

### Сборка кода (кратко)

```bash
# нативный инжектор
aarch64-linux-android29-clang -O2 -static -o mtinject mtinject.c

# нативный .so для Xposed-модуля
aarch64-linux-android29-clang -shared -fPIC -O2 -o libmtinject_native.so MtNative.c -llog

# APK Xposed-модуля (см. code/picomultitouch-module, используйте _build.bat)
```

Подробности — в `code/README.md` и комментариях к коду.

---

## Возможные будущие направления (не реализованы, для справки)

1. **Нативная бинарная / ELF-инъекция**: добавить регистрацию `ABS_MT` внутри `libxrshell.so: create_device()`, затем перепаковать + Magisk-overlay.
   ⚠️ Проверено: в `.so` **нет пригодных «дыр» во всей секции `.text`**; нужна ELF-инъекция секций (добавить исполняемый сегмент + править програм-заголовки/динамические таблицы). Высокий риск.
2. **Найти и воспроизвести «регистрацию маршрута»**: дизассемблировать `InputManager::InjectMotionEvent` / `Renderer::setViewPort` / глобальный `currentDisplayId`.
3. **Ввести общую inline-hook библиотеку (Dobby)**: на устройстве пока нет Dobby, только LSPlant/`liboat_hook` (для ART).
4. **Динамическая привязка дисплея `pvr-virtual-input`**: изучить, можно ли привязать к произвольному NS_APP в рантайме и почему Touch Mapper отключён на не-хостовых дисплеях.

---

## Лицензия

[MIT](LICENSE) © 2026 Horizon
