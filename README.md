# Network Source Casting

[English](README.md) | [简体中文](README.zh-CN.md)

Let Ars Nouveau spells pay their cost from **the Source stored in your storage networks** — an add-on
for Ars Nouveau that hooks into Applied Energistics 2 or Beyond Dimensions (either one is enough).

## Overview

Carry any AE2 wireless terminal bound to an **ME Wireless Access Point** — AE2's own terminal, or one
registered by AE2WTLib or another add-on, are all recognised — and Ars Nouveau spell costs are paid
from the Source in that ME network first. Whatever the network cannot cover falls back to the caster's
own mana.

Source is stored in the network by an **ME Source Storage Component / ME Source Jar**, provided by
Ars Énergistique.

With **Beyond Dimensions** installed, the Source in the player's **primary dimension network** counts
too — feed Source into the network through an **Interdimensional Source Pathway**, no ME network
required. `sources.me_first` decides which of the two is spent first (the ME network by default).

This mod is **behaviour-only**: it registers no items, blocks, GUIs or recipes.

## Requirements

| Type | Mod | Notes |
|---|---|---|
| Required | Ars Nouveau ≥ 5.13.0 | The casting side; this mod Mixins its `LivingCaster` |
| Channel 1 | Applied Energistics 2 ≥ 19.2.0 **+** Ars Énergistique ≥ 2.1.0 | The ME network route; both must be present — AE2 provides the wireless terminal and the storage network, Ars Énergistique registers the ME Source key `arseng:source` |
| Channel 2 | Beyond Dimensions ≥ 0.7.0 | The dimension network route |
| Optional | AE2 Wireless Terminals ≥ 19.2.0 | Terminals it registers are recognised too; the feature works without it |

At least **one channel** must be installed, otherwise the mod has nothing to do — it registers nothing
and stays silent while casting. With both installed, `sources.me_first` decides which is spent first.

## Installation

1. Install the required mods listed above.
2. Put `netsourcecasting-<version>.jar` into your `mods/` folder.

## Quick start

1. Bind an AE2 wireless terminal to an **ME Wireless Access Point**.
2. Make sure the network has Source storage (ME Source Storage Component / ME Source Jar).
3. Cast a spell — the network pays first, and only what it cannot cover comes out of your own mana.

Beyond Dimensions on its own (no AE2) works too: skip steps 1–2, create a dimension network, select it
as your **primary** one in the Primary Network Switcher, and feed Source in through an
**Interdimensional Source Pathway** — no ME network is involved.

## Configuration

`config/netsourcecasting-common.toml` (a common config, read by both client and server; it is not
synced over the network):

| Key | Default | Meaning |
|---|---|---|
| `payment.network_pays_full_cost` | `true` | `true`: the network pays the **whole** cost first — when it holds enough Source, casting consumes none of the caster's mana<br>`false`: the network covers only the **shortfall** — the caster's mana is spent as usual and the network pays just what the caster cannot; when the caster can pay in full, the network is untouched |
| `sources.me_first` | `true` | `true`: the ME network's Source is spent first, the dimension network only once it runs short<br>`false`: the other way round<br>No effect without Beyond Dimensions installed |

Under either setting, "network plus caster mana still insufficient → the spell is not cast" holds.

## Development commands

```bash
# Build (requires JDK 21)
./gradlew build

# Development client
./gradlew runClient
```

Development-only dependencies come from CurseForge Maven and are declared `compileOnly` +
`localRuntime`; see [docs/troubleshooting.md](docs/troubleshooting.md) for environment pitfalls
(Patchouli, JEI).

## Documentation

- [Payment model and behaviour](docs/design/payment-model.md)
- [Spell cost interception](docs/design/mixin-interception.md)
- [Beyond Dimensions integration](docs/design/beyond-dimensions.md)
- [Environment and build pitfalls](docs/troubleshooting.md)

## License

LGPL-3.0-only — see [LICENSE](LICENSE).
