package dev.isoforge.input;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Geometria de navegacao baseada na superficie real de colisao.
 * Cada altura e quantizada em meios blocos, cobrindo lajes e os degraus das escadas vanilla.
 */
final class IsoNavigationRules {
    private static final double HALF_WIDTH = 0.30;
    private static final double PLAYER_HEIGHT = 1.80;
    private static final double EPSILON = 1.0E-4;

    private IsoNavigationRules() {}

    static List<Double> findStandHeights(ClientLevel level, int x, int z, double aroundY) {
        TreeSet<Double> heights = new TreeSet<>();
        int baseY = (int) Math.floor(aroundY);
        CollisionContext context = CollisionContext.empty();

        for (int blockY = baseY - 2; blockY <= baseY + 2; blockY++) {
            BlockPos support = new BlockPos(x, blockY, z);
            if (level.isOutsideBuildHeight(support) || !level.hasChunkAt(support)) continue;
            if (isDangerousBlock(level.getBlockState(support).getBlock())) continue;

            VoxelShape shape = level.getBlockState(support).getCollisionShape(level, support, context);
            for (AABB box : shape.toAabbs()) {
                if (0.5 < box.minX - EPSILON || 0.5 > box.maxX + EPSILON
                        || 0.5 < box.minZ - EPSILON || 0.5 > box.maxZ + EPSILON) continue;
                double feetY = blockY + box.maxY;
                if (Math.abs(feetY - aroundY) > 3.1) continue;
                if (Math.abs(feetY * 2.0 - Math.rint(feetY * 2.0)) > 0.02) continue;
                if (isStandable(level, new NavPoint(x, feetY, z))) heights.add(feetY);
            }
        }
        return new ArrayList<>(heights);
    }

    static boolean isStandable(ClientLevel level, NavPoint point) {
        return hasBodyClearance(level, point.x(), point.z(), point.feetY())
                && hasSupport(level, point.x(), point.z(), point.feetY());
    }

    static boolean isWaterNode(ClientLevel level, NavPoint point) {
        BlockPos fluidPos = BlockPos.containing(point.x() + 0.5, point.feetY() + 0.1, point.z() + 0.5);
        return !level.isOutsideBuildHeight(fluidPos) && level.hasChunkAt(fluidPos)
                && level.getFluidState(fluidPos).is(FluidTags.WATER)
                && hasBodyClearance(level, point.x(), point.z(), point.feetY());
    }

    static boolean hasBodyClearance(ClientLevel level, int x, int z, double feetY) {
        BlockPos feet = BlockPos.containing(x + 0.5, feetY + EPSILON, z + 0.5);
        BlockPos head = BlockPos.containing(x + 0.5, feetY + PLAYER_HEIGHT, z + 0.5);
        if (level.isOutsideBuildHeight(feet) || level.isOutsideBuildHeight(head)
                || !level.hasChunkAt(feet) || !level.hasChunkAt(head)) return false;
        if (isLava(level, feet) || isLava(level, head)) return false;
        if (isDangerousBlock(level.getBlockState(feet).getBlock())
                || isDangerousBlock(level.getBlockState(head).getBlock())) return false;

        AABB body = new AABB(x + 0.5 - HALF_WIDTH, feetY, z + 0.5 - HALF_WIDTH,
                x + 0.5 + HALF_WIDTH, feetY + PLAYER_HEIGHT, z + 0.5 + HALF_WIDTH);
        return level.noCollision(null, body);
    }

    private static boolean hasSupport(ClientLevel level, int x, int z, double feetY) {
        BlockPos support = BlockPos.containing(x + 0.5, feetY - EPSILON, z + 0.5);
        if (level.isOutsideBuildHeight(support) || !level.hasChunkAt(support) || isLava(level, support)) return false;
        Block block = level.getBlockState(support).getBlock();
        if (isDangerousBlock(block)) return false;
        VoxelShape shape = level.getBlockState(support).getCollisionShape(level, support, CollisionContext.empty());
        double localTop = feetY - support.getY();
        for (AABB box : shape.toAabbs()) {
            if (Math.abs(box.maxY - localTop) <= 0.02
                    && xzContainsCenter(box)) return true;
        }
        return false;
    }

    private static boolean xzContainsCenter(AABB box) {
        return box.minX <= 0.5 + EPSILON && box.maxX >= 0.5 - EPSILON
                && box.minZ <= 0.5 + EPSILON && box.maxZ >= 0.5 - EPSILON;
    }

        static boolean isWaterNode(ClientLevel level, int x, double feetY, int z) {
        return isWaterNode(level, new NavPoint(x, feetY, z));
    }

    static boolean isStandable(ClientLevel level, int x, double feetY, int z) {
        return isStandable(level, new NavPoint(x, feetY, z));
    }

    static boolean canDropSafely(ClientLevel level, int fromX, double fromY, int fromZ,
                                  int toX, double toY, int toZ) {
        double drop = fromY - toY;
        if (drop <= 0.60 || drop > 3.0 || !isStandable(level, new NavPoint(toX, toY, toZ))) return false;
        for (double y = toY + 0.1; y <= fromY + PLAYER_HEIGHT; y += 0.5) {
            if (!hasBodyClearance(level, toX, toZ, y)) return false;
        }
        return true;
    }

    static boolean canJumpUp(ClientLevel level, int fromX, double fromY, int fromZ,
                             int toX, double toY, int toZ) {
        double rise = toY - fromY;
        if (rise <= 0.60 || rise > 1.05 || !isStandable(level, new NavPoint(toX, toY, toZ))) return false;
        return hasBodyClearance(level, fromX, fromZ, fromY)
                && hasBodyClearance(level, toX, toZ, fromY + 0.5);
    }

    static boolean isJumpArcClear(ClientLevel level, int fromX, double fromY, int fromZ,
                                  int landingX, double landingY, int landingZ, int dx, int dz) {
        if (Math.abs(dx) + Math.abs(dz) != 1
                || !isStandable(level, new NavPoint(landingX, landingY, landingZ))) return false;
        double arcY = fromY + 0.55;
        return hasBodyClearance(level, fromX + dx, fromZ + dz, arcY);
    }

    private static boolean isLava(ClientLevel level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.LAVA);
    }

    private static boolean isDangerousBlock(Block block) {
        return block == Blocks.MAGMA_BLOCK || block == Blocks.CACTUS || block == Blocks.FIRE
                || block == Blocks.SOUL_FIRE || block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE
                || block == Blocks.SWEET_BERRY_BUSH || block == Blocks.WITHER_ROSE
                || block == Blocks.POINTED_DRIPSTONE || block == Blocks.POWDER_SNOW;
    }

    record NavPoint(int x, double feetY, int z) {}
}
