#!/usr/bin/env python3
"""Captures real screens from the running Android emulator through adb/UIAutomator."""
import os, re, subprocess, time, xml.etree.ElementTree as ET

OUT = "screenshots"
PACKAGE = "br.com.metanoia"
ACTIVITY = f"{PACKAGE}/.MainActivity"
os.makedirs(OUT, exist_ok=True)

def adb(*args, binary=False, check=True, timeout=30):
    try:
        p = subprocess.run(["adb", *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
    except subprocess.TimeoutExpired as exc:
        raise RuntimeError(f"Timeout de {timeout}s em adb {' '.join(args)}") from exc
    if check and p.returncode:
        raise RuntimeError(
            f"adb {' '.join(args)} falhou ({p.returncode}): "
            + p.stderr.decode("utf-8", errors="replace")
            + p.stdout.decode("utf-8", errors="replace")
        )
    return p.stdout if binary else p.stdout.decode("utf-8", errors="replace")

def dump_ui():
    print("Atualizando árvore UIAutomator...", flush=True)
    adb("shell", "uiautomator", "dump", "/sdcard/window.xml", timeout=45)
    return ET.fromstring(adb("exec-out", "cat", "/sdcard/window.xml"))

def visible_node(root, label):
    for node in root.iter("node"):
        if node.attrib.get("text", "").strip() == label or node.attrib.get("content-desc", "").strip() == label:
            m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
            if m:
                return node, tuple(map(int, m.groups()))
    return None, None

def capture(name):
    print("Capturando:", name, flush=True)
    with open(os.path.join(OUT, name + ".png"), "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", binary=True))

def find_and_tap(label):
    print("Navegando para:", label, flush=True)
    width, height = map(int, re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size")).groups())
    for attempt in range(12):
        root = dump_ui()
        _, bounds = visible_node(root, label)
        if bounds:
            x1, y1, x2, y2 = bounds
            if x1 >= 0 and y1 >= 0 and x2 <= width and y2 <= height:
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(2)
                return
        _, nav_bounds = visible_node(root, "Devoção")
        if not nav_bounds:
            _, nav_bounds = visible_node(root, "Painel")
        if not nav_bounds:
            raise RuntimeError("Não foi possível localizar a barra de navegação para revelar: " + label)
        _, y1, _, y2 = nav_bounds
        y = (y1 + y2) // 2
        adb("shell", "input", "swipe", str(width - 40), str(y), "40", str(y), "350")
        time.sleep(0.5)
    raise RuntimeError("Não foi possível navegar para: " + label)

installed = adb("shell", "pm", "path", PACKAGE, check=False)
if "package:" not in installed:
    raise RuntimeError(f"O pacote {PACKAGE} não está instalado no emulador: {installed}")

print("Pacote encontrado:", installed.strip(), flush=True)
adb("shell", "am", "force-stop", PACKAGE)
launch = adb("shell", "am", "start", "-n", ACTIVITY)
print("Resultado da Activity principal:", launch.strip(), flush=True)
if "Error" in launch or "unable to resolve" in launch.lower():
    raise RuntimeError("Falha ao iniciar a Activity principal: " + launch)

time.sleep(0.2)
capture("00-splash-transicao")
time.sleep(5)
capture("01-dashboard")

for label, name in [
    ("Devoção", "02-devocao"),
    ("Matrimônio", "03-matrimonio"),
    ("Profissional", "04-profissional"),
    ("Cotidiano", "05-cotidiano"),
]:
    find_and_tap(label)
    capture(name)

find_and_tap("Agenda")
capture("06-agenda-marca-dagua")
print("Capturas reais:", ", ".join(sorted(os.listdir(OUT))), flush=True)
