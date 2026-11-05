# G4A-0 安全与隐私（SECURITY & PRIVACY）

> 范围：完整备份/恢复的威胁模型、加密、秘密排除、系统备份、日志与 QA/正式隔离。
> 本轮只读审计与设计；没有实现加密、恢复或修改 Manifest。

## 1. 数据分类

| 级别 | 数据 | 例子 | 备份策略 |
|---|---|---|---|
| **S3 秘密** | API Key、Keystore 私钥/密钥、备份口令、Keystore 别名 | `mengya_ai_key`、`encrypted_api_key` | **永不进入备份**；永不出设备 |
| **S2 高敏感** | 日记正文/标题、顾问对话、成长线索/记忆、洞察、周回顾、AI 周报、草案 | journal body、advisor content | 必须加密备份；恢复后严格校验 |
| **S1 中敏感** | 目标/进展（健康/习惯）、AI endpoint/model、授权接收方/类别、条目计数 | goals、checkins、consent | 加密备份；endpoint/model 可选；consent 不恢复 |
| **S0 低敏感** | App 版本、dbSchemaVersion、backupFormatVersion、主题、周开关、时间戳 | generator、settings.theme | 可随备份；仍建议整体加密 |
| **设备状态** | 通知权限、通知渠道、WorkManager DB、设备型号/序列号 | OS | 不备份；恢复后重建/重新请求 |

注意：即使 S0 的“条目计数”也会泄露用户记录量；v2 默认整体加密，不在明文头部暴露 counts（预览在解密后）。

## 2. 威胁模型

| 威胁 | 场景 | 影响 | 控制 |
|---|---|---|---|
| 设备丢失/被窃 | 备份文件在 Download/云盘 | S2 数据泄露 | 口令 + AES-GCM；不明文导出 |
| 云盘/聊天工具转发 | 用户分享备份 | 泄露 | 加密 + 明确警告；不自动上传 |
| 恶意备份文件 | 用户导入他人/构造文件 | 崩溃、注入、数据污染 | 大小/结构/引用/枚举/版本全校验；不执行 SQL 字符串拼接 |
| 篡改/截断 | 传输损坏/恶意修改 | 恢复错数据 | AEAD tag + payload hash + per-table hash + counts |
| 重放旧备份 | 用户误选旧文件 | 覆盖新数据 | 预览日期/backupId；高风险确认；不自动恢复；可选“已恢复过”检测 |
| 口令暴力破解 | 备份泄露 | 解密 | PBKDF2 高迭代/Argon2 备选；口令强度提示；无后门 |
| Keystore 导出 | 备份/ADB | 密钥泄露 | Keystore 密钥不可导出；API Key 不备份 |
| 授权被恢复 | 换机/旧备份 | 向旧接收方自动发送 | consent 永不恢复；AI 自动关；重新同意 |
| 半恢复 | 进程死亡/失败 | 数据不一致 | DB 事务 + journal + app_meta + 启动 reconciliation |
| 日志/崩溃泄露 | 异常信息 | S2 泄露 | 不记录正文/Key/口令；错误码化 |
| QA/正式串数据 | 导入错包 | 测试数据进正式/真实数据进 QA | 包名+qaBuild 严格匹配 |
| 系统备份 | OEM D2D/云 | 未加密副本 | allowBackup=false + dataExtractionRules + fullBackupContent=false |

## 3. API Key 与 Keystore：永久排除

### 3.1 现状

- API Key 以 `encrypted_api_key` 存在 `mengya_ai` DataStore（`AiConfigStorage.kt:45`），密文由 Android Keystore AES/GCM 保护（`SecretCipher.kt`）。
- Keystore 密钥不可导出；删除 alias 会使旧密文永久不可解。
- 备份/导出当前 v1 不含 Key；这是正确的，v2 必须保持。

### 3.2 规则

1. **任何备份（v2 明文/密文）都不得包含** `encrypted_api_key`、Keystore alias、Key 明文、Key 哈希。
2. 备份文件不得包含可用于重建 Key 的设备信息（ANDROID_ID、序列号、Keystore blob）。
3. 恢复时：
   - 推荐删除本机 API Key + Keystore alias（fail-closed）；用户重新输入；
   - 或保留本机 Key（若存在），但必须重新授权后才可用；不得自动发送；
   - 绝不从备份恢复 Key。
4. 日志/崩溃/错误消息/导出预览不得出现 Key；现有测试已断言 DataStore/日志不含 Key（G3F `verify_weekly_ai`、`verify_advisor`）。
5. QA 假引擎开关 `use_fake_engine` 不属于用户数据，不进入正式备份；正式构建也不创建假引擎。

### 3.3 为什么不能“备份加密后的 Key”

- 密文绑定设备 Keystore；换设备/恢复出厂后不可解，备份会变成“有 Key 但永远用不了”的坏状态。
- 若把 Keystore 密钥导出，等于破坏 Android Keystore 的安全模型。
- 正确做法：**Key 不备份，恢复后重输**。

## 4. 授权同意：永不恢复

`mengya_consent` 的 7 个键（`granted/text_version/categories_version/granted_at/recipient/categories/epoch`，`AiConsentRepository.kt:46-52`）属于 S1/S2：

- 授权是“对当前设备、当前接收方、当前数据类别”的明确同意；跨设备/跨版本恢复不成立。
- 恢复后必须 `granted=false`，重新走首次授权面板。
- 旧 epoch 不得复用；新同意产生新 epoch，旧 AI 结果不得进入新请求。
- 备份预览明确写“授权不会恢复，需要重新同意”。
- 即使恢复旧备份，也不能把旧 recipient/categories 自动写回。

## 5. 加密方案比较

| 方案 | 机密性 | 完整性 | 跨设备 | 密钥遗失 | 依赖/成本 | 结论 |
|---|---|---|---|---|---|---|
| 不加密 | 无（依赖存储） | 仅 SHA | 是 | 可读 | 0 | 仅 v1 遗留；v2 不默认 |
| Keystore AES-GCM（设备绑定） | 高 | GCM tag | **否** | 备份永久不可解 | 低（已有 Keystore） | 本地快照可用；不做换机备份 |
| 口令 PBKDF2-HMAC-SHA256 + AES-256-GCM | 高 | GCM tag | **是** | 不可恢复 | 中（系统 API） | **推荐默认** |
| 口令 Argon2id + AES-256-GCM | 更高（抗 GPU） | GCM tag | 是 | 不可恢复 | 高（额外库/原生） | 备选，G4A-3 评估 |
| SQLCipher 整库 | 高 | 页面 HMAC | 是 | 不可恢复 | 高（依赖/迁移/恢复复杂） | 不作为文件备份首选 |

### 5.1 推荐参数（待 G4A-1 基准锁定）

- KDF：PBKDF2-HMAC-SHA256；salt 16B 随机；输出 256-bit；
- 迭代：初始候选 310k–600k，目标解锁 ≤1.5s；不低于 210k；
- AEAD：AES-256-GCM，nonce 12B 随机，tag 128-bit；
- AAD：外层头（format/version/KDF 参数/salt）规范化 JSON；
- 每次导出新 salt/nonce；同一口令不产生相同密文；
- 不做压缩，避免 zip bomb 与额外解析面。

### 5.2 口令与密钥遗失

- 口令不落盘、不进日志、不进崩溃报告、不存 Keystore。
- 忘记口令 = 备份永久不可解；UI 在设置口令时必须明确，不提供后门/客服解密。
- 可选“口令强度提示”，但不强制复杂规则导致用户写下来；建议 ≥10 字符，禁止纯数字。
- 不在备份中存口令提示（hint 也可能泄露）。

### 5.3 跨设备

- v2 加密备份只依赖口令与格式版本，不绑定 Google 账号/设备。
- 恢复后 AI Key/consent 必须重建。
- 若未来提供“设备绑定备份”，只能作为本地快照，不用于换机。

## 6. Android 系统备份与 D2D

### 6.1 真实配置

| 项 | 当前值 | 证据 |
|---|---|---|
| `android:allowBackup` | `false` | `AndroidManifest.xml:15` |
| `android:dataExtractionRules` | **缺失** | Manifest 无该属性；`res/xml` 无规则文件 |
| `android:fullBackupContent` | **缺失** | 同上 |
| `targetSdk` | 35 | `app/build.gradle:16` |
| 合并后 Manifest | 仍只有 allowBackup=false | `app/build/intermediates/merged_manifest/qa/.../AndroidManifest.xml:32` |

### 6.2 风险

- `allowBackup=false` 通常禁用云备份/ADB backup；但 Android 12+ 的 **device-to-device (D2D) 迁移**在某些 OEM 设备上可能仍复制应用数据，包括数据库和 Prefs。Mozilla 在 Fenix/Focus 上报告过此问题，并通过 `dataExtractionRules` + `fullBackupContent="false"` 修复（[Bug 2073967](https://bugzilla.mozilla.org/2073967)）。
- 若 D2D 复制了 Room DB + DataStore，但 Keystore Key 不随迁，可能出现“有加密 Key 密文但无法解密”“旧 consent 仍 granted”“旧 AI endpoint 仍存在”的半状态。
- 这与“本机优先、无云同步”的产品承诺冲突，属于 P0 隐私/一致性风险。

### 6.3 建议（G4A-1 或独立安全修复）

1. 新增 `res/xml/data_extraction_rules.xml`，在 `cloud-backup` 与 `device-transfer` 中排除 `root`、`file`、`database`、`sharedpref`、`external` 全部路径（按需保留无敏感缓存？本 App 无）。
2. Manifest 增加：
   ```xml
   android:dataExtractionRules="@xml/data_extraction_rules"
   android:fullBackupContent="false"
   ```
3. 保持 `allowBackup="false"`。
4. 增加 lint/测试断言，防止以后误删规则。
5. 这样系统不迁移任何 App 数据；换机由用户主动使用 v2 加密备份。

> 备选：允许系统备份但用 `dataExtractionRules` 只排除 DB/Key，保留主题等；不推荐——仍会产生未加密的系统副本，且无法保证跨版本一致性。

## 7. 恢复过程的安全控制

1. **先校验后写入**：格式/版本/大小/解密/结构/引用全部通过才进入 DB。
2. **参数化写入**：Room `@Insert`/`execSQL` 绑定参数，不拼接用户字符串。
3. **路径安全**：只用 SAF URI；不根据文件名拼路径；不扫描目录。
4. **资源上限**：文件/载荷/行/字段限制；先 stat 再读；拒绝超大/深嵌套。
5. **完整性**：AEAD tag + SHA-256 + counts + 引用；任何不一致拒绝。
6. **原子性**：DB 事务 + app_meta + journal；失败回滚。
7. **fail-closed**：恢复前撤权/关 AI 自动/清 Key（推荐）；失败后不自动重发。
8. **保密**：不记录正文/Key/口令/完整备份路径到日志；错误信息只含错误码/表名/行号。
9. **临时文件**：`noBackupFilesDir`、0600、完成后删除；不在公共目录留明文。
10. **用户确认**：预览 + 高风险确认；不默认勾选。
11. **QA/正式**：`generator.appPackage` + `qaBuild` 必须与当前构建一致，否则拒绝。
12. **快照**：恢复前快照设备本地加密/沙箱；不自动回滚。

## 8. 日志、证据与隐私

- 备份/恢复日志只记录：backupId（随机）、phase、耗时、表名、行数、错误码、版本、哈希前 8 位。
- 不记录：日记正文、对话内容、目标标题、Key、口令、完整文件路径、设备序列号。
- 证据包（如测试）使用 QA 假数据；不得把真实用户数据打进证据。
- 现有 G3F 测试已断言 Key 不进 DataStore 明文/日志（`verify_weekly_ai`、`verify_advisor`）；G4A 新增测试必须延续。

## 9. QA / 正式隔离

| 维度 | 正式 | QA |
|---|---|---|
| applicationId | `com.mengyalife.app` | `com.mengyalife.app.qa` |
| DB | `mengya-life.db` | `mengya-life-qa.db` |
| Prefs | `mengya_settings` / `mengya_ai` / `mengya_consent` | `_qa` |
| Keystore | `mengya_ai_key` | `mengya_ai_key_qa` |
| 备份 generator | `qaBuild=false` | `qaBuild=true` |

规则：

- 正式版**拒绝**恢复 QA 备份；QA 版**拒绝**恢复正式备份（防止真实数据进入测试设备）。
- 测试导出只写 QA；正式包测试只读，不执行恢复。
- 恢复破坏性用例仅允许 QA（沿用 G3F `require_qa` + QA_BUILD 前置校验）。
- 不把正式库/正式导出复制到仓库或证据包。

## 10. 安全风险清单（P0/P1）

| 级别 | 风险 | 控制/计划 |
|---|---|---|
| **P0** | 系统 D2D 可能复制未加密数据 | 加 `dataExtractionRules` + `fullBackupContent=false`；lint/test 防回归 |
| **P0** | 备份含 API Key/Keystore | 格式显式排除；测试断言；代码审查 |
| **P0** | 恢复旧 consent 导致自动发送 | consent 永不恢复；AI 自动关；重新同意 |
| **P0** | 无跨存储事务/半恢复 | app_meta + journal + reconciliation |
| **P0** | 恶意/超大文件 | 大小/结构/引用/资源上限；先校验后写 |
| **P1** | 口令弱/遗失 | 强度提示；明确不可找回；高迭代 KDF |
| **P1** | 日志泄露 | 脱敏；测试断言 |
| **P1** | QA/正式串数据 | 包名+qaBuild 拒绝 |
| **P1** | 备份文件被重放 | 预览 backupId/日期；高风险确认；已恢复检测 |

## 11. 结论

- v2 推荐“口令 + PBKDF2 + AES-256-GCM”的可移植加密备份；API Key/Keystore/consent 永不进入备份。
- 系统备份必须显式关闭 D2D/云（当前只有 allowBackup=false，不足）。
- 恢复必须 fail-closed：先撤权/关自动/清 Key，再替换数据，最后要求重新同意与重输 Key。
