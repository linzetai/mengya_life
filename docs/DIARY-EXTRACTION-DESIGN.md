# Gate 3C-1 · 日记 AI 提取设计

## 1. 触发与前置条件

- 日记保存（新建/编辑）成功后，ViewModel 触发一次前台机会式提取；
- 仅在以下全部满足时发送：
  - AI 已配置且 Key 可用；
  - 持续授权有效且接收方与 runtime endpoint 一致；
  - `JOURNAL` 类别开启；
- 不满足时返回 Skipped，不发起网络请求；日记保存本身不受影响；
- 用户不需要逐条审批。

## 2. 状态

`journal_entries.analysis_status`：

- `PENDING`：待分析（默认；未配置/未授权也停留在此）；
- `ANALYZING`：正在分析；
- `COMPLETED`：已完成；
- `FAILED`：失败，可手动重试。

UI「AI 发现」显示状态、错误、重试。

## 3. 幂等与版本

- 哈希：`SHA-256(title + NUL + body)`；
- 同哈希且 COMPLETED → AlreadyDone，不再请求；
- 同版本重复线索由唯一索引 `(source, version, contentHash)` 忽略；
- 编辑日记：`analysisStatus=PENDING`、旧线索 `STALE`；重新提取；
- 删除日记：删除相关线索；
- 保存日记不会循环触发分析。

## 4. 并发、频率与体积

- 服务内 `Mutex`：同一时刻只跑一个提取；
- 每篇日记最小间隔 30 秒（手动重试 force 可绕过）；
- 输入截断 4000 字；
- 输出最多 6 条、每条 content 500 字；
- `maxTokens=1200`；
- 前台执行，不引入后台常驻服务。

## 5. 失败与授权撤回

- 网络/超时/解析失败：`FAILED` + `analysis_error`，无线索；
- 失败可重试；重试不会重复已有 contentHash；
- 请求中授权撤回/配置变化/日记被编辑：丢弃结果，标记 FAILED，不写线索；
- 引擎返回后再次检查 consent、endpoint、日记版本；
- 冷启动把上次进程遗留的 ANALYZING 标记为 FAILED（分析不会跨进程存活）。

## 6. UI

日记详情「AI 发现」区域：

- 状态：待分析 / 分析中 / 已完成 / 失败；
- 每条：分类、事实/推测、可信度、content、原文依据；
- 纠正：编辑内容，标记 USER/CORRECTED；
- 删除：软删除；
- 失败重试；
- 不把详情变成复杂分析工作台，正文仍是主体。

## 7. 顾问集成

- `AdvisorDataRepository` 只在 JOURNAL 允许时读取 ACTIVE/CORRECTED；
- 过滤 sourceVersion 与当前日记哈希不一致的线索；
- 过滤日记已删除的线索；
- 若原日记被选中，跳过其线索，避免重复；
- 用户纠正的日记只使用纠正结果；
- `AdvisorSourceType.GROWTH` 来源保存 id + 日记 id 标签。

## 8. 测试

JVM：

- 协议合法/非法/证据不在原文/诊断内容/confidence；
- 首次提取、同版本幂等、编辑重提取、失败重试、删除、授权关闭、撤回、频率；
- 上下文线索来源、原日记去重、纠正优先；
- JSON 映射/tempId/appliedGoalIds。

真机/真实模型：待 Wi-Fi 后验证（本轮未执行 adb）。

## 9. 非范围

不自动转目标、不自动打卡、不做长期记忆总结/向量库/云同步/多会话。
