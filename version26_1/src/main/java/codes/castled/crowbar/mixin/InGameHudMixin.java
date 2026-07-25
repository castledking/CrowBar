package codes.castled.crowbar.mixin;

import codes.castled.crowbar.CrowBarHudRenderer;
import codes.castled.crowbar.CrowBarState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class InGameHudMixin {
    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void crowbar$hideHotbarInSelfView(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void crowbar$renderOverlays(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            CrowBarHudRenderer.drawSelfView(context);
        }
    }

    /**
     * Draws CrowBar's bar background under the experience level.
     *
     * <p>{@code GuiRenderState} layers overlapping elements in extraction order, and vanilla's
     * order here is bar background, experience level, bar dots. Extracting the background at the
     * end of the HUD painted it over the level number, so it belongs in this slot.
     */
    @Inject(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.AFTER))
    private void crowbar$renderLocatorBackground(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        CrowBarHudRenderer.renderLocatorBarBackground(context, tickCounter);
    }

    /**
     * Draws the locator bar markers after vanilla's own dots.
     *
     * <p>When CrowBar does not take the bar over — no Allium data on a plain server — vanilla still
     * extracts its dots here. Running before that let those dots paint over CrowBar's skin
     * overlays, so the markers have to come after. The background is handled separately above,
     * because it has to stay under the experience level.
     */
    @Inject(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.AFTER))
    private void crowbar$renderLocatorMarkers(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        CrowBarHudRenderer.renderLocatorBarPlayers(context, tickCounter);
    }
}
