# 超越维度联动（实现）

> 行为语义与优先级见 [payment-model.md](payment-model.md)。这里只讲怎么接上去、为什么这么接。

## 接的是哪一层

超越维度（Beyond Dimensions，mod id `beyonddimensions`）的「维度网络」把物品、流体、FE、
通用机械化学品和**魔源**统一存在一个 `UnifiedStorage` 里。它的 Ars 集成模块把魔源注册成资源类型
`beyonddimensions:stack_type/source`，并提供一个「维度魔源通道」方块把网络暴露给 Ars Nouveau 的
`SourceManager`。

本模组**不走**那个方块，而是直接操作网络存储：

```java
DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
KeyAmount moved = net.getUnifiedStorage().extract(sourceKey, amount, simulate, false);
```

理由与 ME 那一路一致：方块路径要求玩家待在通道旁边，而本模组的既定语义是「绑一次即可用」。
网络本身是跨维度的，直接读写存储没有距离问题。

## 为什么不复用 AE2 存储元件

超越维度自带 AE2 存储元件（`net_ae_storage_cell`），把它的网络映射成 AE2 的 `SourceKey`。
把元件插进 ME 驱动器，再按 ME 那一路走，**本模组原本就能用**——不需要任何新代码。

之所以还要直接支持，是因为那条路的前提是玩家已经建起 ME 网络；只有维度网络的玩家用不上。
两条路互不冲突，同时存在时玩家任选。

## 可选依赖的隔离

超越维度是**可选依赖**（`compileOnly` + `localRuntime`，mods.toml 里 `type="optional"`）。
代码侧有两条纪律，缺一条就会在没装它的环境里炸类加载：

1. **只在 `ModList.get().isLoaded("beyonddimensions")` 为真后**才调用 `BeyondDimensionsSource`。
   该类的方法签名引用了 `DimensionsNet` / `UnifiedStorage`，一旦在未安装时被加载就会
   `NoClassDefFoundError`。
2. **魔源键按资源 ID 查，不引用 `SourceStackKey` 类**：它在超越维度的 `integration.module.ars`
   包里而不是 `api` 包里，直接引用等于把本模组绑死在对方的内部结构上。改成遍历
   `StackKeyRegistry.getAllTypes()` 找 `beyonddimensions:stack_type/source`，只依赖它的 `api` 包。

查到的键会缓存；查不中时不写缓存值，下次调用重查——类型注册发生在 `FMLCommonSetupEvent`，
而施法在游戏运行期，所以实际只会查中一次。

## 只认主网络

`getPrimaryNetFromPlayer` 而不是 `getAllNetFromPlayer`：一个玩家可能同时属于多个维度网络，
逐个去猜会让他不知道在花谁的魔源。主网络是超越维度自己的「当前网络」概念，玩家用它的
「主网络切换器」就能切换，行为可预期。

玩家不属于任何网络、或没设主网络时，这一路不参与——不算作失败，只是少一个来源。
