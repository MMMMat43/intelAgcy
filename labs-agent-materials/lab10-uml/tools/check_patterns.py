#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import json
import os
import re
import sys

root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
src = os.path.join(root, "src", "main", "kotlin", "com", "example", "agent")
cfg = json.load(open(os.path.join(os.path.dirname(__file__), "patterns.json"), encoding="utf-8"))
diagrams = json.load(open(os.path.join(os.path.dirname(__file__), "diagrams.json"), encoding="utf-8"))

text = ""
for base, _, names in os.walk(src):
    for name in names:
        if name.endswith(".kt"):
            with open(os.path.join(base, name), encoding="utf-8-sig") as handle:
                text += handle.read() + "\n"

decl = re.compile(r"\b(?:class|interface|object)\s+([A-Za-z_][A-Za-z0-9_]*)")
found = set(decl.findall(text))
problems = []
for item in cfg["patterns"]:
    if item["class"] not in found:
        problems.append(f"patterns.json: класс {item['class']} не найден в коде")
    if item["stereotype"] not in cfg["colors"]:
        problems.append(f"patterns.json: нет цвета для шаблона {item['stereotype']}")
for group in diagrams["groups"]:
    for cls in group["classes"]:
        if cls not in found:
            problems.append(f"diagrams.json ({group['file']}): класс {cls} не найден в коде")

forbidden = re.compile(r"llm|gpt|openai|нейро", re.I)
out = os.path.join(os.path.dirname(__file__), "..", "out")
if os.path.isdir(out):
    for name in sorted(os.listdir(out)):
        if name.endswith(".puml"):
            with open(os.path.join(out, name), encoding="utf-8") as handle:
                for number, line in enumerate(handle, 1):
                    if forbidden.search(line):
                        problems.append(f"{name}:{number}: запрещённое слово: {line.strip()}")

if problems:
    print("\n".join(problems))
    sys.exit(1)
print(f"OK: {len(cfg['patterns'])} пометок шаблонов, {sum(len(g['classes']) for g in diagrams['groups'])} классов на диаграммах, классов в коде: {len(found)}")
