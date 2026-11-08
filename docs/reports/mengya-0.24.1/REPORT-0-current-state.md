# Gate 0 现状确认（基线 6acc390 / 0.24 / DB21）

| ID | 结论 | 最小复现 | 证据 | 影响 | 计划 |
|---|---|---|---|---|---|
| R01 | 确认（已部分修复） | 连续运行 Worker/退后台 | CareRunner.kt:61 返回旧 SEND；CareWorker.kt:35 无条件 post；CareViewModel notifyIfNeeded | 重复通知 | 0.24.1 增加 isNew/decisionId + notified_at 原子 claim；旧卡片不再通知 |
| R02 | 确认（已修复） | “以后别提醒简历” | CarePreferenceParser 旧 extractTopicWord 缺“别提醒”，label fallback “这个话题” | 静音键错误 | 重写 marker，匹配 plan:4；新增测试 |
| R03 | 确认（已修复） | 先静音再跑旧 PENDING | CareRunner 旧 pending 未过滤偏好（旧 :124） | 旧机会绕过静音 | pending 按统一 suppressed 过滤；SEND 前重读偏好 |
| R04 | 确认（已修复） | 关闭 PROGRESS 跑 Care | 旧 CareRunner:93/138/145 无条件读计数 | 越权读取进展 | PROGRESS 关闭时 prompt 为 UNKNOWN_DUE_TO_CONSENT |
| R05 | 确认（已修复，缺测试） | 授权后换 endpoint 跑 Worker | 旧仅检查 consent.enabled(GOALS)，未绑定 recipient/epoch/generation | 旧授权用于新 endpoint | BoundCareContext：请求前/返回后/写入前校验 epoch、recipient、endpoint、generation |
| R06 | 确认（未修复） | 备份→改数据→恢复 | BackupFormatV2 无 recordProposals；RestoreTransaction.deleteAll 无该表 | 旧候选残留/丢失 | 需格式升级；本轮未完成 |

时序：A 今天页→CareRunner→消息→卡片；B Worker→CareRunner→DB→通知；C 聊天→Parser→care_preferences；D Receiver→feedback/status；E 请求中撤权/换 endpoint/恢复由 BoundCareContext 在返回后丢弃。

结论：R01-R05 已定位并部分修复；R06 未修复，不能宣布完成。
