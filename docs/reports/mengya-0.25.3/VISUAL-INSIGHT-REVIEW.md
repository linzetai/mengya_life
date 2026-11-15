# 洞察详情：实现期视觉复核

## 检查范围与状态

已逐张 read_image 检查冻结的 320/360/412dp × 浅色/深色 × 字体 1.0/1.3 × 收起/展开/展开末端，共 36 张原图；另单独读取了归档的旧暗色容器问题图作对照。未制作缩略图、未操作设备、未修改代码。

这是实现者的实现期视觉复核，不是用户独立验收。截图能证明内容在对应状态可见，不能替代按钮点击或授权失效路径测试。下方先保留 320dp 首批记录，再追加 360/412dp 的 24 张记录；额外独立事实样例缺口仍保留。

**320dp 限定结论：层级与换行正常；四组展开末端均可完整看到来源、主 CTA、安全说明与关闭。最终暗色容器为深绿，旧紫灰容器问题已关闭。设备悬浮人物仍覆盖部分正文/日期，需作为截图环境问题保留，不能宣称这 12 张完全无遮挡。**

## 七项检查

1. **留白：总体合理，标题略占空间。** 两行大标题清楚表达结论，但在 1.3 下使正文和依据需要更多滚动。关闭固定区域留白充足，未挤压按钮文本。没有从截图发现固定高度造成的正文消失。
2. **重复：本样例已消除逐字复述。** 展开显示“上述解释已引用 1 条记录事实，来源保留如下”，没有把解释里同一事实再抄一遍；两条来源分别为目标与进展记录，虽然标签同名，来源类型不同，不是重复证据。截图没有额外的独立 fact 样例，不能据此证明独立依据保留，见下节限定。
3. **主次：清楚。** 结论 → 推测/置信度 → 解释 → 展开依据 → 来源 → 讨论，阅读顺序稳定。深绿填充的“和顾问讨论”为唯一主 CTA，“目标/趋势/关闭”为次操作。主题修正后暗色对话框与按钮均为绿色系，不再出现紫灰默认容器。
4. **操作：末端视觉可达。** 收起态主 CTA 和关闭可见；展开首屏因内容变长，不一定显示主 CTA，但四组 expanded-bottom 均能看到完整讨论按钮和关闭，关闭不随正文滚出。这里只确认截图中的可见落点，不声称按钮点击成功。
5. **截断：未确认组件内硬裁切。** 1.3 文字自然换行；收起态 1.3 的安全说明后半在滚动视口之外，展开末端两张 1.3 均完整显示该说明，证明并非固定高度永久丢失。展开首屏下缘内容被视口截住是滚动位置边界，不能误判为组件裁切。
6. **遮挡：存在设备叠加层干扰。** 右侧人物在收起/展开态压到解释末侧，在 1.0 末端压到日期，在 1.3 末端压到“来源保留如下”附近。无法从静态图证明叠加层归属或其他动画帧；建议验收图移开该层。关闭、主 CTA 文字未被人物遮挡。
7. **横溢与定位：未见左右越界。** 四组合标题、正文、日期、引用数量说明、来源与按钮均在容器内换行，无横向溢出。来源为友好中文类型名，没有裸露技术类型/独立记录 ID；定位见下面的 12 张逐图记录。

## 依据保留与主题闭环

- 展开后保留完整数据区间与生成时间，以及“1 条记录事实”引用说明。
- 末端保留两条来源：“目标 · QA 样例：长期阅读”和“进展记录 · QA 样例：长期阅读”，并未因正文去重而删掉来源。
- **独立依据视觉覆盖不足：** 本组样例只有解释已完整引用的一条事实，未出现“另有一条未在解释内重复的事实”。因此本报告只确认该样例去重和来源保留，不能声称已在真机截图验证所有独立事实。纯逻辑回归测试对 additionalInsightFacts 的独立证据保留另有覆盖，不能把该单元测试冒充视觉覆盖。
- 已实际读取[旧紫灰问题原图](<docs/reports/mengya-0.25.3/screenshots/review-r2/dark-dialog-default-container.png>)，与本轮 6 张暗色对照，确认最终容器已切换为深绿；本报告不归因到未经本轮源码审查的具体 theme 角色。

## 320dp 逐张记录

| 原图 | 逐张观察 |
| --- | --- |
| [浅色 1.0 收起](<docs/reports/mengya-0.25.3/screenshots/after-320-light-10-insight-collapsed.png>) | 标题两行，解释与展开操作完整；主 CTA、安全说明、关闭完整；人物覆盖解释右侧。 |
| [浅色 1.0 展开](<docs/reports/mengya-0.25.3/screenshots/after-320-light-10-insight-expanded.png>) | 区间、引用数量与两条来源可见；主 CTA 尚在视口外，关闭仍可见；人物压解释末侧。 |
| [浅色 1.0 展开末端](<docs/reports/mengya-0.25.3/screenshots/after-320-light-10-insight-expanded-bottom.png>) | 两条来源、目标/趋势、讨论主 CTA、安全说明及关闭完整；人物覆盖区间日期右侧。 |
| [浅色 1.3 收起](<docs/reports/mengya-0.25.3/screenshots/after-320-light-13-insight-collapsed.png>) | 标题自然两行、正文增加行数；讨论与关闭完整；安全说明后半需滚动；人物压正文。 |
| [浅色 1.3 展开](<docs/reports/mengya-0.25.3/screenshots/after-320-light-13-insight-expanded.png>) | 日期三行自然换行；引用说明后半及来源在屏外；关闭完整，需看末端图确认后续。 |
| [浅色 1.3 展开末端](<docs/reports/mengya-0.25.3/screenshots/after-320-light-13-insight-expanded-bottom.png>) | 日期、引用说明、两条来源与主 CTA/关闭完整；安全说明完整两行；人物压引用说明行末。 |
| [深色 1.0 收起](<docs/reports/mengya-0.25.3/screenshots/after-320-dark-10-insight-collapsed.png>) | 容器深绿；排版与浅色 1.0 一致，主 CTA、安全说明、关闭完整；人物压解释。 |
| [深色 1.0 展开](<docs/reports/mengya-0.25.3/screenshots/after-320-dark-10-insight-expanded.png>) | 区间、引用数量与两条来源可见；主 CTA 屏外、关闭可见；来源无横溢。 |
| [深色 1.0 展开末端](<docs/reports/mengya-0.25.3/screenshots/after-320-dark-10-insight-expanded-bottom.png>) | 两条来源、主 CTA、安全说明及关闭完整；人物覆盖日期右侧；无暗色特有裁切。 |
| [深色 1.3 收起](<docs/reports/mengya-0.25.3/screenshots/after-320-dark-13-insight-collapsed.png>) | 容器深绿；讨论/关闭完整；安全说明后半在滚动区域外；人物压正文。 |
| [深色 1.3 展开](<docs/reports/mengya-0.25.3/screenshots/after-320-dark-13-insight-expanded.png>) | 日期自然换行；引用说明下半与来源屏外，关闭可见；布局未横溢。 |
| [深色 1.3 展开末端](<docs/reports/mengya-0.25.3/screenshots/after-320-dark-13-insight-expanded-bottom.png>) | 来源、讨论、安全说明和关闭完整；引用说明处有人物遮挡；最终深绿主题保持一致。 |

## 遗留与后续覆盖

- 环境 P2：悬浮人物确实覆盖了若干文字；不是字体裁切，但对无障碍阅读和验收截图有影响。应移开后重拍，或在最终报告显式标注。
- 覆盖缺口：未在本组截图验证额外独立事实、来源失效重评、授权变化后的证据清除、讨论按钮点击后的路由；这不是对现有实现失败的推断。
- 360/412dp 已补足 24 张原图逐图检查，结论见下一节；这不补足独立事实或失效重评场景。

## 360/412dp 追加复验

已对每个新增文件单独调用 read_image，24 张均实际读取，包括部分展开/末端图像相同的组合；未以文件名或另一主题推断。

### 七项复核与末端可达性

- 留白：360/412dp 容器居中、左右有边距，1.0 展开态基本可以完整容纳本样例。1.3 的标题仍为两行、日期为三行，需要滚动；空间占用变大但未导致永久丢失。
- 重复：仍是单条已引用事实的数量说明、目标与进展记录两种友好来源；没有逐字重复完整规则事实。
- 主次：结论/解释/依据/来源/讨论的层级稳定，讨论为唯一填充主操作，关闭与目标/趋势为文字操作；最终暗色 12 张均为深绿容器。
- 操作：新增 8 组末端图全部能完整看到两条来源、主 CTA、安全说明与关闭。360/412dp 的 1.0 展开通常已全页可见，末端不一定产生位移；1.3 的展开首屏来源或主 CTA 可能被滚动视口下缘截住，但末端图确认其完整出现。
- 截断：360 / 1.3 展开首屏的第二条来源处于视口下沿；412 / 1.3 展开首屏的主按钮下圆角/安全说明处于视口下沿。对应末端图均完整，属于滚动位置边界而非固定高度裁切。1.3 末端正文顶部会从行中切入，也是正常视口边界。
- 遮挡：360dp 的人物主要在容器右边缘外侧或内边距，个别动画小装饰触及正文右侧边缘；412dp 人物处在对话框外侧，没有发现其盖住新增图的主 CTA、关闭或完整来源文字。320dp 已记录的实际正文遮挡仍是遗留，不能因此关闭。
- 横溢：新增 24 张未见标题、正文、日期、来源或操作文字左右越界；无主题特有文字重叠。

### 360dp 逐张记录

| 原图 | 逐张观察 |
| --- | --- |
| [浅色 1.0 收起](<docs/reports/mengya-0.25.3/screenshots/after-360-light-10-insight-collapsed.png>) | 标题两行；解释、展开入口、讨论、安全说明及关闭完整；人物靠右缘。 |
| [浅色 1.0 展开](<docs/reports/mengya-0.25.3/screenshots/after-360-light-10-insight-expanded.png>) | 区间/引用说明/两条来源和全部操作可同屏完整看到，无需把 CTA 挤出。 |
| [浅色 1.0 展开末端](<docs/reports/mengya-0.25.3/screenshots/after-360-light-10-insight-expanded-bottom.png>) | 与展开图同一完整布局，来源、讨论、安全说明与关闭均完整。 |
| [浅色 1.3 收起](<docs/reports/mengya-0.25.3/screenshots/after-360-light-13-insight-collapsed.png>) | 解释自然增加行数；讨论、安全说明与关闭仍完整，无横溢。 |
| [浅色 1.3 展开](<docs/reports/mengya-0.25.3/screenshots/after-360-light-13-insight-expanded.png>) | 日期三行，引用数量完整，第二条来源位于视口下沿；关闭固定可见，CTA需向下。 |
| [浅色 1.3 展开末端](<docs/reports/mengya-0.25.3/screenshots/after-360-light-13-insight-expanded-bottom.png>) | 两条来源、讨论、安全说明、关闭全部完整；顶部行中边界为正文滚动位置。 |
| [深色 1.0 收起](<docs/reports/mengya-0.25.3/screenshots/after-360-dark-10-insight-collapsed.png>) | 深绿容器；解释与所有操作完整；未见暗色特有裁切。 |
| [深色 1.0 展开](<docs/reports/mengya-0.25.3/screenshots/after-360-dark-10-insight-expanded.png>) | 区间、引用说明、来源与主 CTA/安全说明/关闭完整同屏。 |
| [深色 1.0 展开末端](<docs/reports/mengya-0.25.3/screenshots/after-360-dark-10-insight-expanded-bottom.png>) | 与展开布局一致，全部末端内容完整；深绿维持一致。 |
| [深色 1.3 收起](<docs/reports/mengya-0.25.3/screenshots/after-360-dark-13-insight-collapsed.png>) | 讨论、安全说明和关闭完整，正文自然换行，无左右越界。 |
| [深色 1.3 展开](<docs/reports/mengya-0.25.3/screenshots/after-360-dark-13-insight-expanded.png>) | 日期、引用数量完整，第二条来源下半在视口边缘；关闭可见，后续需看末端。 |
| [深色 1.3 展开末端](<docs/reports/mengya-0.25.3/screenshots/after-360-dark-13-insight-expanded-bottom.png>) | 来源、讨论、安全说明及关闭完整；未见紫灰回归或新重叠。 |

### 412dp 逐张记录

| 原图 | 逐张观察 |
| --- | --- |
| [浅色 1.0 收起](<docs/reports/mengya-0.25.3/screenshots/after-412-light-10-insight-collapsed.png>) | 对话框居中且侧边距充足；解释及全部收起态操作完整；人物在框外。 |
| [浅色 1.0 展开](<docs/reports/mengya-0.25.3/screenshots/after-412-light-10-insight-expanded.png>) | 区间、引用数量、两条来源、讨论、安全说明与关闭全部完整同屏。 |
| [浅色 1.0 展开末端](<docs/reports/mengya-0.25.3/screenshots/after-412-light-10-insight-expanded-bottom.png>) | 与展开图相同完整布局；无需位移也可见主 CTA 与关闭。 |
| [浅色 1.3 收起](<docs/reports/mengya-0.25.3/screenshots/after-412-light-13-insight-collapsed.png>) | 标题两行，正文自然换行；讨论、安全说明、关闭完整。 |
| [浅色 1.3 展开](<docs/reports/mengya-0.25.3/screenshots/after-412-light-13-insight-expanded.png>) | 引用说明与两条来源完整；主 CTA下圆角/安全说明位于滚动视口下方，关闭可见。 |
| [浅色 1.3 展开末端](<docs/reports/mengya-0.25.3/screenshots/after-412-light-13-insight-expanded-bottom.png>) | 两条来源、主 CTA、安全说明及关闭完整；不存在前图看似按钮底部裁切的永久丢失。 |
| [深色 1.0 收起](<docs/reports/mengya-0.25.3/screenshots/after-412-dark-10-insight-collapsed.png>) | 深绿容器；解释和全部操作完整，人物在框外。 |
| [深色 1.0 展开](<docs/reports/mengya-0.25.3/screenshots/after-412-dark-10-insight-expanded.png>) | 区间/引用说明/来源/讨论/安全说明/关闭完整同屏，无横溢。 |
| [深色 1.0 展开末端](<docs/reports/mengya-0.25.3/screenshots/after-412-dark-10-insight-expanded-bottom.png>) | 与展开图相同完整布局；来源与主次操作完整。 |
| [深色 1.3 收起](<docs/reports/mengya-0.25.3/screenshots/after-412-dark-13-insight-collapsed.png>) | 标题两行，解释自然换行；讨论、安全说明和关闭完整。 |
| [深色 1.3 展开](<docs/reports/mengya-0.25.3/screenshots/after-412-dark-13-insight-expanded.png>) | 两条来源完整，CTA底部在视口边缘，安全说明需继续滚动；关闭可见。 |
| [深色 1.3 展开末端](<docs/reports/mengya-0.25.3/screenshots/after-412-dark-13-insight-expanded-bottom.png>) | 两条来源、主 CTA、安全说明、关闭均完整；主题保持深绿，无新增遮挡。 |

### 全矩阵限定结论

36 张均完成逐张原图检查。12 个宽度/主题/字号组合的末端都保留本样例两条友好来源，主 CTA、安全说明与关闭在相应末端图完整可见；未确认组件永久裁切、横溢或主次操作重叠。1.3 展开首屏需滚动不等于不可达。

独立事实视觉样例仍未覆盖；来源失效重评和按钮点击成功尚由主代理另行实际验证，本报告不将其标为已通过。320dp 设备人物覆盖正文/日期的问题继续保留为环境项；这是实现期有条件的视觉复核，不是用户独立验收。
