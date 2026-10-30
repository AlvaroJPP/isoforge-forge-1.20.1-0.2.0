package dev.isoforge.mixin;

import dev.isoforge.IsoClient;
import net.minecraft.client.Camera;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** O "ouvido" fica no jogador, nao na camera distante (senao tudo soa longe/mudo). */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Redirect(method = "updateSource",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"),
            require = 0)
    private Vec3 isoforge$listener(Camera camera) {
        return IsoClient.focus(camera.getPosition());
    }
}
