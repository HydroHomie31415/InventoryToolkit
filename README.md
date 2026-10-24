# Inventory Toolkit

Inventory Toolkit combines seventeen client-side Fabric inventory quality-of-life
mods for Minecraft 26.3:

- **Shift Drag Transfer** — quick-moves dragged stacks and transfers precise
  item amounts with the scroll wheel.
- **Container Quick Stack** — one key deposits your items into every chest,
  barrel, and shulker box within reach that already holds them, opening each
  container without showing its screen; the inventory screen has a button for
  it too and stays open while it runs. Progress is shown above the hotbar, and
  while a run is going its button turns into ✕ to cancel it. Inside an open
  container (including hoppers and dispensers), a header button or the same
  key quick stacks into just that container.
- **Chest Sorting** — one key or inventory button gives every chest and barrel
  within reach a category (wood, stone, redstone, mob drops, farming, nether,
  end, equipment, or valuables) and moves items into the chest of their category, carrying
  them through your empty inventory slots. Your own inventory items are put
  into the chest of their category too, even when it does not hold them yet
  (following Quick Stack's hotbar and unstackable settings). With Traveler's
  Backpack installed, the contents of your worn backpack (or else the first
  backpack in your inventory) are put away as well, whenever the chest of their
  category has room. Each category goes to the chest that
  already holds the most of it; spare chests take categories that have no chest
  yet or overflow. A chest renamed to a category ("Redstone", "Mob Drops",
  "Farm", ...) always gets that category. Items that fit no category, such as
  wool, glass, and dyes, stay where they are unless a custom
  `item_id=category` rule gives them one. By default the first press only
  previews the sort: every nearby chest is labelled with the category it would
  get, and pressing again within 20 seconds sorts them.
- **Compact** — a button in the crafting table screen crafts every nine
  ingots, gems, nuggets, dried kelp, and similar materials in your inventory
  into their blocks, using the server's recipe-book placement. By default only
  blocks that can be crafted back into the nine items are made.
- **Quick Trade** — Shift+click a trade in a villager's list, or pick a trade
  and press the ⟳ button, to repeat it until the villager is out of stock, you
  run out of payment, or your inventory is full.
- **Container Search** — remembers what every chest, barrel, and shulker box
  you open, quick stack into, or scan holds (including items inside stored
  shulker boxes and bundles), and searches all of it by item name or ID.
  Picking a result, or Alt+clicking an item in any inventory screen, outlines
  the containers holding it through walls. Results show when each container
  was last seen, can be picked with the arrow keys and Enter, and items found
  only in another dimension are pointed out. **Scan Nearby** refreshes every
  container within reach without showing their screens.
- **Hotbar Loadouts** — five configurable, rule-based hotbar presets.
- **Offhand Swapper** — opens a configurable secondary hotbar for quickly
  swapping inventory items into the offhand.
- **Auto Tool Switch** — selects the best eligible hotbar tool before mining,
  with configurable activation, tool and slot eligibility, harvest and
  enchantment priorities, durability protection, block/item ID or tag rules,
  and optional return to the previous slot.
- **Bundle Routing** — routes items into named bundles and provides a complete
  browser for bundle contents.
- **Stack Refill** — replaces or tops up a used stack after successful block
  placement.
- **Inventory Sweeper** — safely drops newly acquired items that match
  configurable presets or item rules.
- **Inventory Sort** — middle-click in an inventory screen to sort what is under
  the mouse, like ClientSort: a chest, barrel, shulker box, dispenser, or
  hopper slot (or a slot of another mod's storage screen, such as a backpack)
  sorts that container, the main inventory (or anywhere on the panel, when only
  your inventory is open) sorts your inventory, and the hotbar sorts on its own.
  Bundles are swapped like any other item, and middle-clicks on the recipe book
  are left to it.
  Stacks are merged and put in creative-inventory order; Shift sorts by
  quantity, Ctrl alphabetically, and Alt by registry ID (each configurable,
  along with a Chest Sorting category order). Locked slots stay where they are.
- **Slot Lock** — hover over a slot in any inventory screen and press N to lock
  it. Locked slots show a padlock (on the hotbar too) and are never touched by
  Inventory Sort, Quick Stack, Chest Sorting, Stack Refill, Hotbar Loadouts, Bundle Routing,
  Compact, Inventory Sweeper, recipe-book placement, or drag and scroll
  transfers. Shift-clicking a locked slot is ignored by default; a normal click
  still picks the item up. Locks belong to slot positions and apply in every
  world.
- **Drop Protection** — enchanted, renamed, uncommon-or-rarer, and filled
  container items, items in locked slots, and any listed item IDs are not
  dropped by the drop key or by clicking outside an inventory screen. By
  default, repeating the drop within 3 seconds confirms it; it can also block
  such drops outright. Inventory Sweeper never drops protected items.
- **Shulker Box Preview** — shows the contents of shulker boxes, and of other
  items that store container contents, as a slot grid in the tooltip, tinted
  with the box's dye color. It can be limited to while Shift is held.
- **Hydro's Recipe Book** — shows every locally known recipe in the vanilla
  recipe-book interface, ungroups recipes, keeps persistent favorites with
  gold star indicators, and supports right-click or Shift-right-click crafting.

The modules use ordinary vanilla inventory actions and work on multiplayer
servers without a server-side installation.

## Configuration

Open **Mods → Inventory Toolkit → Configure**, type `/inventorytoolkit`, or
click the link in the one-time welcome message to reach the configuration hub.
It groups the modules by where they act (in the world, in inventories, and at
the crafting table) and has an on/off switch next to each one. The toolkit
also adds an unbound **Toolkit: Open settings** mapping. Every key mapping is
in the shared Inventory Toolkit Controls category and is named
`Module: Action`.

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
- `config/inventory-toolkit-quick-stack.json`
- `config/inventory-toolkit-compact.json`
- `config/inventory-toolkit-quick-trade.json`

The `inventory-toolkit-recipe-book.json` file contains the master Recipe Book
Enhancements switch. It is also available in **Inventory Toolkit Settings →
Recipe Book Enhancements**; turning it off restores the vanilla recipe-book
interface and interactions. Auto Tool Switch settings use
`inventory-toolkit-auto-tool-switch.json`; Container Quick Stack, Chest
Sorting, and Container Search share `inventory-toolkit-quick-stack.json`, and
Compact uses `inventory-toolkit-compact.json`. Inventory Sort, Slot Lock
(including the locked slots), Drop Protection, and Shulker Box Preview use
`inventory-toolkit-inventory-sort.json`, `inventory-toolkit-slot-lock.json`,
`inventory-toolkit-drop-protection.json`, and
`inventory-toolkit-shulker-preview.json`. Remembered container
contents are stored separately for each world and server under
`inventory-toolkit/container-memory/` in the game directory.

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

- Minecraft Java Edition 26.3
- Fabric Loader 0.19.3 or newer
- Fabric API 0.161.0+26.3 or newer
- Mod Menu 21.0.0 or newer
- Cloth Config 26.3.159 or newer
- Java 25

## Build

```sh
./gradlew build
```

The distributable JAR is written to
`build/libs/inventory-toolkit-1.5.0.jar`. Every build also installs it into the
PandoraLauncher 26.3 instance and removes older Toolkit, standalone Shift Drag
Transfer, standalone OffhandSwapper, and standalone Hydro's Recipe Book JARs.
