# ME Source Casting

[English](README.md) | [简体中文](README.zh-CN.md)

Let Ars Nouveau spells pay their cost from **the Source stored in your ME network** — an add-on for
Ars Nouveau × Applied Energistics 2 × Ars Énergistique.

## Overview

Carry any AE2 wireless terminal bound to an **ME Wireless Access Point** — AE2's own terminal, or one
registered by AE2WTLib or another add-on, are all recognised — and Ars Nouveau spell costs are paid
from the Source in that ME network first. Whatever the network cannot cover falls back to the caster's
own mana.

Source is stored in the network by an **ME Source Storage Component / ME Source Jar**, provided by
Ars Énergistique.

This mod is **behaviour-only**: it registers no items, blocks, GUIs or recipes.

## Requirements

| Type | Mod | Notes |
|---|---|---|
| Required | Ars Nouveau ≥ 5.13.0 | The casting side; this mod Mixins its `LivingCaster` |
| Required | Applied Energistics 2 ≥ 19.2.0 | Wireless terminal binding and the ME storage network |
| Required | Ars Énergistique ≥ 2.1.0 | Registers the ME network Source key `arseng:source`; **cannot be omitted** |
| Optional | AE2 Wireless Terminals ≥ 19.2.0 | Terminals it registers are recognised too; the feature works without it |

"Source inside AE2" is provided entirely by Ars Énergistique, which is why it is a hard dependency:
without it, Source does not exist as a resource in an ME network at all.

## Installation

1. Install the required mods listed above.
2. Put `mesourcecasting-<version>.jar` into your `mods/` folder.

## Quick start

1. Bind an AE2 wireless terminal to an **ME Wireless Access Point**.
2. Make sure the network has Source storage (ME Source Storage Component / ME Source Jar).
3. Cast a spell — the network pays first, and only what it cannot cover comes out of your own mana.

## Configuration

`config/mesourcecasting-common.toml` (a common config, read by both client and server; it is not
synced over the network):

| Key | Default | Meaning |
|---|---|---|
| `payment.network_pays_full_cost` | `true` | `true`: the network pays the **whole** cost first — when it holds enough Source, casting consumes none of the caster's mana<br>`false`: the network covers only the **shortfall** — the caster's mana is spent as usual and the network pays just what the caster cannot; when the caster can pay in full, the network is untouched |

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
- [Environment and build pitfalls](docs/troubleshooting.md)

## License

LGPL-3.0-only — see [LICENSE](LICENSE).
