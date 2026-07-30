# Inventory Toolkit

Inventory Toolkit combines three client-side Fabric inventory quality-of-life
mods for Minecraft 26.2:

- **Hotbar Loadouts** — five configurable, rule-based hotbar presets.
- **Stack Refill** — replaces or tops up a used stack after successful block
  placement.
- **Inventory Sweeper** — safely drops newly acquired items that match
  configurable presets or item rules.

The modules use ordinary vanilla inventory actions and work on multiplayer
servers without a server-side installation.

## Configuration

Open **Mods → Inventory Toolkit → Configure** to reach the configuration hub.
The toolkit also adds an unbound **Open Inventory Toolkit Settings** mapping
under its shared Controls category. Existing module key mappings remain
available.

Existing settings are imported automatically because the combined mod continues
to use the original configuration files:

- `config/smart-hotbar-loadouts.json`
- `config/stack-refill.json`
- `config/inventory-sweeper.json`

The three standalone JARs must be removed before installing Inventory Toolkit.
Fabric will report them as incompatible if they are accidentally installed
together, preventing duplicate initializers and inventory actions.

## Safety and coordination

The modules share an inventory-action coordinator. During a client tick, manual
loadout actions take priority over stack refills, and refills take priority over
automatic sweeping. Pending automatic work waits for the next available tick.

Inventory Sweeper retains its safe defaults: it starts disabled and only drops
matching amounts gained after its baseline was established.

## Requirements

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API 0.155.2+26.2 or newer
- Mod Menu 20.0.1 or newer
- Cloth Config 26.2.155 or newer
- Java 25

## Build

```sh
./gradlew build
```

The distributable JAR is written to
`build/libs/inventory-toolkit-1.0.0.jar`.
