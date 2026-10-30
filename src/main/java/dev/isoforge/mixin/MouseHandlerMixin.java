package dev.isoforge.mixin;

import dev.isoforge.IsoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mantem o cursor livre (sem "grab") enquanto o modo isometrico esta ligado. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void isoforge$noGrab(CallbackInfo ci) {
        if (IsoClient.active()) ci.cancel();
    }

    /** O vanilla so permite segurar o botao para minerar se o mouse estiver "grabbed". */
    @Inject(method = "isMouseGrabbed", at = @At("HEAD"), cancellable = true)
    private void isoforge$grabbed(CallbackInfoReturnable<Boolean> cir) {
        if (IsoClient.active() && Minecraft.getInstance().screen == null) cir.setReturnValue(true);
    }
}
