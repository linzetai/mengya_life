"""测试模块选择：源码 -> 模块 -> JVM/真机入口 -> 执行级别。

所有判断只来自 tests/test-map.json，不在代码里写死业务路径；
无法匹配的源码变化一律保守升级到 L3。
"""
from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass, field
from pathlib import Path

from .changes import ChangeSet
from .util import MAP_PATH, ROOT, matches_any

LEVEL_ORDER = ["fast", "feature", "integration", "gate", "release"]

DOC_PATTERNS = ["docs/**", "**/*.md", "artifacts/**", ".gitignore", "LICENSE", "LICENSE.*"]
# 这些路径下的未知文件视为「可能影响构建/运行」的源码，必须保守升级
CODE_ROOTS = (
    "app/", "gradle/", "keystore/",
    "build.gradle", "build.gradle.kts",
    "settings.gradle", "settings.gradle.kts",
    "gradle.properties", "build.sh", "env.sh", "gradlew",
)


def _unique_object(pairs: list[tuple[str, object]]) -> dict:
    out = {}
    for key, value in pairs:
        if key in out:
            raise ValueError(f"test-map.json contains duplicate key: {key}")
        out[key] = value
    return out


def load_map(path: Path | None = None) -> dict:
    p = path or MAP_PATH
    with open(p, encoding="utf-8") as f:
        return json.load(f, object_pairs_hook=_unique_object)


def level_index(name: str) -> int:
    try:
        return LEVEL_ORDER.index(name)
    except ValueError:
        raise SystemExit(f"未知执行级别：{name}（可选：{', '.join(LEVEL_ORDER)}）")


def max_level(*levels: str) -> str:
    return max(levels, key=level_index)


@dataclass
class ModulePlan:
    name: str
    title: str
    risk: str
    reasons: list[str] = field(default_factory=list)
    matched_files: list[str] = field(default_factory=list)
    propagated_from: list[str] = field(default_factory=list)
    jvm: list[str] = field(default_factory=list)
    device: list[dict] = field(default_factory=list)

    def as_dict(self) -> dict:
        return {
            "name": self.name,
            "title": self.title,
            "risk": self.risk,
            "reasons": self.reasons,
            "matchedFiles": self.matched_files,
            "propagatedFrom": self.propagated_from,
            "jvmTests": self.jvm,
            "device": [
                {"script": d["script"], "package": d["package"], "tier": d["tier"]}
                for d in self.device
            ],
        }


@dataclass
class Selection:
    level: str
    mode: str  # changed / module / all / explicit
    profile: str = "standard"  # standard / dev (local-only, never a Gate PASS)
    case_filter: str | None = None
    deferred_device_scripts: list[dict] = field(default_factory=list)
    modules: list[ModulePlan] = field(default_factory=list)
    device_scripts: list[dict] = field(default_factory=list)
    jvm_classes: list[str] = field(default_factory=list)
    full_jvm: bool = False
    rules_hit: list[dict] = field(default_factory=list)
    unknown_files: list[str] = field(default_factory=list)
    docs_only: bool = False
    not_selected: list[dict] = field(default_factory=list)
    not_run: list[dict] = field(default_factory=list)
    static_checks: list[str] = field(default_factory=list)
    device_required: bool = False
    device_required_original: bool = False
    prod_smoke_required: bool = False
    level_satisfied_requires_device: bool = False
    warnings: list[str] = field(default_factory=list)

    def module_names(self) -> list[str]:
        return [m.name for m in self.modules]

    def as_dict(self) -> dict:
        return {
            "level": self.level,
            "mode": self.mode,
            "profile": self.profile,
            "caseFilter": self.case_filter,
            "modules": [m.as_dict() for m in self.modules],
            "jvmClasses": self.jvm_classes,
            "fullJvm": self.full_jvm,
            "deviceScripts": [
                dict(d, runMode=f"case:{self.case_filter}" if self.case_filter else device_run_mode(d, self.level))
                for d in self.device_scripts
            ],
            "deferredDeviceScripts": [
                dict(d, runMode=device_run_mode(d, self.level)) for d in self.deferred_device_scripts
            ],
            "rulesHit": self.rules_hit,
            "unknownFiles": self.unknown_files,
            "docsOnly": self.docs_only,
            "notSelected": self.not_selected,
            "notRun": self.not_run,
            "staticChecks": self.static_checks,
            "deviceRequired": self.device_required,
            "deviceRequiredOriginal": self.device_required_original,
            "prodSmokeRequired": self.prod_smoke_required,
            "warnings": self.warnings,
        }


def _module_matches(module: dict, path: str) -> bool:
    return matches_any(path, module.get("sourcePaths", []))


def _rule_matches(rule: dict, path: str) -> bool:
    if matches_any(path, rule.get("patterns", [])):
        return True
    return False


def _is_doc(path: str) -> bool:
    return matches_any(path, DOC_PATTERNS)


def _is_code_candidate(path: str) -> bool:
    return any(path == r or path.startswith(r) for r in CODE_ROOTS)


def _dependents(map_: dict, name: str) -> list[str]:
    out = []
    for mname, m in map_["modules"].items():
        if name in m.get("dependsOn", []):
            out.append(mname)
    return out


def select(map_: dict, cs: ChangeSet | None, modules: list[str] | None,
           level: str | None, all_flag: bool = False) -> Selection:
    mods = map_["modules"]
    levels = map_["levels"]

    if all_flag:
        level_name = level or "gate"
        return _select_full(map_, level_name, mode="all", reason="--all 强制全量回归")

    # 显式模块：只跑指定模块（+ 其默认级别），不自动扩散上游依赖
    if modules:
        unknown = [m for m in modules if m not in mods]
        if unknown:
            raise SystemExit(f"未知模块：{', '.join(unknown)}\n可用模块：{', '.join(sorted(mods))}")
        level_name = level if level is not None else max_level(*[mods[m]["defaultLevel"] for m in modules])
        sel = Selection(level=level_name, mode="module")
        for m in modules:
            plan = ModulePlan(m, mods[m]["title"], mods[m]["risk"],
                              reasons=[f"显式指定 --module {m}"])
            sel.modules.append(plan)
        _apply_level(map_, sel, direct_modules=set(modules))
        sel.rules_hit.append({"id": "explicit-module", "reason": "用户显式指定模块"})
        return sel

    if cs is None:
        raise SystemExit("内部错误：没有变更集")

    changed = cs.paths
    sel = Selection(level=level or "fast", mode="changed")

    matched_modules: dict[str, ModulePlan] = {}
    rule_hits: list[dict] = []
    unknown: list[str] = []
    docs = [p for p in changed if _is_doc(p)]

    for path in changed:
        hit = False
        for mname, m in mods.items():
            if _module_matches(m, path):
                hit = True
                plan = matched_modules.setdefault(
                    mname, ModulePlan(mname, m["title"], m["risk"]))
                if path not in plan.matched_files:
                    plan.matched_files.append(path)
                if not any(r.startswith("源码路径命中") for r in plan.reasons):
                    plan.reasons.append(f"源码路径命中（{path}）")
        for rule in map_["impactRules"]:
            if not _rule_matches(rule, path):
                continue
            hit = True
            rh = {
                "id": rule["id"],
                "description": rule["description"],
                "reason": rule["reason"],
                "files": [],
                "minLevel": rule.get("minLevel", "fast"),
                "modules": rule.get("modules", []),
            }
            existing = next((r for r in rule_hits if r["id"] == rule["id"]), None)
            if existing is None:
                rule_hits.append(rh)
                existing = rh
            if path not in existing["files"]:
                existing["files"].append(path)
        if not hit:
            if _is_doc(path):
                continue
            if _is_code_candidate(path):
                unknown.append(path)
            else:
                unknown.append(path)

    # 规则显式模块
    rule_modules: set[str] = set()
    for rh in rule_hits:
        rule_modules.update(rh["modules"])
        rule = next(r for r in map_["impactRules"] if r["id"] == rh["id"])
        for mname in rule.get("modules", []):
            if mname not in mods:
                continue
            plan = matched_modules.setdefault(mname, ModulePlan(mname, mods[mname]["title"], mods[mname]["risk"]))
            plan.reasons.append(f"规则 {rule['id']}：{rule['description']}")

    # 依赖传播：上游模块变化 -> 直接依赖它的模块（仅 JVM，L2+ 才跑其真机）
    propagated: dict[str, list[str]] = {}
    depth = int(map_.get("propagation", {}).get("maxDepth", 1))
    frontier = set(matched_modules.keys())
    for _ in range(depth):
        nxt = set()
        for mname in list(frontier):
            for dep in _dependents(map_, mname):
                if dep in matched_modules:
                    continue
                plan = matched_modules.setdefault(
                    dep, ModulePlan(dep, mods[dep]["title"], mods[dep]["risk"]))
                if mname not in propagated.setdefault(dep, []):
                    propagated[dep].append(mname)
                plan.reasons.append(f"依赖传播：{mname} 是 {dep} 的上游")
                nxt.add(dep)
        frontier = nxt

    # 级别：用户显式级别不能低于规则要求；任何未知路径直接 L3
    required = level or "fast"
    for rh in rule_hits:
        required = max_level(required, rh["minLevel"])
    if unknown:
        required = max_level(required, map_["unknownChangePolicy"]["level"])
    sel.level = required

    docs_only = bool(changed) and all(_is_doc(p) for p in changed) and not matched_modules
    sel.docs_only = docs_only

    if unknown:
        sel.unknown_files = sorted(set(unknown))
        sel.warnings.append(
            "存在无法映射的路径，按保守策略升级到 L3： " + ", ".join(sel.unknown_files))
        full = _select_full(map_, required, mode="changed", reason="未知路径保守升级")
        full.rules_hit = rule_hits
        full.unknown_files = sel.unknown_files
        full.warnings = sel.warnings
        full.docs_only = False
        return full

    if docs_only:
        sel.modules = []
        sel.docs_only = True
        sel.static_checks = ["validate-map"]
        sel.not_run.append({
            "item": "Android 构建/安装/真机",
            "reason": "仅文档变化；不改变 APK，按映射规则 G 不安装 APK。",
        })
        return sel

    sel.modules = sorted(matched_modules.values(), key=lambda p: p.name)
    direct = set(matched_modules.keys()) - set(propagated.keys())
    sel.rules_hit = rule_hits
    if not sel.rules_hit:
        sel.not_selected = []
    _apply_level(map_, sel, direct_modules=direct, propagated=set(propagated.keys()))
    return sel


def device_run_mode(entry: dict, level: str) -> str | None:
    if not entry.get("smokeEnvVar"):
        return None
    return "smoke" if level in entry.get("smokeLevels", ["feature"]) else "full"


def apply_case_filter(sel: Selection, case: str) -> Selection:
    """Keep exactly one registered device scenario; defer all sibling suites."""
    normalized = case.strip().upper()
    matches = [d for d in sel.device_scripts if normalized in d.get("scenarios", [])]
    if len(matches) != 1:
        available = sorted({c for d in sel.device_scripts for c in d.get("scenarios", [])})
        raise SystemExit(
            f"case {normalized!r} 未唯一匹配当前选择；可用case: {', '.join(available) or '无'}"
        )
    target = matches[0]
    min_level = target.get("scenarioMinLevel")
    if min_level and level_index(sel.level) < level_index(min_level):
        raise SystemExit(f"case {normalized} 要求至少 {min_level}，当前级别为 {sel.level}")
    deferred = [d for d in sel.device_scripts if d is not target]
    sel.case_filter = normalized
    sel.deferred_device_scripts.extend(deferred)
    for d in deferred:
        sel.not_run.append({
            "item": f"{d['package']}:{d['script']}",
            "reason": f"--case {normalized}：只重跑单个scenario；其他用例保留为NOT RUN",
        })
    sel.device_scripts = [target]
    sel.device_required = True
    return sel


def apply_execution_profile(sel: Selection, profile: str) -> Selection:
    """Apply an explicit local-only profile without weakening standard selection.

    The dev profile preserves the required level/JVM set, records every planned
    device script as deferred, and never claims full-level acceptance.
    """
    if profile not in ("standard", "dev"):
        raise SystemExit(f"未知执行 profile：{profile}（可选：standard, dev）")
    sel.profile = profile
    if profile == "dev":
        sel.deferred_device_scripts = list(sel.device_scripts)
        for d in sel.deferred_device_scripts:
            sel.not_run.append({
                "item": f"{d['package']}:{d['script']}",
                "reason": "--profile dev：只做本地静态/JVM/编译，延期到 standard/feature/integration 验收",
            })
        sel.device_scripts = []
        sel.device_required = False
    return sel


def _select_full(map_: dict, level_name: str, mode: str, reason: str) -> Selection:
    mods = map_["modules"]
    sel = Selection(level=level_name, mode=mode)
    for mname, m in mods.items():
        sel.modules.append(ModulePlan(mname, m["title"], m["risk"], reasons=[reason]))
    _apply_level(map_, sel, direct_modules=set(mods.keys()))
    # --all 是「强制全量回归」：JVM 不加 --tests 过滤，确保未映射到注册表的
    # 临时/新增测试类也会执行（否则选择性过滤会漏跑它们）。
    sel.full_jvm = True
    sel.rules_hit.append({"id": "force-full", "reason": reason, "files": []})
    return sel


def _apply_level(map_: dict, sel: Selection, direct_modules: set[str],
                 propagated: set[str] | None = None) -> None:
    propagated = propagated or set()
    mods = map_["modules"]
    # 没有 JVM 测试的模块无法在 L0 验证：自动提升到该模块 defaultLevel，
    # 避免「零测试执行却报告 PASS」。
    if sel.mode != "all" and not map_["levels"][sel.level].get("allModules"):
        for plan in sel.modules:
            m = mods[plan.name]
            if m.get("jvmTests") or plan.name == "test-infra":
                continue
            need = m.get("defaultLevel", "feature")
            if level_index(sel.level) < level_index(need):
                sel.warnings.append(
                    f"模块 {plan.name} 没有 JVM 测试，自动从 {sel.level} 提升到 {need} 以执行真机验证")
                sel.level = need
    lv = map_["levels"][sel.level]
    all_modules = lv.get("allModules", False)
    full_jvm = lv.get("fullJvm", False) or any(
        next((r for r in map_["impactRules"] if r["id"] == rh["id"]), {}).get("fullJvm")
        for rh in sel.rules_hit)

    selected_names = {m.name for m in sel.modules}
    if all_modules:
        selected_names = set(mods.keys())
        if sel.mode != "all":
            sel.modules = [
                ModulePlan(n, mods[n]["title"], mods[n]["risk"],
                           reasons=[f"{sel.level} 级别要求全模块回归"])
                for n in sorted(mods)
            ]

    # Exclude a global integration prerequisite only when every explicitly
    # selected module opts out; one focused module cannot suppress coverage
    # required by another module in a combined invocation.
    skip_sets = [set(mods.get(direct, {}).get("skipAlwaysModules", []))
                 for direct in direct_modules if direct in mods]
    focused_exclusions = set.intersection(*skip_sets) if skip_sets else set()
    for mname in lv.get("alwaysModules", []) or []:
        if mname in focused_exclusions:
            continue
        if mname not in selected_names:
            selected_names.add(mname)
            if mname not in {m.name for m in sel.modules}:
                sel.modules.append(ModulePlan(mname, mods[mname]["title"], mods[mname]["risk"],
                                              reasons=[f"{sel.level} 级别强制包含高风险模块"]))
    sel.modules.sort(key=lambda p: p.name)

    # 规则里显式指定的「真机模块」
    device_allowed = set()
    if sel.mode == "module":
        device_allowed.update(direct_modules)
    for rh in sel.rules_hit:
        rule = next((r for r in map_["impactRules"] if r["id"] == rh["id"]), None)
        if not rule:
            continue
        if "deviceModules" in rule:
            device_allowed.update(rule["deviceModules"])
        else:
            device_allowed.update(rule.get("modules", []))
        if rule.get("deviceFromMatched"):
            device_allowed.update(direct_modules)
    if not sel.rules_hit:
        device_allowed.update(direct_modules)
    if any(r["id"] == "explicit-module" for r in sel.rules_hit):
        device_allowed.update(direct_modules)
    for mname in lv.get("alwaysModules", []) or []:
        device_allowed.add(mname)

    # 级别的真机层级
    tiers = set(lv.get("deviceTiers", []))
    run_device = bool(tiers)

    device_scripts: dict[tuple[str, str], dict] = {}
    for mname in selected_names:
        m = mods[mname]
        plan = next((p for p in sel.modules if p.name == mname), None)
        if plan is None:
            continue
        plan.jvm = list(m.get("jvmTests", []))
        for d in m.get("device", []):
            if d["tier"] not in tiers:
                continue
            # 传播模块只在 L2+ 跑真机；直接/规则模块按级别跑
            if mname in propagated and level_index(sel.level) < level_index("integration"):
                continue
            if not all_modules and mname not in device_allowed:
                continue
            key = (d["script"], d["package"])
            if key in device_scripts:
                if mname not in device_scripts[key]["modules"]:
                    device_scripts[key]["modules"].append(mname)
            else:
                device_scripts[key] = dict(d, modules=[mname], tier=d["tier"])
        plan.device = [dict(d, modules=[mname]) for d in m.get("device", [])
                       if d["tier"] in tiers
                       and not (mname in propagated and level_index(sel.level) < level_index("integration"))
                       and (all_modules or mname in device_allowed)]

    # L1+ 必要的导航冒烟：只要真的要跑真机，且导航未选择
    focused_modules = [mods[name] for name in direct_modules if name in mods]
    # A global navigation sweep is redundant only when every direct module
    # selected for this run declares its own UI/device coverage. Shared-nav
    # rules include the navigation module (which does not opt out), so they
    # continue to run the cross-page sweep.
    skip_navigation = bool(focused_modules) and all(
        m.get("skipNavigationSmoke", False) for m in focused_modules
    )
    if run_device and level_index(sel.level) >= level_index("feature") and not skip_navigation:
        if "navigation" not in selected_names:
            selected_names.add("navigation")
            sel.modules.append(ModulePlan(
                "navigation", mods["navigation"]["title"], mods["navigation"]["risk"],
                reasons=["L1+ 必要导航冒烟（跨页面回归）"]))
            sel.modules.sort(key=lambda p: p.name)
        if "verify_navigation.py" not in {d["script"] for d in device_scripts.values()}:
            device_scripts[("verify_navigation.py", "qa")] = {
                "script": "verify_navigation.py", "package": "qa", "tier": "feature",
                "assertions": 28, "destructive": False, "resetsQaData": False,
                "measuredSec": 124, "estimatedSec": 124, "modules": ["navigation"],
                "reason": "L1+ 必要导航冒烟",
            }

    # Gate 级别必须含正式包只读冒烟
    if lv.get("requiresProdSmoke"):
        prod_entry = next(
            (d for m in mods.values() for d in m.get("device", []) if d["package"] == "prod"),
            None)
        if prod_entry:
            device_scripts[("verify_prod_smoke.py", "prod")] = dict(
                prod_entry, modules=["app-smoke"], reason="Gate 正式包只读冒烟")

    sel.device_scripts = sorted(device_scripts.values(), key=lambda d: (d["package"], d["script"]))
    sel.device_required = bool(sel.device_scripts)
    sel.prod_smoke_required = any(d["package"] == "prod" for d in sel.device_scripts)

    # JVM
    if full_jvm or all_modules:
        classes = set()
        for m in mods.values():
            classes.update(m.get("jvmTests", []))
        sel.jvm_classes = sorted(classes)
        sel.full_jvm = True
    else:
        classes = set()
        for mname in selected_names:
            classes.update(mods[mname].get("jvmTests", []))
        sel.jvm_classes = sorted(classes)
        sel.full_jvm = False

    # 静态检查
    checks = {"validate-map"}
    if "database-migration" in selected_names:
        checks.add("schema-contract")
    if "test-infra" in selected_names:
        checks.add("script-syntax")
    sel.static_checks = sorted(checks)

    # 未选择的模块说明
    for mname, m in mods.items():
        if mname in selected_names:
            continue
        sel.not_selected.append({
            "module": mname,
            "title": m["title"],
            "reason": "未命中变更路径，也不是被选模块的上游依赖",
        })

    # 级别导致的未运行项
    if not run_device:
        sel.not_run.append({"item": "真机测试", "reason": f"{sel.level} 级别不启动设备"})
    for m in sel.modules:
        for d in mods[m.name].get("device", []):
            if d["tier"] not in tiers:
                sel.not_run.append({
                    "item": f"{m.name}:{d['script']}",
                    "reason": f"该用例层级 {d['tier']} 高于当前 {sel.level} 允许的 {sorted(tiers)}",
                })
