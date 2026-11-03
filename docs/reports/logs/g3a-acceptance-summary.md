# Gate 3A 验收结果汇总

> 版本 0.6.0-g3a（versionCode 6）
> 正式 APK：`artifacts/mengya-life-0.6.0-g3a-debug.apk`
> 11,256,370 B / SHA-256 `cc4ac2af2f0efe4aa37a14557e1f6fa566d5fb138f42721dd27b49fdf63470a4`
> QA APK：`artifacts/mengya-life-0.6.0-g3a-qa-test.apk`
> 11,256,402 B / SHA-256 `4c8bed2a9f2fffd9c70813ee84f67aa5a8ce908f0d8cdc078342aa3f08c4fc73`

## 结论

**PASS**：功能与真机验收 396/396 通过；用户在正式包输入有效 Key，
DeepSeek `deepseek-flash` 的真实回复于 2026-11-04 08:11 成功返回并落库
（`g3a-real-model-success.log` + `advisor-13-real-success.png`）。
真实 HTTPS 401 亦已验证；QA 离线引擎回复明确标注，不冒充真实输出。

## 测试

| 项目 | 结果 | 日志 |
|---|---|---|
| JVM 单元测试 | 126/126 | `g3a-unit-test-summary.txt` |
| 顾问闭环（配置/加密/对话/失败/重试/取消/上下文/删除） | 59/59 | `g3a-verify-advisor.log` |
| 真实 HTTPS 401（无效测试 Key） | 10/10 | `g3a-verify-network.log` |
| 设置页 | 38/38 | `g3a-verify_settings.log` |
| 日记回归 | 25/25 | `g3a-verify_diary.log` |
| 目标回归（v3） | 64/64 | `g3a-verify_goals.log` |
| 趋势回归 | 73/73 | `g3a-verify_trends.log` |
| 导航回归 | 28/28 | `g3a-verify_navigation.log` |
| 数据导出回归（v3，无 Key） | 49/49 | `g3a-verify_export.log` |
| 正式包只读冒烟 | 50/50 | `g3a-verify_prod_smoke.log` |
| 真实模型成功回复（用户 Key） | PASS | `g3a-real-model-success.log` |
| **合计** | **396/396** | — |

## 数据库迁移

- v2 → v3，新增 `advisor_messages`；
- 正式包安装后 `user_version=3`；
- 旧日记两行逐字段一致；目标/进展仍为空；
- 详见 `g3a-migration.log` 与报告 §7。

## 真机 / APK

- 覆盖安装成功（调试签名固定，证书 SHA-256 `6fcae914…a0efaf`）；
- 冷启动 `Status: ok / COLD`，无崩溃；
- 设备 `base.apk` 哈希与交付文件一致：`g3a-apk-hash.log`；
- 安装/启动日志：`g3a-install.log`、`g3a-launch.log`。

## 证据

- 截图 56 张（`g3a-shots/`，含顾问 12 张关键路径 ）；
- 导出样本：`g3a-export/mengya-life-export-20261104-020724.json`（1 日记/1 目标/2 进展，无 Key）；
- 源码快照：`source/`；
- 构建日志：`g3a-build.log`。
