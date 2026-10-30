package dev.isoforge.camera;

import dev.isoforge.IsoConfig;
import net.minecraft.world.phys.Vec3;

/** Estado da camera isometrica: giro em passos de 90 graus com suavizacao, zoom e FOV estreito. */
public final class CameraRig {
    private static double targetYaw = 45.0;
    private static double yaw = 45.0;
    private static double yawO = 45.0;
    private static double targetZoom = 20.0;
    private static double zoom = 20.0;
    private static double zoomO = 20.0;
    private static double targetPitchOffset;
    private static double pitchOffset;
    private static double pitchOffsetO;

    private CameraRig() {}

    public static void reset() {
        double y = IsoConfig.V.startYaw.get();
        targetYaw = yaw = yawO = y;
        double z = IsoConfig.V.zoomDefault.get();
        targetZoom = zoom = zoomO = z;
        targetPitchOffset = 0.0;
        pitchOffset = 0.0;
        pitchOffsetO = 0.0;
    }

    public static void rotate(int steps) {
        rotateDegrees(90.0 * steps);
    }

    /** Rotacao suave, usada enquanto uma tecla de giro permanece pressionada. */
    public static void rotateDegrees(double degrees) {
        targetYaw += degrees;
    }

    public static void adjustPitch(double degrees) {
        targetPitchOffset = Math.max(-25.0, Math.min(25.0, targetPitchOffset + degrees));
    }

    public static void setPitch(double degrees) {
        IsoConfig.V.pitch.set(Math.max(10.0, Math.min(80.0, degrees)));
        IsoConfig.V.pitch.save();
        targetPitchOffset = 0.0;
        pitchOffset = 0.0;
        pitchOffsetO = 0.0;
    }

    public static void zoomBy(double blocks) {
        double min = IsoConfig.V.zoomMin.get();
        double max = Math.max(min + 1.0, IsoConfig.V.zoomMax.get());
        targetZoom = Math.max(min, Math.min(max, targetZoom + blocks));
    }

    /** Chamado uma vez por tick de cliente. */
    public static void tick() {
        yawO = yaw;
        zoomO = zoom;
        pitchOffsetO = pitchOffset;
        double k = IsoConfig.V.smoothing.get();
        double diff = targetYaw - yaw;
        yaw = Math.abs(diff) < 0.05 ? targetYaw : yaw + diff * k;
        double zd = targetZoom - zoom;
        zoom = Math.abs(zd) < 0.01 ? targetZoom : zoom + zd * 0.35;
        double pd = targetPitchOffset - pitchOffset;
        pitchOffset = Math.abs(pd) < 0.05 ? targetPitchOffset : pitchOffset + pd * k;
    }

    /** Yaw alvo (sem animacao). O reveal usa este valor para nao recalcular a cada frame. */
    public static double targetYaw() {
        return targetYaw;
    }

    public static double yaw(float partialTick) {
        return yawO + (yaw - yawO) * partialTick;
    }

    /** Yaw no tick atual, usado para converter o WASD. */
    public static double yawNow() {
        return yaw;
    }

    public static double pitch() {
        return Math.max(10.0, Math.min(80.0, IsoConfig.V.pitch.get() + pitchOffset));
    }

    /** Inclinacao interpolada entre ticks, usada pela camera para evitar degraus visuais. */
    public static double pitch(float partialTick) {
        double offset = pitchOffsetO + (pitchOffset - pitchOffsetO) * partialTick;
        return Math.max(10.0, Math.min(80.0, IsoConfig.V.pitch.get() + offset));
    }

    /** Inclinacao de destino; o reveal acompanha a direcao final da camera. */
    public static double targetPitch() {
        return Math.max(10.0, Math.min(80.0, IsoConfig.V.pitch.get() + targetPitchOffset));
    }

    public static double zoomNow() {
        return zoom;
    }

    public static double distance() {
        return IsoConfig.V.distance.get();
    }

    /** FOV vertical (graus) para que a tela mostre {@code zoom} blocos de altura a {@code distance} blocos. */
    public static double fovDegrees(float partialTick) {
        double z = zoomO + (zoom - zoomO) * partialTick;
        return Math.toDegrees(2.0 * Math.atan((z * 0.5) / distance()));
    }

    /** Vetor de visao para yaw/pitch em graus (mesma convencao do Minecraft). */
    public static Vec3 forward(double yawDeg, double pitchDeg) {
        double yr = Math.toRadians(yawDeg);
        double pr = Math.toRadians(pitchDeg);
        double cp = Math.cos(pr);
        return new Vec3(-Math.sin(yr) * cp, -Math.sin(pr), Math.cos(yr) * cp);
    }
}
