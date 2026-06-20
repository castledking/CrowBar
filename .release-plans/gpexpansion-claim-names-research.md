# GPExpansion Claim Names — Investigation Results

## Summary

GPExpansion stores claim names in its own `ClaimDataStore` (YAML file `plugins/GPExpansion/claimdata.yml`), keyed by GP3D claim ID. The name is **not** in `ClaimSnapshot`, not in GP3D's data model, and not accessible from Fabric. GPExpansion is **Bukkit/Paper only** — no Fabric mod exists.

For CrowBar to display claim names on Fabric, the name must be added to GP3D's `ClaimSnapshot` and relayed through the `ClaimWaypointPayload`.

---

## 1. Where `/claim rename <name>` Stores the Name

**File:** `GPExpansion/src/main/java/codes/castled/gpexpansion/command/ClaimCommand.java`
**Method:** `handleName()` at line 1150

```java
// line 1201-1203
codes.castled.gpexpansion.storage.ClaimDataStore store = plugin.getClaimDataStore();
store.setCustomName(ctx.claimId, stored);
store.save();
```

**Storage:** `ClaimDataStore` (inner class `ClaimData`, line 33-65)

```java
public static class ClaimData {
    public String customName = null;  // line 40
    // ...
}
```

**Getter (line 953-956):**
```java
public Optional<String> getCustomName(String claimId) {
    ClaimData data = claimData.get(claimId);
    return data != null ? Optional.ofNullable(data.customName) : Optional.empty();
}
```

**Setter (line 958-960):**
```java
public void setCustomName(String claimId, String name) {
    get(claimId).customName = truncateCustomName(name);
}
```

---

## 2. Where the Name Is Stored

The name is stored in **GPExpansion's own YAML file**, NOT in GP3D's data model.

**Physical location:** `plugins/GPExpansion/claimdata.yml`

**YAML structure (line 363-365):**
```yaml
claims:
  "123":
    name: "My Claim Name"
    description: "..."
    icon: "DIAMOND_BLOCK"
```

**Keyed by:** GP3D claim ID (numeric string like `"123"`)

**Not stored in:**
- `ClaimSnapshot` (gp3d-core) — no name field
- `Claim` (Bukkit GP3D) — no name field
- `FabricClaimFileStore` (Fabric GP3D) — no name field
- PersistentDataContainer — not used

---

## 3. Is There an Existing API?

**No public API for external mods.** The `ClaimDataStore` is:
- Accessed only through `GPExpansionPlugin.getClaimDataStore()`
- Requires the GPExpansion plugin instance
- Bukkit-only (`JavaPlugin` subclass)
- No Fabric equivalent exists

**Internal consumers:** 33+ call sites across GUIs and commands use `getCustomName(claimId)`.

---

## 4. Can GriefPrevention3D Fabric Retrieve the Name?

**Not directly.** The problems:

1. **GPExpansion is Bukkit/Paper only** — no Fabric mod, no `fabric.mod.json`
2. **`ClaimDataStore` is package-private** — not accessible from outside GPExpansion
3. **Names are in GPExpansion's YAML** — GP3D Fabric has no awareness of `claimdata.yml`
4. **No shared data model** — GP3D's `ClaimSnapshot` has no name field

**On Fabric servers:** GPExpansion doesn't exist. Claim names would need to be stored in GP3D's own data model.

---

## 5. The Smallest, Cleanest Change

### Architecture: Metadata in GPExpansion

**Claim names belong in GPExpansion, not in `ClaimSnapshot`.**

`ClaimSnapshot` represents core claim geometry/ownership. Adding `name`, `description`, `icon`, `welcomeMessage`, `taxData` turns it into GPExpansion. Instead:

- GP3D owns claim data (geometry, ownership, trust)
- GPExpansion owns claim metadata (names, descriptions, icons)
- CrowBar bridge merges both layers

### GPExpansion Metadata API

**File (new):** `src/main/java/codes/castled/gpexpansion/api/ClaimMetadataService.java`

```java
public interface ClaimMetadataService {
    Optional<String> getName(Claim claim);
    Optional<String> getDescription(Claim claim);
    Optional<Material> getIcon(Claim claim);
    Collection<ClaimMetadata> getAllMetadata();
}
```

**File (new):** `src/main/java/codes/castled/gpexpansion/api/ClaimMetadata.java`

```java
public record ClaimMetadata(
    Claim claim,
    @Nullable String name,
    @Nullable String description,
    @Nullable Material icon
) {}
```

### GPExpansion Events

Base class: `ClaimValueChangedEvent<T>` with fields `Claim claim`, `T oldValue`, `T newValue`, `@Nullable CommandSender actor`

- `ClaimRenamedEvent extends ClaimValueChangedEvent<String>` — `getOldName()`, `getNewName()`
- `ClaimDescriptionChangedEvent extends ClaimValueChangedEvent<String>` — `getOldDescription()`, `getNewDescription()`
- `ClaimIconChangedEvent extends ClaimValueChangedEvent<Material>` — `getOldIcon()`, `getNewIcon()`
- `ClaimGlobalListedEvent extends ClaimValueChangedEvent<Boolean>` — `getOldListed()`, `getNewListed()`
- `ClaimSpawnChangedEvent extends ClaimValueChangedEvent<Location>` — `getOldSpawn()`, `getNewSpawn()`
- `ClaimBanChangedEvent` (standalone) — `Claim claim`, `UUID player`, `boolean banned`

### GPExpansion Integration

On **Bukkit servers**, GPExpansion stores names in its own `ClaimDataStore` and exposes them via `ClaimMetadataService`:

```java
// In ClaimDataStore.setCustomName():
String oldName = getCustomName(claimId).orElse(null);
get(claimId).customName = truncateCustomName(name);
save();
Claim claim = resolveClaim(claimId);
CommandSender actor = ClaimDataStore.getCurrentActor();
Bukkit.getPluginManager().callEvent(new ClaimRenamedEvent(claim, oldName, name, actor));
```

On **Fabric servers**, GPExpansion doesn't exist. Names would be set via:
- A future Fabric port of GPExpansion
- Or a simple metadata service that reads from a YAML file

---

## 6. ClaimWaypointPayload Design

### Data Record

```java
record ClaimWaypointData(
    long id,
    int x,
    int y,
    int z,
    UUID ownerId,
    @Nullable String displayName
) {}
```

**Where `displayName` comes from:**
```java
displayName = metadataService.getName(claim)
    .orElse(claim.getOwnerName() + "'s Claim");
```

### Server-Side Logic (CrowBar Bridge)

```java
// In ClaimWaypointCache, when building waypoint data:
Claim claim = claimRepository.getClaim(claimId).orElse(null);
ClaimMetadata metadata = metadataService.getAllMetadata().stream()
    .filter(m -> m.claim().getID() == claimId)
    .findFirst()
    .orElse(null);

String displayName = (metadata != null && metadata.name() != null)
    ? metadata.name()
    : claim.getOwnerName() + "'s Claim";

ClaimWaypointData data = new ClaimWaypointData(
    claim.getID(),
    claim.getLesserBoundaryCorner().getBlockX() + claim.getArea() / 2,
    claim.getLesserBoundaryCorner().getBlockY(),
    claim.getLesserBoundaryCorner().getBlockZ() + claim.getArea() / 2,
    claim.getOwnerUUID(),
    displayName
);
```

### Client-Side Logic (CrowBar)

```java
// In CrowBarHudRenderer, when rendering claim waypoint label:
String label;
if (displayName != null) {
    label = displayName;
} else if (ownerId != null) {
    String ownerName = getNameFromPlayerList(ownerId);
    label = ownerName != null ? ownerName + "'s Claim" : "Claim #" + claimId;
} else {
    label = "Admin Claim";
}
```

---

## 7. Open Questions

1. **Should GP3D Fabric add its own `/claim rename` command?** Not needed — names belong in GPExpansion. A future Fabric port of GPExpansion would provide this.
2. **Should the name be limited in length?** GPExpansion truncates to configurable max (default unknown). The bridge layer should enforce a reasonable limit (e.g., 32 chars).
3. **Should subdivisions support names?** Probably not — only top-level claims.
4. **Migration for existing GPExpansion users?** No migration needed — GPExpansion keeps its own YAML storage and exposes names via `ClaimMetadataService`.
5. **Should the `name` field be in `ClaimSnapshot` or in a separate sidecar?** In GPExpansion's `ClaimDataStore` — keeps `ClaimSnapshot` pure (geometry/ownership only).
