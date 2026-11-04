# Gate 3C-2 · 成长记忆生命周期

状态：`ACTIVE / CORRECTED / STALE / DELETED`。

| 事件 | 行为 |
|---|---|
| 首次形成 | 同主题 >=1 条当前线索 → ACTIVE，version=1，insufficient 视日记/线索数 |
| 新增相关线索 | 同一记忆更新 content/来源/version+1；fingerprint 相同则不写 |
| 不相关线索 | 进入其它主题，不合并 |
| 用户纠正 | ACTIVE→CORRECTED，内容/版本更新，后续 AI 不覆盖 |
| 来源日记编辑 | 旧线索 STALE；记忆只使用新版本线索，重建或转 STALE |
| 来源日记删除 | 线索删除；重建时移除来源；无剩余线索 → STALE |
| 线索用户删除 | 不再进入 loadActive；记忆重建，可能 STALE |
| 用户删除记忆 | DELETED，保留 fingerprint 墓碑；同证据重建不会复活 |
| 新证据（指纹变化） | 可创建新的 ACTIVE 记忆，不复活旧 DELETED 内容 |
| 顾问 | 只用 ACTIVE/CORRECTED 且来源存在的记忆；JOURNAL 关闭完全隔离 |

## 幂等

- 相同 fingerprint 不重复创建/不升版本；
- 用户删除后，同指纹重建被跳过；
- 事务内更新记忆。

## 证据

每条记忆保存 signal IDs / diary IDs；UI 详情列出线索内容、原文 evidence、日记标题与时间。
