package codes.castled.crowbar.mixin;

import codes.castled.crowbar.CrowBarState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PlayerEntity.class)
public abstract class PlayerXpGainMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void crowbar$detectXpGain(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self == client.player) {
            CrowBarState.checkXpGain(self);
        }
    }
}
