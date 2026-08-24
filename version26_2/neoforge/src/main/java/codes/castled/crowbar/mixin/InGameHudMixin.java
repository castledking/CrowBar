package codes.castled.crowbar.mixin;

import codes.castled.crowbar.CrowBarHudRenderer;
import codes.castled.crowbar.CrowBarState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class InGameHudMixin {

    @Inject(method = "extractHealthLevel", at = @At("HEAD"), cancellable = true)
    private void crowbar$hideHealthForSelfView(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
        }
    }

    @Inject(method = "extractArmorLevel", at = @At("HEAD"), cancellable = true)
    private void crowbar$hideArmorForSelfView(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
        }
    }

    @Inject(method = "extractFoodLevel", at = @At("HEAD"), cancellable = true)
    private void crowbar$hideFoodForSelfView(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
        }
    }

    @Inject(method = "extractAirLevel", at = @At("HEAD"), cancellable = true)
    private void crowbar$hideAirForSelfView(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
        }
    }

    /**
     * Vanilla chooses either the XP or locator background before this point. Draw CrowBar's forced
     * locator background afterward so GPX claims keep the locator graphic alive even when no nearby
     * player caused vanilla to select it.
     */
    @Inject(method = "extractContextualInfoBarBackground", at = @At("TAIL"))
    private void crowbar$renderLocatorBackground(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        CrowBarHudRenderer.renderLocatorBarBackground(context, tickCounter);
    }

    /** Draw CrowBar markers and the self view last so the selected bar and XP level cannot bury them. */
    @Inject(method = "extractContextualInfoBar", at = @At("TAIL"))
    private void crowbar$renderContextualOverlays(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            CrowBarHudRenderer.drawSelfView(context);
        } else {
            CrowBarHudRenderer.renderLocatorBarMarkers(context, tickCounter);
        }
    }
}
