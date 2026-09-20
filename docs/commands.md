# XeroDelta 命令参考

> **此项目已停止更新。** 本文按当前命令注册代码整理，不是未来版本的承诺。

[返回首页](../README.md) · [实现说明](IMPLEMENTATION.md) · [配置与数据](REFERENCE.md)

## 目录

1. [命名与权限](#命名与权限)
2. [布局和游戏规则](#布局和游戏规则)
3. [物品规则与钱包](#物品规则与钱包)
4. [安全箱和刀具](#安全箱和刀具)
5. [交易行](#交易行)
6. [邮件](#邮件)、[邮件载荷](#邮件载荷)
7. [界面与提示](#界面与提示)
8. [旧命令映射](#旧命令映射)
9. [命令轮盘与排错](#命令轮盘与排错)

## 命名与权限

稳定根命令为 `/xero`，旧 `/xero_*` 保留。
`/market`、`/weight` 等短名仅在没有其他模组占用时注册；
脚本应优先使用 `/xero ...`。

约定：`<参数>` 必填，`[参数]` 可选，`a|b` 表示二选一，
括号本身不输入。连续可选参数必须按注册顺序填写，不能任意跳项。

**权限并不统一。** `xero_set`、刀具管理、邮件管理等分支有等级 2 检查；
当前价格/尺寸/品质的部分修改、自动计算和重置注册路径没有统一
`hasPermission(2)` 门槛，`getData` 也不补做该检查。
这不是建议开放给普通玩家，而是当前源码风险说明。
本文不修改权限；公开服务器部署前需单独审查管理入口。

选择器权限、执行者是否为玩家、目标界面和服务类业务校验还能限制执行；
“没有注册门槛”不等于任意上下文都成功。别名复制保留已有分支权限。

源码：
[ModCommands](../src/main/java/com/xtdpotato/xero_delta/command/ModCommands.java)、
[CommandNames](../src/main/java/com/xtdpotato/xero_delta/command/CommandNames.java)、
[CommandAliases](../src/main/java/com/xtdpotato/xero_delta/command/CommandAliases.java)。

## 布局和游戏规则

除 `help` 外，本表映射到等级 2 的管理分支。

| 命令 | 作用/范围 |
| --- | --- |
| `/xero help` | 根功能入口，不是所有参数组合 |
| `/xero layout <true\|false>` | 世界布局规则 |
| `/xero layout click <true\|false>` | 增强布局点击交互 |
| `/xero health effects <multiplier>` | 效果倍率 `0..10` |
| `/xero health retain <true\|false>` | 健康效果保留规则 |
| `/xero health penalty chest <percent>` | 胸部惩罚 `0..90` |
| `/xero health penalty yellow_rescue <percent>` | 黄色救援惩罚 `0..90` |
| `/xero corpse lifetime <minutes>` | 尸体寿命 `1..1440` 分钟 |
| `/xero corpse attackable <true\|false>` | 生物尸体可攻击 |
| `/xero loot search <true\|false>` | 面向目标的搜索规则 |
| `/xero loot search <true\|false> <x> <y> <z>` | 指定方块搜索规则 |
| `/xero loot search <true\|false> <entity_target>` | 指定实体搜索规则 |
| `/xero loot search <true\|false> entity <target>` | 实体形式的显式分支 |
| `/xero stamina set <targets> <maximum>` | 体力上限 `1..100000` |
| `/xero reward give <targets> <currency>` | 增加战局收益，至少 1，受货币上限约束 |
| `/xero reward clear <targets>` | 清除当前战局收益 |
| `/xero evacuate <targets>` | 撤离收益结算路径 |
| `/xero equipment access <targets> <true\|false>` | 装备更换权限 |
| `/xero bind <targets> <true\|false>` | 目标手持物品绑定 |
| `/xero bullet <gray> <green> <blue> <purple> <gold> <red> [item] [mode] [durability]` | 六品质子弹/护甲规则 |
| `/xero market policy <up\|recycle\|none> <item> [mode] [durability]` | 上架/回收策略 |

`targets` 为玩家名或玩家选择器。收益不等于钱包余额。
没有目标参数的分支由处理器决定执行者/当前世界的作用范围，
不能自行追加 `@a`。复杂参数使用服务器 Tab 补全核对顺序。

## 物品规则与钱包

### 价格与余额

```text
/xero price auto
/xero price reset
/xero price set <value> <type> [durability]
/xero price get <true|false|*> [item]
/xero price remove <true|false|*> [item]
/xero price add <targets> <currency>
/xero price set <targets> <currency>
/xero price get <targets> [this_type|all_type] [item]
```

`set <value> <type>` 修改执行者手持物品规则，值范围 `-1..999999999`；
`-1` 删除对应价格规则，不是负售价。
带目标的 `add/set/get` 是钱包及目标价格入口，要求等级 2；
钱包写入范围从 0 到当前货币上限。
`price reset` 不清空玩家余额。

### 尺寸

```text
/xero size auto [rotate] [stretch] [type] [durability]
/xero size reset
/xero size set <length> <width> [rotate] [stretch] [type] [rangeOrItem] [item]
/xero size get <true|false|*> [item]
/xero size remove <true|false|*> [item]
```

尺寸两个维度均为 `1..10`。`rangeOrItem` 在耐久模式下为区间，
否则可为物品 ID；显式 ID 按补全填写。尺寸不是屏幕像素。
旋转支持 `rotate` / `false`，纹理策略支持 `stretch` / `false`、
`prop1` / `prop2` 等代码支持的比例模式。
无额外参数的手动设置默认 rotate/stretch，自动路径的默认纹理策略不同。

### 品质、重量和匹配

```text
/xero quality auto
/xero quality reset
/xero quality set <quality> <type> [durability]
/xero quality get <true|false|*> [item]
/xero quality remove <true|false|*> [item]
/xero weight auto
/xero weight set <weight> <mode> [item]
/xero weight get <mode> [item]
```

品质为 `gray`、`green`、`blue`、`purple`、`gold`、`red`，
旧 `common`、`rare`、`epic`、`legendary`、`mythic` 仍有兼容路径。
重量单位千克，设置范围 `0.001..1000000`，set/auto 要求等级 2。
没有 `/xero weight reset` 注册入口。

| 参数 | 含义 |
| --- | --- |
| `this_type` | 当前组件类型，类型键忽略原版当前磨损 |
| `all_type` | 整个注册 ID |
| `this_durability` | 耐久范围规则，需给区间 |
| `durability` | 如 `0..max`，由 `DurabilityRange` 解释 |
| `item` | 如 `minecraft:paper`，不是显示名称 |
| `true` / `false` / `*` | 旧式 get/remove 等分支保留的匹配字面量 |

不同命令支持的模式集合并不完全相同，尤其旧式 get/remove 与重量分支。
规则变体键不能直接作为注册 ID 参数输入。

### 全部自动计算和重置

```text
/xero itemrules auto
/xero itemrules reset
```

作用于**品质、尺寸、价格**，不包含重量、仓库、邮件或玩家资产。
后台单线程同一时刻只接受一项；忙碌时等待，不要反复提交。
开始反馈只表示已提交，完成/失败反馈和日志才是结果依据。
这些命令会修改现有规则，先备份，不用作普通卡顿的反复清理手段。

## 安全箱和刀具

```text
/xero safety reload
/xero safety unlock <targets> <item_id> <days>
/xero safety lock <targets> <item_id>
/xero knife unlock <targets> [item_id]
/xero knife lock <targets> [item_id]
```

reload 是安全箱包重载，不是全局 reload。
安全箱授权和刀具管理要求等级 2，安全箱天数 `1..36500`。
重复解锁经过有效期合并；省略刀具 ID 时取命令来源的手持物品，
控制台没有手持物品。解锁和当前位置是否允许更换装备是两项检查。

## 交易行

### 查询和购买

```text
/xero market
/xero market list
/xero market info <listing_id> [to <listing_id>]
/xero market buy <listing_id> [amount]
/xero market detail [item]
/xero market balance
```

无子命令打开军需/运营入口；普通市场可用 `/xero gui open @s trading_market`。
`info` 必须有条目 ID；`listing_id` 是市场公开编号，不是物品 ID。
购买数量默认 1、范围 `1..640`，还受库存/余额/接收条件约束。
`balance` 沿用 `xero_trade info` 的世界交易信息入口；
查询特定玩家钱包使用带目标的 `price get`。

### 管理上架

以下要求等级 2：

```text
/xero market up this_type <amount> <price_or_offset> [durationDays]
/xero market up world [item] this_type <amount> <price_or_offset> [durationDays]
/xero market up <sellerOrItem> this_type <amount> <price_or_offset> [durationDays]
/xero market up <seller> <item> this_type <amount> <price_or_offset> [durationDays]
/xero market down <listing_id> [to <listing_id>]
/xero market relist <listing_id> [to <listing_id>]
```

数量 `1..9999`，期限 `1..3650` 且不能超过当前世界上限。
省略物品取来源手持；省略卖方使用世界账户。
单个可解析注册 ID 参数视为物品，其他名字/选择器按卖方解析。
管理上架不是普通玩家扣除来源的同一流程。

| `price_or_offset` | 含义 |
| --- | --- |
| `10000` | 绝对价格 |
| `+500` | 估值加 500 |
| `-200` | 估值减 200 |
| `-200..500` | 随机估值偏移区间，不是绝对售价区间 |

### 配置和补货

```text
/xero market config max_duration <days>
/xero market config max_listings <count>
/xero market config max_player_slots <count>
/xero market config slot_level_cost <levels>
/xero market config max_currency <amount>
/xero market slots unlock <targets>
/xero market slots set <targets> <count>
/xero market auto remove all
/xero market auto add <all|item> <baseAmount> <priceOffset> <amountOffset> <priceAdjust>
```

全部为等级 2 分支。范围：
max_duration `1..3650`，max_listings `1..4096`，
max_player_slots/slots set `1..256`，slot_level_cost `0..10000`，
max_currency `1..2000000000`，baseAmount `1..9999`，
priceAdjust `-100..10000`（百分比）。
两个 Offset 按范围解析器读取。自动删除/补货操作市场，不清理世界掉落实体。

## 邮件

```text
/xero mail
/xero mail send <targets> <mailJson>
/xero mail read <targets> <mailId>
/xero mail limit get
/xero mail limit set <max>
```

无参数打开自己的邮箱；send/read/limit 要求等级 2；
邮箱上限 `1..2000`。mailId 是邮件 UUID，不是订单号或附件 UUID。
命令 send 使用在线玩家选择器；写信界面的名字/离线账户/历史广播是另一条路径，
不能在 JSON 里写收件人来替代 targets。
创造写信网络包检查创造模式，与命令等级 2 不完全相同。

## 邮件载荷

| 顶层字段 | 类型/限制 |
| --- | --- |
| `title` | 字符串，最多 256 |
| `sender` | 字符串，最多 64，默认执行者 |
| `text` | 字符串，最多 8192 |
| `attachments` 或 `attachment` | 数组，最多 64 项；同时出现优先 `attachment` |

| 附件 `type` | 主要字段 | 作用 |
| --- | --- | --- |
| `item` | `item` 或 `id`；`count` 或 `amount` | 注册物品，数量上限 9999 |
| `currency` / `coin` / `money` | `amount` | 货币 |
| `experience` / `xp` / `experience_points` | `amount` | 经验点 |
| `level` / `levels` / `experience_levels` | `amount` | 经验等级 |
| `recipe` / `rei` / `jei` | `item` 或 `value`，可选 `label` / `icon` | 配方入口，不发放该物品 |
| `ftb_task` / `task` | `task` 或 `value`，可选 `label` / `icon` | 任务入口，不强制完成任务 |

也支持 `"minecraft:paper*3"`、`"currency:1000"`、`"xp:10"`、
`"level:1"` 等字符串附件。未知类型/物品可能被跳过，
JSON 能解析不表示全部附件都被接受。

示例会实际发放附件，只在授权测试世界使用：

```text
/xero mail send @s {"title":"测试邮件","sender":"服务器","text":"请检查附件。","attachments":[{"type":"item","item":"minecraft:paper","count":3},{"type":"currency","amount":1000}]}
```

JSON 是最后的贪婪字符串，可含空格。
写信网络包的收件人上限 1024、JSON 上限 16384，
不代表聊天栏也能输入同样长度。领取由服务端检查状态和接收条件，
不要复制邮件 NBT 来重复投递。

## 界面与提示

```text
/xero gui open <targets> <screen>
/xero dialog open <targets> <options> <data>
/xero dialog close <targets>
/xero notice actionbar <targets> <position> <content>
```

gui open 对打开他人界面有处理器权限检查；dialog/notice 为等级 2。
打开界面不能绕过装备、市场或转移校验。

`screen` 稳定 ID：
`trading_market`（交易行）、`recycling`（回收）、`trading_operator`（军需/运营）、
`safety_box`（安全箱选择）、`knife`（刀具选择）、`card_holder`（卡包选择）、
`player_status`（角色）、`effect_hud`（效果 HUD）、`mail`（邮箱）。
`GuiScreenTarget` 还接受部分别名，脚本推荐稳定 ID。

dialog open 将剩余字符串按首个空白分成 options 和 data，
两部分必须都有内容，并受 `DialogPacket` 长度限制。
数据由客户端解析器消费，不是任意系统命令执行接口。

position 可为 `top|center`、`top|left`、`top|right`、`center`、`left`、
`right`、`bottom|center`、`bottom|left`、`bottom|right`。
此处竖线是字面量的一部分，不是二选一。
富文本提示支持项目解析的 `[icon:...]`、`[translate:...]` 等标记，
必须有实际图标和翻译资源。

## 旧命令映射

覆盖 `CommandNames.ROUTES`；较长旧前缀优先匹配，尾部参数保留。

| 旧前缀 | 新前缀 |
| --- | --- |
| `xero_set layout` | `xero layout` |
| `xero_set layout_click` | `xero layout click` |
| `xero_set effect` | `xero health effects` |
| `xero_set re_effect` | `xero health retain` |
| `xero_set health_penalty` | `xero health penalty` |
| `xero_set corpse_lifetime` | `xero corpse lifetime` |
| `xero_set mob_corpse_attackable` | `xero corpse attackable` |
| `xero_loot_search` | `xero loot search` |
| `xero_set block_search` | `xero loot search` |
| `xero_set stamina` | `xero stamina set` |
| `xero_set coin_give give` | `xero reward give` |
| `xero_set coin_give remove` | `xero reward clear` |
| `xero_set player_evacuate` | `xero evacuate` |
| `xero_set allow_change_bc` | `xero equipment access` |
| `xero_set item_bound` | `xero bind` |
| `xero_set item trading upload` | `xero market policy` |
| `xero_set bullet` | `xero bullet` |
| `xero_price` | `xero price` |
| `xero_price reset_all` | `xero price reset` |
| `xero_size` | `xero size` |
| `xero_size reset_all` | `xero size reset` |
| `xero_quality` | `xero quality` |
| `xero_quality reset_all` | `xero quality reset` |
| `xero_weight` | `xero weight` |
| `xero_safety_box` | `xero safety` |
| `xero_knife` | `xero knife` |
| `xero_all qsq` | `xero itemrules auto` |
| `xero_all reset` | `xero itemrules reset` |
| `xero_trading` | `xero market` |
| `xero_trading_detail` | `xero market detail` |
| `xero_trade info` | `xero market balance` |
| `xero_mail` | `xero mail` |
| `xero_gui` | `xero gui` |
| `xero_dialog` | `xero dialog` |
| `xero_title` | `xero notice` |

## 命令轮盘与排错

轮盘从服务器命令树读取分支；选中不执行，补参数或手工编辑后再确认。
手工编辑后停止参数自动覆盖，恢复按钮才重新生成。
默认 G，可在按键设置修改，没有写死 ALT 前缀。

| 现象 | 检查 |
| --- | --- |
| 找不到短命令 | 使用 `/xero`；其他模组可能占用短名 |
| 轮盘缺分支 | 下发命令树、权限、双方版本 |
| 无物品错误 | 是否依赖手持物品，控制台是否缺 item 参数 |
| 参数红色 | 顺序、耐久模式、注册 ID、是否跳过可选前项 |
| 自动计算无新开始 | 已有任务、忙碌/失败日志 |
| 邮件已发但余额不变 | 货币附件尚未领取或领取失败 |
| 重置后重量不变 | 全部规则重置不含重量 |

`CommandRegistrationTest` 可生成 `build/reports/commands.md`，
包含可执行参数组合；这是本地构建产物，不作为发布仓库中的链接目标。

```powershell
.\gradlew.bat --no-daemon --max-workers=1 test --tests "*CommandRegistrationTest"
```

完整测试目前有类加载问题，上述是生成入口，不是本次运行成功声明。
生成失败以源码注册树为准，不能把旧报告当成本次结果。
