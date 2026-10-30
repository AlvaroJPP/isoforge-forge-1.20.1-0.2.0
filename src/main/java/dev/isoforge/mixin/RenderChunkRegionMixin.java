package dev.isoforge.mixin;

import dev.isoforge.reveal.IsoReveal;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Malha do vanilla: blocos escondidos pelo reveal viram ar (nao geram geometria nem block entity). */
@Mixin(RenderChunkRegion.class)
public abstract class RenderChunkRegionMixin {
    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void isoforge$hide(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (IsoReveal.isHidden(pos.getX(), pos.getY(), pos.getZ(), cir.getReturnValue())) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
