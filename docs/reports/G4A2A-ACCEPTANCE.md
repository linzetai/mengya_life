# G4A-2A 验收：完整备份只读验证与预览

## 1. Gate 状态

**G4A-2A Gate：PASS（只读范围）。**

本批未实现任何 Room/DataStore 写入路径；设置页可经 SAF ACTION_OPEN_DOCUMENT 选择 v2 备份，
输入口令后完成解密、完整性/结构/引用校验并展示预览。真实 QA 预览 **21/21 PASS**，预览前后 QA DB 逐表逐字段不变。

## 2. 真机预览结果

| 项 | 结果 |
|---|---|
| 设备 | `10.13.224.241:46519` / `2509FPN0BC` / SDK 37 |
| 包 | `com.mengyalife.app.qa` / `0.14.1-g3e2-1-qa` |
| SAF | ACTION_OPEN_DOCUMENT，Downloads 选择 `G4A2A-preview.mengyalife` |
| 文件 | v2 加密 / 15,373 字节 / KDF 310000 |
| 基础计数 | 2/3/2/3/2/3/2/2/2/2（10 表与当前 DB 一致） |
| 预览内容 | 备份时间、来源 QA/版本、格式、DB 11→11、10 表备份/本机计数、不恢复清单 |
| 不恢复 | API Key/Keystore、AI 授权、自动发送、通知权限、历史 weekly AI |
| 执行按钮 | `执行恢复（G4A-2B 开放）` **不可点击** |
| 页面提示 | “尚未执行恢复；没有写入任何数据” |
| DB 前后 | **完全一致**（全表 JSON 相等） |
| 重启后 | 无预览/口令残留；应用日志无口令 |
| 汇总 | **21/21 PASS，142.93s** |

原始页面：`docs/reports/G4A2A-acceptance/qa/preview-ui.xml`
原始日志：`docs/reports/G4A2A-acceptance/qa/run-output.txt`

## 3. 校验实现覆盖

| 层 | 已实现 |
|---|---|
| 文件 | 64 MiB 上限、空文件、SAF 只读、v1 仅识别 |
| 格式 | v2/format/backupFormatVersion；未知版本拒绝 |
| 来源 | `generator.appPackage` + `qaBuild` 必须与当前构建一致（QA 备份不被正式包静默接受） |
| DB 版本 | 未来 schema 拒绝；旧 schema warning（本阶段不做迁移） |
| 加密 | KDF 210k–600k、salt 16、nonce 12、AES-256-GCM、AAD |
| 完整性 | 解密、AEAD、payloadSha256、perTableSha256、counts |
| 结构 | 10 表字段/类型/必填/可空/唯一键/枚举/有限 Double/JSON TEXT/嵌套上限 |
| 引用 | 强引用拒绝；历史悬挂 warning；显式 repair plan |
| 预览 | 10 表备份/本机计数、覆盖范围、不恢复内容、warning/repair、未执行 |

JVM 覆盖：正确/错误口令、截断/篡改、v1/未知版本、未来 schema/旧 schema、QA/正式冲突、空/10 表、重复主键、非法枚举/Double、异常嵌套、强引用/历史悬挂、旧/新 factIds、64 MiB、预览无执行。

## 4. 测试

| 命令 | 退出码 | 结果 | 耗时 |
|---|---|---|---|
| `./scripts/verify.sh --module restore-validate --level fast` | 0 | PASS（15 个 restore 测试） | 7.8s |
| 全量 JVM | 0 | **291/291 PASS** | 8.8s |
| `DEV=... ./scripts/verify.sh --module restore-validate --level feature` | 0 | **L1 PASS** | 295.1s |
| `verify_restore_preview.py`（最终 APK） | 0 | **21/21 PASS** | 142.93s |
| APK 构建 | 0 | BUILD SUCCESSFUL | 13s |

L1 报告：`docs/reports/G4A2A-acceptance/l1/report.json`（包含设置页/导航冒烟；不是七组全量）。

## 5. APK

| 包 | SHA256 |
|---|---|
| QA `0.14.1-g3e2-1-qa` | `584e312badb63ec901e91ff8b515129ebe50dd00dcf6552ee5d91672343db404` |
| Debug `0.14.1-g3e2-1` | `3818347abf619e8ab9d2a66d51599bcd55f01a4ce9422d7618ad663a4f851cdb` |

## 6. 上一轮遗留修复

- `FileBackupTempStore.delete()` 不再吞 `File.delete()==false`：真实“非空目录替换临时文件”JVM 测试断言抛 `IOException`；`BackupFileWriter` 将其作为清理失败。
- `G4A1-1-evidence.zip` 已移出仓库（`/root/workspace/G4A1-1-evidence.zip` 与本机 Download）；Git 中删除该 ZIP。
- 本批证据 ZIP 也只在仓库外生成，不进入 Git。

## 7. 待确认产品决策

| 决策 | 状态 | 本批处理 |
|---|---|---|
| E：历史 weekly AI 仅归档、不恢复 | **未获得本批显式确认** | 预览明确“仅归档，不恢复”；不写回 |
| F：恢复后删除本机 API Key | **未确认** | 预览只声明“不会恢复 Key”；不执行删除 |

本轮只读，不依赖 E/F 的写入语义；进入 G4A-2B 实现覆盖/密钥删除前必须确认。

## 8. 未覆盖/边界

- 没有真正执行恢复、没有 DB 写入、没有 DataStore 写入、没有 Gate/app_meta/迁移。
- 未验证恢复后的跨存储 reset、Worker/AI 门禁、回滚、事务崩溃窗口（G4A-2B/C）。
- 未做七组完整真机回归；L1 只跑恢复预览+设置/导航冒烟。
- 预览只使用 QA 假数据，不接触正式用户数据。
- 未对真实大文件/大数据量做性能测试。

## 9. 结论

只读验证与预览阶段通过；没有可执行恢复路径。停止在 G4A-2A，不进入 G4A-2B。
