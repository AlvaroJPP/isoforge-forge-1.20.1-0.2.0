package dev.isoforge.reveal;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Finds reachable air around the player and the first roof block above each reached column. */
final class RoomRevealScanner {
    private static final int MAX_VISITED_CELLS = 65_536;
    private static final int MIN_ROOF_COLUMNS = 4;

    private RoomRevealScanner() {}

    static Map<Long, Integer> scan(ClientLevel level, BlockPos feet, int radius, int verticalRange,
                                   boolean leavesAreRoof) {
        int minY = Math.max(level.getMinBuildHeight(), feet.getY() - 8);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, feet.getY() + verticalRange + 1);
        int radiusSquared = radius * radius;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long seed = BlockPos.asLong(feet.getX(), feet.getY() + 1, feet.getZ());

        if (feet.getY() + 1 > maxY || !isPassable(level, pos, feet.getX(), feet.getY() + 1, feet.getZ())) {
            return Map.of();
        }

        ArrayDeque<Long> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        Map<Long, Integer> highestByColumn = new HashMap<>();
        queue.add(seed);
        visited.add(seed);

        while (!queue.isEmpty()) {
            long packed = queue.removeFirst();
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            long column = BlockPos.asLong(x, 0, z);
            highestByColumn.merge(column, y, Math::max);

            if (visited.size() >= MAX_VISITED_CELLS) return Map.of();
            addIfPassable(level, queue, visited, pos, x + 1, y, z, feet, radiusSquared, minY, maxY);
            addIfPassable(level, queue, visited, pos, x - 1, y, z, feet, radiusSquared, minY, maxY);
            addIfPassable(level, queue, visited, pos, x, y + 1, z, feet, radiusSquared, minY, maxY);
            addIfPassable(level, queue, visited, pos, x, y - 1, z, feet, radiusSquared, minY, maxY);
            addIfPassable(level, queue, visited, pos, x, y, z + 1, feet, radiusSquared, minY, maxY);
            addIfPassable(level, queue, visited, pos, x, y, z - 1, feet, radiusSquared, minY, maxY);
        }

        Map<Long, Integer> roofCuts = new HashMap<>();
        for (Map.Entry<Long, Integer> column : highestByColumn.entrySet()) {
            int x = BlockPos.getX(column.getKey());
            int z = BlockPos.getZ(column.getKey());
            int roofSearchEnd = Math.min(level.getMaxBuildHeight() - 1, column.getValue() + verticalRange);
            for (int y = column.getValue() + 1; y <= roofSearchEnd; y++) {
                pos.set(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (!state.canOcclude() || (!leavesAreRoof && state.is(BlockTags.LEAVES))) continue;
                roofCuts.put(column.getKey(), y);
                break;
            }
        }

        return roofCuts.size() >= MIN_ROOF_COLUMNS ? Map.copyOf(roofCuts) : Map.of();
    }

    private static void addIfPassable(ClientLevel level, ArrayDeque<Long> queue, Set<Long> visited,
                                      BlockPos.MutableBlockPos pos, int x, int y, int z, BlockPos center,
                                      int radiusSquared, int minY, int maxY) {
        int dx = x - center.getX();
        int dz = z - center.getZ();
        if (y < minY || y > maxY || dx * dx + dz * dz > radiusSquared) return;

        long packed = BlockPos.asLong(x, y, z);
        if (visited.contains(packed) || !isPassable(level, pos, x, y, z)) return;
        visited.add(packed);
        queue.addLast(packed);
    }

    private static boolean isPassable(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        pos.set(x, y, z);
        if (!level.hasChunkAt(pos)) return false;
        return level.getBlockState(pos).getCollisionShape(level, pos, CollisionContext.empty()).isEmpty();
    }
}
