"""统一测试入口：选择 -> JVM -> 构建/安装 -> QA 真机 -> 报告。"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path

from . import changes, device, gradle, report, selection, validate
from .util import (
    ROOT, RUNS_DIR, STATE_DIR, STATUS_FAIL, STATUS_NOT_RUN, STATUS_PASS, STATUS_SKIP,
    ensure_dir, iso, log, sha256_file, write_text,
)

LEVEL_CHOICES = selection.LEVEL_ORDER


def parse_args(argv: list[str]) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        prog="scripts/verify.sh",
        description="萌芽人生统一测试入口（Gate 3F）：按源码影响选择测试模块。",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""示例：
  ./scripts/verify.sh --changed
  ./scripts/verify.sh --changed --dry-run --explain
  ./scripts/verify.sh --module weekly-ai
  ./scripts/verify.sh --level fast
  ./scripts/verify.sh --level feature --module diary
  ./scripts/verify.sh --level integration --changed
  ./scripts/verify.sh --level gate
  ./scripts/verify.sh --all --level release
""",
    )
    g = p.add_mutually_exclusive_group()
    g.add_argument("--changed", action="store_true", help="按 Git 变更自动选择（默认模式）")
    g.add_argument("--module", action="append", metavar="NAME", help="显式指定模块（可重复）")
    g.add_argument("--all", action="store_true", help="强制全量回归（等同 --level gate）")
    p.add_argument("--level", choices=LEVEL_CHOICES, help="执行级别：fast/feature/integration/gate/release")
    p.add_argument("--base", help="--changed 的 Git 基线（默认见文档）")
    p.add_argument("--files", action="append", metavar="PATH",
                   help="模拟变更文件（用于验收选择器；不影响真实 Git）")
    p.add_argument("--dry-run", action="store_true", help="只输出选择计划，不执行任何测试")
    p.add_argument("--explain", action="store_true", help="输出每个模块被选择/未运行的理由")
    p.add_argument("--report-dir", help="报告目录（默认 docs/reports/test-runs/<runId>）")
    p.add_argument("--no-device", action="store_true", help="跳过所有真机步骤（级别将不满足）")
    p.add_argument("--no-build", action="store_true", help="禁止构建 APK（需要时直接失败）")
    p.add_argument("--force-build", action="store_true", help="强制重新构建 APK")
    p.add_argument("--keep-qa-data", action="store_true", help="结束后不清空 QA 包数据")
    p.add_argument("--online", action="store_true", help="Gradle 允许联网（默认 --offline）")
    p.add_argument("--json", action="store_true", help="stdout 输出机器可读 JSON")
    p.add_argument("--list-modules", action="store_true", help="列出注册表模块")
    p.add_argument("--timeout-device", type=int, default=1200, help="单个真机脚本超时秒数")
    p.add_argument("--timeout-jvm", type=int, default=2400, help="Gradle 测试超时秒数")
    p.add_argument("--strict", action="store_true", help="把 SKIPPED 也视为级别不满足")
    args = p.parse_args(argv)
    if not (args.changed or args.module or args.all or args.list_modules):
        # 默认按 --changed 处理，符合「小改动默认 L0」
        args.changed = True
    return args


def default_report_dir(run_id: str) -> Path:
    return RUNS_DIR / run_id


def load_state() -> dict:
    p = STATE_DIR / "state.json"
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except Exception:
        return {}


def save_state(state: dict) -> None:
    ensure_dir(STATE_DIR)
    (STATE_DIR / "state.json").write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")


def newest_mtime(paths: list[Path]) -> float:
    newest = 0.0
    for r in paths:
        if r.is_file():
            newest = max(newest, r.stat().st_mtime)
        elif r.is_dir():
            for p in r.rglob("*"):
                if p.is_file():
                    newest = max(newest, p.stat().st_mtime)
    return newest


def apk_stale(apk: Path) -> bool:
    if not apk.exists():
        return True
    newest = newest_mtime([
        ROOT / "app" / "src", ROOT / "app" / "schemas", ROOT / "app" / "build.gradle",
        ROOT / "build.gradle", ROOT / "settings.gradle", ROOT / "gradle.properties",
    ])
    return newest > apk.stat().st_mtime + 1


def apk_meta(path: Path, version: str, built: bool, reused: bool) -> dict:
    return {
        "path": str(path),
        "sha256": sha256_file(path),
        "size": path.stat().st_size,
        "versionName": version,
        "built": built,
        "reused": reused,
    }


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    map_ = selection.load_map()

    if args.list_modules:
        for name, m in map_["modules"].items():
            devs = ", ".join(d["script"] for d in m.get("device", [])) or "—"
            print(f"{name:<20} {m['risk']:<8} {m['defaultLevel']:<11} JVM={len(m.get('jvmTests', [])):<2} 真机={devs}")
        return 0

    started = time.time()
    run_id = time.strftime("%Y%m%d-%H%M%S")
    report_dir = Path(args.report_dir) if args.report_dir else default_report_dir(run_id)
    logs_dir = ensure_dir(report_dir / "logs")
    shots_dir = ensure_dir(report_dir / "shots")
    export_dir = ensure_dir(report_dir / "export")
    state = load_state()

    # ---------- 变更与选择 ----------
    simulate = args.files
    cs = changes.collect_changes(args.base, simulate)
    for label, path in (("report", report_dir), ("logs", logs_dir)):
        if not str(path).startswith(str(ROOT)) and label == "report":
            pass
    sel = selection.select(map_, cs, args.module, args.level,
                           all_flag=args.all)
    original_device = bool(sel.device_scripts)
    sel.device_required_original = original_device
    if args.no_device:
        for d in sel.device_scripts:
            sel.not_run.append({"item": f"{d['package']}:{d['script']}", "reason": "--no-device 显式跳过"})
        sel.device_scripts = []
        sel.device_required = False
    if args.explain or args.dry_run:
        report.print_plan(map_, sel, cs, argv)

    if args.dry_run:
        out = {
            "schemaVersion": 1,
            "runId": run_id,
            "dryRun": True,
            "startedAt": iso(started),
            "git": cs.as_dict(),
            "selection": sel.as_dict(),
            "summary": {"verdict": "PLAN ONLY", "exitCode": 0,
                        "levelSatisfied": False, "levelNotes": ["dry-run：未执行测试"]},
        }
        jp, mp = report.write_report(report_dir, _maybe_missing(out))
        if args.json:
            print(json.dumps(out, ensure_ascii=False, indent=2))
        else:
            print(f"\n[计划] 未执行任何测试。计划已写入 {jp}")
        return 0

    if not (args.explain):
        report.print_plan(map_, sel, cs, argv)

    # ---------- 静态检查 ----------
    steps: list[dict] = []
    failures: list[dict] = []
    not_run_global: list[dict] = []
    static_start = time.time()
    static_issues = validate.run_static_checks(map_, sel.static_checks)
    steps.append({
        "name": "静态检查（" + ", ".join(sel.static_checks) + "）",
        "status": STATUS_PASS if not static_issues else STATUS_FAIL,
        "durationSec": round(time.time() - static_start, 2),
        "counts": {"issues": len(static_issues)},
        "detail": "; ".join(static_issues[:5]),
    })
    if static_issues:
        for i in static_issues:
            failures.append({"module": "registry", "name": "static-check", "detail": i})
        return finish(args, map_, sel, cs, started, run_id, report_dir, steps, [], [], None, None, None, failures, not_run_global)

    # ---------- 设备 / APK ----------
    serial, why = (None, "no device") if args.no_device else device.resolve_device()
    dev_info = None
    apk_report: dict[str, dict] = {}
    installed_hashes: dict[str, str] = {}
    kinds = sorted({d["package"] for d in sel.device_scripts})
    needs_qa = "qa" in kinds
    needs_prod = "prod" in kinds

    if sel.device_scripts:
        if not serial:
            not_run_global.append({
                "item": "真机测试（全部）",
                "reason": f"未找到可用设备：{why}",
            })
            sel.device_scripts = []
            sel.device_required = False
        else:
            dev_info = device.device_env(serial)

    if sel.device_scripts and serial:
        # 构建决策
        build_kinds: list[str] = []
        apk_paths = {"qa": ROOT / map_["repo"]["apkQa"], "prod": ROOT / map_["repo"]["apkProd"]}
        for kind in kinds:
            apk = apk_paths[kind]
            prev = state.get("apk", {}).get(kind, {})
            stale = args.force_build or (not apk.exists()) or apk_stale(apk)
            digest = gradle.source_digest()
            digest_changed = prev.get("sourceDigest") not in (None, digest)
            if digest_changed:
                stale = True
            if stale:
                build_kinds.append(kind)
        version = gradle.expected_version()

        if build_kinds:
            if args.no_build:
                failures.append({"module": "build", "name": "apk", "detail": f"需要构建 {build_kinds}，但指定了 --no-build"})
                return finish(args, map_, sel, cs, started, run_id, report_dir, steps, [], [], dev_info, apk_report, None, failures, not_run_global)
            b = gradle.build_apks(build_kinds, logs_dir, args.timeout_jvm, offline=not args.online)
            steps.append({
                "name": "构建 APK（" + ", ".join(build_kinds) + "）",
                "status": b["status"], "durationSec": b["durationSec"],
                "detail": "", "log": b["log"], "counts": {"tasks": len(b["tasks"])},
            })
            if b["status"] != STATUS_PASS:
                failures.append({"module": "build", "name": "gradle-assemble",
                                 "detail": "构建失败，见 " + b["log"]})
                return finish(args, map_, sel, cs, started, run_id, report_dir, steps, [], [], dev_info, apk_report, None, failures, not_run_global)
            for kind in build_kinds:
                apk = apk_paths[kind]
                if not apk.exists():
                    failures.append({"module": "build", "name": kind,
                                     "detail": f"构建后找不到 {apk}"})
            if failures:
                return finish(args, map_, sel, cs, started, run_id, report_dir, steps, [], [], dev_info, apk_report, None, failures, not_run_global)
            for kind in build_kinds:
                state.setdefault("apk", {})[kind] = {
                    "sourceDigest": gradle.source_digest(),
                    "sha256": sha256_file(apk_paths[kind]),
                    "builtAt": iso(),
                }
            save_state(state)

        # 安装决策（按已安装哈希判断，避免无谓重装）
        for kind in kinds:
            pkg = map_["packages"][kind]["applicationId"]
            apk = apk_paths[kind]
            if not apk.exists():
                failures.append({"module": "apk", "name": kind, "detail": f"APK 不存在：{apk}"})
                continue
            local_sha = sha256_file(apk)
            inst = device.installed_apk_sha(serial, pkg)
            installed_hashes[kind] = inst or ""
            reused = inst == local_sha
            apk_report[kind] = apk_meta(apk, version, built=kind in build_kinds, reused=reused)
            if reused:
                steps.append({
                    "name": f"安装 {kind} APK", "status": STATUS_SKIP,
                    "durationSec": 0.0, "counts": {},
                    "detail": "已安装包哈希与本地 APK 一致，复用安装",
                })
                continue
            ok, detail, dur = device.install_apk(serial, apk)
            steps.append({
                "name": f"安装 {kind} APK", "status": STATUS_PASS if ok else STATUS_FAIL,
                "durationSec": round(dur, 1), "detail": detail[-300:], "counts": {},
            })
            if not ok:
                failures.append({"module": "install", "name": kind, "detail": detail[-300:]})
            else:
                state.setdefault("installed", {})[kind] = {"sha256": local_sha, "at": iso()}
                save_state(state)
        if failures:
            return finish(args, map_, sel, cs, started, run_id, report_dir, steps, [], [], dev_info, apk_report, None, failures, not_run_global)

        # QA 构建标记 + 包安全校验（使用 UI 标记，证明 BuildConfig.QA_BUILD=true）
        if needs_qa:
            pre = device.run_device_script(
                serial, "verify_qa_preflight.py", map_["packages"]["qa"]["applicationId"],
                logs_dir, shots_dir, export_dir, timeout=300)
            steps.append({
                "name": "QA 安全前置校验（包名 / QA_BUILD 标记）",
                "status": pre["status"], "durationSec": pre["durationSec"],
                "counts": {"assertions": f"{pre['assertionsPassed']}/{pre['assertionsTotal']}"},
                "log": pre["log"], "detail": "; ".join(pre["failures"])[:300],
            })
            if pre["status"] != STATUS_PASS:
                failures.append({"module": "safety", "name": "qa-preflight",
                                 "detail": "QA 包/QA_BUILD 校验失败；拒绝执行破坏性测试"})
                return finish(args, map_, sel, cs, started, run_id, report_dir, steps, [], [], dev_info, apk_report, None, failures, not_run_global)
        if needs_prod:
            pkg = map_["packages"]["prod"]["applicationId"]
            info = device.package_info(serial, pkg)
            ok_ver = info is not None and info.get("versionName") == version
            ok_fix, fix_msg = device.prod_fixture_ok(serial, pkg, map_["packages"]["prod"]["database"])
            steps.append({
                "name": "正式包只读前置校验",
                "status": STATUS_PASS if (ok_ver and ok_fix) else STATUS_NOT_RUN,
                "durationSec": 0.0, "counts": {},
                "detail": f"version={info.get('versionName') if info else None} expected={version}; {fix_msg}",
            })
            if not (ok_ver and ok_fix):
                for d in sel.device_scripts:
                    if d["package"] == "prod":
                        not_run_global.append({"item": d["script"], "reason": f"正式包前置不满足：{fix_msg}"})
                sel.device_scripts = [d for d in sel.device_scripts if d["package"] != "prod"]

    # ---------- JVM ----------
    if sel.jvm_classes or sel.full_jvm:
        j = gradle.run_jvm(sel.jvm_classes, sel.full_jvm, logs_dir,
                           timeout=args.timeout_jvm, offline=not args.online)
        steps.append({
            "name": "JVM 测试" + ("（全量）" if j["full"] else f"（{len(sel.jvm_classes)} 类）"),
            "status": j["status"], "durationSec": j["durationSec"],
            "counts": {"tests": j["tests"], "failures": j["failures"]},
            "log": j["log"], "detail": "; ".join(
                f"{x['class']}.{x['test']}: {x['message']}" for x in j["failuresDetail"][:3]),
        })
        jvm_result = j
        for x in j["failuresDetail"]:
            failures.append({"module": "jvm", "name": f"{x['class']}.{x['test']}",
                             "detail": x["message"]})
    else:
        jvm_result = {"status": STATUS_SKIP, "tests": 0, "classes": {}, "failures": 0,
                      "durationSec": 0, "failuresDetail": [], "log": ""}
        steps.append({"name": "JVM 测试", "status": STATUS_SKIP, "durationSec": 0.0,
                      "counts": {}, "detail": "所选模块没有 JVM 测试"})

    # ---------- 真机 ----------
    device_steps: list[dict] = []
    for d in sel.device_scripts:
        pkg = map_["packages"][d["package"]]["applicationId"]
        entry = dict(d)
        if d.get("requiresNetwork") and serial:
            if not device.network_available(serial):
                device_steps.append({
                    "script": d["script"], "package": d["package"], "status": STATUS_SKIP,
                    "exitCode": 0, "durationSec": 0.0,
                    "assertionsPassed": 0, "assertionsTotal": d.get("assertions", 0),
                    "failures": ["设备无网络，跳过真实网络用例（不报告 PASS）"],
                    "log": "", "tail": "",
                })
                not_run_global.append({"item": d["script"], "reason": "设备无网络（requiresNetwork）"})
                continue
        extra_env = {
            "MENGYA_EXPECTED_VERSION": gradle.expected_version(),
            "MENGYA_EXPECTED_SCHEMA": str(gradle.expected_schema()),
        }
        if d.get("smokeEnvVar") and sel.level == "feature":
            extra_env[d["smokeEnvVar"]] = "1"
        status = device.run_device_script(
            serial, d["script"], pkg, logs_dir, shots_dir, export_dir,
            timeout=d.get("timeoutSec") or args.timeout_device,
            extra_env=extra_env,
        )
        status["modules"] = d.get("modules", [])
        status["tier"] = d.get("tier", "")
        device_steps.append(status)
        steps.append({
            "name": f"真机 {d['script']}",
            "status": status["status"], "durationSec": status["durationSec"],
            "counts": {"assertions": f"{status['assertionsPassed']}/{status['assertionsTotal']}"},
            "log": status["log"], "detail": "; ".join(status["failures"])[:300],
        })
        if status["status"] == STATUS_FAIL:
            for f in status["failures"][:10]:
                failures.append({"module": d["script"], "name": "assertion", "detail": f})
        elif status["status"] == STATUS_SKIP:
            not_run_global.append({"item": d["script"], "reason": "环境性跳过"})

    # ---------- 清理 QA ----------
    if serial and needs_qa and not args.keep_qa_data:
        qa = map_["packages"]["qa"]["applicationId"]
        code, _ = device.adb(serial, f"shell pm clear {qa}", timeout=60)
        steps.append({"name": "清理 QA 数据（pm clear）",
                      "status": STATUS_PASS if code == 0 else STATUS_FAIL,
                      "durationSec": 0.0, "counts": {},
                      "detail": "仅清理 QA 包；正式包未触碰"})

    # ---------- 模块状态 ----------
    module_rows = []
    for plan in sel.modules:
        m = map_["modules"][plan.name]
        jvm_classes = m.get("jvmTests", [])
        jstatus = []
        jtime = 0.0
        for cls in jvm_classes:
            r = jvm_result.get("classes", {}).get(cls)
            if r is None:
                jstatus.append(STATUS_NOT_RUN)
            else:
                jtime += r.get("time", 0)
                jstatus.append(STATUS_PASS if r["failures"] == 0 and r["errors"] == 0 else STATUS_FAIL)
        dstatus = []
        dtime = 0.0
        for ds in device_steps:
            if plan.name in ds.get("modules", []):
                dstatus.append(ds["status"])
                dtime += ds["durationSec"]
        if jstatus or dstatus:
            allst = jstatus + dstatus
            if STATUS_FAIL in allst:
                mstatus = STATUS_FAIL
            elif STATUS_NOT_RUN in allst:
                mstatus = STATUS_NOT_RUN
            elif allst and all(x == STATUS_SKIP for x in allst):
                mstatus = STATUS_SKIP
            else:
                mstatus = STATUS_PASS
        elif plan.name == "test-infra":
            mstatus = STATUS_PASS if not static_issues else STATUS_FAIL
        else:
            # L0 只承诺 JVM；没有 JVM 的模块在 fast 下设 SKIPPED（不冒充 PASS，也不拉低 L0）
            mstatus = STATUS_SKIP if sel.level == "fast" else STATUS_NOT_RUN
        module_rows.append({
            "name": plan.name, "title": plan.title, "risk": plan.risk,
            "status": mstatus, "reasons": plan.reasons,
            "jvmStatuses": jstatus, "deviceStatuses": dstatus,
            "durationSec": round(jtime + dtime, 1),
            "jvmClassCount": len(jvm_classes),
        })

    # ---------- 汇总 ----------
    return finish(args, map_, sel, cs, started, run_id, report_dir, steps, device_steps,
                  module_rows, dev_info, apk_report, jvm_result, failures, not_run_global)


def finish(args, map_, sel, cs, started, run_id, report_dir, steps, device_steps,
           module_rows, dev_info, apk_report, jvm_result, failures, not_run_global) -> int:
    finished = time.time()
    lv = map_["levels"][sel.level]
    failed_steps = [s for s in steps if s["status"] == STATUS_FAIL]
    skipped_steps = [s for s in steps if s["status"] == STATUS_SKIP]
    notrun_steps = [s for s in steps if s["status"] == STATUS_NOT_RUN]

    required_device = bool(sel.device_scripts)
    if args.no_device and sel.device_required_original and selection.level_index(sel.level) >= selection.level_index("feature"):
        required_device = True
    if not args.no_device and sel.device_required_original and not dev_info:
        required_device = True
    missing_device = bool(dev_info) is False and required_device

    level_satisfied = not failed_steps and not notrun_steps and not missing_device
    level_notes: list[str] = []
    if sel.docs_only:
        level_notes.append("仅文档变化：没有需要执行的 Android 测试，符合规则 G。")
    if failed_steps:
        level_notes.append(f"{len(failed_steps)} 个步骤 FAIL。")
    if notrun_steps:
        level_notes.append(f"{len(notrun_steps)} 个步骤 NOT RUN。")
    if missing_device:
        level_notes.append("当前级别需要真机，但没有可用设备。")
    if args.no_device:
        level_notes.append("--no-device 跳过了真机步骤，级别不视为满足。")
    # 复用安装和「模块本来就没有 JVM 测试」不是跳过测试，不应拉低级别结论
    optional_skips = [s for s in skipped_steps
                      if s["name"].startswith("安装") or s["name"].startswith("JVM 测试")]
    hard_skips = [s for s in skipped_steps if s not in optional_skips]
    if hard_skips:
        level_notes.append("存在 SKIPPED 步骤（不计为 PASS）：" + ", ".join(s["name"] for s in hard_skips))
        if args.strict or lv.get("forbidOptionalSkips"):
            level_satisfied = False
    if args.strict and hard_skips:
        level_satisfied = False
    if any(m["status"] == STATUS_NOT_RUN for m in (module_rows or [])):
        level_satisfied = False
        level_notes.append("存在被选择但没有任何测试执行的模块（NOT RUN）。")
    skipped_modules = [m["name"] for m in (module_rows or []) if m["status"] == STATUS_SKIP]
    if skipped_modules:
        level_notes.append("fast 级别未覆盖以下模块（需 L1+）：" + ", ".join(skipped_modules))

    total = round(finished - started, 1)
    if failed_steps:
        verdict = "FAIL"
        exit_code = 1
    elif not level_satisfied:
        verdict = "INCOMPLETE"
        exit_code = 2
    else:
        verdict = "PASS"
        exit_code = 0
    if sel.docs_only:
        verdict = "PASS (docs-only)"
        exit_code = 0

    data = {
        "schemaVersion": 1,
        "runId": run_id,
        "gate": "3F",
        "startedAt": iso(started),
        "finishedAt": iso(finished),
        "durationSec": total,
        "level": sel.level,
        "levelTitle": lv["id"] + " " + lv["title"],
        "mode": sel.mode,
        "git": cs.as_dict() if cs else {},
        "device": dev_info,
        "apk": apk_report,
        "selection": sel.as_dict(),
        "steps": steps,
        "deviceSteps": device_steps,
        "modules": module_rows,
        "failures": failures,
        "notRun": not_run_global,
        "summary": {
            "verdict": verdict,
            "exitCode": exit_code,
            "levelSatisfied": level_satisfied,
            "levelNotes": level_notes,
            "pass": sum(1 for s in steps if s["status"] == STATUS_PASS),
            "fail": len(failed_steps),
            "skipped": len(skipped_steps),
            "notRun": len(notrun_steps) + len(not_run_global),
        },
        "artifacts": {
            "dir": str(report_dir),
            "json": str(report_dir / "report.json"),
            "md": str(report_dir / "summary.md"),
            "logs": str(report_dir / "logs"),
        },
    }
    jp, mp = report.write_report(report_dir, data)
    if cs and cs.commit:
        state = load_state()
        state["lastCommit"] = cs.commit
        state["lastRun"] = iso(finished)
        state["lastLevel"] = sel.level
        save_state(state)

    print("\n== 结果 ==")
    for st in steps:
        print(f"  {st['status']:<8} {st['name']:<45} {st['durationSec']:>7.1f}s")
    print(f"\n总耗时 {total:.1f}s | 级别 {sel.level} | 结论 {verdict} | 退出码 {exit_code}")
    if failures:
        print("失败：")
        for f in failures[:12]:
            print(f"  - {f.get('module')} / {f.get('name')}: {f.get('detail')[:200]}")
    print(f"报告：{jp}")
    print(f"摘要：{mp}")
    if args.json:
        print(json.dumps(data, ensure_ascii=False, indent=2))
    return exit_code


def _maybe_missing(out: dict) -> dict:
    """dry-run 报告补齐渲染所需字段。"""
    out.setdefault("durationSec", 0.0)
    out.setdefault("finishedAt", out["startedAt"])
    out.setdefault("level", out["selection"]["level"])
    out.setdefault("levelTitle", out["selection"]["level"])
    out.setdefault("mode", out["selection"].get("mode", "changed"))
    out.setdefault("device", None)
    out.setdefault("apk", {})
    out.setdefault("steps", [])
    out.setdefault("deviceSteps", [])
    out.setdefault("modules", [])
    out.setdefault("failures", [])
    out.setdefault("notRun", [])
    out.setdefault("artifacts", {"dir": "", "json": "", "md": "", "logs": ""})
    out.setdefault("git", out.get("git", {"commit": "", "branch": "", "base": "", "baseReason": "", "dirty": False, "files": []}))
    return out


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
