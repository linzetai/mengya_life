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
            if d.get("scenarios"):
                if not d.get("scenarioEnvVar"):
                    issues.append(f"{mname}/{d['script']} 有 scenarios 但缺少 scenarioEnvVar")
                if len(d["scenarios"]) != len(set(d["scenarios"])):
                    issues.append(f"{mname}/{d['script']} scenarios 有重复值")
                min_level = d.get("scenarioMinLevel")
                if min_level and min_level not in map_.get("levels", {}):
                    issues.append(f"{mname}/{d['script']} scenarioMinLevel 无效：{min_level}")
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
    issues.extend(validate_restore_safety_source())
    return issues


def validate_restore_safety_source() -> list[str]:
    """Guard critical fail-closed ordering and the shared business-write boundary."""
    root = ROOT / "app/src/main/java/com/mengyalife/app"
    barrier = (root / "data/restore/RestoreWriteBarrier.kt").read_text(encoding="utf-8")
    coordinator = (root / "data/restore/RestoreCoordinator.kt").read_text(encoding="utf-8")
    worker = (root / "data/weekly/WeeklyReviewWorker.kt").read_text(encoding="utf-8")
    ai = (root / "data/weekly/WeeklyAiService.kt").read_text(encoding="utf-8")
    goal = (root / "data/repository/GoalRepository.kt").read_text(encoding="utf-8")
    journal = (root / "data/repository/JournalRepository.kt").read_text(encoding="utf-8")
    root_app = (root / "MengyaLifeApp.kt").read_text(encoding="utf-8")
    gate_test = (TEST_ROOT / "com/mengyalife/app/restore/RestoreGateJournalTest.kt").read_text(encoding="utf-8")
    issues: list[str] = []
    if "if (!ready) throw RestoreException(\"RESTORE_BLOCKED\"" not in barrier:
        issues.append("RestoreWriteBarrier 未在非READY状态拒绝写入")
    if "withContext(Permit(this, exclusive = false)) { block() }" not in barrier:
        issues.append("普通业务写入口未统一通过RestoreWriteBarrier")
    if "if (metaResult.isFailure) { finishMaintenance(); return }" not in coordinator:
        issues.append("DB app_meta读取失败未明确fail-closed到MAINTENANCE")
    if "private suspend fun readMetaResult(): Result<AppMetaEntity?> =\n        runCatching { database.appMetaDao().get() }" not in coordinator:
        issues.append("app_meta读取异常未封装为可判定的Result")
    readiness_pos = root_app.find("val restoreGateActive")
    first_viewmodel_pos = root_app.find("viewModel(")
    if readiness_pos < 0 or first_viewmodel_pos < 0 or readiness_pos > first_viewmodel_pos:
        issues.append("restore startup gate必须早于任意数据库ViewModel/Flow创建")
    if "if (restoreGateActive)" not in root_app[:first_viewmodel_pos]:
        issues.append("未READY时缺少只读维护屏的早期return")
    if "gate.clear(); journal.clear(); finishReady()" not in coordinator:
        issues.append("Gate/Journal释放后才进入READY的顺序缺失")
    converge = coordinator[coordinator.find("private suspend fun converge") : coordinator.find("private fun writeQaPhase")]
    release = converge.find("gate.clear(); journal.clear(); finishReady()")
    catch = converge.find("catch (e: Exception)")
    if release < 0 or catch < release or "finishMaintenance()" not in converge[catch:]:
        issues.append("Gate/Journaling释放失败没有收敛到MAINTENANCE")
    if "restoreWriteBarrier.withWrite { runWorker(app) }" not in worker:
        issues.append("WeeklyReviewWorker的副作用区未被写屏障包住")
    if "writeBarrier?.withWrite(expectedGeneration) { block() }" not in ai:
        issues.append("Weekly AI 请求引擎未受恢复写屏障保护")
    if "writeBarrier?.withWrite(expectedGeneration) { ensureWritable(); block() }" not in goal:
        issues.append("GoalRepository普通写入口未受恢复写屏障保护")
    if "writeBarrier?.withWrite(expectedGeneration) { ensureWritable(); block() }" not in journal:
        issues.append("JournalRepository普通写入口未受恢复写屏障保护")
    if "gate clear failure is not silent" not in gate_test:
        issues.append("缺少Gate清除失败测试")
    return issues


def validate_selection_policy(map_: dict) -> list[str]:
    """Self-test duplicate collapse and the explicit local-only dev profile."""
    try:
        from .selection import apply_case_filter, apply_execution_profile, device_run_mode, select
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
        weekly_case = select(map_, None, ["weekly-ai"], "integration")
        apply_case_filter(weekly_case, "FAILED_401")
        if len(weekly_case.device_scripts) != 1 or weekly_case.device_scripts[0]["script"] != "verify_weekly_ai.py":
            return ["weekly-ai 失败场景case应可单独选择"]

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
        backup_case = select(map_, writer_change, None, None)
        apply_case_filter(backup_case, "write")
        if len(backup_case.device_scripts) != 1 or backup_case.device_scripts[0]["script"] != "verify_backup_v2_failures.py":
            return ["backup --case write 应只选择一个故障scenario"]

        restore_case = select(map_, None, ["restore-validate"], "integration")
        apply_case_filter(restore_case, "R7")
        if len(restore_case.device_scripts) != 1 or restore_case.device_scripts[0]["script"] != "verify_restore_failures.py":
            return ["restore --case R7 应只选择故障矩阵脚本"]
        if len(restore_case.deferred_device_scripts) != 4:
            return ["restore --case R7 必须明确延期其余四个脚本"]
        e2e_case = select(map_, None, ["restore-validate"], "integration")
        apply_case_filter(e2e_case, "A_TO_B_TO_A")
        if len(e2e_case.device_scripts) != 1 or e2e_case.device_scripts[0]["script"] != "verify_restore_e2e.py":
            return ["A->B->A E2E 应可独立选择"]
        fresh_case = select(map_, None, ["restore-validate"], "integration")
        apply_case_filter(fresh_case, "FRESH_V12")
        if len(fresh_case.device_scripts) != 1 or fresh_case.device_scripts[0]["script"] != "verify_restore_fresh_db.py":
            return ["fresh v12 scenario 应可单独选择"]
        db_unreadable_case = select(map_, None, ["restore-validate"], "integration")
        apply_case_filter(db_unreadable_case, "DB_UNREADABLE")
        if len(db_unreadable_case.device_scripts) != 1 or db_unreadable_case.device_scripts[0]["script"] != "verify_restore_db_unreadable.py":
            return ["DB不可读startup fail-closed scenario应可单独选择"]
        settings_fail_case = select(map_, None, ["restore-validate"], "integration")
        apply_case_filter(settings_fail_case, "SETTINGS_FAIL")
        if len(settings_fail_case.device_scripts) != 1 or settings_fail_case.device_scripts[0]["script"] != "verify_restore_failures.py":
            return ["Gate/maintenance失败场景应可单独选择"]

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
        barrier_change = ChangeSet(
            base="(self-test)", base_reason="policy self-test",
            changes=[Change("app/src/main/java/com/mengyalife/app/data/restore/RestoreWriteBarrier.kt", "M", "self-test")],
            commit="self-test", branch="self-test",
        )
        barrier_plan = select(map_, barrier_change, None, None)
        if barrier_plan.level != "integration" or "com.mengyalife.app.restore.RestoreWriteBarrierTest" not in barrier_plan.jvm_classes:
            return ["RestoreWriteBarrier 改动必须升级L2并运行屏障单测"]
        barrier_scripts = {d["script"] for d in barrier_plan.device_scripts}
        if not {"verify_restore_e2e.py", "verify_restore_failures.py", "verify_g3b1.py", "verify_migration.py"}.issubset(barrier_scripts):
            return ["RestoreWriteBarrier 改动的L2安全/迁移设备覆盖不足"]
        if "verify_navigation.py" in barrier_scripts:
            return ["数据层写屏障变更不应重复加入全局导航冒烟"]

        startup_gate_change = ChangeSet(
            base="(self-test)", base_reason="policy self-test",
            changes=[Change("app/src/main/java/com/mengyalife/app/MengyaLifeApp.kt", "M", "self-test")],
            commit="self-test", branch="self-test",
        )
        startup_gate_plan = select(map_, startup_gate_change, None, None)
        startup_scripts = {d["script"] for d in startup_gate_plan.device_scripts}
        if startup_gate_plan.level != "integration" or not {
            "verify_restore_db_unreadable.py", "verify_restore_fresh_db.py", "verify_restore_e2e.py",
            "verify_weekly_ai.py", "verify_g3b1.py",
        }.issubset(startup_scripts):
            return ["startup gate变更必须覆盖DB不可读/fresh/restore/weekly/consent L2"]
        for guarded_path in (
            "app/src/main/java/com/mengyalife/app/data/weekly/WeeklyReviewWorker.kt",
            "app/src/main/java/com/mengyalife/app/di/AppContainer.kt",
        ):
            guarded = select(map_, ChangeSet(
                base="(self-test)", base_reason="policy self-test",
                changes=[Change(guarded_path, "M", "self-test")],
                commit="self-test", branch="self-test",
            ), None, None)
            guarded_scripts = {d["script"] for d in guarded.device_scripts}
            if guarded.level != "integration" or "verify_restore_db_unreadable.py" not in guarded_scripts:
                return [f"{guarded_path}改动必须命中恢复维护/Worker fail-closed L2探针"]

        gate_case = select(map_, None, ["restore-validate"], "gate")
        try:
            apply_case_filter(gate_case, "R7")
        except SystemExit:
            pass
        else:
            return ["Gate不允许用单case PASS跳过必须执行的完整安全用例"]

        unknown_change = ChangeSet(
            base="(self-test)", base_reason="policy self-test",
            changes=[Change("unmapped/restore-sensitive.kt", "M", "self-test")],
            commit="self-test", branch="self-test",
        )
        unknown_plan = select(map_, unknown_change, None, None)
        if not unknown_plan.unknown_files or len(unknown_plan.modules) != len(map_["modules"]):
            return ["未知源码路径必须保守扩大到完整模块选择"]

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
