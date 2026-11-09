import json
import os
import platform
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "build"
INDEX = "https://pypi.org/simple"


def interpreter(target):
    executable = "Scripts/python.exe" if os.name == "nt" else "bin/python"
    return str(target / executable)


def run(*args):
    result = subprocess.run(
        args, cwd=ROOT, capture_output=True, text=True,
        encoding="utf-8", errors="replace",
    )
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout


def normalize(name):
    return re.sub(r"[-_.]+", "-", name.lower())


def install_reference(name, version):
    target = BUILD / ("reference-" + name)
    run(sys.executable, "-m", "venv", str(target))
    python = interpreter(target)
    output = run(
        python, "-m", "pip", "--disable-pip-version-check", "install",
        "--no-input", "--no-color", "--progress-bar", "off", "--index-url",
        INDEX, "--force-reinstall", name + "==" + version,
    )
    (BUILD / (name + "-pip.log")).write_text(output, encoding="utf-8")
    line = re.search(r"Installing collected packages: ([^\r\n]+)", output)
    order = [normalize(package) for package in line.group(1).split(", ")]
    return python, order


def java_command(name, version):
    return [
        "java", "-Djava.awt.headless=true", "-cp", "build", "Main",
        "--package", name, "--version", version,
        "--python-version", platform.python_version(),
    ]


def dependency_edges(graph):
    edges = set()
    for line in graph.splitlines():
        if " -> " in line:
            parent, child = line.split(" -> ")
            edges.add((parent.split("==")[0], child.split("==")[0]))
    return edges


def check_installation(python, java_versions, pip_order, edges):
    if set(java_versions) != set(pip_order):
        raise AssertionError((list(java_versions), pip_order))
    for parent, child in edges:
        if pip_order.index(child) >= pip_order.index(parent):
            raise AssertionError((parent, child, pip_order))
    installed = run(
        python, "-m", "pip", "list", "--format", "json",
        "--disable-pip-version-check",
    )
    versions = {
        normalize(package["name"]): package["version"]
        for package in json.loads(installed)
    }
    for package, selected in java_versions.items():
        if versions[package] != selected:
            raise AssertionError((package, selected, versions[package]))


def write_example(name, suffix, text):
    (ROOT / "examples" / (name + suffix)).write_text(text, encoding="utf-8")


def write_order(name, version, java_versions, pip_order):
    order_text = (
        f"{name}=={version}\n"
        f"java: {' -> '.join(java_versions)}\n"
        f"pip: {' -> '.join(pip_order)}\n"
        "versions: match\ndependencies first: yes\n"
    )
    write_example(name, "-order.txt", order_text)
    print(order_text, end="")


def compare(name, version):
    python, pip_order = install_reference(name, version)
    command = java_command(name, version)
    order = run(*command, "--mode", "order")
    versions = dict(line.split("==") for line in order.splitlines())
    graph = run(*command, "--mode", "graph")
    edges = dependency_edges(graph)
    check_installation(python, versions, pip_order, edges)
    write_order(name, version, versions, pip_order)


if __name__ == "__main__":
    compare(*(sys.argv[1:] or ["requests", "2.32.3"]))
