# Android emulator in an Amp orb

An Android emulator runs in an orb, but only with software CPU emulation
(TCG). Orbs expose no `/dev/kvm`, so QEMU interprets every guest instruction.
A small orb boots Android in three to five minutes and installs a small APK in
20 to 80 seconds. That is enough for a smoke test, a screenshot, or a manual
look at a screen. It is a poor fit for a Compose UI test suite.

```
bash .agents/android-emulator.sh install   # SDK, system image, AVD (~7 GB, once)
bash .agents/android-emulator.sh start     # supervised service, waits for boot
WINDOW=1 bash .agents/android-emulator.sh start   # same, plus a window in the Desktop tab
bash .agents/android-emulator.sh stop
```

`.agents/setup` runs `install` when you set `ANDROID_EMULATOR=1`.

## What the orb provides

Measured in this orb: 2 vCPU, 3.9 GB of RAM.

| Check | Result |
| --- | --- |
| `/dev/kvm` | absent. `emulator -accel-check` prints `Could not open /dev/kvm : No such device` and exits 12 |
| `mknod /dev/kvm c 10 232` as root | node gets created, then `open()` fails with `ENODEV`. `/proc/misc` lists no `kvm` entry, so the kernel has no KVM device to attach to |
| Workload memory limit | `/sys/fs/cgroup/amp.slice/amp-workload.slice/memory.max` = 3 GiB, no swap |
| Nested virtualisation | not exposed |

The 3 GiB cgroup limit matters more than the CPU speed. QEMU gets OOM-killed as
soon as its resident set reaches about 3.0 GB, which leaves little room for the
guest.

## Why API 30 at 1536 MB

The emulator raises small `-memory` values to a per-image floor: 2048 MB for
API 30 and 2560 MB for API 34. My API 34 attempts with 2560 MB and with 2048 MB
both died with `Memory cgroup out of memory: Killed process ... (qemu-system-x86)`
before finishing a boot, after roughly nine minutes of trying.

Passing `-m` straight to QEMU bypasses that floor:

```
emulator -avd orb-emulator -no-window ... -qemu -m 1536 -smp cores=2
```

Unmodified API 34 has no usable configuration here, because its floor leaves no
headroom. API 30 does. At 1024 MB it boots, but System UI ANR dialogs cover the
app constantly. At 1536 MB the guest stays usable: it launched an app, accepted
taps, and produced screenshots. Expect the occasional ANR dialog under load.
The script sets `hide_error_dialogs=1` after boot to suppress new ones.

## Measured timings

2 vCPU, TCG, 1536 MB guest.

| Operation | Time |
| --- | --- |
| Cold boot to `sys.boot_completed=1` | 192 s, 244 s, 317 s on three runs |
| Snapshot save | 5.5 s, 1.4 GB on disk |
| Snapshot restore | fails, falls back to a cold boot |
| `adb install` of a 13 KB APK | 22 s warm, 68 to 83 s cold |
| `am start -W` cold launch | 6 to 8 s |
| `adb exec-out screencap -p` | 2 to 4 s idle, up to 21 s under load |
| `adb shell input tap` | about 5 s |

Snapshot restore does not work under TCG. The emulator logs
`error while loading state for instance 0x0 of device 'ram'`, then cold boots
anyway. Budget a full boot for every emulator start.

The SDK install pulls about 7 GB: cmdline-tools, emulator 37.2.13,
platform-tools, `platforms;android-34`, `build-tools;34.0.0`, and one
`android-30;default;x86_64` system image.

## Desktop tab

A windowed emulator does show up in the Desktop tab. `amp orb desktop ensure`
starts a Wayland session with Xwayland on `DISPLAY=:0`, and QEMU draws its
window there. Two flags make that work. Qt needs `QT_QPA_PLATFORM=xcb`, because
the emulator ships no Wayland plugin, and `-scale 0.5` keeps a 720x1280 phone
inside the 1280x720 desktop. Resizing the window afterwards does not rescale
the phone, so set the scale at launch.

`WINDOW=1 bash .agents/android-emulator.sh start` handles the display variables
and the scale.

Windowed mode has two costs. A "Compatibility Warnings" modal about CPU cores
sits over the phone until you click OK, which you can script with
`xdotool mousemove <x> <y> click 1`. Rendering into the desktop also pushes QEMU
to about 2.1 GB, and the Chrome that `amp orb desktop ensure` starts competes
for the same 3 GiB. Close Chrome when you do not need it.

For scripted work, stay headless. `adb exec-out screencap` plus
`adb shell input` is faster and does not depend on the desktop.

## What fails

- `-accel tcg,tb-size=128` is rejected with `Invalid parameter 'tb-size'`.
- `-gpu host` needs a GPU the orb does not have. Keep `-gpu swiftshader_indirect`.
- Gradle Managed Devices needs KVM too, so it fails the same way.
- `/dev/kvm` created with `mknod` stays unusable, because nothing in the kernel
  backs it.

## Cheaper options for Compose

For UI checks that do not need a device, use Robolectric or a JVM screenshot
library such as Roborazzi or Paparazzi. They run in seconds and skip KVM, the
emulator, and the five-minute boot.
