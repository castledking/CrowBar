# GP3D Public API & Event System — Research Results

## Summary

GP3D Bukkit has a mature event system (15+ events) and minimal API interfaces. GP3D Fabric has **zero events** and **zero public API**. GPExpansion has **zero events** and **zero API**. The desired architecture requires additions to both GP3D (Fabric events + query API) and GPExpansion (metadata events + metadata API).

---

## 1. What Already Exists

### GP3D Bukkit Events (15+ events, mature)

All events are in `me.ryanhamshire.GriefPrevention.events` and follow standard Bukkit patterns:

| Event | Cancellable | Fired At | Data Exposed |
|-------|------------|----------|--------------|
| `ClaimCreatedEvent` | Yes | `DataStore.createClaim()` | Claim, creator (CommandSender) |
| `ClaimDeletedEvent` | No | `DataStore.deleteClaimUnchecked()` | Claim (post-deletion) |
| `PreDeleteClaimEvent` | Yes | `DataStore.preDeleteCancelled()` | Claim (pre-deletion) |
| `ClaimResizeEvent` | Yes | `DataStore` resize path | from/to Claim, modifier |
| `ClaimExtendEvent` | Yes | `DataStore.extendClaim()` | from/to Claim, mutable newDepth |
| `ClaimTransferEvent` | Yes | `DataStore` transfer | Claim, mutable newOwner UUID |
| `ClaimExpirationEvent` | Yes | `CleanupUnusedClaimTask` | Claim |
| `ClaimsInactivityExpireEvent` | No | `CleanupUnusedClaimTask` | playerUUID, expired Claims |
| `TrustChangedEvent` | Yes | Trust commands | changer, claims, permission, given/taken |
| `ClaimPermissionCheckEvent` | Special | `Claim.checkPermission()` | player, claim, permission, denial reason |
| `PreventPvPEvent` | Yes | Entity damage handlers | attacker, defender, claim |
| `ProtectDeathDropsEvent` | Yes | EntityEventHandler | nullable Claim |
| `PlayerKickBanEvent` | Yes | PlayerKickBanTask | reason, source, ban flag |
| `AccrueClaimBlocksEvent` | Yes | DeliverClaimBlocksTask | blocksToAccrue (mutable), isIdle |
| `StartClaimCreationEvent` | Yes | ClaimToolDispatcher | clickedBlock |
| `StartSubdivideClaimCreationEvent` | Yes | ClaimToolDispatcher | clickedBlock, parent Claim |
| `StartClaimResizeEvent` | Yes | ClaimToolDispatcher | claim, clickedBlock |
| `ClaimInspectionEvent` | Yes | ClaimToolDispatcher | claims, block, isNearby |
| `SaveTrappedPlayerEvent` | Yes | GriefPrevention commands | nullable destination (mutable) |
| `BoundaryVisualizationEvent` | No | BoundaryVisualization | boundaries (mutable), provider (mutable) |

**Event hierarchy:**
```
org.bukkit.event.Event
  ├── ClaimEvent (abstract, wraps Claim)
  │     ├── ClaimCreatedEvent (Cancellable)
  │     ├── ClaimDeletedEvent
  │     ├── PreDeleteClaimEvent (Cancellable)
  │     ├── ClaimExpirationEvent (Cancellable)
  │     ├── ClaimTransferEvent (Cancellable, mutable newOwner)
  │     ├── ClaimPermissionCheckEvent (Cancellable via denial reason)
  │     ├── PreventPvPEvent (Cancellable)
  │     └── SaveTrappedPlayerEvent (Cancellable, mutable destination)
  ├── ClaimChangeEvent (Cancellable, from/to Claim)
  │     ├── ClaimResizeEvent
  │     └── ClaimExtendEvent (mutable newDepth)
  ├── MultiClaimEvent (Collection<Claim>)
  │     └── TrustChangedEvent (Cancellable)
  └── PlayerEvent subclasses
        ├── StartClaimCreationEvent (Cancellable)
        ├── StartSubdivideClaimCreationEvent
        ├── StartClaimResizeEvent (Cancellable)
        ├── ClaimInspectionEvent (Cancellable)
        ├── AccrueClaimBlocksEvent (Cancellable, mutable)
        ├── PlayerKickBanEvent (Cancellable)
        └── BoundaryVisualizationEvent (mutable)
```

### GP3D API Interfaces (com.griefprevention.api)

| Interface | Purpose | Methods |
|-----------|---------|---------|
| `ClaimCommandAddon` | Extend `/claim` tab-complete and subcommands | `getTabCompletions()`, `getSubcommandCompletions()`, `handleSubcommand()` |
| `ClaimCommandAddonRegistry` | Static registry for addons | `register()`, `unregister()`, `getAdditionalTabCompletions()` |
| `ClaimToolHandler` | Custom golden shovel handlers | `getPriority()`, `handle(ClaimToolContext)` |
| `ClaimToolHandlerRegistry` | Instance-based handler registry | `register()`, `unregister()`, `dispatch()` |
| `ClaimToolContext` | Rich context for tool dispatch | `getPlugin()`, `getDataStore()`, `getPlayer()`, `getEvent()`, etc. |
| `ClaimCommandContext` | Immutable command context | `getSender()`, `getRootCommand()`, `getSubcommand()`, `getArgs()` |

### GP3D Service Patterns

**Bukkit side:**
- Static singleton: `GriefPrevention.instance` (line 122)
- Public fields: `instance.dataStore`, `instance.playerEventHandler`
- Registry accessors: `instance.getClaimToolHandlerRegistry()`, `instance.getVisualizationStyleRegistry()`
- No `Bukkit.getServicesManager()` usage
- External plugins use `@EventHandler` for GP events

**Fabric side:**
- Constructor injection in `GriefPreventionFabric.onInitialize()`
- `FabricClaimRepository` is package-private (no external access)
- Uses Fabric API callbacks (`UseBlockCallback.EVENT.register()`, etc.)
- No custom events fired
- No `ServiceLoader` pattern

### GP3D Documentation Files

- `Public Api.txt` (37 lines) — original GP API doc, lists supported operations
- `Public Api 3d.txt` (199 lines) — GP3D extensions: Y-dimension API, boundary visualization API, `VisualizationStyle` interface, `BoundaryVisualizationEvent`

---

## 2. What GPExpansion Has

### Events: ZERO

GPExpansion fires **no custom events**. The only `HandlerList` usage is unregistering temporary listeners (SignInputGUI, IconSelectionGUI). No `callEvent()` calls anywhere.

### API: ZERO

No `api/` package, no service interfaces, no plugin-facing API. The only data access is:
- `GPExpansionPlugin.getClaimDataStore()` — returns internal `ClaimDataStore`
- `ClaimDataStore.getCustomName(claimId)` — returns `Optional<String>`
- PlaceholderAPI expansion: only exposes claim flight data, not claim names

### Name Change Flow

```
/claim name <name>
  → ClaimCommand.handleName()           (line 1150)
  → ClaimCustomizationUtil.normalizeName()
  → ClaimDataStore.setCustomName(claimId, name)  (line 1202)
  → ClaimDataStore.save()                         (line 1203)
  → sender.sendMessage("Claim renamed")
  → DONE. No event. No broadcast. No notification.
```

**Storage:** `ClaimDataStore.ClaimData.customName` field (line 40), persisted to `plugins/GPExpansion/claimdata.yml` under `claims.<id>.name`

**No Fabric code** — GPExpansion is 100% Bukkit/Paper.

---

## 3. What's Missing

### For GP3D Fabric

| Missing | Why Needed |
|---------|-----------|
| **Public query API** | External mods need `getClaims()`, `getClaims(owner)`, `findClaimAt()` |
| **Claim lifecycle events** | Mods need to react to create/delete/modify without polling |
| **Fabric event pattern** | Fabric has no `callEvent()` — needs `EventFactory.createArrayBacked()` callbacks |

### For GPExpansion

| Missing | Why Needed |
|---------|-----------|
| **`ClaimRenamedEvent`** | External projects need to react to name changes |
| **Metadata query API** | Projects need `getName(claimId)`, `getDescription(claimId)`, `getIcon(claimId)` |
| **Fabric port** | GPExpansion doesn't exist on Fabric |
| **Event firing** | Currently fires nothing — silent storage |

### For the Desired Architecture

The target stack is:
```
GP3D Fabric API          ← query claims, subscribe to events
  ↑
GPExpansion Metadata API  ← query metadata, subscribe to metadata events
  ↑
CrowBar bridge           ← receives claim data via networking
  ↑
CrowBar client           ← renders waypoints
```

**Currently missing layers:**
1. GP3D Fabric has no query API (only package-private `FabricClaimRepository`)
2. GP3D Fabric has no events (only Fabric API protection callbacks)
3. GPExpansion has no metadata API (only internal `ClaimDataStore`)
4. GPExpansion has no events (fires nothing)

---

## 4. Recommended Architecture

### 4.1. GP3D: Public Query API

**File (new):** `gp3d-core/src/main/java/com/griefprevention/claims/ClaimRepository.java`

```java
public interface ClaimRepository {
    Collection<ClaimSnapshot> getClaims();
    Collection<ClaimSnapshot> getClaims(UUID owner);
    Optional<ClaimSnapshot> getClaim(long id);
    Optional<ClaimSnapshot> findClaimAt(String worldKey, int x, int y, int z);
}
```

**Implementation:** `FabricClaimRepository` (make public, implement interface)

**Why:** Keeps `ClaimSnapshot` pure (geometry/ownership only). External mods query through a clean interface.

### 4.2. GP3D Fabric: Event Callbacks

Use Fabric's native `EventFactory.createArrayBacked()` pattern:

```java
// Callback interface
@FunctionalInterface
public interface ClaimCreatedCallback {
    Event<ClaimCreatedCallback> EVENT = EventFactory.createArrayBacked(
        ClaimCreatedCallback.class,
        listeners -> (claim) -> {
            for (ClaimCreatedCallback listener : listeners) {
                listener.onClaimCreated(claim);
            }
        }
    );
    void onClaimCreated(ClaimSnapshot claim);
}
```

Repeat for `ClaimDeletedCallback`, `ClaimModifiedCallback`, `ClaimTransferredCallback`.

**Why:** Mirrors GP3D's Bukkit event hierarchy. Feels natural to Fabric developers. No custom event bus.

### 4.3. GPExpansion: Metadata API + Events

**API:**
```java
public interface ClaimMetadataService {
    Optional<String> getName(long claimId);
    Optional<String> getDescription(long claimId);
    Optional<Material> getIcon(long claimId);
    Collection<ClaimMetadata> getAllMetadata();
}

public record ClaimMetadata(
    long claimId,
    @Nullable String name,
    @Nullable String description,
    @Nullable Material icon
) {}
```

**Events:**
- `ClaimRenamedEvent` — has `oldName`, `newName`
- `ClaimDescriptionChangedEvent` — has `oldDescription`, `newDescription`
- `ClaimIconChangedEvent` — has `oldIcon`, `newIcon`

**Why:** Metadata (names, descriptions, icons) belongs in GPExpansion, not in `ClaimSnapshot`. Keeps the core model clean.

---

## 5. Files That Would Need Modification

### GP3D Core (gp3d-core)
| File | Change |
|------|--------|
| `ClaimRepository.java` (new) | Public query interface |

### GP3D Fabric
| File | Change |
|------|--------|
| `FabricClaimRepository.java` | Make public, implement `ClaimRepository`, fire events |
| `GriefPreventionFabric.java` | Register repository, expose event callbacks |
| `ClaimCreatedCallback.java` (new) | Fabric event callback |
| `ClaimDeletedCallback.java` (new) | Fabric event callback |
| `ClaimModifiedCallback.java` (new) | Fabric event callback |
| `ClaimTransferredCallback.java` (new) | Fabric event callback |

### GPExpansion
| File | Change |
|------|--------|
| `ClaimMetadataService.java` (new) | Public metadata query interface |
| `ClaimMetadata.java` (new) | Metadata record |
| `ClaimMetadataServiceImpl.java` (new) | Metadata implementation |
| `ClaimMetadataEvent.java` (new) | Base metadata event class |
| `ClaimRenamedEvent.java` (new) | Rename event |
| `ClaimDescriptionChangedEvent.java` (new) | Description event |
| `ClaimIconChangedEvent.java` (new) | Icon event |
| `GPExpansionPlugin.java` | Register service, fire events |
| `ClaimDataStore.java` | Fire metadata events |

---

## 6. Claim Names: Inside GP3D or Inside GPExpansion?

### Recommendation: Inside GPExpansion (not in `ClaimSnapshot`)

**Why:**
- `ClaimSnapshot` represents core claim geometry/ownership — names are metadata
- Adding `name`, `description`, `icon`, `welcomeMessage`, `taxData` turns `ClaimSnapshot` into GPExpansion
- Keeps the core model clean and focused
- GPExpansion owns the metadata layer, GP3D owns the claim layer

**GPExpansion's role:**
- Provides the user-facing `/claim rename` command
- Stores claim names in its own `ClaimDataStore`
- Exposes `ClaimMetadataService` for external access
- Fires `ClaimRenamedEvent` for downstream consumers

**CrowBar's role:**
- Listens to GP3D claim events (geometry changes)
- Listens to GPExpansion metadata events (name changes)
- Merges data in bridge layer
- Pushes `ClaimWaypointPayload` to clients

---

## 7. Smallest Clean Architecture

### Phase 1: GP3D Public Query API

1. Create `ClaimRepository` interface in gp3d-core
2. Make `FabricClaimRepository` public and implement interface
3. Register service in `GriefPreventionFabric`

### Phase 2: GP3D Fabric Events

1. Create callback interfaces: `ClaimCreatedCallback`, `ClaimDeletedCallback`, `ClaimModifiedCallback`, `ClaimTransferredCallback`
2. Use `EventFactory.createArrayBacked()` pattern
3. Fire events from `FabricClaimRepository` mutations

### Phase 3: GPExpansion Metadata API

1. Create `ClaimMetadataService` interface
2. Create `ClaimMetadata` record
3. Create metadata events: `ClaimRenamedEvent`, `ClaimDescriptionChangedEvent`, `ClaimIconChangedEvent`
4. Implement `ClaimMetadataServiceImpl`
5. Register service and fire events from `ClaimDataStore` mutations

### The Stack

```
GP3D Fabric: ClaimRepository + Claim callbacks
  ↑
GPExpansion: ClaimMetadataService + Metadata events
  ↑
CrowBar Bridge: Listens to both, maintains cache, pushes payloads
  ↑
CrowBar Client: Renders waypoints
```

Each layer only depends on the one below it. No reflection. No YAML parsing. No implementation details leaked.
