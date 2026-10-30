package dev.isoforge.input;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Regras fisicas e de seguranca compartilhadas pelo planejador de rotas.
 * Mantem a busca A* focada no grafo e concentra aqui as consultas de colisao,
 * agua, perigos e transicoes especiais do terreno.
 */
final class IsoNavigationRules {
    private static final double PLAYER_HALF_WIDTH = 0.30;
    private static final double PLAYER_HEIGHT = 1.80;

    private IsoNavigationRules() {}

    static boolean isTraversable(ClientLevel level, BlockPos feet) {
        if (!hasBodyClearance(level, feet)) return false;
        if (isWaterCell(level, feet)) return true;
        return hasSafeFloor(level, feet);
    }

    static boolean isGroundCell(ClientLevel level, BlockPos feet) {
        return hasBodyClearance(level, feet) && hasSafeFloor(level, feet);
    }

    static boolean isWaterCell(ClientLevel level, BlockPos feet) {
        return !level.isOutsideBuildHeight(feet)
                && level.hasChunkAt(feet)
                && level.getFluidState(feet).is(FluidTags.WATER);
    }

    private static boolean hasBodyClearance(ClientLevel level, BlockPos feet) {
        if (level.isOutsideBuildHeight(feet) || !level.hasChunkAt(feet)) return false;
        int topY = feet.getY() + (int) Math.ceil(PLAYER_HEIGHT);
        BlockPos top = new BlockPos(feet.getX(), topY, feet.getZ());
        if (level.isOutsideBuildHeight(top) || !level.hasChunkAt(top)) return false;
        if (isHazardousFluid(level, feet)) return false;
        if (isDangerousBlock(level.getBlockState(feet).getBlock())
                || isDangerousBlock(level.getBlockState(feet.above()).getBlock())) return false;

        // Testa a caixa real aproximada do jogador, nao apenas se os blocos estao vazios.
        // Assim, portas abertas e formas parciais deixam de bloquear quando ha espaco real.
        AABB body = new AABB(
                feet.getX() + 0.5 - PLAYER_HALF_WIDTH,
                feet.getY(),
                feet.getZ() + 0.5 - PLAYER_HALF_WIDTH,
                feet.getX() + 0.5 + PLAYER_HALF_WIDTH,
                feet.getY() + PLAYER_HEIGHT,
                feet.getZ() + 0.5 + PLAYER_HALF_WIDTH
        );
        return level.noCollision(null, body);
    }

    private static boolean hasSafeFloor(ClientLevel level, BlockPos feet) {
        BlockPos floor = feet.below();
        if (level.isOutsideBuildHeight(floor) || !level.hasChunkAt(floor) || isHazardousFluid(level, floor)) {
            return false;
        }
        Block block = level.getBlockState(floor).getBlock();
        if (isDangerousBlock(block)) return false;
        return !level.getBlockState(floor)
                .getCollisionShape(level, floor, CollisionContext.empty()).isEmpty();
    }

    private static boolean isHazardousFluid(ClientLevel level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.LAVA);
    }

    private static boolean isDangerousBlock(Block block) {
        return block == Blocks.MAGMA_BLOCK
                || block == Blocks.CACTUS
                || block == Blocks.FIRE
                || block == Blocks.SOUL_FIRE
                || block == Blocks.CAMPFIRE
                || block == Blocks.SOUL_CAMPFIRE
                || block == Blocks.SWEET_BERRY_BUSH
                || block == Blocks.WITHER_ROSE
                || block == Blocks.POINTED_DRIPSTONE
                || block == Blocks.POWDER_SNOW;
    }

    static boolean canDropSafely(ClientLevel level, BlockPos from, BlockPos landing) {
        int drop = from.getY() - landing.getY();
        if (drop < 2 || drop > 3) return false;
        if (!isGroundCell(level, landing)) return false;

        // Toda a coluna da queda precisa estar livre; nao atravessa pisos nem tetos.
        for (int y = landing.getY() + 1; y <= from.getY() + 1; y++) {
            BlockPos segment = new BlockPos(landing.getX(), y, landing.getZ());
            if (!hasBodyClearance(level, segment)) return false;
        }
        return true;
    }

    static boolean isJumpArcClear(ClientLevel level, BlockPos from, int dx, int dz) {
        if (Math.abs(dx) + Math.abs(dz) != 1) return false;
        BlockPos gap = from.offset(dx, 0, dz);

        // A caixa do personagem precisa passar sobre a lacuna sem atingir teto ou parede.
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos segment = gap.above(dy);
            if (!hasBodyClearance(level, segment) || isHazardousFluid(level, segment)) return false;
        }

        BlockPos landing = from.offset(dx * 2, 0, dz * 2);
        return isGroundCell(level, landing);
    }
}
