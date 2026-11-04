# Gate 3E-2.1 报告 · AI 周回顾隐私竞态修复

| 项 | 值 |
|---|---|
| 版本 | 0.14.1-g3e2-1（versionCode 15） |
| 数据库 | Room v11（v10→v11） |
| 结论 | **PARTIAL**：P0 请求前/结果隔离 JVM PASS；真实 AI、状态 UI、全量回归 NOT VERIFIED |

## A 请求前竞态

| 项 | 状态 | 证据 |
|---|---|---|
| 撤权/关闭类别/切换 endpoint/修改数据前停止 | **PASS（JVM）** | WeeklyAiTest：engine.calls=0，PENDING 释放 |
| 请求中数据/授权/端点变化不写 READY | **PASS（JVM）** | beforeComplete 钩子 |
| 自动 AI 关闭时请求前停止 | **PASS（代码）** | autoEnabled 校验；未单独 JVM |
| 已发送字节不可撤回 | **PASS（文档/设计）** | 结果隔离，不承诺撤回 |

## B 状态/预算

| 项 | 状态 | 说明 |
|---|---|---|
| 并发自动/手动只发一次 | **PASS（JVM）** | period_key + markSent |
| PENDING 崩溃恢复 | **PASS（代码）** | 未发送 PENDING 删除；SENT/FAILED 消耗预算 |
| 超时/失败不无限重试 | **PASS（代码）** | 单次预留，FAILED 不再调用 |
| generateAiNow 退出 generating | **代码已改** | **NOT VERIFIED**：未做 UI 超时/取消真机 |
| FAILED/PENDING/READY 展示 | **代码已改** | **NOT VERIFIED**：未走完真实状态 |
| 模型配置/授权/数据版本缓存 | **PASS（代码）** | configHash + versions；未集成真机 |
| 输入上下文限制/maxTokens | **PASS（代码）** | facts ≤20、text ≤300、输出 900；无费用实测 |
| 提示词示例合法 JSON | **PASS（JVM）** | prompt 示例解析测试 |
| 信息不足无来源 | **PASS（JVM）** | WeeklyAiTest |

## C 验收

| 项 | 状态 |
|---|---|
| A 请求前四类变化 engine.calls=0 | PASS（JVM） |
| B 请求中变化无 READY | PASS（JVM） |
| C 成功解析 factIds/sources | PASS（JVM） |
| D 网络超时 UI/预算 | NOT VERIFIED |
| E 自动+手动并发一次 | PASS（JVM） |
| F QA 真实模型 | **NOT VERIFIED**：无 QA 真实配置；未取得真实成功 |
| G 0.14 全量 UI 回归 | **NOT VERIFIED**：仅 diary 25/25；其余未执行/中断 |
| H v10→v11 安装保留 | PASS：设备 user_version=11，日记 2 篇 |

## D 交付

- Debug：`cceaafb51bb7924a83d9b1ccf0337448b63222eb8f869a39878fcdbb294fc95d` · 12,134,241 B
- QA：`718d1939a8a803bba4e7ce90791d74a572f08303731cc5afdb642609fef7e1c1` · 12,134,244 B
- 设备 base.apk 哈希一致；Room v11。
- JVM 241/241；XML 在证据包。

## E 未验证清单

1. 真实 QA 模型请求成功与延迟事实。
2. UI 超时/取消后 generating、状态、按钮恢复。
3. 0.14.1 全量 UI 回归（仅 diary PASS）。
4. 自动/手动并发真机、授权撤销/切换 endpoint 真机。
5. 输入 token/费用实际上限（只有代码上限）。
6. 通知/周回顾页面在 0.14.1 的真机更新。
7. 生产测试 weekly/AI 历史行未清理（无删除入口）。
