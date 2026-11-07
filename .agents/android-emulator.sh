#!/usr/bin/env bash
# Android emulator for Amp orbs, using software (TCG) CPU emulation because orbs
# expose no /dev/kvm. Reading the whole file before you run it is worth the minute.
#
#   bash .agents/android-emulator.sh install   # SDK, system image, AVD (~7 GB, once)
#   bash .agents/android-emulator.sh start     # supervised service, waits for boot
#   bash .agents/android-emulator.sh wait      # wait for a boot already in progress
#   bash .agents/android-emulator.sh stop
#
# Expect a 4-6 minute cold boot and an emulator that fits in the orb's 3 GiB
# workload cgroup limit. See .agents/android-emulator.md for the measurements.
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

# API 34 refuses to boot below a 2560 MB guest, and the guest plus QEMU's own
# overhead then exceeds the 3 GiB cgroup limit, so the kernel OOM-kills QEMU
# mid-boot. API 30 boots at 1536 MB, which fits.
API="${API:-30}"
IMAGE="system-images;android-$API;default;x86_64"
BUILD_TOOLS="build-tools;34.0.0"
PLATFORM="platforms;android-34"
AVD_NAME="${AVD_NAME:-orb-emulator}"
GUEST_MB="${GUEST_MB:-1536}"
SERVICE_NAME="${SERVICE_NAME:-android-emulator}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-13114758_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"

install_sdk() {
  if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "==> installing Android command line tools into $ANDROID_HOME"
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    local tmp
    tmp="$(mktemp -d)"
    curl -sSL -o "$tmp/tools.zip" "$CMDLINE_TOOLS_URL"
    unzip -q "$tmp/tools.zip" -d "$ANDROID_HOME/cmdline-tools"
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    rm -rf "$tmp"
  fi

  yes | sdkmanager --licenses >/dev/null 2>&1 || true
  sdkmanager --install "emulator" "platform-tools" "$PLATFORM" "$BUILD_TOOLS" "$IMAGE" >/dev/null
}

create_avd() {
  if [ -f "$HOME/.android/avd/$AVD_NAME.avd/config.ini" ]; then
    echo "==> AVD $AVD_NAME already exists"
    return
  fi
  echo "==> creating AVD $AVD_NAME"
  echo "no" | avdmanager create avd -n "$AVD_NAME" -k "$IMAGE" -d pixel_2 >/dev/null
  local cfg="$HOME/.android/avd/$AVD_NAME.avd/config.ini"
  # hw.ramSize is informational: the emulator raises it, so the real cap comes
  # from "-qemu -m" in start_emulator.
  sed -i \
    -e "s/^hw\.ramSize *=.*/hw.ramSize = $GUEST_MB/" \
    -e 's/^hw\.cpu\.ncore *=.*/hw.cpu.ncore = 2/' \
    -e 's/^hw\.lcd\.width *=.*/hw.lcd.width = 720/' \
    -e 's/^hw\.lcd\.height *=.*/hw.lcd.height = 1280/' \
    -e 's/^hw\.lcd\.density *=.*/hw.lcd.density = 320/' \
    "$cfg"
}

emulator_args() {
  # -accel off selects TCG; -gpu swiftshader_indirect keeps rendering on the host
  # so screencap works without a GPU. -feature -Vulkan drops guest Vulkan, which
  # costs memory we do not have. -qemu -m overrides the emulator's own RAM floor.
  # Everything after -qemu goes to QEMU itself, so emulator flags go first.
  echo -n "-avd $AVD_NAME -no-audio -no-boot-anim -no-snapshot -accel off"
  echo -n " -gpu swiftshader_indirect -cores 2 -skin 720x1280 -no-metrics"
  if [ "${WINDOW:-0}" = "1" ]; then
    # Draw into the orb's Wayland desktop so the Desktop tab shows the phone.
    # -scale 0.5 keeps 720x1280 inside a 1280x720 desktop.
    echo -n " -scale 0.5"
  else
    echo -n " -no-window"
  fi
  echo -n " -feature -Vulkan -qemu -m $GUEST_MB -smp cores=2"
}

start_emulator() {
  install_sdk
  create_avd
  adb start-server >/dev/null 2>&1 || true
  echo "==> starting $SERVICE_NAME (software emulation, no KVM)"
  local env_prefix=""
  if [ "${WINDOW:-0}" = "1" ]; then
    amp orb desktop ensure >/dev/null 2>&1 || true
    env_prefix="DISPLAY=${DISPLAY:-:0} QT_QPA_PLATFORM=xcb XDG_RUNTIME_DIR=${XDG_RUNTIME_DIR:-$HOME/.cache/amp/desktop/runtime} WAYLAND_DISPLAY=${WAYLAND_DISPLAY:-wayland-0} "
  fi
  amp orb service start "$SERVICE_NAME" \
    --command "${env_prefix}exec '$ANDROID_HOME/emulator/emulator' $(emulator_args)" >/dev/null
  wait_for_boot
}

wait_for_boot() {
  adb start-server >/dev/null 2>&1 || true
  local start elapsed boot
  start=$(date +%s)
  while :; do
    # adb fails with "device offline" for the first minutes; pipefail + set -e
    # would abort the script here without the trailing || true.
    boot="$(timeout 10 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    elapsed=$(( $(date +%s) - start ))
    if [ "$boot" = "1" ]; then
      echo "==> boot completed in ${elapsed}s"
      adb shell input keyevent 82 >/dev/null 2>&1 || true
      # System UI ANRs often enough under TCG that dialogs would cover the app.
      # hide_error_dialogs stops new ones; animations off saves a lot of CPU.
      adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true
      adb shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
      adb shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
      adb shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
      return 0
    fi
    if [ "$elapsed" -gt 900 ]; then
      echo "==> gave up after 15 min; check: amp orb service logs $SERVICE_NAME" >&2
      return 1
    fi
    if [ $((elapsed % 60)) -lt 10 ]; then
      echo "    waiting for boot (${elapsed}s)"
    fi
    sleep 10
  done
}

stop_emulator() {
  adb emu kill >/dev/null 2>&1 || true
  amp orb service stop "$SERVICE_NAME" >/dev/null 2>&1 || true
  echo "==> stopped"
}

case "${1:-}" in
  install) install_sdk; create_avd; echo "==> ready: bash .agents/android-emulator.sh start" ;;
  start) start_emulator ;;
  wait) wait_for_boot ;;
  stop) stop_emulator ;;
  *) echo "usage: $0 {install|start|wait|stop}" >&2; exit 2 ;;
esac
