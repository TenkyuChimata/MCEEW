# MCEEW

A real-time Earthquake Early Warning (EEW) plugin for Minecraft servers and
Velocity networks.

## Features

* Receives JMA, CENC, Sichuan, Fujian, CWA, and Chongqing earthquake warnings
* Receives final earthquake-list reports from JMA and CENC
* Delivers configurable chat, title, and sound notifications
* Supports permission-based delivery and deterministic test alerts
* Provides cached earthquake information and safe configuration reload commands
* Supports standalone Spigot/Paper/Folia servers and Velocity 3.4.x–4.x proxies

## Choose the correct artifact

| Environment | Install |
|---|---|
| Standalone Spigot 1.13.2+ | `MCEEW-x.y.z.jar` |
| Standalone Paper 1.13.2+ | `MCEEW-x.y.z.jar` |
| Standalone Folia 1.19.4+ | `MCEEW-x.y.z.jar` |
| Velocity network | `MCEEW-Velocity-x.y.z.jar` in the proxy's `plugins/` directory only |
| Backend behind MCEEW-Velocity | Do not install the Bukkit MCEEW artifact |

`mceew-core` is an internal library and is not a user-installed plugin.

### Important Velocity deployment warning

When `MCEEW-Velocity` is installed on a Velocity proxy, **do not install the
Bukkit MCEEW plugin on its backend servers**. MCEEW-Velocity already provides
the network's earthquake connection, cache, notifications, targeting,
permission checks, commands, information lookup, and reload support.

Running both artifacts in the same network is unsupported and not recommended.
They are independent plugins: MCEEW does not detect, coordinate, disable, or
deduplicate them. Installing both may create duplicate Wolfx connections and
duplicate chat, title, and sound notifications, with separate cache and
lifecycle state. Avoiding this deployment is the administrator's
responsibility.

Installing only the Bukkit plugin on an individual backend remains valid
standalone Bukkit behavior, but it does not provide Velocity-wide targeting.

## Installation

### Standalone Bukkit-family server

1. Download or build `MCEEW-x.y.z.jar`.
2. Place it in the Spigot, Paper, or Folia server's `plugins/` directory.
3. Start the server and edit the generated Bukkit configuration if needed.

This artifact retains the established Bukkit connection, cache, notifications,
commands, bStats, and updater behavior. No Velocity settings are required.

### Velocity network

1. Download or build `MCEEW-Velocity-x.y.z.jar`.
2. Place it in `Velocity/plugins/`.
3. Start Velocity once so `plugins/mceew/config.yml` is created and loaded.
4. Configure notifications and network targeting as needed, then use
   `/eew reload`.
5. Remove the Bukkit MCEEW plugin from backend servers if it is present.

No backend MCEEW plugin is required for targeting, permission checks, chat,
titles, supported sound, commands, cache/info, or reload.

## Requirements and tested platforms

| Artifact | Plugin bytecode | Tested platform/runtime |
|---|---:|---|
| `MCEEW-x.y.z.jar` | Java 11 (major 55) | Spigot/Paper 1.13.2+; Folia 1.19.4+ |
| `MCEEW-Velocity-x.y.z.jar` | Java 17 (major 61) | Velocity 3.4.x on Java 17+, 3.5.x on Java 21+, and 4.x on Java 25+ |

The pinned compatibility smoke matrix is Velocity 3.4.0 build 566 on Java 17,
3.5.1 build 615 on Java 21, and 4.0.0 build 6 on Java 25. Velocity 5 and later
are not currently guaranteed.

Newer Minecraft server releases can require a newer Java runtime than the
Bukkit plugin bytecode itself. Use the Java version required by the server or
proxy when it is higher.

Velocity-delivered sound requires a Minecraft Java client 1.19.3 or newer and
a current backend connection. When sound cannot be delivered, chat and title
notifications continue; only sound is skipped. There is no packet fallback.

## Commands

`/mceew` is an alias of `/eew`.

| Command | Velocity permission | Behavior |
|---|---|---|
| `/eew` | None | Shows the plugin version and available command paths |
| `/eew info jma` | None | Shows the latest locally cached JMA earthquake-list entry |
| `/eew info cenc` | None | Shows the latest locally cached CENC earthquake-list entry |
| `/eew test forecast` | `mceew.admin` | Sends the deterministic JMA forecast test |
| `/eew test alert` | `mceew.admin` | Sends the deterministic JMA alert test |
| `/eew test sc` | `mceew.admin` | Sends the Sichuan test |
| `/eew test fj` | `mceew.admin` | Sends the Fujian/Taiwan test |
| `/eew test cwa` | `mceew.admin` | Sends the Taiwan CWA test |
| `/eew test cenc` | `mceew.admin` | Sends the China CENC test |
| `/eew test cq` | `mceew.admin` | Sends the Chongqing test |
| `/eew reload` | `mceew.admin` | Validates and atomically applies the Velocity configuration |

The info commands read the local cache; they do not perform a fresh network
query. Data may be unavailable until the first earthquake-list update arrives.

The test command uses the normal Velocity notification, targeting, channel, and
permission path. Its fixed test warning is then sent to the proxy console and
all connected players, matching the established command behavior. It does not
contact Wolfx or modify the earthquake cache.

On Velocity, `/eew reload` validates the complete new configuration before it
is committed. An invalid file leaves the working configuration and runtime
active. An enabled-to-enabled reload retains the existing Wolfx connection and
cache; source, notification, and target changes apply to future data.

## Notification permissions

Velocity player delivery requires both `mceew.notify.all` and the applicable
source permission:

* `mceew.notify.jma.alert`
* `mceew.notify.jma.forecast`
* `mceew.notify.jma.eqlist`
* `mceew.notify.cenc.eqlist`
* `mceew.notify.sc`
* `mceew.notify.fj`
* `mceew.notify.cwa`
* `mceew.notify.cenc.eew`
* `mceew.notify.cq`

All notification permissions default to allowed, so ordinary players receive
notifications without explicit permission grants. Explicitly denying either
`mceew.notify.all` or the applicable source permission opts that player out.

`mceew.admin` controls Velocity test and reload commands; it remains
default-deny and must be granted explicitly.

Proxy-console notifications are proxy-global. Player target membership, player
permissions, and backend-specific channel overrides do not govern console
delivery.

## Velocity configuration

The Velocity file is `plugins/mceew/config.yml`. Its current top-level schema is:

* `platform_config_version`: Velocity schema version; currently `1`
* `global.enabled`: enables or disables the one proxy-global Wolfx runtime
* `global.sources`: realtime processing switches `enable_jp`, `enable_sc`,
  `enable_fj`, `enable_cwa`, `enable_cenceew`, and `enable_cq`
* `notifications.time_format`: date-time format shared by rendered reports
* `notifications.defaults`: global `broadcast`, `title`, and `alert` delivery
  switches
* `notifications.sources`: source messages, titles, sounds, and optional
  source channel overrides
* `targets`: default and source-specific player-recipient rules
* `groups`: named sets of Velocity backend server names
* `servers`: backend-specific `broadcast`/`title`/`alert` overrides

`global.sources` controls realtime warning processing. JMA and CENC
earthquake-list cache updates remain independent of those realtime switches.
The independent report-delivery switches are
`notifications.sources.jma_eqlist.broadcast` and
`notifications.sources.cenc_eqlist.broadcast`. Disabling either switch stops
that report's broadcast while its local cache and `/eew info` output continue
to update.

With `global.enabled: false`, the operational Wolfx runtime is disabled, but
the plugin shell, `/eew`, `/mceew`, and `/eew reload` remain available. This is
an operational switch, not a proxy/backend coexistence mode.

### Targets

Target modes have these exact meanings:

* `all`: every player currently connected through this Velocity proxy
* `selected`: players currently connected to the explicitly listed backend
  servers or servers expanded from the listed groups
* `none`: no player recipients

A source-specific entry under `targets.sources` completely replaces
`targets.default`; server and group lists are not deep-merged.

Example:

```yaml
targets:
  default:
    mode: selected
    servers: [lobby]
    groups: [games]
  sources:
    jma_alert:
      mode: all

groups:
  games: [survival, creative]
```

Valid source keys are `jma_alert`, `jma_forecast`, `sichuan`, `fujian`, `cwa`,
`cenc_eew`, `chongqing`, `jma_eqlist`, and `cenc_eqlist`. The same keys are
used under `notifications.sources`, `targets.sources`, and server source
overrides.

### Delivery-channel precedence

For the `broadcast` (chat), `title`, and `alert` (sound-enable) switches, the
most specific configured value wins:

```text
server + source > server > source > global
```

Example backend overrides:

```yaml
servers:
  lobby:
    notifications:
      alert: false
    sources:
      jma_alert:
        title: false
```

The nested `sound` object under a notification source contains the Adventure
sound `key`, `volume`, and `pitch`; it is distinct from the boolean `alert`
delivery switch.

`groups` and `servers` describe delivery. They do not create per-backend
connections, caches, parsers, or runtimes: the proxy owns exactly one of each.

## Configuration lineage

The artifacts use intentionally separate configuration schemas:

* Bukkit: `config-version: 9`
* Velocity: `platform_config_version: 1`

Do not copy the Bukkit configuration over the Velocity file. Bukkit's schema
and deployment behavior remain standalone and contain no proxy mode.

## Building

Run:

```shell
mvn -B clean package
```

The user-installable outputs are:

```text
mceew-bukkit/target/MCEEW-x.y.z.jar
mceew-velocity/target/MCEEW-Velocity-x.y.z.jar
```

The root Maven `${revision}` property is the single current-version authority
for module versions, plugin metadata, artifact filenames, and command version
output.

## Downloads

* [SpigotMC](https://www.spigotmc.org/resources/mceew-earthquake-early-warning.104549/)
* [GitHub Releases](https://github.com/TenkyuChimata/MCEEW/releases/latest)

## Screenshots

![1.png](https://s2.loli.net/2024/02/29/IwmO7C4foXhk2ZP.png)
![2.png](https://s2.loli.net/2024/02/29/G9EjJDSUtwyVgMQ.png)
![3.png](https://s2.loli.net/2024/02/29/kUsoMQPlBz98DcW.png)
![4.png](https://s2.loli.net/2024/02/29/ncFAuWD4wEsqIah.png)
![5.png](https://s2.loli.net/2024/04/03/QltcV4RZfe8kwIm.png)
![6.png](https://s2.loli.net/2025/09/13/GNYrfU8JQTP7IdE.png)
![7.png](https://files.seeusercontent.com/2026/08/09/8gkT/2026-08-09_233526.png)
![8.png](https://s2.loli.net/2024/02/29/OSGKuyq9zE8ChTY.png)
![9.png](https://s2.loli.net/2024/02/29/tuXgnVqkrxQoYGJ.png)

## bStats

The standalone Bukkit artifact uses bStats project 17261:

[![bStats](https://bstats.org/signatures/bukkit/MCEEW.svg)](https://bstats.org/plugin/bukkit/MCEEW/17261)

The Velocity artifact uses its separately registered bStats project 33363:

[![bStats](https://bstats.org/signatures/velocity/MCEEW.svg)](https://bstats.org/plugin/velocity/MCEEW/33363)
