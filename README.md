# AE2 Convenience Features 0.7

独立的 Minecraft 1.7.10 / GTNH 2.9.0 模组，客户端和服务端均安装。

## 功能

- 携带有效的 AE2 无线终端，在 NEI 右侧、历史栏、左侧书签以及配方页按 Shift+右键取 1，Shift+左键取一组。无可提取库存但有样板时进入 AE2 原生数量/确认下单流程。
- 零库存不显示数字；有样板且具备合成权限时，物品左上角显示白色 `+`。右侧、历史栏及配方页内物品的库存数量为右下白字；左侧书签为左下绿字，均贴格子底部。配方页左侧机器列表不显示库存或加号。刷新直接替换快照，不周期隐藏数字；连续 10 秒收不到有效数据后停止显示旧数字。
- NEI 中键：右侧、历史、左侧书签/标签和配方页均支持，无需 Shift。有样板时直接进入原生下单页，即使已有库存也可以下单，不提取库存。
- 世界中键：优先选中背包已有的同物品（精确 meta/NBT），否则从 AE 提取至快捷栏并选中；没有库存但有样板则下单。不会覆盖或删除原手持物品，背包满时拒绝提取。
- F 键将悬停物品名写入 AE2 终端、接口终端、诊断终端的搜索框；兼容 GTNH 自带 AE2FC 的等级终端及无线等级终端。文本框已有焦点时不抢键。
- 主背包无线终端优先，饰品终端通过可选隔离桥支持。服务器检查注册 handler、绑定、安全站、电量、范围、EXTRACT，合成另查 CRAFT。

旧模组的样板上传、样板总成/多方块配方池适配不迁入。本模组不依赖 AE2-Auto-Pattern-Upload、NotEnoughEnergistics、TenkeiTech 或其他额外扩展。

## 安装

1. 客户端与服务端从 mods 移除旧 AE2-Auto-Pattern-Upload，备份原 jar。
2. 双端加入 `AE2 Convenience Features 0.7.jar`，删除旧版功能包。不要安装开发包 `dev.jar` 或源码包 `sources.jar`。
3. 原 GTNH 2.9.0 自带 AE2、GTNH NEI、CodeChickenLib、UniMixins 已满足必需运行条件。Baubles 为可选，AE2FC 搜索兼容没有硬类型依赖。
4. 新配置生成在 `config/ae2conveniencefeatures.cfg`。`neiActions`、`neiMiddleCraft`、`inventoryOverlay`、`worldPickBlock` 可单独关闭；`searchKeyCode=33` 表示 F，设 0 关闭。

不需要安装任何额外第三方模组。不要同时保留新旧两个功能模组，否则旧模组仍会注册自己的网络与按键处理。

## 输入共存

NEI 中键在当前 GTNH NEI 的 `GuiContainerManager.handleMouseHotkey` 入口接管，早于配方页和各模组的点击通知/下单热键。旧版 NEI 则由管理器和配方页的 HEAD 注入接管。只消费携带终端、悬停 NEI 物品的中键及其释放/拖拽；已缓存确认无样板、无终端、普通背包槽位或关闭功能时放行。冷缓存由服务端确认一次，不重放同次输入给其他模组，也不修改其他模组配置。服务端另有频率限制，并重新验证样板和权限。


在世界、生存模式、目标是可拾取形式方块、携带已注册且绑定的无线终端时，最高优先级 Forge MouseEvent 只消费本次中键按下，防止它继续触发 AE2 自带 PacketPickBlock 或已核实的同类 middleClickMouse Mixin。已被其他处理器取消的事件会立即放行。

没有无线终端、GUI 打开、创造模式、目标非方块或关闭本功能时，不改变原版输入。服务器明确拒绝且没有提取/打开下单界面时，仅回退 Forge 原版背包选中，不重放给网络取物模组。ACK 超时不重发、不回放，避免重复取物/重复下单。

已核实 AE2 977/1095、GTNHLib 0.11.9/0.11.54、Pro 中 sciencenotleisure 与 GregTech 的事件路径。未知模组若绕过 Forge 输入取消而独立轮询，仍需在该组合中实测；不承诺所有未知第三方实现均已验证。

## 构建与校验

JDK 17 运行 Gradle，Java 8 字节码输出：

```sh
bash build.sh spotlessApply build
python3 scripts/audit-artifact.py "build/libs/AE2 Convenience Features 0.7.jar"
```

`build` 自动运行实际 Minecraft 物品/NBT、Forge/Netty 编解码、缓存及手持物品守恒回归。具体运行结果、接口核实和 14 项自检随交付报告提供。构建通过不能代替 GTNH 客户端交互实测。

每次发布功能更新都递增一次版本号，统一维护根目录 `VERSION`；构建和产物审计均读取该文件。同一次更新重复构建不增加版本。上轮应计为 0.6，历史 0.5 交付原样留档；本次为 0.7，下一次为 0.8。
