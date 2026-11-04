"""注册表与测试资产自检（不依赖设备）。"""
from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path

from .util import ROOT, matches_any

TEST_ROOT = ROOT / "app" / "src" / "test" / "java"
DEVICE_ROOT = ROOT / "tools" / "ui-verify"
SCHEMA_ROOT = ROOT / "app" / "schemas" / "com.mengyalife.app.data.local.MengyaDatabase"


def jvm_classes() -> dict[str, Path]:
    out: dict[str, Path] = {}
    for p in TEST_ROOT.rglob("*.kt"):
        text = p.read_text(encoding="utf-8", errors="replace")
        m = re.search(r"^package\s+([\w.]+)", text, re.M)
        if not m:
            continue
        pkg = m.group(1)
        for cm in re.finditer(r"^(?:@\w+(?:\([^)]*\))?\s*)*(?:class|object)\s+(\w+)", text, re.M):
            out[f"{pkg}.{cm.group(1)}"] = p
    return out


def validate_map(map_: dict) -> list[str]:
    issues: list[str] = []
    mods = map_.get("modules", {})
    classes = jvm_classes()
    for mname, m in mods.items():
        for cls in m.get("jvmTests", []):
            if cls not in classes:
                issues.append(f"模块 {mname} 的 JVM 测试类不存在：{cls}")
        for dep in m.get("dependsOn", []):
            if dep not in mods:
                issues.append(f"模块 {mname} 的上游依赖不存在：{dep}")
        for d in m.get("device", []):
            p = DEVICE_ROOT / d["script"]
            if not p.exists():
                issues.append(f"模块 {mname} 的真机脚本不存在：{d['script']}")
                continue
            text = p.read_text(encoding="utf-8", errors="replace")
            if d["package"] == "prod":
                if "require_prod(" not in text:
                    issues.append(f"{d['script']} 是正式包脚本，但缺少 require_prod()")
            else:
                if d.get("destructive") and "require_qa(" not in text:
                    issues.append(f"{d['script']} 是破坏性 QA 脚本，但缺少 require_qa()")
            for key in ("tier", "assertions", "destructive", "resetsQaData"):
                if key not in d:
                    issues.append(f"{mname}/{d['script']} 缺少字段 {key}")
    for rule in map_.get("impactRules", []):
        for mname in rule.get("modules", []):
            if mname not in mods:
                issues.append(f"规则 {rule['id']} 引用了不存在的模块：{mname}")
        for mname in rule.get("deviceModules", []):
            if mname not in mods:
                issues.append(f"规则 {rule['id']} 的 deviceModules 引用了不存在的模块：{mname}")
    for lname in map_.get("levelOrder", []):
        if lname not in map_.get("levels", {}):
            issues.append(f"levelOrder 引用了不存在的级别：{lname}")
    for lname, lv in map_.get("levels", {}).items():
        for mname in lv.get("alwaysModules", []) or []:
            if mname not in mods:
                issues.append(f"级别 {lname} 的 alwaysModules 引用了不存在的模块：{mname}")
    for pat in map_.get("impactRules", []):
        for p in pat.get("patterns", []):
            if not p.strip():
                issues.append(f"规则 {pat['id']} 有空 pattern")
    return issues


def validate_schema_contract(map_: dict) -> list[str]:
    issues: list[str] = []
    db = ROOT / "app/src/main/java/com/mengyalife/app/data/local/MengyaDatabase.kt"
    text = db.read_text(encoding="utf-8", errors="replace")
    m = re.search(r"DB_SCHEMA_VERSION\s*=\s*(\d+)", text)
    if not m:
        return ["MengyaDatabase.kt 未找到 DB_SCHEMA_VERSION"]
    version = int(m.group(1))
    if not SCHEMA_ROOT.exists():
        return [f"Room schema 目录不存在：{SCHEMA_ROOT}"]
    existing = sorted(int(p.stem) for p in SCHEMA_ROOT.glob("*.json") if p.stem.isdigit())
    if existing != list(range(1, version + 1)):
        issues.append(f"schema 编号不连续：dated={existing}, DB_SCHEMA_VERSION={version}")
    cur = SCHEMA_ROOT / f"{version}.json"
    if not cur.exists():
        issues.append(f"当前版本 schema {version}.json 不存在")
    else:
        doc = json.loads(cur.read_text(encoding="utf-8"))
        if doc.get("database", {}).get("version") != version:
            issues.append(
                f"{version}.json 的 database.version={doc.get('database', {}).get('version')} 与代码 {version} 不一致")
    if version > 1 and f"MIGRATION_{version-1}_{version}" not in text:
        issues.append(f"未找到 MIGRATION_{version-1}_{version} 注册")
    # 注释里会提到「不使用 fallbackToDestructiveMigration」，只检查真实代码
    code = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    code = re.sub(r"//.*", "", code)
    if "fallbackToDestructiveMigration" in code.replace(" ", ""):
        issues.append("检测到 fallbackToDestructiveMigration：禁止破坏性迁移")
    if "ALL_MIGRATIONS" not in code and "addMigrations(" not in code:
        issues.append("未找到 addMigrations(...) 注册")
    return issues


def validate_scripts() -> list[str]:
    issues: list[str] = []
    for p in sorted(DEVICE_ROOT.glob("*.py")):
        r = subprocess.run([sys.executable, "-m", "py_compile", str(p)],
                           capture_output=True, text=True)
        if r.returncode != 0:
            issues.append(f"{p.name} 语法错误：{r.stderr.strip().splitlines()[-1] if r.stderr else ''}")
    wrapper = ROOT / "scripts" / "verify.sh"
    if wrapper.exists():
        r = subprocess.run(["bash", "-n", str(wrapper)], capture_output=True, text=True)
        if r.returncode != 0:
            issues.append(f"scripts/verify.sh 语法错误：{r.stderr.strip()}")
    for p in DEVICE_ROOT.glob("*.py"):
        if p.name == "harness.py":
            continue
        text = p.read_text(encoding="utf-8", errors="replace")
        if "sys.path.insert" not in text:
            issues.append(f"{p.name} 未把 ui-verify 目录加入 sys.path")
    return issues


def run_static_checks(map_: dict, checks: list[str]) -> list[str]:
    issues: list[str] = []
    if "validate-map" in checks:
        issues += validate_map(map_)
    if "schema-contract" in checks:
        issues += validate_schema_contract(map_)
    if "script-syntax" in checks:
        issues += validate_scripts()
    for i in issues:
        print(f"  [static] {i}")
    return issues
