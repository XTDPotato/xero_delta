# XeroDelta 配置与数据参考

> **此项目已停止更新。** 本文对应 2026-09-20 整理的源码快照。
> 默认值来自代码，不表示每个已有世界都使用这些值。

[返回首页](../README.md) · [实现说明](IMPLEMENTATION.md) · [命令参考](commands.md)

## 目录

1. [运行与构建参数](#运行与构建参数)
2. [文件配置目录](#文件配置目录)
3. [通用配置键](#通用配置键)
4. [世界持久化数据](#世界持久化数据)
5. [物品数据组件](#物品数据组件)
6. [网络协议目录](#网络协议目录)
7. [维护与兼容约定](#维护与兼容约定)

## 运行与构建参数

主要依据：[gradle.properties](../gradle.properties)、
[build.gradle](../build.gradle)、
[模组元数据](../src/main/resources/META-INF/neoforge.mods.toml)。

| 参数 | 当前值或行为 |
| --- | --- |
| Java toolchain | 21 |
| Minecraft | 1.21.1 |
| Minecraft 声明范围 | `[1.21.1,1.22)`；不等于每个版本都实测 |
| NeoForge 开发版本 | 21.1.233 |
| 模组 ID | `xero_delta` |
| 模组版本 | `0.1.0`，需结合提交辨认实际构建 |
| Curios | 元数据要求 `[9.0,)`；开发版本 9.5.1+1.21.1 |
| Delta Spot | 元数据要求 `[0.1,)`，独立模组 |
| 网络版本 | `39` |
| JAR 输出 | `build/libs/XeroDelta-1.21.1-NeoForge-0.1.0.jar` |
| 主文件源码包 | `build/distributions/` 下 `*-github-source.zip` |
| 源码备份默认目录 | `build/backups/` |
| 显式备份覆盖参数 | `-Pgithub_backup_dir=<目录>` |
| 完整测试可选前置参数 | `-Pdelta_spot_test_jar=<JAR路径>` |

Super Resolution 不再是设置 UI 的依赖。测试参数仅提供 Delta Spot 的运行依赖，
不能保证解决完整测试任务的类加载问题。常用专项构建命令见首页。

## 文件配置目录

通常位于当前游戏或服务器实例的 `config/delta_packs/`，
由 `ConfigPaths` / `DeltaPacksConfig` 统一解析。
文件不一定全部在第一次启动时生成，有些在对应功能首次读取或保存时创建。

| 相对路径 | 读写入口 | 内容与作用域 |
| --- | --- | --- |
| `xero_delta-common.toml` | `Config` | 通用配置；本地表现和游戏规则键并存 |
| `xero_delta_item_rules.json` | `ServerItemRules` | 尺寸、品质及手动/自动规则归类 |
| `xero_delta_item_weights.json` | `ServerItemWeights` | 重量规则 |
| `item_formulas.json` | `RuleFormulaConfig` | 自动推导默认值、范围和名称颜色映射 |
| `xero_delta_container_grid.json` | `ContainerGridRules` | `screens` 对象：菜单类名 → 是否启用 |
| `container_ui_positions.json` | `ContainerUiPositionState` | 客户端容器位置 |
| `delta_inventory_ui.json` | `DeltaInventoryUiState` | 客户端 Delta 物品栏表现偏好 |
| `player_status_screen.json` | `PlayerStatusScreenState` | 角色页表现状态 |
| `player_status_panel.json` | `PlayerStatusPanelState` | 角色面板表现状态 |
| `status_effect_hud.json` | `StatusEffectHudState` | 效果 HUD 布局与显示 |
| `safety_box_screens.json` | `SafetyBoxOverlay` | 安全箱屏幕相关配置 |
| `trading_ui.properties` | `TradingUiPreferences` | 客户端交易 UI 偏好 |
| `default/` | `SafetyBoxLayoutPack` | 默认安全箱布局包、manifest、items、模型、动画和配置 |

`SafeHtmlTemplate` 也使用该目录保存对应模板文件；
模板文件名由 `ConfigHtmlTemplate`、`TradingHtmlTemplate`、
`TradingOperatorHtmlTemplate`、`RecyclingHtmlTemplate` 等调用者指定。
旧的 `config/raritycore/auto/auto_rarity.json` 是
`DeltaPriceManager` 可读取的外部兼容路径，不是本项目主配置目录。

文件规则位于实例配置目录，因此同一实例的多个单人世界可能共享这些规则；
世界 SavedData 则不共享。复制世界不一定复制了尺寸和重量配置。

### 迁移与手工修改

`prepareConfigFile` 迁移旧根目录下相关 TOML，布局包还处理旧游戏根目录
`delta_packs`。相同内容去重，不同内容按时间合并并保存 `.migrated-*` 冲突副本。
这些是现有迁移行为说明，不是建议用户手工删除旧目录。

停止服务器后备份再修改业务配置；客户端外观配置也应保留原副本。
不同加载器的缓存/重载方式不同，不能假设每个 JSON 都支持即时热重载。
`ServerItemRules` 有文件修改时间/大小检查和已解析缓存，
不应由外部程序在写到一半时让服务端读取。

## 通用配置键

以下表格覆盖 `Config` 中的配置定义。路径为 `节名.键名`；
多个同类键合并列出，但每组均给出默认值和范围。
“默认”指新配置的构造值，已有配置不会因阅读本文被重置。

### 快速移动

| 键 | 默认 | 范围/含义 |
| --- | --- | --- |
| `quick_move.quickMoveEnabled` | `true` | 启用快速移动时自动放入安全箱 |
| `quick_move.quickMoveValueThreshold` | `10000` | `0..2147483647`，最低价值门槛 |

门槛通过不等于必能放入，还要满足过滤、有效期、权限和完整占格条件。

### 占格与 Tooltip

| `item_grid` 中的键 | 默认 | 范围/含义 |
| --- | --- | --- |
| `itemGridEnabled` | `true` | 占格显示与容器保护开关 |
| `gridBorderThickness` | `0.5` | `0.1..2.0` |
| `tooltipSizeMode` | `VISUAL` | `TEXT` / `GRAPHIC` / `VISUAL` / `OFF` |
| `tooltipSizeScale` | `2.0` | `0.5..3.0` |
| `tooltipSizePaddingTop` | `4` | `0..20` |
| `tooltipSizePaddingBottom` | `4` | `0..20` |
| `tooltipSizePaddingLeft` | `4` | `0..20` |
| `tooltipSizePaddingRight` | `4` | `0..20` |
| `tooltipTitleOffsetX` | `0` | `-100..100` |
| `tooltipTitleOffsetY` | `0` | `-100..100` |
| `tooltipTitleAutoOffset` | `true` | 避让其他模组的内联模型 |
| `inventoryShowEquipment` | `true` | 玩家模型显示穿戴装备 |
| `inventoryModelControl` | `MOUSE` | 模型控制字符串，支持 `MOUSE` / `DRAG` |

数值使用 GUI 坐标单位，不是固定显示器像素。无效 Tooltip 模式有回退逻辑，
但不应依赖所有任意字符串都被自动修正。

### 轮盘与求救

| 键 | 默认 | 范围/含义 |
| --- | --- | --- |
| `wheel_menu.medicalWheelHoldSeconds` | `0.75` | `0.10..3.0` 秒 |
| `wheel_menu.markerWheelSlots` | `8` | 数值配置允许 `4..8`，业务设计为 4/8 槽 |
| `wheel_menu.markerWheelHoldSeconds` | `0.75` | `0.10..3.0` 秒 |
| `wheel_menu.markerDoubleClickSeconds` | `1.0` | `0.10..3.0` 秒 |
| `wheel_menu.markerLifetimeSeconds` | `20.0` | `1..60` 秒 |
| `wheel_menu.enemyMarkerLifetimeSeconds` | `10.0` | `1..60` 秒 |
| `downed.rescueRequestSound` | `minecraft:block.note_block.bell` | 字符串最多 128 字符，设置页另行检查资源 ID |

标点功能还受独立 Delta Spot 模组的实际实现和配置影响。
按键值本身由 Minecraft 按键设置保存，不是这些长按秒数。

### Material 主题和品质色

| 键 | 默认 |
| --- | --- |
| `config_screen.theme` | `mint` |
| `config_screen.customHighlight` | `0xFF55D6B0` |
| `config_screen.customPrimary` | `0xF20B1418` |
| `config_screen.customSecondary` | `0xF2223238` |
| `quality_colors.red` | `0x66CC3333` |
| `quality_colors.gold` | `0x66D6A629` |
| `quality_colors.purple` | `0x669554D9` |
| `quality_colors.blue` | `0x664D8FD9` |
| `quality_colors.green` | `0x6658A65C` |
| `quality_colors.gray` | `0x66666666` |

颜色为 ARGB；最高字节是透明度。品质颜色解析也接受 `#`、
无前缀十六进制和不带 alpha 的短形式，失败回退灰色。
主题颜色和物品品质颜色是不同用途，修改主题不会改写品质等级。

### 安全箱叠加层布局

| 键 | 默认 | 范围/含义 |
| --- | --- | --- |
| `display.overlayFontSize` | `4` | `4..24`，历史兼容项 |
| `layout.overlayLayout` | `TOP` | `TOP` / `BOTTOM` / `LEFT` / `RIGHT` |
| `layout.overlayIconScale`、`overlayTextScale` | 各 `0.5` | `0.2..2.0` |
| `layout.overlayIconOffsetX`、`overlayIconOffsetY` | 各 `0` | `-30..30` |
| `layout.overlayTextOffsetX`、`overlayTextOffsetY` | 各 `0` | `-30..30` |
| `layout.overlayTextPadding` | `4` | `0..12` |
| `layout.overlayBgWidth`、`overlayBgHeight` | 各 `0` | `0..200`，0 自动 |
| `layout.overlayBorderPadding` | `2` | `0..10` |
| `layout.overlayGlobalOffsetX`、`overlayGlobalOffsetY` | 各 `0` | `-100..100` |
| `layout.overlayCenterX`、`overlayCenterY` | 各 `-1` | `-1` 自动；`0..100` 百分比 |
| `layout.overlayGridOffsetX`、`overlayGridOffsetY` | 各 `0` | `-30..30` |
| `layout.overlayGridScale` | `1.0` | `0.5..2.0` |
| `layout.overlayVerticalText` | `false` | 竖排 |
| `layout.overlayBorderOffsetX`、`overlayBorderOffsetY` | 各 `0` | `-30..30` |

同一行省略节名前缀的第二个键仍属于 `layout`。
布局包的分区/组件配置与这些历史叠加层参数同时存在，不应混为一张配置表。

### 物品过滤与尸体

| 键 | 默认/格式 |
| --- | --- |
| `blacklist.safetyBoxAllowlist` | `["tacz:ammo*"]` |
| `blacklist.safetyBoxBlacklist` | `["sophisticatedbackpacks:*", "travelersbackpack:*", "xero_delta:*", "tacz:*"]` |
| `corpse_rules.entityIds` | `CorpseRules.DEFAULT_MOB_ENTITY_IDS` 排序后的列表 |
| `corpse_rules.chestRigCandidates` | 默认每实体一项 `entity_id\|item_id\|1`，物品为 `DEFAULT_CHEST_RIG_ID` |
| `corpse_rules.backpackCandidates` | 空列表 |

实体 ID 与候选格式在 `CorpseRulesConfig` / `CorpseRules` 中解析，
世界规则还能在 `CorpseRulesData` 中保存。列表中的权重是相对权重，不是百分数。

## 世界持久化数据

以下列出当前所有继承 `SavedData` 的业务类。
除 `ModDataStorage.get(ServerLevel)` 由调用方传入维度外，
这些 `get(server)` 入口取主世界的数据存储。
名称对应数据存储 ID，通常写入世界数据目录的同名 `.dat`，不是 JSON。

| 数据 ID | 类 | 主要内容 |
| --- | --- | --- |
| `safety_box_data` | `ModDataStorage` | 价格映射、自动价格规则归类；保留历史 ID |
| `xero_delta_safety_box_access` | `SafetyBoxAccessData` | 玩家安全箱解锁和到期 |
| `xero_delta_knife_access` | `KnifeAccessData` | 刀具解锁及堆栈数据 |
| `xero_delta_player_feature_access` | `PlayerFeatureAccessData` | 装备修改许可、增强布局点击开关 |
| `xero_delta_player_layout` | `PlayerLayoutRulesData` | 世界玩家布局规则 |
| `xero_delta_personal_warehouse` | `PersonalWarehouseData` | UUID、名称、分类容量与物品 |
| `xero_delta_corpse_rules` | `CorpseRulesData` | 尸体寿命、可攻击、实体载具候选 |
| `xero_delta_loot_search_rules` | `LootSearchRulesData` | 带维度/位置的方块搜索覆盖 |
| `xero_delta_health_system_rules` | `HealthSystemRulesData` | 健康系统效果相关规则 |
| `xero_delta_health_penalty_rules` | `HealthPenaltyRulesData` | 胸部/救援惩罚规则 |
| `xero_delta_bullet_armor_rules` | `BulletArmorRulesData` | 子弹与护甲规则 |
| `xero_delta_raid_earnings` | `RaidEarningsData` | 尚未结算的战局收益 |
| `xero_delta_trading_market` | `TradingMarketData` | 托管、钱包、收藏、历史、账户和市场参数 |
| `xero_delta_trading_upload_rules` | `TradingUploadRulesData` | 上架/回收策略 |
| `xero_delta_recipe_world_market` | `RecipeWorldMarketData` | 有限供给、观察持有量、商品关联 |
| `xero_delta_mail` | `MailData` | 邮箱、广播、已发送与投递状态 |

这张表不包含全部玩家/实体 NBT 或内存状态。伤势、倒地、实体内容等
应继续追踪各 Manager/Entity 的保存和生命周期；不能只复制本表中的文件
就称完成了整个世界的备份。

### 关键结构示意

以下为逻辑关系，不是可以直接粘贴进 `.dat` 的 SNBT：

```text
PersonalWarehouseData
  warehouses[player UUID]
    name
    bins[category]
      rows
      items[{slot, stack}]

TradingMarketData
  listings[listing UUID]
  balances[player UUID]
  favorites[player UUID]
  unresolvedNamedAccounts[name]
  history + hiddenHistory[player UUID]
  playerListingSlots[player UUID]
  market limits

MailData
  mailboxes[recipient UUID]
  sentMail[sender UUID]
  broadcasts
  deliveredBroadcasts[recipient UUID]

RaidEarningsData
  earnings[{player UUID, amount}]
```

仓库保留旧 `rows/items` 单仓格式的迁移；权限保留旧字段兼容；
规则键保留历史哈希与组件顺序兼容。无需迁移时不要仅为命名一致而重命名数据 ID。

## 物品数据组件

注册位置：[ModDataComponents.java](../src/main/java/com/xtdpotato/xero_delta/ModDataComponents.java)。
下表 ID 均以 `xero_delta:` 为命名空间。

| ID | 值 | 持久化 | 显式网络同步 | 用途 |
| --- | --- | --- | --- | --- |
| `grid_contents` | `List<ItemStack>` | 是 | 是 | 主区域锚点列表 |
| `grid_containers` | `List<List<ItemStack>>` | 是 | 是 | 额外储物区域 |
| `box_uuid` | UUID | 是 | 否 | 箱子实例身份 |
| `grid_rotated` | boolean | 是 | 是 | 物理物品堆旋转 |
| `item_bound` | boolean | 是 | 是 | 绑定状态 |
| `item_bound_owner` | String | 是 | 是 | 绑定归属 |
| `loot_searched` | String | 是 | 是 | 搜索状态标识 |
| `safety_box_skin` | String | 是 | 是 | 外观选择 |

“显式网络同步”表示注册调用配置了组件网络编解码，不表示有独立同名 Payload。
`box_uuid` 没有在此注册显式网络同步，不应要求客户端总能读取它。
列表按锚点存储，多格物品的覆盖格不能再复制 ItemStack。

## 网络协议目录

注册来源：[ModNetwork.java](../src/main/java/com/xtdpotato/xero_delta/network/ModNetwork.java)。
`C→S` 是客户端发往服务器；`S→C` 是服务器发往客户端。
下面按功能列出所有在该注册方法中注册的 Payload 类；
这是维护目录，不是第三方可依赖的稳定公共网络 API。

### 规则、占格与检视

| Payload | 方向 | 用途 |
| --- | --- | --- |
| `SyncDataPacket` | S→C | 全局物品规则等快照 |
| `CorpseRulesSyncPacket` | S→C | 尸体规则及客户端编辑状态 |
| `CorpseRulesUpdatePacket` | C→S | 尸体规则修改 |
| `GridSyncPacket` | S→C | 安全箱格子状态 |
| `ConfigUpdatePacket` | C→S | 配置更改请求 |
| `CreativeRuleBatchPacket` | C→S | 创造编辑批量规则 |
| `GridActionPacket` | C→S | 安全箱格子操作 |
| `CarriedRotationPacket` | C→S | 光标物品旋转 |
| `OverlaySlotSyncPacket` | C→S | 叠加层槽位相关状态 |
| `ItemGridConfigPacket` | C→S | 物品占格配置请求 |
| `InspectRequestPacket` | C→S | 请求检视 |
| `InspectAnimationPacket` | S→C | 检视动画 |
| `InspectCancelRequestPacket` | C→S | 请求取消检视 |
| `InspectCancelPacket` | S→C | 取消检视通知 |
| `SafetyBoxPackReloadPacket` | S→C | 布局包重载通知 |

### 交易、仓库与邮件

| Payload | 方向 | 用途 |
| --- | --- | --- |
| `TradingActionPacket` | C→S | 市场操作、收藏等 |
| `SplitItemStackPacket` | C→S | 拆分堆叠 |
| `OpenNearbyWarehousePacket` | C→S | 打开附近仓库 |
| `OpenWarehouseCategoryPacket` | C→S | 切换仓库分类 |
| `WarehouseSourceTransferPacket` | C→S | 来源到仓库转移 |
| `WarehouseSlotCategoryTransferPacket` | C→S | 仓库槽位跨分类转移 |
| `WarehouseSafetyBoxTransferPacket` | C→S | 仓库/安全箱转移 |
| `RenameWarehousePacket` | C→S | 重命名仓库 |
| `CreativeListingPacket` | C→S | 创造来源上架 |
| `TradingSyncPacket` | S→C | 市场结果与快照 |
| `MailActionPacket` | C→S | 邮箱操作 |
| `MailSyncPacket` | S→C | 邮件结果与快照 |

### 装备与来源转移

| Payload | 方向 | 用途 |
| --- | --- | --- |
| `SafetyBoxAccessPacket` | S→C | 安全箱访问状态 |
| `SafetyBoxSelectionResultPacket` | S→C | 安全箱选择结果 |
| `SafetyBoxSelectPacket` | C→S | 安全箱选择 |
| `SafetyBoxSkinSelectPacket` | C→S | 安全箱皮肤选择 |
| `CurioSlotSwapPacket` | C→S | Curios 槽位交换 |
| `CardHolderSelectPacket` | C→S | 从已有来源装备卡包 |
| `EquippedStorageActionPacket` | C→S | 已装备储物容器操作 |
| `EquippedStorageShortcutPacket` | C→S | 已装备储物快捷操作 |
| `InventorySourceToMenuPacket` | C→S | 来源到当前菜单转移 |
| `InventorySourceToEquippedStoragePacket` | C→S | 来源到已装备储物转移 |
| `InventorySourceQuickMovePacket` | C→S | 来源快速移动 |
| `CarrierReplacePacket` | C→S | 更换载具 |
| `KnifeAccessPacket` | S→C | 刀具访问状态 |
| `KnifeSelectPacket` | C→S | 刀具选择 |
| `PlayerLayoutSlotClickPacket` | C→S | 玩家布局槽位点击 |
| `PlayerLayoutTogglePacket` | C→S | 布局切换 |
| `PlayerEquipmentSlotClickPacket` | C→S | 装备槽点击 |

### 状态、界面与搜刮

| Payload | 方向 | 用途 |
| --- | --- | --- |
| `PlayerStatusPacket` | S→C | 玩家状态和布局/权限标志 |
| `TeamStatusPacket` | S→C | 团队状态 |
| `XeroTitlePacket` | S→C | 富文本提示 |
| `GuiOpenPacket` | S→C | 打开指定客户端界面 |
| `DialogPacket` | S→C | 对话框 |
| `DownedStatePacket` | S→C | 倒地状态 |
| `CombatFeedPacket` | S→C | 战斗提示 |
| `StaminaStatePacket` | S→C | 体力状态 |
| `MedicalUseActionPacket` | C→S | 医疗操作 |
| `MedicalWheelUsePacket` | C→S | 轮盘医疗选择 |
| `ItemDetailActionPacket` | C→S | 详情页动作 |
| `ItemDetailActionResultPacket` | S→C | 详情动作结果 |
| `MedicalUseStatePacket` | S→C | 医疗进度/状态 |
| `CarryActionPacket` | C→S | 搬运动作 |
| `CorpseOpenPacket` | C→S | 请求打开尸体 |
| `CorpseStorageTransferPacket` | C→S | 尸体储物转移 |
| `DownedActionPacket` | C→S | 倒地交互 |
| `RescueHoldPacket` | C→S | 救援持续输入 |
| `RescueRequestPulsePacket` | S→C | 求救提示 |
| `DamageDirectionPacket` | S→C | 受击方向 |
| `LootSearchStatePacket` | S→C | 搜索进度 |
| `LootSearchHoverPacket` | C→S | 搜索悬停优先请求 |
| `BetterLootingStorageDropPacket` | C→S | Better Looting 储物丢弃操作 |
| `OpenGroundPackPacket` | C→S | 请求打开地面背包 |

### GridAction 动作

| 编号 | 常量 | 意图 |
| --- | --- | --- |
| 0 | `PICKUP` | 取出 |
| 1 | `PLACE` | 放置 |
| 2 | `SWAP` | 交换 |
| 3 | `RIGHT_CLICK` | 右键操作 |
| 4 | `SPLIT` | 拆分 |
| 5 | `DOUBLE_COLLECT` | 双击收集 |
| 6 | `RETURN_ORIGIN` | 回原位 |
| 7 | `COMPLETE_INVENTORY_SWAP` | 完成物品栏交换 |

请求包含动作、逻辑 x/y、Shift、旋转、容器分区索引。
客户端预览坐标不是授权，服务端仍要检查实际箱子、只读规则、
区域有效性和放置状态。动作编号、组件 Codec 或记录字段顺序变更都应视为协议变更。

## 维护与兼容约定

- 业务写入应由服务端确认；客户端缓存不应成为余额、权限或物品数量来源。
- 标识符先解析并检查注册表是否存在，再取注册对象；不要把未知 ID 当空气继续扣物品。
- 原版菜单同步与自定义 Payload 并存，不能仅检查自定义包就断言物品同步完整。
- 增加组件、NBT 或配置字段优先采用默认值兼容；变更旧格式需要迁移与测试。
- 修改注册表 ID、SavedData ID、组件键或网络版本后必须检查已有存档与双方构建。
- “用了 `enqueueWork`”只说明调度路径，不等于所有权限、长度与数量检查都正确。
- 异步任务结果回服务器线程提交；不要在后台线程随意写玩家、世界或容器。
- GitHub 发布只包含主要代码、资源、测试和这些文档，不上传实例配置、存档和私有开发记录。
