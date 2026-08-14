package codes.castled.crowbar.mixin;

import codes.castled.crowbar.CrowBarHudRenderer;
import codes.castled.crowbar.CrowBarState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void crowbar$hideStatusBarsForSelfView(DrawContext context, CallbackInfo ci) {
        if (CrowBarState.viewSelfEnabled) {
            ci.cancel();
        }
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

    /** Draw CrowBar markers last so the selected bar, XP level, and vanilla addons cannot bury them. */
    @Inject(method = "renderMainHud", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/bar/Bar;renderAddons(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", shift = At.Shift.AFTER))
    private void crowbar$renderLocatorMarkers(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        CrowBarHudRenderer.renderLocatorBarMarkers(context, tickCounter);
    }
}
