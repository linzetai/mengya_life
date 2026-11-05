# G4A-0 备份格式 v2 设计（BACKUP FORMAT V2）

> 本文是设计，不是已实现代码。当前产品只有 v1 三表导出（见 `G4A0-DATA-INVENTORY.md`）。
> 目标：在不破坏 v1 既有语义的前提下，定义可版本化、可校验、可加密、可跨设备恢复的完整备份格式。

## 1. 现有 v1 审计

### 1.1 真实行为

| 项 | v1 现状 | 证据 |
|---|---|---|
| 格式标识 | `format="mengyalife-backup"` | `ExportJson.kt:43` |
| 格式版本 | `schemaVersion=1` | `ExportJson.kt:43,98` |
| 数据范围 | journal 5 字段、goals 11 字段、checkins 7 字段 | `ExportJson.kt:152-184` |
| 一致性 | Room `withTransaction` 内读三表 | `DataExportRepository.kt:47-49` |
| 校验 | JSON 结构、计数、id、父子、checkin→goal | `ExportJson.kt:206+` |
| 加密 | 无 | — |
| 导入 | **没有**；Settings 明确“导入恢复尚未实现” | `SettingsScreen.kt:264-268,406` |
| 文件写入 | SAF `openOutputStream(uri,"wt")` 直接写，回读校验 | `ExportViewModel.kt:157-168` |
| 文件名 | `mengya-life-export-YYYYMMDD-HHmmss.json` | `ExportJson.kt` |
| 系统备份 | `allowBackup=false` | `AndroidManifest.xml:15` |

### 1.2 v1 的语义（必须保留）

- v1 是“**三表导出**”，不是完整备份；它的字段、文件名、JSON 结构不能改变。
- v1 文件可以被新版本**识别**，但不能被静默当作完整备份恢复。
- v1 不含分析状态、对话、草案、成长线索/记忆、洞察、周回顾、设置、加密。
- v1 的 `database.schemaVersion` 是导出时的 Room 版本（当前 11），不是备份格式版本。

## 2. v2 设计原则

1. **格式版本与数据库版本分离**：`backupFormatVersion` 独立于 `dbSchemaVersion`。
2. **完整覆盖用户数据**：所有 10 张 Room 表 + 非秘密设置；秘密/授权按策略排除。
3. **恢复安全优先**：宁可拒绝，也不做部分恢复、不静默丢字段。
4. **可审计**：每个表有计数与摘要，恢复前可预览，恢复后可核验。
5. **可移植**：默认不绑定设备；API Key/Keystore 永不进入备份。
6. **向后兼容**：v1 只识别；v2 读取器支持 `dbSchemaVersion <= current` 的有界映射，未来版本拒绝。
7. **不把 SQLite 文件当备份**：WAL/连接/设备差异不可控；使用逻辑行数据。

## 3. 文件类型与命名

| 项 | 建议 |
|---|---|
| 扩展名 | `.mengyalife`（加密 v2）；v1 继续 `.json` |
| MIME | `application/octet-stream`（SAF 不依赖扩展名） |
| 文件名 | `mengya-life-backup-v2-YYYYMMDD-HHmmss.mengyalife` |
| 明文 v2 | **不默认提供**；仅调试构建可导出，且文件名带 `-plaintext-` 警告 |
| 识别 | 先读前 4 KiB，按 `format` + `backupFormatVersion` 判定；不信任扩展名 |

## 4. v2 逻辑载荷（解密后的明文 JSON）

### 4.1 顶层结构

```json
{
  "format": "mengyalife-backup",
  "backupFormatVersion": 2,
  "backupId": "0f5b4f0e-9f6a-4a6e-9c2d-2f7a0f6f1a11",
  "createdAt": 1793827200000,
  "createdAtIso": "2026-11-05T12:00:00Z",
  "generator": {
    "appPackage": "com.mengyalife.app",
    "qaBuild": false,
    "versionName": "0.14.1-g3e2-1",
    "versionCode": 15,
    "androidSdk": 35
  },
  "compatibility": {
    "dbSchemaVersion": 11,
    "requiredFeatures": ["core", "derived-insight", "derived-weekly"],
    "minReaderBackupFormatVersion": 2
  },
  "counts": {
    "journalEntries": 0,
    "goals": 0,
    "goalCheckins": 0,
    "advisorMessages": 0,
    "goalProposals": 0,
    "growthSignals": 0,
    "growthMemories": 0,
    "insightReports": 0,
    "weeklyReviews": 0,
    "weeklyAiReviews": 0
  },
  "payloadSha256": "hex-of-canonical-payload",
  "data": {
    "journalEntries": [],
    "goals": [],
    "goalCheckins": [],
    "advisorMessages": [],
    "goalProposals": [],
    "growthSignals": [],
    "growthMemories": [],
    "insightReports": [],
    "weeklyReviews": [],
    "weeklyAiReviews": []
  },
  "settings": {
    "themeMode": "SYSTEM",
    "weeklyReview": { "enabled": true, "aiEnabled": false },
    "ai": { "endpoint": "", "model": "" }
  },
  "integrity": {
    "perTableSha256": {
      "journalEntries": "hex",
      "goals": "hex"
    }
  }
}
```

说明：

- 字段名使用 v1 的 camelCase 风格，便于阅读与迁移。
- `payloadSha256` 是**规范化 JSON**（键排序、无多余空白）的 SHA-256；`integrity.perTableSha256` 便于定位哪张表损坏。
- `counts` 与数组长度必须一致；`counts` 也参与校验。
- `data` 必须包含全部声明的核心表；`derived` 表可以省略，但 `requiredFeatures` 要如实声明。
- unknown 顶层字段：读取器忽略并记录警告；unknown 表/数组：拒绝（防止误判为可恢复）。

### 4.2 行对象（与列一一对应）

行对象使用**完整字段**，不允许“只导出 UI 需要的字段”。示例：

```json
{
  "id": 1,
  "title": "标题",
  "body": "正文",
  "createdAt": 1793827200000,
  "updatedAt": 1793827200000,
  "analysisStatus": "PENDING",
  "analysisHash": null,
  "analysisError": null,
  "analysisAttempts": 0,
  "analyzedAt": null,
  "analysisLastAttemptAt": null
}
```

表字段映射：

| v2 数组 | Room 表 | 备注 |
|---|---|---|
| `journalEntries` | `journal_entries` | 11 列 |
| `goals` | `goals` | 11 列；按 level/id 拓扑 |
| `goalCheckins` | `goal_checkins` | 7 列；FK |
| `advisorMessages` | `advisor_messages` | 12 列 |
| `goalProposals` | `goal_proposals` | 13 列；JSON 字段保持字符串或结构化？见 §4.3 |
| `growthSignals` | `growth_signals` | 15 列 |
| `growthMemories` | `growth_memories` | 16 列 |
| `insightReports` | `insight_reports` | 11 列；派生 |
| `weeklyReviews` | `weekly_reviews` | 11 列；派生 |
| `weeklyAiReviews` | `weekly_ai_reviews` | 13 列；只归档，不恢复 |

### 4.3 JSON 列：保持字符串还是结构化？

Room 中的 `sources_json`、`nodes_json`、`facts_json`、`sections_json`、`highlights_json`、`signal_ids_json` 等目前是 TEXT，内容是应用自己生成的 JSON。

**建议：在 v2 中同时保留“语义化字段”和“原始字符串”两选一，而不是无条件展开：**

- 对已知结构（proposal nodes、weekly facts/sections、insight facts/insights）使用结构化数组，便于校验与未来迁移；
- 对未知/历史脏数据提供 `raw` 字段（或 `jsonText`），恢复时原样写回并标记警告；
- 对 `weekly_ai_reviews.fact_ids_json` 特别处理：它实际是 U+001F 分隔串（`WeeklyAiService.kt:184`），v2 应输出 `factIds: ["..."]`，同时保留 `legacyFactIdsRaw` 以审计；历史行不强行改写。

> 这是格式设计，不是本批改数据。G4A-1 修 writer/reader；历史行修正需单独评审。

### 4.4 设置载荷

```json
"settings": {
  "themeMode": "SYSTEM|LIGHT|DARK",
  "weeklyReview": {
    "enabled": true,
    "aiEnabled": false,
    "notificationAsked": false
  },
  "ai": {
    "endpoint": "https://api.example.com/v1",
    "model": "model-name"
  }
}
```

规则：

- **不包含** `encrypted_api_key`、Keystore alias、`use_fake_engine`。
- **不包含** consent 的任何键（granted/textVersion/categoriesVersion/grantedAt/recipient/categories/epoch）。
- `aiEnabled` 在导出时记录，但恢复时**强制 false**（见恢复设计）。
- `notificationAsked` 恢复为 false；OS 通知权限不能被备份恢复。
- `ai` 是否纳入是 A/B 决策（§10）；默认建议纳入 endpoint/model 但不自动启用。

## 5. 加密封装（推荐默认）

### 5.1 外层结构

```json
{
  "format": "mengyalife-backup",
  "backupFormatVersion": 2,
  "encryption": {
    "cipher": "AES-256-GCM",
    "kdf": "PBKDF2-HMAC-SHA256",
    "iterations": 310000,
    "salt": "base64-16-bytes",
    "nonce": "base64-12-bytes",
    "tagBits": 128
  },
  "aad": "canonical-outer-header-without-ciphertext",
  "ciphertext": "base64(encrypted-compressed? no-compression)"
}
```

- **AAD** 绑定 `format/backupFormatVersion/encryption` 参数与 `aad` 字段本身，防止降级 KDF/迭代次数或替换 salt。
- **nonce** 每次导出随机 12 字节；同一密钥下绝不重用。
- **salt** 16 字节随机；每次导出重新生成。
- **不做压缩**（避免 zip bomb 与实现复杂度）；大小限制在解密前后都执行。
- 明文只存在于内存与短生命周期临时文件；写 SAF 前清理临时文件。

### 5.2 KDF/AEAD 选型比较

| 方案 | 跨设备 | 密钥遗失 | 安全强度 | 实现成本 | 结论 |
|---|---|---|---|---|---|
| 不加密 | 是 | 可读 | 依赖用户存储 | 最低 | 仅 v1 历史；v2 不默认 |
| Android Keystore AES-GCM（设备绑定） | **否** | 备份不可解 | 高（不出设备） | 低 | 适合**本地自动快照**，不适合换机备份 |
| 口令 + PBKDF2-HMAC-SHA256 + AES-256-GCM | **是** | 不可恢复 | 高（足够迭代） | 中（JDK/Android 内置） | **推荐默认** |
| 口令 + Argon2id + AES/ChaCha20 | 是 | 不可恢复 | 更高（抗 GPU） | 高（额外依赖/原生） | 备选，G4A-3 评估 |
| SQLCipher 整库加密 | 是 | 不可恢复 | 高 | 高（迁移/恢复复杂） | 不用于文件备份 |

参数建议（需 G4A-1 基准后锁定）：

- PBKDF2-HMAC-SHA256，salt 16B，输出 256-bit；
- 迭代次数初始候选 **310,000–600,000**，目标在目标机解锁 ≤1.5s；不得低于 210,000；
- AES-256-GCM，nonce 12B，tag 128-bit；
- 口令不落盘、不进日志、不进崩溃报告；不提供“找回密钥”。

### 5.3 密钥遗失与跨设备

- 忘口令 = 备份永久不可解；UI 必须在设置口令时明确告知，不设后门。
- 跨设备：只要同一 `backupFormatVersion` 与口令即可解密；不绑定设备/Google 账号。
- API Key 不在备份中；恢复后必须在设置页重新输入，Keystore 重新生成。
- 不建议把备份口令存进 App 或 Keystore；可提示用户使用密码管理器。

### 5.4 不加密 v2 的选项

- 仅作为“高级/调试”导出，必须二次确认“文件包含日记/对话等敏感信息”；
- 默认关闭；正式版不提供或深藏；
- 文件名/内容带 `plaintext` 标记，导入时同样高亮警告；
- 推荐 A：v2 默认加密，明文仅开发构建。

## 6. 限额与校验

### 6.1 硬限制（建议初值）

| 项 | 上限 | 处理 |
|---|---|---|
| 加密文件 | 64 MiB | 读取前 stat 检查，超限拒绝 |
| 解密后载荷 | 48 MiB | 解密前按密文长度预估；解密后再次检查 |
| 总行数 | 200,000 | 解析时累计 |
| 单表行数 | 100,000 | 解析时累计 |
| 单 TEXT 字段 | 1 MiB（journal body 仍受 20,000 字业务限制） | 超限拒绝 |
| 单 JSON 字段 | 2 MiB | 超限拒绝 |
| 数组元素（如 highlights） | 5,000 | 超限拒绝 |
| `id`/时间戳 | 正整数/有限数 | 非法拒绝 |
| 字符串编码 | UTF-8 | 解码失败拒绝 |

> 这些是**防御性上限**，不是产品目标；G4A-1 用真实 QA 数据校准，G4A-3 复核。

### 6.2 完整性校验层次

1. 文件级：size、前 4 KiB 头部、`format`、`backupFormatVersion`；
2. 加密级：KDF 参数范围、salt/nonce 长度、AEAD tag；
3. 载荷级：`payloadSha256`、`counts` 与数组长度、`perTableSha256`；
4. 结构级：每行必填字段/类型/枚举/长度/有限数；
5. 引用级：goals 父子、checkins→goals、proposals→messages/goals、signals→journals、memories→signals/journals、insight/weekly 版本；
6. 业务级：`ANALYZING` 重置、`weekly_ai_reviews` 不恢复、consent 不恢复；
7. 恢复后：行数/摘要/采样逐项比对，失败回滚。

任一层失败都**不得**进入数据库写入。

## 7. v1 文件处理策略

**推荐：识别但不恢复。**

| 行为 | 说明 |
|---|---|
| 识别 | `format=mengyalife-backup` 且 `schemaVersion=1` |
| 展示 | “旧版三表导出（日记/目标/进展），不是完整备份” |
| 恢复 | **禁止**；不提供“尽力导入” |
| 原因 | 缺 7 张表与设置，静默部分恢复会造成“看起来成功但丢数据” |
| 可选 | 只读“查看摘要/校验”；未来如要做有界兼容，必须单独设计并显示“只恢复三表” |

替代方案（不推荐）：把 v1 转成 v2 的三表子集再恢复。它仍然缺对话/草案/线索/设置，且用户会误以为完整；若未来要做，必须命名 `legacy-partial-import` 并在预览中列出缺失范围。

## 8. 兼容性矩阵

| 备份 | 当前 reader | 策略 |
|---|---|---|
| v1 `schemaVersion=1` | 识别 | 只读提示，不恢复 |
| v2 `backupFormatVersion=2`，`dbSchemaVersion=11` | 支持 | 完整校验/恢复 |
| v2，`dbSchemaVersion<11` | 支持（有界） | 字段映射 + 默认值；缺字段显式警告；不静默 |
| v2，`dbSchemaVersion>11` | 拒绝 | 提示升级 App |
| v2，`requiredFeatures` 含未知 | 拒绝 | 不猜测 |
| v2，`backupFormatVersion>2` | 拒绝 | 提示升级 App |
| 跨包（QA↔正式） | 拒绝 | `generator.appPackage`/`qaBuild` 必须一致 |
| 篡改/截断/超大 | 拒绝 | 见 §6 |

## 9. 导出流程（设计）

```text
用户点击“导出完整备份”
  → 读取设置快照（theme/weekly/ai endpoint/model）
  → Room 单事务读取 10 张表（一致快照，按 id 排序）
  → 组装 v2 载荷 + counts + perTableSha256 + payloadSha256
  → 口令加密（AES-GCM）
  → 写 app-private 临时文件（noBackupFilesDir/backup-tmp）
  → 用户选择 SAF 目标
  → 流式写入 + flush + fsync（若可用）
  → 回读 → 解密 → 校验 payloadSha256/counts
  → 成功提示；失败删除临时文件，不谎报成功
```

关键点：

- Room 读取在单事务中；设置读取不在同一事务，需前后比对或维护锁；若设置变化，重试/提示。
- 不把待导出明文长期落盘；临时文件在 `noBackupFilesDir`，完成/失败即删。
- SAF 目标写入可能被中断；回读校验失败时提示“文件可能不完整”，不显示成功。
- 不自动上传、不自动分享。

## 10. 需要用户决策的 A/B

### A. v2 是否默认加密

- **A1（推荐）**：默认必须口令加密；明文仅调试构建。
  - 优点：敏感数据不裸奔；跨设备可用；与“本机优先”一致。
  - 代价：用户忘口令无法恢复；导出多一步。
- **A2**：默认明文，可选加密。
  - 优点：简单；代价：SAF/云盘泄露风险高，不推荐。

### B. 派生缓存是否纳入

- **B1（推荐）**：核心 7 表 + 派生 2 表（insight/weekly review）纳入；`weekly_ai_reviews` 只归档。
  - 优点：完整；恢复后派生数据可按版本失效。
  - 代价：文件更大；需 stale 规则。
- **B2**：只备份核心 7 表，派生数据重建。
  - 优点：更小、更确定；代价：历史 AI 文案/周回顾丢失。

### C. v1 处理

- **C1（推荐）**：只识别、不恢复。
- **C2**：有界兼容（只恢复三表，明确警告）。
  - 代价：用户可能误以为完整；不推荐。

### D. AI endpoint/model

- **D1（推荐）**：备份 endpoint/model（不含 Key），恢复后仍要求重新输入 Key；不自动启用 AI。
- **D2**：完全不备份 AI 配置；恢复后从零配置。
  - 优点：最少元数据；代价：换机多一步。

## 11. 与恢复设计的衔接

- v2 的 `backupFormatVersion`/`dbSchemaVersion`/`requiredFeatures` 是恢复器的输入契约。
- `weekly_ai_reviews` 归档但不恢复；恢复器必须在预览中说明。
- 任何“缺失字段/未知版本/跨包/超限”都在写 DB 前拒绝。
- 参见 `G4A0-RESTORE-TRANSACTION.md` 与 `G4A0-SECURITY-PRIVACY.md`。
