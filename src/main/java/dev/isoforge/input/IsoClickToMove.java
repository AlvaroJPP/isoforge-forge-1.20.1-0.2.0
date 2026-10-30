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
 * Navegacao ARPG client-side com nos em meios blocos.
 * A altura fracionaria permite representar superficies de slabs e escadas sem
 * arredondar o personagem para dentro do proprio bloco de apoio.
 */
public final class IsoClickToMove {
    private static final int MAX_EXPANSIONS = 14_000;
    private static final int MAX_ROUTE_RADIUS = 64;
    private static final int REPLAN_STUCK_TICKS = 18;
    private static final int REPLAN_COOLDOWN_TICKS = 20;
    private static final int ROUTE_CHECK_INTERVAL = 5;
    private static final int ROUTE_LOOKAHEAD = 3;
    private static final double WAYPOINT_RADIUS_SQUARED = 0.30 * 0.30;
    private static final double PROGRESS_DISTANCE_SQUARED = 0.20 * 0.20;
    private static final double MAX_WALK_STEP = 0.60;
    private static final double MAX_JUMP_STEP = 1.05;
    private static final double MAX_SAFE_DROP = 3.0;

    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    private static final int[][] CARDINAL_DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private static final List<RouteStep> path = new ArrayList<>();
    private static NavPos target;
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

    /** Destino em coordenadas de bloco, para HUD e integracoes existentes. */
    public static BlockPos target() {
        return active && target != null ? target.blockPos() : null;
    }

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

    public static boolean setTarget(ClientLevel level, LocalPlayer player, Vec3 point) {
        cancel();
        if (level == null || player == null || point == null) return false;

        NavPos start = NavPos.from(player.position());
        List<RouteStep> route = findPath(level, start, findNearbyGoals(level, point, start), point);
        if (route.size() <= 1) return false;

        path.addAll(route);
        target = path.get(path.size() - 1).position();
        active = true;
        lastProgressPosition = player.position();
        return true;
    }

    public static void tick(Minecraft mc) {
        if (!active) return;
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null || mc.screen != null) {
            cancel();
            return;
        }

        // O teclado manual sempre tem prioridade sobre o click-to-move.
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
        jumpRequested = (next.jump() && player.onGround())
                || (player.isInWater() && next.position().feetY() > player.getY() + 0.1);
    }

    private static void advanceReachedWaypoints(LocalPlayer player) {
        while (path.size() > 1) {
            NavPos next = path.get(1).position();
            double dx = next.x() + 0.5 - player.getX();
            double dz = next.z() + 0.5 - player.getZ();
            double dy = Math.abs(next.feetY() - player.getY());
            if (dx * dx + dz * dz > WAYPOINT_RADIUS_SQUARED || dy > 0.20) break;
            path.remove(0);
        }
    }

    private static boolean hasBlockedWaypoint(ClientLevel level) {
        int end = Math.min(path.size(), ROUTE_LOOKAHEAD + 1);
        for (int i = 1; i < end; i++) {
            if (!isValidStep(level, path.get(i - 1).position(), path.get(i))) return true;
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

    private static boolean rebuildPath(ClientLevel level, LocalPlayer player) {
        if (target == null) return false;
        Vec3 destination = target.center();
        NavPos start = NavPos.from(player.position());
        List<RouteStep> replacement = findPath(level, start,
                findNearbyGoals(level, destination, start), destination);
        if (replacement.size() <= 1) return false;

        path.clear();
        path.addAll(replacement);
        target = path.get(path.size() - 1).position();
        jumpRequested = false;
        routeCheckTicks = 0;
        return true;
    }

    public static boolean applyInput(Input input, LocalPlayer player) {
        if (!active || path.size() < 2) return false;
        RouteStep next = path.get(1);
        NavPos destination = next.position();
        double dx = destination.x() + 0.5 - player.getX();
        double dz = destination.z() + 0.5 - player.getZ();

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
        if (player.isInWater() && destination.feetY() < player.getY() - 0.1) {
            input.shiftKeyDown = true;
        }
        return true;
    }

    private static Set<Long> findNearbyGoals(ClientLevel level, Vec3 point, NavPos start) {
        int x = (int) Math.floor(point.x);
        int z = (int) Math.floor(point.z);
        Set<Long> goals = new HashSet<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int tx = x + dx;
                int tz = z + dz;
                for (double height : IsoNavigationRules.findStandHeights(level, tx, tz, point.y)) {
                    goals.add(NavPos.of(tx, height, tz).key());
                }
                for (int quarterY = (int) Math.round(point.y * 4.0) - 8;
                     quarterY <= (int) Math.round(point.y * 4.0) + 4; quarterY++) {
                    NavPos water = new NavPos(tx, quarterY, tz);
                    if (IsoNavigationRules.isWaterNode(level, water.x(), water.feetY(), water.z())) goals.add(water.key());
                }
            }
        }
        goals.remove(start.key());
        return goals;
    }

    private static List<RouteStep> findPath(ClientLevel level, NavPos start, Set<Long> goals, Vec3 reference) {
        if (goals.isEmpty()) return List.of();
        long startKey = start.key();
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

            NavPos current = NavPos.fromKey(node.key());
            boolean swimming = IsoNavigationRules.isWaterNode(level, current.x(), current.feetY(), current.z());
            double currentY = current.feetY();

            for (int[] direction : DIRECTIONS) {
                int nx = current.x() + direction[0];
                int nz = current.z() + direction[1];
                if (Math.abs(nx - start.x()) > MAX_ROUTE_RADIUS
                        || Math.abs(nz - start.z()) > MAX_ROUTE_RADIUS) continue;

                boolean diagonal = direction[0] != 0 && direction[1] != 0;
                if (diagonal && (!IsoNavigationRules.hasBodyClearance(level, nx, nz, currentY)
                        || !IsoNavigationRules.hasBodyClearance(level, current.x(), nz, currentY)
                        || !IsoNavigationRules.hasBodyClearance(level, nx, current.z(), currentY))) {
                    continue;
                }

                if (swimming) {
                    NavPos horizontal = new NavPos(nx, current.yQuarter(), nz);
                    if (IsoNavigationRules.isWaterNode(level, horizontal.x(), horizontal.feetY(), horizontal.z())) {
                        addNode(open, costs, parents, jumpEdges, node, horizontal,
                                (diagonal ? 1.41421356237 : 1.0) + 0.35, false, reference);
                    }
                    continue;
                }

                for (double surface : IsoNavigationRules.findStandHeights(level, nx, nz, currentY)) {
                    NavPos next = NavPos.of(nx, surface, nz);
                    double delta = surface - currentY;
                    if (delta > MAX_JUMP_STEP || delta < -MAX_SAFE_DROP) continue;

                    boolean jump = delta > MAX_WALK_STEP;
                    if (jump && !IsoNavigationRules.canJumpUp(level, current.x(), current.feetY(), current.z(), next.x(), next.feetY(), next.z())) continue;
                    if (delta < -MAX_WALK_STEP && !IsoNavigationRules.canDropSafely(level, current.x(), current.feetY(), current.z(), next.x(), next.feetY(), next.z())) continue;

                    double cost = diagonal ? 1.41421356237 : 1.0;
                    cost += Math.abs(delta) * 0.9;
                    if (jump) cost += 0.65;
                    addNode(open, costs, parents, jumpEdges, node, next, cost, jump, reference);
                }
            }

            if (swimming) {
                for (int dyQuarter : new int[]{-4, -2, -1, 1, 2, 4}) {
                    NavPos next = new NavPos(current.x(), current.yQuarter() + dyQuarter, current.z());
                    if (IsoNavigationRules.isWaterNode(level, next.x(), next.feetY(), next.z())) {
                        addNode(open, costs, parents, jumpEdges, node, next,
                                Math.abs(dyQuarter) * 0.55, false, reference);
                    }
                }
            } else {
                // Salto curto: atravessa uma lacuna de um bloco e pousa em superficie real.
                for (int[] direction : CARDINAL_DIRECTIONS) {
                    int lx = current.x() + direction[0] * 2;
                    int lz = current.z() + direction[1] * 2;
                    for (double landingHeight : IsoNavigationRules.findStandHeights(level, lx, lz, currentY)) {
                        if (Math.abs(landingHeight - currentY) > 0.5) continue;
                        NavPos landing = NavPos.of(lx, landingHeight, lz);
                        if (!IsoNavigationRules.isJumpArcClear(level, current.x(), current.feetY(), current.z(), landing.x(), landing.feetY(), landing.z(), direction[0], direction[1])) continue;
                        addNode(open, costs, parents, jumpEdges, node, landing, 2.85, true, reference);
                    }
                }
            }
        }
        return List.of();
    }

    private static void addNode(PriorityQueue<Node> open, Map<Long, Double> costs,
                                Map<Long, Long> parents, Map<Long, Boolean> jumpEdges, Node from,
                                NavPos next, double stepCost, boolean jump, Vec3 reference) {
        double nextCost = from.cost() + stepCost;
        long nextKey = next.key();
        double previous = costs.getOrDefault(nextKey, Double.MAX_VALUE);
        if (nextCost >= previous) return;
        costs.put(nextKey, nextCost);
        parents.put(nextKey, from.key());
        jumpEdges.put(nextKey, jump);
        open.add(new Node(nextKey, nextCost, heuristic(next, reference)));
    }

    private static boolean isValidStep(ClientLevel level, NavPos from, RouteStep step) {
        NavPos to = step.position();
        // Waypoints aquáticos nao exigem suporte solido; validamos o fluido e o volume do corpo.
        if (IsoNavigationRules.isWaterNode(level, to.x(), to.feetY(), to.z())) return true;
        if (step.jump() && Math.abs(to.x() - from.x()) + Math.abs(to.z() - from.z()) == 2) {
            int dx = Integer.signum(to.x() - from.x());
            int dz = Integer.signum(to.z() - from.z());
            return IsoNavigationRules.isJumpArcClear(level, from.x(), from.feetY(), from.z(), to.x(), to.feetY(), to.z(), dx, dz);
        }
        if (!IsoNavigationRules.isStandable(level, to.x(), to.feetY(), to.z())) return false;
        double delta = to.feetY() - from.feetY();
        if (delta < -MAX_WALK_STEP) return IsoNavigationRules.canDropSafely(level, from.x(), from.feetY(), from.z(), to.x(), to.feetY(), to.z());
        if (delta > MAX_WALK_STEP) return step.jump() && IsoNavigationRules.canJumpUp(level, from.x(), from.feetY(), from.z(), to.x(), to.feetY(), to.z());
        return true;
    }

    private static double heuristic(NavPos from, Vec3 to) {
        double dx = Math.abs(from.x() + 0.5 - to.x);
        double dz = Math.abs(from.z() + 0.5 - to.z);
        double diagonal = Math.min(dx, dz);
        double horizontal = diagonal * 1.41421356237 + Math.max(dx, dz) - diagonal;
        return horizontal + Math.abs(from.feetY() - to.y) * 1.1;
    }

    private static List<RouteStep> reconstruct(long start, long goal, Map<Long, Long> parents,
                                               Map<Long, Boolean> jumpEdges) {
        List<NavPos> positions = new ArrayList<>();
        long cursor = goal;
        positions.add(NavPos.fromKey(cursor));
        int guard = 0;
        while (cursor != start && guard++ < MAX_EXPANSIONS) {
            Long parent = parents.get(cursor);
            if (parent == null) return List.of();
            cursor = parent;
            positions.add(NavPos.fromKey(cursor));
        }
        if (cursor != start) return List.of();
        Collections.reverse(positions);

        List<RouteStep> result = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            NavPos position = positions.get(i);
            boolean jump = i > 0 && jumpEdges.getOrDefault(position.key(), false);
            result.add(new RouteStep(position, jump));
        }
        return result;
    }

    private record NavPos(int x, int yQuarter, int z) {
        static NavPos from(Vec3 position) {
            return new NavPos((int) Math.floor(position.x), (int) Math.round(position.y * 4.0),
                    (int) Math.floor(position.z));
        }

        static NavPos of(int x, double feetY, int z) {
            return new NavPos(x, (int) Math.round(feetY * 4.0), z);
        }

        static NavPos fromKey(long key) {
            return new NavPos(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
        }

        long key() {
            // O campo Y do BlockPos e usado como unidade de quarto de bloco (double dentro dos limites vanilla).
            return BlockPos.asLong(x, yQuarter, z);
        }

        double feetY() {
            return yQuarter * 0.25;
        }

        BlockPos blockPos() {
            return BlockPos.containing(x + 0.5, feetY(), z + 0.5);
        }

        Vec3 center() {
            return new Vec3(x + 0.5, feetY(), z + 0.5);
        }
    }

    private record RouteStep(NavPos position, boolean jump) {}
    private record Node(long key, double cost, double estimate) {
        double score() { return cost + estimate; }
    }
}
