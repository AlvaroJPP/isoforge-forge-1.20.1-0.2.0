package dev.isoforge.mixin;

import dev.isoforge.IsoClient;
import dev.isoforge.input.IsoPicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "pick", at = @At("TAIL"))
    private void isoforge$pick(float partialTick, CallbackInfo ci) {
        if (IsoClient.active()) IsoPicker.update(Minecraft.getInstance(), partialTick);
    }
}
