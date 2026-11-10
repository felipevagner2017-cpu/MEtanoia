#!/usr/bin/env python3
"""Collects real emulator screenshots captured by the Compose instrumented test."""
import os
import subprocess
import time

OUT = "screenshots"
APK = "app/build/outputs/apk/debug/app-debug.apk"
os.makedirs(OUT, exist_ok=True)

def package_and_activity_from_apk():
    build_tools = os.path.join(os.environ.get("ANDROID_HOME", ""), "build-tools")
    candidates = []
    if os.path.isdir(build_tools):
        candidates = sorted(
            [os.path.join(build_tools, name, "aapt") for name in os.listdir(build_tools)],
            key=lambda path: tuple(int(part) for part in os.path.basename(os.path.dirname(path)).split(".") if part.isdigit())
        )
    aapt = next((path for path in reversed(candidates) if os.path.isfile(path)), None)
    if not aapt:
        raise RuntimeError("aapt não encontrado para identificar o package ID do APK.")
    result = subprocess.run([aapt, "dump", "badging", APK], capture_output=True, text=True, check=True)
    import re
    package = activity = None
    for line in result.stdout.splitlines():
        if line.startswith("package:"):
            match = re.search(r"name='([^']+)'", line)
            package = match.group(1) if match else package
        elif line.startswith("launchable-activity:"):
            match = re.search(r"name='([^']+)'", line)
            activity = match.group(1) if match else activity
    if not package or not activity:
        raise RuntimeError("Não foi possível identificar package ID e Activity principal do APK debug.")
    return package, activity

if not os.path.isfile(APK):
    raise RuntimeError("APK debug do mesmo commit não encontrado: " + APK)
PACKAGE, MAIN_ACTIVITY = package_and_activity_from_apk()
ACTIVITY = f"{PACKAGE}/{MAIN_ACTIVITY}"

def adb(*args, binary=False, check=True, timeout=30):
    p = subprocess.run(["adb", *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
    if check and p.returncode:
        raise RuntimeError(
            f"adb {' '.join(args)} falhou ({p.returncode}): "
            + p.stderr.decode("utf-8", errors="replace")
            + p.stdout.decode("utf-8", errors="replace")
        )
    return p.stdout if binary else p.stdout.decode("utf-8", errors="replace")

def capture(name):
    with open(os.path.join(OUT, name + ".png"), "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", binary=True))

def pull_test_capture(name):
    remote = f"/sdcard/Download/MEtanoia-{name}.png"
    local = os.path.join(OUT, name + ".png")
    adb("pull", remote, local, timeout=45)
    if not os.path.isfile(local) or os.path.getsize(local) < 1024:
        raise RuntimeError("Captura instrumentada ausente ou inválida: " + remote)

# connectedDebugAndroidTest may remove the target package after the test run.
# Reinstall only on this disposable CI emulator; never on the user's phone.
installed = adb("shell", "pm", "path", PACKAGE, check=False)
if "package:" not in installed:
    if not os.path.isfile(APK):
        raise RuntimeError("APK debug do mesmo commit não encontrado: " + APK)
    print("Reinstalando APK recém-compilado no emulador para a captura da splash.", flush=True)
    adb("install", "-r", APK, timeout=90)
    installed = adb("shell", "pm", "path", PACKAGE, check=False)
if "package:" not in installed:
    raise RuntimeError("Não foi possível confirmar a instalação de " + PACKAGE)

adb("shell", "am", "force-stop", PACKAGE)
launch = adb("shell", "am", "start", "-n", ACTIVITY)
print("Activity principal:", launch.strip(), flush=True)
if "Error" in launch or "unable to resolve" in launch.lower():
    raise RuntimeError("Falha ao iniciar a Activity principal: " + launch)

time.sleep(0.2)
capture("00-splash-transicao")
time.sleep(5)

# The Compose instrumented test captures each page while it is displayed.
# This avoids relying on UIAutomator's incomplete view of Compose semantics.
for name in [
    "01-jornada-diaria",
    "02-devocao",
    "02b-fontes",
    "03-matrimonio",
    "04-profissional",
    "05-cotidiano",
    "06-agenda",
    "06b-agenda-semana",
    "06c-agenda-dia",
    "07-diario",
    "08-revisoes",
    "09-alertas",
]:
    pull_test_capture(name)

print("Capturas reais do emulador:", ", ".join(sorted(os.listdir(OUT))), flush=True)
