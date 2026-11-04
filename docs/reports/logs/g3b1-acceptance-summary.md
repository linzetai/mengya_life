# Gate 3B-1 验收汇总

结论：**PASS**（核心功能/真机/真实模型）；未覆盖项见报告 §4。

APK
- 正式：11,416,412 B · `d5eae3fada72ca532b58c218b2cddb991b9547ebb3cd6e745121edea057e638b`
- QA：11,416,433 B · `2400e83b86870758e9176f625e6d41e23232dae5e8763808e2c9347169172e80`

| 验证 | 结果 | 日志 |
|---|---|---|
| JVM | 145/145 | `g3b1-unit-tests.md` |
| Gate 3B-1 真机 | 47/47 | `g3b1-device.log` |
| 日记回归 | 25/25 | `g3b1-verify_diary.log` |
| 目标回归 | 64/64 | `g3b1-verify_goals.log` |
| 趋势回归 | 73/73 | `g3b1-verify_trends.log` |
| 导出回归 | 49/49 | `g3b1-verify_export.log` |
| 正式包冒烟 | 50/50 | `g3b1-verify_prod_smoke.log` |
| **真机合计** | **308/308** | — |
| v3→v4 迁移 | PASS | `g3b1-migration.log` |
| 真实模型 | 3 场景 | `g3b1-real-model.log` |

未覆盖：设置/导航 v4 全量回归；真实模型压力/限流/长对话；草案写入正式目标；聊天导出恢复。
