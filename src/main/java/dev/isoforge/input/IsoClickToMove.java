package dev.isoforge.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

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
 * O A* trabalha em uma grade 3D limitada e combina caminhada, natacao,
 * saltos curtos sobre lacunas e descidas com pouso seguro.
 * Nao envia pacotes nem altera o servidor.
 */
public final class IsoClickToMove {
    private static final int MAX_EXPANSIONS = 12_000;
    private static final int MAX_ROUTE_RADIUS = 64;
    private static final int MAX_SAFE_DROP = 3;
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
    private static final int[][] CARDINAL_DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private static final List<RouteStep> path = new ArrayList<>();
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

    /** Cria uma rota ate uma superficie caminhavel ou um volume de agua alcancavel. */
    public static boolean setTarget(ClientLevel level, LocalPlayer player, Vec3 point) {
        cancel();
        if (level == null || player == null || point == null) return false;

        BlockPos start = player.blockPosition();
        List<RouteStep> route = findPath(level, start, findNearbyGoals(level, point, start), point);
        if (route.size() <= 1) return false;

        path.addAll(route);
        target = path.get(path.size() - 1).position();
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

        // Teclado manual tem prioridade; Shift tambem cancela para permitir agachar normalmente.
        if (mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown()
                || mc.options.keyShift.isDown()) {
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

        RouteStep next = path.get(1);
        int currentY = player.blockPosition().getY();
        jumpRequested = (next.jump() && player.onGround())
                || (next.position().getY() > currentY && (player.onGround() || player.isInWater()));
    }

    private static void advanceReachedWaypoints(LocalPlayer player) {
        while (path.size() > 1) {
            BlockPos next = path.get(1).position();
            double dx = next.getX() + 0.5 - player.getX();
            double dz = next.getZ() + 0.5 - player.getZ();
            double dy = Math.abs(next.getY() - player.getY());
            // Nao avanca para o proximo waypoint enquanto ainda esta caindo ou subindo na agua.
            if (dx * dx + dz * dz > WAYPOINT_RADIUS_SQUARED || dy > 0.70) break;
            path.remove(0);
        }
    }

    private static boolean hasBlockedWaypoint(ClientLevel level) {
        int end = Math.min(path.size(), ROUTE_LOOKAHEAD + 1);
        for (int i = 1; i < end; i++) {
            RouteStep step = path.get(i);
            BlockPos from = path.get(i - 1).position();
            if (!isValidStep(level, from, step)) return true;
        }
        return false;
    }

    private static void updateProgress(LocalPlayer player) {
        Vec3 current = player.position();
        if (lastProgressPosition == null
                || current.distanceToSqr(lastProgressPosition) >= PROGRESS_DISTANCE_SQUARED) {
            lastProgressPosition = current;
            stuckTicks = 0;
        } else if (player.onGround() || player.isInWater()) {
            stuckTicks++;
        }
    }

    /**
     * Recalcula preservando o destino. Se o ponto ficou bloqueado, procura outra
     * superficie alcançavel perto dele, sem redirecionar para o jogador atual.
     */
    private static boolean rebuildPath(ClientLevel level, LocalPlayer player) {
        if (target == null) return false;
        Vec3 destinationPoint = new Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        List<RouteStep> replacement = findPath(level, player.blockPosition(),
                findNearbyGoals(level, destinationPoint, player.blockPosition()), destinationPoint);
        if (replacement.size() <= 1) return false;

        path.clear();
        path.addAll(replacement);
        target = path.get(path.size() - 1).position();
        jumpRequested = false;
        routeCheckTicks = 0;
        return true;
    }

    /** Substitui os impulsos de teclado enquanto a rota estiver ativa. */
    public static boolean applyInput(Input input, LocalPlayer player) {
        if (!active || path.size() < 2) return false;
        RouteStep next = path.get(1);
        BlockPos destination = next.position();
        double dx = destination.getX() + 0.5 - player.getX();
        double dz = destination.getZ() + 0.5 - player.getZ();
        if (dx * dx + dz * dz < 1.0E-5) {
            input.leftImpulse = 0.0F;
            input.forwardImpulse = 0.0F;
        } else {
            double desiredYaw = Math.atan2(-dx, dz);
            double yaw = Math.toRadians(player.getYRot());
            double difference = desiredYaw - yaw;
            input.leftImpulse = (float) -Math.sin(difference);
            input.forwardImpulse = (float) Math.cos(difference);
        }

        input.jumping = jumpRequested;
        // No mergulho, Shift faz o jogador descer; nao e aplicado em terra.
        if (player.isInWater() && destination.getY() < player.blockPosition().getY()) {
            input.shiftKeyDown = true;
        }
        return true;
    }

    /**
     * Cria candidatos dentro da regiao clicada. Superficies perigosas e lava
     * nunca entram no conjunto de destinos.
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
                    if (IsoNavigationRules.isTraversable(level, candidate)) goals.add(candidate.asLong());
                }
            }
        }
        goals.remove(start.asLong());
        return goals;
    }

    private static List<RouteStep> findPath(ClientLevel level, BlockPos start, Set<Long> goals, Vec3 reference) {
        if (goals.isEmpty()) return List.of();
        long startKey = start.asLong();
        if (goals.contains(startKey)) return List.of(new RouteStep(start, false));

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        Map<Long, Double> costs = new HashMap<>();
        Map<Long, Long> parents = new HashMap<>();
        Map<Long, Boolean> jumpEdges = new HashMap<>();
        open.add(new Node(startKey, 0.0, heuristic(start, reference)));
        costs.put(startKey, 0.0);

        int expanded = 0;
        while (!open.isEmpty() && expanded++ < MAX_EXPANSIONS) {
            Node node = open.poll();
            Double known = costs.get(node.key());
            if (known == null || node.cost() > known + 1.0E-5) continue;
            if (goals.contains(node.key())) return reconstruct(startKey, node.key(), parents, jumpEdges);

            BlockPos current = unpack(node.key());
            boolean swimming = IsoNavigationRules.isWaterCell(level, current);

            for (int[] direction : DIRECTIONS) {
                int nx = current.getX() + direction[0];
                int nz = current.getZ() + direction[1];
                if (Math.abs(nx - start.getX()) > MAX_ROUTE_RADIUS
                        || Math.abs(nz - start.getZ()) > MAX_ROUTE_RADIUS) continue;

                boolean diagonal = direction[0] != 0 && direction[1] != 0;
                if (diagonal) {
                    BlockPos sideX = new BlockPos(current.getX() + direction[0], current.getY(), current.getZ());
                    BlockPos sideZ = new BlockPos(current.getX(), current.getY(), current.getZ() + direction[1]);
                    if (!IsoNavigationRules.isTraversable(level, sideX) || !IsoNavigationRules.isTraversable(level, sideZ)) continue;
                }

                int[] verticalMoves = swimming ? new int[]{0} : new int[]{0, 1, -1, -2, -3};
                for (int dy : verticalMoves) {
                    BlockPos next = new BlockPos(nx, current.getY() + dy, nz);
                    if (!IsoNavigationRules.isTraversable(level, next)) continue;
                    if (dy < -1 && !IsoNavigationRules.canDropSafely(level, current, next)) continue;

                    double stepCost = diagonal ? 1.41421356237 : 1.0;
                    if (dy > 0) stepCost += 0.85;       // subir exige salto
                    else if (dy == -1) stepCost += 1.65;
                    else if (dy < -1) stepCost += 1.2 + Math.abs(dy) * 0.75;
                    if (IsoNavigationRules.isWaterCell(level, next)) stepCost += 0.35;
                    addNode(open, costs, parents, jumpEdges, node, next, stepCost, false, reference);
                }
            }

            // Movimentacao vertical em coluna de agua: flutuar e mergulhar.
            if (swimming) {
                for (int dy : new int[]{1, -1}) {
                    BlockPos next = current.offset(0, dy, 0);
                    if (!IsoNavigationRules.isWaterCell(level, next)) continue;
                    addNode(open, costs, parents, jumpEdges, node, next, 1.15, false, reference);
                }
            }

            // Parkour curto: um unico bloco sem apoio entre a saida e o pouso.
            if (!swimming) {
                for (int[] direction : CARDINAL_DIRECTIONS) {
                    BlockPos landing = current.offset(direction[0] * 2, 0, direction[1] * 2);
                    if (!IsoNavigationRules.isGroundCell(level, landing) || !IsoNavigationRules.isJumpArcClear(level, current, direction[0], direction[1])) {
                        continue;
                    }
                    addNode(open, costs, parents, jumpEdges, node, landing, 2.85, true, reference);
                }
            }
        }
        return List.of();
    }

    private static void addNode(PriorityQueue<Node> open, Map<Long, Double> costs,
                                Map<Long, Long> parents, Map<Long, Boolean> jumpEdges, Node from, BlockPos next,
                                double stepCost, boolean jump, Vec3 reference) {
        double nextCost = from.cost() + stepCost;
        long nextKey = next.asLong();
        double previous = costs.getOrDefault(nextKey, Double.MAX_VALUE);
        if (nextCost >= previous) return;
        costs.put(nextKey, nextCost);
        parents.put(nextKey, from.key());
        jumpEdges.put(nextKey, jump);
        open.add(new Node(nextKey, nextCost, heuristic(next, reference)));
    }

    private static boolean isValidStep(ClientLevel level, BlockPos from, RouteStep step) {
        BlockPos to = step.position();
        if (step.jump()) {
            int dx = to.getX() - from.getX();
            int dz = to.getZ() - from.getZ();
            return IsoNavigationRules.isGroundCell(level, to) && Math.abs(dx) + Math.abs(dz) == 2
                    && IsoNavigationRules.isJumpArcClear(level, from, Integer.signum(dx), Integer.signum(dz));
        }
        if (from.getY() - to.getY() > 1) return IsoNavigationRules.canDropSafely(level, from, to);
        return IsoNavigationRules.isTraversable(level, to);
    }

    private static double heuristic(BlockPos from, Vec3 to) {
        double dx = Math.abs(from.getX() + 0.5 - to.x);
        double dz = Math.abs(from.getZ() + 0.5 - to.z);
        double diagonal = Math.min(dx, dz);
        double horizontal = diagonal * 1.41421356237 + Math.max(dx, dz) - diagonal;
        double vertical = Math.abs(from.getY() - to.y);
        return horizontal + vertical * 1.2;
    }

    private static List<RouteStep> reconstruct(long start, long goal, Map<Long, Long> parents,
                                                       Map<Long, Boolean> jumpEdges) {
        List<BlockPos> positions = new ArrayList<>();
        long cursor = goal;
        positions.add(unpack(cursor));
        int guard = 0;
        while (cursor != start && guard++ < MAX_EXPANSIONS) {
            Long parent = parents.get(cursor);
            if (parent == null) return List.of();
            cursor = parent;
            positions.add(unpack(cursor));
        }
        if (cursor != start) return List.of();
        Collections.reverse(positions);

        List<RouteStep> result = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            BlockPos position = positions.get(i);
            boolean jump = i > 0 && jumpEdges.getOrDefault(position.asLong(), false);
            result.add(new RouteStep(position, jump));
        }
        return result;
    }

    private static BlockPos unpack(long packed) {
        return new BlockPos(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));
    }

    private record RouteStep(BlockPos position, boolean jump) { }

    private record Node(long key, double cost, double estimate) {
        double score() { return cost + estimate; }
    }
}
