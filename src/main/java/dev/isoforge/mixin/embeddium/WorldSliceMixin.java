package dev.isoforge.mixin.embeddium;

import dev.isoforge.reveal.IsoReveal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Malha do Embeddium/Rubidium (Sodium 0.5.x): WorldSlice e o "mundo" que o construtor de chunks enxerga.
 * Alvo por nome (string) para nao precisar do jar do Embeddium na compilacao.
 * Se a classe/metodo nao existir nesta versao, o mixin simplesmente nao e aplicado (require = 0).
 */
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.WorldSlice", remap = false)
@Pseudo
public abstract class WorldSliceMixin {
    @Inject(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void isoforge$hideXYZ(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (IsoReveal.isHidden(x, y, z)) cir.setReturnValue(Blocks.AIR.defaultBlockState());
    }

    @Inject(method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void isoforge$hidePos(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (IsoReveal.isHidden(pos.getX(), pos.getY(), pos.getZ())) cir.setReturnValue(Blocks.AIR.defaultBlockState());
    }
}
