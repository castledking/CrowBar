package codes.castled.crowbar.mixin;

import codes.castled.crowbar.CrowBarHudRenderer;
import codes.castled.crowbar.CrowBarState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class InGameHudMixin {

    @Inject(method = "renderHealthLevel", at = @At("HEAD"), cancellable = true)
    private void crowbar$hideHealthForSelfView(GuiGraphics context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
        }
    }

    @Inject(method = "renderArmorLevel", at = @At("HEAD"), cancellable = true)
    private void crowbar$hideArmorForSelfView(GuiGraphics context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFoodLevel", at = @At("HEAD"), cancellable = true)
    private void crowbar$hideFoodForSelfView(GuiGraphics context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
        }
    }

    @Inject(method = "renderAirLevel", at = @At("HEAD"), cancellable = true)
    private void crowbar$hideAirForSelfView(GuiGraphics context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
        }
    }

    /**
     * Vanilla chooses either the XP or locator background before this point. Draw CrowBar's forced
     * locator background afterward so GPX claims keep the locator graphic alive even when no nearby
     * player caused vanilla to select it.
     */
    @Inject(method = "renderContextualInfoBarBackground", at = @At("TAIL"))
    private void crowbar$renderLocatorBackground(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        CrowBarHudRenderer.renderLocatorBarBackground(context, tickCounter);
    }

    /** Draw CrowBar markers and the self view last so the selected bar and XP level cannot bury them. */
    @Inject(method = "renderContextualInfoBar", at = @At("TAIL"))
    private void crowbar$renderContextualOverlays(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            CrowBarHudRenderer.drawSelfView(context);
        } else {
            CrowBarHudRenderer.renderLocatorBarMarkers(context, tickCounter);
        }
    }
}
