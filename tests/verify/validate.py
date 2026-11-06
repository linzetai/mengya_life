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
        if not isinstance(m.get("skipNavigationSmoke", False), bool):
            issues.append(f"模块 {mname} 的 skipNavigationSmoke 必须是 bool")
        for excluded in m.get("skipAlwaysModules", []):
            if excluded not in mods:
                issues.append(f"模块 {mname} 的 skipAlwaysModules 引用了不存在的模块：{excluded}")
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
            if d.get("smokeEnvVar"):
                smoke_levels = d.get("smokeLevels", ["feature"])
                if not smoke_levels or any(level not in map_.get("levels", {}) for level in smoke_levels):
                    issues.append(f"{mname}/{d['script']} 的 smokeLevels 无效")
                if not d.get("smokeAssertions") or not d.get("measuredFullSec"):
                    issues.append(f"{mname}/{d['script']} 有 smokeEnvVar 但缺少 smoke/full 覆盖元数据")
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
    issues.extend(validate_selection_policy(map_))
    return issues


def validate_selection_policy(map_: dict) -> list[str]:
    """Self-test duplicate collapse and the explicit local-only dev profile."""
    try:
        from .selection import apply_execution_profile, device_run_mode, select
        sample = select(map_, None, ["advisor", "ai-consent", "settings"], "integration")
        keys = [(d["script"], d["package"]) for d in sample.device_scripts]
        if len(keys) != len(set(keys)):
            return ["真机选择器未按 script+package 去重"]
        for expected_once in ("verify_g3b1.py", "verify_settings.py"):
            if sum(1 for d in sample.device_scripts if d["script"] == expected_once) != 1:
                return [f"跨模块共享脚本未合并为一次运行：{expected_once}"]
        if any(d["script"] == "verify_navigation.py" for d in sample.device_scripts):
            return ["所有直接模块均有自身UI覆盖时仍重复加入导航冒烟"]
        jvm_before = list(sample.jvm_classes)
        apply_execution_profile(sample, "dev")
        if sample.device_scripts:
            return ["dev profile 仍然计划执行真机"]
        if len(sample.deferred_device_scripts) != len(keys):
            return ["dev profile 未完整记录被延期的设备脚本"]
        if sample.jvm_classes != jvm_before:
            return ["dev profile 改变了受影响 JVM 用例集合"]
        if not sample.deferred_device_scripts and keys:
            return ["dev profile 丢失设备延期清单"]

        goals = select(map_, None, ["goals"], "feature")
        if any(d["script"] == "verify_navigation.py" for d in goals.device_scripts):
            return ["goals已有专属UI用例，不应重复跑全局导航冒烟"]

        from .changes import Change, ChangeSet
        shared_change = ChangeSet(
            base="(self-test)", base_reason="policy self-test",
            changes=[Change("app/src/main/java/com/mengyalife/app/MengyaLifeApp.kt", "M", "self-test")],
            commit="self-test", branch="self-test",
        )
        shared = select(map_, shared_change, None, "feature")
        if not any(d["script"] == "verify_navigation.py" for d in shared.device_scripts):
            return ["导航/共享UI变更必须保留跨页导航冒烟"]

        weekly_l2 = select(map_, None, ["weekly-ai"], "integration")
        weekly_entry = next((d for d in weekly_l2.device_scripts if d["script"] == "verify_weekly_ai.py"), None)
        if weekly_entry is None or device_run_mode(weekly_entry, weekly_l2.level) != "smoke":
            return ["weekly-ai L2 应跑成功冒烟，不应误跑Gate全矩阵"]
        weekly_gate = select(map_, None, ["weekly-ai"], "gate")
        weekly_gate_entry = next((d for d in weekly_gate.device_scripts if d["script"] == "verify_weekly_ai.py"), None)
        if weekly_gate_entry is None or device_run_mode(weekly_gate_entry, weekly_gate.level) != "full":
            return ["weekly-ai Gate/Release 必须保留完整状态矩阵"]

        backup_l2 = select(map_, None, ["backup-export"], "integration")
        backup_scripts = {d["script"] for d in backup_l2.device_scripts}
        if "verify_g3b1.py" in backup_scripts or "verify_navigation.py" in backup_scripts:
            return ["独立备份模块L2不应重复运行无关AI授权/导航用例"]
        if "verify_migration.py" not in backup_scripts:
            return ["备份模块L2仍需保留数据库迁移前置"]
        from .changes import Change, ChangeSet
        writer_change = ChangeSet(
            base="(self-test)", base_reason="policy self-test",
            changes=[Change("app/src/main/java/com/mengyalife/app/data/backup/BackupFileWriter.kt", "M", "self-test")],
            commit="self-test", branch="self-test",
        )
        backup_fault = select(map_, writer_change, None, None)
        fault_scripts = {d["script"] for d in backup_fault.device_scripts}
        if backup_fault.level != "integration" or "verify_backup_v2_failures.py" not in fault_scripts:
            return ["备份写入故障代码改动必须升级L2并选择故障矩阵"]
        if "verify_g3b1.py" in fault_scripts or "verify_navigation.py" in fault_scripts:
            return ["备份L2不应捎带无关授权或全局导航用例"]

        backup_ui_change = ChangeSet(
            base="(self-test)", base_reason="policy self-test",
            changes=[Change("app/src/main/java/com/mengyalife/app/ui/settings/BackupSection.kt", "M", "self-test")],
            commit="self-test", branch="self-test",
        )
        backup_ui = select(map_, backup_ui_change, None, "feature")
        backup_ui_scripts = {d["script"] for d in backup_ui.device_scripts}
        if "backup-export" not in backup_ui.module_names() or "settings" in backup_ui.module_names():
            return ["BackupSection 改动应只命中备份模块，不应触发整页设置重复测试"]
        if "verify_backup_v2_full.py" not in backup_ui_scripts or any(
            x in backup_ui_scripts for x in ("verify_settings.py", "verify_theme.py", "verify_navigation.py")
        ):
            return ["备份Section L1 应只跑全字段导出用例，不重复跑设置/导航"]

        backup_gate = select(map_, None, ["backup-export"], "gate")
        if "verify_g3b1.py" not in {d["script"] for d in backup_gate.device_scripts}:
            return ["全量Gate不能因backup模块的focused exclusion漏掉AI授权"]

        restore_l2 = select(map_, None, ["restore-validate"], "integration")
        if "verify_g3b1.py" in {d["script"] for d in restore_l2.device_scripts}:
            return ["独立restore L2应以端到端安全断言替代泛用AI授权套件"]
        combined_l2 = select(map_, None, ["restore-validate", "weekly-ai"], "integration")
        if "verify_g3b1.py" not in {d["script"] for d in combined_l2.device_scripts}:
            return ["合并restore+weekly时不能让restore exclusion屏蔽weekly授权覆盖"]
    except Exception as exc:
        return [f"选择器策略自检失败：{type(exc).__name__}: {exc}"]
    return []


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
