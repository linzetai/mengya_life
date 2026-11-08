# 恢复状态矩阵

| 场景 | endpoint | Key | 授权 | 结果 |
|---|---|---|---|---|
| 同 endpoint 恢复 | 不变 | 保留 | 保留 | 可直接用 |
| 不同 endpoint 恢复 | 变化 | 删除（若有） | reset/撤销 | 必须重配 Key + 重新授权 |
| 备份无 endpoint | 保持本机 | 保留 | 保留 | 不扩大接收方 |
| 无 Key | 变化 | 无 | reset | 必须重配 |
| 恢复失败 | 保持旧值 | 保留 | 保持 | 原库不变（设备已验证） |
| 恢复中 Key 删除失败 | 变化 | 未确认 | 未重置 | COMMITTED_NEEDS_CONVERGENCE，维护态 |
