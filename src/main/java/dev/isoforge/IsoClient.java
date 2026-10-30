package dev.isoforge;

import dev.isoforge.camera.CameraRig;
import dev.isoforge.input.IsoPicker;
import dev.isoforge.input.IsoClickToMove;
import dev.isoforge.reveal.IsoReveal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Estado global do modo isometrico e eventos do Forge. */
@Mod.EventBusSubscriber(modid = IsoForge.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IsoClient {
    private static boolean active;
    private static boolean inWorld;
    private static double lastFov;

    private IsoClient() {}

    public static boolean active() {
        return active;
    }

    public static double lastFov() {
        return lastFov;
    }

    /**
     * Ponto usado para distancias de entidades, particulas e som. A camera fica ~64 blocos longe do jogador,
     * entao esses calculos precisam ser feitos a partir do jogador.
     */
    public static Vec3 focus(Vec3 cameraPos) {
        if (!active) return cameraPos;
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null ? p.getEyePosition() : cameraPos;
    }

    public static void setActive(boolean on) {
        if (on == active) return;
        Minecraft mc = Minecraft.getInstance();
        active = on;
        if (on) {
            lastFov = 0.0;
            if (mc.screen == null) mc.mouseHandler.releaseMouse();
        } else {
            IsoClickToMove.cancel();
            restoreVanilla(mc, mc.level != null && mc.screen == null);
        }
    }

    private static void restoreVanilla(Minecraft mc, boolean regrabMouse) {
        mc.smartCull = true;
        Entity.setViewScale(vanillaViewScale(mc));
        IsoPicker.clear();
        if (regrabMouse) mc.mouseHandler.grabMouse(); // active ja e false aqui, entao o mixin nao cancela
    }

    private static double vanillaViewScale(Minecraft mc) {
        return Mth.clamp((double) mc.options.getEffectiveRenderDistance() / 8.0, 1.0, 2.5)
                * mc.options.entityDistanceScaling().get();
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (mc.level == null || player == null) {
            IsoClickToMove.cancel();
            if (inWorld) {
                inWorld = false;
                if (active) {
                    active = false;
                    restoreVanilla(mc, false);
                }
            }
            IsoReveal.tick(mc);
            return;
        }

        if (!inWorld) {
            inWorld = true;
            active = false;
            CameraRig.reset();
            if (IsoConfig.V.enabledByDefault.get()) setActive(true);
        }

        while (IsoKeys.TOGGLE.consumeClick()) setActive(!active);
        while (IsoKeys.OPEN_SETTINGS.consumeClick()) mc.setScreen(new IsoConfigScreen(mc.screen));

        if (active) {
            while (IsoKeys.TOGGLE_REVEAL.consumeClick()) IsoReveal.toggleUserEnabled();
            while (IsoKeys.ZOOM_IN.consumeClick()) CameraRig.zoomBy(-2.0);
            while (IsoKeys.ZOOM_OUT.consumeClick()) CameraRig.zoomBy(2.0);
            if (IsoKeys.ROTATE_LEFT.isDown()) CameraRig.rotateDegrees(2.4);
            if (IsoKeys.ROTATE_RIGHT.isDown()) CameraRig.rotateDegrees(-2.4);
            if (IsoKeys.PITCH_UP.isDown()) CameraRig.adjustPitch(0.45);
            if (IsoKeys.PITCH_DOWN.isDown()) CameraRig.adjustPitch(-0.45);

            CameraRig.tick();
            IsoClickToMove.tick(mc);
            mc.smartCull = false; // a camera fica longe/dentro de terreno: o culling por oclusao do vanilla falharia
            Entity.setViewScale(vanillaViewScale(mc) + CameraRig.distance() / 64.0);
            faceCursor(mc, player);
        } else {
            IsoClickToMove.cancel();
            while (IsoKeys.TOGGLE_REVEAL.consumeClick()) { }
            while (IsoKeys.ZOOM_IN.consumeClick()) { }
            while (IsoKeys.ZOOM_OUT.consumeClick()) { }
        }

        IsoReveal.tick(mc);
    }

    /** O jogador sempre olha na direcao do cursor (estilo ARPG). */
    private static void faceCursor(Minecraft mc, LocalPlayer player) {
        if (mc.screen != null || !player.isAlive() || player.isSleeping() || !IsoPicker.hasAim()) return;
        Vec3 aim = IsoPicker.aimPoint();
        Vec3 eye = player.getEyePosition();
        double dx = aim.x - player.getX();
        double dz = aim.z - player.getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        if (h < 0.35) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(aim.y - eye.y, h));
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        // Elytra ascent depends on the player's real pitch. During flight and swimming,
        // leave vertical look under vanilla mouse control instead of pinning it to the ground cursor.
        if (!player.isFallFlying() && !player.isSwimming() && !player.isInWater()) {
            player.setXRot(Mth.clamp(pitch, -89.0F, 89.0F));
        }
    }

    // ------------------------------------------------------------------ render / input events

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        if (!active) return;
        lastFov = CameraRig.fovDegrees((float) e.getPartialTick());
        e.setFOV(lastFov);
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog e) {
        if (!active) return;
        if (e.getMode() != FogRenderer.FogMode.FOG_TERRAIN || e.getType() != FogType.NONE) return;
        float d = (float) CameraRig.distance();
        e.setNearPlaneDistance(e.getNearPlaneDistance() + d);
        e.setFarPlaneDistance(e.getFarPlaneDistance() + d);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent e) {
        if (active && IsoConfig.V.hideHand.get()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        if (active && IsoConfig.V.hideCrosshair.get() && e.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()) {
            e.setCanceled(true);
        }
    }


    /** Ctrl + clique direito define o destino do click-to-move sem interferir no uso normal do botao direito. */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre e) {
        if (!active || !IsoConfig.V.clickToMove.get() || e.getAction() != GLFW.GLFW_PRESS
                || e.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT
                || (e.getModifiers() & GLFW.GLFW_MOD_CONTROL) == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.level == null || mc.player == null) return;
        IsoPicker.update(mc, 1.0F);
        if (IsoPicker.hasAim() && IsoClickToMove.setTarget(mc.level, mc.player, IsoPicker.aimPoint())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent e) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        if (IsoConfig.V.scrollNeedsCtrl.get() && !Screen.hasControlDown()) return;
        CameraRig.zoomBy(-Math.signum(e.getScrollDelta()) * 2.0);
        e.setCanceled(true);
    }
}
