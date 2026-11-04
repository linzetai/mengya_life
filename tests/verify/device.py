"""adb / 真机执行 / 包与 APK 安全校验。"""
from __future__ import annotations

import hashlib
import json
import os
import re
import sqlite3
import subprocess
import tempfile
import time
from pathlib import Path

from .util import ROOT, STATUS_FAIL, STATUS_PASS, STATUS_SKIP, ensure_dir, iso, sha256_file

ADB = os.environ.get("ADB", "adb")


def adb(serial: str, args: list[str] | str, timeout: int = 60,
        binary: bool = False) -> tuple[int, bytes | str]:
    cmd = [ADB, "-s", serial] + (args.split() if isinstance(args, str) else args)
    try:
        p = subprocess.run(cmd, capture_output=True, timeout=timeout)
        out: bytes | str = p.stdout if binary else p.stdout.decode("utf-8", "replace")
        return p.returncode, out
    except subprocess.TimeoutExpired:
        return 124, (b"" if binary else "")


def list_devices() -> list[dict]:
    p = subprocess.run([ADB, "devices", "-l"], capture_output=True, text=True)
    out = []
    for line in p.stdout.splitlines()[1:]:
        line = line.strip()
        if not line:
            continue
        # 不同 adb 版本用 tab 或空格分隔，统一按空白解析
        parts = line.split()
        if len(parts) < 2:
            continue
        serial, state = parts[0], parts[1]
        if state != "device":
            continue
        rest = " ".join(parts[2:])
        model = re.search(r"model:(\S+)", rest)
        product = re.search(r"product:(\S+)", rest)
        out.append({"serial": serial, "model": model.group(1) if model else "",
                    "product": product.group(1) if product else ""})
    return out


def resolve_device() -> tuple[str | None, str]:
    env = os.environ.get("DEV", "").strip()
    if env:
        return env, "环境变量 DEV"
    devs = list_devices()
    if len(devs) == 1:
        return devs[0]["serial"], "adb 唯一设备"
    if len(devs) > 1:
        return None, f"检测到 {len(devs)} 台设备，请用 DEV= 指定：" + ", ".join(d["serial"] for d in devs)
    return None, "没有处于 device 状态的 adb 设备（请 export DEV=<IP>:<端口>）"


def device_env(serial: str) -> dict:
    code, model = adb(serial, "shell getprop ro.product.model")
    code2, release = adb(serial, "shell getprop ro.build.version.release")
    code3, sdk = adb(serial, "shell getprop ro.build.version.sdk")
    return {
        "serial": serial,
        "model": str(model).strip(),
        "android": str(release).strip(),
        "sdk": str(sdk).strip(),
        "fetchedAt": iso(),
    }


def package_info(serial: str, pkg: str) -> dict | None:
    code, out = adb(serial, f"shell dumpsys package {pkg}", timeout=60)
    out = str(out)
    if code != 0 or f"Package [{pkg}]" not in out:
        return None
    info = {"package": pkg}
    for key in ("versionName", "versionCode", "lastUpdateTime", "firstInstallTime"):
        m = re.search(rf"{key}=(\S+)", out)
        info[key] = m.group(1) if m else ""
    m = re.search(r"flags=\[([^\]]*)\]", out)
    info["flags"] = m.group(1) if m else ""
    info["debuggable"] = "DEBUGGABLE" in info["flags"]
    info["installed"] = True
    return info


def installed_apk_sha(serial: str, pkg: str) -> str | None:
    code, out = adb(serial, f"shell pm path {pkg}", timeout=30)
    out = str(out)
    m = re.search(r"package:(\S+)", out)
    if code != 0 or not m:
        return None
    code, data = adb(serial, ["exec-out", "cat", m.group(1)], timeout=180, binary=True)
    if code != 0 or not data:
        return None
    return hashlib.sha256(data).hexdigest()


def install_apk(serial: str, path: Path, timeout: int = 240) -> tuple[bool, str, float]:
    start = time.time()
    p = subprocess.run([ADB, "-s", serial, "install", "-r", "-t", str(path)],
                       capture_output=True, text=True, timeout=timeout)
    dur = time.time() - start
    ok = p.returncode == 0 and "Success" in p.stdout
    detail = (p.stdout + p.stderr).strip()
    return ok, detail, dur


def pull_db(serial: str, pkg: str, db_name: str, dest_dir: Path) -> Path | None:
    """只读拉取数据库（含 WAL/SHM），用于断言；不写设备。"""
    ensure_dir(dest_dir)
    base = dest_dir / db_name
    ok_any = False
    for suffix in ("", "-wal", "-shm"):
        code, data = adb(serial, ["exec-out", "run-as", pkg, "cat", f"databases/{db_name}{suffix}"],
                         timeout=120, binary=True)
        target = Path(str(base) + suffix)
        if code == 0 and data:
            target.write_bytes(data)  # type: ignore[arg-type]
            ok_any = True
        elif target.exists():
            target.unlink()
    return base if ok_any else None


def query_db(path: Path, sql: str) -> list[tuple]:
    con = sqlite3.connect(str(path))
    try:
        return con.execute(sql).fetchall()
    finally:
        con.close()


def prod_fixture_ok(serial: str, pkg: str, db_name: str) -> tuple[bool, str]:
    """正式包只读冒烟的前置数据：2 条迁移日记、0 目标/进展。不修改任何数据。"""
    tmp = Path(tempfile.mkdtemp(prefix="verify-prod-fixture-"))
    db = pull_db(serial, pkg, db_name, tmp)
    if not db:
        return False, "无法读取正式库（run-as 不可用或文件不存在）"
    try:
        j = query_db(db, "SELECT COUNT(*) FROM journal_entries")[0][0]
        g = query_db(db, "SELECT COUNT(*) FROM goals")[0][0]
        c = query_db(db, "SELECT COUNT(*) FROM goal_checkins")[0][0]
        if (j, g, c) != (2, 0, 0):
            return False, f"正式库前置数据不满足 (日记,目标,进展)=({j},{g},{c})，要求 (2,0,0)"
        return True, "正式库前置数据满足 (2,0,0)"
    except Exception as e:
        return False, f"读取正式库失败：{e}"


def network_available(serial: str) -> bool:
    code, out = adb(serial, "shell ping -c 1 -W 2 223.5.5.5", timeout=12)
    return code == 0 and "1 packets received" in str(out)


def run_device_script(serial: str, script: str, pkg: str, log_dir: Path,
                      shots_dir: Path, export_dir: Path, timeout: int,
                      extra_env: dict | None = None) -> dict:
    ensure_dir(log_dir)
    ensure_dir(shots_dir)
    ensure_dir(export_dir)
    name = script.replace(".py", "")
    log_path = log_dir / f"{pkg.split('.')[-1]}-{name}.log"
    env = dict(os.environ)
    env.update({
        "DEV": serial,
        "MENGYA_PKG": pkg,
        "SHOT_DIR": str(shots_dir),
        "EXPORT_OUT": str(export_dir),
        "MENGYA_QA_BUILD": "1" if pkg.endswith(".qa") else "0",
        "PYTHONUNBUFFERED": "1",
    })
    if extra_env:
        env.update(extra_env)
    start = time.time()
    with open(log_path, "w", encoding="utf-8") as f:
        try:
            p = subprocess.run(
                ["python3", f"tools/ui-verify/{script}"],
                cwd=str(ROOT), env=env, stdout=f, stderr=subprocess.STDOUT,
                timeout=timeout,
            )
            rc = p.returncode
        except subprocess.TimeoutExpired:
            rc = 124
            f.write(f"\n[TIMEOUT after {timeout}s]\n")
    dur = time.time() - start
    text = log_path.read_text(encoding="utf-8", errors="replace")
    m = re.search(r"SUMMARY:\s*(\d+)/(\d+)\s*passed", text)
    passed = int(m.group(1)) if m else 0
    total = int(m.group(2)) if m else 0
    failures = re.findall(r"^FAIL\s+(.+)$", text, re.M)
    if rc == 0 and total > 0 and passed == total:
        status = STATUS_PASS
    elif rc == 0 and total == 0:
        status = STATUS_FAIL
        failures.append("脚本退出 0 但没有 SUMMARY：报告不可信，按 FAIL 处理")
    else:
        status = STATUS_FAIL
    if rc == 124:
        failures.append(f"超时（{timeout}s）")
    return {
        "script": script,
        "package": pkg,
        "status": status,
        "exitCode": rc,
        "durationSec": round(dur, 1),
        "assertionsPassed": passed,
        "assertionsTotal": total,
        "failures": failures[:20],
        "log": str(log_path),
        "tail": "\n".join(text.strip().splitlines()[-12:]),
    }
