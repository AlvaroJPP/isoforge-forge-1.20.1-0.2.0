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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Navegacao ARPG client-side.
 * A busca A* usa uma grade 3D limitada, evita cortes diagonais por cantos bloqueados,
 * permite degraus de um bloco e procura um destino realmente alcancavel perto do clique.
 * Nao envia pacotes nem altera o servidor.
 */
public final class IsoClickToMove {
    private static final int MAX_EXPANSIONS = 9_000;
    private static final int MAX_ROUTE_RADIUS = 64;
    private static final int REPLAN_STUCK_TICKS = 18;
    private static final int REPLAN_COOLDOWN_TICKS = 20;
    private static final int ROUTE_CHECK_INTERVAL = 5;
    private static final int ROUTE_LOOKAHEAD = 3;
    private static final double WAYPOINT_RADIUS_SQUARED = 0.30 * 0.30;
    private static final double PROGRESS_DISTANCE_SQUARED = 0.20 * 0.20;

    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private static final List<BlockPos> path = new ArrayList<>();
    private static BlockPos target;
    private static boolean active;
    private static boolean jumpRequested;
    private static Vec3 lastProgressPosition;
    private static int stuckTicks;
    private static int routeCheckTicks;
    private static int repathCooldown;

    private IsoClickToMove() {}

    public static boolean isActive() {
        return active;
    }

    /** Destino atual da navegacao automatica, ou null quando inativa. */
    public static BlockPos target() {
        return active ? target : null;
    }

    /** Quantidade de nos restantes na rota, incluindo a posicao atual. */
    public static int remainingNodes() {
        return active ? path.size() : 0;
    }

    public static void cancel() {
        active = false;
        jumpRequested = false;
        target = null;
        path.clear();
        lastProgressPosition = null;
        stuckTicks = 0;
        routeCheckTicks = 0;
        repathCooldown = 0;
    }

    /** Cria uma rota ate uma superficie caminhavel alcancavel perto do ponto clicado. */
    public static boolean setTarget(ClientLevel level, LocalPlayer player, Vec3 point) {
        cancel();
        if (level == null || player == null || point == null) return false;

        BlockPos start = player.blockPosition();
        List<BlockPos> route = findPath(level, start, findNearbyGoals(level, point, start), point);
        if (route.size() <= 1) return false;

        path.addAll(route);
        target = path.get(path.size() - 1);
        active = true;
        lastProgressPosition = player.position();
        return true;
    }

    /** Avanca a rota, valida os proximos passos e tenta recuperar movimentos interrompidos. */
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

        if (repathCooldown > 0) repathCooldown--;
        advanceReachedWaypoints(player);
        if (path.size() <= 1) {
            cancel();
            return;
        }

        if (++routeCheckTicks >= ROUTE_CHECK_INTERVAL) {
            routeCheckTicks = 0;
            if (hasBlockedWaypoint(mc.level)) {
                if (!rebuildPath(mc.level, player)) {
                    cancel();
                    return;
                }
            }
        }

        updateProgress(player);
        if (stuckTicks >= REPLAN_STUCK_TICKS && repathCooldown == 0) {
            if (!rebuildPath(mc.level, player)) {
                cancel();
                return;
            }
            stuckTicks = 0;
            repathCooldown = REPLAN_COOLDOWN_TICKS;
            lastProgressPosition = player.position();
        }

        if (path.size() <= 1) {
            cancel();
            return;
        }

        BlockPos next = path.get(1);
        jumpRequested = next.getY() > player.blockPosition().getY() && player.onGround();
    }

    private static void advanceReachedWaypoints(LocalPlayer player) {
        while (path.size() > 1) {
            BlockPos next = path.get(1);
            double dx = next.getX() + 0.5 - player.getX();
            double dz = next.getZ() + 0.5 - player.getZ();
            if (dx * dx + dz * dz > WAYPOINT_RADIUS_SQUARED) break;
            path.remove(0);
        }
    }

    private static boolean hasBlockedWaypoint(ClientLevel level) {
        int end = Math.min(path.size(), ROUTE_LOOKAHEAD + 1);
        for (int i = 1; i < end; i++) {
            if (!isWalkable(level, path.get(i))) return true;
        }
        return false;
    }

    private static void updateProgress(LocalPlayer player) {
        Vec3 current = player.position();
        if (lastProgressPosition == null
                || current.distanceToSqr(lastProgressPosition) >= PROGRESS_DISTANCE_SQUARED) {
            lastProgressPosition = current;
            stuckTicks = 0;
        } else if (player.onGround()) {
            stuckTicks++;
        }
    }

    /**
     * Recalcula preservando o ponto original. Se ele ficou ocupado ou isolado,
     * a busca aceita outra superficie caminhavel proxima que seja alcancavel.
     */
    private static boolean rebuildPath(ClientLevel level, LocalPlayer player) {
        if (target == null) return false;
        Vec3 destinationPoint = new Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        List<BlockPos> replacement = findPath(level, player.blockPosition(),
                findNearbyGoals(level, destinationPoint, player.blockPosition()), destinationPoint);
        if (replacement.size() <= 1) return false;

        path.clear();
        path.addAll(replacement);
        target = path.get(path.size() - 1);
        jumpRequested = false;
        routeCheckTicks = 0;
        return true;
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

    /**
     * Cria um conjunto pequeno de destinos candidatos em volta do clique.
     * O A* escolhe o primeiro destino desse conjunto que realmente consegue alcancar.
     */
    private static Set<Long> findNearbyGoals(ClientLevel level, Vec3 point, BlockPos start) {
        int x = (int) Math.floor(point.x);
        int y = (int) Math.floor(point.y);
        int z = (int) Math.floor(point.z);
        Set<Long> goals = new HashSet<>();

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 2; dy >= -3; dy--) {
                    BlockPos candidate = new BlockPos(x + dx, y + dy, z + dz);
                    if (isWalkable(level, candidate)) goals.add(candidate.asLong());
                }
            }
        }
        goals.remove(start.asLong());
        return goals;
    }

    private static List<BlockPos> findPath(ClientLevel level, BlockPos start, Set<Long> goals, Vec3 reference) {
        if (goals.isEmpty()) return List.of();
        long startKey = start.asLong();
        if (goals.contains(startKey)) return List.of(start);

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        Map<Long, Double> costs = new HashMap<>();
        Map<Long, Long> parents = new HashMap<>();
        open.add(new Node(startKey, 0.0, heuristic(start, reference)));
        costs.put(startKey, 0.0);

        int expanded = 0;
        while (!open.isEmpty() && expanded++ < MAX_EXPANSIONS) {
            Node node = open.poll();
            Double known = costs.get(node.key());
            if (known == null || node.cost() > known + 1.0E-5) continue;
            if (goals.contains(node.key())) return reconstruct(startKey, node.key(), parents);

            BlockPos current = unpack(node.key());
            for (int[] direction : DIRECTIONS) {
                int nx = current.getX() + direction[0];
                int nz = current.getZ() + direction[1];
                if (Math.abs(nx - start.getX()) > MAX_ROUTE_RADIUS
                        || Math.abs(nz - start.getZ()) > MAX_ROUTE_RADIUS) continue;

                boolean diagonal = direction[0] != 0 && direction[1] != 0;
                if (diagonal) {
                    BlockPos sideX = new BlockPos(current.getX() + direction[0], current.getY(), current.getZ());
                    BlockPos sideZ = new BlockPos(current.getX(), current.getY(), current.getZ() + direction[1]);
                    if (!isWalkable(level, sideX) || !isWalkable(level, sideZ)) continue;
                }

                for (int dy : new int[]{0, 1, -1}) {
                    BlockPos next = new BlockPos(nx, current.getY() + dy, nz);
                    if (!isWalkable(level, next)) continue;

                    double stepCost = diagonal ? 1.41421356237 : 1.0;
                    if (dy > 0) stepCost += 0.85;       // subir exige salto
                    else if (dy < 0) stepCost += 1.65;  // evita quedas desnecessarias
                    double nextCost = node.cost() + stepCost;
                    long nextKey = next.asLong();
                    double previous = costs.getOrDefault(nextKey, Double.MAX_VALUE);
                    if (nextCost >= previous) continue;

                    costs.put(nextKey, nextCost);
                    parents.put(nextKey, node.key());
                    open.add(new Node(nextKey, nextCost, heuristic(next, reference)));
                }
            }
        }
        return List.of();
    }

    private static double horizontalDistanceSquared(Vec3 point, BlockPos from) {
        double dx = point.x - (from.getX() + 0.5);
        double dz = point.z - (from.getZ() + 0.5);
        return dx * dx + dz * dz;
    }

    private static boolean isWalkable(ClientLevel level, BlockPos feet) {
        if (level.isOutsideBuildHeight(feet) || !level.hasChunkAt(feet)) return false;
        BlockPos head = feet.above();
        BlockPos floor = feet.below();
        if (level.isOutsideBuildHeight(head) || !level.hasChunkAt(head) || !level.hasChunkAt(floor)) return false;

        CollisionContext context = CollisionContext.empty();
        if (!level.getBlockState(feet).getCollisionShape(level, feet, context).isEmpty()) return false;
        if (!level.getBlockState(head).getCollisionShape(level, head, context).isEmpty()) return false;
        return !level.getBlockState(floor).getCollisionShape(level, floor, context).isEmpty();
    }

    private static double heuristic(BlockPos from, Vec3 to) {
        double dx = Math.abs(from.getX() + 0.5 - to.x);
        double dz = Math.abs(from.getZ() + 0.5 - to.z);
        double diagonal = Math.min(dx, dz);
        double horizontal = diagonal * 1.41421356237 + Math.max(dx, dz) - diagonal;
        double vertical = Math.abs(from.getY() - to.y);
        // A distancia residual ate o ponto clicado desempata superficies alcancaveis:
        // uma rota nao deve terminar no primeiro chao proximo se houver outro junto ao clique.
        return horizontal + vertical * 1.2;
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
