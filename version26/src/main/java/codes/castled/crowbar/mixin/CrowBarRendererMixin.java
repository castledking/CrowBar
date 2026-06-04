package codes.castled.crowbar.mixin;

import codes.castled.crowbar.CrowBarState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.LocatorBarRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LocatorBarRenderer.class)
public abstract class CrowBarRendererMixin {
    private static boolean shouldCancelLocatorBar() {
        if (CrowBarState.isExternalRenderSuppressed()) {
            return !CrowBarState.shouldKeepVanillaLocatorBarDuringExternalSuppression();
        }
        if (CrowBarState.viewSelfEnabled) return true;
        Entity cameraEntity = Minecraft.getInstance().getCameraEntity();
        if (cameraEntity == null) return false;
        return CrowBarState.hasRenderablePlayers(cameraEntity.getUUID());
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void crowbar$hideLocatorBarBackground(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (shouldCancelLocatorBar()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void crowbar$hideLocatorBarRenderState(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (shouldCancelLocatorBar()) {
            ci.cancel();
        }
    }
}
