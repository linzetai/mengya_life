# 真机 UI 验证脚本

这些脚本在**真机**上驱动 `萌芽人生` 跑验收流程，定位方式全部是
**Compose `testTag` → Android resource-id** 或**文本**，不使用固定坐标。

## 前置

```sh
# 手机：开发者选项 → 无线调试 打开，记下 IP:端口
export DEV=192.168.x.x:<端口>
adb connect "$DEV"
adb devices                     # 状态应为 device

# 亮屏，否则 HyperOS 会拒绝安装/点击无效
adb -s "$DEV" shell input keyevent KEYCODE_WAKEUP
```

被测 APK：`app/build/outputs/apk/debug/app-debug.apk`

## 运行

| 脚本 | 覆盖 | 是否会改数据 |
|---|---|---|
| `verify_diary.py` | 日记新建 / 空正文拦截 / 编辑 / 删除确认 / 重启持久化 | 只增删自己创建的测试记录 |
| `verify_goals.py` | 长期→阶段→行动三层、层级合法性、进展、状态、编辑、删除影响范围、总览联动 | **会清空全部目标**（需开关） |
| `verify_theme.py` | 浅色/深色/跟随系统 + 重启保持（含亮度断言） | 只改主题偏好 |
| `verify_navigation.py` | 五页切换、设置进出、返回行为 | 不改数据 |

```sh
# 全部跑一遍
./tools/ui-verify/run_all.sh

# 单独跑（目标脚本会清库，必须显式确认）
MENGYA_TEST_DESTRUCTIVE=1 python3 tools/ui-verify/verify_goals.py
python3 tools/ui-verify/verify_diary.py
```

截图默认写到 `docs/reports/logs/verify-shots/`，可用 `SHOT_DIR` 覆盖。

## 已知约束（都踩过）

1. **`ModalBottomSheet` / `DropdownMenu` / `AlertDialog` 是独立窗口**：
   它们出现时，`uiautomator dump` 只会返回该窗口的节点，列表内容不在里面。
   断言前先等弹层关闭（见 `wait_sheet_closed`）。
2. **弹层内也要声明 `testTagsAsResourceId`**，否则内部的 testTag 不会变成 resource-id。
3. **软键盘会顶起底部导航**：输入后先 `KEYCODE_BACK` 关键盘再点底栏。
4. **目标 id 不要硬编码**：自增 id 会随删除继续增长，脚本按 dump 顺序动态获取。
5. **展开/收起是同一个按钮**：重复点击会把节点收起来，展开逻辑必须幂等。
6. 测试期间不要把手机切到别的应用（`uiautomator dump` 在 Chrome 上容易超时）；
   `harness.dump()` 会在超时后尝试把应用拉回前台。
