# CEE-TrainSpeed 一次性重写交付说明

> 2026-10-31 · 基准：原作者 1.21.1 main（`Create-Electro-Energetics-1.21.1.zip`）+ 需求重审全部生效需求 + P-01(选A) / P-02(选A)。
> 编译状态：**全源 537 个 Java 文件本地 javac 全量编译通过（0 错误）**。构建走你原有的 GitHub Actions 管线（build.gradle / gradle.properties 未动）。
> 历史反甩人代码（自研乘客位置同步、站立视角跟随、AABB 判定等）**全部不存在**——本树从原作者基准重新长出，只有 K-11 之后的"强制落座"方案。

## 一、需求逐条落点（对照《需求重审.md》编号）

### A. 牵引与列车物理 —— `TrainTractionModel.java`（新）
- A-01 直线最高 100 m/s：`CTrains.electricTrainMaxSpeed=100`
- A-02 每节 50 t / 1375 kW：`CResistances.electricTrainMassPerCarriage` / `CTrains.electricTrainPowerPerCarriage`
- A-03 Davis 阻力 A+B·v+C·v²（C=0.0014 气动平方项）
- A-04 低速恒力→达功率恒功率，拐点 ≈ P/(m_eff·a0)；起步加速度 2.0 可调
- A-05 坡度只走重力分量（`gradeSinOf` 用前后转向架高差），无固定坡道限速表
- A-06 无弯道限速：`TrainMixin` 把 `maxTurnSpeed()` 等同 `maxSpeed()`
- A-07 COAST 纯阻力先快后慢
- A-08 制动 0.8 恒定；`maxHoldableGrade` 由 0.8/9.81≈8.2% 物理决定（未写死表）
- A-09 电动列车绕开 Create 手动 0.75 倍率：`ElectricManualSpeed` + `TrainHUDMixin`（速度条分母恒为设计最高速）
- A-10 起步加速度 2.0 默认可调

### B. 五挡操纵 —— `TrainGear` / `TrainDriverState` / `TrainControlInput` / `CarriageContraptionEntityMixin`
- B-01 电动列车 W/S、滚轮全部无效（`control()` 拦截 + `TrainHUDMixin.onScroll` 拦截）；燃料车原版
- B-02 右下角面板：拉杆五挡 + K 紧急制动 / J 警惕确认 两按钮 + 显示屏；两步选择（上下选挡 + Enter 挂入）
- B-03 加速挡到 100 自动切匀速（tick 内切换，不振荡）
- B-04 匀速挡 hold：上坡满功率掉速、下坡制动保速、恢复后回原速
- B-05 断电挡纯阻力；失电自动落断电挡并 latch（`powerLossCoasted`）
- B-06 制动挡 max(摩擦0.8, 再生0.5) 不叠加
- B-07 行进中挂 R（>0.25 m/s）→ 直接布防紧急制动（K-06 裁定，无额外按钮）
- B-08 J=警惕确认、Enter=挂入、K=紧急制动、上下=选挡、左右=选弓、P=升降弓、O=自动升弓开关（全部可重绑定，`TrainControlKeys`）
- B-09 控制台动画只跟随拉杆（`ControlsMovementBehaviourMixin` 重定向 `Vec3.length()`），A/D 过岔保留
- B-10 失电/堵停一次性锁存，只在 HUD 报告
- B-11 铁路尽头堵停 1 秒 → 自动落制动驻车（`stalledTicks`≥20），R 可倒出
- B-12 双端驾驶方向翻转：司机座位 → `conductorSeats` 标志位判定 `driverCabInverted`
- B-13 按住空格进站适配：挂 R 之外的原挡先记忆；到站 → 驻车制动（不再立即发车）；到达前松开 → 恢复原挡
- B-14 司机脱离 = "还在但不按键"：无人 36 s → 常用制动驻车；窗口内可换司机
- B-15 有时刻表 → 交还 Create 联控（`TrainControlModeLogic.decide`）

### C. 警惕 / 紧急制动 / 惩罚 —— `TrainDriverState`
- C-01 J 确认重置整条 30 s；20–30 s 黄、30–35 s 红（进度条在按钮下沿）
- C-02 36 s 走**常用制动**（K-07 覆盖裁定）
- C-03 速度为 0 每 tick 重置警惕
- C-04 EB 需要 R 挡 + 供电（K/无电禁用）；触发后锁操作
- C-05 EB 不可中断，中途失电只剩摩擦制动继续到停
- C-06 EB 电机满功率反拖、计费、不回馈；HUD 显示实际功率（`motorBraking` 红字）
- C-07 停稳 → 自动降弓 → 2 s 后解锁面板 → 交 Create
- C-08 40 km/h 限速挂 `Train.maxSpeed()`，交接后仍生效；到站或 10000 方块兜底解除
- C-09 停车 2 s 解锁面板，但升弓禁令持续到下一站（面板文案「因紧急制动锁定 不能操控」）
- C-10 无电时三个牵引挡 + EB 全部禁用，BRAKE/COAST 可用

### D. 受电弓与模式 —— `TrainControlModeLogic` / `CatenaryModule` / `SetPantographPacket`
- D-01 电机+受电弓（接触靴不算）才是电气化列车
- D-02 无原作者自动升弓；逐弓自动开关默认关（M172），开启时手动键禁用
- D-03 升弓必须有接触网可及 + 提示；主动降弓无限制
- D-04 降最后一根工作弓先切 COAST
- D-05 无弓 → Create 操控；从 Create 升弓回到电动 → 拉杆自动进断电挡；28 m/s 速度门控交接（P-02 选 A：有电时与静止无关，面板常驻；无电 0 速交 Create）
- D-06 电压/功率不足 → powerScale 按电压缩放、不主动要电、HUD 黄字提示
- D-07 两种模式都能升降弓（packet 不按模式门控）
- D-08 紧急制动惩罚期升弓禁用
- D-09 装配默认**降弓**（接触靴除外，M118 保持原作者机制）
- D-10 逐弓独立操作；≥2 弓才需要左右选弓
- D-11 取电横向 3 格（`sidewaysReach` 2→3，视觉判定同源常量）

### E. 座位门控 —— `RiderSeatGate` / `SeatIntentTracker` / `RiderDismountLockMixin`
- **E-15/L-01 硬约束：唯一在车判据 = 玩家脚下坐标 → 车厢局部 BlockPos → `Contraption.getBlocks()` 含该格或下一格。全代码零世界 AABB。**
- E-01 >28 m/s 任何方式（tp）上车未落座 → 强制落座
- P-01 选 A：无距离上限，严格本列车最近空座；局部方块判据天然不会把邻车/站台玩家吸进来
- E-02 用传送后位置找座；E-16 客户端上报意图 + 服务端权威校验/冲突裁决（`ReportSeatIntentPacket`，防伪造 16 格）
- E-03 超载 = 28 强制落座时人数>空座；非玩家占座算已占；车顶站立算未就座
- E-04 28 以下加速到 28 超载 → 切匀速保 28；>28 时新超载 → 制动到 28 后保匀速；都不刹停
- E-05 超载不锁加速挡（可挂，由 tick 钳制）
- E-06 超载解除只看"确实没人站着"，恢复记忆挡位
- E-07 <28 不判定不提示不限速，自由移动疾跑跳跃
- E-08 >28 shift 下车拒绝+提示；tp/死亡/换维度不受影响（`wantsToStopRiding` 单点锁）
- E-09 提示送达站立者本人（含全车广播）
- E-10 文案无「加速挡已锁定」
- E-11 站立退出=原版坐标；坐座退出绑定座位，重进回座（登录时回迁+清绑）
- E-12 旁观者完全排除
- E-13 幽灵座位清理（乘客链比对；非玩家实体保留）；登出即释放座位，重登回迁
- E-14 seated 是整列车属性
- E-17 文案 100 km/h
- 调试：`/cee debug eeseat` 静止一次性触发强制落座（M200）

### F. 电气 —— 配置 + `WireType` + `CatenaryModule`
- F-01 25 kV（trainMaxVoltage=25000）
- F-02 多车共享电网电压跌落 → powerScale 分摊
- F-03 铜线 0.0002 Ω/m、1000 A
- F-04 绝缘/高压绝缘=铜芯，只差绝缘电压（1500/27500 V）
- F-05 铁家族按电导率：0.00116 / 0.00029(新配置 ironBusWireResistance) / 0.0000723 Ω/m，载流 415/1000/1000 A
- F-06 所有线 1000 A 上限（`WIRE_RATING_LIMIT`）
- F-07 变压器不限功率（`transformerMaxPower` 删除）
- F-08 `temperatureForAmpacity`：T=1000·(min(I,1000)/33.3−1)，铜 29000 触发温度
- F-09 低压限制功率保护线路
- F-10 再生真实回馈电网：`fromThevenin` Norton 源向 trainNode 注入电流（功率=制动力×速度×效率×powerScale）

### G. HUD —— `ElectricTrainHud`（左下） / `TrainControlHud`（右下） / `TrainHUDMixin`
- G-01/02 六行读数：速度/挡位/坡度/功率/接触网/车厢+电机；仅驾驶可见
- G-03 18 格速度条分母恒为 100 m/s（maxSpeed 覆盖 + 0.75 倍率旁路）
- G-04 显示平滑 τ=0.12 s、0.1 m/s 量化
- G-05 左下读数、右下面板，中间留空看路况
- G-06 全中文、模式行与拉杆分离、`*` 只在待挂入时黄显
- G-07 编组侧视图（两端下侧平滑尖头）+ 受电弓位置 + 车头箭头 + 自动升弓 A 标
- G-08 视角跟随 = Create 原生坐姿跟随（K-17 裁定：站立不跟随）。**没有自研视角代码**
- G-09 无电区不显示回馈、最高速显示保留
- G-10 字符串缓存、5 tick 同步节流、无热路径 AABB
- G-11 面板状态行（锁定/断电惰行/电动驾驶）

### H. 杂项
- H-01 无边框通透玻璃（alpha 30，原版玻璃熔炼 1:1，"绊脚石" tooltip，创造标签页）
- H-02~H-05 工作流约定（构建/推送规则）不涉及代码

## 二、关键架构决定（与你旧实现的差异）

1. **牵引物理注入点**：`TrainMixin` 包裹 `maxSpeed()/maxTurnSpeed()/tickPassiveSlowdown()`，挡位律由服务端 `TrainDriverState.tick` 每 tick 直接积分 `train.speed`（a/400 每 tick，与 Create 的单位约定一致）。Create 的 `approachTargetSpeed` 只在 Create 模式生效，两边互不打架。
2. **挡位律/警惕/EB/座位门控全部在同一个每列车 tick 状态机**（`GameEvents.serverTickEventTrains` → `TrainDriverState.tick`），先决模式、再算力、再门控，没有跨系统散落的隐藏状态。
3. **再生回馈用 Norton 电流源**（`ElectricalProperties.fromThevenin`）注入 trainNode——这是真实回馈，不是 HUD 显示。**极性如果在游戏里发现方向反了，把 `CatenaryModule` 里 `builder.connect(trainNode, groundNode, ...)` 两个参数对调即可。**
4. **司机判定** = 坐在 `CarriageContraption.conductorSeats` 里的玩家（Create 自己的司机座定义），双端驾驶方向由此推出。
5. **下车锁只有一个 mixin**（`Player.wantsToStopRiding`）：tp/死亡/换维度走 `Entity.stopRiding` 直连，天然放行，不再需要你旧实现里的那套放行标志。
6. **坡度**取 0 号车厢前后转向架锚点高差（与 Create 自身 pitch 计算同源），列车前后方向任意、上下坡双向正确。

## 三、建议实测清单（按历史事故点排序）

1. **起步**：静止挂加速挡 → 恒力起步 ≈2 m/s²，42 km/h 附近切恒功率，100 m/s 自动跳匀速（不再蹦迪）。
2. **过分相**：断电瞬间拉杆自动落「断电」、面板保持（DEAD_COAST）、警惕继续计时；降回 28 m/s 以下自动切 Create 操控。
3. **紧急制动**：行进中挂 R → 锁操作、电机满功率反拖（HUD 红字反拖功率）、停稳自动降弓、2 s 后面板解锁、40 km/h + 禁升弓持续到下一站。
4. **警惕**：运行中 20 s 黄 / 30 s 红 / 36 s 常用制动；停车时倒计时永远重置。
5. **座位**：>28 m/s tp 上车 → 落到最近空座（用 `/cee debug eeseat` 静止验证选座）；座位满 → 制动到 28 保匀速；人走空 → 解除限速并恢复原挡。
6. **受电弓**：逐弓升降、逐弓自动开关、无网升弓被拒有提示、双弓车左右选弓、3 格横向取电。
7. **电表**：匀速巡航功率 ≈ 阻力×速度；再生制动时电网侧电表应看到反向电流（极性反了按上文第 3 点对调）。
8. **通透玻璃**：配方=原版玻璃熔炼、无边框、放置后"绊脚石"提示。
9. **重进回座**：坐座退出重进仍在原座位；站立退出重进在原世界坐标。

## 四、已知边界（如实说明）

- 本地 javac 只验证类型/引用正确性；mixin 运行时命中（refmap 由 Actions 构建生成）需进游戏确认。若某个 @WrapMethod 目标在 Create 6.0.10 内被混淆/改名，日志会直接报 mixin 应用失败——这是首批要看的日志行。
- `SyncTrainPanelPacket` 每 5 tick/车 一发，128 格半径内玩家可见；多人多车时带宽占用约等于原版 SyncTrainGaugeDataPacket 的量级。
- 40 km/h 惩罚兜底 10000 方块是历史遗留的 dev 参数（审计报告提过），保留在配置里可改。
- 再生回馈的电路极性、`conductorSeats` 在双端车上的标志语义，是两处"编译正确但需要游戏里确认"的点。

## 五、文件清单

新增 30 个类 + 8 个资源/数据文件，修改 26 个（完整列表 `git diff --name-only b059f03 HEAD`）。
基准 commit `b059f03`（原作者 1.21.1 原样），实现 commit `fe9be5d`。
