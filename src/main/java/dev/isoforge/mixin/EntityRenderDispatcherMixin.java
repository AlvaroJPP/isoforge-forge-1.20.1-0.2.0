package dev.isoforge.mixin;

import dev.isoforge.reveal.IsoReveal;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Esconde entidades que estao dentro da area cortada (teto / andares de cima). */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void isoforge$hide(Entity entity, Frustum frustum, double x, double y, double z,
                               CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && IsoReveal.isEntityHidden(entity)) cir.setReturnValue(false);
    }
}
