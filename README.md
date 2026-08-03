# ServerWarashi

ServerWarashi 是一款服务器诊断与治理模组，用于定位常加载区块、实体堆积和 tick 性能问题，并提供对应的管理操作。

## 安装

- 服务端安装后可使用诊断、加载器冻结、实体保护和 watchdog 报告。
- 客户端安装本模组后可查看性能窗口和地图覆盖层；没有客户端模组时，性能报告会降级为聊天或控制台文本。
- Xaero's World Map 是可选客户端集成，安装后自动启用地图功能。
- 所有 `/warashi` 命令与地图管理操作都需要权限等级 `2`（OP）。

## 功能

- **性能分析**：按需采样区块、自然生成、实体、方块实体和服务器任务，报告热点、MSPT、峰值、样本数及玩家/加载器来源。客户端可在可排序、可分页的窗口中查看，或改为聊天输出。
- **Xaero 地图热力图**：显示高耗时区块、弱加载风险、离屏热点、加载器覆盖范围和工作状态；地图内可开始、停止或清除分析。
- **弱加载区块治理**：扫描实体密集的已跟踪区块，显示主要实体/物品类型的估算；OP 可在地图右键二次确认后分批清理主要类型。此操作会永久删除实体或掉落物。
- **区块加载器冻结**：对可识别的方块加载器保留原始 ticket，并将其有效 level 暂停为 `33`，使区块退出常规 ticking。冻结策略会持久保存，并自动应用到之后插入的同一加载器 ticket；可从地图右键冻结或解冻。
- **实体加载保护**：单个区块一次载入超过 5,000 个实体时，最多三种数量最高的实体/掉落物各保留 100 个，其余自动删除并广播。这是不可逆的保护行为，当前没有配置开关。
- **Watchdog 诊断**：专用服 watchdog 崩溃报告会增加 `ServerWarashi Ticket Info`，列出各维度的活跃 ticket owner、区块、实体和方块实体统计。

## 低开销采样

性能采样使用 **MixinLite** 的无分配回调注入与方法作用域。未进行 profiling 时，采样器不会运行，相关钩子只存在轻量的开关检查，因此额外开销接近零。

相较以传统 `@Inject` 或 `@Redirect` 实现同类采样路径，MixinLite 在这里更轻量，也更容易与其他 Mixin 的调用链共存。

## 命令

| 命令 | 说明 |
| --- | --- |
| `/warashi perf open` | 打开当前性能报告窗口。 |
| `/warashi perf analyze start [seconds] [open]` | 开始性能分析；`seconds` 范围为 `1..86400`，`open` 默认 `true`。 |
| `/warashi perf analyze stop` | 停止当前分析并发布报告。 |
| `/warashi loader frozen list` | 列出已冻结的加载器策略。 |
| `/warashi loader frozen clear-orphans` | 清理没有有效 ticket 的冻结策略。 |
| `/warashi loader frozen clear <dimension> <controller> <owner>` | 清除指定加载器的冻结策略。 |

## 配置

| 文件 | 配置项 | 默认值 |
| --- | --- | --- |
| `config/serverwarashi-server.toml` | `perf_auto_stop_seconds` | `900` 秒 |
| `config/serverwarashi-server.toml` | `perf_sample_interval_us` | `1000` 微秒 |
| `config/serverwarashi-client.toml` | `perf_output_mode` | `SCREEN`，可选 `CHAT` 或 `BOTH` |

## 地图附属 API

其他地图模组可通过 `com.moepus.serverwarashi.api.client.map` 接入覆盖层、标准分析按钮和区块右键菜单。该 API 随模组一同发布，但模组仍处于早期开发阶段，API 可能在后续版本中调整；Xaero 的内置适配可作为参考实现。

## 许可证

**All Rights Reserved (ARR)**。除非获得版权所有者明确授权，否则不得复制、修改、再发布或以其他方式分发本模组及其源代码。
