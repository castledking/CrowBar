# GPExpansion Public API & Event Architecture

## 1. What GP3D Already Exposes

### 1.1. Static Singleton Accessor

```java
GriefPrevention.instance          // static field, set in onEnable()
GriefPrevention.instance.dataStore // public field, direct access
```

### 1.2. Registry Accessors (instance methods)

```java
GriefPrevention.instance.getClaimToolHandlerRegistry()  // instance-based
GriefPrevention.instance.getVisualizationStyleRegistry() // instance-based
```

### 1.3. Static Utility Registry

```java
ClaimCommandAddonRegistry.register(addon)   // static, no instance needed
ClaimCommandAddonRegistry.unregister(addon)
```

### 1.4. API Interfaces (com.griefprevention.api)

| Interface | Pattern | Access |
|-----------|---------|--------|
| `ClaimCommandAddon` | Interface | `ClaimCommandAddonRegistry.register()` |
| `ClaimCommandAddonRegistry` | Static utility | Direct static call |
| `ClaimToolHandler` | Interface | `gp.getClaimToolHandlerRegistry().register()` |
| `ClaimToolHandlerRegistry` | Instance registry | `gp.getClaimToolHandlerRegistry()` |
| `ClaimToolContext` | Immutable context | Passed to handlers |
| `ClaimCommandContext` | Immutable context | Passed to addons |

### 1.5. Bukkit Events (me.ryanhamshire.GriefPrevention.events)

```
Event
├── ClaimEvent (abstract; field: Claim claim)
│   ├── ClaimCreatedEvent (Cancellable; field: CommandSender creator)
│   ├── ClaimDeletedEvent (NOT cancellable)
│   ├── ClaimChangeEvent (Cancellable; fields: Claim from, Claim to)
│   │   ├── ClaimResizeEvent (field: CommandSender modifier)
│   │   │   └── ClaimModifiedEvent [DEPRECATED]
│   │   └── ClaimExtendEvent (MUTABLE: int newDepth)
│   ├── ClaimTransferEvent (Cancellable; MUTABLE: UUID newOwner)
│   ├── PreDeleteClaimEvent (Cancellable)
│   ├── ClaimExpirationEvent (Cancellable)
│   ├── ClaimPermissionCheckEvent (Cancellable with Supplier<String> denial)
│   ├── PreventPvPEvent (Cancellable; fields: Player attacker, Entity defender)
│   └── SaveTrappedPlayerEvent (Cancellable; MUTABLE: Location destination)
│
├── MultiClaimEvent (abstract; field: Collection<Claim> claims)
│   └── TrustChangedEvent (Cancellable; fields: Player changer, ClaimPermission, boolean given, String identifier)
│
├── PlayerEvent (Bukkit)
│   ├── StartClaimCreationEvent (Cancellable; field: Block clickedBlock)
│   │   └── StartSubdivideClaimCreationEvent (field: Claim parent)
│   ├── StartClaimResizeEvent (Cancellable; fields: Claim claim, Block clickedBlock)
│   ├── PlayerKickBanEvent (Cancellable; fields: reason, source, ban)
│   ├── AccrueClaimBlocksEvent (Cancellable; MUTABLE: int blocksToAccrue, boolean isIdle)
│   ├── ClaimInspectionEvent (Cancellable; fields: claims, inspectedBlock, inspectingNearbyClaims)
│   └── BoundaryVisualizationEvent (NOT cancellable; MUTABLE: provider, boundaries)
│
├── ProtectDeathDropsEvent (Cancellable; field: Claim claim)
├── PreventBlockBreakEvent (Cancellable; DEPRECATED)
└── ClaimsInactivityExpireEvent (NOT cancellable; fields: UUID playerUUID, List<Claim> expiredClaims)
```

### 1.6. Key Event Patterns

| Pattern | Example |
|---------|---------|
| Pre-change cancellable | `ClaimCreatedEvent`, `ClaimTransferEvent`, `PreDeleteClaimEvent` |
| Post-change notification | `ClaimDeletedEvent`, `ClaimsInactivityExpireEvent` |
| From/to snapshot | `ClaimChangeEvent` (from, to) |
| Mutable new value | `ClaimTransferEvent.setNewOwner()`, `ClaimExtendEvent.setNewDepth()` |
| Unique cancel semantics | `ClaimPermissionCheckEvent.setDenialReason(Supplier<String>)` |
| Abstract base class | `ClaimEvent` (single claim), `MultiClaimEvent` (multiple claims) |

---

## 2. GPExpansion Metadata Operations

### 2.1. Current Metadata Fields

| Field | Type | Getters | Setters |
|-------|------|---------|---------|
| `customName` | `String` | `getCustomName(claimId)` | `setCustomName(claimId, name)` |
| `description` | `String` | `getDescription(claimId)` | `setDescription(claimId, desc)` |
| `icon` | `Material` | `getIcon(claimId)` | `setIcon(claimId, icon)` |
| `iconHistory` | `List<Material>` | `getIconHistory(claimId)` | `pushRecentIcon()`, `cycleIcon()` |
| `publicListed` | `boolean` | `isPublicListed(claimId)` | `setPublicListed(claimId, listed)` |
| `globalApprovalPending` | `boolean` | `isGlobalApprovalPending(claimId)` | `setGlobalApprovalPending(claimId, pending)` |
| `spawn` | `Location` | `getSpawn(claimId)` | `setSpawn(claimId, loc)`, `clearSpawn(claimId)` |
| `bans` | `BanData` | `getBans(claimId)`, `getBannedPlayers()`, `isPublicBanned()` | `addBannedPlayer()`, `removeBannedPlayer()`, `setPublicBanned()` |
| `trustedPlayers` | `Set<UUID>` | `getTrustedPlayers()`, `getTrustedPlayerNames()` | `addTrustedPlayer()`, `removeTrustedPlayer()` |
| `rental` | `RentalData` | `getRental(claimId)`, `isRented()` | `setRental()`, `clearRental()` |
| `mailbox` | `MailboxData` | `getMailbox()`, `isMailbox()`, `getMailboxOwner()` | `setMailbox()`, `clearMailbox()` |
| `eviction` | `EvictionData` | `getEviction()`, `hasPendingEviction()` | `setEviction()`, `clearEviction()` |

### 2.2. Mutation Points (where metadata is written)

| Operation | Command Handler | GUI Handler |
|-----------|----------------|-------------|
| **Rename** | `ClaimCommand.handleName()` :1202 | `GlobalClaimSettingsGUI` :336, `OwnedClaimsGUI` :448, `ChildrenClaimsGUI` :338, `AdminClaimsGUI` :402, `AllPlayerClaimsGUI` :401 |
| **Description** | `ClaimCommand.handleDescription()` :1320 | `DescriptionInputManager.processInput()` :76 |
| **Icon** | `ClaimCommand.handleIcon()` :1254 | `GlobalClaimSettingsGUI` :355, `ClaimMapEditorGUI` :469 |
| **Global listing** | `ClaimCommand.handleToggleGlobal()` :3179-3188 | `GlobalClaimSettingsGUI` :377-386, `SignListener` :1645-1650 |
| **Spawn** | `ClaimCommand.handleSetSpawn()` :3004 | `SignListener` :1657 |
| **Bans** | `ClaimCommand.handleBan()` :1498-1516, `handleUnban()` :1594-1624 | — |
| **Trusted players** | `ClaimCommand.maybeTrackTrustedPlayerChange()` :812 | `ClaimTrustEditorGUI` :169, `ClaimTrustedPlayersGUI` :88,266 |

---

## 3. Gap Analysis: Existing Events vs GPExpansion Operations

| GPExpansion Operation | Existing GP3D Event? | reusable? | New Event Needed? |
|----------------------|---------------------|-----------|-------------------|
| **Rename** | None | No | Yes: `ClaimRenamedEvent` |
| **Description change** | None | No | Yes: `ClaimDescriptionChangedEvent` |
| **Icon change** | None | No | Yes: `ClaimIconChangedEvent` |
| **Global listing toggle** | None | No | Yes: `ClaimGlobalListedEvent` |
| **Spawn set** | None | No | Yes: `ClaimSpawnChangedEvent` |
| **Ban/unban** | `TrustChangedEvent`? | Partial — TrustChangedEvent covers trust changes but not ban-specific data | Yes: `ClaimBanChangedEvent` |
| **Trusted player change** | `TrustChangedEvent` | **Yes** — GP3D already fires this for trust commands | No new event needed |
| **Claim deleted** | `ClaimDeletedEvent` | **Yes** — GP3D fires this; GPExpansion cleanup in `GPBridge` | No new event needed |
| **Claim created** | `ClaimCreatedEvent` | **Yes** — GP3D fires this | No new event needed |

---

## 4. Proposed Architecture

### 4.1. Style Decision: Follow GP3D's Pattern

GP3D uses:
- Static singleton: `GriefPrevention.instance`
- Instance getter for registries: `gp.getClaimToolHandlerRegistry()`
- Static utility for simple registries: `ClaimCommandAddonRegistry.register()`
- Bukkit events with `HandlerList`
- No Bukkit ServicesManager, no ServiceLoader, no DI

**GPExpansion should follow the same pattern:**

```java
public class GPExpansionPlugin extends JavaPlugin {
    private static GPExpansionPlugin instance;
    private ClaimDataStore claimDataStore;
    private ClaimMetadataService metadataService;

    public static GPExpansionPlugin getInstance() { return instance; }
    public ClaimDataStore getClaimDataStore() { return claimDataStore; }
    public ClaimMetadataService getMetadataService() { return metadataService; }
}
```

### 4.2. Public Metadata API (Read-Only)

**File (new):** `src/main/java/codes/castled/gpexpansion/api/ClaimMetadataService.java`

```java
package codes.castled.gpexpansion.api;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

/**
 * Read-only access to claim metadata stored by GPExpansion.
 *
 * <p>Obtain an instance via {@link codes.castled.gpexpansion.GPExpansionPlugin#getMetadataService()}.
 *
 * <p>All mutation goes through GP3D commands/GUIs. This interface is intentionally
 * read-only to avoid creating dual mutation paths.
 */
public interface ClaimMetadataService {

    /**
     * Get the custom name for a claim.
     *
     * @param claim the GP3D claim
     * @return the custom name, or empty if none set
     */
    @NotNull Optional<String> getName(@NotNull Claim claim);

    /**
     * Get the description for a claim.
     *
     * @param claim the GP3D claim
     * @return the description, or empty if none set
     */
    @NotNull Optional<String> getDescription(@NotNull Claim claim);

    /**
     * Get the icon material for a claim.
     *
     * @param claim the GP3D claim
     * @return the icon material, or empty if none set
     */
    @NotNull Optional<Material> getIcon(@NotNull Claim claim);

    /**
     * Get all metadata entries.
     *
     * @return unmodifiable collection of all metadata
     */
    @NotNull Collection<ClaimMetadata> getAllMetadata();
}
```

**File (new):** `src/main/java/codes/castled/gpexpansion/api/ClaimMetadata.java`

```java
package codes.castled.gpexpansion.api;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable snapshot of a claim's metadata.
 *
 * @param claim     the GP3D claim (provides ID, owner, geometry)
 * @param name      custom name, or null if unset
 * @param description custom description, or null if unset
 * @param icon      icon material, or null if unset
 */
public record ClaimMetadata(
    @NotNull Claim claim,
    @Nullable String name,
    @Nullable String description,
    @Nullable Material icon
) {}
```

**Why `Claim` instead of `long claimId`:**
- Avoids `String.valueOf(claimId)` conversions everywhere
- Provides access to `claim.getID()`, `claim.getOwnerName()`, `claim.getBounds()`
- Matches GP3D's `ClaimEvent` pattern (always passes `Claim`, not ID)
- Callers rarely need just the ID — they almost always need the claim itself

**Implementation:** `ClaimMetadataServiceImpl` delegates to internal `ClaimDataStore`, converting claim IDs as needed.

### 4.3. Event Design: `ClaimValueChangedEvent<T>` Base

To reduce duplication across events that all follow the old/new pattern:

```java
package codes.castled.gpexpansion.events;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base for events where a single metadata value changes from old to new.
 *
 * <p>Subclasses must provide their own {@code HandlerList}.
 *
 * @param <T> the type of value that changed
 */
public abstract class ClaimValueChangedEvent<T> extends Event {

    private final @NotNull Claim claim;
    private final @Nullable T oldValue;
    private final @Nullable T newValue;
    private final @Nullable CommandSender actor;

    protected ClaimValueChangedEvent(
            @NotNull Claim claim,
            @Nullable T oldValue,
            @Nullable T newValue,
            @Nullable CommandSender actor) {
        this.claim = claim;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.actor = actor;
    }

    /**
     * The claim whose metadata changed.
     */
    public @NotNull Claim getClaim() {
        return claim;
    }

    /**
     * The previous value, or null if unset before the change.
     */
    public @Nullable T getOldValue() {
        return oldValue;
    }

    /**
     * The new value, or null if the value was cleared.
     */
    public @Nullable T getNewValue() {
        return newValue;
    }

    /**
     * Who initiated this change, or null if triggered by a system task.
     */
    public @Nullable CommandSender getActor() {
        return actor;
    }

    /**
     * Convenience check: was this change initiated by a player?
     */
    public boolean isPlayerInitiated() {
        return actor instanceof org.bukkit.entity.Player;
    }
}
```

**Why this works:**
- `ClaimRenamedEvent extends ClaimValueChangedEvent<String>` — no duplication
- `ClaimIconChangedEvent extends ClaimValueChangedEvent<Material>` — same pattern
- Each concrete class only needs `HandlerList` + constructor + typed getters
- Generic typing gives compile-time safety: `getOldValue()` returns `String` for rename, `Material` for icon
- Actor is explicit — no hidden ThreadLocal state

### 4.4. Complete Event Hierarchy

```
Event
└── ClaimValueChangedEvent<T> (abstract; fields: Claim claim, T oldValue, T newValue, CommandSender actor)
    ├── ClaimRenamedEvent (T = String)
    │   HandlerList, constructor
    │   getOldName(), getNewName() — convenience aliases
    ├── ClaimDescriptionChangedEvent (T = String)
    │   HandlerList, constructor
    │   getOldDescription(), getNewDescription()
    ├── ClaimIconChangedEvent (T = Material)
    │   HandlerList, constructor
    │   getOldIcon(), getNewIcon()
    ├── ClaimGlobalListedEvent (T = Boolean)
    │   HandlerList, constructor
    │   getOldListed(), getNewListed()
    └── ClaimSpawnChangedEvent (T = Location)
        HandlerList, constructor
        getOldSpawn(), getNewSpawn()

ClaimBanChangedEvent (standalone; different shape)
    HandlerList, fields: Claim claim, UUID player, boolean banned, CommandSender actor
```

### 4.5. Concrete Event Implementations

**`ClaimRenamedEvent`:**

```java
package codes.castled.gpexpansion.events;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called after a claim's custom name is changed.
 */
public class ClaimRenamedEvent extends ClaimValueChangedEvent<String> {
    private static final HandlerList HANDLERS = new HandlerList();

    public ClaimRenamedEvent(
            @NotNull Claim claim,
            @Nullable String oldName,
            @Nullable String newName,
            @Nullable CommandSender actor) {
        super(claim, oldName, newName, actor);
    }

    public @Nullable String getOldName() { return getOldValue(); }
    public @Nullable String getNewName() { return getNewValue(); }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
```

**`ClaimDescriptionChangedEvent`:**

```java
package codes.castled.gpexpansion.events;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called after a claim's description is changed.
 */
public class ClaimDescriptionChangedEvent extends ClaimValueChangedEvent<String> {
    private static final HandlerList HANDLERS = new HandlerList();

    public ClaimDescriptionChangedEvent(
            @NotNull Claim claim,
            @Nullable String oldDescription,
            @Nullable String newDescription,
            @Nullable CommandSender actor) {
        super(claim, oldDescription, newDescription, actor);
    }

    public @Nullable String getOldDescription() { return getOldValue(); }
    public @Nullable String getNewDescription() { return getNewValue(); }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
```

**`ClaimIconChangedEvent`:**

```java
package codes.castled.gpexpansion.events;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called after a claim's icon is changed.
 */
public class ClaimIconChangedEvent extends ClaimValueChangedEvent<Material> {
    private static final HandlerList HANDLERS = new HandlerList();

    public ClaimIconChangedEvent(
            @NotNull Claim claim,
            @Nullable Material oldIcon,
            @Nullable Material newIcon,
            @Nullable CommandSender actor) {
        super(claim, oldIcon, newIcon, actor);
    }

    public @Nullable Material getOldIcon() { return getOldValue(); }
    public @Nullable Material getNewIcon() { return getNewValue(); }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
```

**`ClaimGlobalListedEvent`:**

```java
package codes.castled.gpexpansion.events;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called after a claim's global listing status changes.
 */
public class ClaimGlobalListedEvent extends ClaimValueChangedEvent<Boolean> {
    private static final HandlerList HANDLERS = new HandlerList();

    public ClaimGlobalListedEvent(
            @NotNull Claim claim,
            boolean oldListed,
            boolean newListed,
            @Nullable CommandSender actor) {
        super(claim, oldListed, newListed, actor);
    }

    public boolean getOldListed() { return getOldValue(); }
    public boolean getNewListed() { return getNewValue(); }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
```

**`ClaimSpawnChangedEvent`:**

```java
package codes.castled.gpexpansion.events;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called after a claim's spawn point is set or cleared.
 *
 * <p>Naturally covers all transitions:
 * <ul>
 *   <li>Set: null → location</li>
 *   <li>Change: locA → locB</li>
 *   <li>Clear: location → null</li>
 * </ul>
 */
public class ClaimSpawnChangedEvent extends ClaimValueChangedEvent<Location> {
    private static final HandlerList HANDLERS = new HandlerList();

    public ClaimSpawnChangedEvent(
            @NotNull Claim claim,
            @Nullable Location oldSpawn,
            @Nullable Location newSpawn,
            @Nullable CommandSender actor) {
        super(claim, oldSpawn, newSpawn, actor);
    }

    public @Nullable Location getOldSpawn() { return getOldValue(); }
    public @Nullable Location getNewSpawn() { return getNewValue(); }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
```

**`ClaimBanChangedEvent` (standalone, not `ClaimValueChangedEvent`):**

```java
package codes.castled.gpexpansion.events;

import me.ryanhamshire.GriefPrevention.Claim;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Called after a player is banned from or unbanned from a claim.
 *
 * <p>Bans are conceptually distinct from trust changes and have their own
 * data model (public bans, per-player bans). This event is standalone
 * rather than extending {@link ClaimValueChangedEvent} because ban
 * operations don't follow a simple old/new value pattern.
 */
public class ClaimBanChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final @NotNull Claim claim;
    private final @NotNull UUID player;
    private final boolean banned;
    private final @Nullable CommandSender actor;

    public ClaimBanChangedEvent(
            @NotNull Claim claim,
            @NotNull UUID player,
            boolean banned,
            @Nullable CommandSender actor) {
        this.claim = claim;
        this.player = player;
        this.banned = banned;
        this.actor = actor;
    }

    public @NotNull Claim getClaim() { return claim; }
    public @NotNull UUID getPlayer() { return player; }
    public boolean isBanned() { return banned; }
    public @Nullable CommandSender getActor() { return actor; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
```

### 4.6. Why These Events Are Not Cancellable

GP3D's cancellable events prevent a state change before it happens. GPExpansion's events fire **after** the mutation in `ClaimDataStore`:

1. The mutation has already happened — cancelling after the fact is confusing
2. GP3D's `ClaimDeletedEvent` follows the same post-change pattern
3. If pre-change validation is needed later, add `PreClaimRenamedEvent` variants
4. Current use cases (logging, sync, external integrations) don't need cancellation

### 4.7. Actor/Cause Information

Every metadata event should know **who** initiated the change. This is critical for audit logging and debugging.

**Approach:** Explicit `@Nullable CommandSender actor` parameter on setter methods:

```java
// In ClaimDataStore — parameter overloads
public void setCustomName(Claim claim, String name) {
    setCustomName(claim, name, null); // system/internal
}

public void setCustomName(Claim claim, String name, @Nullable CommandSender actor) {
    String oldName = getCustomName(String.valueOf(claim.getID())).orElse(null);
    get(String.valueOf(claim.getID())).customName = truncateCustomName(name);
    save();
    Bukkit.getPluginManager().callEvent(
        new ClaimRenamedEvent(claim, oldName, name, actor));
}
```

**Callers pass actor explicitly:**

```java
// In ClaimCommand.handleName()
store.setCustomName(claim, stored, sender);  // player initiated

// In GUI handler
store.setCustomName(claim, name, player);    // player via GUI

// In system task (migration, cleanup)
store.setCustomName(claim, name);            // null actor
```

**Why explicit over ThreadLocal:**
- No hidden state — caller decides who the actor is
- ThreadLocal becomes fragile with async tasks, thread pools, and event propagation
- Explicit parameters are self-documenting in API signatures
- Matches GP3D's pattern: `ClaimCreatedEvent` has `@Nullable CommandSender creator` passed explicitly

---

## 5. Firing Locations

Fire from `ClaimDataStore` setter methods to guarantee all mutation paths emit events:

| Event | Fire Location | File | Line |
|-------|--------------|------|------|
| `ClaimRenamedEvent` | After `setCustomName()` | `ClaimDataStore` | ~958 |
| `ClaimDescriptionChangedEvent` | After `setDescription()` | `ClaimDataStore` | ~945 |
| `ClaimIconChangedEvent` | After `setIcon()` | `ClaimDataStore` | ~854 |
| `ClaimGlobalListedEvent` | After `setPublicListed()` | `ClaimDataStore` | ~815 |
| `ClaimSpawnChangedEvent` | After `setSpawn()`/`clearSpawn()` | `ClaimDataStore` | ~1198 |
| `ClaimBanChangedEvent` | After `addBannedPlayer()`/`removeBannedPlayer()` | `ClaimDataStore` | ~1026-1043 |

**Why `ClaimDataStore` and not command handlers:**
- `ClaimDataStore` is the single write authority
- Commands, GUIs, signs, and API calls all go through `ClaimDataStore`
- Firing from command handlers would miss GUI mutations
- Explicit `CommandSender` parameter provides actor info without hidden state

---

## 6. Why Not Add Names to ClaimSnapshot?

### Option A: Inside GP3D's ClaimSnapshot

**Pros:**
- Single source of truth
- Available on Fabric without GPExpansion
- No cross-plugin coupling

**Cons:**
- `ClaimSnapshot` becomes a dumping ground for metadata
- Today: `name`, `description`, `icon`. Tomorrow: `welcomeMessage`, `taxData`, `rentalData`, `banList`...
- Fabric claims are simple geometry/ownership — metadata is a Bukkit-side concept
- Forces GP3D to carry GPExpansion's data model
- Migration nightmare for existing GP3D installations

### Option B: Inside GPExpansion Metadata (Recommended)

**Pros:**
- Clean separation: GP3D owns claim geometry, GPExpansion owns claim metadata
- `ClaimSnapshot` stays pure and focused
- GPExpansion can evolve its data model independently
- No changes to GP3D's core
- Existing GPExpansion users need no migration

**Cons:**
- Requires GPExpansion for claim names on Bukkit
- Fabric has no claim names (until a Fabric port of GPExpansion exists)
- Cross-plugin dependency for CrowBar bridge

**Verdict:** Option B. Metadata belongs in GPExpansion. `ClaimMetadataService` makes it accessible without coupling to internals.

---

## 7. Files Requiring Modification

### New Files (GPExpansion)

| File | Purpose |
|------|---------|
| `src/main/java/codes/castled/gpexpansion/api/ClaimMetadataService.java` | Public read-only metadata query interface |
| `src/main/java/codes/castled/gpexpansion/api/ClaimMetadata.java` | Immutable metadata record |
| `src/main/java/codes/castled/gpexpansion/api/ClaimMetadataServiceImpl.java` | Implementation delegating to ClaimDataStore |
| `src/main/java/codes/castled/gpexpansion/events/ClaimValueChangedEvent.java` | Abstract base for old/new value events |
| `src/main/java/codes/castled/gpexpansion/events/ClaimRenamedEvent.java` | Rename event |
| `src/main/java/codes/castled/gpexpansion/events/ClaimDescriptionChangedEvent.java` | Description change event |
| `src/main/java/codes/castled/gpexpansion/events/ClaimIconChangedEvent.java` | Icon change event |
| `src/main/java/codes/castled/gpexpansion/events/ClaimGlobalListedEvent.java` | Global listing toggle event |
| `src/main/java/codes/castled/gpexpansion/events/ClaimSpawnChangedEvent.java` | Spawn point change event |
| `src/main/java/codes/castled/gpexpansion/events/ClaimBanChangedEvent.java` | Ban/unban event |

### Modified Files (GPExpansion)

| File | Change |
|------|--------|
| `GPExpansionPlugin.java` | Add `instance` field, `getMetadataService()` accessor |
| `ClaimDataStore.java` | Fire events from setter methods; add `@Nullable CommandSender actor` parameter overloads for actor tracking |
| `ClaimCommand.java` | Set `currentActor` before calling `ClaimDataStore` mutations |
| GUI handlers | Set `currentActor` before calling `ClaimDataStore` mutations |

### No GP3D Changes Required

GPExpansion's API and events are self-contained. GP3D does not need modification.

---

## 8. Build Order

```
1. Create api/ package (ClaimMetadataService, ClaimMetadata, ClaimMetadataServiceImpl)
2. Create events/ package (ClaimValueChangedEvent base + 6 concrete events)
3. Modify GPExpansionPlugin (add instance, getMetadataService())
4. Modify ClaimDataStore (add @Nullable CommandSender actor parameter overloads, fire events from setters)
5. Modify ClaimCommand + GUI handlers (set currentActor before mutations)
6. Build and test GPExpansion
7. (Future) CrowBar bridge consumes ClaimMetadataService + events
```

---

## 9. Dependency Graph

```
GP3D (unchanged)
  ├── ClaimSnapshot (geometry/ownership)
  ├── ClaimCreatedEvent, ClaimDeletedEvent, etc. (existing events)
  └── DataStore, Claim (existing classes)

GPExpansion (new API + events)
  ├── api/ClaimMetadataService (public read-only query interface)
  ├── api/ClaimMetadata (immutable record: Claim, name, description, icon)
  ├── events/ClaimValueChangedEvent<T> (abstract base: claim, oldValue, newValue, actor)
  ├── events/ClaimRenamedEvent (T = String)
  ├── events/ClaimDescriptionChangedEvent (T = String)
  ├── events/ClaimIconChangedEvent (T = Material)
  ├── events/ClaimGlobalListedEvent (T = Boolean)
  ├── events/ClaimSpawnChangedEvent (T = Location)
  ├── events/ClaimBanChangedEvent (standalone: claim, player, banned)
  └── storage/ClaimDataStore (modified: fires events, explicit actor parameter)

CrowBar Bridge (future)
  ├── Consumes GP3D ClaimRepository + claim events
  ├── Consumes GPExpansion ClaimMetadataService + metadata events
  ├── Maintains ClaimWaypointData cache
  └── Pushes ClaimWaypointPayload to clients
```

---

## 10. Migration Considerations

### Existing GPExpansion Users

- **No migration needed.** The API wraps existing `ClaimDataStore` methods.
- Events fire from the same setter methods that already exist.
- YAML format unchanged.

### Existing GP3D Users

- **No changes to GP3D.** GPExpansion's API is self-contained.
- GP3D events (`ClaimCreatedEvent`, `ClaimDeletedEvent`) continue to work as-is.

### CrowBar Integration (Future)

- CrowBar bridge `@EventHandler` GP3D claim events + GPExpansion metadata events.
- Bridge maintains a merged cache: `ClaimWaypointData(long id, int x, int y, int z, UUID ownerId, String displayName)`.
- `displayName` resolved via: `metadataService.getName(claim).orElse(claim.getOwnerName() + "'s Claim")`.
- Bridge pushes `ClaimWaypointPayload` to clients via Fabric networking.

---

## 11. Naming: ClaimWaypointData

The data model should describe **what it represents**, not the plugin that renders it.

| Name | Purpose | Context |
|------|---------|---------|
| `ClaimWaypointData` | Internal cache/model | `Map<Long, ClaimWaypointData>` |
| `ClaimWaypointPayload` | Serialized network packet | Fabric `CustomPayload` |
| `ClaimWaypointManager` | Manages cache + sync | Server-side |
| `ClaimWaypointSyncService` | Coordinates GP3D + GPExpansion data | Bridge layer |

**Hierarchy (if multiple marker types later):**

```
WaypointData
    ^
    |
ClaimWaypointData
HomeWaypointData
MailboxWaypointData
WarpWaypointData
```

"Waypoint" naming preferred in internal package names or protocol IDs if needed:

```
codes.castled.crowbar.waypoint.data.ClaimWaypointData
codes.castled.crowbar.waypoint.network.ClaimWaypointPayload
```

---

## 12. Summary

| Aspect | Decision | Rationale |
|--------|----------|-----------|
| **API pattern** | Instance getter on plugin (`getMetadataService()`) | Matches GP3D's `getClaimToolHandlerRegistry()` |
| **API mutability** | Read-only | Avoids dual mutation paths; `ClaimDataStore` remains write authority |
| **ID type** | `long` (via `Claim.getID()`) | Matches GP3D's fundamental type; avoids String conversions |
| **Event base** | `ClaimValueChangedEvent<T>` with `Claim` field | Reduces duplication; matches GP3D's `ClaimEvent` base |
| **Event cancellability** | Not cancellable (post-change notification) | Matches GP3D's `ClaimDeletedEvent` pattern |
| **Event data** | Old + new values exposed | Enables addons to diff changes without querying storage |
| **Actor tracking** | `@Nullable CommandSender` explicit parameter overloads | Audit logging; matches GP3D's `@Nullable CommandSender creator` pattern |
| **Firing location** | `ClaimDataStore` setters | Guarantees all mutation paths emit events |
| **Name ownership** | GPExpansion, not `ClaimSnapshot` | Clean separation; GP3D stays pure |
| **HandlerList placement** | Concrete classes only, not abstract base | Nobody registers for abstract events |
| **Bukkit ServicesManager** | Not used | GP3D doesn't use it; follow existing pattern |
| **Data model naming** | `ClaimWaypointData` / `ClaimWaypointPayload` | Describes purpose, not implementation |
