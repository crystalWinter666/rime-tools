# RIME 雾凇 服务器工具

RIME 雾凇服务器定制基础插件。

README 由 AI 生成。

面向 Fabric 服务器的模块化工具模组，当前包含：

- `teleport`：跨世界传送、地标、TPA、返回与随机传送。
- `title`：基于 LuckPerms 的玩家头衔、聊天展示与 Placeholder API 支持。

## 结构

服务端由 `RimeTools` 通过 `ModuleRegistry` 统一注册并初始化 `RimeModule`；客户端由
`RimeToolsClient` 通过 `ClientModuleRegistry` 注册并初始化 `RimeClientModule`。
界面统一使用 `client.ui.RimeUi`，GUI 入口通过 `client.ui.ClientGuiRegistry` 开放注册，
模块切换器自动收集所有已注册入口，不产生模块间 UI 依赖。

**新增一个模块只需三步**，无需修改任何公共代码：

1. 实现 `RimeModule`（服务端，可选 `RimeClientModule` 提供客户端行为）；
2. 在 `RimeTools` / `RimeToolsClient` 的注册表各注册一次；
3. 客户端模块在 `initializeClient` 中调用 `ClientGuiRegistry.register(id, label, opener)`
   注册 GUI 入口，并在 `config/rime-tools/<模块 id>.yml` 放置配置文件。

配置文件统一为 YAML 格式，一个模块一个配置文件：

- `config/rime-tools/teleport.yml`：传送模块服务端配置
- `config/rime-tools/title.yml`：头衔模块服务端配置
- `config/rime-tools/teleport-client.json`：传送模块客户端配置（客户端进程独立存储）

传送模块的数据文件（传送点、TPA 白名单、离线位置等）位于
`config/rime-tools/teleport/` 子目录，不属于配置文件。

头衔模块的 RankBoard 周榜与月榜结算配置位于 `config/rime-tools/title.yml`：

- `weekly-rank-awards.enabled`：启用周榜头衔结算。
- `weekly-rank-awards.day`：每周结算日（如 `MONDAY`）。
- `weekly-rank-awards.time`：结算时间（如 `"00:05"`）。
- `weekly-rank-awards.zone`：结算所用时区（如 `Asia/Shanghai`）。
- `monthly-rank-awards.enabled`：启用月榜头衔结算。
- `monthly-rank-awards.day`：每月结算日（如 `1`）。
- `monthly-rank-awards.time`：结算时间（如 `"00:05"`）。
- `monthly-rank-awards.zone`：结算所用时区（如 `Asia/Shanghai`）。

配置修改后需要重启服务器生效。若检测到旧布局
（`config/rime-tools/teleport/config.yml` 或 `config/rime-tools/title.properties`），
首次启动会自动迁移为新配置文件。

传送模块的性能相关配置位于 `config/rime-tools/teleport.yml`：

- `random_teleport.max_concurrent_searches`：全服同时执行的随机传送搜索上限，默认 `2`。
- `offline_player_retention_days`：离线位置保留天数，默认 `180`；设为 `0` 禁用按时间清理。
- `offline_player_max_entries`：最多保留的离线位置数量，默认 `5000`；设为 `0` 不限制数量。
- `offline_player_list_limit`：传送 GUI 返回的离线玩家数量上限，默认 `1000`；设为 `0` 不限制返回量。

## 从旧版本升级

升级到本版本后，命名空间统一为 `rime-tools`，配置统一为 YAML、一模块一配置文件。
以下变化需要留意：

### 配置文件（首次启动自动迁移）

| 旧路径                                      | 新路径                              | 迁移方式                                                                                     |
|------------------------------------------|----------------------------------|------------------------------------------------------------------------------------------|
| `config/rime-tools/teleport/config.yml`  | `config/rime-tools/teleport.yml` | 自动复制（内容为 YAML，键名不变）                                                                      |
| `config/rime-tools/title.properties`     | `config/rime-tools/title.yml`    | 自动转换（properties → YAML，键名从 `weekly-rank-awards-enabled` 变为 `weekly-rank-awards.enabled`） |
| `config/rime-tools/teleport/`（传送点等数据文件）  | 不变                               | 无需迁移                                                                                     |
| `config/rime-tools/teleport-client.json` | 不变                               | 无需迁移                                                                                     |

自动迁移只在旧文件存在、新文件不存在时发生一次；迁移后旧文件保留作备份。
如需手动迁移（例如自动迁移失败时）：

- **传送模块**：把 `teleport/config.yml` 复制为 `teleport.yml`。
- **头衔模块**：把 `title.properties` 改写为 YAML，例如：

  ```yaml
  default-title: 玩家
  default-color: "#AAAAAA"
  weekly-rank-awards:
    enabled: true
    day: MONDAY
    time: "00:05"
    zone: Asia/Shanghai
  monthly-rank-awards:
    enabled: true
    day: 1
    time: "00:05"
    zone: Asia/Shanghai
  ```

### 权限节点（不会自动迁移，需要管理员手动处理）

- **头衔模块**：所有 LuckPerms 节点从 `rime_tools.title.*` 改为 `rime-tools.title.*`。
  旧节点在新版本不再生效，已授权玩家会失去头衔权限，需重新授权：

  ```text
  /lp group default permission set rime-tools.title.title.player true
  /lp group vip permission set rime-tools.title.title.vip true
  /lp user Admin permission set rime-tools.title.admin true
  ```

  可先查询旧授权再批量替换：`/lp group default permission info rime_tools.title.*`。
- **传送模块**：旧前缀 `rime_tools.teleport.*`、`rime.teleport.*` 不再识别，
  使用标准节点 `rime-tools:xxx`（或 `rime-tools.teleport.xxx` 兼容写法）。
  旧版用权限插件按前缀授权的，需要把前缀替换为 `rime-tools.`。

### 翻译 key（仅影响自定义资源包）

客户端翻译 key 从 `rime_tools.*` 改为 `rime-tools.*`（如 `rime-tools.title.screen.title`）。
若使用资源包覆盖过默认文本，需要同步替换 key；语言文件本身由模组内置，无需操作。

### 不受影响

- PlaceholderAPI 占位符：仍为 `%rime-tools:title%`、`%rime-tools:title_id%`、`%rime-tools:title_decorated%`。
- 传送点、TPA 白名单、离线位置等数据文件。
- 头衔授予记录与周榜/月榜结算状态（保存在世界存档中）。
- 网络协议与客户端/服务端互通。

### 回滚提示

如果回滚到旧版本，新格式配置文件（`teleport.yml`、`title.yml`）与
`rime-tools.title.*` 权限节点无法被旧版识别。回滚前建议先手动恢复旧路径配置与旧权限节点。

## 命令

尖括号表示必填参数，方括号表示可选参数。`/rime` 不带参数时会打开传送点界面，
`/stp` 是 `/rime` 根命令的完整别名。

### 传送点

| 命令                                  | 说明                                        |
|-------------------------------------|-------------------------------------------|
| `/rime setp [-f] <名称> [描述]`         | 在当前位置创建私人传送点；`-f` 覆盖同名点。别名：`setpersonal`。 |
| `/rime setg [-f] <名称> [描述]`         | 在当前位置创建公共传送点；`-f` 覆盖同名点。别名：`setglobal`。   |
| `/rime tpp <名称>`                    | 传送到自己的私人传送点。别名：`tpersonal`。               |
| `/rime tpg <名称>`                    | 传送到公共传送点。别名：`tglobal`。                    |
| `/rime delp <名称>`                   | 删除自己的私人传送点。别名：`delpersonal`。              |
| `/rime delg <名称>`                   | 删除公共传送点。别名：`delglobal`。                   |
| `/rime list`                        | 列出私人和公共传送点。                               |
| `/rime listp`                       | 只列出私人传送点。别名：`listpersonal`。               |
| `/rime listg`                       | 只列出公共传送点。别名：`listglobal`。                 |
| `/rime descp <名称> <描述>`             | 修改私人传送点描述。                                |
| `/rime descg <名称> <描述>`             | 修改公共传送点描述。                                |
| `/rime gui`                         | 打开自己的传送点管理界面。                             |
| `/rime manage <在线玩家>`               | 管理指定玩家的传送点；仅管理员。                          |
| `/rime tpother <玩家名或 UUID> [私人传送点]` | 打开其他玩家的私人点列表，或直接传送到指定点；需要相应权限。            |

### 玩家传送

| 命令                              | 说明                          |
|---------------------------------|-----------------------------|
| `/rime tp <在线玩家>`               | 将自己传送到目标玩家；默认需要管理权限。        |
| `/rime tphere <在线玩家>`           | 将目标玩家传送到自己身边；默认需要管理权限。      |
| `/rime tpa <在线玩家>`              | 请求传送到目标玩家。                  |
| `/rime tpahere <在线玩家>`          | 请求目标玩家传送到自己身边。              |
| `/rime accept [玩家]`             | 接受最新请求或指定玩家的请求。别名：`allow`。  |
| `/rime deny [玩家]`               | 拒绝最新请求或指定玩家的请求。别名：`reject`。 |
| `/rime cancel`                  | 取消自己发出的待处理 TPA 请求。          |
| `/rime tpaallow <玩家名或 UUID>`    | 允许指定玩家以后直接传送到自己。            |
| `/rime tpadisallow <玩家名或 UUID>` | 从直接传送允许列表移除玩家。              |
| `/rime tpaallowlist`            | 查看自己的直接传送允许列表。              |
| `/rime back`                    | 返回上一个位置或死亡位置。               |
| `/rime last <玩家名或 UUID>`        | 传送到玩家最后一次下线的位置；需要相应权限。      |
| `/rime rtp`                     | 在当前世界随机传送。                  |
| `/rime confirm`                 | 确认一次不安全位置传送。                |
| `/rime cancelconfirm`           | 取消待确认的不安全位置传送。              |

### 管理与维护

| 命令                                                                                          | 说明                                     |
|---------------------------------------------------------------------------------------------|----------------------------------------|
| `/rime help`                                                                                | 显示传送模块帮助。                              |
| `/rime reload`                                                                              | 从磁盘重新加载配置、语言、传送点、TPA 允许列表和离线位置；仅管理员。   |
| `/rime importstp [文件] [--include-back] [--clear] [--offline-uuid\|--raw-uuid\|--auto-uuid]` | 导入兼容数据；默认文件为 `example_data.json`，仅管理员。 |
| `/rime testwp [名称]`                                                                         | 在当前位置创建测试公共传送点；仅管理员调试使用。               |

`easy_tp` 启用时，也可以使用 `/rime <传送点名或在线玩家名>` 快速匹配传送目标。

### 独立快捷命令

下列命令直接映射到对应的 `/rime` 子命令：

```text
/setp  /tpp  /delp  /listp  /descp
/setg  /tpg  /delg  /listg  /descg  /tplist
/back  /last  /tpother
/tpa  /tpahere  /tphere  /tpaccept  /tpdeny  /tpcancel
/tpconfirm  /tpcancelconfirm
/rtp  /tpr  /r
```

当 `commands.override_tp: true` 时还会注册 `/tp` 快捷命令。快捷命令接受与对应
`/rime` 子命令相同的参数。

### 头衔

头衔命令仅在服务端安装 LuckPerms 后注册。

| 命令                      | 说明                     |
|-------------------------|------------------------|
| `/title list`           | 列出所有已启用头衔。             |
| `/title select <头衔 ID>` | 选择已通过 LuckPerms 解锁的头衔。 |

#### RankBoard 周榜与月榜头衔

同时安装 RankBoard 与 LuckPerms 后，模组默认在每周一 `00:05`（`Asia/Shanghai`）
结算前一个完整周一至周日。每个 RankBoard 榜单取分数大于 `0` 的前五名，并在头衔库中
创建 `weekly_<榜单 ID>_t<名次>`，显示名称格式为 `周<榜单>T<名次>`，例如
`周大胃王榜T1`。

- 周榜 T1 在聊天和 PlaceholderAPI 中按字符使用彩虹渐变，头衔库预览色跟随 RankBoard 榜单配置。
- 周榜 T2 至 T5 使用金黄色 `#FFD700`。
- 每次周榜结算会先撤销上一周的全部周榜头衔，再发放新结果；掉出前五名会自动失去对应头衔。

每月第一天 `00:05` 结算上个月每个榜单的前十名，创建
`monthly_<榜单 ID>_t<名次>`，显示名称格式为 `<yy>年<MM>月<榜单>T<名次>`，例如
`26年7月大胃王榜T1`。

- 月榜 T1 至 T3 在聊天和 PlaceholderAPI 中按字符使用彩虹渐变；T1 头衔库预览色跟随 RankBoard 榜单配置，T2、T3 预览色为金黄色
  `#FFD700`。
- 月榜 T2、T3 使用金黄色 `#FFD700`。
- 月榜 T4 至 T10 使用绿色 `#55FF55`。
- 月榜头衔结算后不会自动收回，玩家可以长期佩戴。
- 结算日期和获奖权限会保存在世界存档中，重启不会重复发放。
- RankBoard 历史缓存或完整周期边界尚未就绪时，本次结算会延后重试。

### 客户端调试命令

这些命令只在安装了模组客户端时存在，用于测试 TPA 提示界面：

| 命令                        | 说明                         |
|---------------------------|----------------------------|
| `/rimenotify`             | 循环切换 TPA 通知样式。             |
| `/rimetest tpa <玩家> [类型]` | 创建测试 TPA 通知；类型为 `0` 或 `1`。 |
| `/rimetest result <玩家>`   | 将指定玩家的测试请求标记为已接受。          |
| `/rimetest toast`         | 一次创建两条测试通知。                |

## Placeholder API

占位符使用 Placeholder API 的 `%命名空间:路径%` 格式：

| 占位符                            | 返回内容                                        |
|--------------------------------|---------------------------------------------|
| `%rime-tools:title%`           | 当前玩家可见头衔，保留头衔颜色；没有可见头衔时返回配置中的默认头衔。          |
| `%rime-tools:title_id%`        | 当前可见头衔的稳定 ID；没有头衔时返回空字符串。仅安装 LuckPerms 时注册。 |
| `%rime-tools:title_decorated%` | 带方括号装饰和颜色的头衔，例如 `[ 管理员 ]`。                  |

示例：

```text
%rime-tools:title% %player:name%: %message%
%rime-tools:title_decorated% %player:name%
```

本模组目前只注册以上三个头衔占位符，传送模块没有注册 Placeholder API 占位符。

## 权限节点

### 传送模块

传送模块通过 Fabric Permission API 检查权限，节点是 Minecraft Identifier，格式为
`rime-tools:路径`。`默认开放`表示没有权限插件覆盖时普通玩家也可使用；`仅管理员`
表示默认要求服务器管理员权限。拥有 `rime-tools:admin` 会绕过传送模块的其他权限检查。

| 权限节点                         | 默认值  | 控制内容                                                    |
|------------------------------|------|---------------------------------------------------------|
| `rime-tools:admin`           | 仅管理员 | `/rime reload`、`manage`、`importstp`、`testwp`，并绕过所有传送权限。 |
| `rime-tools:personal`        | 默认开放 | 创建、覆盖、编辑和删除自己的私人传送点。                                    |
| `rime-tools:personal/tp`     | 默认开放 | 传送到自己的私人传送点。                                            |
| `rime-tools:global`          | 仅管理员 | 创建、覆盖、编辑和删除公共传送点。                                       |
| `rime-tools:global/tp`       | 默认开放 | 传送到公共传送点。                                               |
| `rime-tools:list`            | 默认开放 | 使用传送点列表命令。                                              |
| `rime-tools:tp`              | 仅管理员 | 使用 `/rime tp` 直接传送到其他玩家。                                |
| `rime-tools:tphere`          | 仅管理员 | 使用 `/rime tphere` 拉取其他玩家。                               |
| `rime-tools:tpa`             | 默认开放 | 发送 `/rime tpa` 请求。                                      |
| `rime-tools:tpahere`         | 默认开放 | 发送 `/rime tpahere` 请求。                                  |
| `rime-tools:tpa/allowlist`   | 默认开放 | 管理和查看 TPA 直接传送允许列表。                                     |
| `rime-tools:back`            | 默认开放 | 使用 `/rime back`。                                        |
| `rime-tools:last`            | 仅管理员 | 传送到玩家最后下线位置。                                            |
| `rime-tools:other_personal`  | 仅管理员 | 查看并传送到其他玩家的私人传送点。                                       |
| `rime-tools:rtp`             | 默认开放 | 使用随机传送。                                                 |
| `rime-tools:easy`            | 默认开放 | 使用 `/rime <名称>` 快速匹配。                                   |
| `rime-tools:crossworld`      | 仅管理员 | 允许跨世界传送。                                                |
| `rime-tools:cooldown/bypass` | 仅管理员 | 绕过所有传送冷却。                                               |
| `rime-tools:cost/bypass`     | 仅管理员 | 绕过经验和物品消耗。                                              |
| `rime-tools:safety/bypass`   | 仅管理员 | 绕过目标位置安全检查。                                             |

示例取决于服务器安装的 Fabric 权限提供器。使用支持 Identifier 节点的 LuckPerms
桥接时，授权形式类似：

```text
/lp group default permission set rime-tools:personal true
/lp group default permission set rime-tools:global/tp true
/lp user Steve permission set rime-tools:crossworld true
```

### 头衔模块

头衔模块直接使用 LuckPerms 权限节点。所有节点都需要明确授予：

| 权限节点                             | 控制内容                              |
|----------------------------------|-----------------------------------|
| `rime-tools.title.admin`         | 允许进入头衔定义管理和玩家授权界面，相当于后两个管理节点的总权限。 |
| `rime-tools.title.admin.titles`  | 新建、编辑和删除头衔定义。                     |
| `rime-tools.title.admin.assign`  | 查看已知玩家并授予或撤销其头衔。                  |
| `rime-tools.title.title.<头衔 ID>` | 解锁指定头衔，允许玩家选择并显示该头衔。              |

周榜与月榜头衔使用同一权限格式，例如
`rime-tools.title.title.weekly_food_t1` 和 `rime-tools.title.title.monthly_food_t1`。
周榜权限由结算服务自动授予并在掉出前五时自动撤销；月榜权限只增不删，不建议手动维护。

`rime-tools.title.admin` 只授予管理能力，不会自动为管理员自己解锁每个头衔；仍需
授予对应的 `rime-tools.title.title.<头衔 ID>`。

```text
/lp group default permission set rime-tools.title.title.player true
/lp group vip permission set rime-tools.title.title.vip true
/lp user Admin permission set rime-tools.title.admin true
```

## 依赖

- Fabric Loader、Fabric API、Placeholder API、Carpet：必需
- LuckPerms：启用头衔选择与管理，以及 Carpet 命令权限节点
- RankBoard：启用周榜与月榜排行榜头衔结算
- Mod Menu：提供头衔管理界面入口

## 构建

```powershell
.\gradlew.bat build
```
