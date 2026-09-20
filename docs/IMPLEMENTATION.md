# XeroDelta 功能实现说明

> **此项目已停止更新。**
> 本文说明 2026-09-20 发布快照中的主要实现，供阅读源码和自行维护。
> 不是后续开发承诺，也不代表所有功能均经过完整游戏内回归测试。

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
|   `-- IMPLEMENTATION.md
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
    |   |   |-- bedrock/                # 模型与动画资源处理
    |   |   |-- block/                  # 方块注册与实现
    |   |   |-- client/                 # 客户端状态、叠加层、渲染和输入
    |   |   |-- command/                # 管理命令与后台计算调度
    |   |   |-- compat/                 # 特定模组的联动入口
    |   |   |-- container/              # 通用容器相关逻辑
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
    |   |   |-- pricing/                # 价格计算相关实现
    |   |   |-- quality/                # 品质相关实现
    |   |   |-- screen/                 # 配置、交易、仓库、角色等界面
    |   |   |   `-- material/           # 自带 Material 3 绘制和控件
    |   |   |-- sizing/                 # 尺寸计算相关实现
    |   |   |-- slot/                   # 槽位定义与约束
    |   |   |-- tag/                    # 模组标签
    |   |   |-- trading/                # 交易、回收、分类和配方市场
    |   |   `-- util/                   # 共用辅助函数
    |   `-- resources/
    |       |-- META-INF/neoforge.mods.toml
    |       |-- xero_delta.mixins.json
    |       |-- assets/xero_delta/
    |       |   |-- lang/               # 简体中文、英文语言资源
    |       |   |-- models/
    |       |   `-- textures/
    |       |-- assets/minecraft/
    |       |-- assets/curios/
    |       `-- data/                   # 配方、标签、Curios 等资源
    `-- test/java/com/xtdpotato/xero_delta/
        `-- ...                        # 算法、资源、接线及集成相关测试
```

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

Better Looting 的原始列表及交互通过专门的 Mixin 和兼容层接入，
附近物品以占格容器形式组织，不是仅改变文字列表样式。
地面背包使用 `GroundPackMenu` / `GroundPackScreen` 等实现。
不要把客户端显示的一个占格条目当成新的世界掉落实体副本。

## 搜索与物品详情

`LootSearchManager`、`LootSearchRules` 和搜索状态网络包负责搜索规则与进度，
客户端显示相应遮罩或状态。
搜索相关数据与物品实际内容分开处理。

`ItemDetailOverlay` 负责物品详情弹层及操作，
`ItemDetailActionPacket` 将需服务端执行的动作提交处理。
详情布局、模型预览和底部操作区域分别计算；长内容可以滚动，
不应通过滚动整个窗口把底部按钮推出屏幕。
收藏键的生成和收藏显示也有独立逻辑，不能仅按显示名称区分物品。

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

## 创造模式规则编辑

`CreativeItemRuleEditor` 为有权限的创造玩家提供品质、尺寸和价格三个页面。
每页独立保留待提交项，支持编辑开关、单选、多选、框选和撤销/重做。
通过 `CreativeRuleBatchPacket` 批量提交，不是直接在客户端修改全服规则。

创造界面的显示条件直接检查当前屏幕、游戏模式和权限，
不依赖重量包抵达后才显示。
从创造物品栏打开角色状态时保留返回目标及创造菜单，
返回按钮或 Esc 回到原界面；正常生存背包的关闭逻辑与此入口区分。

## 个人仓库

`PersonalWarehouseData` 是按玩家保存的世界 `SavedData`，
保存分类仓库及其中的物品。
`PersonalWarehouseContainer` / `PersonalWarehouseMenu` 对接菜单，
`PersonalWarehouseScreen` 显示分类、物品格和滚动区域。

`WarehouseTransferService` 及 `Warehouse*Packet`
处理从玩家物品栏、安全箱或其他受支持来源到仓库的转移。
`WarehouseNameRules` 对名称输入做约束。
仓库内容不是写在客户端设置 JSON 中，也不等同于视觉上的仓库方块模型。

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

## 邮件

`MailMessage` 和 `MailAttachment` 表达邮件及附件，
`MailData` 保存玩家邮件、发送记录和投递相关状态，
`MailService` 处理投递、读取、领取、删除等操作。
`MailPayloadParser` 和 `MailCommandFormatter` 用于命令/载荷转换。

客户端通过邮件列表、写信、附件选择和已发送界面操作，
通过 `MailActionPacket` / `MailSyncPacket` 与服务器交换数据。
附件选择器只产生选择结果，实际领取和转移由邮件服务端路径决定。
对重复领取、历史广播或撤回行为，应追踪服务数据状态，而非依赖客户端按钮。

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

## 验证与已知限制

- `verifyMaterialSettings`：原生设置依赖边界、输入/弹层接线、语言资源和 GUI 几何检查。
- `verifyCreativeRules`：创造编辑器几何、状态去重、导航和资源相关检查。
- `verifyItemDetail`：详情布局、资源与拆分/丢弃接线检查。
- 这些任务包含纯 Java 算法测试和源码/资源断言，不是完整的游戏自动操作测试。
- 完整 NeoForge `test` 任务存在已观察到的类加载问题，专项检查不能替代它。
- 本次 Material 3 迁移尚未完成游戏内截图、不同整合包和多人服务器视觉验收。
- 首次自行维护时，建议从测试存档检查容器转移、网络权限、窗口缩放和外部模组联动。

## 建议阅读顺序

1. `XeroDelta`、`Config`、`ModDataComponents`，了解入口及保存格式。
2. `ItemSize`、`ItemSizeRule`、`GridGeometry`、`GridBackingStore`，了解占格核心。
3. `GridActionPacket` 与对应同步包，追踪一次完整移动。
4. `DeltaContainerLayoutController`、`GridWidget`、`PlayerStatusScreen`，阅读显示与交互。
5. 再按交易、邮件、医疗等模块进入服务层及 `SavedData`。
6. 修改规则或存档结构前阅读已有测试，保留旧数据解析路径或提供明确迁移。
