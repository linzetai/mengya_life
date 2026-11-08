# Gate E
- reconcileSending 增加 10 分钟 lease，只收敛超时 SENDING；Runner 不再每次改 UNKNOWN。
- 发送前读最新偏好（PAUSED/SILENCED）+ fresh DND。
- 未完成：BUSY/TIME 在模型期间变化的最终复核；QA fake clock 与 Repository created_at 一致性。
- PERIODIC 6h 只能保证“大致时段”，不能保证 20:00 精确触达。

## Round 5
- 发送前重新读取 care_preferences：若模型运行期间新增 BUSY（全局或命中 plan/goal/topic），SEND 改为 DEFER，不投递。
- 仍缺：TIME 在运行中变化的复核（当前仅 WEEKLY_REVIEW 生效）。

## Round 15
- CareRepository 的 now 由容器注入：QA_BUILD 且 test_now>0 时使用虚拟时钟，决策 created_at 与 Runner 一致；生产使用真实时钟。
- 修复 QA 时钟下预算/冷却用真实时间导致失真的问题。
