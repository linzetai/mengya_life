"""Gradle JVM 测试与 APK 构建。"""
from __future__ import annotations

import hashlib
import os
import re
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

from .util import ROOT, STATUS_FAIL, STATUS_PASS, ensure_dir, run_cmd

RESULTS = ROOT / "app" / "build" / "test-results" / "testDebugUnitTest"
GRADLEW = str(ROOT / "gradlew")


def _gradle(args: list[str], log_path: Path, timeout: int, offline: bool) -> tuple[int, float]:
    env = dict(os.environ)
    env.setdefault("JAVA_TOOL_OPTIONS", "")
    cmd = [GRADLEW, "--console=plain"] + args
    if offline:
        cmd.append("--offline")
    code, out, dur = run_cmd(cmd, timeout=timeout, env=env)
    ensure_dir(log_path.parent)
    log_path.write_text(out, encoding="utf-8")
    return code, dur


def source_digest() -> str:
    roots = [
        ROOT / "app" / "src",
        ROOT / "app" / "schemas",
        ROOT / "app" / "build.gradle",
        ROOT / "build.gradle",
        ROOT / "settings.gradle",
        ROOT / "gradle.properties",
        ROOT / "gradle",
    ]
    h = hashlib.sha256()
    files: list[Path] = []
    for r in roots:
        if r.is_file():
            files.append(r)
        elif r.is_dir():
            files.extend(p for p in r.rglob("*") if p.is_file()
                         and "__pycache__" not in p.parts and not p.name.endswith(".pyc"))
    for p in sorted(files, key=lambda x: x.as_posix()):
        rel = p.relative_to(ROOT).as_posix()
        h.update(rel.encode())
        h.update(p.read_bytes())
    return h.hexdigest()


def expected_schema() -> int:
    text = (ROOT / "app/src/main/java/com/mengyalife/app/data/local/MengyaDatabase.kt").read_text(encoding="utf-8")
    m = re.search(r"DB_SCHEMA_VERSION\s*=\s*(\d+)", text)
    return int(m.group(1)) if m else 0


def expected_version() -> str:
    text = (ROOT / "app" / "build.gradle").read_text(encoding="utf-8")
    m = re.search(r"versionName\s+'([^']+)'", text)
    return m.group(1) if m else ""


def clear_test_results() -> None:
    if RESULTS.exists():
        shutil.rmtree(RESULTS, ignore_errors=True)
    ensure_dir(RESULTS)


def parse_results() -> dict:
    classes: dict[str, dict] = {}
    if not RESULTS.exists():
        return {"classes": classes, "tests": 0, "failures": 0, "errors": 0, "skipped": 0,
                "time": 0.0, "missingDetails": []}
    total = fails = errors = skipped = 0
    tsum = 0.0
    details = []
    for p in sorted(RESULTS.glob("TEST-*.xml")):
        try:
            root = ET.parse(p).getroot()
        except ET.ParseError:
            continue
        name = root.get("name", p.stem)
        t = int(root.get("tests", "0"))
        f = int(root.get("failures", "0"))
        e = int(root.get("errors", "0"))
        s = int(root.get("skipped", "0"))
        tm = float(root.get("time", "0") or 0)
        classes[name] = {"tests": t, "failures": f, "errors": e, "skipped": s, "time": tm}
        total += t
        fails += f
        errors += e
        skipped += s
        tsum += tm
        for tc in root.findall("testcase"):
            for fail in list(tc.findall("failure")) + list(tc.findall("error")):
                details.append({
                    "class": name,
                    "test": tc.get("name", ""),
                    "message": (fail.get("message") or "")[:300],
                })
    return {"classes": classes, "tests": total, "failures": fails, "errors": errors,
            "skipped": skipped, "time": round(tsum, 2), "failuresDetail": details}


def run_jvm(classes: list[str], full: bool, log_dir: Path, timeout: int = 2400,
            offline: bool = True) -> dict:
    ensure_dir(log_dir)
    clear_test_results()
    log_path = log_dir / "jvm-testDebugUnitTest.log"
    args = [":app:testDebugUnitTest"]
    if not full:
        for c in classes:
            args += ["--tests", c]
    code, dur = _gradle(args, log_path, timeout, offline)
    parsed = parse_results()
    expected = set(classes)
    ran = set(parsed["classes"].keys())
    missing = sorted(expected - ran) if not full else []
    ok = (
        code == 0
        and parsed["failures"] == 0
        and parsed["errors"] == 0
        and not missing
        and (parsed["tests"] > 0)
    )
    status = STATUS_PASS if ok else STATUS_FAIL
    if code != 0 and not parsed["tests"]:
        parsed["failuresDetail"] = [{
            "class": "gradle", "test": "build/test task",
            "message": "Gradle 失败且没有生成测试 XML，见日志"}]  # type: ignore[index]
    if missing:
        parsed["failuresDetail"] = parsed.get("failuresDetail", []) + [{
            "class": "selection", "test": "missing",
            "message": "以下选中的测试类没有执行结果：" + ", ".join(missing)}]
    return {
        "status": status,
        "exitCode": code,
        "durationSec": round(dur, 1),
        "log": str(log_path),
        "tests": parsed["tests"],
        "failures": parsed["failures"] + parsed["errors"],
        "skipped": parsed["skipped"],
        "testTimeSec": parsed["time"],
        "classes": parsed["classes"],
        "failuresDetail": parsed.get("failuresDetail", []),
        "missing": missing,
        "full": full,
    }


def build_apks(kinds: list[str], log_dir: Path, timeout: int = 2400,
               offline: bool = True) -> dict:
    ensure_dir(log_dir)
    tasks = []
    if "qa" in kinds:
        tasks.append(":app:assembleQa")
    if "prod" in kinds:
        tasks.append(":app:assembleDebug")
    log_path = log_dir / "gradle-assemble.log"
    code, dur = _gradle(tasks, log_path, timeout, offline)
    return {"status": STATUS_PASS if code == 0 else STATUS_FAIL,
            "exitCode": code, "durationSec": round(dur, 1), "log": str(log_path),
            "tasks": tasks}
