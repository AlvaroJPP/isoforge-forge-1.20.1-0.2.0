package dev.isoforge.mixin;

import dev.isoforge.IsoClient;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A distancia maxima de block entities (64) e medida da camera; aqui passa a ser medida do jogador. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRender(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/phys/Vec3;)Z"),
            require = 0)
    private boolean isoforge$distance(BlockEntityRenderer renderer, BlockEntity be, Vec3 cameraPos) {
        return renderer.shouldRender(be, IsoClient.focus(cameraPos));
    }
}
