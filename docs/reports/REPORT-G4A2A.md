# Gate 4A-2A 报告：完整备份恢复的只读验证与预览

> 范围：只读 SAF 选择、解密、校验、预览；**没有恢复执行**，没有 Room/DataStore 写入。
> 基线：G4A-1.1 PASS；设备 QA `10.13.224.241:46519`。
> 结果：**PASS（只读范围）**。

## 1. 实现结构

```text
设置页 → ActivityResultContracts.OpenDocument (ACTION_OPEN_DOCUMENT)
  → SafRestoreFileSource（只读 URI，大小上限）
  → BackupRestoreReader（detect/v1 拒绝、KDF 校验、AES-GCM、hash）
  → RestoreValidator（来源/版本/结构/枚举/数值/唯一键/引用）
  → RestorePlanner（当前只读计数 + backup 计数）
  → RestorePreview UI（只读，执行按钮禁用）
```

源码：

- `app/src/main/java/com/mengyalife/app/data/restore/BackupRestoreReader.kt`
- `.../data/restore/RestoreValidator.kt`
- `.../data/restore/RestorePlanner.kt`
- `.../data/restore/RestoreCurrentStateSource.kt`
- `.../data/restore/SafRestoreFileSource.kt`
- `.../data/restore/RestoreModels.kt`
- `.../ui/settings/RestoreViewModel.kt`、`RestoreSection.kt`
- `.../di/AppContainer.kt`（只读依赖）
- `SettingsScreen.kt`（恢复入口）

**没有任何 `RestoreExecutor`、DAO insert/update/delete、DataStore.edit 或 Gate/journal 写入。**

## 2. 校验范围

1. 文件大小：64 MiB；空文件拒绝；SAF 只读。
2. 格式：v2、外层 format/version、AAD、KDF/cipher/nonce/salt/tagBits；v1 只识别拒绝。
3. 来源：`generator.appPackage` + `qaBuild` 必须一致（QA 备份不被正式包接受）。
4. 版本：未来 schema 拒绝；旧 schema warning，不做迁移。
5. 完整性：AEAD、payloadSha256、perTableSha256、counts。
6. 结构：120 列类型/必填/可空/唯一键/枚举/有限 Double/JSON TEXT/嵌套深度 64。
7. 引用：goals 父子/环、checkins FK 强拒绝；消息 context/proposal message/applied ids/signal/memory 历史悬挂 warning + repair plan。
8. 兼容：旧 U+001F factIds 与新 JSON 均可读；历史行不强行改写。
9. 资源：超过 64 MiB、未知表、未知 feature、未来版本 fail-closed。

## 3. 预览内容

真实 QA 预览（`G4A2A-acceptance/qa/preview-ui.xml`）：

- 备份时间、来源包/版本/QA、格式、KDF 310000、DB 11→11、文件 15,373 B；
- 10 表备份/本机计数（2/3/2/3/2/3/2/2/2/2）；
- 9 表“将覆盖”；weekly AI“仅归档，不恢复”；
- 不恢复：API Key/Keystore、授权、自动发送、通知权限、weekly AI；
- “尚未执行恢复；没有写入任何数据”；
- 执行按钮 `执行恢复（G4A-2B 开放）` 不可点击。

预览前后 DB 全表 JSON 相等；重启后无预览/口令，应用日志无口令。

## 4. 测试

| 命令 | 结果 | 耗时 |
|---|---|---|
| `--module restore-validate --level fast` | PASS（15 restore 测试） | 7.8s |
| 全量 JVM | **291/291 PASS** | 8.8s |
| `--module restore-validate --level feature` | **L1 PASS**（预览 21/21） | 295.1s |
| `verify_restore_preview.py` 最终 APK | **21/21 PASS** | 142.93s |
| APK 构建 | BUILD SUCCESSFUL | 13s |

JVM 覆盖：正确/错误口令、截断/篡改、v1/未知版本、未来/旧 schema、QA/正式冲突、空/10 表、重复主键、非法枚举/Double、异常嵌套、强引用/历史悬挂、旧 factIds、64 MiB、预览不可执行。
`FileBackupTempStoreTest` 用真实“非空目录导致 File.delete()==false”验证删除失败，不再只测人工抛异常。

## 5. 遗留修复

- TempStore 删除失败现在抛 `IOException`，`BackupFileWriter` 报告 `tempCleanupFailed`。
- G4A1-1 ZIP 已移至仓库外；本批 ZIP 也不入库。
- 证据包：`G4A2A-evidence.zip`（仓库外 `/root/workspace/G4A2A-evidence.zip` + 本机 Download）。

## 6. 未做

- 实际恢复、覆盖、回滚、Gate/journal/app_meta、schema 迁移；
- 恢复后 Key/consent 删除与重置；
- 大数据量与七组全量回归；
- 正式包接受 QA 备份（明确拒绝）。

## 7. 待确认

- **E**：历史 weekly AI 仅归档、不恢复（本批预览已按此展示，但未获本批显式确认）。
- **F**：恢复后删除本机 API Key（本批不执行）。
- 进入 G4A-2B 前需确认 E/F、恢复写入门禁与最终确认 UX。

## 8. 结论

G4A-2A 的只读 SAF、解密、完整性/结构/引用校验、预览与失败保持已完成；**无写入路径**。停止，不进入 G4A-2B。
