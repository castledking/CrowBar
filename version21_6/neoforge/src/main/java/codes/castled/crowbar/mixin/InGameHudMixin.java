package codes.castled.crowbar.mixin;

import codes.castled.crowbar.CrowBarHudRenderer;
import codes.castled.crowbar.CrowBarState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Inject(method = "render", at = @At("HEAD"))
    private void crowbar$beginFrame(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        CrowBarHudRenderer.beginFrame();
    }

    @Inject(method = "renderHealthLevel", at = @At("HEAD"), cancellable = true)
    private void crowbar$beforeHealth(GuiGraphics context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
            return;
        }
        crowbar$healthLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "renderHealthLevel", at = @At("RETURN"))
    private void crowbar$afterHealth(GuiGraphics context, CallbackInfo ci) {
        crowbar$healthLifted = CrowBarHudRenderer.popHudLift(context, crowbar$healthLifted);
    }

    @Inject(method = "renderArmorLevel", at = @At("HEAD"), cancellable = true)
    private void crowbar$beforeArmor(GuiGraphics context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
            return;
        }
        crowbar$armorLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "renderArmorLevel", at = @At("RETURN"))
    private void crowbar$afterArmor(GuiGraphics context, CallbackInfo ci) {
        crowbar$armorLifted = CrowBarHudRenderer.popHudLift(context, crowbar$armorLifted);
    }

    @Inject(method = "renderFoodLevel", at = @At("HEAD"), cancellable = true)
    private void crowbar$beforeFood(GuiGraphics context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
            return;
        }
        crowbar$foodLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "renderFoodLevel", at = @At("RETURN"))
    private void crowbar$afterFood(GuiGraphics context, CallbackInfo ci) {
        crowbar$foodLifted = CrowBarHudRenderer.popHudLift(context, crowbar$foodLifted);
    }

    @Inject(method = "renderAirLevel", at = @At("HEAD"), cancellable = true)
    private void crowbar$beforeAir(GuiGraphics context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
            return;
        }
        crowbar$airLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "renderAirLevel", at = @At("RETURN"))
    private void crowbar$afterAir(GuiGraphics context, CallbackInfo ci) {
        crowbar$airLifted = CrowBarHudRenderer.popHudLift(context, crowbar$airLifted);
    }

    @Inject(method = "renderVehicleHealth", at = @At("HEAD"))
    private void crowbar$beforeVehicleHealth(GuiGraphics context, CallbackInfo ci) {
        crowbar$vehicleHealthLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "renderVehicleHealth", at = @At("RETURN"))
    private void crowbar$afterVehicleHealth(GuiGraphics context, CallbackInfo ci) {
        crowbar$vehicleHealthLifted = CrowBarHudRenderer.popHudLift(context, crowbar$vehicleHealthLifted);
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"))
    private void crowbar$beforeExperienceLevel(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        crowbar$experienceLevelLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "renderExperienceLevel", at = @At("RETURN"))
    private void crowbar$afterExperienceLevel(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        crowbar$experienceLevelLifted = CrowBarHudRenderer.popHudLift(context, crowbar$experienceLevelLifted);
    }

    // The single-argument overload only delegates here, so this one covers every caller.
    @Inject(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;I)V", at = @At("HEAD"))
    private void crowbar$beforeSelectedItemName(GuiGraphics context, int yShift, CallbackInfo ci) {
        crowbar$selectedItemLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;I)V", at = @At("RETURN"))
    private void crowbar$afterSelectedItemName(GuiGraphics context, int yShift, CallbackInfo ci) {
        crowbar$selectedItemLifted = CrowBarHudRenderer.popHudLift(context, crowbar$selectedItemLifted);
    }

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"))
    private void crowbar$beforeOverlayMessage(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        crowbar$overlayMessageLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "renderOverlayMessage", at = @At("RETURN"))
    private void crowbar$afterOverlayMessage(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        crowbar$overlayMessageLifted = CrowBarHudRenderer.popHudLift(context, crowbar$overlayMessageLifted);
    }

    @Inject(method = "renderChat", at = @At("HEAD"))
    private void crowbar$beforeChat(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        crowbar$chatLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "renderChat", at = @At("RETURN"))
    private void crowbar$afterChat(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        crowbar$chatLifted = CrowBarHudRenderer.popHudLift(context, crowbar$chatLifted);
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

    /**
     * Draw CrowBar markers and the self view last so the selected bar and XP level cannot bury them,
     * then every label queued by them and by the overlay on vanilla's own dots.
     */
    @Inject(method = "renderContextualInfoBar", at = @At("TAIL"))
    private void crowbar$renderContextualOverlays(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            CrowBarHudRenderer.drawSelfView(context);
        } else {
            CrowBarHudRenderer.renderLocatorBarMarkers(context, tickCounter);
            CrowBarHudRenderer.drawQueuedLabels(context);
        }
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
