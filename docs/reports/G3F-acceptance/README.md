# Gate 3F 验收证据

设备：`2509FPN0BC` / Android 17 / adb `192.168.10.183:33537`。
QA APK SHA256：`055da4efd2f6042fd80400943f08924f2d8e09a1fe341aa6c3eba56bf39c2e70`（12,393,063 B，`0.14.1-g3e2-1-qa`）。
正式 APK SHA256：`7cafa0e1506594cfb6c696d97903c93f352adb3b21b87232ac515158d5bb4b95`（`0.14.1-g3e2-1`）。
Room：v11；QA / 正式包均 `install -r` 覆盖安装，未卸载、未清正式库。

| 目录/文件 | 内容 | 结果 |
|---|---|---|
| `db-l2/` | `--module database-migration --level integration` | 见报告 |
| `diary-l1/` | 模拟只改 DiaryScreen 的 L1 运行 | PASS，337.6s |
| `weekly-l1/` | 模拟只改 WeeklyAiParser 的 L1 运行（周回顾冒烟） | PASS，644.2s |
| `l0-fast/` | 模拟只改 MetricFormat 的 L0 运行 | PASS，14.8s |
| `docs-only/` | 只改 README | PASS，0.2s；无构建/安装/设备 |
| `failure-probe/` | 注入失败测试的 `--all --level fast` | FAIL，rc=1（预期） |
| `advisor-feature.log` | 修复后的 `verify_advisor.py` 直接运行 | 56/56 PASS，368s |
| `weekly-full.log` | 完整周回顾状态机（95 条）原始输出 | 95/95 PASS，1160s |

说明：

- 旧的 `docs/reports/logs/*` 是历史 Gate 的日志；本目录只放 Gate 3F 的证据。
- 真机脚本截图未提交（体积大且非本轮交付重点）；需要时可在报告目录下重新生成。
- 基线全量运行曾暴露两个**脚本漂移**（不是业务回归）：
  - `verify_advisor.py` 未适配 Gate 3B1 的全局授权页，且 schema 断言写死为 3；
  - `verify_g3b1.py` 的草案入口 tag 已从 `advisor_proposal_open` 改为 `advisor_proposal_open_last`。
  两者已修复并重跑通过；修复记录见 `TEST-ARCHITECTURE-AUDIT.md` 与 `TEST-ACCEPTANCE.md`。
