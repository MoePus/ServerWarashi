# ServerWarashi

ServerWarashi 是一款面向 Minecraft 1.21.1 NeoForge 服务端的诊断与治理模组，用于定位常加载区块、实体堆积和 tick 性能问题，并提供对应的管理操作。

## 安装与兼容性

- 服务端安装后可使用性能诊断、弱加载区块治理、加载器冻结、实体加载保护和 watchdog 诊断。
- 客户端也安装本模组后，可使用性能分析窗口、世界内覆盖层以及地图集成。纯服务端安装时，性能报告会降级为聊天或控制台文本。
- Xaero's World Map 是可选客户端依赖，检测到后会自动启用地图覆盖层和右键菜单。
- FTB Chunks、C2ME no-tick 和兼容的方块加载器 ticket 会参与加载来源归属；无法识别的来源仍会作为未知或系统来源显示。
- 所有 `/warashi` 命令、弱加载清理和加载器控制操作都需要权限等级 `2`（OP）。单人游戏所有者可订阅性能数据。

## 功能

### 性能分析

- 按需采样服务器 tick，统计区块 tick、自然生成、实体、方块实体、服务器任务、事件处理器和 GC 开销。
- 报告包含平均 MSPT、峰值、样本数、坐标以及玩家或区块加载器来源，并限制内部聚合和网络条目数量，避免异常服务器状态无限占用内存。
- 客户端性能窗口支持分类标签、排序、分页、展开来源、坐标传送以及分析开始和停止控制。
- 没有兼容客户端时，报告以聊天或控制台文本输出。
- Windows 上分析期间会临时请求更精确的系统计时器分辨率，分析结束后恢复。

### 实时空间覆盖层

- 分析运行期间，服务端持续发布有界的区块空间快照以及附近实体和方块实体热点；只向有权限且主动订阅的客户端发送。
- Xaero 地图实时显示区块性能热力、弱加载风险、离屏危险方向、加载器覆盖范围和工作状态，不必等分析结束后刷新完整报告。
- 世界内 GPU 覆盖层在热点实体、掉落物和方块实体上绘制火焰与耗时标签，并用光柱标出危险区块和弱加载区块。对象按距离和视锥裁剪，掉落物热点会聚类显示。
- 单人游戏被地图界面暂停时，分析状态会明确显示为暂停；关闭暂停地图后继续采样。

### 弱加载区块治理

- 后台分批扫描已跟踪区块，对实体密集区块进行稳定抽样，并估算主要实体和物品类型数量。
- 地图会显示弱加载热力和主要类型。OP 可通过二次确认的右键菜单提交清理任务。
- 清理任务按 tick 分批处理，避免一次删除大量实体阻塞服务器。此操作会永久删除目标实体或掉落物，无法撤销。
- `weak_chunk_scan_enabled` 只控制后台弱加载巡检；关闭后，已提交的清理任务和性能热点报告所需的实体统计仍会继续工作。

### 区块加载器冻结

- 对可识别的方块加载器保留原始 ticket，同时把被冻结 ticket 的有效 level 暂停为 `33`，使对应区块退出常规实体和方块 ticking。
- 冻结策略保存到世界数据中，并自动应用到之后插入的同一加载器 ticket。
- 可在地图右键冻结或解冻，也可通过命令查看、清理孤立策略或删除指定策略。
- `data/serverwarashi/tags/block/chunk_loaders.json` 定义用于记录放置者的加载器方块，数据包可以扩展该 tag。

### 实体加载保护

- 单个区块一次载入的实体数超过配置阈值时，选择数量最多的若干实体或物品类型，每类只保留配置数量，其余自动删除。
- 默认在超过 `5000` 个实体时处理前三种类型，每种保留 `100` 个，并向所有在线玩家广播结果。
- 这是自动且不可逆的保护行为。可通过服务端配置整体关闭、调整阈值和保留数量，或只关闭广播。

### Watchdog 诊断

- 专用服 watchdog 保存崩溃报告前，可增加 `ServerWarashi Ticket Info` 分类。
- 诊断列出各维度活跃 ticket owner 的代表坐标、覆盖区块数、方块实体数和实体数，单个维度采集失败不会阻止崩溃报告保存。
- 如遇兼容性或崩溃报告生成开销问题，可关闭该附加诊断。

## 低开销采样

主计时由独立线程每约 1ms 读取一次 `PerfWorkTracker` 完成，不读取目标线程栈。tracker 使用预分配 slot 和 version 一致性校验；Entity、乘客 Entity、Block Entity、Chunk 与通用 Handler 的服务线程发布路径不分配对象，也不执行 `nanoTime`。tick 内没有显式上下文时仍记录 `TASK / Unknown`，因此 JFR 不可用时主计时不会消失。

HotSpot JFR 的 `ExecutionSample` / `NativeMethodSample` 只作为 10ms 低频明细来源，用于把已有的通用 Handler/Unknown 主样本按比例细分为具体监听器、Entity Dispatch、Block Entity Dispatch 和 Unknown 栈明细。JFR 事件按时间戳限流并归入保留约一秒的 closed tick；它们不增加主样本数或时间权重。拆分使用整数配额并严格守恒，证据不足时保留通用条目。

报告的 `sampleAttempts`、MSPT、max、热点与总时间只来自主采样。耗时按主采样的实际尝试密度估算，不是逐任务 stopwatch；JFR 的启动失败、运行故障、丢样和外部 recording 频率不会改变主计时，只会降低明细质量并显示 degradation reason。实现不会回退到 `ThreadMXBean`、`Thread.getStackTrace()` 或 ThreadDump。

## 命令

| 命令 | 说明 |
| --- | --- |
| `/warashi perf open` | 打开当前性能报告窗口。 |
| `/warashi perf analyze start [seconds] [open]` | 开始性能分析；`seconds` 范围为 `1..86400`，未提供时使用配置值；`open` 默认 `true`。 |
| `/warashi perf analyze stop` | 停止当前分析并发布最终报告。 |
| `/warashi loader frozen list` | 列出已保存的冻结策略，最多显示 100 条。 |
| `/warashi loader frozen clear-orphans` | 清理当前没有有效 ticket 的冻结策略。 |
| `/warashi loader frozen clear <dimension> <controller> <owner>` | 清除指定加载器的冻结策略。 |

## 配置

服务端配置位于世界的 `serverconfig/serverwarashi-server.toml`；NeoForge 也可能在默认配置目录生成或复制模板。客户端配置位于 `config/serverwarashi-client.toml`。下表中的开关均保持升级前的默认行为。

### 服务端

| 配置项 | 默认值 | 范围或说明 |
| --- | ---: | --- |
| `perf_auto_stop_seconds` | `900` | 未指定命令时长时的自动停止时间，`1..86400` 秒。 |
| `perf_sample_interval_us` | `1000` | 性能采样间隔，`1000..100000` 微秒；越低采样越密集。 |
| `perf_world_hotspot_limit` | `2048` | 向每名玩家发送的附近实体和方块实体热点上限，`256..4096`。 |
| `perf_allow_non_op_subscriptions` | `false` | 允许非 OP 客户端订阅性能报告和空间数据；不授予管理操作权限。 |
| `entity_load_guard_enabled` | `true` | 启用实体加载保护。 |
| `entity_load_guard_batch_threshold` | `5000` | 单区块批次必须超过此数量才触发，`100..1000000`。 |
| `entity_load_guard_top_type_limit` | `3` | 最多处理数量最高的类型数，`1..64`。 |
| `entity_load_guard_retain_per_type` | `100` | 每个被处理类型保留的实体数，`1..1000000`。 |
| `entity_load_guard_broadcast` | `true` | 向所有在线玩家广播自动删除结果。 |
| `weak_chunk_scan_enabled` | `true` | 启用后台弱加载区块巡检。 |
| `watchdog_ticket_report_enabled` | `true` | 在专用服 watchdog 崩溃报告中附加 ticket 诊断。 |

### 客户端

| 配置项 | 默认值 | 可选值或说明 |
| --- | ---: | --- |
| `perf_output_mode` | `SCREEN` | 最终报告显示方式：`SCREEN`、`CHAT` 或 `BOTH`。 |
| `world_overlay_filter` | `TWO_OR_MORE` | 世界内对象覆盖层显示 `ALL` 样本，或只显示有效强度至少为 2 的 `TWO_OR_MORE`。 |
| `world_overlay_enabled` | `true` | 启用世界内 GPU 火焰、标签和区块光柱；关闭后停止世界专用热点订阅，但不影响 Xaero 地图覆盖层。 |

## 地图附属 API

其他地图模组可通过 `com.moepus.serverwarashi.api.client.map` 接入覆盖层、标准分析按钮和区块右键菜单。集成通过 `MapOverlayApi.register` 注册，并提供地图视口、目标、菜单项和标准控制接口。

该 API 随模组一同发布，但模组仍处于早期开发阶段，API 可能在后续版本中调整；Xaero's World Map 的内置适配可作为参考实现。

## 许可证

**All Rights Reserved (ARR)**。除非获得版权所有者明确授权，否则不得复制、修改、再发布或以其他方式分发本模组及其源代码。
