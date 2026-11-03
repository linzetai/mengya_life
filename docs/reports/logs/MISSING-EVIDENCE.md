# Gate 2C 证据缺口说明（最终）

> 本文件随 `G2C-evidence.zip` 交付，说明证据的**边界**，避免把定向复跑误读为
> “9 个脚本一次连续全绿”。

## 1. 最终结果

- JVM 单元测试：**88/88**。
- 真机脚本：**345 条断言全部通过**（分脚本）。
- 最后一次 `run_all.sh`（15:47–16:12）中：
  - **8 个脚本连续通过，共 312 条**：
    diary 25、theme 11、navigation 28、goals 64、trends 73、dates 17、export 48、prod 46；
  - `verify_settings` 首轮 **8/32** 失败；
  - 修复脚本的冷启动等待后，**定向复跑 33/33**。

## 2. 唯一缺口：settings 没有包含在同一次连续运行里

`verify_settings` 首轮失败的原因：

- `pm clear` 后冷启动，脚本在 Compose 首页尚未渲染时点击了设置按钮；
- `dumpsys` 显示应用已 resumed，但 `uiautomator dump` 拿到的仍是 DSH/系统界面；
- 后续 24 条断言全部落空，并非产品功能失败。

修复：

- `harness.restart_app()` 新增 `wait_app_ui()`：等被测应用真正渲染出 ≥3 个可用节点；
- `verify_settings` 额外等待首页的 `btn_settings` 出现；
- 仅重跑该脚本，33/33 通过。

因此：

- 所有脚本都已有**通过的原始日志**；
- 但没有“settings 也包含在内的一次连续 run_all 全绿日志”。

保留的失败原始日志：`logs/g2c-verify_settings-first-attempt.log`（8/32）。
通过日志：`logs/g2c-verify_settings.log`（33/33）。

## 3. 证据清单

| 证据 | 文件 | 结果 |
|---|---|---|
| JVM 单元测试 | `logs/g2c-unit-test.log` + `logs/g2c-unit-test-summary.txt` | 88/88 |
| settings 首轮失败（保留） | `logs/g2c-verify_settings-first-attempt.log` | 8/32（时序） |
| settings 定向复跑 | `logs/g2c-verify_settings.log` | 33/33 |
| diary | `logs/g2c-verify_diary.log` | 25/25 |
| theme | `logs/g2c-verify_theme.log` | 11/11 |
| navigation | `logs/g2c-verify_navigation.log` | 28/28 |
| goals | `logs/g2c-verify_goals.log` | 64/64 |
| trends | `logs/g2c-verify_trends.log` | 73/73 |
| dates | `logs/g2c-verify_dates.log` | 17/17 |
| export | `logs/g2c-verify_export.log` | 48/48 |
| prod smoke | `logs/g2c-verify_prod_smoke.log` | 46/46 |
| 最后一次连续运行汇总 | `logs/g2c-final-regression.log` | 8/9 脚本通过（含 settings 首轮失败） |
| 截图 | `shots/` | 48 张 |
| 导出样本 | `export/` | QA 1,420 B + 正式包 847 B |
| 设备 APK 哈希 | `logs/g2c-apk-hash.log` | 与交付 APK 一致 |

## 4. 不能据此得出的结论

- 不能宣称“345 条断言在**一次**连续运行中全部通过”；
- 可以宣称“**所有脚本的断言均通过**，其中 settings 为定向复跑”；
- 若评审强制要求单次连续全绿，恢复无线调试后重跑一次：

```sh
export DEV=<手机IP>:<无线调试端口>
./tools/ui-verify/run_all.sh
```

## 5. 设备与网络

本轮经历了多次 WiFi 切换（10.13.x → 192.168.34.x → 10.13.x），
无线调试端口随之变化；最终 `run_all.sh` 在 `198.19.0.1:38327` 上完整执行到结束。
