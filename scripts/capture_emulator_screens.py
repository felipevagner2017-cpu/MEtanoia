#!/usr/bin/env python3
"""Captures real screens from the running Android emulator through adb/UIAutomator."""
import os, re, subprocess, time, xml.etree.ElementTree as ET
OUT="screenshots"
os.makedirs(OUT, exist_ok=True)
def adb(*args, binary=False):
    p=subprocess.run(["adb",*args],stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
    return p.stdout if binary else p.stdout.decode("utf-8",errors="replace")
def find_and_tap(label):
    for _ in range(10):
        adb("shell","uiautomator","dump","/sdcard/window.xml")
        xml=adb("exec-out","cat","/sdcard/window.xml")
        root=ET.fromstring(xml)
        for node in root.iter("node"):
            if node.attrib.get("text","").strip()==label or node.attrib.get("content-desc","").strip()==label:
                m=re.fullmatch(r"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]",node.attrib.get("bounds",""))
                if m:
                    x1,y1,x2,y2=map(int,m.groups())
                    adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2))
                    time.sleep(2)
                    return
        time.sleep(1)
    raise RuntimeError("Não foi possível navegar para: "+label)
def capture(name):
    with open(os.path.join(OUT,name+".png"),"wb") as f:
        f.write(adb("exec-out","screencap","-p",binary=True))
time.sleep(4)
capture("01-dashboard")
for label,name in [("Devoção","02-devocao"),("Matrimônio","03-matrimonio"),("Profissional","04-profissional"),("Cotidiano","05-cotidiano")]:
    find_and_tap(label); capture(name)
find_and_tap("Agenda"); capture("06-agenda-marca-dagua")
print("Capturas reais:",", ".join(sorted(os.listdir(OUT))))
