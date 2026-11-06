import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "build"


def run(*args):
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout


def compare(name, version):
    target = BUILD / ("reference-" + name)
    run(sys.executable, "-m", "venv", str(target))
    python = str(target / ("Scripts/python.exe" if os.name == "nt" else "bin/python"))
    output = run(python, "-m", "pip", "--disable-pip-version-check", "install", "--no-input", "--no-color",
                 "--progress-bar", "off", "--index-url", "https://pypi.org/simple", "--force-reinstall", name + "==" + version)
    (BUILD / (name + "-pip.log")).write_text(output, encoding="utf-8")
    pip_order = re.search(r"Installing collected packages: ([^\r\n]+)", output).group(1).split(", ")
    java = run("java", "-cp", "build", "Main", "--package", name, "--version", version, "--mode", "order")
    java_order = [line.split("==")[0] for line in java.splitlines()]
    graph = run("java", "-cp", "build", "Main", "--package", name, "--version", version, "--mode", "graph")
    edges = [tuple(part.split("==")[0] for part in line.split(" -> ")) for line in graph.splitlines() if " -> " in line]
    normalize = lambda value: re.sub(r"[-_.]+", "-", value.lower())
    pip_order = list(map(normalize, pip_order))
    if set(java_order) != set(pip_order):
        raise AssertionError((java_order, pip_order))
    for parent, child in edges:
        if pip_order.index(child) >= pip_order.index(parent):
            raise AssertionError((parent, child, pip_order))
    installed = json.loads(run(python, "-m", "pip", "list", "--format", "json", "--disable-pip-version-check"))
    versions = {normalize(p["name"]): p["version"] for p in installed}
    for line in java.splitlines():
        package, selected = line.split("==")
        if versions[package] != selected:
            raise AssertionError((line, versions[package]))
    text = f"{name}=={version}\njava: {' -> '.join(java_order)}\npip: {' -> '.join(pip_order)}\nversions: match\ndependencies first: yes\n"
    (ROOT / "examples" / (name + "-order.txt")).write_text(text, encoding="utf-8")
    print(text, end="")


if __name__ == "__main__":
    compare(*(sys.argv[1:] or ["requests", "2.32.3"]))
