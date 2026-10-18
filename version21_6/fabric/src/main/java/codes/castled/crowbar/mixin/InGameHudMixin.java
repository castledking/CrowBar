package codes.castled.crowbar.mixin;

import codes.castled.crowbar.CrowBarHudRenderer;
import codes.castled.crowbar.CrowBarState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    // One flag per lifted element: each records whether its HEAD pushed a matrix, so its RETURN
    // pops only what was pushed even if the lift changed in between.
    @Unique
    private boolean crowbar$statusBarsLifted;
    @Unique
    private boolean crowbar$mountHealthLifted;
    @Unique
    private boolean crowbar$experienceLevelLifted;
    @Unique
    private boolean crowbar$heldItemLifted;
    @Unique
    private boolean crowbar$overlayMessageLifted;
    @Unique
    private boolean crowbar$chatLifted;

    @Inject(method = "render", at = @At("HEAD"))
    private void crowbar$beginFrame(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        CrowBarHudRenderer.beginFrame();
    }

    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void crowbar$beforeStatusBars(DrawContext context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
            return;
        }
        crowbar$statusBarsLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "renderStatusBars", at = @At("RETURN"))
    private void crowbar$afterStatusBars(DrawContext context, CallbackInfo ci) {
        crowbar$statusBarsLifted = CrowBarHudRenderer.popHudLift(context, crowbar$statusBarsLifted);
    }

    @Inject(method = "renderMountHealth", at = @At("HEAD"))
    private void crowbar$beforeMountHealth(DrawContext context, CallbackInfo ci) {
        crowbar$mountHealthLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "renderMountHealth", at = @At("RETURN"))
    private void crowbar$afterMountHealth(DrawContext context, CallbackInfo ci) {
        crowbar$mountHealthLifted = CrowBarHudRenderer.popHudLift(context, crowbar$mountHealthLifted);
    }

    @Inject(method = "renderMainHud", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/bar/Bar;drawExperienceLevel(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;I)V"))
    private void crowbar$beforeExperienceLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        crowbar$experienceLevelLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "renderMainHud", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/bar/Bar;drawExperienceLevel(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;I)V", shift = At.Shift.AFTER))
    private void crowbar$afterExperienceLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        crowbar$experienceLevelLifted = CrowBarHudRenderer.popHudLift(context, crowbar$experienceLevelLifted);
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"))
    private void crowbar$beforeHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        crowbar$heldItemLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "renderHeldItemTooltip", at = @At("RETURN"))
    private void crowbar$afterHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        crowbar$heldItemLifted = CrowBarHudRenderer.popHudLift(context, crowbar$heldItemLifted);
    }

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"))
    private void crowbar$beforeOverlayMessage(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        crowbar$overlayMessageLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "renderOverlayMessage", at = @At("RETURN"))
    private void crowbar$afterOverlayMessage(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        crowbar$overlayMessageLifted = CrowBarHudRenderer.popHudLift(context, crowbar$overlayMessageLifted);
    }

    @Inject(method = "renderChat", at = @At("HEAD"))
    private void crowbar$beforeChat(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        crowbar$chatLifted = CrowBarHudRenderer.pushHudLift(context);
    }

    @Inject(method = "renderChat", at = @At("RETURN"))
    private void crowbar$afterChat(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        crowbar$chatLifted = CrowBarHudRenderer.popHudLift(context, crowbar$chatLifted);
    }

    @Inject(method = "renderMainHud", at = @At("TAIL"))
    private void crowbar$renderSelfViewBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            CrowBarHudRenderer.drawSelfView(context);
        }
    }

    /**
     * Vanilla chooses either the XP or locator background before this point. Draw CrowBar's forced
     * locator background afterward so GPX claims keep the locator graphic alive even when no nearby
     * player caused vanilla to select it.
     */
    @Inject(method = "renderMainHud", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/bar/Bar;renderBar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", shift = At.Shift.AFTER))
    private void crowbar$renderLocatorBackground(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        CrowBarHudRenderer.renderLocatorBarBackground(context, tickCounter);
    }

    /**
     * Draw CrowBar markers last so the selected bar, XP level, and vanilla addons cannot bury them,
     * then every label queued by them and by the overlay on vanilla's own dots.
     */
    @Inject(method = "renderMainHud", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/bar/Bar;renderAddons(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", shift = At.Shift.AFTER))
    private void crowbar$renderLocatorMarkers(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        CrowBarHudRenderer.renderLocatorBarMarkers(context, tickCounter);
        CrowBarHudRenderer.drawQueuedLabels(context);
    }
}
