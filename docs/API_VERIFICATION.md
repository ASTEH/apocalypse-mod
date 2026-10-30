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
- **Scavenging:** ChestMenu opening is no longer an XP source. The server compares source-container and player-inventory item counts and awards XP only after items transfer into the player's inventory.
- **Time:** use Minecraft server level/world time, never client wall-clock time.

Core gameplay has no Create dependency. Create-specific mixins are declared with `requiredMods=["create"]` and are loaded only when Create is installed.
