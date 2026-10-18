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

/**
 * NeoForge splits the status bars into one GUI layer per element, so each one hides for the self
 * view and moves with the HUD lift on its own.
 */
@Mixin(Gui.class)
public abstract class InGameHudMixin {
    // One flag per lifted element: each records whether its HEAD pushed a matrix, so its RETURN
    // pops only what was pushed even if the lift changed in between.
    @Unique
    private boolean crowbar$healthLifted;
    @Unique
    private boolean crowbar$armorLifted;
    @Unique
    private boolean crowbar$foodLifted;
    @Unique
    private boolean crowbar$airLifted;
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

    @Inject(method = "extractHealthLevel", at = @At("HEAD"), cancellable = true)
    private void crowbar$beforeHealth(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
            return;
        }
        crowbar$healthLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "extractHealthLevel", at = @At("RETURN"))
    private void crowbar$afterHealth(GuiGraphicsExtractor context, CallbackInfo ci) {
        crowbar$healthLifted = CrowBarHudRenderer.popHudLift(context, crowbar$healthLifted);
    }

    @Inject(method = "extractArmorLevel", at = @At("HEAD"), cancellable = true)
    private void crowbar$beforeArmor(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
            return;
        }
        crowbar$armorLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "extractArmorLevel", at = @At("RETURN"))
    private void crowbar$afterArmor(GuiGraphicsExtractor context, CallbackInfo ci) {
        crowbar$armorLifted = CrowBarHudRenderer.popHudLift(context, crowbar$armorLifted);
    }

    @Inject(method = "extractFoodLevel", at = @At("HEAD"), cancellable = true)
    private void crowbar$beforeFood(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
            return;
        }
        crowbar$foodLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "extractFoodLevel", at = @At("RETURN"))
    private void crowbar$afterFood(GuiGraphicsExtractor context, CallbackInfo ci) {
        crowbar$foodLifted = CrowBarHudRenderer.popHudLift(context, crowbar$foodLifted);
    }

    @Inject(method = "extractAirLevel", at = @At("HEAD"), cancellable = true)
    private void crowbar$beforeAir(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
            return;
        }
        crowbar$airLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "extractAirLevel", at = @At("RETURN"))
    private void crowbar$afterAir(GuiGraphicsExtractor context, CallbackInfo ci) {
        crowbar$airLifted = CrowBarHudRenderer.popHudLift(context, crowbar$airLifted);
    }

    @Inject(method = "extractVehicleHealth", at = @At("HEAD"))
    private void crowbar$beforeVehicleHealth(GuiGraphicsExtractor context, CallbackInfo ci) {
        crowbar$vehicleHealthLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "extractVehicleHealth", at = @At("RETURN"))
    private void crowbar$afterVehicleHealth(GuiGraphicsExtractor context, CallbackInfo ci) {
        crowbar$vehicleHealthLifted = CrowBarHudRenderer.popHudLift(context, crowbar$vehicleHealthLifted);
    }

    @Inject(method = "extractExperienceLevel", at = @At("HEAD"))
    private void crowbar$beforeExperienceLevel(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        crowbar$experienceLevelLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "extractExperienceLevel", at = @At("RETURN"))
    private void crowbar$afterExperienceLevel(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        crowbar$experienceLevelLifted = CrowBarHudRenderer.popHudLift(context, crowbar$experienceLevelLifted);
    }

    // The single-argument overload only delegates here, so this one covers every caller.
    @Inject(method = "extractSelectedItemName(Lnet/minecraft/client/gui/GuiGraphicsExtractor;I)V", at = @At("HEAD"))
    private void crowbar$beforeSelectedItemName(GuiGraphicsExtractor context, int yShift, CallbackInfo ci) {
        crowbar$selectedItemLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "extractSelectedItemName(Lnet/minecraft/client/gui/GuiGraphicsExtractor;I)V", at = @At("RETURN"))
    private void crowbar$afterSelectedItemName(GuiGraphicsExtractor context, int yShift, CallbackInfo ci) {
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

    /**
     * Vanilla chooses either the XP or locator background before this point. Draw CrowBar's forced
     * locator background afterward so GPX claims keep the locator graphic alive even when no nearby
     * player caused vanilla to select it.
     */
    @Inject(method = "extractContextualInfoBarBackground", at = @At("TAIL"))
    private void crowbar$renderLocatorBackground(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        CrowBarHudRenderer.renderLocatorBarBackground(context, tickCounter);
    }

    /**
     * Draw CrowBar markers and the self view last so the selected bar and XP level cannot bury them,
     * then every label queued by them and by the overlay on vanilla's own dots.
     */
    @Inject(method = "extractContextualInfoBar", at = @At("TAIL"))
    private void crowbar$renderContextualOverlays(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            CrowBarHudRenderer.drawSelfView(context);
        } else {
            CrowBarHudRenderer.renderLocatorBarMarkers(context, tickCounter);
            CrowBarHudRenderer.drawQueuedLabels(context);
        }
    }
}
