package dev.isoforge.input;

import com.mojang.blaze3d.platform.Window;
import dev.isoforge.IsoClient;
import dev.isoforge.camera.CameraRig;
import dev.isoforge.reveal.IsoReveal;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import java.util.Optional;

/** Converte a posicao do cursor em um raio no mundo e preenche mc.hitResult / mc.crosshairPickEntity. */
public final class IsoPicker {
    private static Vec3 aimPoint = Vec3.ZERO;
    private static boolean hasAim;

    private IsoPicker() {}

    public static boolean hasAim() {
        return hasAim;
    }

    public static Vec3 aimPoint() {
        return aimPoint;
    }

    public static void clear() {
        hasAim = false;
    }

    public static void update(Minecraft mc, float partialTick) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            hasAim = false;
            return;
        }
        if (mc.screen != null) return;

        Camera cam = mc.gameRenderer.getMainCamera();
        if (!cam.isInitialized()) return;

        Window w = mc.getWindow();
        double nx = Mth.clamp(mc.mouseHandler.xpos() / (double) w.getScreenWidth() * 2.0 - 1.0, -1.0, 1.0);
        double ny = Mth.clamp(1.0 - mc.mouseHandler.ypos() / (double) w.getScreenHeight() * 2.0, -1.0, 1.0);

        double fov = IsoClient.lastFov();
        if (fov <= 0.0) fov = CameraRig.fovDegrees(partialTick);
        double tanHalf = Math.tan(Math.toRadians(fov) * 0.5);
        double aspect = (double) w.getWidth() / (double) w.getHeight();

        Vector3f look = cam.getLookVector();
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();
        double dx = look.x() - left.x() * nx * tanHalf * aspect + up.x() * ny * tanHalf;
        double dy = look.y() - left.y() * nx * tanHalf * aspect + up.y() * ny * tanHalf;
        double dz = look.z() - left.z() * nx * tanHalf * aspect + up.z() * ny * tanHalf;
        Vec3 dir = new Vec3(dx, dy, dz).normalize();

        Vec3 origin = cam.getPosition();
        double len = CameraRig.distance() * 2.0 + 96.0;
        Vec3 end = origin.add(dir.scale(len));

        BlockHitResult blockHit = raycastBlocks(level, player, origin, end);
        double bestSq = blockHit != null ? origin.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;

        Entity hitEntity = null;
        Vec3 hitEntityPos = null;
        AABB search = player.getBoundingBox().inflate(48.0);
        for (Entity e : level.getEntities(player, search, en -> en.isPickable() && !en.isSpectator())) {
            if (IsoReveal.isEntityHidden(e)) continue;
            AABB bb = e.getBoundingBox().inflate(e.getPickRadius() + 0.2);
            Optional<Vec3> c = bb.clip(origin, end);
            if (c.isEmpty()) continue;
            double d = origin.distanceToSqr(c.get());
            if (d < bestSq) {
                bestSq = d;
                hitEntity = e;
                hitEntityPos = c.get();
            }
        }

        Vec3 eye = player.getEyePosition();
        HitResult result;
        Entity crosshair = null;

        if (hitEntity != null) {
            aimPoint = hitEntity.getBoundingBox().getCenter();
            double reach = player.getEntityReach();
            if (eye.distanceToSqr(hitEntityPos) <= reach * reach) {
                result = new EntityHitResult(hitEntity, hitEntityPos);
                crosshair = hitEntity;
            } else {
                result = BlockHitResult.miss(hitEntityPos, Direction.UP, BlockPos.containing(hitEntityPos));
            }
        } else if (blockHit != null) {
            aimPoint = blockHit.getLocation();
            double reach = player.getBlockReach();
            if (eye.distanceToSqr(aimPoint) <= reach * reach) {
                result = blockHit;
            } else {
                result = BlockHitResult.miss(aimPoint, blockHit.getDirection(), blockHit.getBlockPos());
            }
        } else {
            // cursor sobre o vazio: usa o plano horizontal na altura dos pes do jogador
            double t = Math.abs(dir.y) > 1.0E-4 ? (player.getY() - origin.y) / dir.y : 0.0;
            if (t <= 0.0) t = len * 0.5;
            aimPoint = origin.add(dir.scale(t));
            result = BlockHitResult.miss(aimPoint, Direction.UP, BlockPos.containing(aimPoint));
        }

        hasAim = true;
        mc.hitResult = result;
        mc.crosshairPickEntity = crosshair;
    }

    /** Raycast em blocos que ignora o que o reveal esconde (voce nao clica em parede invisivel). */
    private static BlockHitResult raycastBlocks(ClientLevel level, LocalPlayer player, Vec3 start, Vec3 end) {
        final BlockHitResult[] out = new BlockHitResult[1];
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final CollisionContext ctx = CollisionContext.of(player);
        Dda.traverse(start.x, start.y, start.z, end.x, end.y, end.z, 1200, (x, y, z) -> {
            if (level.isOutsideBuildHeight(y)) return false;
            if (IsoReveal.isHidden(x, y, z)) return false;
            pos.set(x, y, z);
            BlockState bs = level.getBlockState(pos);
            if (bs.isAir()) return false;
            VoxelShape shape = bs.getShape(level, pos, ctx);
            if (shape.isEmpty()) return false;
            BlockHitResult hit = shape.clip(start, end, pos.immutable());
            if (hit != null) {
                out[0] = hit;
                return true;
            }
            return false;
        });
        return out[0];
    }
}
