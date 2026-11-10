# AE2 Convenience Features

面向 **GTNH 2.9.0 / Minecraft 1.7.10** 的 AE2 与 NEI 便捷功能模组，当前版本 **0.9**。新增模组代码采用 **[0BSD](LICENSE)** 协议。

> **本项目完全由 AI 编写。** 模组功能代码、版本适配与项目文档均由 AI 完成，zeroCNM 提供需求与测试反馈。构建脚手架沿用 GTNH 模板，并保留原许可证。

## 功能

| 功能 | 使用方式 |
| --- | --- |
| NEI 库存与样板标记 | 显示 AE 正库存数量；有可下单样板时显示左上角白色 `+`。支持物品栏、历史、书签与配方页面，配方页左侧机器列表不显示。 |
| NEI 快捷取物 | `Shift + 右键` 取 1 个，`Shift + 左键` 取一组；无库存但有样板时进入 AE 下单界面。 |
| NEI 中键下单 | 悬停有样板的物品按中键，直接打开下单界面，已有库存也能下单。 |
| 世界中键选取 | 对准方块按中键：优先选中背包物品，否则从 AE 取到快捷栏；无库存但有样板时下单。没有无线终端时保留原版中键行为。 |
| F 键搜索 | 在支持的 AE 终端界面悬停物品并按 `F`，将物品名填入搜索框。支持存储、接口、诊断及 AE2FC 等级终端。 |
| 制作完成临时栏 | 实际制作完成的物品显示在同网络终端顶部，最新优先、自动续行。关闭终端或首次显示满 60 秒后清除。 |

NEI 库存、快捷取物与中键功能需要携带有效的已绑定无线终端；F 搜索和制作完成栏不要求随身携带无线终端。普通 NEI 区域使用右下白色库存数字，左侧书签使用左下绿色数字，零库存不显示。

取物与下单由服务端校验绑定、电量、范围和权限，并精确区分物品 meta/NBT。兼容处理以 GTNH 已核实的输入路径为准，其他模组组合仍需实测。

## 安装

客户端与服务端均安装 `AE2 Convenience Features 0.9.jar`，并移除旧版本。本模组只需 GTNH 2.9.0 自带的前置，无需额外安装第三方模组。各功能可在 `config/ae2conveniencefeatures.cfg` 中单独关闭。

本模组独立运行，不包含样板上传或多方块配方池识别功能；迁移时请移除旧的修改版样板上传模组，以免重复注册功能。

## 构建

使用 **JDK 17**，输出 Java 8 字节码：

```sh
git clone https://github.com/zeroCNM/AE2-Convenience-Features.git
cd AE2-Convenience-Features
bash build.sh spotlessApply build
```

正式模组位于 `build/libs/AE2 Convenience Features 0.9.jar`。不要将 `dev.jar` 或 `sources.jar` 放入游戏。

0.9 已通过 171 项回归和纯 GTNH 服务端启动验证；图形客户端交互仍需实测。[开发与兼容说明](docs/DEVELOPMENT.md) · [验证记录与实测清单](docs/VALIDATION.md)

## 参考与致谢

本项目的功能设计与实现思路参考了：

- **样板上传**：[GaLicn/AE2-Auto-Pattern-Upload](https://github.com/GaLicn/AE2-Auto-Pattern-Upload)
- **EAEP / ExtendedAE Plus**：[GaLicn/ExtendedAE_Plus](https://github.com/GaLicn/ExtendedAE_Plus)

曾基于'样板上传'开发，后独立出全部额外添加功能

本项目为独立实现，不依赖上述两个模组。

## 许可证

本项目新增的模组代码、测试和文档采用 **[0BSD（零条款 BSD）](LICENSE)**：任何人都可以免费使用、复制、修改、传播、商用或闭源，无需署名、公开源码或另行申请许可。

构建模板保留原有 [MIT 协议](LICENSES/MIT-GTNH-Template.txt)，Gradle Wrapper 保留 [Apache-2.0 协议](LICENSES/Apache-2.0.txt)；这些第三方内容仍按各自协议使用。范围与来源说明见 [NOTICE](NOTICE)。
