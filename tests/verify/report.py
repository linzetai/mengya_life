"""计划输出与机器/人类双格式报告。"""
from __future__ import annotations

import json
from pathlib import Path

from .selection import Selection, device_run_mode
from .util import ROOT, ensure_dir, write_text


def print_plan(map_: dict, sel: Selection, cs, argv: list[str]) -> None:
    lv = map_["levels"][sel.level]
    print(f"\n== 测试选择（{lv['id']} {lv['title']} / {sel.mode} / profile={sel.profile}）==")
    if sel.profile == "dev":
        print("开发快回路：只执行静态/JVM/必要编译；延期真机用例不计 Gate PASS。")
    if cs is not None:
        print(f"Git 基线: {cs.base[:12]}（{cs.base_reason}）  HEAD: {cs.commit[:12]}  branch: {cs.branch}")
        if sel.mode == "changed":
            print(f"变更文件: {len(cs.paths)}")
            for c in cs.changes[:60]:
                print(f"  {c.status}  {c.path}  [{c.source}]")
            if len(cs.changes) > 60:
                print(f"  ... 其余 {len(cs.changes) - 60} 个")
        else:
            print(f"工作区变更 {len(cs.paths)} 个（显式模块模式不参与选择）")
    if sel.unknown_files:
        print("! 无法映射的路径（保守升级）：")
        for p in sel.unknown_files:
            print(f"    {p}")
    if sel.rules_hit:
        print("命中规则：")
        for r in sel.rules_hit:
            print(f"  [{r['id']}] {r.get('description', r.get('reason', ''))}")
            if r.get("files"):
                print(f"      文件: {', '.join(r['files'][:6])}")
    print(f"选择模块（{len(sel.modules)}）：")
    for m in sel.modules:
        devs = ", ".join(
            f"{d['script']}({d['tier']}{('/' + device_run_mode(d, sel.level)) if device_run_mode(d, sel.level) else ''})"
            for d in m.device
        ) or "无真机"
        print(f"  - {m.name:<20} risk={m.risk:<8} JVM={len(m.jvm):<2} 真机={devs}")
        for reason in m.reasons:
            print(f"      理由: {reason}")
    print(f"JVM: {'全量' if sel.full_jvm else len(sel.jvm_classes)} 个类")
    print("真机：")
    planned_device = sel.device_scripts or sel.deferred_device_scripts
    if planned_device:
        declarations = sum(len(m.device) for m in sel.modules)
        def script_estimate(d: dict) -> float:
            # Feature smoke variables (e.g. weekly AI) expand to the full matrix at L2+.
            if d.get("smokeEnvVar") and sel.level not in d.get("smokeLevels", ["feature"]):
                return float(d.get("measuredFullSec") or d.get("estimatedSec") or d.get("measuredSec") or 0)
            return float(d.get("estimatedSec") or d.get("measuredSec") or 0)
        estimated = sum(script_estimate(d) for d in planned_device)
        print(f"  唯一脚本 {len(planned_device)} 个；估算脚本耗时 {estimated:.0f}s（按当前 smoke/full 模式；不含构建/前置与设备空闲）")
        if declarations > len(planned_device):
            print(f"  去重：模块注册 {declarations} 次 → 实际按 script+package 执行 {len(planned_device)} 次")
    for d in sel.device_scripts:
        mode = device_run_mode(d, sel.level)
        suffix = f"  mode={mode}" if mode else ""
        print(f"  - [{d['package']}] {d['script']}  tier={d['tier']}{suffix}  modules={','.join(d.get('modules', []))}")
    if not sel.device_scripts:
        if sel.deferred_device_scripts:
            print("  （本次不启动设备；以下是标准 profile 延期用例）")
            for d in sel.deferred_device_scripts:
                print(f"  - {d['package']}:{d['script']} [{d['tier']}] modules={','.join(d.get('modules', []))}")
        else:
            print("  （无；本级别不启动设备）")
    if sel.not_run:
        print("不运行：")
        for n in sel.not_run:
            print(f"  - {n['item']}: {n['reason']}")
    if sel.not_selected:
        print(f"未选择的模块（{len(sel.not_selected)}）：")
        for n in sel.not_selected:
            print(f"  - {n['module']}: {n['reason']}")
    if sel.warnings:
        print("警告：")
        for w in sel.warnings:
            print(f"  ! {w}")


def write_report(run_dir: Path, data: dict) -> tuple[Path, Path]:
    ensure_dir(run_dir)
    jp = run_dir / "report.json"
    jp.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    mp = run_dir / "summary.md"
    write_text(mp, render_markdown(data))
    return jp, mp


def _status_badge(s: str) -> str:
    return {"PASS": "PASS", "FAIL": "**FAIL**", "SKIPPED": "SKIPPED", "NOT RUN": "NOT RUN"}.get(s, s)


def render_markdown(d: dict) -> str:
    s = d["summary"]
    lines = [
        f"# 测试执行报告 · {d['runId']}",
        "",
        f"- 开始：{d['startedAt']}",
        f"- 结束：{d['finishedAt']}",
        f"- 总耗时：**{d['durationSec']:.1f}s**",
        f"- 级别：**{d['level']}**（{d['levelTitle']}）",
        f"- 执行 profile：**{d.get('profile', 'standard')}**",
        f"- 模式：{d['mode']}",
        f"- 结论：**{s['verdict']}**",
        f"- 退出码：{s['exitCode']}",
        "",
        "## Git 与变更",
        "",
        f"- commit：`{d['git']['commit']}`",
        f"- branch：`{d['git']['branch']}`",
        f"- 基线：`{d['git']['base']}`（{d['git']['baseReason']}）",
        f"- 工作区 dirty：{d['git']['dirty']}",
        f"- 变更文件：{len(d['git']['files'])}",
        "",
    ]
    if d["git"]["files"]:
        lines += ["| 状态 | 文件 | 来源 |", "|---|---|---|"]
        for f in d["git"]["files"][:80]:
            lines.append(f"| {f['status']} | `{f['path']}` | {f['source']} |")
        if len(d["git"]["files"]) > 80:
            lines.append(f"| … | 其余 {len(d['git']['files']) - 80} 个 | |")
        lines.append("")
    lines += ["## 设备与 APK", ""]
    dev = d.get("device") or {}
    if dev:
        lines += [
            f"- 设备：{dev.get('model', '')} / Android {dev.get('android', '')} / `{dev.get('serial', '')}`",
        ]
    else:
        lines.append("- 设备：未使用（本级别不启动设备）")
    for kind, apk in (d.get("apk") or {}).items():
        if apk:
            lines.append(f"- {kind} APK：`{apk.get('sha256', '')}` · {apk.get('size', '')} B · "
                         f"versionName={apk.get('versionName', '')} · "
                         f"{'复用安装' if apk.get('reused') else '重新安装/构建'}")
    lines += ["", "## 选择与原因", ""]
    if d["selection"].get("rulesHit"):
        for r in d["selection"]["rulesHit"]:
            lines.append(f"- 规则 `{r.get('id')}`：{r.get('description') or r.get('reason', '')}")
    lines += ["", "| 模块 | 风险 | JVM 类 | 真机 | 原因 |", "|---|---|---|---|---|"]
    for m in d["selection"]["modules"]:
        devs = ", ".join(x["script"] for x in m.get("device", [])) or "—"
        reason = "；".join(m.get("reasons", []))[:160]
        lines.append(f"| {m['name']}（{m['title']}） | {m['risk']} | {len(m.get('jvmTests', []))} | {devs} | {reason} |")
    lines.append("")

    lines += ["## 执行结果", "", "| 步骤 | 状态 | 耗时 | 计数 | 日志 |", "|---|---|---|---|---|"]
    for st in d["steps"]:
        counts = ""
        c = st.get("counts") or {}
        if c:
            counts = ", ".join(f"{k}={v}" for k, v in c.items())
        log = st.get("log") or ""
        log_cell = f"`{Path(log).name}`" if log else ""
        lines.append(f"| {st['name']} | {_status_badge(st['status'])} | {st['durationSec']:.1f}s | {counts} | {log_cell} |")
    lines.append("")

    if d.get("deviceSteps"):
        lines += ["## 真机用例明细", "", "| 包 | 脚本 | 模式 | 状态 | 断言 | 耗时 |", "|---|---|---|---|---|---|"]
        for x in d["deviceSteps"]:
            lines.append(
                f"| {x['package']} | {x['script']} | {x.get('runMode') or '—'} | "
                f"{_status_badge(x['status'])} | {x['assertionsPassed']}/{x['assertionsTotal']} | {x['durationSec']}s |")
        lines.append("")

    lines += ["## 未运行与原因", ""]
    nr = d["selection"].get("notRun", []) + d.get("notRun", [])
    if nr:
        for n in nr:
            lines.append(f"- **{n['item']}**：{n['reason']}")
    else:
        lines.append("- 无")
    lines += ["", "## 失败与日志", ""]
    failures = d.get("failures", [])
    if failures:
        for f in failures:
            lines.append(f"- `{f.get('module', '')}` / {f.get('name', '')}：{f.get('detail', '')[:300]}")
    else:
        lines.append("- 无失败")
    lines += [
        "",
        f"- 报告目录：`{d['artifacts']['dir']}`",
        f"- JSON：`{d['artifacts']['json']}`",
        f"- 日志目录：`{d['artifacts']['logs']}`",
        "",
        "## 级别验收",
        "",
        f"- 满足当前级别：**{'是' if s['levelSatisfied'] else '否'}**",
        f"- 本 profile 检查满足：**{'是' if s.get('profileSatisfied', s['levelSatisfied']) else '否'}**",
        f"- 完整级别验收满足：**{'是' if s.get('fullLevelSatisfied', s['levelSatisfied']) else '否'}**",
    ]
    for n in s.get("levelNotes", []):
        lines.append(f"- {n}")
    if d["selection"].get("unknownFiles"):
        lines.append("- 未知路径（保守升级）：" + ", ".join(d["selection"]["unknownFiles"]))
    lines.append("")
    return "\n".join(lines)
