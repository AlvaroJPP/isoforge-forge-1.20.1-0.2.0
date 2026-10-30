package dev.isoforge.mixin;

import dev.isoforge.IsoClient;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** O vanilla descarta particulas a mais de 32 blocos da camera; aqui conta a distancia do jogador. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Redirect(method = "addParticleInternal(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(DDD)D"),
            require = 0)
    private double isoforge$particleDistance(Vec3 cameraPos, double x, double y, double z) {
        return IsoClient.focus(cameraPos).distanceToSqr(x, y, z);
    }
}
