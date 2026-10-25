package codes.castled.crowbar.mixin;

import codes.castled.crowbar.CrowBarHudRenderer;
import codes.castled.crowbar.CrowBarState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Gui.class)
public abstract class InGameHudMixin {
    // One flag per lifted element: each records whether its HEAD pushed a matrix, so its RETURN
    // pops only what was pushed even if the lift changed in between.
    @Unique
    private boolean crowbar$playerHealthLifted;
    @Unique
    private boolean crowbar$vehicleHealthLifted;
    @Unique
    private boolean crowbar$experienceLevelLifted;
    @Unique
    private boolean crowbar$selectedItemLifted;
    @Unique
    private boolean crowbar$overlayMessageLifted;
    @Unique
    private boolean crowbar$chatLifted;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void crowbar$beginFrame(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        CrowBarHudRenderer.beginFrame();
    }

    @Inject(method = "extractPlayerHealth", at = @At("HEAD"))
    private void crowbar$beforePlayerHealth(GuiGraphicsExtractor context, CallbackInfo ci) {
        crowbar$playerHealthLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "extractPlayerHealth", at = @At("RETURN"))
    private void crowbar$afterPlayerHealth(GuiGraphicsExtractor context, CallbackInfo ci) {
        crowbar$playerHealthLifted = CrowBarHudRenderer.popHudLift(context, crowbar$playerHealthLifted);
    }

    @Inject(method = "extractVehicleHealth", at = @At("HEAD"))
    private void crowbar$beforeVehicleHealth(GuiGraphicsExtractor context, CallbackInfo ci) {
        crowbar$vehicleHealthLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "extractVehicleHealth", at = @At("RETURN"))
    private void crowbar$afterVehicleHealth(GuiGraphicsExtractor context, CallbackInfo ci) {
        crowbar$vehicleHealthLifted = CrowBarHudRenderer.popHudLift(context, crowbar$vehicleHealthLifted);
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"))
    private void crowbar$beforeExperienceLevel(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        crowbar$experienceLevelLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V", shift = At.Shift.AFTER))
    private void crowbar$afterExperienceLevel(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        crowbar$experienceLevelLifted = CrowBarHudRenderer.popHudLift(context, crowbar$experienceLevelLifted);
    }

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"))
    private void crowbar$beforeSelectedItemName(GuiGraphicsExtractor context, CallbackInfo ci) {
        crowbar$selectedItemLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "extractSelectedItemName", at = @At("RETURN"))
    private void crowbar$afterSelectedItemName(GuiGraphicsExtractor context, CallbackInfo ci) {
        crowbar$selectedItemLifted = CrowBarHudRenderer.popHudLift(context, crowbar$selectedItemLifted);
    }

    @Inject(method = "extractOverlayMessage", at = @At("HEAD"))
    private void crowbar$beforeOverlayMessage(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        crowbar$overlayMessageLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "extractOverlayMessage", at = @At("RETURN"))
    private void crowbar$afterOverlayMessage(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        crowbar$overlayMessageLifted = CrowBarHudRenderer.popHudLift(context, crowbar$overlayMessageLifted);
    }

    @Inject(method = "extractChat", at = @At("HEAD"))
    private void crowbar$beforeChat(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        crowbar$chatLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "extractChat", at = @At("RETURN"))
    private void crowbar$afterChat(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        crowbar$chatLifted = CrowBarHudRenderer.popHudLift(context, crowbar$chatLifted);
    }

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

    /**
     * Shows the experience bar (or the mount's jump bar) instead of a blank locator bar; see
     * {@link CrowBarHudRenderer#shouldSkipLocatorBar}. Vanilla's bar type enum is not public, so
     * it is matched and replaced by constant name.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "nextContextualInfoState", at = @At("RETURN"), cancellable = true)
    private void crowbar$skipBlankLocatorBar(CallbackInfoReturnable cir) {
        Enum<?> bar = (Enum<?>) cir.getReturnValue();
        if (bar != null && "LOCATOR".equals(bar.name()) && CrowBarHudRenderer.shouldSkipLocatorBar()) {
            cir.setReturnValue(Enum.valueOf((Class) bar.getDeclaringClass(), CrowBarHudRenderer.locatorBarFallback()));
        }
    }
}
