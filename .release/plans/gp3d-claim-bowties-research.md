# GriefPrevention3D Claim Bowties — Research & Integration Plan

Show claim centers as bowtie markers inside CrowBar's existing locator bar. Uses the existing `bowtie.png` texture with random tints per claim owner.

---

## 1. GriefPrevention3D Architecture

### Modules
| Module | Path | Purpose |
|--------|------|---------|
| **gp3d-core** | `gp3d-core/` | Platform-neutral claim data model (shared jar-in-jar) |
| **Fabric adapter** | `platforms/fabric-1.21.11/` | Fabric server mod |
| **Bukkit adapter** | `src/` | Bukkit/Spigot/Paper plugin |

### Key Data Classes (gp3d-core)

**`ClaimSnapshot`** — immutable claim data:
- `id: Long` — unique claim ID
- `worldKey: String` — `"world"`, `"world_nether"`, `"world_the_end"`
- `ownerId: UUID` — owner (null = admin claim)
- `parentId: Long` — parent claim ID (null = top-level)
- `bounds: ClaimBounds` — spatial bounds
- `threeDimensional: boolean` — Y boundaries enforced
- `subdivision: boolean` — is subclaim

**`ClaimBounds`** — spatial bounds:
- `minX, minY, minZ, maxX, maxY, maxZ: int`
- `polygon: OrthogonalPolygon` — non-null for shaped claims
- Center calculation: `((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2)`

**`ClaimSnapshotIndex`** — chunk-aware spatial index:
- `snapshots()` → all claims
- `findAt(worldKey, x, y, z)` → point query

### Fabric Module State

**`FabricClaimRepository`** (`final class`, package-private):
- `snapshots()` → `List<ClaimSnapshot>` — all loaded claims
- `findClaimAt(ServerLevel, BlockPos)` — point query
- `trustFor(ClaimSnapshot)` — trust data
- **No public API, no static singleton, no networking**

**`GriefPreventionFabric`** — mod entry point:
- Mod ID: `griefprevention3d`
- Only has `"main"` entrypoint (no `"client"`)
- Creates `FabricClaimRepository` as local variable, passes to constructors

### Permissions
- `griefprevention.claimslistother` — view other players' claims (already exists)
- Used in `/claimslist [player]` (Bukkit) and `/gp3d claim list` (Fabric)

### Existing Textures
- `crowbar:textures/gui/sprites/hud/locator_bar_dot/bowtie.png` — exists in both v21 and v26
- Registered in `_list.json` — ready to use

---

## 2. The Integration Challenge

CrowBar is a **client mod**. GriefPrevention3D is **server-side only**. The client has no access to claim data.

**Current state:**
- `FabricClaimRepository` is package-private — no external access
- No Fabric networking (no custom payloads, no channels)
- No `"client"` entrypoint in `fabric.mod.json`
- `ClaimSnapshot` has no serialization (no JSON/codec/NBT)

**The client needs:**
1. A list of claim centers for the current dimension
2. For each claim: claim ID, center position (x, y, z), owner UUID (for tint)
3. Permission check: can this player see claims?

---

## 3. Integration Approach: Fabric Networking

Both mods are ours. Use Fabric's networking API with typed `CustomPayload` records. No JSON serialization layer.

**GP3D changes needed:**
1. Register a `PayloadTypeRegistry` S2C channel for `ClaimWaypointPayload`
2. On player join, send claim data to clients with `griefprevention.claimslistother`
3. Push updates on claim create/delete/modify events
4. Permission check: `ServerPlayerEntity.hasPermissionLevel(2)` or LuckPerms integration

**CrowBar changes needed:**
1. Register the same payload type for S2C reception
2. On join, store claim data in `CrowBarState`
3. Render bowtie markers inside the existing locator bar

**Why not reflection?**
Reflection into `FabricClaimRepository` is technical debt — fragile, only works on integrated server, no permission check. Skip it. Go straight to networking.

---

## 4. Feature Design: Claim Bowties in the Locator Bar

### Core Concept

Claim bowties are NOT a separate HUD element. They are additional sprites rendered inside the **existing** locator bar, alongside player dots.

The background rendering is conditional:

```
boolean vanillaBackgroundExists = hasWaypoints();

if (!vanillaBackgroundExists && hasRenderableEntries()) {
    renderBackground();  // CrowBar draws the background
}

// Both paths render into the same bar
renderPlayerDots();
renderClaimWaypoints();  // bowtie sprites at claim center positions
```

When `hasWaypoints() == true`:
- Vanilla already rendered the background and player dots
- CrowBar only adds bowtie sprites at the proper angular positions
- No background texture, no scaling — just another dot

When `hasWaypoints() == false`:
- CrowBar renders the locator bar background itself
- Renders player dots
- Renders claim bowties

### LocatorElement Abstraction

Both players and claims are locator elements. The renderer loops over all elements uniformly:

```java
interface LocatorElement {
    double yaw();        // relative yaw from camera
    double distance();   // distance from camera
    int color();         // tint color
    Identifier sprite(); // dot sprite (default_0..3 for players, bowtie for claims)
    UUID ownerUuid();    // for skin/name lookup (null for claims without owner)
    boolean allowVerticalArrow();
}
```

Implemented by:
- `PlayerLocatorElement` — uses existing dot sprites, distance-based scaling
- `ClaimWaypointElement` — uses `BOWTIE_SPRITE`, fixed size, random tint from owner UUID

The renderer:

```java
List<LocatorElement> elements = new ArrayList<>();
elements.addAll(playerEntries);
elements.addAll(claimBowtieEntries);

// Background (conditional)
if (!vanillaBackgroundExists && !elements.isEmpty()) {
    renderBackground();
}

// Render all elements
for (LocatorElement element : elements) {
    renderDot(element);       // sprite + tint
    renderSkin(element);      // if player + skins enabled
    renderLabel(element);     // if nameTags/distance enabled
}
```

### Display Rules

| Toggle State | What Shows |
|-------------|-----------|
| OFF | No claim bowties |
| ON | Claim bowties (server decides what to send) |

Server-side permission controls the data:
- No permission → server sends nothing → toggle does nothing
- `griefprevention.claimslistother` → server sends own claims
- `griefprevention.claimslistother` + OP/LuckPerms → server sends all claims

No client-side ALL mode. The server decides what data to deliver based on permission.

### Visual Design

- **Texture:** `BOWTIE_SPRITE` = `crowbar:hud/locator_bar_dot/bowtie`
- **Tint:** Random hue derived from claim owner UUID (golden-angle algorithm, same as player dots)
- **Position:** Center of claim bounds: `((minX + maxX) / 2, (minZ + maxZ) / 2)`
- **Size:** Fixed (no distance scaling — claims are large geographic features)
- **Label:** Claim owner name + claim ID (e.g., "PlayerName #123")
- **Arrow:** Vertical arrow based on Y delta (existing `getArrowSpriteForPosition`)

### Packet Design

**Channel:** `crowbar:claim_bowties` (S2C)

**Payload record (Fabric `CustomPayload`):**

```java
record ClaimWaypointData(long claimId, int x, int y, int z, UUID ownerId) {}

record ClaimWaypointPayload(List<ClaimWaypointData> claims) implements CustomPayload {
    public static final CustomPayload.Type<ClaimWaypointPayload> ID =
        new CustomPayload.Type<>(Identifier.fromNamespaceAndPath("crowbar", "claim_bowties"));

    public static final StreamCodec<ByteBuf, ClaimWaypointPayload> CODEC = ...;
}
```

No JSON. Typed records with `PacketCodec`. Both mods share the payload definition via `gp3d-core` (or a shared module).

**Server sends:**
- On player join: all claims in the player's current dimension
- On claim create/delete/modify: updated list for affected dimension

**Client receives:**
- Stores in `CrowBarState.claimWaypoints`
- Renderer reads from this list each frame

---

## 5. Required Changes

### GriefPrevention3D (Fabric Module)

1. **New file:** `FabricClaimNetworking.java`
   - Register `crowbar:claim_bowties` S2C channel
   - On `ServerPlayConnectionEvents.JOIN`: filter claims by dimension, build payload, send
   - On claim create/delete/modify: send updated list to all players in dimension
   - Permission check: `player.hasPermissionLevel(2)` or LuckPerms

2. **New file:** `ClaimWaypointPayload.java` (or in gp3d-core)
   - `record ClaimWaypointData(long claimId, int x, int y, int z, UUID ownerId)`
   - `record ClaimWaypointPayload(List<ClaimWaypointData> claims) implements CustomPayload`
   - `PacketCodec<ByteBuf, ClaimWaypointPayload>`

3. **Modify:** `GriefPreventionFabric.java`
   - Initialize networking in `onInitialize()`

4. **Modify:** `FabricClaimRepository`
   - Expose `snapshots()` for the networking layer (or make `FabricClaimNetworking` a friend class)

### CrowBar

1. **New file:** `ClaimWaypointData.java` (shared with GP3D via gp3d-core, or duplicated)
   - `record ClaimWaypointData(long claimId, int x, int y, int z, UUID ownerId)`

2. **Modify:** `CrowBarState.java`
   - Add `List<ClaimWaypointData> claimWaypoints`
   - Add `boolean showClaimWaypoints` (toggled by keybind)

3. **New file:** `ClaimWaypointPacketHandler.java`
   - Register payload type
   - Receive `ClaimWaypointPayload`, store in `CrowBarState.claimWaypoints`

4. **Modify:** `CrowBarHudRenderer.java` (v26 and v21)
   - Build `LocatorElement` list from both player data and claim bowties
   - Claim bowtie elements use `BOWTIE_SPRITE` instead of dot sprite
   - Background rendering remains conditional on `!hasWaypoints()`

5. **Modify:** `CrowBarClient.java`
   - Register claim bowtie payload type
   - Register receiver
   - Add `KEY_C` keybind: toggle `showClaimWaypoints`

6. **Modify:** `CrowBarRendererMixin.java`
   - Include claim bowties in `hasRenderablePlayers()` check (rename to `hasRenderableEntries()`)

---

## 6. Data Flow

```
Server (GP3D Fabric)                         Client (CrowBar)
┌──────────────────────────┐                 ┌──────────────────────────┐
│ FabricClaimRepository     │                 │ CrowBarState             │
│   .snapshots()           │                 │   .claimWaypoints          │
│                          │                 │   List<ClaimWaypointData>  │
│ FabricClaimNetworking    │──S2C payload──→│ ClaimWaypointPacketHandler │
│   on JOIN / on change    │                 │   .handle(payload)       │
└──────────────────────────┘                 └──────────────────────────┘
                                                        │
                                                        ▼
                                               CrowBarHudRenderer
                                                 renderClaimWaypoints()
                                                 (inside existing bar)
```

---

## 7. Implementation Plan

### Step 1: Shared Payload Definition
- Define `ClaimWaypointData` and `ClaimWaypointPayload` records
- Place in `gp3d-core` (both mods depend on it) or duplicate in both mods

### Step 2: GP3D Server Networking
- Register S2C channel in `GriefPreventionFabric.onInitialize()`
- Send claim data on player join (filtered by dimension)
- Send updates on claim create/delete/modify
- Permission check: only send to players with `griefprevention.claimslistother` or OP

### Step 3: CrowBar Client Reception
- Register payload type and receiver
- Store claim data in `CrowBarState`
- Add `KEY_C` keybind

### Step 4: CrowBar Renderer Integration
- Build unified `LocatorElement` list (players + claims)
- Claim elements use `BOWTIE_SPRITE`, fixed size, random tint
- Background conditional on `!hasWaypoints()`
- Labels show owner name + claim ID

### Step 5: GP3D Claim Event Hooks
- Fire payload updates when claims are created/deleted/modified
- Filter by dimension to minimize network traffic

---

## 8. Open Questions

1. **Should subdivisions show as separate bowties?** No — only top-level claims.
2. **Should admin claims (no owner) show?** Could use a distinct tint (e.g., red).
3. **How to handle shaped claims?** Use bounding box center, not polygon centroid.
4. **Claim updates — push or poll?** Push via events on GP3D side.
5. **Shared payload definition?** Place in `gp3d-core` to avoid duplication, or just duplicate the 3-line record in both mods.
6. **What if GP3D is not installed?** CrowBar never receives the payload → `claimWaypoints` stays empty → no bowties rendered. No crash, no error.
