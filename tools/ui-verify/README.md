# 真机 UI 验证脚本（Gate 2C）

这些脚本在**真机**上驱动 `萌芽人生` 跑验收流程，定位方式全部是
**Compose `testTag` → Android resource-id** 或**文本**，不使用固定坐标。

## 安全模型：正式包与 QA 包隔离

| 包 | applicationId | 数据库 | 用途 |
|---|---|---|---|
| 正式包 | `com.mengyalife.app` | `mengya-life.db` | 只读冒烟，**不清数据** |
| QA 包 | `com.mengyalife.app.qa` | `mengya-life-qa.db` | 全部写/删测试 |

- 所有会写数据的脚本默认 `MENGYA_PKG=com.mengyalife.app.qa`，并通过 `require_qa()` 做包名 + `-qa` + debuggable 硬校验（不再提供正式包覆盖开关）；
- 正式包上只有 `verify_prod_smoke.py`，它不清数据、不新建日记/目标、不切主题；
- 清空 QA 数据用 `pm clear com.mengyalife.app.qa`，只影响测试包。

## 前置

```sh
export DEV=<手机IP>:<无线调试端口>
adb connect "$DEV"
adb devices                       # 状态应为 device

# 安装两个包
adb -s "$DEV" install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s "$DEV" install -r -t app/build/outputs/apk/qa/app-qa.apk
```

正式包里应保留本轮验收数据（2 条 v1 迁移日记），
`verify_prod_smoke.py` 会逐字段校验它们没有被改动。

## 运行

推荐使用统一入口（Gate 3F）：

```sh
./scripts/verify.sh --profile dev --changed  # 日常本地快回路：静态/JVM/必要编译，不连真机
./scripts/verify.sh --changed                # standard：按 Git 影响执行要求的 QA 真机
./scripts/verify.sh --level gate             # 全量 QA + 正式包只读冒烟
```

`dev` 报告为 `LOCAL PASS (device deferred)`，`fullLevelSatisfied=false`；它只加快开发反馈，不能代替 L1/L2/Gate。

兼容入口（等价于 `--all --level gate`）：

```sh
./tools/ui-verify/run_all.sh
```

单跑：

```sh
MENGYA_PKG=com.mengyalife.app.qa   python3 tools/ui-verify/verify_settings.py
MENGYA_PKG=com.mengyalife.app.qa   python3 tools/ui-verify/verify_diary.py
MENGYA_PKG=com.mengyalife.app.qa   python3 tools/ui-verify/verify_theme.py
MENGYA_PKG=com.mengyalife.app.qa   python3 tools/ui-verify/verify_navigation.py
MENGYA_PKG=com.mengyalife.app.qa   python3 tools/ui-verify/verify_goals.py
MENGYA_PKG=com.mengyalife.app.qa   python3 tools/ui-verify/verify_trends.py
MENGYA_PKG=com.mengyalife.app.qa   python3 tools/ui-verify/verify_dates.py
MENGYA_PKG=com.mengyalife.app.qa   python3 tools/ui-verify/verify_export.py
MENGYA_PKG=com.mengyalife.app      python3 tools/ui-verify/verify_prod_smoke.py
```

| 脚本 | 覆盖 | 是否改数据 |
|---|---|---|
| `verify_settings` | 过期文案扫描、导出入口、导入未实现提示、QA 标识、版本号 | 只读 |
| `verify_diary` | 空状态 / 空正文拦截 / 新建 / 编辑 / 重启 / 删除确认 | 清空 QA 数据 |
| `verify_theme` | 浅色 / 深色 / 跟随系统 / 重启保持（亮度断言） | 改 QA 主题 |
| `verify_navigation` | 五页切换、设置进出、返回行为 | 只读 |
| `verify_goals` | 三层目标、禁止跳级、进展、状态、编辑、删除影响、统计口径 | 清空 QA 数据 |
| `verify_trends` | 空 / 单点 / 多点 / 多单位、非法数值、演示区分隔 | 清空 QA 数据 |
| `verify_dates` | 日期选择器保存 / 重启展示 / UTC 零点 | 清空 QA 数据 |
| `verify_export` | 导出提醒 / SAF / JSON 完整性 / 与库逐项一致 | 清空 QA 数据 + 写 Downloads |
| `verify_prod_smoke` | 正式包旧数据、冷启动、设置文案、只读导出 | **只读**（除导出外部文件） |
| `verify_weekly_ai` | 周回顾 + AI 成功/失败/超时/中断/STALE/撤权/自动开关 | 清空 QA 数据 |
| `verify_migration` | v10 夹具 → Room 10→11 迁移、数据保留、幂等 | 清空 QA 数据 |
| `verify_qa_preflight` | QA 包名、`-qa` 版本、debuggable、QA_BUILD 界面标记 | 只读 |

截图默认写到 `docs/reports/logs/g2c-shots/`，导出样本写到
`docs/reports/logs/g2c-export/`，可用 `SHOT_DIR` / `EXPORT_OUT` 覆盖。

## 已知约束（都踩过）

1. **`ModalBottomSheet` / `DropdownMenu` / `AlertDialog` 是独立窗口**：
   `uiautomator dump` 只返回该窗口节点，列表内容不在里面。
2. **下拉菜单项没有 resource-id**（Popup 未继承 `testTagsAsResourceId`）：
   菜单项用文本点击（`tap_text`），其余用 `testTag`。
3. **软键盘会顶起底部导航**：输入后先 `KEYCODE_BACK` 关键盘再点底栏。
4. **目标 id 不要硬编码**：自增 id 会随删除继续增长，按 dump 动态获取。
5. **展开/收起是同一个按钮**：重复点击会收起，展开逻辑必须幂等。
6. **进展历史是长面板**：图表在面板内部，必要时在面板内上滑（`scroll_sheet`）。
7. **SAF 文件选择器**：确认后系统界面底部按钮文本是「保存」；
   导出文件名带秒级时间戳，不会覆盖旧文件。
8. 测试期间不要把手机切到别的应用（`uiautomator dump` 在 Chrome 上容易超时）。
