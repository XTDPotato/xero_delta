# XeroDelta 功能实现说明

> **此项目已停止更新。**
> 本文说明 2026-09-20 发布快照中的主要实现，供阅读源码和自行维护。
> 不是后续开发承诺，也不代表所有功能均经过完整游戏内回归测试。

## 阅读导航

本文按“结构 → 数据与交互 → 功能系统 → 兼容与验证”组织。
配置键和协议清单见 [配置与数据参考](REFERENCE.md)，可执行命令见
[命令参考](commands.md)。本文中的类名均指
`src/main/java/com/xtdpotato/xero_delta/` 下的实现。

1. [目录结构树](#目录结构树)、[总体分层](#总体分层)、[生命周期与数据流](#生命周期与数据流)
2. [占格系统](#占格系统)、[安全箱、背包与装备](#安全箱背包与装备)
3. [尸体与附近掉落物](#尸体与附近掉落物)、[搜索与物品详情](#搜索与物品详情)
4. [品质、价格、重量与自动计算](#品质价格重量与自动计算)、[创造模式规则编辑](#创造模式规则编辑)
5. [个人仓库](#个人仓库)、[交易、回收与配方市场](#交易回收与配方市场)、[邮件](#邮件)
6. [战局收益与功能权限](#战局收益与功能权限)、[角色状态、医疗、倒地与体力](#角色状态医疗倒地与体力)
7. [轮盘、队伍与命令](#轮盘队伍与命令)、[Material 3 设置界面](#material-3-设置界面)
8. [注册内容与资源渲染](#注册内容与资源渲染)、[配置、存档与兼容边界](#配置存档与兼容边界)
9. [验证与已知限制](#验证与已知限制)、[建议阅读顺序](#建议阅读顺序)

## 目录结构树

以下列出主要结构和代表性文件，不展开每个网络包、纹理或测试文件。

```text
xero_delta/
|-- README.md
|-- LICENSE
|-- LICENSE.txt
|-- CREDITS.txt
|-- build.gradle
|-- settings.gradle
|-- gradle.properties
|-- gradlew / gradlew.bat
|-- gradle/wrapper/
|-- .github/workflows/build.yml
|-- docs/
|   |-- IMPLEMENTATION.md             # 本文：系统实现
|   |-- REFERENCE.md                  # 配置、保存格式、网络目录
|   `-- commands.md                   # 命令与载荷
|-- tools/
|   `-- verify_item_detail.gradle
`-- src/
    |-- main/
    |   |-- java/com/xtdpotato/xero_delta/
    |   |   |-- XeroDelta.java          # 注册与生命周期入口
    |   |   |-- Config.java             # 通用配置
    |   |   |-- ModDataComponents.java  # 物品数据组件
    |   |   |-- ServerEvents.java       # 服务端事件、角色状态同步
    |   |   |-- api/                    # 占格与菜单访问接口
    |   |   |-- bedrock/                # BedrockLoader
    |   |   |   |-- model/              # 骨骼、立方体、面和几何解析
    |   |   |   |-- animation/          # 关键帧、轨道、插值和动画控制
    |   |   |   |-- molang/             # 表达式解析与求值上下文
    |   |   |   `-- render/             # 变换、过滤与渲染控制
    |   |   |-- block/                  # 方块注册与实现
    |   |   |-- client/                 # 客户端状态、叠加层、渲染和输入
    |   |   |-- command/                # 管理命令与后台计算调度
    |   |   |-- compat/                 # 特定模组的联动入口
    |   |   |-- data/                   # 规则、配置、SavedData、玩家状态
    |   |   |   `-- size/provider/      # 原版、配方、模组兼容尺寸提供器
    |   |   |-- effect/                 # 状态效果实现
    |   |   |-- entity/                 # 尸体实体
    |   |   |-- event/                  # 事件处理与 Tooltip 组件
    |   |   |-- grid/                   # 占格算法、存储、放置及转移
    |   |   |-- gui/                    # 可打开界面的目标定义
    |   |   |-- item/                   # 安全箱、背包、装备等物品
    |   |   |-- mail/                   # 邮件模型、服务和存档
    |   |   |-- menu/                   # 尸体、地面背包和仓库菜单
    |   |   |-- mixin/                  # 原版与第三方交互注入
    |   |   |-- network/                # Payload 编解码、请求与同步
    |   |   |-- screen/                 # 配置、交易、仓库、角色等界面
    |   |   |   `-- material/           # 自带 Material 3 绘制和控件
    |   |   |-- tag/                    # 模组标签
    |   |   |-- trading/                # 交易、回收、分类和配方市场
    |   |   `-- util/                   # 共用辅助函数
    |   `-- resources/
    |       |-- META-INF/neoforge.mods.toml
    |       |-- xero_delta.mixins.json
    |       |-- assets/xero_delta/
    |       |   |-- lang/               # 简体中文、英文语言资源
    |       |   |-- models/
    |       |   |-- animations/
    |       |   |-- blockstates/
    |       |   |-- trading/            # 交易与回收模板
    |       |   `-- textures/
    |       |-- assets/minecraft/
    |       |-- assets/curios/
    |       `-- data/                   # 配方、标签、Curios 等资源
    `-- test/java/com/xtdpotato/xero_delta/
        `-- ...                        # 算法、资源、接线及集成相关测试
```

结构树只列实际源码/资源目录。历史工作区中的 `container/`、`pricing/`、
`quality/`、`sizing/`、`slot/` 为空目录，不属于发布源码中的有效模块。
“价格”“品质”“尺寸”是功能分类，不能据此推导同名 Java 包。

### 模块职责索引

| 边界 | 代表实现 | 主要职责 |
| --- | --- | --- |
| 注册入口 | `XeroDelta`、`ModDataComponents`、`ModMenus` | 模组初始化、数据组件和菜单注册 |
| 服务端生命周期 | `ServerEvents` | 登录/退出、状态更新与同步触发 |
| 占格核心 | `GridBackingStore`、`GridGeometry`、`GridPackingPlan` | 保存锚点、几何转换、放置计划 |
| 转移服务 | `SafetyBoxTransferService`、`DeltaPackTransferService`、`WarehouseTransferService` | 在实际物品来源和目标之间转移 |
| 通用规则 | `ServerItemRules`、`ModDataStorage`、`ServerItemWeights` | 尺寸/品质、价格、重量与缓存 |
| 持久化业务 | `PersonalWarehouseData`、`TradingMarketData`、`MailData` | 仓储、托管资产、邮件 |
| 玩家状态 | `PlayerInjuryManager`、`DownedManager`、`PlayerStaminaManager` | 伤势、倒地、体力 |
| 界面呈现 | `PlayerStatusScreen`、`DeltaContainerLayoutController`、`ItemDetailOverlay` | 角色页、组合容器布局、详情层 |
| 通信边界 | `ModNetwork`、各 `*Packet` | 编解码、操作请求、结果同步 |
| 外部适配 | `compat/`、`mixin/`、各 `*Integration` | 外部来源访问、输入优先级、UI 注入 |

## 总体分层

[`XeroDelta`](../src/main/java/com/xtdpotato/xero_delta/XeroDelta.java)
在模组总线上注册物品、方块、实体、菜单、效果、数据组件和网络处理器；
通用配置通过 NeoForge `ModConfig` 注册。

界面负责展示、选择和发送操作意图。真正修改容器、余额、邮件或角色状态的代码，
通常位于服务端网络处理器、服务类及 `SavedData` 中。
客户端还保留同步后的缓存，避免在每一帧重复计算和访问服务端数据。

读取一个功能时，可按下面的方向追踪：

```text
Screen / 客户端输入
    -> network/*Packet
    -> 服务端校验与服务类
    -> ItemStack 数据组件 / SavedData / 配置文件
    -> 同步包
    -> 客户端缓存与重新绘制
```

并非所有设置都发包。颜色、布局、界面缩放等表现层设置主要保存在本地；
涉及游戏结果或服务器规则的更改由对应服务端处理器决定是否接受。

## 生命周期与数据流

### 初始化与进入世界

1. `XeroDelta` 注册模组内容、网络和通用 TOML 配置。
2. 客户端入口 `XeroDeltaClient` 注册按键、屏幕/渲染相关事件。
3. 配置类按需要读取 `config/delta_packs/`；世界数据由各 `SavedData.get`
   从世界数据存储取得，不能把这些状态当成同一份客户端配置。
4. 服务端玩家事件同步规则、装备访问、角色、交易/邮件等相关状态。
5. 客户端保存同步快照，并由对应屏幕或 HUD 消费。

管理员首次引导由 `FirstJoinGuidePolicy` 判定：
具备管理权限、没有物品规则、没有价格规则且布局未启用时才显示。
它不是“每个新玩家必弹”的欢迎页，也不是完整的地图任务系统。

### 一次物品操作的边界

```text
客户端点击/拖动
  -> 找到物品来源和目标逻辑格
  -> 显示本地预览
  -> 发送操作请求
服务端处理器
  -> 重新定位当前菜单、来源、装备或世界实体
  -> 检查权限、来源是否仍有效、数量、格子边界与物品限制
  -> 执行对应转移服务或菜单操作
  -> 更新组件 / SavedData / 菜单槽位
客户端
  <- 结果包、状态快照或原版菜单同步
  -> 更新显示
```

请求里带物品 ID 不等于允许客户端生成该物品。
例如卡包选择会重新检查玩家来源槽位、数量、标签和 Curios 槽位；
其他功能的检查各自位于处理器中。这个设计方向不等于已经完成所有包的安全审计。

### 状态的四种寿命

| 状态 | 寿命 | 例子 |
| --- | --- | --- |
| 物品组件 | 跟随 ItemStack 保存 | 箱内锚点、旋转、绑定 |
| 世界持久化数据 | 跟随世界保存 | 仓库、钱包、邮件、安全箱解锁 |
| 文件配置 | 跟随实例配置目录 | 尺寸规则、重量、主题、布局 |
| 运行期缓存 | 运行期间，不能替代存档 | 客户端快照、拖拽来源、搜索队列、渲染缓存 |

## 占格系统

### 尺寸与规则

[`ItemSize`](../src/main/java/com/xtdpotato/xero_delta/data/ItemSize.java)
保存逻辑宽高，单个维度约束在 1 到 10 之间；旋转时交换宽高。
[`ItemSizeRule`](../src/main/java/com/xtdpotato/xero_delta/data/ItemSizeRule.java)
在尺寸之外保存纹理是否跟随旋转、是否拉伸以及比例参数。
规则可以编码到 `long`，不是用屏幕像素作为存档尺寸。

`ModDataStorage`、`ServerItemRules` 和 `ComponentRuleKeys`
负责物品规则及键的解析；物品类型规则和带组件的具体物品变体不是同一层匹配。
尺寸提供器由 `ItemSizeProviders` 按兼容、自动和默认层级组织，
包括 Sophisticated Backpacks、TaCZ、原版、配方和默认提供器。
具体覆盖顺序应结合规则入口读取，不能简单理解为“所有物品都按配方决定尺寸”。

### 锚点存储

[`GridBackingStore`](../src/main/java/com/xtdpotato/xero_delta/grid/GridBackingStore.java)
使用长度为 `columns * rows` 的列表。
一个多格物品只在左上角锚点存一份 `ItemStack`，
其他被覆盖格不是额外的物品副本。

示例：宽为 5 的容器内，一个 `2x1` 物品从 `(1, 2)` 开始放置：

```text
索引 = row * columns + column
锚点 = 2 * 5 + 1 = 11

第 2 行: [空] [A] [覆盖] [空] [空]
```

查找某个格子上的物品时，`findAnchorIndexAt` 根据已有锚点及其实际占格范围反查。
因此点击物品覆盖的任意格都可以定位到同一份物品。

### 放置、叠加和交换

放置检测先检查物品限制，再检查边界、背包分区或分隔带，最后检查占用情况。
`PlacementResult` 将结果区分为：

- `BLOCKED`：不允许放置。
- `CAN_PLACE`：可放入空闲区域。
- `CAN_STACK`：可与现有堆叠合并。
- `CAN_SWAP`：可尝试交换；是否完成还取决于服务端转移规则和来源状态。

`resolveExactPlacement` 接受已经确定的左上角坐标。
服务端不应再次把坐标当作鼠标悬停格居中，否则偶数宽高物品会出现偏移。
拆分堆叠先寻找能容纳完整占格的目标，再修改物品；找不到目标时保持原状。

### 旋转与拖拽来源

物品的已保存旋转状态使用 `GRID_ROTATED` 数据组件。
`ClientGridRotation`、`ServerGridRotationState` 和 `ServerGridCarryState`
分别处理客户端预览、服务端旋转状态以及拖拽来源。
来源记录用于放回原位、交换后回填以及跨容器移动，避免仅凭当前鼠标位置猜测来源。

### 几何与显示

[`GridGeometry`](../src/main/java/com/xtdpotato/xero_delta/grid/GridGeometry.java)
将逻辑格子映射为 GUI 坐标，提供格子位置、鼠标命中和有效格集合。
基础格长为 18 GUI 像素，可根据布局缩放或显式格长计算。
它也处理安全箱特殊分隔布局，放置规则需与这一分隔保持一致。

`GridWidget`、`GridItemRenderer`、`DeltaGridCellRenderer`
以及容器界面控制器负责绘制和交互。
占格宽高与物品模型自身的纹理边界是两个概念；
详情预览同样应先按占格确定区域，再根据显示规则绘制模型。

### 自动整理

[`GridPackingPlan`](../src/main/java/com/xtdpotato/xero_delta/grid/GridPackingPlan.java)
先在逻辑层生成完整放置计划，再由调用者修改实际槽位。
它逐个处理输入条目，按行、列扫描候选锚点，先试首选朝向，再试另一朝向。
任何物品无法放下时返回空结果。

这是确定性的顺序放置方法，不是保证最优装箱的搜索算法。
容量相同但排列不同的物品集合，可能需要改变条目顺序或布局后才能放入。

### 数据保存与网络

`ModDataComponents` 注册：

| 组件 | 用途 |
| --- | --- |
| `grid_contents` | 主占格区域的锚点物品列表 |
| `grid_containers` | 同一物品内额外容器区域的列表 |
| `grid_rotated` | 具体物品堆的旋转状态 |
| `box_uuid` | 安全箱身份标识 |
| `item_bound` / `item_bound_owner` | 物品绑定及归属 |
| `loot_searched` | 搜索状态标记 |
| `safety_box_skin` | 安全箱外观选择 |

`GridActionPacket` 发送取出、放置、交换、右键、拆分、收集和返回原位等动作，
其中包含坐标、旋转状态和容器区域索引。
服务端重新查找真实安全箱，检查区域索引、权限和放置条件后处理。
`GridSyncPacket` 等将实际结果同步回客户端。
UI 的可放置预览不能替代服务端校验。

### 原版菜单与分区容器

普通 Minecraft 菜单已有自身槽位和点击语义，不能直接当成安全箱组件列表。
`ContainerGridHelper`、`ContainerGridClickHandler`、
`ContainerGridRenderBridge` 与容器 Mixin 对接原版槽位；
`ContainerGridRules` 以菜单 Java 类名保存是否启用占格的覆盖规则。
未配置的菜单默认启用，但复杂第三方菜单仍需单独验证槽位含义。

胸挂/背包的 `PackRegionLayout` 将可存储区域划成有效分区。
一件物品总面积小于空余格数，并不保证能放入：其完整矩形必须在允许区域中，
不能穿过分隔带、无效格或其他物品。
界面缩放只影响坐标转换，不应改变逻辑占格和保存索引。

### 占格示例与不变量

```text
5 列容器；A=2x1，B=1x2，C=2x2
[A][a][B][ ][ ]
[C][c][b][ ][ ]
[c][c][ ][ ][ ]
```

大写表示保存 ItemStack 的锚点，小写只是覆盖示意，不能另存一份物品。
旋转 A 后尺寸变为 `1x2`，总面积不变；只有纹理旋转不等于逻辑旋转。
维护时必须保持以下不变量：

- 任意两件物品的有效占格不能重叠。
- 存储索引使用容器逻辑宽度，不使用屏幕上可见列数。
- 转移成功前保留来源；转移失败不能无条件扣除来源。
- 拆分、快速移动、整理、交换都必须检查完整物品矩形。
- 修改尺寸规则后应复查旧存储布局；规则变更不等于自动迁移所有物品的位置。

### 常见定位路径

| 现象 | 优先检查 |
| --- | --- |
| 偶数宽度物品放置偏一格 | 客户端计算出的左上角是否又被服务端居中 |
| 点击覆盖格无反应 | 是否先使用 `findAnchorIndexAt` 反查锚点 |
| 空余很多仍放不下 | 分区、宽高、旋转和确定性装箱次序 |
| 拖拽后返回错误位置 | `ServerGridCarryState` 中记录的来源 |
| 图标大小与占格不同 | `ItemSizeRule` 的纹理策略与 `GridItemRenderer` |

## 安全箱、背包与装备

`SafetyBoxItem` 和 `DeltaPackItem` 定义可携带容器。
Curios 提供安全箱、胸挂、背包等装备槽的接入点；
`DeltaPackTransferService`、`SafetyBoxTransferService`、
`DeltaQuickMoveService` 处理不同存储来源之间的物品移动。

`SafetyBoxItemPolicy`、`WildcardMatcher` 及允许/禁止列表决定物品过滤。
`SafetyBoxAccessData`、`SafetyBoxExpiry` 和 `SafetyBoxReadOnlyPolicy`
处理解锁、有效期及到期后的允许操作，不只是界面按钮是否可点击。

`SafetyBoxLayoutPack` 保存布局描述，`SafetyBoxLayoutScreen` 提供布局编辑。
皮肤和检视动画由 `SafetyBoxSkinCatalog`、`SafetyBoxInspectResources`
及客户端动画/模型代码处理；表现资源不改变容器中的物品归属。

### 容量与物品身份

`ModItems` 注册的安全箱逻辑容量为 `2x1`、`2x2`、`3x2`、`3x3`、`4x2`。
箱子物品在其他容器中的外部占格，与箱子内部容量是两套属性。
`box_uuid` 标识箱子实例，不能用相同物品 ID 代替实例身份。

默认额外装备包括 `dar_assault_chest_rig`（4x6）、`gto_heavy_tactical_pack`
（5x9）、`delta_card_holder`（3x3）。这些是构造器中的基本容量；
实际显示还可能经过分区布局及物品专用规则，不能一律视为无间隔的大矩形。

### 解锁、到期和过滤

解锁记录以玩家 UUID 与安全箱物品 ID 管理；`SafetyBoxExpiry.merge`
以当前有效期或当前时间中的较晚者叠加新增时长，并处理永久权限与上限。
`SafetyBoxReadOnlyPolicy` 将到期箱子视为只读来源：
允许合规取出、收集，禁止插入和会写入箱子的交换。到期不是删除箱内物品。

`Config.isBlacklisted` 的检查顺序包括允许列表、显式允许策略、
耐久附魔标签限制和黑名单。默认 `tacz:ammo*` 在允许列表中，
而 `tacz:*` 在黑名单中，所以不能简单描述为“TaCZ 全部禁止”。
不同策略类的通配支持存在边界，不应把模式字符串当作任意正则表达式。

### 刀具与卡包

`KnifeAccessData` 保存玩家刀具解锁及相关堆栈数据，
`KnifeSkinRules`、`KnifeDisplayStatsParser` 和选择包共同完成刀具目录与选择。
它不是任意刀具 ID 的无条件生成入口。

`CardHolderSelectPacket` 从玩家物品栏或光标来源选择已有卡包：
需要装备修改权限，来源数量必须是 1，物品必须属于 `curios:card_holder`，
并且当前 Curios 槽位有效、接受该物品。原卡包与来源位置交换。

## 尸体与附近掉落物

`CorpseEntity` 保存尸体相关状态，`CorpseMenu` 提供服务端菜单，
`CorpseScreen` 与 `DeltaContainerLayoutController` 组织客户端显示。
`CorpseRules.Settings` 按实体 ID 保存胸挂和背包候选列表。
每项是物品 ID 加权重，同类别内的相对概率按 `weight / totalWeight` 计算。

`CorpseRulesScreen` 编辑实体集合；
`CorpseEntityEditorScreen` 编辑某实体的候选与权重。
保存前验证注册 ID、物品所属载具类别、重复候选和权重范围。
在线修改还需服务器赋予编辑权限，随后通过 `CorpseRulesUpdatePacket` 提交。
实体模型预览使用 `EntityModelPreview` 与独立预览界面。

### 尸体规则的两个来源

通用配置中的 `corpse_rules` 提供实体 ID 和默认候选；
`CorpseRulesData` 保存当前世界的寿命、可攻击设置和实体载具规则，
并保留旧式字段的读取迁移。
候选权重只描述同一类别中的相对选择，不表示“有多少概率生成整个尸体”。
服务端应过滤不存在或类别不符的物品 ID，客户端预览不是有效性依据。

### Better Looting 与地面背包的实际范围

`BetterLootingInventoryLootListMixin` 当前做的是移动原列表位置、对齐顶部、
读取其拖动物品并绘制 Delta 放置预览。`BetterLootingInteractionMixin`、
`BetterLootingPickupCompat`、长按和批量拾取相关 Mixin 对接拾取行为。
它们依赖第三方内部类或反射接口，不是一个独立重写的附近物品服务。

**当前源码中没有找到完整的“默认 5x5、占格不够时扩为 5x7 等高度”的
附近掉落物网格替代实现。** 原列表仍存在，不能把历史需求写为已完成。
这是对旧版文档描述的纠正，不是本次文档更新实现了该功能。

地面背包使用 `GroundPackMenu` / `GroundPackScreen`，
由打开和转移请求操作实际背包来源；它与“把周围所有散落物聚成一个网格”
不是同一功能。客户端显示条目也不是新建的世界掉落实体副本。

## 搜索与物品详情

`LootSearchManager`、`LootSearchRules` 和搜索状态网络包负责搜索规则与进度，
客户端显示相应遮罩或状态。
搜索相关数据与物品实际内容分开处理。

`ItemDetailOverlay` 负责物品详情弹层及操作，
`ItemDetailActionPacket` 将需服务端执行的动作提交处理。
详情布局、模型预览和底部操作区域分别计算；长内容可以滚动，
不应通过滚动整个窗口把底部按钮推出屏幕。
收藏键的生成和收藏显示也有独立逻辑，不能仅按显示名称区分物品。

### 搜索队列与时间

`LootSearchRules.visualOrder` 按屏幕 y、x、slotId 排序；
悬停优先操作通过 `prioritizeNext` 调整队列。
默认每项搜索时间：灰 10 tick、绿 20、蓝 35、紫 40、金 60、红 75。
这些是游戏 tick；低 TPS 时不能按真实秒表保证完成时间。
尸体槽位 0..4、10、11 属于立即可见范围，其他内容由搜索路径处理。

`LootSearchRulesData` 的方块覆盖键包含维度和坐标；
搜索运行状态还包括玩家队列、容器/范围的已搜索状态及锁。
规则配置、当前搜索进度和物品 `loot_searched` 标记各司其职。

### 详情框的尺寸与层级

`ItemDetailLayout` 把标题、预览、动作和说明拆成独立区域：
标题为 9 GUI 像素文字高度加上下各 2 像素，总高 13；
预览基准格长为 48，因此 `1x1` 是 48x48，`2x1` 是 96x48，
`3x3` 是 144x144 的逻辑预览尺寸。
它们不是所有屏幕缩放下固定的物理像素。

自动尺寸先计算内容需求，再限制到屏幕可用范围。
内容放得下时不需要滚动；放不下时按分区计算滚动范围。
动作区优先保留空间，但极小窗口下动作区自身也可能产生滚动，
不能承诺任何分辨率都完整显示所有操作。

收藏使用 `favorite.png` 与 `favorite_off.png`，前者对应金色填充状态；
客户端即时更新并发送收藏请求，服务端收藏集合随市场状态同步。
详情层和 Tooltip 使用 `ScreenLayerResolver` 的末次绘制及相对层级，
避免被容器前景挡住，同时避免无限增大深度值超出 GUI 范围。

## 品质、价格、重量与自动计算

`ServerItemRules` 管理服务端尺寸、品质规则；
`ModDataStorage`、`DeltaPriceManager`、`DynamicItemValuation` 等参与价格解析。
`RuleFormulaConfig` 保存自动计算使用的尺寸、随机偏移和各品质价值范围。
`AutomaticItemSizing`、`AutomaticItemValuation`、`AutomaticItemWeight`
承担批量推导，兼容层可以对特定模组物品提供专门结果。

`ServerItemWeights` 和 `PlayerWeightCalculator` 处理重量规则及玩家携带重量。
`ServerEvents` 计算角色状态并向客户端同步。
状态去重不仅比较重量与余额，也包含布局启用和操作权限等标志，
防止切换模式后必须等重量变化才刷新界面。

`ModCommands` 使用单线程后台执行器和运行中标志组织自动计算、批量重置。
自动计算流程构建 `AutomaticCalculationSnapshot` 后计算结果，
再通过 `server.execute` 回到服务器线程提交及同步。
重置流程也区分后台清理和服务器提交，并给出开始、繁忙或失败提示。

这不意味着可以在工作线程随意修改世界、玩家或注册表。
当前快照中的注册表/配方读取发生在快照构建路径上；
自行维护时需要审查第三方实现的线程安全及重载期间的一致性。

### 规则键不是显示名称

`ModDataStorage.getKey` 以注册 ID 加规范化组件文本的 Base64URL 指纹构成变体键，
有组件时使用 `{v2:...}` 后缀。规则身份会移除旋转、绑定/归属、搜索标记，
防止同一物品仅因旋转或搜索而得到另一套价值规则。
`getTypeKey` 还移除原版 DAMAGE；旧哈希键、旧组件顺序键有兼容读取入口。

| 匹配意图 | 机制 |
| --- | --- |
| 某个组件变体 | 稳定组件键，例如不同 TaCZ 枪械数据 |
| 忽略当前磨损的同类 | 类型键移除 DAMAGE，保留其他相关组件 |
| 整个注册物品 | 仅 `namespace:path` |
| 通配或标签 | `~` 模式前缀、`#` 标签前缀，按对应解析器支持范围 |
| 耐久区间 | `DurabilityRange` 与命令生成的范围规则 |

### 尺寸和品质的优先级

运行时尺寸解析不是直接执行一条公式字符串：

1. 手动尺寸规则优先。
2. 检查内置固定尺寸，以及指定拉伸策略的内置规则。
3. 读取已配置尺寸，再查询组件敏感的兼容尺寸。
4. 存在兼容尺寸时保留其宽高；可套用已配置的旋转/拉伸显示策略。
5. 否则使用配置尺寸、内置规则或默认值。

品质先处理安全箱专用固定品质，再处理手动/非自动配置、
特定近战兼容、自动动态估值、内置品质和模组名称颜色等分支。
自动计算阶段的来源顺序与运行时解析不是同一流程。
`RuleFormulaConfig` 中 `qualityFormula`、`sizeFormula`、`valueFormula`
是描述性字段，不能把修改字符串当作注入可执行脚本；
可实际调整的默认尺寸、配方开关、偏移和范围由 Java 代码读取。

### 自动估值与重量

`AutomaticCalculationSnapshot` 收集计算输入；
自动估值结合内置材料、配方关系、分类和回退规则生成结果。
`RuleFormulaConfig.Range` 限制每格最低、每格最高和总价上限，
随机偏移默认范围为 1..999。自动推导值不是物品真实市场价格的保证。

重量由独立 `ServerItemWeights` 文件规则和 `AutomaticItemWeight` 处理，
`PlayerWeightCalculator` 汇总携带内容；重量与占格面积不能等同。
绑定、旋转等物理堆栈状态不能意外让重量规则换键。

### 异步重置的准确范围

```text
命令进入 -> 原子标志占用任务 -> 立即反馈“开始”
工作线程 -> 文件规则清理 / 快照与计算
服务器线程 -> 世界价格提交、同步与结果反馈
finally -> 释放任务标志
```

并发请求会收到忙碌提示，而不是同时重写规则。
`itemrules reset` 当前调用 `clearSizes`、`clearQualities`、`clearPrices`，
**不清除重量、仓库、邮件、钱包或玩家物品**。
价格清除和同步仍在服务器线程，异步化不保证超大数据下完全没有提交开销。
没有断点续算或跨重启任务恢复承诺；失败时应查看日志和实际规则文件。

## 创造模式规则编辑

`CreativeItemRuleEditor` 为有权限的创造玩家提供品质、尺寸和价格三个页面。
每页独立保留待提交项，支持编辑开关、单选、多选、框选和撤销/重做。
通过 `CreativeRuleBatchPacket` 批量提交，不是直接在客户端修改全服规则。

创造界面的显示条件直接检查当前屏幕、游戏模式和权限，
不依赖重量包抵达后才显示。
从创造物品栏打开角色状态时保留返回目标及创造菜单，
返回按钮或 Esc 回到原界面；正常生存背包的关闭逻辑与此入口区分。

编辑器是受权限约束的规则工具，不是第三套物品存储。
草稿、选择和撤销历史位于客户端；批量提交由服务端包处理。
切换标签时保留各自草稿，避免把尺寸编辑误当作价格变更。
网络响应前的本地预览不能作为规则已经持久化的证据。

## 个人仓库

`PersonalWarehouseData` 是按玩家保存的世界 `SavedData`，
保存分类仓库及其中的物品。
`PersonalWarehouseContainer` / `PersonalWarehouseMenu` 对接菜单，
`PersonalWarehouseScreen` 显示分类、物品格和滚动区域。

`WarehouseTransferService` 及 `Warehouse*Packet`
处理从玩家物品栏、安全箱或其他受支持来源到仓库的转移。
`WarehouseNameRules` 对名称输入做约束。
仓库内容不是写在客户端设置 JSON 中，也不等同于视觉上的仓库方块模型。

### 分类与容量

所有分类固定 9 列，以下行数来自 `WarehouseCategory`：

| 分类 ID | 默认行数 | 最大行数 | 接受范围 |
| --- | --- | --- | --- |
| `main` | 35 | 70 | 通用；每次扩容增加 5 行 |
| `medical` | 8 | 8 | 医疗、修理及相应识别词 |
| `equipment` | 8 | 8 | 护甲、盾及护具识别词 |
| `items` | 12 | 12 | 通用物品 |
| `ammo` | 8 | 8 | 当前实现识别的枪械模组弹药 |
| `weapons` | 10 | 10 | 枪械、原版武器及特定枪械配件 |
| `collectibles` | 8 | 8 | 排除上述专类后的金/红品质物品 |

分类使用类型、注册 ID、名称和组件描述等启发式检查，不是通用语义识别。
例如弹药页的图标是箭，但当前 `isAmmo` 检查枪械模组描述，
不能由图标推断所有原版箭都可放入。

保存结构是玩家 UUID → 仓库名称 → 各分类 bins → rows 与 slot/stack 列表。
旧式单一 `rows/items` 会载入主仓，避免旧物品因新增分类而丢失。
名称最长 24 字符并经过清理；分类不匹配、越界和容量不足应由服务端拒绝。

## 交易、回收与配方市场

`TradingMarketService` 执行市场操作，
`TradingMarketData` 保存上架记录、交易记录等世界数据。
`TradingListing`、`TradingPriceSpec` 和 `TradingListingId`
表达商品、价格以及条目身份。

`TradingInventorySources` 统一玩家物品栏、装备容器和可选背包来源，
`TradingSourceAddress` 定义来源地址；
处理购买、上架或回收时需要重新确认源物品及数量。
`TradingItemEligibility`、禁止路径和上传规则限制允许交易的物品。
绑定物品通过 `BoundItemPolicy` 等策略限制，不能仅靠界面隐藏按钮。

`RecyclingMenu` 与回收规则完成回收流程。
`RecipeSupplyGraph`、`RecipeSupplyIndex`、`RecipeWorldMarket`
及相关数据类支持配方供需估值和世界市场变化。
这部分与固定手动物品价格规则不同，应分别阅读。

资源中仍有交易/回收等 HTML 模板及 `TradingHtmlThemeParser`。
本次移除的是超分 UI 前置，不是删掉所有名字包含 HTML 的模板解析器。

### 钱包、托管与成交

`TradingMarketData` 保存上架托管、钱包、收藏、历史、未解析名字账户、
玩家上架槽位和市场参数。`WORLD_ACCOUNT_ID` 是世界市场的专用账户，
不是某个在线玩家。

```text
选定来源 -> 检查允许交易/绑定/数量 -> 上架托管
买方请求 -> 检查商品、余额、数量与接收条件 -> 更新成交状态
卖方收益 -> MailService.sendTradeSale -> 货币附件
卖方领取附件 -> 钱包入账
```

售款**不会在邮件生成时自动进入卖方钱包**。
满仓、物品接收失败和余额上限等情况，应沿服务类结果判断，
不能仅凭 UI 上的成交动画推断所有后续步骤成功。

默认规则来自 `TradingRules`：初始余额 1,000,000，上架槽位默认 6、
可配置最大默认 12，槽位等级成本默认 5；默认市场条目上限 255。
最低价为估值的 10% 向上取整，最高价为估值 3 倍并受货币上限约束；
税率 10%、保障扣除 3%，分别按整数计算后相加。
这些为代码默认值，当前世界可覆盖部分市场参数。

### 回收与世界供需不是同一个系统

回收通过回收菜单、内置回收估值和上传策略处理，`up`、`recycle`、`none`
决定不同处置方式。不能从“有价格”推断“允许玩家上架”。

配方市场通过 `RecipeSupplyIndex` 建立配方索引，
`RecipeSupplyGraph` 处理输入/输出关联，`RecipeWorldMarketData`
保存有限供给、上次观察到的玩家持有量和商品键。
观察库存增量不直接消耗玩家物品；同一注册物品仅组件/弹药变化时，
增量分配受物品总数净增长限制，防止把变体变化当成新获得实物。
`DailyWorldMarketDrift` 提供运行期市场变化状态。

`TradingNavigationHistory`、`TradingHistorySelection`、
`CalendarDateSelection` 和 `ChineseLunarCalendar` 服务于客户端导航、
记录筛选和日历展示；它们不是交易结算或计时的权威来源。

## 邮件

`MailMessage` 和 `MailAttachment` 表达邮件及附件，
`MailData` 保存玩家邮件、发送记录和投递相关状态，
`MailService` 处理投递、读取、领取、删除等操作。
`MailPayloadParser` 和 `MailCommandFormatter` 用于命令/载荷转换。

客户端通过邮件列表、写信、附件选择和已发送界面操作，
通过 `MailActionPacket` / `MailSyncPacket` 与服务器交换数据。
附件选择器只产生选择结果，实际领取和转移由邮件服务端路径决定。
对重复领取、历史广播或撤回行为，应追踪服务数据状态，而非依赖客户端按钮。

### 附件、收件人与权限

附件支持物品、货币、经验点、经验等级、配方入口、FTB 任务入口。
后两种是交互入口，不是发放配方物品或强制完成任务。
JSON 结构、长度和示例见 [邮件载荷](commands.md#邮件载荷)。

命令发送使用玩家选择器；编辑器发送路径支持分隔的名字、
缓存中的离线档案和未解析名字账户。`*` / `@a` 广播会保存广播记录，
由玩家投递记录避免同一广播重复投递。
命令 `/xero mail send` 要求权限等级 2；
当前 `MailActionPacket.SEND` 检查的是创造模式，两种入口的门槛不同。

`MailActionPacket` 区分打开、刷新、标为已读、单附件领取、
全部/选中/整个收件箱领取、删除、已发送历史、撤回、重发和发送。
已读不等于已领取；删除已读不能被当作领取全部附件的快捷操作。
广播、历史和已领取状态都应连同邮箱备份。

## 战局收益与功能权限

### 战局奖励与钱包分离

`RaidEarningsData` 按 UUID 保存当前战局收益，`add` 使用范围保护，
`take` 以同步操作移除并返回收益；这与市场余额是两份数据。
`reward give` 累加战局奖励，`reward clear` 清除奖励，
`evacuate` 的命令处理路径读取奖励并执行结算。
不能用发放奖励代替钱包加款，也不能把 `evacuate` 的名字理解为
项目自带完整撤离地图、区域编辑器或任务流程。

### 装备修改与布局点击权限

`PlayerFeatureAccessData.allowChangeBc(UUID)` 默认 false；
玩家级检查还允许在个人仓库方块附近操作。
当前检查遍历玩家坐标各轴正负 5 格的立方体，并不是半径 5 格球体距离。
相关装备选择包仍会执行自己的额外校验。

`layoutClick` 默认开启，关闭值单独保存；
它控制增强拖拽、详情和双击等交互，不等于禁用全部 Minecraft 物品栏。
`FeatureAccessMigration` 兼容旧 `canSetKnife` / `canSetSafetyBox` 字段，
维护时应保留迁移而不是重命名后丢弃旧权限。

## 角色状态、医疗、倒地与体力

`PlayerStatusScreen` 仍继承原版 `InventoryScreen`，
以保留依靠该类型识别玩家物品栏的兼容入口。
`DeltaContainerLayoutController` 将玩家装备、储物区域和状态显示组合到界面中。

`PlayerInjuryManager`、`MedicalUseManager`、
`DownedManager`、`PlayerStaminaManager` 分别负责伤势、治疗、倒地和体力。
规则类描述伤害影响、治疗效果、移动限制及相应参数。
`MedicalUse*Packet`、`Downed*Packet`、`StaminaStatePacket`
以及救援网络包同步操作和状态。
HUD、医疗轮盘和角色页显示的是这些系统的客户端状态，不是独立生命值来源。

护甲与枪械相关兼容规则包含 `BallisticArmorManager`、
`BulletArmorRulesData`、`TaczCompatibilityRules` 等；
效果取决于服务器规则和实际安装的外部模组。

### 伤势与医疗处理

部位伤势和原版生命值相互关联但并非同一字段。
`InjuryImpactRule`、`LegInjuryRule`、`FallInjuryRule`、
`HealthPenaltyRule` 等将伤害或治疗结果转成具体影响；
`HealthSystemRulesData` 与 `HealthPenaltyRulesData` 保存相关世界规则。

`MedicalUseManager` 支持手持、储物来源、轮盘与自动选择入口，
维护治疗中的预留物品、进度、取消和完成状态。
自动候选评分结合当前伤势需求、生命需求、偏好、手术能力和物品优先级，
不是“永远用背包里第一个药品”。
取消与禁止动作也经过服务端路径，不能让客户端倒计时直接发放治疗结果。

`ConsumableProfile`、医疗包专用规则和 `MedicalTreatment`
描述容量及处理类型。修理包通过 `RepairKitItem.Target`
区分护甲和头盔，注册的比例为 25%、50%、75%、100% 四档。
体力、感知和负重强化物品使用各自治疗/增益类型，不能统一解释为回血药。

### 倒地、救援与搬运

`DownedManager` 配合 `DownedRules` 管理倒地时序、救援和搬运。
当前常量：红色倒地计时 50 秒对应的 tick 数、黄色窗口 3 分钟、
红/黄救援均 15 秒、救援心跳宽限 10 tick、搬起/放下预备各 2 秒、
求救冷却 10 秒、放弃长按 3 秒。这里的“秒”按 20 tick/秒换算，
不承诺低 TPS 下的实际墙钟时间。

救援持续性由 `RescueHoldPacket` 等心跳与服务端状态判断；
`CarryPositionResolver` / `CarryPoseMath` 决定搬运位置和姿态，
客户端平滑和动画只改善视觉，不改变服务端归属。
`CombatFeedClientState`、伤害方向提示和倒地 HUD 是结果展示层。

### 体力、团队与状态同步

`PlayerStaminaManager` 和 `StaminaRules` 处理体力上限、消耗和恢复，
`StaminaStatePacket` 供 HUD 使用。
角色状态包同时含布局和权限状态；仅按重量去重会漏掉模式切换，
所以状态同步必须比较这些标志。
重连、死亡、维度切换、第三方伤害来源都属于需要实际多人回归的场景。

## 轮盘、队伍与命令

标点轮盘布局通过 `DeltaSpotWheelLayoutCompat` 对接独立的 Delta Spot，
医疗轮盘复用相关大小、位置和部分交互配置。
`CommandWheelScreen` 等客户端组件负责命令轮盘及参数输入。
键位映射、长按判定和冲突处理位于客户端事件代码。

`TeamStatusSync`、`FtbTeamIntegration` 与 `TeamStatusPacket`
处理队伍信息获取和状态同步。
FTB 相关实现是有边界的联动，不意味着这里重新实现了 FTB Teams。

命令集中注册在 `ModCommands.register`。
精确命令语法、权限等级和参数类型以 Brigadier 注册树为准；
不要直接把翻译文本或界面按钮文字当作可执行命令。

### 默认按键与命令轮盘

以下是 `XeroDeltaClient` 注册默认值，玩家已有绑定以 `options.txt` 为准：

| 注册键后缀 | 默认 | 功能 |
| --- | --- | --- |
| `toggle` | 重音符键 | 安全箱叠加层开关 |
| `rotate` | T | 物品旋转 |
| `bullet_details` | 左 Ctrl | 弹药详情 |
| `effect_hud_config` | 单引号键 | 状态效果 HUD 配置 |
| `carry` | H | 搬运交互 |
| `rescue` | F | 救援交互 |
| `medical_wheel` | 5 | 医疗快捷操作/轮盘 |
| `command_wheel` | G | 命令轮盘 |
| `safety_box_config` | 反斜杠键 | 安全箱配置入口 |
| `toggle_layout` | F8 | 布局切换 |

按键分类翻译为中文 `XeroDelta三角洲`、英文 `XeroDelta`。
命令轮盘使用游戏内冲突上下文；当前默认绑定没有强制 ALT 修饰键。
绑定可改不代表任何菜单中都可触发，输入上下文仍需满足。

`CommandWheelCatalog` 从服务器下发的 Brigadier 命令树构建命令目录，
选择命令或参数不立即执行；完整命令在底部字段展示，可手工编辑再执行。
手动修改后不应继续被参数重算覆盖，恢复操作才从参数重建。
小窗口采用紧凑列表；多语言来自资源键和命令元信息，
不能保证第三方命令说明也具备翻译。

团队数据由 `FtbTeamIntegration` 获取并经 `TeamStatusSync` 同步，
客户端绘制队伍 HUD、位置、颜色、求救提示。
Delta Spot 是独立必需模组，其标点业务不归本仓库完整拥有；
这里的适配重点是轮盘布局和使用体验。

## Material 3 设置界面

`Material3PageScreen` 提供顶部标题、分类导航、滚动正文、固定底部按钮和确认弹层。
`MaterialPageLayout` 以 Minecraft GUI 像素计算布局，
不修改超分的缩放状态，也不创建 Yoga 节点。

控件复用 `Material3Button`、`Material2ToggleRow`、
`Material2Slider`、`Material3CompactEditBox` 和 `Material2Drawing`。
部分文件名保留 `Material2` 是兼容历史调用点，实际使用共享 `Material3Theme`。
颜色遵循主题令牌，图标使用已打包的位图资源。

`XeroDeltaMaterialSettingsScreen` 组织设置页面，
`MaterialSettingsState` 保留原有加载、默认值和保存映射。
数字输入保留跨页草稿，非法内容不应悄悄按旧数值保存；
最小/最大范围、颜色和音效 ID 在提交前检查。
选择主题可预览，取消返回时重新读取已保存主题。

`CorpseRulesScreen` 与 `CorpseEntityEditorScreen` 使用同一表单基础，
而 `MailItemPickerScreen` 保留专用物品网格、搜索、分类、滚动及多选逻辑。
这些屏幕不需要 Super Resolution、NanoVG 或 Yoga。

### 设置的编辑与提交

```text
读取已保存配置 -> 创建 MaterialSettingsState 草稿
切换分类/输入 -> 保留草稿并预览允许预览的表现项
保存 -> 校验范围、颜色、资源 ID -> 调用已有保存/发包路径
取消 -> 返回并恢复已保存主题
重置 -> 确认弹层 -> 修改草稿/执行对应重置路径
```

设置 UI 使用 Minecraft GUI 坐标和自带绘制，不嵌入浏览器。
交易 HTML 文件是项目解析的模板/主题资源，不等于网页引擎，
也不能因为扩展名是 `.html` 就当作 Super Resolution 的运行依赖。

### HUD 与布局编辑

`StatusEffectHudConfigScreen`、`SafetyBoxLayoutScreen`、
`ContainerUiPositionState`、`PlayerStatusPanelState` 等分别管理 HUD、
安全箱组件布局、容器位置和角色面板偏好。
保存表现位置不能改变服务器逻辑容量。
拖动、缩放、滚动等变换需要绘制与命中共用同一坐标规则，
否则会出现看得见但点不到或 Tooltip 被裁剪的问题。

## 注册内容与资源渲染

### 医疗与装备素材的权利边界

本节描述注册和渲染机制，不是素材授权证明。
医疗用品、药品、注射剂、修理包以及头盔、防弹衣、胸挂、背包等装备的
图标、贴图、模型、外观设计、名称和标识可能涉及第三方权利。
仓库没有提供逐项核实的授权清单；`OfficialItemCatalog` 等代码名称
也不代表获得官方授权、认可或背书。

项目 MIT 许可证不覆盖项目无权授权的第三方素材。
提取素材、修改后再分发、用于整合包或商业项目之前，应核实来源与许可，
必要时取得授权或替换素材；注明来源或声明学习/非商业用途不代表已经取得授权。
详见 [第三方声明](../CREDITS.txt) 和 [首页提示](../README.md)。

### 内容分类

| 内容 | 实现入口 | 说明 |
| --- | --- | --- |
| 安全箱 | `ModItems`、`SafetyBoxItem` | 五种容量及数据组件 |
| 胸挂/背包/卡包 | `DeltaPackItem`、`OfficialItemCatalog` | Curios 类型、基本容量及分区 |
| 头盔/防弹衣 | `OfficialArmorMaterials`、`TacticalArmorItem` | 原版护甲类型与自定义渲染/数值 |
| 医疗/强化/修理 | `MedicalItem`、`RepairKitItem`、`ConsumableProfile` | 治疗类型、耐久容量与修理目标 |
| 交易行/仓库/回收站 | `ModBlocks` | 三种真实方块，各有对应 BlockItem |
| 尸体 | `CorpseEntity` | 世界实体及搜刮菜单 |

`OfficialItemCatalog` 以目录数组批量注册头盔、防弹衣、胸挂和背包，
以及 `proteus_jammer`。后者注册为普通 `Item`，
仅有名称和图标并不能证明实现了干扰技能。
同理，不能把全部展示装备都宣称为带有独立战术技能。

仓库方块的世界外观由 `blockstates`、方块模型和纹理资源决定，
手持/物品栏显示由 BlockItem 模型决定；
修复一个物品模型不等于同时修复方块世界模型。

### Bedrock 与检视

`BedrockLoader` 从 JSON Reader 加载几何和动画；
`model/` 表达骨骼、立方体、面、定位点和向量；
`animation/` 处理关键帧、插值、轨道与控制器；
`molang/` 提供项目实现的表达式解析/上下文；
`render/` 负责变换、渲染过滤和控制。
这是一套按项目需求实现的运行时，不是基岩版引擎或完整 Molang 标准实现。

安全箱布局包包含 manifest、items、模型、动画和配置资源。
`SafetyBoxLayoutPack.ensureDefaultPack` 仅在默认目录不存在时复制内置资源，
并继续处理元数据和旧布局迁移；不能承诺升级永不触碰任何旧布局。
使用者自行改过的资源包应在替换前备份。

### 语言、图标与缓存

`assets/xero_delta/lang/zh_cn.json` 与 `en_us.json` 提供主要翻译，
图标包括品质、锁、操作、装备槽和效果资源。
`WidgetImageCache`、`ItemTextureAspectCache` 等避免重复加载或分析；
资源重载时要同时关注缓存失效与引用路径。

存在语言包完整性和图标资源测试，但源码仍可能有直接写入的中文文案，
例如部分邮件内容和详情标签。不能宣称所有界面和第三方文本完全双语。

## 配置、存档与兼容边界

`ConfigPaths` 统一调用 `DeltaPacksConfig`，
文件配置位于游戏配置目录下的 `delta_packs/`。
主配置名为 `xero_delta-common.toml`；
其他布局、公式、皮肤、界面偏好等按各实现的文件名保存。
`DeltaPacksConfig` 含旧路径迁移处理，不能把删除旧文件当成升级步骤。

世界数据包括 `ModDataStorage`、`PersonalWarehouseData`、
`MailData`、`TradingMarketData`、功能权限等 `SavedData`。
物品内部储物通过数据组件保存。
这三类数据的生命周期不同，备份世界时不要只备份本地设置文件。

`ModNetwork` 集中注册自定义 Payload；此快照协议版本为 `39`。
客户端与服务端应使用匹配构建。
Mixin 清单位于 `xero_delta.mixins.json`，包括原版容器、排序、Better Looting、
Sophisticated 和客户端显示等注入点。
外部模组更新后，Mixin 目标或反射接口可能改变，停止维护不等于自动兼容新版本。

### 适配清单与边界

| 外部模块 | 本项目的接入点 | 边界 |
| --- | --- | --- |
| Curios | 装备槽、标签、槽位有效性、存储访问 | 必需；槽位标签与资源需匹配 |
| Delta Spot | `DeltaSpotWheelLayoutCompat` | 必需；独立模组的业务不包含在此 |
| Better Looting | 拾取、长按、列表定位、拖拽预览 | 可选；当前并未完整实现附近物品 5x5 替换 |
| TaCZ | 尺寸、组件变体、装备文字、交互优先级、护甲规则 | 可选；依赖其数据和接口形态 |
| Sophisticated 系列 | 尺寸、背包来源、菜单与整理转移 Mixin | 可选；槽位行为需对应版本验证 |
| Inventory Sorter 等 | 排序、滚轮与点击注入 | 可选；不可绕过占格放置约束 |
| Legendary Tooltips | 品质映射、Tooltip 协调 | 可选；不是品质唯一来源 |
| FTB Teams / Quests | 团队状态、任务入口、侧栏协调 | 可选；没有重写第三方团队/任务系统 |
| 配方查看器 | `RecipeViewerIntegration`、叠加层避让 | 取决于支持的接口与安装情况 |

Mixin 分为公共和客户端清单；`required` 与注入点自己的 `require=0`、
`@Pseudo` 等条件要一起看。可选联动发生静默失效时，应查看目标类变化，
而不是直接把所有注入失败改为忽略。
所有客户端屏幕、模型与输入类都不应从纯服务端路径直接加载。

### 备份与迁移

备份至少包含完整世界、实例配置和自定义资源包。
`ModDataStorage` 仍使用历史 `safety_box_data` 标识，
大多数其他世界数据使用 `xero_delta_*`，不要按名字只备份一种前缀。
`DeltaPacksConfig` 在同名文件冲突时比较内容和修改时间，
保留差异副本为 `.migrated-*`，而不是无条件覆盖。
完整文件与数据目录见 [持久化参考](REFERENCE.md#世界持久化数据)。

## 验证与已知限制

- `verifyMaterialSettings`：原生设置依赖边界、输入/弹层接线、语言资源和 GUI 几何检查。
- `verifyCreativeRules`：创造编辑器几何、状态去重、导航和资源相关检查。
- `verifyItemDetail`：详情布局、资源与拆分/丢弃接线检查。
- 这些任务包含纯 Java 算法测试和源码/资源断言，不是完整的游戏自动操作测试。
- 完整 NeoForge `test` 任务存在已观察到的类加载问题，专项检查不能替代它。
- 本次 Material 3 迁移尚未完成游戏内截图、不同整合包和多人服务器视觉验收。
- 首次自行维护时，建议从测试存档检查容器转移、网络权限、窗口缩放和外部模组联动。

### 测试类别

| 类别 | 代表测试 | 能证明什么 |
| --- | --- | --- |
| 占格与转移 | `GridPackingPlanTest`、`GridBackingStoreSplitTest`、`SafetyBoxGridInteractionTest` | 对应算法或交互分支的断言 |
| 几何与详情 | `ItemDetailLayoutTest`、`CreativeRuleEditorLayoutTest`、`ScreenLayerResolverTest` | 输入尺寸下的布局/层级计算 |
| 规则与迁移 | `ComponentRuleKeyTest`、`SafetyBoxExpiryTest`、`FeatureAccessMigrationTest` | 规则键、有效期和旧字段行为 |
| 经济与邮件 | `TradingRulesTest`、`RecipeWorldMarketDataTest`、`MailCommandFormatterTest` | 数值、供给和格式等分支 |
| 角色系统 | `DownedRulesTest`、`PlayerInjuryManagerTest`、`StaminaRulesTest` | 时序和状态规则 |
| 资源与接线 | `LanguageBundleCompletenessTest`、`ItemDetailVisualResourcesTest`、`Material3NativeSettingsTest` | 资源存在性、语言键及源码约束 |
| 模型与命令 | `BedrockRuntimeTest`、`CommandRegistrationTest`、`CommandAliasesTest` | 模型运行时子集、注册树和别名 |

测试文件存在不等于本次运行过。此前同一源码快照的三个专项任务合计
63 项检查通过，并完成编译打包；此次是文档整理，不重复宣称运行完整 Java 测试。
完整 NeoForge `test` 的类加载问题仍然保留，资源断言也不能证明游戏内画面正确。

### 已知差异与风险

1. Better Looting 的 5x5 自动扩高需求未在当前源码中完整实现。
2. 异步计算仍读取注册表/配方，第三方线程安全与重载并发未全面验证。
3. 命令权限并非全部统一：部分规则命令未设置等级 2 门槛，
   见命令文档，公开服务器部署前需审查。
4. 部分文本仍为直接中文字符串；主要语言包存在不代表彻底消除硬编码。
5. 装箱不是全局最优算法，分类也含启发式匹配。
6. 极小窗口可能需要动作区自身滚动，不能无限保持所有控件完整可见。
7. 文档不承诺跨版本兼容、崩溃时事务回滚或完整安全审计。

### 手动回归清单

| 场景 | 应检查 |
| --- | --- |
| 新世界、旧世界、重连 | 规则同步、迁移、首次引导和权限 |
| 玩家/尸体/箱子/第三方容器 | 覆盖格点击、旋转、交换、拆分、快速移动、Tooltip |
| 安全箱到期 | 能取出，不能插入；物品不会因到期消失 |
| 仓库满格/专类拒绝 | 来源不丢失、不重复，分类切换正确 |
| 交易与邮件 | 余额、托管、售款附件、重复领取、满背包处理 |
| 创造/生存切换 | 无重量变化也更新布局，角色状态返回正确 |
| 受伤/倒地/救援 | 心跳中断、死亡/断线、搬运取消与同步 |
| GUI 缩放/语言/资源重载 | 命中和画面一致，长文本、图标、模型和层级正常 |
| 第三方组合 | 各联动开启/关闭、输入冲突、Mixin 日志 |

### 问题定位顺序

先确认双方构建和前置一致，再检查服务端权限/规则、客户端同步快照，
最后检查布局与渲染。价格问题先区分固定规则、自动规则、市场售价和回收值；
物品“消失”先确认来源、锚点、分区、分类及服务端实际状态，避免直接清空存档。
自动计算卡住时查看任务开始/失败日志与服务器提交情况，不要重复发送大量重置请求。

## 建议阅读顺序

1. `XeroDelta`、`Config`、`ModDataComponents`，了解入口及保存格式。
2. `ItemSize`、`ItemSizeRule`、`GridGeometry`、`GridBackingStore`，了解占格核心。
3. `GridActionPacket` 与对应同步包，追踪一次完整移动。
4. `DeltaContainerLayoutController`、`GridWidget`、`PlayerStatusScreen`，阅读显示与交互。
5. 再按交易、邮件、医疗等模块进入服务层及 `SavedData`。
6. 修改规则或存档结构前阅读已有测试，保留旧数据解析路径或提供明确迁移。
