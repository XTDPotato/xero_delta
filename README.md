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

- [功能实现说明](docs/IMPLEMENTATION.md)：从入口、数据、算法、操作到网络同步解释各系统。
- [目录结构与阅读路线](docs/IMPLEMENTATION.md#目录结构树)：主要包及代表性文件。
- [占格系统详解](docs/IMPLEMENTATION.md#占格系统)：锚点、尺寸、旋转、碰撞、排序和容器存储。
- [许可证](LICENSE) 与 [第三方声明](CREDITS.txt)。

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
