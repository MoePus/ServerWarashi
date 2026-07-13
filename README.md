# ServerWarashi

NeoForge 1.21.1 服务端降负载 mod：不删除任何 chunk 加载 ticket，而是给 ticket 叠加「暂停掩码」，
让低价值区块保持加载，同时将有效 ticket level 降到 33 以退出常规 entity/block ticking，从而压低常加载区块很多的服务器的 tick 成本。

## 功能模块

| 模块 | 作用 |
| --- | --- |
| bucket | 把常加载区块的 owner 分桶轮转，同一时刻只让一部分 owner 活跃 |
| idlefreeze | 按现实日期冻结长期无人访问的重负载 owner，玩家进入任一 chunk 时自动解冻 |
| cost | 低频采样每 chunk 的 tick 成本，供 bucket 调度排序与 OP 的 HUD 展示 |
| performance | 手动性能分析，按 owner 聚合 chunk/BE/entity 耗时并生成报告 |

命令入口统一为 `/warashi bucket|idlefreeze|perf|ticket ...`（需要 OP）。
服务端配置见 `serverwarashi-server.toml`，键按 `bucket_` / `perf_` / `resident_` / `idlefreeze_` 前缀分组。

## 上手

- 核心机制、目录职责、运行链路与推荐阅读顺序：[ARCHITECTURE.md](ARCHITECTURE.md)
- 每个类的逻辑流程图（mermaid）：`src/flowcharts/main`，与源码目录一一对应

## 构建

```text
gradlew build              # 构建
gradlew runServer          # 本地起测试服务端
```

运行环境：Minecraft 1.21.1 / NeoForge 21.1.x / Java 21。
JUnit 与 GameTest 位于 `src/test`（本地开发基建，不随仓库分发；本地存在时用
`gradlew test` 与 `gradlew runGameTestServer` 验证）。
