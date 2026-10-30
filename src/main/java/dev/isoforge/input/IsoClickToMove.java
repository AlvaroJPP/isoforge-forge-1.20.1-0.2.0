package dev.isoforge.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Click-to-move client-side para o modo isometrico.
 * A rota e calculada em uma grade 3D pequena, respeitando espaco para o jogador,
 * piso, degraus de um bloco e cantos diagonais. Nao envia pacotes nem altera o servidor.
 */
public final class IsoClickToMove {
    private static final int MAX_EXPANSIONS = 3_500;
    private static final int MAX_ROUTE_RADIUS = 48;
    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private static final List<BlockPos> path = new ArrayList<>();
    private static boolean active;
    private static boolean jumpRequested;

    private IsoClickToMove() {}

    public static boolean isActive() {
        return active;
    }

    public static void cancel() {
        active = false;
        jumpRequested = false;
        path.clear();
    }

    /** Cria uma rota ate uma posicao clicada. A posicao final e ajustada para o bloco caminhavel mais proximo. */
    public static boolean setTarget(ClientLevel level, LocalPlayer player, Vec3 point) {
        cancel();
        if (level == null || player == null || point == null) return false;

        BlockPos start = player.blockPosition();
        BlockPos goal = findNearbyGoal(level, point, start);
        if (goal == null) return false;

        List<BlockPos> route = findPath(level, start, goal);
        if (route.isEmpty()) return false;
        path.addAll(route);
        active = path.size() > 1;
        return active;
    }

    /** Chamado no tick do cliente para avancar a rota e respeitar movimento manual. */
    public static void tick(Minecraft mc) {
        if (!active) return;
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null || mc.screen != null) {
            cancel();
            return;
        }

        // O teclado manual sempre tem prioridade sobre a navegacao automatica.
        if (mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown()) {
            cancel();
            return;
        }

        while (path.size() > 1) {
            BlockPos next = path.get(1);
            double dx = next.getX() + 0.5 - player.getX();
            double dz = next.getZ() + 0.5 - player.getZ();
            if (dx * dx + dz * dz > 0.30 * 0.30) break;
            path.remove(0);
        }

        if (path.size() <= 1) {
            cancel();
            return;
        }

        BlockPos next = path.get(1);
        jumpRequested = next.getY() > player.blockPosition().getY() && player.onGround();
    }

    /** Substitui os impulsos de teclado enquanto a rota estiver ativa. */
    public static boolean applyInput(Input input, LocalPlayer player) {
        if (!active || path.size() < 2) return false;
        BlockPos next = path.get(1);
        double dx = next.getX() + 0.5 - player.getX();
        double dz = next.getZ() + 0.5 - player.getZ();
        if (dx * dx + dz * dz < 1.0E-5) {
            input.leftImpulse = 0.0F;
            input.forwardImpulse = 0.0F;
            input.jumping = jumpRequested;
            return true;
        }

        double desiredYaw = Math.atan2(-dx, dz);
        double yaw = Math.toRadians(player.getYRot());
        double difference = desiredYaw - yaw;
        input.leftImpulse = (float) -Math.sin(difference);
        input.forwardImpulse = (float) Math.cos(difference);
        input.jumping = jumpRequested;
        return true;
    }

    private static BlockPos findNearbyGoal(ClientLevel level, Vec3 point, BlockPos start) {
        int x = (int) Math.floor(point.x);
        int y = (int) Math.floor(point.y);
        int z = (int) Math.floor(point.z);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 2; dy >= -3; dy--) {
                    BlockPos candidate = new BlockPos(x + dx, y + dy, z + dz);
                    if (!isWalkable(level, candidate)) continue;
                    double horizontal = Math.hypot(candidate.getX() + 0.5 - point.x,
                            candidate.getZ() + 0.5 - point.z);
                    double vertical = Math.abs(candidate.getY() - start.getY()) * 0.35;
                    double score = horizontal + vertical;
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private static List<BlockPos> findPath(ClientLevel level, BlockPos start, BlockPos goal) {
        long startKey = start.asLong();
        long goalKey = goal.asLong();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        Map<Long, Double> costs = new HashMap<>();
        Map<Long, Long> parents = new HashMap<>();
        open.add(new Node(startKey, 0.0, heuristic(start, goal)));
        costs.put(startKey, 0.0);

        int expanded = 0;
        while (!open.isEmpty() && expanded++ < MAX_EXPANSIONS) {
            Node node = open.poll();
            Double known = costs.get(node.key());
            if (known == null || node.cost() > known + 1.0E-5) continue;
            if (node.key() == goalKey) return reconstruct(startKey, goalKey, parents);

            BlockPos current = unpack(node.key());
            for (int[] direction : DIRECTIONS) {
                int nx = current.getX() + direction[0];
                int nz = current.getZ() + direction[1];
                if (Math.abs(nx - start.getX()) > MAX_ROUTE_RADIUS
                        || Math.abs(nz - start.getZ()) > MAX_ROUTE_RADIUS) continue;

                boolean diagonal = direction[0] != 0 && direction[1] != 0;
                if (diagonal && (!isWalkable(level, new BlockPos(current.getX() + direction[0], current.getY(), current.getZ()))
                        || !isWalkable(level, new BlockPos(current.getX(), current.getY(), current.getZ() + direction[1])))) {
                    continue;
                }

                for (int dy : new int[]{0, 1, -1}) {
                    BlockPos next = new BlockPos(nx, current.getY() + dy, nz);
                    if (!isWalkable(level, next)) continue;
                    double step = diagonal ? 1.41421356237 : 1.0;
                    if (dy != 0) step += 0.55;
                    double nextCost = node.cost() + step;
                    long nextKey = next.asLong();
                    double previous = costs.getOrDefault(nextKey, Double.MAX_VALUE);
                    if (nextCost >= previous) continue;
                    costs.put(nextKey, nextCost);
                    parents.put(nextKey, node.key());
                    open.add(new Node(nextKey, nextCost, heuristic(next, goal)));
                }
            }
        }
        return List.of();
    }

    private static boolean isWalkable(ClientLevel level, BlockPos feet) {
        if (level.isOutsideBuildHeight(feet) || !level.hasChunkAt(feet) || !level.hasChunkAt(feet.below())) return false;
        CollisionContext context = CollisionContext.empty();
        if (!level.getBlockState(feet).getCollisionShape(level, feet, context).isEmpty()) return false;
        BlockPos head = feet.above();
        if (level.isOutsideBuildHeight(head) || !level.getBlockState(head).getCollisionShape(level, head, context).isEmpty()) return false;
        BlockPos floor = feet.below();
        return !level.getBlockState(floor).getCollisionShape(level, floor, context).isEmpty();
    }

    private static double heuristic(BlockPos from, BlockPos to) {
        int dx = Math.abs(from.getX() - to.getX());
        int dz = Math.abs(from.getZ() - to.getZ());
        int dy = Math.abs(from.getY() - to.getY());
        int diagonal = Math.min(dx, dz);
        return diagonal * 1.41421356237 + (Math.max(dx, dz) - diagonal) + dy * 1.2;
    }

    private static List<BlockPos> reconstruct(long start, long goal, Map<Long, Long> parents) {
        List<BlockPos> result = new ArrayList<>();
        long cursor = goal;
        result.add(unpack(cursor));
        int guard = 0;
        while (cursor != start && guard++ < MAX_EXPANSIONS) {
            Long parent = parents.get(cursor);
            if (parent == null) return List.of();
            cursor = parent;
            result.add(unpack(cursor));
        }
        if (cursor != start) return List.of();
        Collections.reverse(result);
        return result;
    }

    private static BlockPos unpack(long packed) {
        return new BlockPos(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));
    }

    private record Node(long key, double cost, double estimate) {
        double score() { return cost + estimate; }
    }

}
