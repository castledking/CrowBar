# GP3D + GPExpansion + CrowBar — Implementation Plan

## Context

CrowBar's claim waypoints require three data sources:
1. **Claim bounds** (where the claim is) — from GP3D
2. **Claim names** (what to display on the waypoint) — from GPExpansion
3. **Permission** (who can see claim waypoints) — server-side check

Currently:
- GP3D Fabric has **no public query API** and **no events**
- GPExpansion is **Bukkit-only** and fires **zero events**, has **zero API**
- CrowBar is Fabric-only and has no way to query GP3D claims or receive metadata

The plan separates concerns cleanly: GP3D owns claim data, GPExpansion owns claim metadata, and CrowBar consumes both through a bridge layer.

---

## Architecture

```
gp3d-core
    ClaimSnapshot (pure geometry/ownership)
    ClaimRepository (public query interface)

gp3d-fabric
    FabricClaimRepository (implements ClaimRepository)
    Claim callbacks (Fabric EventFactory pattern)

gp3d-bukkit
    Bukkit events (existing hierarchy)

gpexpansion
    ClaimMetadataService (read-only: name, description, icon)
    Metadata events (ClaimValueChangedEvent<T> hierarchy)

crowbar bridge (server module)
    ClaimWaypointSyncService
    Listens to GP3D claim events
    Listens to GPExpansion metadata events
    Maintains ClaimWaypointData cache
    Pushes ClaimWaypointPayloads

crowbar client (client module)
    Renders waypoints on locator bar
```

---

## Phase 1: GP3D Public Query API

**Goal:** Expose `ClaimRepository` interface in gp3d-core; implement in Fabric.

### 1.1. Create `ClaimRepository` Interface

**File (new):** `gp3d-core/src/main/java/com/griefprevention/claims/ClaimRepository.java`

```java
public interface ClaimRepository {
    Collection<ClaimSnapshot> getClaims();
    Collection<ClaimSnapshot> getClaims(UUID owner);
    Optional<ClaimSnapshot> getClaim(long id);
    Optional<ClaimSnapshot> findClaimAt(String worldKey, int x, int y, int z);
}
```

### 1.2. Implement in Fabric

**File:** `platforms/fabric-1.21.11/src/main/java/com/griefprevention/fabric/FabricClaimRepository.java`

**Changes:**
- Make class `public` (currently package-private)
- Implement `ClaimRepository` interface
- Wrap existing query methods

### 1.3. Register Service

**File:** `platforms/fabric-1.21.11/src/main/java/com/griefprevention/fabric/GriefPreventionFabric.java`

**Changes:**
- Store `FabricClaimRepository` instance as a field
- Add `public static ClaimRepository getClaimRepository()` accessor

### 1.4. Build Verification

```bash
cd /mnt/storage/repos/GriefPrevention3D && ./gradlew build
```

---

## Phase 2: GP3D Fabric Events

**Goal:** Create Fabric-native claim lifecycle events using `EventFactory.createArrayBacked()`.

### 2.1. Create Callback Interfaces

**File (new):** `platforms/fabric-1.21.11/src/main/java/com/griefprevention/callback/ClaimCreatedCallback.java`

```java
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

Repeat for:
- `ClaimDeletedCallback`
- `ClaimModifiedCallback`
- `ClaimTransferredCallback`

### 2.2. Fire Events from FabricClaimRepository

**File:** `FabricClaimRepository.java`

Add event firing to mutation methods:
- `createClaim()` → `ClaimCreatedCallback.EVENT.invoker().onClaimCreated(snapshot)`
- `deleteClaimAt()` → `ClaimDeletedCallback.EVENT.invoker().onClaimDeleted(snapshot)`
- `updateClaimBounds()` → `ClaimModifiedCallback.EVENT.invoker().onClaimModified(oldSnapshot, newSnapshot)`
- Transfer logic → `ClaimTransferredCallback.EVENT.invoker().onClaimTransferred(snapshot, newOwner)`

### 2.3. Build Verification

```bash
cd /mnt/storage/repos/GriefPrevention3D && ./gradlew build
```

---

## Phase 3: GPExpansion Metadata API + Events

**Goal:** GPExpansion exposes a public metadata service and fires events on changes.

### 3.1. Create `ClaimMetadataService` Interface

**File (new):** `src/main/java/codes/castled/gpexpansion/api/ClaimMetadataService.java`

```java
public interface ClaimMetadataService {
    Optional<String> getName(long claimId);
    Optional<String> getDescription(long claimId);
    Optional<Material> getIcon(long claimId);
    Collection<ClaimMetadata> getAllMetadata();
}
```

### 3.2. Create `ClaimMetadata` Record

**File (new):** `src/main/java/codes/castled/gpexpansion/api/ClaimMetadata.java`

```java
public record ClaimMetadata(
    long claimId,
    @Nullable String name,
    @Nullable String description,
    @Nullable Material icon
) {}
```

### 3.3. Create Metadata Events

**File (new):** `src/main/java/codes/castled/gpexpansion/events/ClaimMetadataEvent.java`

Base class:
```java
public abstract class ClaimMetadataEvent extends Event {
    private final long claimId;
    public long getClaimId() { return claimId; }
}
```

Concrete events:
- `ClaimRenamedEvent` — has `oldName`, `newName`
- `ClaimDescriptionChangedEvent` — has `oldDescription`, `newDescription`
- `ClaimIconChangedEvent` — has `oldIcon`, `newIcon`

### 3.4. Implement `ClaimMetadataServiceImpl`

**File (new):** `src/main/java/codes/castled/gpexpansion/api/ClaimMetadataServiceImpl.java`

```java
public class ClaimMetadataServiceImpl implements ClaimMetadataService {
    private final ClaimDataStore dataStore;

    public ClaimMetadataServiceImpl(ClaimDataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public Optional<String> getName(long claimId) {
        return dataStore.getCustomName(String.valueOf(claimId));
    }

    @Override
    public Optional<String> getDescription(long claimId) {
        return dataStore.getDescription(String.valueOf(claimId));
    }

    @Override
    public Optional<Material> getIcon(long claimId) {
        return dataStore.getIcon(String.valueOf(claimId));
    }

    @Override
    public Collection<ClaimMetadata> getAllMetadata() {
        return dataStore.getAllClaims().stream()
            .map(id -> new ClaimMetadata(
                Long.parseLong(id),
                dataStore.getCustomName(id).orElse(null),
                dataStore.getDescription(id).orElse(null),
                dataStore.getIcon(id).orElse(null)
            ))
            .toList();
    }
}
```

### 3.5. Register Service + Fire Events

**File:** `GPExpansionPlugin.java`

- Create `ClaimMetadataServiceImpl` instance
- Add `public static ClaimMetadataService getMetadataService()` accessor
- In `ClaimDataStore.setCustomName()`: fire `ClaimRenamedEvent` after storage
- In `ClaimDataStore.setDescription()`: fire `ClaimDescriptionChangedEvent` after storage
- In `ClaimDataStore.setIcon()`: fire `ClaimIconChangedEvent` after storage

### 3.6. Build Verification

```bash
cd /mnt/storage/repos/GPExpansion && ./gradlew build
```

---

## Phase 4: CrowBar Bridge (Server Module)

**Goal:** Server-side bridge that listens to GP3D + GPExpansion events, maintains a cache, and pushes `ClaimWaypointPayload` to clients.

### 4.1. Claim Waypoint Data

**File (new):** `src/main/java/codes/castled/crowbar/bridge/ClaimWaypointData.java`

```java
public record ClaimWaypointData(
    long id,
    int x,
    int y,
    int z,
    UUID ownerId,
    @Nullable String displayName
) {}
```

### 4.2. Claim Waypoint Cache

**File (new):** `src/main/java/codes/castled/crowbar/bridge/ClaimWaypointCache.java`

```java
public class ClaimWaypointCache {
    private final ClaimRepository claimRepository;
    private final ClaimMetadataService metadataService;
    private final Map<Long, ClaimWaypointData> cache = new ConcurrentHashMap<>();

    public ClaimWaypointCache(ClaimRepository claimRepository, ClaimMetadataService metadataService) {
        this.claimRepository = claimRepository;
        this.metadataService = metadataService;
        rebuild();
    }

    private void rebuild() {
        cache.clear();
        for (ClaimSnapshot claim : claimRepository.getClaims()) {
            String displayName = metadataService.getName(claim.id())
                .orElse(claim.ownerName() + "'s Claim");
            cache.put(claim.id(), new ClaimWaypointData(
                claim.id(),
                claim.bounds().centerX,
                claim.bounds().centerY,
                claim.bounds().centerZ,
                claim.ownerUUID(),
                displayName
            ));
        }
    }

    public List<ClaimWaypointData> getWaypointsNear(ServerPlayer player, int range) {
        // Return waypoints within range of player
    }

    public void onClaimCreated(ClaimSnapshot claim) { ... }
    public void onClaimDeleted(ClaimSnapshot claim) { ... }
    public void onClaimModified(ClaimSnapshot oldClaim, ClaimSnapshot newClaim) { ... }
    public void onClaimRenamed(long claimId, String newName) { ... }
}
```

### 4.3. Claim Waypoint Sender

**File (new):** `src/main/java/codes/castled/crowbar/bridge/ClaimWaypointSender.java`

```java
public class ClaimWaypointSender {
    private final ClaimWaypointCache cache;

    public void sendWaypoints(ServerPlayer player) {
        List<ClaimWaypointData> getWaypoints = cache.getWaypointsNear(player, 256);
        List<ClaimWaypointPayload> payloads = waypoints.stream()
            .map(b -> new ClaimWaypointPayload(b.id(), b.displayName(), b.x(), b.y(), b.z()))
            .toList();
        for (ClaimWaypointPayload payload : payloads) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
```

### 4.4. Wire Events

**File:** `GriefPreventionFabric.java`

```java
// Register GP3D claim events
ClaimCreatedCallback.EVENT.register(claim -> waypointCache.onClaimCreated(claim));
ClaimDeletedCallback.EVENT.register(claim -> waypointCache.onClaimDeleted(claim));
ClaimModifiedCallback.EVENT.register((old, new_) -> waypointCache.onClaimModified(old, new_));

// Register GPExpansion metadata events (if available via ServiceLoader)
// ...
```

### 4.5. Build Verification

```bash
cd /mnt/storage/repos/GriefPrevention3D && ./gradlew build
```

---

## Phase 5: CrowBar Client Receiver

**Goal:** CrowBar client receives `ClaimWaypointPayload` and stores waypoint data.

### 5.1. Claim Waypoint Payload

**File (new):** `version26/src/main/java/codes/castled/crowbar/network/ClaimWaypointPayload.java`

```java
public record ClaimWaypointPayload(
    long claimId,
    @Nullable String claimName,
    int centerX, int centerY, int centerZ
) {
    public static final CustomPayload.Id<ClaimWaypointPayload> ID =
        new CustomPayload.Id<>(ResourceLocation.fromNamespaceAndPath("crowbar", "claim_bowties"));
    public static final PacketCodec<RegistryFriendlyByteBuf, ClaimWaypointPayload> CODEC = ...;
}
```

### 5.2. Claim Waypoint State

**File (new):** `version26/src/main/java/codes/castled/crowbar/ClaimWaypointState.java`

```java
public class ClaimWaypointState {
    private static final List<ClaimWaypointData> activeWaypoints = new CopyOnWriteArrayList<>();
    private static boolean enabled = true;

    public static void update(List<ClaimWaypointData> waypoints) { ... }
    public static List<ClaimWaypointData> getActiveWaypoints() { ... }
    public static boolean isEnabled() { ... }
    public static void toggle() { enabled = !enabled; }
    public static void clear() { activeWaypoints.clear(); }
}
```

### 5.3. Client Network Handler

**File (new):** `version26/src/main/java/codes/castled/crowbar/network/CrowBarNetworkHandler.java`

```java
public class CrowBarNetworkHandler {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ClaimWaypointPayload.ID, (payload, context) -> {
            ClaimWaypointState.update(payload.toDataList());
        });
    }
}
```

### 5.4. Register Network Handler

**File:** `CrowBarClient.java`

Call `CrowBarNetworkHandler.register()` during client init.

### 5.5. Build Verification

```bash
cd /mnt/storage/repos/CrowBar && ./gradlew build
```

---

## Phase 6: CrowBar Renderer

**Goal:** Render claim waypoints on the locator bar.

### 6.1. Update CrowBarHudRenderer

**File:** `version26/src/main/java/codes/castled/crowbar/CrowBarHudRenderer.java`

**Changes:**
- After rendering player dots, render claim waypoints
- Filter by `ClaimWaypointState.isEnabled()`
- Use `BOWTIE_SPRITE` (not player dot sprite)
- Size: fixed or scaled by claim radius
- Color: hash of owner UUID or claim name for variety
- Position: `centerX/centerZ` converted to player-relative screen offset
- Claim name as label above waypoint (if non-null)

### 6.2. Tint Calculation

```java
private static Vec3 getWaypointColor(UUID ownerId) {
    int hash = ownerId.hashCode();
    float hue = (hash & 0xFFFFFF) / (float) 0xFFFFFF;
    return Vec3.fromHSB(hue, 0.6f, 0.9f);
}
```

### 6.3. Build Verification

```bash
cd /mnt/storage/repos/CrowBar && ./gradlew build
```

---

## Files to Modify/Create

### GP3D Core (gp3d-core)
| File | Action | Phase |
|------|--------|-------|
| `ClaimRepository.java` (new) | Public query interface | 1 |

### GP3D Fabric
| File | Action | Phase |
|------|--------|-------|
| `FabricClaimRepository.java` | Make public, implement `ClaimRepository` | 1 |
| `GriefPreventionFabric.java` | Register repository, fire events | 1, 2 |
| `ClaimCreatedCallback.java` (new) | Fabric event callback | 2 |
| `ClaimDeletedCallback.java` (new) | Fabric event callback | 2 |
| `ClaimModifiedCallback.java` (new) | Fabric event callback | 2 |
| `ClaimTransferredCallback.java` (new) | Fabric event callback | 2 |

### GPExpansion
| File | Action | Phase |
|------|--------|-------|
| `ClaimMetadataService.java` (new) | Public metadata API | 3 |
| `ClaimMetadata.java` (new) | Metadata record | 3 |
| `ClaimMetadataServiceImpl.java` (new) | Metadata implementation | 3 |
| `ClaimMetadataEvent.java` (new) | Base metadata event | 3 |
| `ClaimRenamedEvent.java` (new) | Rename event | 3 |
| `ClaimDescriptionChangedEvent.java` (new) | Description event | 3 |
| `ClaimIconChangedEvent.java` (new) | Icon event | 3 |
| `GPExpansionPlugin.java` | Register service, fire events | 3 |
| `ClaimDataStore.java` | Fire metadata events | 3 |

### CrowBar Bridge (server)
| File | Action | Phase |
|------|--------|-------|
| `ClaimWaypointData.java` (new) | Waypoint data record | 4 |
| `ClaimWaypointCache.java` (new) | Event-driven cache | 4 |
| `ClaimWaypointSender.java` (new) | Packet sender | 4 |
| `GriefPreventionFabric.java` (GP3D) | Wire bridge events | 4 |

### CrowBar Client
| File | Action | Phase |
|------|--------|-------|
| `ClaimWaypointPayload.java` (new) | Network payload | 5 |
| `ClaimWaypointState.java` (new) | Client state | 5 |
| `CrowBarNetworkHandler.java` (new) | Client network handler | 5 |
| `CrowBarClient.java` | Register network handler | 5 |
| `CrowBarHudRenderer.java` | Render claim waypoints | 6 |

---

## Dependency Graph

```
Phase 1 (ClaimRepository)
  ↓
Phase 2 (Fabric callbacks)
  ↓
Phase 3 (GPExpansion metadata API + events)
  ↓
Phase 4 (CrowBar bridge)     ← depends on Phase 1 + 2 + 3
  ↓
Phase 5 (CrowBar client receiver)
  ↓
Phase 6 (CrowBar renderer)
```

Phases 1-2 are GP3D-only. Phase 3 is GPExpansion-only. Phases 5-6 are CrowBar-only. Phase 4 bridges them.

---

## Verification

After each phase:
```bash
cd /mnt/storage/repos/GriefPrevention3D && ./gradlew build
cd /mnt/storage/repos/GPExpansion && ./gradlew build
cd /mnt/storage/repos/CrowBar && ./gradlew build
```

Integration test:
1. Start Fabric server with GP3D + GPExpansion
2. Create a claim with a name
3. Join with CrowBar client
4. Verify waypoints appear on locator bar with claim names
5. Toggle claim waypoints off/on with keybind
6. Verify non-ops don.t see waypoints

---

## Out of Scope

- GPExpansion Fabric port (future work)
- Dynmap/BlueMap claim overlay (future work)
- Dynamic claim boundary visualization (future work)
- Claim name editing from CrowBar client (future work)
- Custom `ClaimSnapshot` fields (keep it pure geometry/ownership)
