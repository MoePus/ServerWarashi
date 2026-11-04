# 火焰覆盖层离线预览

不启动游戏，直接渲染世界内的性能火焰，用来调整 `world_perf_object.vsh/.fsh`。

渲染器编译的就是仓库里的 shader 文件，marker 数据的打包方式、绘制批次和混合方式都和游戏一致，按 1920×1080、FOV 70 渲染，背景是一块熔炉加石头地面，再裁出火焰附近的区域。离线看到的效果和游戏里一致。

## 运行

需要 JDK 21+ 和 `unzip`，并且执行过一次 `./gradlew build`，用来准备 Gradle 缓存里的 LWJGL 和 Minecraft 客户端 jar。

```bash
tools/flame-preview/preview.sh                  # 输出 build/flame-preview/flame_sheet.png
tools/flame-preview/preview.sh gif=4            # 另外输出 4 秒的 flame.gif
tools/flame-preview/preview.sh "set=PIXEL_SNAP=0.0;CHIP_CORNERS_FROM=99.0" out=build/flame-preview/free
```

静帧图的每一行对应一个严重度和一种背景，每一列是一个时间点；动图的每一列对应一个严重度。

## 参数

所有参数都是 `key=value` 形式。

| 参数 | 默认值 | 说明 |
|---|---|---|
| `strengths` | `1,2,3,4,5` | 要渲染的火焰强度 |
| `bg` | `day,night` | 背景：`day`、`night` |
| `distance` | `6` | 相机到火焰的距离（格） |
| `cols`、`dt`、`t0` | `6`、`0.12`、`3.0` | 静帧图的列数、帧间隔和起始时间（秒） |
| `gif` | `0` | 动图时长（秒），`0` 表示不输出 |
| `crop`、`zoom` | `220x380`、`1` | 每个火焰的裁切尺寸，以及静帧图的放大倍数 |
| `screen`、`fov`、`yaw`、`pitch` | `1920x1080`、`70`、`25`、`12` | 模拟的屏幕分辨率和相机参数 |
| `set` | 无 | 临时覆盖 shader 常量，例如 `set=PIXEL_SNAP=0.0;SPARK_TWINKLE_OFF=0.6` |
| `out` | `build/flame-preview/flame` | 输出文件名前缀 |
| `batches` | 火焰、spark 两个批次 | 批次布局 `pass:start:slots;...`，`start` 和 `slots` 可以写 shader 常量名 |

文字标签不在预览范围内：它依赖游戏运行时生成的字形图集。
