# CareRunner 写入点栅栏矩阵

| 写点 | 栅栏 |
|---|---|
| ensure(drafts) | fenced(boundOk + withWrite(boundGeneration)) |
| markStatus(EXPIRED) | fenced |
| 模型失败 DROP | saveDecisionFenced |
| 解析失败 DROP | saveDecisionFenced |
| BUSY DEFER | saveDecisionFenced |
| DND DEFER | saveDecisionFenced |
| stillSuppressed DROPPED | fenced |
| freshBusy DEFER | saveDecisionFenced |
| 策略拒绝 DEFER | saveDecisionFenced |
| 普通 DEFER/DROP | saveDecisionFenced / fenced markStatus |
| SEND claim/message/decision/status | withWrite + Room transaction |

旧 generation 在 ensure 阶段即失败，不会进入模型或产生业务行。
