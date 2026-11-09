# 通知与恢复交错

- CareWorker：记录 startGeneration；runOnce 后、claimNotification 前、post 前分别校验 barrier.isReady() 且 generation 未变；不满足则丢弃投递。
- CareViewModel.notifyIfNeeded：同样在 claim 前/后校验 ready/generation。
- 恢复发生在 SEND 事务后、claim 前：generation 变化 → 不 claim、不 post。
- 恢复发生在 claim 后、post 前：ready/generation 校验 → 不 post；DB 已 claim（至多一次，可能丢失一次通知）。
- 明确边界：Android 通知与 Room 无法跨系统事务；保证 DB at-most-once，不保证通知与恢复的 exactly-once；宁可少发不重复/不投递过期关心。

## CareDeliveryCoordinator（Round 13）
- 顺序：ready/gen 检查 → claim → 再次 ready/gen；false 则不 post。
- JVM 覆盖：maintenance 不 claim；claim 内发生恢复（generation+1）→ approve=false，通知不投递。
- 仍无法与 Room 组成跨系统事务；claim 后恢复会丢弃本次通知（at-most-once）。
