# Gate 2C 验收结果汇总（最终）

> 生成时间：2026-11-03
> 交付 APK（真机安装并核对哈希）：
> `mengya-life-0.5.0-g2c-debug.apk`
> SHA-256 `53de2e8fd0977475110c9eee9e056940c55d643c3cc08d88dcb027885b6297fa`

## 1. 结论

- **JVM 单元测试：88/88 通过**（9 个测试类）。
- 真机脚本：**345 条断言全部通过**（分脚本）。
- 最后一次连续 `run_all.sh`：8 个脚本连续通过（312 条）；
  `verify_settings` 首轮因冷启动 UI 未就绪失败（8/32），
  修复等待逻辑后定向复跑 **33/33**。
- 唯一缺口：没有“settings 也包含在内的单次连续全绿日志”，
  详见 `MISSING-EVIDENCE.md`。

## 2. 分脚本结果

| 脚本 | 通过 | 来源 | 日志 |
|---|---|---|---|
| verify_settings | 33/33 | 首轮时序失败后定向复跑 | `g2c-verify_settings.log`（失败原始：`...-first-attempt.log`） |
| verify_diary | 25/25 | 最后一次连续运行 | `g2c-verify_diary.log` |
| verify_theme | 11/11 | 最后一次连续运行 | `g2c-verify_theme.log` |
| verify_navigation | 28/28 | 最后一次连续运行 | `g2c-verify_navigation.log` |
| verify_goals | 64/64 | 最后一次连续运行 | `g2c-verify_goals.log` |
| verify_trends | 73/73 | 最后一次连续运行 | `g2c-verify_trends.log` |
| verify_dates | 17/17 | 最后一次连续运行 | `g2c-verify_dates.log` |
| verify_export | 48/48 | 最后一次连续运行 | `g2c-verify_export.log` |
| verify_prod_smoke | 46/46 | 最后一次连续运行 | `g2c-verify_prod_smoke.log` |
| **合计** | **345** | 分脚本 | — |

## 3. 截图

- `g2c-shots/`：48 张，覆盖设置、日记、主题、导航、目标三层、进展历史、
  真实趋势（空/单点/多点/多单位）、非法数值、演示区、导出提醒/SAF/成功。

## 4. 数据证据

- 正式包升级前后 `journal_entries` 逐字段一致（报告中 §7.3）。
- QA 与正式包 applicationId / 数据库 / DataStore 完全隔离。
- 导出样本：
  - QA：`g2c-export/mengya-life-export-20261103-160723.json`（1,420 B）；
  - 正式包：`g2c-export/mengya-life-export-20261103-161002.json`（847 B）。
- 设备安装 APK 哈希：`g2c-apk-hash.log`，与 `artifacts/*.sha256` 一致。

## 5. 复现

```sh
export DEV=<手机IP>:<无线调试端口>
./tools/ui-verify/run_all.sh
```

脚本依赖无线调试；设备 IP/端口变化会导致 adb 断开，重连后再跑。
`restart_app()` 现在会等应用真正渲染，已修复本轮出现的冷启动时序问题。
