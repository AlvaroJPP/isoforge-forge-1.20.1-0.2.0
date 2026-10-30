package dev.isoforge.reveal;

import dev.isoforge.IsoClient;
import dev.isoforge.IsoConfig;
import dev.isoforge.camera.CameraRig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * Controlador do dollhouse reveal.
 *
 * O reveal funciona na geracao da malha: os mixins (vanilla e Embeddium) fazem getBlockState devolver AR
 * nas posicoes escondidas, entao a geometria nem e criada. Por isso funciona com qualquer shaderpack (Oculus).
 */
public final class IsoReveal {
    private static volatile RevealState state = RevealState.OFF;

    private static final LinkedHashSet<Long> PENDING = new LinkedHashSet<>();

    private static boolean userEnabled = true;
    private static boolean anchorValid;
    private static int anchorX, anchorY, anchorZ;
    private static int appliedCut = RevealState.NO_CUT;
    private static int pendingCut = RevealState.NO_CUT;
    private static int pendingTicks;
    private static int roomScanTicks;
    private static int roomScanFailures;
    private static Map<Long, Integer> roomCuts = Map.of();

    private IsoReveal() {}

    // ------------------------------------------------------------------ consulta (qualquer thread)

    public static boolean isHidden(int x, int y, int z) {
        return state.isHidden(x, y, z);
    }

    /** Consulta usada pelos mixins de malha para preservar agua abaixo do jogador em movimento aquatico. */
    public static boolean isHidden(int x, int y, int z, BlockState blockState) {
        RevealState snapshot = state;
        boolean water = blockState.getFluidState().is(FluidTags.WATER);
        return snapshot.isHidden(x, y, z, water);
    }

    public static boolean isEntityHidden(Entity e) {
        RevealState s = state;
        if (!s.active()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.player || e == mc.getCameraEntity()) return false;
        // Usa o centro vertical do corpo: entidades altas nao devem atravessar o teto
        // so porque os pes ainda estao abaixo da linha de corte.
        int x = Mth.floor(e.getX());
        int y = Mth.floor(e.getY() + Math.min(e.getBbHeight() * 0.5, 1.0));
        int z = Mth.floor(e.getZ());
        return s.hiddenEntity(x, y, z);
    }

    public static void toggleUserEnabled() {
        userEnabled = !userEnabled;
    }

    public static boolean userEnabled() {
        return userEnabled;
    }

    public static void setUserEnabled(boolean enabled) {
        userEnabled = enabled;
    }

    // ------------------------------------------------------------------ tick (thread do cliente)

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        RevealState old = state;
        RevealState neu = RevealState.OFF;

        if (level != null && player != null && IsoClient.active() && userEnabled && IsoConfig.V.revealEnabled.get()) {
            neu = compute(level, player);
        } else {
            anchorValid = false;
            appliedCut = pendingCut = RevealState.NO_CUT;
            pendingTicks = 0;
            roomScanTicks = 0;
            roomScanFailures = 0;
            roomCuts = Map.of();
        }

        if (!neu.equals(old)) {
            state = neu;
            if (level != null) {
                RevealDiff.compute(old, neu, level, level.getMinSection(), level.getMaxSection(),
                        (sx, sy, sz) -> PENDING.add(SectionPos.asLong(sx, sy, sz)));
            }
        }

        if (level == null) {
            PENDING.clear();
        } else {
            drain(mc);
        }
    }

    private static void drain(Minecraft mc) {
        int budget = IsoConfig.V.rebuildsPerTick.get();
        Iterator<Long> it = PENDING.iterator();
        while (budget-- > 0 && it.hasNext()) {
            long key = it.next();
            it.remove();
            mc.levelRenderer.setSectionDirty(SectionPos.x(key), SectionPos.y(key), SectionPos.z(key));
        }
    }

    private static RevealState compute(ClientLevel level, LocalPlayer player) {
        BlockPos feet = player.blockPosition();

        // ancora: o reveal so acompanha o jogador a cada N blocos
        int step = IsoConfig.V.anchorStep.get();
        boolean anchorChanged = !anchorValid
                || Math.abs(feet.getX() - anchorX) >= step
                || Math.abs(feet.getZ() - anchorZ) >= step
                || feet.getY() != anchorY;
        if (anchorChanged) {
            anchorValid = true;
            anchorX = feet.getX();
            anchorY = feet.getY();
            anchorZ = feet.getZ();
        }

        if (IsoConfig.V.roomAwareCutaway.get() && (anchorChanged || ++roomScanTicks >= 10)) {
            roomScanTicks = 0;
            Map<Long, Integer> scannedCuts = RoomRevealScanner.scan(level, new BlockPos(anchorX, anchorY, anchorZ),
                    IsoConfig.V.ceilingRadius.get(), IsoConfig.V.ceilingScan.get(),
                    IsoConfig.V.leavesAreRoof.get());
            if (scannedCuts != null) {
                roomCuts = scannedCuts;
                roomScanFailures = 0;
            } else if (++roomScanFailures >= 2) {
                // Uma leitura incompleta isolada nao deve piscar o reveal; falhas consecutivas
                // descartam a mascara antiga para nao deixar um comodo distante recortado.
                roomCuts = Map.of();
            }
        } else if (!IsoConfig.V.roomAwareCutaway.get()) {
            roomCuts = Map.of();
            roomScanFailures = 0;
        }

        // teto com histerese: um novo valor so e aplicado depois de ficar estavel por alguns ticks
        int cand = findCeiling(level, feet);
        int debounce = IsoConfig.V.debounceTicks.get();
        if (cand == appliedCut) {
            pendingCut = cand;
            pendingTicks = 0;
        } else if (cand == pendingCut) {
            if (++pendingTicks >= debounce) {
                appliedCut = cand;
                pendingTicks = 0;
            }
        } else {
            pendingCut = cand;
            pendingTicks = 1;
            if (pendingTicks >= debounce) {
                appliedCut = cand;
                pendingTicks = 0;
            }
        }

        // direcao jogador -> camera (usa o yaw ALVO, nao o animado, para nao recalcular a cada frame)
        Vec3 f = CameraRig.forward(CameraRig.targetYaw(), CameraRig.targetPitch());
        double reach = Math.min(IsoConfig.V.revealReach.get(), CameraRig.distance());

        boolean roomDetected = !roomCuts.isEmpty();
        return new RevealState(true,
                player.isSwimming() || player.isInWater() || player.isFallFlying(),
                anchorX, anchorY, anchorZ,
                roomDetected ? RevealState.NO_CUT : appliedCut,
                IsoConfig.V.ceilingRadius.get(),
                roomCuts,
                -f.x, -f.y, -f.z,
                IsoConfig.V.tubeNear.get(), IsoConfig.V.tubeFar.get(), IsoConfig.V.tubeRamp.get(),
                IsoConfig.V.tubeStart.get(), reach);
    }

    /** Y do primeiro bloco solido acima da cabeca do jogador, ou NO_CUT se o ceu esta aberto. */
    private static int findCeiling(ClientLevel level, BlockPos feet) {
        int scan = IsoConfig.V.ceilingScan.get();
        boolean leaves = IsoConfig.V.leavesAreRoof.get();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = 2; dy <= scan + 1; dy++) {
            m.set(feet.getX(), feet.getY() + dy, feet.getZ());
            if (level.isOutsideBuildHeight(m)) break;
            BlockState bs = level.getBlockState(m);
            if (bs.isAir() || !bs.canOcclude()) continue;
            if (!leaves && bs.is(BlockTags.LEAVES)) continue;
            return m.getY();
        }
        return RevealState.NO_CUT;
    }
}
