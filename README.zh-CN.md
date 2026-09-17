# ME 魔源施法

[English](README.md) | [简体中文](README.zh-CN.md)

让 Ars Nouveau 施法时**直接消耗 ME 网络中的魔源**——基于 Ars Nouveau、Applied Energistics 2 与
Ars Énergistique 的联动模组。

## 简介

携带任意一台已与 **ME 无线接入点**绑定的 AE2 无线终端（AE2 原生终端、AE2WTLib 或其它附属注册的
终端都认），施法时 Ars Nouveau 的魔力将**优先由 ME 网络中的魔源支付**；网络补不上的部分才扣玩家自身魔力。

魔源存放在网络里的 **ME 魔源存储元件 / ME 魔源罐**中（由 Ars Énergistique 提供）。

本模组是**纯行为模组**：不注册任何物品、方块、GUI、配方。

## 依赖

| 类型 | 模组 | 说明 |
|---|---|---|
| 必需 | Ars Nouveau ≥ 5.13.0 | 施法方；本模组 Mixin 其 `LivingCaster` |
| 必需 | Applied Energistics 2 ≥ 19.2.0 | 无线终端绑定与 ME 存储网络 |
| 必需 | Ars Énergistique ≥ 2.1.0 | ME 网络魔源键 `arseng:source` 的注册者，**不可省略** |
| 可选 | AE2 Wireless Terminals ≥ 19.2.0 | 其注册的终端同样可被识别；不装不影响功能 |

「直接消耗 AE2 里的魔源」这一前提完全由 Ars Énergistique 提供，因此它是硬依赖：
若没有它，ME 网络里根本不存在魔源这种资源。

## 安装

1. 安装上面列出的必需模组。
2. 把 `mesourcecasting-<版本>.jar` 放进 `mods/` 目录。

## 快速开始

1. 把 AE2 无线终端绑定到 **ME 无线接入点**。
2. 确保网络里有魔源存储（ME 魔源存储元件 / ME 魔源罐）。
3. 施法——网络优先支付，网络补不上的部分才扣玩家自身魔力。

## 配置

`config/mesourcecasting-common.toml`（通用配置，客户端与服务端各自读取，不跨网络同步）：

| 键 | 默认 | 含义 |
|---|---|---|
| `payment.network_pays_full_cost` | `true` | `true`：网络优先付**整笔**花费——网络里的魔源够用时，施法完全不消耗玩家自身魔力<br>`false`：网络只补**缺口**——玩家魔力先照常扣，只有玩家付不起的部分才由网络承担；玩家魔力够付整笔时网络一点不动 |

两种设置下，「网络与玩家魔力加起来仍不足 → 不施法」这条都成立。

## 开发命令

```bash
# 构建（需 JDK 21）
./gradlew build

# 开发版客户端
./gradlew runClient
```

开发期专用依赖走 CurseForge Maven，声明为 `compileOnly` + `localRuntime`；
环境坑（Patchouli、JEI）见 [docs/troubleshooting.md](docs/troubleshooting.md)。

## 文档

- [付费模型与行为](docs/design/payment-model.md)
- [法术扣费接管点](docs/design/mixin-interception.md)
- [环境与构建坑](docs/troubleshooting.md)

## 许可

LGPL-3.0-only —— 见 [LICENSE](LICENSE)。
