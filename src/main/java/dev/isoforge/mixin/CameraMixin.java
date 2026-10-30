package dev.isoforge.mixin;

import dev.isoforge.IsoClient;
import dev.isoforge.camera.CameraRig;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow private boolean detached;

    @Inject(method = "setup", at = @At("TAIL"))
    private void isoforge$setup(BlockGetter level, Entity entity, boolean detachedParam, boolean mirror,
                                float partialTick, CallbackInfo ci) {
        if (!IsoClient.active() || entity == null) return;

        double yaw = CameraRig.yaw(partialTick);
        double pitch = CameraRig.pitch();
        Vec3 f = CameraRig.forward(yaw, pitch);
        double d = CameraRig.distance();

        double tx = Mth.lerp(partialTick, entity.xo, entity.getX());
        double ty = Mth.lerp(partialTick, entity.yo, entity.getY()) + 1.0;
        double tz = Mth.lerp(partialTick, entity.zo, entity.getZ());

        this.setRotation((float) yaw, (float) pitch);
        this.setPosition(tx - f.x * d, ty - f.y * d, tz - f.z * d);
        this.detached = true; // renderiza o modelo do jogador
    }
}
