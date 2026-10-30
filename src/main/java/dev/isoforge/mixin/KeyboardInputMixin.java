package dev.isoforge.mixin;

import dev.isoforge.IsoClient;
import dev.isoforge.IsoConfig;
import dev.isoforge.camera.CameraRig;
import dev.isoforge.input.IsoClickToMove;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Converte o WASD para ser relativo a camera, ja que o jogador olha para o cursor. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void isoforge$cameraRelative(boolean sneaking, float sneakBonus, CallbackInfo ci) {
        if (!IsoClient.active()) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        if (IsoClickToMove.applyInput(this, p)) return;
        if (!IsoConfig.V.moveRelativeToCamera.get()) return;
        float s = this.leftImpulse;
        float f = this.forwardImpulse;
        if (s == 0.0F && f == 0.0F) return;
        double d = Math.toRadians(CameraRig.yawNow() - p.getYRot());
        float c = (float) Math.cos(d);
        float n = (float) Math.sin(d);
        this.leftImpulse = s * c - f * n;
        this.forwardImpulse = f * c + s * n;
    }
}
