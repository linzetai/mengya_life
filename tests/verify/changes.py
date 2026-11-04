"""Git 变更发现：已提交范围 + 暂存 + 未暂存 + 未跟踪。

不依赖「最近一次 commit」单个点；默认基线按以下顺序解析：
  1. --base / VERIFY_BASE / git config mengya.verifyBase
  2. .verify/state.json 里上一次 verify 的 commit（且必须是 HEAD 祖先）
  3. origin/main 或 main 的 merge-base
  4. HEAD~1（初始提交则用空树）
"""
from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass, field
from pathlib import Path

from .util import ROOT, git, sha256_text

EMPTY_TREE = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"


@dataclass
class Change:
    path: str
    status: str = "M"  # A/M/D/R/C
    source: str = ""

    def as_dict(self) -> dict:
        return {"path": self.path, "status": self.status, "source": self.source}


@dataclass
class ChangeSet:
    base: str
    base_reason: str
    changes: list[Change] = field(default_factory=list)
    commit: str = ""
    branch: str = ""
    dirty: bool = False

    @property
    def paths(self) -> list[str]:
        return sorted({c.path for c in self.changes})

    def as_dict(self) -> dict:
        return {
            "base": self.base,
            "baseReason": self.base_reason,
            "commit": self.commit,
            "branch": self.branch,
            "dirty": self.dirty,
            "files": [c.as_dict() for c in self.changes],
        }


def _parse_name_status(out: str, source: str) -> list[Change]:
    changes: list[Change] = []
    for line in out.splitlines():
        line = line.rstrip("\n")
        if not line.strip():
            continue
        parts = line.split("\t")
        status = parts[0].strip()
        if status.startswith("R") or status.startswith("C"):
            if len(parts) >= 3:
                changes.append(Change(parts[1], status[0], source))
                changes.append(Change(parts[2], "A" if status[0] == "C" else "R", source))
            continue
        if len(parts) >= 2:
            changes.append(Change(parts[1], status[0], source))
    return changes


def _resolve_base(explicit: str | None, state_commit: str | None, state_reason: str) -> tuple[str, str]:
    if explicit:
        return explicit, "显式指定 --base"
    env = os.environ.get("VERIFY_BASE")
    if env:
        return env, "环境变量 VERIFY_BASE"
    code, out = git("config", "--get", "mengya.verifyBase")
    if code == 0 and out.strip():
        return out.strip(), "git config mengya.verifyBase"
    if state_commit:
        code, _ = git("merge-base", "--is-ancestor", state_commit, "HEAD")
        if code == 0:
            return state_commit, state_reason
    for ref in ("origin/main", "origin/master", "main", "master"):
        code, out = git("rev-parse", "--verify", f"{ref}^{{commit}}")
        if code != 0:
            continue
        code, mb = git("merge-base", ref, "HEAD")
        if code == 0 and mb.strip():
            return mb.strip(), f"{ref} 的 merge-base"
    code, out = git("rev-parse", "--verify", "HEAD~1^{commit}")
    if code == 0 and out.strip():
        return out.strip(), "HEAD~1（没有远端/状态基线时的保守回退）"
    code, out = git("rev-parse", "--verify", "HEAD^{commit}")
    if code == 0:
        return EMPTY_TREE, "初始提交（空树基线）"
    return EMPTY_TREE, "无 Git 历史"


def last_state() -> dict:
    p = ROOT / ".verify" / "state.json"
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except Exception:
        return {}


def collect_changes(base: str | None = None, simulate: list[str] | None = None) -> ChangeSet:
    code, commit = git("rev-parse", "HEAD")
    commit = commit.strip() if code == 0 else ""
    code, branch = git("rev-parse", "--abbrev-ref", "HEAD")
    branch = branch.strip() if code == 0 else ""

    if simulate:
        cs = ChangeSet(base="(simulated)", base_reason="--simulate-changed 测试样本", commit=commit, branch=branch)
        cs.changes = [Change(p, "M", "simulated") for p in simulate]
        code, out = git("status", "--porcelain")
        cs.dirty = bool(out.strip())
        return cs

    st = last_state()
    base_rev, reason = _resolve_base(base, st.get("lastCommit"), "上一次 verify 运行记录")
    if not commit:
        return ChangeSet(base=base_rev, base_reason=reason, commit="", branch=branch)

    merged: dict[str, Change] = {}

    def add(items: list[Change]) -> None:
        for c in items:
            prev = merged.get(c.path)
            if prev is None:
                merged[c.path] = c
            elif prev.status in ("D",) and c.status != "D":
                # 同一路径在多处出现时保留「仍存在」的最新语义
                merged[c.path] = c

    # 1) 已提交：base...HEAD（三点 = 从 merge-base 起算）
    code, out = git("diff", "--name-status", "-M", f"{base_rev}...HEAD")
    if code == 0:
        add(_parse_name_status(out, "committed"))

    # 2) 已暂存
    code, out = git("diff", "--cached", "--name-status", "-M")
    if code == 0:
        add(_parse_name_status(out, "staged"))

    # 3) 未暂存
    code, out = git("diff", "--name-status", "-M")
    if code == 0:
        add(_parse_name_status(out, "unstaged"))

    # 4) 未跟踪
    code, out = git("ls-files", "--others", "--exclude-standard")
    if code == 0:
        add([Change(line.strip(), "A", "untracked") for line in out.splitlines() if line.strip()])

    code, status_out = git("status", "--porcelain")
    cs = ChangeSet(
        base=base_rev,
        base_reason=reason,
        changes=sorted(merged.values(), key=lambda c: c.path),
        commit=commit,
        branch=branch,
        dirty=bool(status_out.strip()),
    )
    return cs


# 影响 APK/编译的源码与构建文件（文档变化不会触发构建）
_APK_ROOTS = (
    "app/src/main/",
    "app/src/test/",
    "app/build.gradle",
    "app/build.gradle.kts",
    "build.gradle",
    "build.gradle.kts",
    "settings.gradle",
    "settings.gradle.kts",
    "gradle.properties",
    "gradle/",
)


def apk_source_digest(changes: list[Change]) -> str:
    """对会改变 APK/JVM 测试产物的文件内容做摘要；删除文件也算变化。"""
    import hashlib
    h = hashlib.sha256()
    for c in sorted(changes, key=lambda x: x.path):
        if not any(c.path == r or c.path.startswith(r) for r in _APK_ROOTS):
            continue
        h.update(c.path.encode())
        p = ROOT / c.path
        if c.status != "D" and p.exists():
            h.update(p.read_bytes())
        else:
            h.update(b"<deleted>")
    return h.hexdigest()
