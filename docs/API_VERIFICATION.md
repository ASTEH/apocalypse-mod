# API verification notes

Verify these against the exact NeoForge 21.1.264 MDK sources before implementing the affected feature:

- **Datapack reload:** check `AddReloadListenerEvent` registration and the listener base class. Parse safely and publish immutable snapshots on the correct thread.
- **Attachments:** use the 1.21.1 `AttachmentType` API with codec/serializer; choose copy-on-death semantics deliberately.
- **Payloads:** use `CustomPacketPayload`, `StreamCodec` and `RegisterPayloadHandlersEvent`. Serverbound handlers validate input and mutate state on the logical server/main thread.
- **Entity goals:** verify `EntityJoinLevelEvent` and navigation APIs. Never clear a selector or erase another mod's goals.
- **Weapon attribution:** vanilla direct melee kills use the server-side death source and held-item tag. TaCZ projectile XP uses the optional reflection adapter to verify the actual projectile owner and firing-gun ID; do not infer projectile ownership from item tags.
- **TV menus:** verify menu data synchronization and server-side distance/interaction checks.
- **Create 6.0.10 learning hooks:** the optional mixin targets `RecipeApplier.applyRecipeOn(ItemEntity, Recipe, boolean)`, `BasinRecipe.apply(BasinBlockEntity, Recipe)`, `MillstoneBlockEntity.process()`, `FanProcessing.applyProcessing(ItemEntity, FanProcessingType)`, the crushing-wheel recipe output path and the saw cutting recipe output path. Create 6.0.10 source signatures were inspected; runtime application still requires local build and in-game verification. The nearest active player within 16 blocks receives XP; a shared cooldown and daily cap prevent continuous factory farming.
- **LRTactical 1.21.1:** inspected its public `ConsumableUseEvent`, `ConsumableIndex.getData()` and medical fields. The optional adapter registers that event reflectively and awards First Aid XP only for consumables that heal or remove effects. `lrtactical:melee` is an optional entry in Aftermath's melee tag.
- **Scavenging:** a pending loot-container marker is recorded before vanilla opens/unpacks a chest, using main-hand interaction only. Only an ungenerated loot-table block followed by a ChestMenu can start tracking; XP requires an observed item transfer into the player's inventory. Ordinary player storage and off-hand duplicate interactions do not start or clear a session.
- **Daily skill balance:** action XP and lesson/TV/VHS rewards share a 120 XP per skill per in-game day ceiling. Each action keeps its own shorter anti-farm cap and cooldown; multipliers are applied before the global ceiling.
- **Create completion guards:** item-entity recipe XP is awarded only if the processed entity survives output conversion. Millstone XP is injected after the first actual recipe-result insertion, not at method tail after a possible early return.
- **Farmer's Delight 1.21.x:** inspected `CookingPotBlockEntity.processCooking(RecipeHolder, CookingPotBlockEntity)` (returns true only after a meal is produced) and `SkilletBlockEntity.cookAndOutputItems(ItemStack, Level)` (the output path consumes one input after spawning the cooked result). Optional mixins award Cooking XP after those operations.
- **Zombie Awareness 1.21.0 upstream:** inspected `ZAUtil.spawnOrBuffSenseAtPos(Level, Vector3d, EnumSenseType, int, boolean)`, enum value `SOUND`, and mod ID `zombieawareness`. The bridge calls this API reflectively and does not link Zombie Awareness or JOML types at compile time. Runtime compatibility with a 1.21.1 jar still needs testing.
- **Time:** use Minecraft server level/world time, never client wall-clock time.

Core gameplay has no Create dependency. Create-specific mixins are declared with `requiredMods=["create"]` and are loaded only when Create is installed.
