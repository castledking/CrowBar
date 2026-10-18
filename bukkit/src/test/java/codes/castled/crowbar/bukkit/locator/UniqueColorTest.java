package codes.castled.crowbar.bukkit.locator;

import codes.castled.crowbar.DotColors;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UniqueColorTest {
    @Test
    void matchesTheClientsGeneratedColour() {
        for (int i = 0; i < 50; i++) {
            UUID uuid = UUID.randomUUID();
            assertEquals(DotColors.generated(uuid) & 0xFFFFFF, UniqueColor.of(uuid));
        }
    }
}
