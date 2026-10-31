# CEE-TrainSpeed 审计报告：你的屎山 ↔ 原作者基线 ↔ 我的实现

> 2026-10-31。方法：你的仓库（`Create-Electro-Energetics-TrainSpeed`，只读）以原作者最后提交 `4828c34`（= 你给的 1.21.1 zip，逐文件 diff 验证一致）为基线，累计变更 **80 文件 / +11039 行**，逐文件读取，每段变化映射到《需求重审》编号，再逐条在重写树中验证。审计后**所有确认的缺口已合并进重写树并全量编译通过**（commit `7e1b52f`）。

## 一、审计结论总览

| 类别 | 数量 | 说明 |
|---|---|---|
| 你的变更文件 | 80 | 全部读取并归类 |
| 我原本已覆盖 | 41 | 同构实现（同需求自然同构） |
| 审计发现缺口并**已补** | 26 | 见第二节，按严重度排序 |
| 有意保持的不同实现 | 6 | 见第三节（每条都有理由，多数是"你的原话 vs 后期 AI 自己的决定"冲突时按你的原话） |
| 纯外观差异 | 7 | 见第四节（不影响行为验收） |

## 二、确认缺口（已全部合并进重写树）

### 严重（不改会直接违反你的需求或产生事故）

| # | 你的实现 | 对应需求 | 缺什么 | 已补为 |
|---|---|---|---|---|
| 1 | `CarriageFloorMixin` | E-07/M151「在车上像非列车一样自由移动疾跑跳跃」、历史"穿地板" | 站在行进车厢内=永久自由落体（原版 onGround 判定不认车厢地板，Create 碰撞器只在摩擦 ≤0.6 时接地）。我的树完全没有 | 同款：Entity.tick HEAD，地板带(0.75)内 onGround=true、清负向速度与跌落距离；客户端用同步面板判定电力列车 |
| 2 | `CarriageChunkProbeMixin` | M176/M177 高速区块加载、M185 橡皮筋 | Create 的 alignEntity 探测**前方 16 格**，高速时落在未加载区→服务端停车/客户端分离/拉回。我的树没有 | 同款：改探车厢自身锚点区块，省 4 个 Vec3 分配/车厢/tick |
| 3 | `TrainControlModeLogic.decide` 的 `emergencyArmed → DEAD_COAST` | **C-05「紧急制动不能停，无论有没有电」** | 我的 EB 掉到 28 m/s 以下时模式翻成 CREATE → EB 中断。这是会出事故的 bug | 同款：armed 期间强制保持面板模式，停稳后经既有路径交 Create |
| 4 | `sticky CREATE`（M100 Q1 你自己的回答：速度门控只在"电气化→Create"方向存在） | D-05 | 我的实现在 Create 模式超 28 会反向翻回 DEAD_COAST，把控制权从玩家手里抢走 | 同款：CREATE 粘滞，只有升弓才回来 |
| 5 | `releaseToCreate/retakeFromCreate` + `wasDriven` 清理 | B-14/B-15 | 我的树 Create 交接后 wasDriven 残留 → 再升弓后第一次无人就会误锁无人机制 | 同款：双沿转换方法，交接清 unmanned/wasDriven/警惕，回取落 COAST |
| 6 | 时刻表电动列车走牵引模型（`approachTargetSpeed` 包裹：功率受限加速 + 混合再生制动） | A-04/B-15（B-15 实现列明确含 approachTargetSpeed） | 我的树时刻表电动列车退回原版物理 | 同款：powered→模型斜坡+再生制动上报；unpowered→原版 |
| 7 | 客户端同步（maxSpeed/controlMode/manualFullSpeed 进包） | G-03/A-09 | 客户端没有电气模拟，我的速度条分母/0.75 豁免在专用服上全部失效 | 同款：SyncTrainPanelPacket 增加 powered/manualFullSpeed/designMaxSpeed/cruiseState/regen，TrainMixin/ElectricManualSpeed 客户端读同步面板 |

### 重要（行为质量）

| # | 你的实现 | 对应需求 | 已补为 |
|---|---|---|---|
| 8 | `RiderViewFollow`（K-17 后的坐姿补角版） | G-08/M203「转弯视角不动」 | 坐姿专补两个 Create 盲区：SeatEntity 间接着力、getViewYRot 完全不动时用位移方向推算；Create 已在转则绝不叠加。站立不跟随。未带诊断日志 |
| 9 | 双层下车锁（`Entity.stopRiding` 第二道 + `TeleportPendingAccessor`） | E-08、M185 | `wantsToStopRiding` 之外 stopRiding 也有旁路（你实测到过）；tp-ack 二次 absMoveTo 会把 ack 前落座的人拽回 → 落座跳过待确认传送者 |
| 10 | `KeyboardHandlerMixin` | M047（mod 按键霸占） | 驾驶时吞掉操纵台按键，其他 mod 同键绑定不再同时触发；松键放行防卡键 |
| 11 | 警惕 trip 锁存（`vigilanceTripped`，确认或停车才解除，15 s 兜底） | C-01/C-02 | 我的 failSafe 没锁存，司机重新挂加速就能继续开；真实警惕装置不允许 |
| 12 | `leaveStation()` 在牵引前释放 | 隐藏软锁 | Create 在 approachTargetSpeed 里释放车站，我绕开了它 → 电动列车停站后 currentStation 永远不清（时刻表/到站判断全坏） |
| 13 | 双端驾驶镜像进挡位律 | **B-12**（我算了标志但没用） | 整个计算在司机坐标系镜像（速度、坡度、输出），两端驾驶台行为一致 |
| 14 | REVERSE 双语义 + 驻坡保持 | M043/M105 | 静止+下坡趋势=电机通电保持（计费不回馈）；行进中=被拖反的发电机（回馈）；静止时制动方向对抗重力而非"前进方向"（否则上坡反而滑得更快） |
| 15 | 动态制动低速淡出（`electricTrainDynamicBrakeMinSpeed=5`） | B-06 物理真实 | 发电机 back-EMF 随速度塌缩，真实列车最后 20 km/h 全靠摩擦 |
| 16 | 惩罚清除：车站事件**锁存** + 里程兜底只在移动时累计 | C-08 + 你的扳手案例 | 逐 tick 采样 currentStation 在扳手 relocation 后永不可满足 → 永久 40 km/h |
| 17 | 超载块整体重写（at-ceiling 门 + 超限制动锁存 + 恢复只在挡位仍是 CRUISE 时） | E-04/E-06/M194 | 我原来的恢复会覆盖司机在超载期间手动改的挡；刹车过 28 继续刹停的 bug |
| 18 | `isBlocked`（Create 自家 carriage.blocked/不兼容轨道标志）替代我的静止计时启发式 | B-11 | Create 自己就知道"被挡住"，无需猜 |
| 19 | `isDriver` 包校验（服务端掌握的控制者记录） | 防伪造 | 只有服务端记录的司机能发挡位指令，乘客/路人发包无效 |
| 20 | EB 拉下即上惩罚 + 重置 leg 锁存 + lockoutTail 重置 | C-08/M105「保留之前限速」 | 二次拉 EB 继承第一次的 tail/leg 计数 → 提前解锁/提前清惩罚 |
| 21 | autoArrive 让位导航（CRUISE ← |targetSpeed|） + gearAvailable 恢复检查 + COAST 兜底 | B-13 | 拉杆会顶着进站曲线给牵引；取消进站时无电不允许恢复牵引挡 |
| 22 | 确认(J) 停车时解除 trip；行驶中只清时钟 | C-01 | trip 后只有停车+确认才能继续开 |
| 23 | `isOnContraption` 三高度采样（脚/腰/头 + 各查下一格） | E-15 | 坐在座位上、低顶走廊内也可判定，平台旁永不误判 |
| 24 | 落座前跳过"传送待确认"玩家 | M185 | 同 #9，橡皮筋根因 |
| 25 | 电力求解成为结构字段权威（hasTractionMotors/hasWorkingPantograph/isPowered/powerScale/maxSpeed/trackGrade/cruiseState 全部由 buildCircuit/finishSimulation 写入）+ 首tick兜底 | D-01/D-06/G-01 | 我的字段散在两处且从未有人写 |
| 26 | 需求公式定型：电气需求 = min(需求力,可给力)·v/(η·K_aux)·K_margin + 2kW/节 辅助负载；再生 = min(电机减速力·v, 额定)·0.7；Norton 内阻 V²/4P + 再生期间辅助负载照常为负载 | C-06/F-10 | 我的旧公式少辅助负载/裕度/额定封顶，且再生期间辅助负载消失 |

## 三、有意保持的不同实现（每条注明依据）

1. **警惕时钟在无电区间继续走**（你的 M100 原话："无电短过分相就刹车和警惕计时归电气化管"）。你的树里 `commanded` 门把它关了（AI 自己的理由："无法牵引时警告不公平"）——与你的原话冲突，按冲突规则**你的原话赢**。保持电气化模式（含 DEAD_COAST）期间时钟运行。
2. **再生的动态制动默认 0.5 而非 0**：你的树默认 0（"让制动就是一个真实的 0.8"），但 B-06 白纸黑字"再生最多 0.5"、F-10/M052 要求回馈真实生效。按需求文档取 0.5（配置可调 0）。
3. **司机判定用"坐在司机座"∪"Create 控制者记录"**：你描述的"脱离驾驶台"=离开座位（M187），坐姿占用是连续信号；你的树只用 Create 的控制者记录（只在按键时存在，需要 grace）。两者取并集，更稳。
4. **座位门控在两个模式都跑**（E-01 字面："时速28以上的电气化列车"，未限定模式）；你的树交接后清除超载。Create 自身 ≤28.8，实际几乎无差。
5. **落座比较坐标系**：你的树严格局部坐标（E-02 字面）；我先世界坐标等价实现，审计后已改为局部坐标，与你一致。
6. **数据包结构**：我合并成一个 SyncTrainPanelPacket（你拆成 gauge+pantographs 两个）。信息面等价，少一条包。

## 四、外观/非行为差异（不影响验收，如实列出）

1. 玻璃：你有独立 item 贴图 + particle 贴图 + datagen advancement；我共用方块模型（功能一致：配方/战利品/挖掘标签/tooltip 齐全）。
2. HUD：你的面板 847 行比我的丰富（更多子状态文本）；功能面（挡位/警惕/超载/受电弓编组/限速/功率）我都覆盖，文本组织不同。
3. 你的 lang 键走 `electroenergetics.train.*` 体系；我走 `riders.*`/`pantograph.*`，中英文都有。
4. 你的 `GearStep`/`CabOrientation`/`TrainHudData` 拆分文件；我用字段/内联等价物。
5. 你的 CEEConfigs 在加载时打印有效牵引参数（防旧配置钉死旧默认值的排查手段）——建议你保留你的这版，我的树未加。
6. 你的 PantographMovementBehaviour 视觉伸缩改为读 entry.active + CONTACT_HALF_WIDTH=2.5；我的保留原作者视觉自适应。机械行为（entry.active 语义）两边一致。
7. 你的 SeatIntent 流程是服务器请求→客户端回报→服务器分配（带诊断日志）；我的是客户端持续回报+服务器校验，去重后流量等价，且无日志噪音。

## 五、逐需求覆盖确认（重审编号 → 验证结果）

- **A-01..A-10**：全参数进配置（含 manualFullSpeed/dynamicBrakeMinSpeed/regen 两开关/gradeResistance 开关/aux/margin），物理模型逐式对齐 ✅
- **B-01..B-15**：全实现；B-12 补镜像；B-13 补让位+恢复检查；B-15 补时刻表牵引模型 ✅
- **C-01..C-10**：全实现；C-05 补 EB 保模式（原为事故级缺口）；C-08 补事件锁存 ✅
- **D-01..D-11**：全实现；D-05 补 sticky CREATE；D-06 补结构权威字段 ✅
- **E-01..E-17**：全实现；E-07 补地板接地（原为缺失级）；E-15 三高度采样；E-16 补 tp-ack 跳过 ✅
- **F-01..F-10**：全实现；F-08 补 CUT_SEGMENT 常量名与护栏（行为原已对）；F-10 补 Norton V²/4P + 辅助负载 ✅
- **G-01..G-11**：全实现；G-03/G-02 补客户端同步分支 ✅
- **H-01..H-05**：全实现（玻璃贴图差异见第四节） ✅
- **P-01 选 A / P-02 选 A**：P-01 无距离上限+本车最近座（局部坐标）✅；P-02 有电常驻面板+无电按无弓门控 ✅
- **K-01..K-17 覆盖链**：与《需求重审》一致；K-17 站立不跟随（RiderViewFollow 只坐姿）✅

## 六、编译状态

重写树（工作副本 + 交付副本）**537 文件全量 javac 0 错误**。你的树未做任何改动（全程只读）。
