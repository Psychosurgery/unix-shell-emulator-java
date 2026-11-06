import json
import os
import platform
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "build"


def interpreter(target):
    return str(target / ("Scripts/python.exe" if os.name == "nt" else "bin/python"))


def run(*args):
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout


def compare(name, version):
    target = BUILD / ("reference-" + name)
    run(sys.executable, "-m", "venv", str(target))
    python = interpreter(target)
    output = run(python, "-m", "pip", "--disable-pip-version-check", "install", "--no-input", "--no-color",
                 "--progress-bar", "off", "--index-url", "https://pypi.org/simple", "--force-reinstall", name + "==" + version)
    (BUILD / (name + "-pip.log")).write_text(output, encoding="utf-8")
    pip_order = re.search(r"Installing collected packages: ([^\r\n]+)", output).group(1).split(", ")
    command = ["java", "-Djava.awt.headless=true", "-cp", "build", "Main", "--package", name, "--version", version,
               "--python-version", platform.python_version()]
    java = run(*command, "--mode", "order")
    java_order = [line.split("==")[0] for line in java.splitlines()]
    graph = run(*command, "--mode", "graph")
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
    tools = BUILD / "reference-tools"
    if not Path(interpreter(tools)).exists():
        run(sys.executable, "-m", "venv", str(tools))
        run(interpreter(tools), "-m", "pip", "--disable-pip-version-check", "install", "--no-input", "--index-url",
            "https://pypi.org/simple", "pipdeptree==2.30.0")
    tree_command = [interpreter(tools), "-m", "pipdeptree", "--python", python, "--packages", name, "--warn", "fail"]
    tree = run(*tree_command, "--json-tree")
    reference_edges = set()
    reference_versions = {}

    def visit(node):
        parent = normalize(node["key"])
        reference_versions[parent] = node["installed_version"]
        for child in node["dependencies"]:
            reference_edges.add((parent, normalize(child["key"])))
            visit(child)

    for node in json.loads(tree):
        visit(node)
    if reference_edges != set(edges):
        raise AssertionError((reference_edges, edges))
    if reference_versions != {line.split("==")[0]: line.split("==")[1] for line in java.splitlines()}:
        raise AssertionError(reference_versions)
    (ROOT / "examples" / (name + "-pipdeptree.txt")).write_text(run(*tree_command), encoding="utf-8")
    (ROOT / "examples" / (name + "-graph.txt")).write_text(graph, encoding="utf-8")
    (ROOT / "examples" / (name + "-comparison.txt")).write_text(
        f"python={platform.python_version()}\nplatform={sys.platform}\npipdeptree=2.30.0\nnodes={len(reference_versions)}\n"
        f"edges={len(reference_edges)}\nversions=match\nedges=match\n", encoding="utf-8")
    run(*command, "--mode", "render", "--show", "false", "--output", "examples/" + name)
    print(text, end="")


if __name__ == "__main__":
    compare(*(sys.argv[1:] or ["requests", "2.32.3"]))
