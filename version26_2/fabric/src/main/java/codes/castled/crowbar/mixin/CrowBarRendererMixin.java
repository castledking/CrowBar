package codes.castled.crowbar.mixin;

import codes.castled.crowbar.CrowBarState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.LocatorBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LocatorBar.class)
public abstract class CrowBarRendererMixin {
    private static boolean shouldCancelLocatorBar() {
        return CrowBarState.shouldCancelVanillaLocatorBar();
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
