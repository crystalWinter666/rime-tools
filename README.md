# RIME 雾凇 服务器工具 (rime-tools)

一个面向 Fabric 服务器的模块化传送与玩家称号工具集。为服务器提供一套开箱即用的 **传送**（个人/公共传送点、TPA、返回、随机传送）与
**玩家称号**（头衔选择、排行授勋、聊天展示）功能，并深度集成 Carpet 假人、LuckPerms 权限与 RankBoard 排行榜。

- **Mod ID**: `rime-tools`
- **环境**: 服务端 + 客户端（客户端为可选增强）

## 功能特性

### 传送模块（Teleport）

- **个人 / 公共传送点**：创建、传送、删除、描述、搜索、别名，支持 Unicode 名称与数量上限控制
- **TPA 系统**：`/tpa`、`/tpahere`、接受/拒绝/取消，支持 TA 允许列表（allowlist）、请求超时与重复策略
- **返回与位置记录**：死亡自动记录返回点（`/back`）、下线位置（`/last`）、他人私人传送点（`/tpother`）
- **随机传送** `/rtp`：异步安全位置搜索，支持半径、并发与尝试次数配置
- ️**安全检测**：传送前检查目标安全（虚空/水/危险方块/空间不足），支持确认（CONFIRM）或就近调整（NEARBY_SAFE）模式
- **冷却与花费**：按传送类型独立冷却；可配置经验（EXP）与物品消耗，支持跨世界额外花费
- **世界管控**：按世界白名单放行传送，跨世界传送权限独立控制
- **客户端 GUI**：传送点管理界面（搜索/编辑/删除/传送）、玩家管理、假人传送；TPA 请求 Toast 通知与快捷键
- **多语言消息**：内置 `zh_CN` / `en_US`，消息 YAML 可在 `config` 中覆盖并热重载
- **数据持久化**：传送点/允许列表/离线位置原子写入，定期自动保存；支持从 STP（SimpleTpa）数据一键导入

### 称号模块（Title）

- **玩家称号系统**：称号含稳定 ID、显示名、颜色、排序权重与启用状态，支持默认称号与回退颜色
- **LuckPerms 权限**（可选）：按称号解锁选择，管理员可授予/撤销；未安装时回退为"无权限系统"模式
- **聊天展示**：聊天中显示 `[ 称号 ] 玩家名: 消息` 前缀（自定义 chat type）
- **PlaceholderAPI 占位符**：`%rime-tools:title%`、`%rime-tools:title_id%`、`%rime-tools:title_decorated%`
- **RankBoard 排行授勋**（可选）：每周前 5 名 / 每月前 10 名自动授予对应排行称号，支持自定义结算时间与时区
- **客户端 GUI**：头衔选择 / 头衔管理 / 玩家授权三个标签页，支持搜索、编辑、删除、权重与颜色配置

### Carpet 集成

- **命令权限包装**：为所有 Carpet 命令（含扩展）自动生成 `carpet.command.<路径>` 权限节点，经 Fabric Permission API
  校验；未设置时回退到 Carpet 原有规则/OP 检查
- **假人创建者追踪**：记录 `player ... spawn` / `shadow` 创建的假人归属，实现"只有创建者能传送到该假人"

## 环境要求

| 依赖              | 版本                |
|-----------------|-------------------|
| Minecraft       | `26.2`            |
| Java            | `>= 25`           |
| Fabric Loader   | `>= 0.19.3`       |
| Fabric API      | `>= 0.156.0+26.2` |
| Placeholder API | `>= 3.1.0-beta.1` |
| Carpet          | `>= 26.2`         |

**可选**（增强功能）:

| 模组        | 作用                       |
|-----------|--------------------------|
| LuckPerms | 称号权限存储与校验（不装则称号选择/管理不可用） |
| RankBoard | 周/月排行称号自动结算              |
| Mod Menu  | 客户端配置入口                  |

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/) 并准备 Minecraft `26.2` 服务端/客户端。
2. 将 `rime-tools` 及其**必需依赖**（Fabric API、Placeholder API、Carpet）放入 `mods/` 目录；可选模组按需添加。
3. 启动服务器。首次启动会在 `config/rime-tools/` 下生成默认配置与消息文件，所有功能默认可用（管理员功能需要 OP 或权限节点）。

> 服务端与客户端均可安装本模组；仅在服务端安装时，玩家使用传送/称号的图形界面会退化为聊天文本交互。

## 模块架构

本项目采用模块化设计：所有功能以 `RimeModule` 为单位注册到 `ModuleRegistry`（服务端）与 `ClientModuleRegistry`
（客户端），由入口类统一初始化；任一模块初始化失败不会影响其他模块。

```
RimeTools (服务端入口)                RimeToolsClient (客户端入口)
├── ModuleRegistry                   ├── ClientModuleRegistry
│   ├── TeleportModule               │   ├── TeleportClientModule (GUI / Toast / 按键)
│   └── TitleModule                  │   └── TitleClientModule (称号 GUI / 按键)
└── (Carpet 集成: 权限包装、假人追踪)
```

新增模块只需实现 `RimeModule` 接口并在 `RimeTools#onInitialize` 中注册。

## 传送模块

### 命令

根命令为 `/rime`（别名 `/stp`）。绝大多数子命令也有独立快捷命令。

| 功能                | 命令                                                                                          |
|-------------------|---------------------------------------------------------------------------------------------|
| 打开传送 GUI（客户端）     | `/rime`（无参数）、`/rime gui`                                                                    |
| 创建个人/公共传送点        | `/rime setp\|setpersonal [-f] <名称> [描述...]`、`/rime setg\|setglobal ...`                     |
| 传送到传送点            | `/rime tpp\|tpersonal <名称>`、`/rime tpg\|tglobal <名称>`                                       |
| 删除传送点             | `/rime delp\|delpersonal <名称>`、`/rime delg\|delglobal <名称>`                                 |
| 列出传送点             | `/rime list`、`/rime listp\|listpersonal`、`/rime listg\|listglobal`                          |
| 修改描述              | `/rime descp\|descg <名称> <描述...>`                                                           |
| 直接传送 / 拉人         | `/rime tp <玩家>`、`/rime tphere <玩家>`                                                         |
| TPA 请求            | `/rime tpa <玩家>`、`/rime tpahere <玩家>`                                                       |
| 接受 / 拒绝 / 取消      | `/rime accept\|allow [玩家]`、`/rime deny\|reject [玩家]`、`/rime cancel`                         |
| TA 允许列表           | `/rime tpaallow <玩家>`、`/rime tpadisallow <玩家>`、`/rime tpaallowlist`                         |
| TA 屏蔽列表           | `/rime tpablock <玩家>`、`/rime tpaunblock <玩家>`、`/rime tpablocklist`                          |
| 返回死亡点             | `/rime back`                                                                                |
| 传送到下线位置           | `/rime last <玩家>`                                                                           |
| 传送到他人私人传送点        | `/rime tpother <玩家> <传送点>`                                                                  |
| 随机传送              | `/rime rtp`（快捷：`/rtp`、`/tpr`、`/r`）                                                          |
| 安全确认              | `/rime confirm`、`/rime cancelconfirm`                                                       |
| 管理他人传送点（管理员）      | `/rime manage <玩家>`                                                                         |
| 测试传送点（调试）         | `/rime testwp <名称>`                                                                         |
| 重载配置与数据           | `/rime reload`                                                                              |
| 从 STP 导入          | `/rime importstp [文件] [--include-back] [--offline-uuid\|--raw-uuid\|--auto-uuid] [--clear]` |
| 简易传送（配置开启时）       | `/rime <玩家名>`（自动判断 `/tp` 或 `/tpa`）                                                          |
| 覆盖原版 `/tp`（配置开启时） | `/tp <玩家>`                                                                                  |

可用快捷命令：`setp` `tpp` `delp` `listp` `descp` `setg` `tpg` `delg` `listg` `descg` `tplist` `back` `last` `tpother`
`tpa` `tpahere` `tphere` `tpaccept` `tpdeny` `tpcancel` `tpconfirm` `tpcancelconfirm` `rtp` `tpr` `r`。

### 权限节点

权限经 Fabric Permission API（`namespace:path` 格式）校验，可由 LuckPerms 等权限模组管理。下表"默认值"指权限**未设置**
时对该玩家的回退行为。

| 节点                           | 用途                                         | 默认     |
|------------------------------|--------------------------------------------|--------|
| `rime-tools:admin`           | 管理员（传送点管理、`/rime reload`、`/rime manage` 等） | OP 2 级 |
| `rime-tools:personal`        | 创建/删除/修改个人传送点                              | 允许     |
| `rime-tools:personal/tp`     | 传送到个人传送点                                   | 允许     |
| `rime-tools:global`          | 创建/删除/修改公共传送点                              | 拒绝     |
| `rime-tools:global/tp`       | 传送到公共传送点                                   | 允许     |
| `rime-tools:tp`              | 直接传送 `/rime tp`                            | 拒绝     |
| `rime-tools:tphere`          | 拉人 `/rime tphere`                          | 拒绝     |
| `rime-tools:tpa`             | TPA 请求                                     | 允许     |
| `rime-tools:tpahere`         | TPAHERE 请求                                 | 允许     |
| `rime-tools:tpa/allowlist`   | 管理 TA 允许列表与屏蔽列表                            | 允许     |
| `rime-tools:crossworld`      | 跨世界传送                                      | 拒绝     |
| `rime-tools:back`            | `/rime back`                               | 允许     |
| `rime-tools:last`            | `/rime last <玩家>`                          | 拒绝     |
| `rime-tools:other_personal`  | `/rime tpother`                            | 拒绝     |
| `rime-tools:rtp`             | 随机传送                                       | 允许     |
| `rime-tools:easy`            | 简易传送 `/rime <玩家名>`                         | 允许     |
| `rime-tools:list`            | 查看传送点列表                                    | 允许     |
| `rime-tools:cooldown/bypass` | 无视传送冷却                                     | 拒绝     |
| `rime-tools:cost/bypass`     | 无视传送花费                                     | 拒绝     |
| `rime-tools:safety/bypass`   | 跳过安全检测                                     | 拒绝     |

### 配置

配置文件：`config/rime-tools/teleport.yml`（服务端自动生成，修改后 `/rime reload` 生效）。

主要选项：

| 选项                                                         | 说明                                         | 默认                                            |
|------------------------------------------------------------|--------------------------------------------|-----------------------------------------------|
| `default_locale`                                           | 消息默认语言（`en_US` / `zh_CN`）                  | `en_US`                                       |
| `worlds`                                                   | 允许传送的世界白名单（空 = 全部）                         | 三个主世界                                         |
| `easy_tp`                                                  | 启用 `/rime <名称>` 简易传送                       | `true`                                        |
| `allow_unicode_names`                                      | 传送点名称允许非 ASCII 字符                          | `false`                                       |
| `waypoint_name_max_length`                                 | 传送点名称最大长度                                  | `24`                                          |
| `personal_max_waypoints` / `global_max_waypoints`          | 个人 / 公共传送点数量上限                             | `10` / `100`                                  |
| `save_interval_seconds`                                    | 自动保存间隔（0 = 关闭）                             | `120`                                         |
| `offline_player_retention_days`                            | 下线位置保留天数（0 = 不过期）                          | `180`                                         |
| `offline_player_max_entries`                               | 下线位置保留条数上限（0 = 不限）                         | `5000`                                        |
| `back_on_death`                                            | 死亡时记录返回点                                   | `true`                                        |
| `tpa_timeout_seconds`                                      | TPA 请求超时                                   | `60`                                          |
| `tpa_duplicate_policy`                                     | 重复请求策略：`REJECT` / `REPLACE`                | `REJECT`                                      |
| `tpa_request_cooldown_seconds`                             | 同一玩家对同一目标两次请求最短间隔（秒，0 = 关闭）                | `5`                                           |
| `tpa_target_chat_limit` / `tpa_target_chat_window_seconds` | 目标玩家在窗口内最多收到的请求聊天消息数（0 = 不限）               | `5` / `10`                                    |
| `confirm_timeout_seconds`                                  | 安全确认超时                                     | `15`                                          |
| `cooldown`                                                 | 各传送类型冷却（秒）                                 | waypoint `5` / tp `10` / back `10` / rtp `60` |
| `random_teleport`                                          | 随机传送开关、最小/最大半径、尝试次数、并发搜索数                  | 开启，500–5000                                   |
| `cost`                                                     | 传送花费（EXP/物品、跨世界模式与附加费）                     | 默认关闭                                          |
| `safety_check`                                             | 安全检测（`CONFIRM` / `NEARBY_SAFE`、搜索范围、禁止水域等） | 开启                                            |
| `commands.override_tp`                                     | 用本模组 `/tp` 覆盖原版命令                          | `false`                                       |

### 消息与数据

- 消息文件：`config/rime-tools/teleport/messages_zh_CN.yml`、`messages_en_US.yml`（可覆盖内置默认值，`/rime reload` 热加载）
- 数据目录：`config/rime-tools/teleport/` 下保存传送点、TPA 允许列表与下线位置数据（原子写入、自动保存）
- 旧版兼容：自动将旧版 `teleport/config.yml` 迁移为 `teleport.yml`

## 称号模块

### 命令

| 命令                   | 说明             |
|----------------------|----------------|
| `/title list`        | 列出所有已启用的称号     |
| `/title select <id>` | 选择（佩戴）一个已解锁的称号 |

> 称号的管理（新建、编辑、删除、授权）在客户端 GUI 中完成；服务端命令仅提供列表与选择。

### 权限节点（LuckPerms）

| 节点                              | 用途          |
|---------------------------------|-------------|
| `rime-tools.title.admin`        | 称号系统管理员     |
| `rime-tools.title.admin.titles` | 称号管理（增删改）   |
| `rime-tools.title.admin.assign` | 玩家授权（授予/撤销） |
| `rime-tools.title.title.<id>`   | 解锁指定称号      |

未安装 LuckPerms 时，称号选择与后台管理不可用，但已选称号仍可正常展示。

### PlaceholderAPI 占位符

| 占位符                            | 说明                      |
|--------------------------------|-------------------------|
| `%rime-tools:title%`           | 当前玩家称号（含颜色，未解锁回退默认称号）   |
| `%rime-tools:title_id%`        | 当前称号的稳定 ID（需 LuckPerms） |
| `%rime-tools:title_decorated%` | 装饰样式称号（`[ 称号 ]`）        |

### 排行授勋（RankBoard）

在 `config/rime-tools/title.yml` 中配置：

- **每周排行**：每周结算日/时间/时区可配，前 5 名获得 `周<榜单>T1..T5` 称号
- **每月排行**：每月结算日/时间/时区可配，前 10 名获得 `月<榜单>T1..T10` 称号

RankBoard 不可用时自动禁用结算并在日志中提示。

### 配置

配置文件：`config/rime-tools/title.yml`（首次启动自动生成）：

| 选项                    | 说明                                         | 默认                         |
|-----------------------|--------------------------------------------|----------------------------|
| `default-title`       | 未佩戴称号时显示的默认称号                              | `玩家`                       |
| `default-color`       | 默认称号颜色                                     | `#AAAAAA`                  |
| `weekly-rank-awards`  | 周排行授勋（`enabled` / `day` / `time` / `zone`） | 周一 00:05 Asia/Shanghai     |
| `monthly-rank-awards` | 月排行授勋（`enabled` / `day` / `time` / `zone`） | 每月 1 日 00:05 Asia/Shanghai |

旧版 `title.properties` 配置会在首次启动时自动迁移为 YAML。

## 聊天模块

聊天模块提供统一的发送者装饰与防滥用治理。频率超限或在短时间内重复发送相同/相似消息时，
可自动**警告**、**禁言**（默认）或**踢出**；自动处罚经惩罚模块持久化审计。配置位于
`config/rime-tools/chat.yml`，旧配置启动时会自动补齐新字段：

- `anti_spam.enabled`、`window_seconds`、`max_messages`：频率检测开关、窗口和阈值
- `anti_spam.action`：`WARN` / `MUTE`（默认）/ `KICK`
- `anti_spam.mute_seconds`、`state_retention_seconds`：自动禁言时长与闲置追踪状态保留时间
- `anti_spam.duplicate_detection`：相似消息检测开关、窗口、重复阈值与 `similarity_threshold`
- `message_rules.max_length`、`strip_formatting`：消息长度上限与旧式 `§` 格式码拦截
- `mute_interception.blocked_commands`：禁言期间拦截的私聊/团队通信命令，默认包含
  `msg`、`tell`、`w`、`teammsg`、`tm`
- `mute_interception.allowed_commands`：禁言期间明确允许的管理命令

拥有 `rime-tools:chat/bypass` 的玩家可绕过聊天治理规则。聊天发送者名称的前缀装饰仍由各模块
注册到聊天模块统一处理（例如头衔模块把 `[ 头衔 ]` 加到名字前）。

## 惩罚模块

提供警告、临时/永久封禁、临时/永久禁言、踢出（覆盖原版 `/kick`）、清榜与违规审计。
原有权限 `rime-tools:punish` 仍是总管理权限；也可细分授予 `punish/apply`、`punish/revoke`、
`punish/history`、`punish/clearrank` 与 `punish/reload`。违规记录持久化在
`config/rime-tools/punishment/punishments.json`；旧数组格式会自动迁移为带版本、稳定记录 ID、
状态与撤销元数据的 v2 格式，撤销和到期均不会删除审计历史。

配置位于 `config/rime-tools/punishment.yml`：

- `announce_punishments`：处罚是否向除执行者外的全服玩家广播，默认 `true`
- `history_page_size`：命令与管理界面的每页历史条数，范围 5–50，默认 `10`
- `mute_notice_cooldown_seconds`：禁言文本提示的最短间隔，默认 `3`

| 命令 | 说明 |
| --- | --- |
| `/punish warn <玩家> [原因]` | 警告玩家并写入审计记录 |
| `/punish ban <玩家> [原因]` | 永久封禁，在线玩家立即被踢出 |
| `/punish tempban <玩家> <时长> [原因]` | 临时封禁；时长支持 `30m` / `2h` / `7d` / 纯秒 |
| `/punish mute <玩家> <时长> [原因]` | 临时禁言 |
| `/punish permmute <玩家> [原因]` | 永久禁言 |
| `/punish kick <玩家> [原因]` | 踢出并记录违规（`/kick` 已覆盖为同等行为） |
| `/punish clearrank <玩家> week\|month` | 清榜：删除该玩家 stats 文件并触发 RankBoard 缓存重载，从当前与未来榜单移除 |
| `/punish unban <玩家>` / `/punish unmute <玩家>` | 撤销该玩家所有对应的生效记录 |
| `/punish revoke <记录 UUID> [原因]` | 精确撤销一条生效记录并保留审计信息 |
| `/punish list [玩家] [页码]` | 分页查看违规记录（本人无需权限，查他人需管理权限） |
| `/punish reload` | 热重载并规范化聊天与惩罚配置 |

行为说明：

- 被临时封禁的玩家登录时会被拒绝并显示剩余时间；永久封禁显示原因。
- 被禁言的玩家发送公共聊天或配置中的私聊/团队消息命令会被拦截，并弹出提示条（客户端 HUD）告知剩余时间。
- 可选客户端提供“管理”界面：搜索玩家、查看活跃状态与历史、执行处罚、按记录撤销和分页浏览；所有操作均由服务端再次鉴权。
- 封禁/禁言到期自动失效（服务端每秒清理）；清榜仅移除该玩家在 RankBoard
  的当前与未来统计来源（历史 NBT 快照保留）。

## 客户端功能（可选）

- **传送 GUI**（按键 `G`，可在按键设置中修改）：传送点管理（搜索、新建、编辑、删除、传送）、玩家传送（TPA / TPAHERE / 下线位置 /
  私人点）、在线假人传送
- **TPA Toast 通知**：收到/发出传送请求时屏幕上方弹出提示，可点击或按键（默认 `Y` 接受、`N` 拒绝）处理
- **称号 GUI**（按键 `H`）：我的头衔 / 头衔管理 / 玩家授权
- 客户端命令 `/rimenotify`：循环切换 TPA 通知样式 `TOAST → CHAT → BOTH`（配置保存在
  `config/rime-tools/teleport-client.json`）
- 模块切换器：各 GUI 左上角的模块下拉菜单，用于在传送/称号界面间切换

## 构建与开发

环境要求：JDK 25、Gradle 9.6.1（通过 wrapper 自动下载）。

```bash
# 构建（产物在 build/libs/，jar 名形如 rime-tools-1.0.0-fabric0.19.3+mc26.2.jar）
./gradlew build

# 运行测试（JUnit 5）
./gradlew test

# 启动开发服务器 / 客户端
./gradlew runServer
./gradlew runClient
```

- 依赖的 SnakeYAML 已 shade 进产物 jar，无需额外安装
- 源码结构：`src/main`（服务端逻辑）、`src/client`（客户端逻辑）、`src/test`（单元测试）
- 持续集成：GitHub Actions（`.github/workflows/build-and-release.yml`）在推送 `v*` tag 时自动构建并发布 GitHub Release

## 常见问题

- **Q: 安装后提示缺少依赖？** 请确保 Fabric API、Placeholder API、Carpet 均安装且版本满足要求，Java 版本不低于 25。
- **Q: 玩家无法使用称号选择？** 称号选择依赖 LuckPerms；请确认服务端已安装 LuckPerms 且玩家拥有
  `rime-tools.title.title.<id>` 权限。
- **Q: 传送被提示"不安全"？** 目标位置可能处于虚空、水中或空间不足；可让玩家使用 `/rime confirm` 强制确认，或由管理员配置
  `safety_check`、授予 `rime-tools:safety/bypass`。
- **Q: 如何迁移旧传送插件数据？** 将 STP（SimpleTpa）导出的 JSON 文件放入 `config/rime-tools/teleport/` 目录，执行
  `/rime importstp <文件名>`（可加 `--include-back`、UUID 模式等参数，详见 `/rime help`）。

## 许可证

本项目许可证信息见 [LICENSE.txt](LICENSE.txt)。分发或修改前请阅读许可条款。
