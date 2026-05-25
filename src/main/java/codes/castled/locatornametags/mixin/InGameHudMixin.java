package codes.castled.locatornametags.mixin;

import codes.castled.locatornametags.LocatorHudRenderer;
import codes.castled.locatornametags.LocatorNameTagsState;
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
    private void locatornametags$hideStatusBarsForSelfView(DrawContext context, CallbackInfo ci) {
        if (LocatorNameTagsState.viewSelfEnabled) {
            ci.cancel();
        }
    }

    @Inject(method = "renderMainHud", at = @At("TAIL"))
    private void locatornametags$renderSelfViewBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (LocatorNameTagsState.viewSelfEnabled) {
            LocatorHudRenderer.drawSelfView(context);
        }
    }
}
