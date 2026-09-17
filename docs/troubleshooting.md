# 环境 / 构建 / 运行坑

## 开发环境依赖

`runClient` / `runServer` 除模组自身依赖外，还需要若干**只存在于开发期**的模组，全部写在
`build.gradle` 的 `localRuntime` 里：

- Ars Nouveau 的硬依赖：Curios、GeckoLib、Caelus
- AE2 的硬依赖：GuideME（游戏内指南）
- JEI 与 AE2 JEI Integration —— 只用于在游戏内查看 AE2 与 ME 魔源相关配方

这些**都不是本模组的依赖**，只为了让开发环境能启动、能查配方；发布产物里不含它们。

## Patchouli 会让 `runClient` 起不来

Ars Nouveau 1.21.1 起自带手册（Worn Notebook 走内置书系统），Patchouli 只是可选联动，
因此开发环境**不再引入 Patchouli**。

引入 Patchouli 1.21-88 时，`runClient` 会在启动期以如下错误崩溃，模组本身无关：

```
ServiceConfigurationError: vazkii.patchouli.xplat.IXplatAbstractions
```

排错方向：先看是不是开发环境多带了 Patchouli，而不是去查本模组的 Mixin。
