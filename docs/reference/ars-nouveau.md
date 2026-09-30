# Ars Nouveau 事实核对（本模组依赖的部分）

> 记本模组读过的 Ars Nouveau 内部事实与核对方式。**版本敏感**：分支与数值随上游改版会变，
> 动手前按下面的命令重新核对，不要照抄结论。
>
> 核对基线：发布版 **5.13.1**，即 `build.gradle` pin 的
> `curse.maven:ars-nouveau-401955:8721482`；并与参考仓库 `Ars-Nouveau/`
> （`mod_version=5.13.1`，HEAD `d16c939`）逐条对照——本文件涉及的方法两者一致。
> 下文用全限定类名指路，源码在参考仓库对应路径下。

## 怎么核对

Ars Nouveau 是 mod，类名不被 NeoForge remap，所以直接反汇编运行时 jar 即可，不必依赖参考源码：

```powershell
$jar = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\curse.maven\ars-nouveau-401955\8721482" `
        -Recurse -Filter 'ars-nouveau-*.jar').FullName
javap -p -c -cp $jar 'com.hollingsworth.arsnouveau.common.items.data.TomeCasterData$1'
javap -p -c -cp $jar com.hollingsworth.arsnouveau.common.event.ArsEvents
javap -p -c -cp $jar com.hollingsworth.arsnouveau.common.items.CasterTome
javap -p -c -cp $jar com.hollingsworth.arsnouveau.api.spell.SpellResolver
```

## 施法者卷册（Caster Tome）

### 判定只看玩家魔力，不问施法者

`TomeCasterData.getSpellResolver(...)` 返回一个匿名 `SpellResolver`，只覆写了 `enoughMana`：

```java
int totalCost = getResolveCost();
IManaCap manaCap = CapabilityRegistry.getMana(entity);
if (manaCap == null) return false;                       // 静默 false，不发消息
boolean canCast = totalCost <= manaCap.getCurrentMana()
        || manaCap.getCurrentMana() == manaCap.getMaxMana()
        || (entity instanceof Player player && player.isCreative());
```

发布版字节码与这三条逐一对应：`dcmpg` + `ifle` 是第一条，`dcmpl` + `ifeq` 是第二条，
`instanceof Player` + `isCreative` 是第三条。

关键点：它**从不调用** `spellContext.getCaster().enoughMana(...)`。因此任何挂在
`LivingCaster.enoughMana` 上的接管都覆盖不到卷册。

### 花费折半，并截断到魔力上限

- `CasterTome.getManaDiscount(...)` 就是 `spell.getCost() / 2`（字节码：`getCost` → `iconst_2`
  → `idiv`），经 `ManaUtil.getPlayerDiscounts` 计入 `getResolveCost()` / `getExpendedCost()`
- `ArsEvents.costCalc(SpellCostCalcEvent)` 订阅的是**基类**事件，所以 `Pre` 与 `Post` 都会收到：

```java
if (casterTool.is(ItemsRegistry.CASTER_TOME) && currentCost > ManaUtil.getMaxMana(player))
    currentCost = ManaUtil.getMaxMana(player);
```

### 魔力条上限就是那个截断值

`ManaCapEvents` 在 `PlayerTickEvent.Post` 里每 tick 收敛一次：

```java
ManaUtil.Mana maxmana = ManaUtil.calcMaxMana(player);
int max = maxmana.getRealMax();                          // 与 ManaUtil.getMaxMana(player) 同值
if (mana.getMaxMana() != max || forceSync) mana.setMaxMana(max);
```

### 合起来的实际语义

满条时 `currentMana == maxMana == realMax >= totalCost`，第一条已经为真，所以第二条
（`currentMana == maxMana`）是**冗余分支**，只在「上限刚变化、还没写进条」的那一两个 tick 里
才可能独立生效。于是 5.13.1 里卷册的实际门槛是：

> **当前魔力 ≥ 折后花费（截断到魔力上限），或创造模式。**

两条附带结论：

- tooltip 的「魔力消耗超过整个魔力条时改为消耗所有魔力」已经**触发不了**——截断保证花费不会
  超过魔力条，属过时文案
- 时间线：截断 `7d0ce4cc2`（2023-07-12）早于那条 `== maxMana` 分支 `4ac4bb0f9`（2024-07-03），
  所以后者不是「截断之前的遗留」，而是截断之后加的冗余防御

### 会拦人的状态

条不满、且折后花费 > 当前魔力。例：玩家 30/100 魔力、法术原价 100 → 卷册价 50 → 报
`ars_nouveau.spell.no_mana`（「魔力不足。」）、法术不放。同一状态下的普通法杖（价 100）判定会
经过 `LivingCaster.enoughMana`，可以被别的支付来源补上。

## 判定与扣费的接管点

`SpellResolver.canCast` 只在法术校验通过之后才调用 `enoughMana`，发布版字节码是：

```
26: aload_0
27: aload_1
28: invokevirtual  // Method enoughMana:(Lnet/minecraft/world/entity/LivingEntity;)Z
31: ireturn
```

它是一处可用的接管点，但调用的是**虚方法**——卷册的匿名覆写才是实际执行体；而且
`SpellResolver.enoughMana` 在返回 false 时**自己**就发 `no_mana`（受 `silent` 字段控制），
所以任何想「事后翻成 true」的接法都会变成「报了不足却照样施法」，必须在调用**之前**决定。

弓与弩各有绕路：`SpellBow` 走 `resolver.canCast(...)`；`SpellCrossbow.tryLoadProjectiles` 先
`getExpendedCost()` 试算，再直接调 `context.getCaster().enoughMana(cost)` 并自己报
`no_mana`，不经过 `SpellResolver`。

## 与本模组的关系

本模组**不接管**卷册的判定（已决策）：卷册的魔力门槛是 Ars Nouveau 自己的设计，本模组只做
「网络替玩家付费」，不做「让卷册无条件放行」。因此留有一处已知口径差：

| 状态 | 普通法杖 | 卷册 |
|---|---|---|
| 条不满、玩家魔力不够付、来源链够付 | 施法（判定由本模组补上） | **不施法**，报「魔力不足。」 |

扣费侧两者一致：只要判定过了，`LivingCaster.expendMana` 都会被本模组接管。

若将来要消除这个差，可行接法是在 `SpellResolver.canCast` 调用 `enoughMana` 处
`@WrapOperation`：命中卷册那类 resolver（`getClass().getEnclosingClass() == TomeCasterData.class`，
避免绑死匿名类名）且来源链补得上时**直接放行、不调用原方法**（既避开它内部的报错，也避开
「事后翻转」的坑），其余 resolver 原样交还原版，以免覆盖第三方自己的非魔力规则。
