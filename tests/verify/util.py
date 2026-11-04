"""调度器公共工具：路径、glob、日志、子进程。"""
from __future__ import annotations

import hashlib
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAP_PATH = ROOT / "tests" / "test-map.json"
RUNS_DIR = ROOT / "docs" / "reports" / "test-runs"
STATE_DIR = ROOT / ".verify"

STATUS_PASS = "PASS"
STATUS_FAIL = "FAIL"
STATUS_SKIP = "SKIPPED"
STATUS_NOT_RUN = "NOT RUN"


def log(msg: str = "") -> None:
    print(msg, flush=True)


def now() -> float:
    return time.time()


def iso(ts: float | None = None) -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S%z", time.localtime(ts if ts is not None else time.time()))


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


_GLOB_CACHE: dict[str, re.Pattern] = {}


def glob_regex(pattern: str) -> re.Pattern:
    """把仓库内 POSIX glob 编译为正则：* 不跨目录，** 跨目录。"""
    if pattern in _GLOB_CACHE:
        return _GLOB_CACHE[pattern]
    i = 0
    out = ["^"]
    while i < len(pattern):
        c = pattern[i]
        if c == "*":
            if i + 1 < len(pattern) and pattern[i + 1] == "*":
                out.append(".*")
                i += 2
                if i < len(pattern) and pattern[i] == "/":
                    i += 1
                continue
            out.append("[^/]*")
        elif c == "?":
            out.append("[^/]")
        else:
            out.append(re.escape(c))
        i += 1
    out.append("$")
    reg = re.compile("".join(out))
    _GLOB_CACHE[pattern] = reg
    return reg


def matches_any(path: str, patterns: list[str]) -> bool:
    return any(glob_regex(p).match(path) for p in patterns)


def run_cmd(cmd: list[str] | str, cwd: Path | None = None, timeout: int | None = None,
            shell: bool = False, env: dict | None = None) -> tuple[int, str, float]:
    start = time.time()
    try:
        p = subprocess.run(
            cmd,
            cwd=str(cwd or ROOT),
            shell=shell,
            capture_output=True,
            text=True,
            errors="replace",
            timeout=timeout,
            env=env,
        )
        code = p.returncode
        out = (p.stdout or "") + (("\n[stderr]\n" + p.stderr) if p.stderr else "")
    except subprocess.TimeoutExpired as e:
        code = 124
        out = (e.stdout or "") if isinstance(e.stdout, str) else ""
        out += f"\n[timeout after {timeout}s]"
    return code, out, time.time() - start


def git(*args: str, base: Path | None = None) -> tuple[int, str]:
    code, out, _ = run_cmd(["git", *args], cwd=base or ROOT)
    return code, out


def ensure_dir(p: Path) -> Path:
    p.mkdir(parents=True, exist_ok=True)
    return p


def rel(p: Path | str) -> str:
    p = Path(p)
    try:
        return p.resolve().relative_to(ROOT).as_posix()
    except Exception:
        return str(p)


@dataclass
class StepResult:
    name: str
    status: str
    duration_sec: float
    detail: str = ""
    log: str | None = None
    counts: dict = field(default_factory=dict)


def write_text(path: Path, text: str) -> None:
    ensure_dir(path.parent)
    path.write_text(text, encoding="utf-8")


def tail(text: str, n: int = 40) -> str:
    lines = text.strip().splitlines()
    return "\n".join(lines[-n:])
