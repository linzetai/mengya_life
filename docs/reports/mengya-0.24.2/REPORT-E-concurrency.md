# Gate E
- reconcileSending 增加 10 分钟 lease，只收敛超时 SENDING；Runner 不再每次改 UNKNOWN。
- 发送前读最新偏好（PAUSED/SILENCED）+ fresh DND。
- 未完成：BUSY/TIME 在模型期间变化的最终复核；QA fake clock 与 Repository created_at 一致性。
- PERIODIC 6h 只能保证“大致时段”，不能保证 20:00 精确触达。
