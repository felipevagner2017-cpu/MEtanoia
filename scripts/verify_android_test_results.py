#!/usr/bin/env python3
"""Fail CI when Gradle XML reports are missing, failed, errored, or skipped."""
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

if len(sys.argv) != 2:
    raise SystemExit("Uso: verify_android_test_results.py <diretorio-de-resultados>")

directory = Path(sys.argv[1])
reports = sorted(directory.rglob("TEST-*.xml")) if directory.exists() else []
if not reports:
    print(f"FALHA: nenhum relatório JUnit encontrado em {directory}.")
    raise SystemExit(1)

totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
for report in reports:
    root = ET.parse(report).getroot()
    nodes = [root] if root.tag == "testsuite" else [node for node in root.iter("testsuite")]
    if root.tag == "testsuites" and root.attrib.get("tests") is not None:
        nodes = [root]
    for node in nodes:
        for key in totals:
            totals[key] += int(node.attrib.get(key, "0") or "0")

summary = (
    f"Testes: {totals['tests']} | aprovados: {totals['tests'] - totals['failures'] - totals['errors'] - totals['skipped']} "
    f"| falhas: {totals['failures']} | erros: {totals['errors']} | ignorados: {totals['skipped']}"
)
print(summary)
step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
if step_summary:
    with open(step_summary, "a", encoding="utf-8") as handle:
        handle.write("### Resultado JUnit\n" + summary + "\n")

if totals["tests"] == 0 or totals["failures"] or totals["errors"] or totals["skipped"]:
    raise SystemExit("FALHA: a suíte não está integralmente aprovada.")
