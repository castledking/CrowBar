package codes.castled.crowbar.mixin;

import codes.castled.crowbar.CrowBarState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Player.class)
public abstract class PlayerXpGainMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void crowbar$detectXpGain(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        Player self = (Player) (Object) this;
        if (self == client.player) {
            CrowBarState.checkXpGain(self);
        }
    }
}
