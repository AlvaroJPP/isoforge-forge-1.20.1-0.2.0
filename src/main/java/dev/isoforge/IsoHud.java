package dev.isoforge;

import dev.isoforge.camera.CameraRig;
import dev.isoforge.input.IsoClickToMove;
import dev.isoforge.reveal.IsoReveal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** HUD leve do modo ARPG: estado da camera, alvo do cursor, vida e fome. */
@Mod.EventBusSubscriber(modid = IsoForge.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IsoHud {
    private IsoHud() {}

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!IsoClient.active() || player == null || mc.screen != null) return;

        GuiGraphics gui = event.getGuiGraphics();
        int x = 10;
        int y = 10;
        int width = 202;
        int height = 72;
        gui.fill(x, y, x + width, y + height, 0xC90E1116);
        gui.fill(x, y, x + width, y + 2, 0xFFF5A524);
        gui.drawString(mc.font, "ISOCRAFT", x + 9, y + 8, 0xFFF5A524, false);
        gui.drawString(mc.font, String.format("CAM  %03.0f°   ZOOM  %.1f", CameraRig.yawNow() % 360.0,
                CameraRig.zoomNow()), x + 9, y + 21, 0xFFE1E6ED, false);

        String reveal = IsoReveal.userEnabled() && IsoConfig.V.revealEnabled.get() ? "REVEAL ON" : "REVEAL OFF";
        String movement = IsoClickToMove.isActive() ? "AUTO MOVE" : "WASD";
        gui.drawString(mc.font, reveal + "  ·  " + movement, x + 9, y + 34, 0xFF9EA8B5, false);

        BlockPos destination = IsoClickToMove.target();
        if (destination != null) {
            String route = "DESTINO  " + destination.getX() + ", " + destination.getY() + ", "
                    + destination.getZ() + "  ·  " + IsoClickToMove.remainingNodes() + " passos";
            gui.drawString(mc.font, route, x + 9, y + 46, 0xFFF5C16A, false);
        }

        drawMeter(gui, x + 9, y + 60, 82, "HP", player.getHealth() / Math.max(1.0F, player.getMaxHealth()), 0xFFE45B5B);
        drawMeter(gui, x + 111, y + 60, 82, "FOOD", player.getFoodData().getFoodLevel() / 20.0F, 0xFFE5B84C);

        if (IsoConfig.V.showAimIndicator.get()) drawAimIndicator(mc, gui);
        drawTargetInfo(mc, gui);
    }

    private static void drawMeter(GuiGraphics gui, int x, int y, int width, String label, float value, int color) {
        gui.drawString(Minecraft.getInstance().font, label, x, y - 1, 0xFFDDE2E8, false);
        int barX = x + 29;
        gui.fill(barX, y, x + width, y + 6, 0xFF303741);
        int fill = Math.max(0, Math.min(width - 29, Math.round((width - 29) * value)));
        if (fill > 0) gui.fill(barX, y, barX + fill, y + 6, color);
    }

    /** Painel compacto do alvo vivo sob o cursor, sem depender de mods de HUD externos. */
    private static void drawTargetInfo(Minecraft mc, GuiGraphics gui) {
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target)
                || !target.isAlive() || target == mc.player) return;

        int width = Math.max(112, Math.min(220, mc.font.width(target.getDisplayName()) + 76));
        int x = gui.guiWidth() - width - 10;
        int y = 10;
        gui.fill(x, y, x + width, y + 38, 0xC90E1116);
        gui.fill(x, y, x + width, y + 2, 0xFFF5A524);
        String name = mc.font.plainSubstrByWidth(target.getDisplayName().getString(), width - 18);
        gui.drawString(mc.font, name, x + 8, y + 7, 0xFFE8EDF3, false);
        float ratio = target.getHealth() / Math.max(1.0F, target.getMaxHealth());
        gui.fill(x + 8, y + 20, x + width - 8, y + 26, 0xFF303741);
        int healthWidth = Math.max(0, Math.min(width - 16, Math.round((width - 16) * ratio)));
        if (healthWidth > 0) gui.fill(x + 8, y + 20, x + 8 + healthWidth, y + 26, 0xFFE45B5B);
        String detail = String.format("%.1f / %.1f HP  ·  %.1fm", target.getHealth(),
                target.getMaxHealth(), Math.sqrt(target.distanceToSqr(mc.player)));
        gui.drawString(mc.font, detail, x + 8, y + 28, 0xFF9EA8B5, false);
    }

    private static void drawAimIndicator(Minecraft mc, GuiGraphics gui) {
        double scale = mc.getWindow().getGuiScale();
        int x = (int) Math.round(mc.mouseHandler.xpos() / scale);
        int y = (int) Math.round(mc.mouseHandler.ypos() / scale);
        int color = mc.hitResult != null && mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                ? 0xFFFFC45B : 0xFFE5E9EF;
        gui.fill(x - 5, y - 1, x + 6, y + 1, color);
        gui.fill(x - 1, y - 5, x + 1, y + 6, color);
        gui.fill(x - 2, y - 2, x + 3, y + 3, 0xB00E1116);
    }
}
