# Gate 3E-1 报告 · 每周成长回顾（本地主动能力 V1）

| 项 | 值 |
|---|---|
| 版本 | **0.13.0-g3e1**（versionCode 13） |
| 数据库 | Room **v9**（v8→v9 迁移） |
| 设备 | Xiaomi 2509FPN0BC / Android 17 |
| 结论 | **PASS（核心）**；未验证项见 §7 |
| 时间 | 2026-11-04（Asia/Shanghai） |

---

## 1. G3D-2 遗留修复

| 项 | 标记 | 证据 |
|---|---|---|
| 8+ 数值点趋势来源包含首尾 | **PASS** | `InsightRules` 首点 + takeLast(6)；8 点单测首/尾/≤7 |
| 趋势 fact 描述实际区间 | **PASS** | 单测/真实 G3D2 报告 |
| prompt 示例含 factIds；解析要求 | **PASS** | 真实模型成功输出 factIds |
| insight.sources 与 factIds 所指事实 sourceRefs 关联 | **PASS** | 解析器拒绝合法但无关来源；单测 |
| insufficient 无来源规则 | **PASS** | 只引用 insufficient 时允许空来源；其他非空 |
| 首页期间数据/授权变化实时失效 | **NOT VERIFIED** | 已监听 goals/diary/memory/consent/config 并 refresh；未做首页停留期间真机改数 |
| 详情失效关闭 | **NOT VERIFIED** | 代码在 hasReport=false 时关闭；未真机详情打开中改权限 |
| 异常后退出 loading | **NOT VERIFIED** | refresh/generate 捕获异常并 loading=false；无注入 UI 测试 |
| 保留请求前隐私 gate/取消/结果隔离/缓存 | **PASS** | 既有 230 条测试通过；未回退 |

说明：以上 NOT VERIFIED 项不是“代码未改”，而是没有真实执行证据，按用户要求不标 PASS。

---

## 2. 每周成长回顾

| 项 | 标记 | 证据 |
|---|---|---|
| 设置开关默认关闭、说明“本地整理/可能通知/后台不调用 AI” | **PASS** | g3e1-settings-weekly-off/on.png |
| 开启/关闭唯一后台周期任务 | **PASS** | WorkManager 单 JOB；关闭后无活动 JOB |
| 反复开关/重启去重 | **PASS** | ExistingPeriodicWorkPolicy.UPDATE；重启后仍 1 个 JOB |
| 不承诺精确触发时间 | **PASS** | 设计文档；7d + flex 1d |
| 本周/上周边界、跨月/跨年 | **PASS（JVM）** | WeekWindow + 2026-W45/2026-W53 |
| 无记录/单点/多单位/到期/完成取消 | **PASS（JVM）** | WeeklyReviewRulesTest |
| 成长记忆使用实际内容、纠正优先 | **PASS（JVM）** | rules test |
| 关闭类别过滤 | **PASS（JVM）** | 见设计说明 |
| AI 总授权关闭仍本地回顾 | **PASS（JVM）** | WeeklyReviewRulesTest |
| 回顾页面/生成/来源目标跳转 | **PASS** | g3e1-weekly-generated/to-goal.png |
| 回顾 → 和顾问聊聊 | **部分** | 进入顾问页 PASS；预填文本 NOT VERIFIED（QA 未配置模型） |
| 后台不请求外部 AI | **PASS（代码）** | Worker 只注入本地 service；无网络 trace 真机验证 |
| 通知权限允许/发送/渠道 | **PASS** | dumpsys notification；id=3001 |
| 同周最多一次通知 | **PASS** | 同周两次 Worker：weekly_reviews=1，notified_at 不新增 |
| 通知点击定位 | **PASS（Intent 路由）** | extra 启动进入 weekly_review_screen；真实点击栏未验证 |
| 通知拒绝后应用内可用/不反复请求 | **PASS** | 撤销权限后回顾正常、无通知、二次开关无对话框 |
| 回顾历史/迁移 v8→v9 | **PASS** | user_version=9；旧日记/缓存保留 |

---

## 3. 交互与真机截图

- `g3e1-settings-weekly-off.png` / `on.png`
- `g3e1-weekly-empty.png` / `generated.png`
- `g3e1-weekly-to-goal.png`（WeeklyGoal 定位）
- `g3e1-weekly-permission-denied.png`（拒绝通知权限后回顾仍可打开）
- `g3e1-notification-route.png`（通知等价 Intent 进入回顾）
- 深色模式、中文、长文本在同批截图中检查；回顾正文长句可滚动。

---

## 4. 测试

| 项 | 结果 |
|---|---|
| JVM | **230/230** |
| 日记 / 目标 / 趋势 / 导航 / 设置 / 导出 / 正式包 | 25 / 64 / 73 / 28 / 38 / 49 / 50 |
| 真机 v8→v9 | PASS |
| 每周规则/边界 | PASS（JVM） |
| 通知/调度 | 允许/拒绝/同周去重/Intent 路由 PASS |
| 真实 AI | 本轮后台/回顾不请求 AI；没有新增真实模型请求 |
| 通知真实点击/下一周通知 | NOT VERIFIED |
| 首页实时失效/详情关闭/异常 loading | NOT VERIFIED |
| 顾问预填 | NOT VERIFIED |

---

## 5. 隐私与设计

- 本地回顾默认不联网；Worker 无网络引擎。
- AI 总授权开启：按 GOALS/PROGRESS/JOURNAL 分类过滤。
- AI 总授权关闭：本地回顾仍可用（不发送）；顾问讨论仍走既有授权和发送前 gate。
- 通知正文只写“回顾已生成”，不含日记/金额。
- 通知权限与 AI 数据授权是两个独立概念。
- 每周任务唯一；无轮询、无定时 AI。

---

## 6. 交付

| 文件 | SHA-256 / 大小 |
|---|---|
| `artifacts/mengya-life-0.13.0-g3e1-debug.apk` | `00218ceac8d80b60c41c13b366f201aa390e4caac59349481fcc5c2905efe0de` · 12,243,977 B |
| `artifacts/mengya-life-0.13.0-g3e1-qa-test.apk` | `45eb380b3b1877059bc919b51b3461e9e4c5492693f67802248bfc77e915b859` · 12,244,699 B |
| 设备哈希 | 与上面一致 |
| 证据包 | `docs/reports/evidence/G3E1-evidence.zip` |

---

## 7. 未验证 / 遗留风险

1. 首页数据/授权变化实时失效、详情自动关闭、异常 loading 退出仅代码实现，未真机注入。
2. 顾问讨论预填未验证（QA 未配置模型）。
3. 真实点击通知栏、下一周再次通知未验证（同周去重已 DB/Worker 验证）。
4. 后台无外网 AI 由代码隔离保证，未做网络抓包。
5. 轮转通知精确时间不承诺；WorkManager 受系统调度影响。
6. 趋势脚本/通知物理点击等 UI 自动化断言存在限制，已按目视/Intent 路由记录，未冒充自动 PASS。

---

## 8. 修改文件

见 `docs/reports/logs/g3e1-source-changes.md`。核心：

- `domain/weekly/*`、`data/weekly/*`、`data/local/weekly/*`
- `WeeklyReviewWorker/Scheduler/Notification/Preferences/Service`
- `ui/weekly/*`、Settings 开关、MengyaLifeApp 路由、MainActivity 通知 Intent
- `MengyaDatabase` v9
- `InsightRules/Parser` 遗留修复
- `InsightViewModel` 异常/刷新
- `build.gradle` WorkManager + 0.13.0

## 9. 下一 Gate 建议

优先做「每周回顾历史列表 + 周期滚动」和「回顾→具体进展定位」；仍不引入后台自动 AI、复杂任务系统或云端同步。
