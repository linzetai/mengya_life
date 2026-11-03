# 数据导出格式说明（Mengya Life · Gate 2C）

> 版本：导出格式 `schemaVersion = 1`，数据库 `schemaVersion = 2`，应用 `0.5.0-g2c`
> 实现：`app/src/main/java/com/mengyalife/app/data/export/ExportJson.kt`、
> `DataExportRepository.kt`、`ui/settings/ExportViewModel.kt`

---

## 1. 能力边界

| 能力 | 本轮状态 |
|---|---|
| 导出日记 / 长期目标 / 阶段目标 / 短期行动 / 进展记录 | ✅ 已实现 |
| 导出文件可解析、可逐项校验 | ✅ 已实现（写后回读 + 与数据库快照比对） |
| 系统文件选择器（SAF）选择保存位置 | ✅ 已实现，**不申请存储权限** |
| 自动上传 / 自动分享 | ❌ 不实现，代码里没有网络或分享调用 |
| 导入恢复 | ❌ 未实现；设置页明确写「导入恢复尚未实现」 |
| 导出 API Key / 账号 / 设备标识 / 设置项 | ❌ 不导出（见 §6） |

导出是**用户主动触发**的：设置 → 数据与隐私 → 导出我的数据 → 阅读敏感信息提醒 →
选择保存位置 → 写入 → 回读校验。

---

## 2. 为什么不是直接复制 `.db`

数据库是 Room + WAL 模式，最新写入可能还在 `mengya-life.db-wal` 里。
直接复制 `.db`（或三个文件）会带来两类风险：

1. **漏数据**：只拷 `.db` 会丢掉 WAL 里尚未 checkpoint 的记录；
2. **撕裂**：复制过程中应用仍在写，可能得到「目标有了、进展没了」的半写状态。

本实现改为：

```text
RoomDatabase.withTransaction {
    journalDao.loadAll()        // 日记
    goalDao.loadAllGoals()      // 三层目标
    goalDao.loadAllCheckins()   // 进展记录
}
```

三条 SQL 在**同一个数据库事务**里执行，看到的是同一个数据库版本；
序列化在事务返回之后进行，文件里不会出现跨表不一致。
代码里没有任何 `File.copy`、`openFileOutput` 或 `.db` 路径拼接。

---

## 3. 顶层结构

```json
{
  "format": "mengyalife-backup",
  "schemaVersion": 1,
  "exportedAt": 1793676687950,
  "exportedAtIso": "2026-11-03T03:31:27.950Z",
  "app": {
    "name": "Mengya Life",
    "package": "com.mengyalife.app",
    "versionName": "0.5.0-g2c",
    "versionCode": 5
  },
  "database": { "engine": "room", "schemaVersion": 2 },
  "counts": { "journalEntries": 2, "goals": 3, "goalCheckins": 3 },
  "data": {
    "journalEntries": [],
    "goals": [],
    "goalCheckins": []
  }
}
```

| 字段 | 含义 |
|---|---|
| `format` | 固定 `mengyalife-backup`，防止把任意 JSON 当备份 |
| `schemaVersion` | **导出格式**版本；本文件为 1 |
| `exportedAt` | 导出时刻，Unix Epoch 毫秒（UTC 语义） |
| `exportedAtIso` | 同一时刻的 ISO-8601 字符串，便于人工阅读 |
| `app` | 生成文件的真实应用包名与版本（QA 包会是 `...qa`） |
| `database.schemaVersion` | 生成文件时的 Room 结构版本（当前 2） |
| `counts` | 三个数组的条数；校验器会与实际数组长度比对 |

时间约定与数据库一致：**所有时间戳都是 Unix Epoch 毫秒**，不带时区；
展示时才按设备时区格式化。日期类字段（`dueDate`）是 UTC 零点。

---

## 4. 数据表

### 4.1 `data.journalEntries[]`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 日记自增主键 |
| `title` | string \| null | 可选标题；空标题在入库时归一为 null |
| `body` | string | 正文（必填，已 trim） |
| `createdAt` | number | 创建时间（Epoch 毫秒） |
| `updatedAt` | number | 最后编辑时间（Epoch 毫秒） |

### 4.2 `data.goals[]`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 目标主键 |
| `title` | string | 标题 |
| `description` | string \| null | 描述 |
| `level` | string | `LONG_TERM` / `STAGE` / `ACTION` |
| `parentId` | number \| null | 父目标 id；长期目标为 null |
| `domain` | string | `HEALTH` / `CAREER` / `WEALTH` / `LIFE` / `LEARNING` |
| `status` | string | `NOT_STARTED` / `IN_PROGRESS` / `DONE` / `PAUSED` / `CANCELLED` |
| `startDate` | number \| null | 开始日期（Epoch 毫秒，UTC 零点） |
| `dueDate` | number \| null | 截止日期（Epoch 毫秒，UTC 零点） |
| `createdAt` / `updatedAt` | number | 创建 / 更新时间 |

父子关系原样导出：阶段目标的 `parentId` 指向长期目标，短期行动的 `parentId`
指向阶段目标。校验器会检查父目标存在且层级合法。

### 4.3 `data.goalCheckins[]`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 进展主键 |
| `goalId` | number | 关联目标 id（必须存在于 `goals[]`） |
| `recordedAt` | number | 记录时间（Epoch 毫秒） |
| `note` | string | 文字说明（必填） |
| `metricValue` | number \| null | 可选数值；非有限数（NaN/Infinity）不会写出 |
| `metricUnit` | string \| null | 可选单位，例如 `次/周`、`kg` |
| `source` | string | 当前固定 `manual`（用户手动记录） |

---

## 5. 校验规则（写后回读）

写入完成后，应用立即从同一个 `content://` URI 读回文本并解析，逐项比对：

1. `format`、`schemaVersion`、`exportedAt` 合法；
2. 三个数组都能解析，且与 `counts` 一致；
3. 日记 id 唯一、正文非空、时间戳 > 0；
4. 目标 id 唯一、层级合法、父目标存在、层级逐级嵌套；
5. 进展 id 唯一、`goalId` 能在目标里找到、`source` 存在、
   `metricValue` 为 null 或有限数；
6. 与写入前的数据库快照比对：三类 id 集合、目标父子关系、进展→目标关联，逐项一致。

只有全部通过，界面才会显示「导出完成并已校验：日记 N 条 · 目标 N 个 · 进展 N 条」。
任一项失败会显示「导出文件校验失败：…」，不会谎报成功。

JVM 单元测试 `app/src/test/java/com/mengyalife/app/data/ExportTest.kt` 覆盖了：
完整导出、计数被篡改、孤儿进展、层级不一致、历史 NaN 脏数据、空库导出、
文件名格式、非 JSON 文本。

---

## 6. 隐私与安全

- 文件可能包含**日记正文**等敏感个人信息；导出前有醒目提醒，要求用户妥善保管。
- 应用**没有网络权限**，不会上传；没有分享 Intent，不会自动分享。
- 文件里没有 API Key、账号、设备标识、IP、设置项或主题偏好。
- 导出到用户通过 SAF 选择的位置；应用不申请 `READ/WRITE_EXTERNAL_STORAGE`。
- 单元测试断言导出文本不包含 `apiKey` / `api_key` / `secret` / `token` / `password`。

---

## 7. 版本演进约定

- **加字段**（向后兼容）保持 `schemaVersion = 1`；
- **删字段 / 改语义 / 改类型**必须把 `schemaVersion` + 1，并提供迁移说明；
- 数据库结构变化时同步更新 `database.schemaVersion`；
- 导入功能实现前，不承诺旧文件可以导回；本轮只承诺「可解析、可校验、内容完整」。
