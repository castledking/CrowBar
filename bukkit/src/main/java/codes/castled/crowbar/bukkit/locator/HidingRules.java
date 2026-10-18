package codes.castled.crowbar.bukkit.locator;

import codes.castled.crowbar.bukkit.PluginSettings;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffectType;

/** Decides who stays off a viewer's bar. Hidden players are left out of the data entirely. */
public final class HidingRules {
    public static final String SEE_HIDDEN_PERMISSION = "crowbar.locator.see-hidden";
    public static final String HIDDEN_PERMISSION = "crowbar.locator.hidden";

    private final PluginSettings.Hiding settings;

    public HidingRules(PluginSettings.Hiding settings) {
        this.settings = settings;
    }

    public boolean hides(Player viewer, Player target) {
        if (target.hasPermission(HIDDEN_PERMISSION)) return true;
        // Citizens and most other NPC plugins tag their fake players this way.
        if (target.hasMetadata("NPC")) return true;
        // Vanish is enforced for everyone: crowbar.locator.see-hidden must not reveal staff the
        // vanish plugin itself hides from this viewer.
        if (settings.vanished() && (!viewer.canSee(target) || (isVanished(target) && !viewer.hasPermission(SEE_HIDDEN_PERMISSION)))) {
            return true;
        }
        if (viewer.hasPermission(SEE_HIDDEN_PERMISSION)) return false;

        if (settings.spectating() && target.getGameMode() == GameMode.SPECTATOR) return true;
        if (settings.sneaking() && target.isSneaking()) return true;
        if (settings.invisible() && (target.isInvisible() || target.hasPotionEffect(PotionEffectType.INVISIBILITY))) return true;
        return isDisguised(target);
    }

    /** Wearing one of the configured head items. */
    public boolean isDisguised(Player target) {
        if (settings.headItems().isEmpty()) return false;
        ItemStack helmet = target.getInventory().getHelmet();
        return helmet != null && settings.headItems().contains(helmet.getType());
    }

    /** The "vanished" metadata that SuperVanish, PremiumVanish, EssentialsX and others set. */
    private static boolean isVanished(Player target) {
        for (MetadataValue value : target.getMetadata("vanished")) {
            if (value.asBoolean()) return true;
        }
        return false;
    }
}
