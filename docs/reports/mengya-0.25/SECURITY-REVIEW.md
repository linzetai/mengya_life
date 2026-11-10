# MengYa Life 0.25 · 安全复审

## 范围

本轮改动集中在 CARE 时间兼容、通知发送失败处理、恢复凭据收敛与 QA 故障注入；
没有新增权限、没有新增网络出口、没有修改备份/加密格式、没有改数据库 schema。

## 1. 恢复凭据收敛（核心）

### 威胁模型

恢复流程会整体替换日记、目标、进展，并可能把 AI 服务地址改成备份里的地址。
如果地址变化，旧 API Key 与旧授权不能继续用于新的接收方。
0.24.2.1 的风险：进程在 DB 提交后、安全收敛前死亡，重启时按“当前 endpoint”判断，
可能把“地址已变”误判为“地址未变”，从而保留旧 Key。

### 修复后的不变量

1. **意图先于提交**：`beforeEndpoint` / `targetEndpoint` / `hadKeyAtCommit` 在 `ARMED` Gate
   写入时就落盘（`noBackupFilesDir/restore/gate.json`，fsync + rename）；
2. **重试只认意图**：`converge()` 读 Gate 中的意图，不再读当前 endpoint；
3. **fail closed**：意图缺失、target 缺失、原 endpoint 未知时，一律删除本机 Key 并撤销授权；
   只有在“备份未携带 endpoint”时才保持本机配置；
4. **删除失败不放行**：Key 删除返回 FAILED 时停留在维护态，返回 `COMMITTED_NEEDS_CONVERGENCE`，
   不写 `markConverged`，不清理 Gate，下一次重试继续删除；
5. **敏感数据边界**：Gate 只存 endpoint 字符串、布尔值和 attempt/generation，不存 Key、不存密文；
   正式包与 QA 包使用各自私有目录，互不读取。

### 未验证

- 真机上“提交后 halt → 重启 → 跨地址删 Key”的完整场景本轮**未执行**（见 VERIFICATION，BLOCKED）；
- 旧版 Gate（无意图字段）迁移路径只有单测覆盖，没有真机迁移证据；
- `deleteKeyConfirmed()` 在 Keystore 部分失败时的行为沿用既有实现，未在本轮扩展。

## 2. 通知权限

`WeeklyReviewNotifier.notifyReady` 在发送前后都可能遇到权限被撤销：

- 发送前 `canPost()` 检查；
- 发送时 catch `SecurityException`，返回 false，不向用户声称“已发送”；
- 不申请新权限，`POST_NOTIFICATIONS` 仍由用户主动开关触发。

未验证：真机上撤销通知权限后生成每周回顾，通知是否按预期静默失败（无设备场景）。

## 3. QA 与正式包隔离

- 新增注入只作用于 `BuildConfig.QA_BUILD`：
  `qa_restore_halt_after_commit` 通过 Intent extra 打开，正式包构建中该分支不存在；
- QA 注入不写入正式包数据库，不改变正式逻辑；
- 本轮没有在正式包上执行破坏性测试。

## 4. 数据与隐私

- 未新增网络请求；未改变 BYOK Key 存储（Keystore + DataStore）；
- 恢复仍不导入/不导出 API Key；
- 本轮真机操作使用 QA 包（`com.mengyalife.app.qa`），种子数据只写入 QA 数据库；
- 未修改正式包用户数据。

## 5. 结论

- 恢复凭据的“跨进程重试不依赖当前 endpoint”与 fail-closed 语义已在**代码和单测**层面成立；
- 设备级故障注入仍为 **BLOCKED**，不能据此声称恢复安全性已通过真机验收；
- 无新增高风险面（权限、网络、导出格式、schema）。
