# Inventory Toolkit

Inventory Toolkit combines eight client-side Fabric inventory quality-of-life
mods for Minecraft 26.2:

- **Shift Drag Transfer** — quick-moves dragged stacks and transfers precise
  item amounts with the scroll wheel.
- **Hotbar Loadouts** — five configurable, rule-based hotbar presets.
- **OffhandSwapper** — opens a configurable secondary hotbar for quickly
  swapping inventory items into the offhand.
- **Auto Tool Switch** — selects the best eligible hotbar tool before mining,
  with configurable activation, tool and slot eligibility, harvest and
  enchantment priorities, durability protection, block/item ID or tag rules,
  and optional return to the previous slot.
- **Hydro'sBundles** — routes items into named bundles and provides a complete
  browser for bundle contents.
- **Stack Refill** — replaces or tops up a used stack after successful block
  placement.
- **Inventory Sweeper** — safely drops newly acquired items that match
  configurable presets or item rules.
- **Hydro's Recipe Book** — shows every locally known recipe in the vanilla
  recipe-book interface, ungroups recipes, keeps persistent favorites with
  gold star indicators, and supports right-click or Shift-right-click crafting.

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
- `config/quickoffhand.json`
- `config/hydros-bundles.json`
- `config/stack-refill.json`
- `config/inventory-sweeper.json`
- `config/shift-drag-transfer.json`
- `config/hydros-recipe-book.json`
- `config/inventory-toolkit-recipe-book.json`
- `config/inventory-toolkit-auto-tool-switch.json`

The `inventory-toolkit-recipe-book.json` file contains the master Recipe Book
Enhancements switch. It is also available in **Inventory Toolkit Settings →
Recipe Book Enhancements**; turning it off restores the vanilla recipe-book
interface and interactions. Auto Tool Switch settings use the final file in
the list.

The standalone JARs must be removed before installing Inventory Toolkit.
Fabric will report them as incompatible if they are accidentally installed
together, preventing duplicate initializers and inventory actions.

## Safety and coordination

The modules share an inventory-action coordinator. During a client tick, manual
transfer, loadout, and offhand swap actions take priority over bundle routing,
followed by stack refills and automatic sweeping. Pending work waits for the
next available tick.

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
`build/libs/inventory-toolkit-1.5.0.jar`. Every build also installs it into the
PandoraLauncher 26.2 instance and removes older Toolkit, standalone Shift Drag
Transfer, standalone OffhandSwapper, and standalone Hydro's Recipe Book JARs.
