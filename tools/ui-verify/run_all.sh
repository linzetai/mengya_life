#!/usr/bin/env bash
# Gate 3F：旧的 run_all.sh 保留为兼容入口，统一转发到新调度器。
#
# - 旧行为：依次跑 QA 脚本 + 正式包只读冒烟（固定顺序、固定清单）；
# - 新行为：--all --level gate 使用 tests/test-map.json 的完整清单，
#   增加迁移、周回顾 AI、QA 安全前置校验，并输出 JSON/Markdown 报告。
#
# 仍然要求正式包只读、QA 包隔离；破坏性脚本的 require_qa 硬闸门不变。
set -uo pipefail
cd "$(dirname "$0")/../.."
exec ./scripts/verify.sh --all --level gate "$@"
