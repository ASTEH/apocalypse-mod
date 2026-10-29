# API verification notes

Verify these against the exact NeoForge 21.1.264 MDK sources before implementing the affected feature:

- **Datapack reload:** check `AddReloadListenerEvent` registration and the listener base class. Parse safely and publish immutable snapshots on the correct thread.
- **Attachments:** use the 1.21.1 `AttachmentType` API with codec/serializer; choose copy-on-death semantics deliberately.
- **Payloads:** use `CustomPacketPayload`, `StreamCodec` and `RegisterPayloadHandlersEvent`. Serverbound handlers validate input and mutate state on the logical server/main thread.
- **Entity goals:** verify `EntityJoinLevelEvent` and navigation APIs. Never clear a selector or erase another mod's goals.
- **Weapon attribution:** vanilla direct melee kills use the server-side death source and the held item tag. Projectile/firearm XP remains disabled until a dedicated adapter verifies server-side projectile ownership and the actual firing item. Item tags alone do not prove which item caused a projectile.
- **TV menus:** verify menu data synchronization and server-side distance/interaction checks.
- **Time:** use Minecraft server level/world time, never client wall-clock time.

No mixin is planned for the core MVP.
