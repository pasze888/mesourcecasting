# 法术扣费的接管点（Mixin 实现）

> 本文只讲实现机制。配置键与默认值见 [README.zh-CN.md](../../README.zh-CN.md#配置)，
> 付费策略与行为语义见 [payment-model.md](payment-model.md)。

## 接管目标

Mixin 目标是 Ars Nouveau 的 `api/spell/wrapped_caster/LivingCaster`：

- `enoughMana(int)` —— 在返回 `false` 时补判「玩家魔力 + 网络可补魔源 ≥ 花费」，够则改为 `true`；
  **只做模拟不实扣**（该判定会被 `SpellBow` 探测多次，实扣会重复扣源）
- `expendMana(int)` 内的 `mana.removeMana(totalCost)` —— 用 `@WrapOperation` 精确替换：先算魔力缺口并
  从网络抽取对应魔源，再让玩家只付自己付得起的那部分；**网络抽取必须发生在扣玩家魔力之前**，
  否则玩家魔力会先被扣空、网络只在见底后才出力，表现为「优先消耗玩家魔力」

## 为什么必须 Mixin 而不是用事件

不是"事件没触发"，而是**事件挂不到"判定"和"扣费"这两个动作上**。
`SpellCostCalcEvent.Pre` 只在 `SpellResolver.getResolveCost()` 里触发，`Post` 只在
`getExpendedCost()` / `LivingCaster.expendMana` 里触发——两者都是**试算花费**的时点，不是判定或扣费的时点。

而弓与弩的落点各自绕开了它们：

- `SpellBow`：调 `resolver.canCast(...)`，内部是 `SpellResolver.enoughMana → getResolveCost`，**能**覆盖
- `SpellCrossbow.java:99-100`：先 `resolver.getExpendedCost()` 试算，再直接调
  `context.getCaster().enoughMana(cost)`——后者是 `LivingCaster` 上的**普通方法**，没有任何事件可监听，
  而且事件顺序与其它路径相反（`Post` 先于判定触发）

因此纯事件方案要么覆盖不到弓/弩的判定（导致这两条路径无法用网络魔源），要么为了覆盖而零化花费，
反而破坏「网络优先、网络不够才用玩家魔力」和「都不足则不施法」两条语义。

## 为什么用 `@WrapOperation` 而不是 `@Inject` + `@Local`

`@Local` 只能在变量已被赋值的位置捕获。
`expendMana` 里的 `mana` 是方法体第一行才赋值的局部变量，注入点在 `HEAD` 时它尚不存在——实测会因
「0 candidate variables but exactly 1 is required」直接让 Mixin 挂载失败；而注入点在 `RETURN` 时，
读到的 `getCurrentMana()` 已经是**扣费之后**的值（`removeMana` 已执行），差额会被算成全额、让网络替
玩家多付一份。`@WrapOperation` 正好在扣费调用点拿到未扣除的魔力，语义准确且无需取消整段方法。

结论：唯一稳定的接管点就是 `LivingCaster` 的 `enoughMana` / `expendMana` 这一对方法。

## 与 Ars Nouveau 原生时序的关系

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
