# ME Source Casting

让玩家施法时**直接消耗 ME 网络中的魔源**——基于 Ars Nouveau、Applied Energistics 2 与
Ars Énergistique 的联动模组。

## 功能

携带任意一台已与 **ME 无线接入点**绑定的 AE2 无线终端（AE2 原生终端、AE2WTLib 或其它附属
注册的终端都认），施法时 Ars Nouveau 的魔力将**优先由 ME 网络中的魔源支付**；网络补不上的
部分才扣玩家自身魔力。

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

开发环境（`runClient` / `runServer`）另需 JEI 与 AE2 JEI Integration，二者只用于在游戏内查看
AE2 与 ME 魔源相关配方，**不是本模组的依赖**，因此只出现在 `build.gradle` 的 `localRuntime` 里。
同理，Patchouli 已从开发环境中移除：Ars Nouveau 1.21.1 起自带手册，Patchouli 只是可选联动，
且 1.21-88 版本在 dev 环境会让 `runClient` 以
`ServiceConfigurationError: vazkii.patchouli.xplat.IXplatAbstractions` 崩溃。

## 行为细则

### 配置

`config/mesourcecasting-common.toml`（通用配置，客户端与服务端各自读取，不跨网络同步）：

| 键 | 默认 | 含义 |
|---|---|---|
| `payment.network_pays_full_cost` | `true` | `true`：网络优先付**整笔**花费——网络里的魔源够用时，施法完全不消耗玩家自身魔力<br>`false`：网络只补**缺口**——玩家魔力先照常扣，只有玩家付不起的部分才由网络承担；玩家魔力够付整笔时网络一点不动 |

两种设置下，「网络与玩家魔力加起来仍不足 → 不施法」这条都成立。

### 优先级

`已绑定网络的魔源 → 玩家自身魔力`，网络优先。默认配置（付整笔）下，只要网络里有足够魔源，
玩家魔力一点不掉；切到「只补缺口」后，玩家魔力会先照常消耗，网络只兜住玩家付不起的部分。

### 取用范围

绑定**一次即可**：不做接入点距离校验，只要终端绑定过的接入点方块仍是有效网格节点就可用。
（这是刻意的设计选择，而非沿用 AE2 原版无线终端的距离限制。）

### 失败时的表现

以默认配置（网络付整笔）为例：

- 网络魔源够付整笔 → 全部由网络支付，玩家魔力不掉
- 网络不够付整笔 → 网络先付它付得起的部分，**余额由玩家魔力补**；玩家魔力也不足则由
  Ars Nouveau 原版报 `no_mana`，法术不放
- 未绑定终端 / 绑定的接入点已不在 / 网络里没有魔源存储 → 各自给出对应提示
- 极端情况：预检通过后、扣费前网络被抽空 → 网络付出的部分为 0，整笔回落玩家魔力，
  法术已经结算放出。成因见下方「与 Ars Nouveau 原生时序的关系」

切到「只补缺口」后把上面第 1、2 条换成：玩家魔力够付整笔则网络不动；玩家魔力不足则由网络补缺口。

## 实现要点

Mixin 目标是 Ars Nouveau 的 `api/spell/wrapped_caster/LivingCaster`：

- `enoughMana(int)` —— 在返回 `false` 时补判「玩家魔力 + 网络可补魔源 ≥ 花费」，够则改为 `true`；
  **只做模拟不实扣**（该判定会被 `SpellBow` 探测多次，实扣会重复扣源）
- `expendMana(int)` 内的 `mana.removeMana(totalCost)` —— 用 `@WrapOperation` 精确替换：先算魔力缺口并
  从网络抽取对应魔源，再让玩家只付自己付得起的那部分；**网络抽取必须发生在扣玩家魔力之前**，
  否则玩家魔力会先被扣空、网络只在见底后才出力，表现为「优先消耗玩家魔力」

**为什么必须 Mixin 而不是用事件**：不是"事件没触发"，而是**事件挂不到"判定"和"扣费"这两个动作上**。
`SpellCostCalcEvent.Pre` 只在 `SpellResolver.getResolveCost()` 里触发，`Post` 只在
`getExpendedCost()` / `LivingCaster.expendMana` 里触发——两者都是**试算花费**的时点，不是判定或扣费的时点。

而弓与弩的落点各自绕开了它们：

- `SpellBow`：调 `resolver.canCast(...)`，内部是 `SpellResolver.enoughMana → getResolveCost`，**能**覆盖
- `SpellCrossbow.java:99-100`：先 `resolver.getExpendedCost()` 试算，再直接调
  `context.getCaster().enoughMana(cost)`——后者是 `LivingCaster` 上的**普通方法**，没有任何事件可监听，
  而且事件顺序与其它路径相反（`Post` 先于判定触发）

因此纯事件方案要么覆盖不到弓/弩的判定（导致这两条路径无法用网络魔源），要么为了覆盖而零化花费，
反而破坏「网络优先、网络不够才用玩家魔力」和「都不足则不施法」两条语义。

**为什么用 `@WrapOperation` 而不是 `@Inject` + `@Local`**：`@Local` 只能在变量已被赋值的位置捕获。
`expendMana` 里的 `mana` 是方法体第一行才赋值的局部变量，注入点在 `HEAD` 时它尚不存在——实测会因
「0 candidate variables but exactly 1 is required」直接让 Mixin 挂载失败；而注入点在 `RETURN` 时，
读到的 `getCurrentMana()` 已经是**扣费之后**的值（`removeMana` 已执行），差额会被算成全额、让网络替
玩家多付一份。`@WrapOperation` 正好在扣费调用点拿到未扣除的魔力，语义准确且无需取消整段方法。

结论：唯一稳定的接管点就是 `LivingCaster` 的 `enoughMana` / `expendMana` 这一对方法。

### 与 Ars Nouveau 原生时序的关系

Ars Nouveau 的原生顺序是**预检 → 结算效果 → 扣费**，三段分明：

```java
// SpellResolver.onCast
if (canCast(caster) && !postEvent().isCanceled()) {    // 预检：不够就 return false，法术不放
    CastResolveType resolveType = castType.onCast(...); // 结算并放出效果
    if (resolveType == CastResolveType.SUCCESS) {
        expendMana();                                   // 之后才真正扣费
    }
}
```

本模组**沿用同一顺序、同一分段**，只是把「Player mana 池」换成「可抽的 ME 网络魔源 + 玩家魔力」：
`enoughMana` 对应预检，`expendMana` 对应扣费。

Ars Nouveau 自身在预检与扣费之间**不重新校验**，因此存在一个固有的时序差；本模组继承了这个特性，
并叠加了一个 ArS 没有的边界：ArS 扣的是玩家自己的魔力池（同一 tick 内不会被第三方抽干），
而魔源来自**可被其它设备并发取用**的网络，所以「预检通过、扣费时已不够」在理论上可达。
处理方式与 Ars Nouveau 一致——取消本次扣费，玩家魔力不受损失；差别只在被保住的是网络魔源而非魔力。

网络侧抽取沿用 AE2 标准写法 `StorageHelper.poweredExtraction`（同时受网络可用能量与魔源储量
约束），动作源用 `IActionSource.ofPlayer(player)`。

## 开发

```bash
# 构建（需 JDK 21）
./gradlew build

# 开发版客户端
./gradlew runClient
```

开发环境依赖通过 CurseForge Maven 拉取（见 `build.gradle`）。`compileOnly` 保证这些 mod 的
类不会被打进本模组 jar。

## 许可

LGPL-3.0-only
