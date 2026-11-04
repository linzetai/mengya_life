# Gate 3E-1 · 每周成长回顾设计

## 1. 产品边界

- 本地主动能力：App 自己整理本地数据，**后台不请求外部 AI**；
- 入口：设置 →「每周成长回顾」开关（默认关闭）/ 打开回顾页；
- 通知只做提醒，不包含日记正文、金额等敏感内容；
- 用户主动进入详情后可「和顾问聊聊」，复用现有 AI 授权/发送前校验，不新增逐次审批。

## 2. 周期

- 周一 00:00（设备时区）为一周起点；`WeekWindow.current(now, zone)` 注入 `Clock`/`now`；
- 回顾显示本周（周一到 now）与上周（上周一至本周一）的对比；
- 跨月/跨年由 `java.time` 计算；ISO 周 key（如 `2026-W45`）用于去重；
- 未来可把周起点做成偏好；本轮固定周一，不承诺通知精确日期/时间。

## 3. 本地事实规则

输入：目标、进展、成长记忆、日记（仅通过记忆，不读正文上屏）。

- 本周/上周进展数量；
- 同目标 + 同单位、至少 2 个点才计算变化；不同单位分开，不相加/换算；
- 单点明确“不足以判断趋势”；同日多点注明“只说明当周变化，不代表长期趋势”；
- 7 天内到期且未完成的目标 → 值得关注；
- 已完成/已取消目标不进入到期提醒；
- 成长记忆必须 ACTIVE/CORRECTED 且来源有效；同主题用户纠正优先；
- 无记录/记录过少：明确“数据不足”，不生成确定性结论；
- 每条事实带 `GOAL` / `CHECKIN` / `MEMORY` 来源；无来源的“数据不足”事实明确标记。

输出四段：本周变化、值得肯定、值得关注、建议下一步。

## 4. 隐私语义

- **纯本地回顾不依赖 AI 总授权**：AI 关闭时仍可用本地数据整理，绝不发送；
- AI 总授权开启时，尊重 GOALS/PROGRESS/JOURNAL 分类；关闭类别后该类别数据不出现在回顾；
- AI 总授权关闭且分类为空时，本地回顾仍可读取本地数据（因为不发送）；
- 「和顾问聊聊」使用现有 Advisor 授权、发送前 endpoint/consent gate；打开回顾本身不会触发 AI。

## 5. 调度

- WorkManager 唯一周期任务 `weekly_review_periodic`：
  - 周期 7 天，flex 1 天；
  - `ExistingPeriodicWorkPolicy.UPDATE`，重复开启不产生重复任务；
  - 关闭 switch → `cancelUniqueWork`；
  - App 启动 `sync`：已开启则确保唯一任务存在；
  - 不承诺精确星期几/几点，遵守 Doze/后台限制；
- QA/手工测试：`qa_run_weekly_review` 触发一次性 Worker；不等待真实一周。

## 6. 通知

- 渠道 `weekly_review`，IMPORTANCE_DEFAULT；
- Android 13+ POST_NOTIFICATIONS：用户开启时请求一次；拒绝后应用内回顾仍可用，不反复请求；
- 通知内容仅“每周回顾已生成”，不包含日记正文/金额；
- `weekly_reviews.notified_at` + `period_key` 唯一：同一周期最多一次；
- 权限被拒绝时不标记 notified，之后获权仍可补发；获权且已发送则同周期不再发；
- PendingIntent 打开 App 并进入「每周成长回顾」；`WeeklyReviewRouter` 处理 onCreate/onNewIntent。

## 7. 存储

Room v9 `weekly_reviews`：id、period_key（唯一）、period_start/end、generated_at、version、facts_json、sections_json、notified_at。

保留历史；当前 UI 展示最新一期，历史行保留供后续扩展。

## 8. 失败降级

- Worker 本地计算/写库异常 → `Result.retry()`；
- 通知权限拒绝或通知异常 → 本地回顾仍可打开；
- 无 AI 配置/授权 → 回顾正常，只有用户主动讨论时才进入现有授权流程。
