# Gate 2/3 UI 实现报告

## 范围

本附录只记录 Gate 2/3 子任务边界：TodayScreen、TrajectoryScreen、InsightSection（仓库没有独立 InsightScreen）、InsightViewModel 的展示状态及新增 UI helper/单元测试。最终集成还包含顾问、计划表单、主题容器角色与版本升级，见 [0.25.3 总体验证](<mengya-0.25.3/UI-VERIFICATION.md>)；应用代码已提交为 `0123120f173c4ba4b75ac23533a29d0b1dfd29c6`。未重构核心 AI 授权、恢复或备份。

## 实现

- 今天：唯一主焦点是根据真实状态选出的「下一步」及主按钮；观察降为紧凑暖白卡，移除大深绿装饰背景和大号引语。建议入口准确写作「查看解释与依据」，不伪装成直接讨论。概览与辅助操作可换行。
- 洞察：结论 → 解释 → 展开依据与来源 → 讨论。结论列表去重；解释已逐字引用的事实不再复述，用引用数量说明代替，其余不同事实完整保留；来源类型使用中文友好名称，不暴露内部类型/记录 ID。缺失关联依据不拿无关事实兜底。
- 失效：同一授权下已展示的报告仅作为历史依据保留，明确标旧，禁止旧建议直接讨论，提供重新评估。授权撤销、类别变动、恢复引起 epoch 变化、模型配置变化均清空 UI 历史证据。旧报告不从数据库额外加载，重启后不承诺恢复历史展示。
- 轨迹：先真实变化摘要，再行动状态，再数值记录，最后回顾与原始记录。实际累计快照变化优先于次数；增量不写成累计下降；日记数量不等于成长；执行记录不等于实际执行。行动优先显示进行中，默认两项，可展开全部；顶部入口实际滚到行动区。
- 修正范围语义：近月/近三月日记不再始终统计近周；最近进展限定当前范围；未来时间记录不混入；更多组数按筛选后结果计算。
- 响应式：无固定高度补丁；事实文字垂直排布，目标标题不单行截断，数值/统计/辅助操作可换行。主操作采用统一 MorrowPrimaryButton（52dp 最小高度、14dp 圆角，由共享组件提供）；维持暖白与深绿语义。

## 重新评估接线核实

1. TodayScreen 的 onGenerateInsight 是必填回调，无默认空实现。
2. InsightDetailDialog 的 onReevaluate 是必填回调，无默认空实现；Today 传入 onGenerateInsight，InsightSection 传入 onGenerate。
3. MengyaLifeApp 将 onGenerateInsight 接到 insightViewModel::generate。
4. InsightViewModel.generate 实际调用 service.generate(force = true)，不是 refresh、关闭弹窗或仅显示提示。
5. 加载状态禁用重新评估按钮；失效/缺失依据时不提供讨论旧行动。

## 测试

新增 Gate23PresentationTest 共 8 项：

1. 实际快照变化优先于记录次数。
2. 增量不被描述为累计下降。
3. 日记遵循选择区间、包含边界并排除未来记录。
4. 单点不生成趋势结论。
5. 授权撤销/epoch 变化/未知证据 epoch 时清除历史依据。
6. 解释已引用事实不重复、不同证据不丢失、原始列表不改动。
7. 友好来源名不展示内部记录 ID。
8. 重复结论合并而不改报告证据。

主代理已确认最终增量 `:app:testQaUnitTest :app:assembleQa :app:assembleQaAndroidTest` 全部 exit 0，涵盖最后的去重复与授权 guard 补丁及全部 8 项展示回归测试。最终 QA 包已安装；320dp / 字体 1.3 真机截图矩阵由主代理继续统一执行，具体结果见主代理验收报告，未将源码检查冒充真机通过。
