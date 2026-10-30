package dev.isoforge.mixin;

import dev.isoforge.IsoClient;
import net.minecraft.client.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Como o jogador olha para o cursor, "andar para frente" nao existe: qualquer direcao pode correr. */
@Mixin(Input.class)
public abstract class InputMixin {
    @Shadow public float leftImpulse;
    @Shadow public float forwardImpulse;

    @Inject(method = "hasForwardImpulse", at = @At("HEAD"), cancellable = true, require = 0)
    private void isoforge$anyDirection(CallbackInfoReturnable<Boolean> cir) {
        if (IsoClient.active()) {
            cir.setReturnValue(Math.abs(this.forwardImpulse) > 1.0E-5F || Math.abs(this.leftImpulse) > 1.0E-5F);
        }
    }
}
