# XeroDelta 三角洲

> **此项目已停止更新。**
>
> 本仓库保留最终源码快照，供学习、研究和自行维护。不再承诺功能更新、
> 问题修复、版本适配或使用支持。
>
> **Development has stopped.** This repository is a source snapshot for reference
> and independent maintenance. No further updates or support are planned.

Minecraft 1.21.1 / NeoForge / Java 21。项目提供占格物品栏、安全箱、装备与背包、
尸体搜刮、品质与价值规则、交易、仓库、邮件、医疗和角色状态界面。

文档整理日期：2026-09-20。模组版本号仍为 `0.1.0`；
同版本文件可能对应不同源码快照，辨认构建时请同时记录 Git 提交。

## 文档

| 文档 | 内容 | 适合读者 |
| --- | --- | --- |
| [功能实现说明](docs/IMPLEMENTATION.md) | 架构、结构树、功能机制、关键流程、兼容与测试边界 | 使用者、整合包作者、接手维护者 |
| [配置与数据参考](docs/REFERENCE.md) | TOML 配置、文件路径、全部 SavedData 类、组件、网络包方向 | 服主、开发者 |
| [命令参考](docs/commands.md) | 命令树、参数、旧命令映射、权限差异、邮件 JSON 示例 | 服主、地图作者 |
| [占格系统详解](docs/IMPLEMENTATION.md#占格系统) | 锚点、尺寸、旋转、碰撞、排序和容器存储 | 占格功能维护者 |
| [验证与已知限制](docs/IMPLEMENTATION.md#验证与已知限制) | 已运行检查、未验证场景、当前源码与历史需求的差异 | 所有读者 |

这些文档按当前源码编写，不把历史需求、截图或类名本身当作已完成的功能。
“存在实现”“专项测试通过”“游戏内已验证”是不同结论。

- [许可证](LICENSE) 与 [第三方声明](CREDITS.txt)。

## 项目内容

| 系统 | 已有实现 | 详细说明 |
| --- | --- | --- |
| 占格物品栏 | 多格尺寸、锚点存储、旋转、叠加、交换、拆分、整理、跨来源转移 | [占格](docs/IMPLEMENTATION.md#占格系统) |
| 安全箱与装备 | 五种安全箱、限时解锁、到期只取不存、皮肤、检视、胸挂、背包、卡包 | [装备](docs/IMPLEMENTATION.md#安全箱背包与装备) |
| 尸体搜刮 | 尸体菜单、实体规则、载具加权生成、搜索进度、地面背包 | [搜刮](docs/IMPLEMENTATION.md#尸体与附近掉落物) |
| 物品详情 | 占格比例预览、详情分区滚动、收藏、拆分及操作请求 | [详情](docs/IMPLEMENTATION.md#搜索与物品详情) |
| 物品规则 | 六档品质、尺寸、价格、重量、组件变体、耐久范围、自动计算及批量重置 | [规则](docs/IMPLEMENTATION.md#品质价格重量与自动计算) |
| 创造编辑器 | 品质/尺寸/价格草稿、多选、框选、撤销重做、批量提交 | [创造模式](docs/IMPLEMENTATION.md#创造模式规则编辑) |
| 个人仓库 | 玩家独立仓储、七种分类、主仓扩容、命名、转移 | [仓库](docs/IMPLEMENTATION.md#个人仓库) |
| 交易与经济 | 钱包、上架、购买、交易记录、收藏、回收、配方供需市场 | [交易](docs/IMPLEMENTATION.md#交易回收与配方市场) |
| 邮件 | 收件箱、附件领取、广播、离线收件人、发送历史与撤回路径 | [邮件](docs/IMPLEMENTATION.md#邮件) |
| 战局收益 | 与钱包分开的收益记录、奖励发放、撤离结算 | [收益与权限](docs/IMPLEMENTATION.md#战局收益与功能权限) |
| 角色与医疗 | 部位伤势、医疗消耗、修理、体力、倒地、救援、搬运、护甲判定 | [角色状态](docs/IMPLEMENTATION.md#角色状态医疗倒地与体力) |
| 轮盘与队伍 | 医疗轮盘、命令轮盘、Delta Spot 布局适配、队伍状态与 HUD | [输入与队伍](docs/IMPLEMENTATION.md#轮盘队伍与命令) |
| 界面编辑 | 自带 Material 3 设置、HUD 和布局配置、图标与中英文资源 | [界面](docs/IMPLEMENTATION.md#material-3-设置界面) |
| 模型与资源 | Bedrock 几何/动画解析、Molang 子集、检视渲染、方块与物品资源 | [注册与渲染](docs/IMPLEMENTATION.md#注册内容与资源渲染) |

**边界说明：** 当前 Better Looting 适配包含列表定位、拖拽预览与拾取对接；
不能据此声称已经完整替换成“默认 5×5、自动向下扩高”的附近掉落物网格。
项目不是整合包，不附带世界、服务器配置、外部模组或外部模组的完整实现。

## 结构概览

```text
xero_delta/
|-- src/main/java/com/xtdpotato/xero_delta/
|   |-- grid/          # 占格存储、几何、整理与转移
|   |-- data/          # 规则、角色状态、权限和持久化
|   |-- trading/       # 交易、回收、配方供需
|   |-- mail/          # 邮件与附件
|   |-- network/       # 客户端请求、服务端同步
|   |-- client/        # 输入、缓存、渲染、叠加层
|   |-- screen/        # 界面及 material/ 原生控件
|   |-- mixin/         # 原版与第三方注入
|   `-- ...            # 注册、物品、方块、菜单、模型等
|-- src/main/resources/
|-- src/test/java/
|-- docs/              # 实现、配置数据、命令文档
|-- tools/verify_item_detail.gradle
|-- gradle/wrapper/
`-- .github/workflows/build.yml
```

[完整模块结构与职责](docs/IMPLEMENTATION.md#目录结构树)。
品质、价格与尺寸代码位于 `data/` 等实际包中，不存在单独已实现的
`quality/`、`pricing/`、`sizing/` 子系统包。

## 运行环境

| 项目 | 要求 |
| --- | --- |
| Minecraft | 1.21.1 |
| Java | 21 |
| NeoForge | 本快照使用 21.1.233，依赖范围以 `neoforge.mods.toml` 为准 |
| Curios | 必需，开发依赖为 9.5.1+1.21.1 |
| Delta Spot | 必需，元数据要求 0.1 及以上；标点轮盘等功能与其联动 |
| Super Resolution（超分） | **不再是前置依赖**，设置等界面不使用其 UI 库 |

Curios 和 Delta Spot 是独立模组，不包含在本仓库的成品 JAR 中。
兼容层针对 Better Looting、TaCZ、Sophisticated 系列等提供特定适配，
不代表支持其所有版本和整合包组合。

## 安装与首次使用

1. 使用 Java 21、Minecraft 1.21.1 和相符 NeoForge 的独立测试实例。
2. 在客户端与服务器安装匹配的本项目 JAR、Curios、Delta Spot。
   单人游戏同样需要这些依赖。
3. 先备份世界和 `config/delta_packs/`，再加载已有存档。
4. 通过游戏按键设置中的中文 `XeroDelta三角洲` / 英文 `XeroDelta`
   分类检查绑定；默认键及用途见 [输入说明](docs/IMPLEMENTATION.md#默认按键与命令轮盘)。
5. 管理员使用 `/xero help` 查看入口；规则初始化会修改服务器规则，
   应在测试世界确认后再使用 `/xero itemrules auto`。

缺少界面不一定是贴图问题：布局开关、当前模式、服务器权限、Curios 槽位和
是否在仓库附近都会影响入口或操作。排查顺序见实现说明的验证章节。

## 当前界面实现

设置、尸体规则、实体规则编辑和物品选择器已使用 Minecraft 原生
`Screen` / `GuiGraphics` 与本项目的 Material 3 控件。
不再链接 Super Resolution、NanoVG 或 Yoga。

- 设置使用分类导航、开关、滑块、输入框、颜色预览和固定底部操作栏。
- 内容溢出时单独滚动，底部返回和保存操作不随内容滚走。
- 设置草稿与持久化分离，重置需确认，保存前检查输入。
- 物品选择保留搜索、分类、Ctrl 多选与 Shift 范围选择。
- 原有存档数据、配置键及本次修改涉及的网络协议保持不变。

详细实现与限制见 [功能实现说明](docs/IMPLEMENTATION.md)。

## 构建

先安装 JDK 21，并配置 `JAVA_HOME`。无需本机绝对路径或超分 JAR。

Windows：

```powershell
.\gradlew.bat --no-daemon --max-workers=1 -I tools/verify_item_detail.gradle verifyMaterialSettings verifyCreativeRules verifyItemDetail build -x test -x backupGithubSource
```

Linux / macOS：

```sh
chmod +x gradlew
./gradlew --no-daemon --max-workers=1 -I tools/verify_item_detail.gradle verifyMaterialSettings verifyCreativeRules verifyItemDetail build -x test -x backupGithubSource
```

成品位于：

```text
build/libs/XeroDelta-1.21.1-NeoForge-0.1.0.jar
```

上述命令运行独立的界面几何、资源和源码接线回归检查，并编译打包。
`-x test` 明确跳过完整 NeoForge 测试任务，不应理解为完整测试集通过。
此快照的完整测试环境曾遇到 transforming classloader / ClassNotFoundException，
尚未解决；本次界面改动未完成游戏内视觉验收。

需要尝试完整测试时，可先构建独立的 Delta Spot 项目，
再指定 `-Pdelta_spot_test_jar=<JAR路径>` 并运行 `test`。
该参数提供运行依赖，不保证解决上述类加载问题。

GitHub Actions 使用同一组专项检查与构建命令，不将其表述为游戏内测试。

## 发布范围

仓库仅包含主要源码、运行资源、测试、Gradle Wrapper、构建配置和必要文档。
不上传本机游戏实例、玩家存档、运行配置、模组缓存、日志、堆转储、
临时反编译文件或个人开发目录。

生成相同范围的源码压缩包：

```powershell
.\gradlew.bat --no-daemon --max-workers=1 githubSourceZip
```

输出位于 `build/distributions/`。`backupGithubSource` 默认只复制到
项目内的 `build/backups/`；可通过显式 `github_backup_dir` 属性另行指定。

## 许可证

本项目按现有 [MIT License](LICENSE) 发布。
Material Symbols 图标和第三方项目保留各自的许可证；
Minecraft、NeoForge 及外部模组不因本仓库发布而被重新授权。
