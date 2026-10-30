package dev.isoforge;

import dev.isoforge.camera.CameraRig;
import dev.isoforge.input.IsoClickToMove;
import dev.isoforge.reveal.IsoReveal;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** HUD leve do modo ARPG: estado da camera, alvo do cursor, vida e fome. */
@Mod.EventBusSubscriber(modid = IsoForge.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IsoHud {
    private static final Map<UUID, HealthSample> HEALTH_SAMPLES = new HashMap<>();
    private static final Map<UUID, DamagePopup> DAMAGE_POPUPS = new HashMap<>();
    private static long lastDamageCleanup = Long.MIN_VALUE;
    private static final long DAMAGE_LIFETIME_TICKS = 18L;

    private IsoHud() {}

    /**
     * Nameplate ARPG para criaturas hostis e para qualquer entidade viva sob o cursor.
     * O desenho e feito no espaco do mundo, usando o buffer da propria renderizacao da entidade.
     */
    @SubscribeEvent
    public static void onLivingRender(RenderLivingEvent.Post<?, ?> event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!IsoClient.active() || player == null || mc.level == null) return;

        LivingEntity entity = event.getEntity();
        if (entity == player || entity.isInvisibleTo(player) || IsoReveal.isEntityHidden(entity)) return;

        long gameTime = mc.level.getGameTime();
        DamagePopup damage = observeDamage(entity, gameTime);
        boolean showDamage = IsoConfig.V.showDamageNumbers.get()
                && damage != null && gameTime - damage.startedAt() < DAMAGE_LIFETIME_TICKS;
        boolean targeted = entity.isAlive()
                && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == entity;
        boolean showNameplate = entity.isAlive() && IsoConfig.V.showNameplates.get()
                && (targeted || entity.getType().getCategory() == MobCategory.MONSTER);

        if (!showDamage && !showNameplate) return;

        double distanceSqr = entity.distanceToSqr(player);
        double maxDistance = showNameplate
                ? IsoConfig.V.nameplateRange.get() + (targeted ? 8.0 : 0.0)
                : IsoConfig.V.nameplateRange.get();
        if (distanceSqr > maxDistance * maxDistance) return;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0, entity.getBbHeight() + 0.42, 0.0);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025F, -0.025F, 0.025F);

        Font font = mc.font;
        var matrix = pose.last().pose();
        var buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();

        if (showNameplate) {
            String rawName = entity.getDisplayName().getString();
            String label = font.plainSubstrByWidth(rawName, 132);
            if (targeted) label = "ALVO  ·  " + label;
            int labelColor = targeted ? 0xFFFFC15A : 0xFFE7EBF0;
            font.drawInBatch(label, -font.width(label) * 0.5F, -2.0F, labelColor, true,
                    matrix, buffers, Font.DisplayMode.NORMAL, 0x700A0D12, light);

            float ratio = Mth.clamp(entity.getHealth() / Math.max(1.0F, entity.getMaxHealth()), 0.0F, 1.0F);
            boolean showHealth = targeted || ratio < 0.999F;
            if (IsoConfig.V.showHealthBars.get() && showHealth) {
                String background = "██████████";
                int barWidth = font.width(background);
                float barX = -barWidth * 0.5F;
                font.drawInBatch(background, barX, 9.0F, 0xFF343B45, false,
                        matrix, buffers, Font.DisplayMode.NORMAL, 0x800A0D12, light);

                int filled = Math.round(10.0F * ratio);
                if (filled > 0) {
                    String foreground = "█".repeat(filled);
                    font.drawInBatch(foreground, barX, 9.0F, healthColor(ratio), false,
                            matrix, buffers, Font.DisplayMode.NORMAL, 0, light);
                }

                if (targeted || ratio < 0.60F) {
                    String health = String.format("%.0f / %.0f", entity.getHealth(), entity.getMaxHealth());
                    font.drawInBatch(health, -font.width(health) * 0.5F, 19.0F, 0xFFD6DCE4, true,
                            matrix, buffers, Font.DisplayMode.NORMAL, 0x700A0D12, light);
                }
            }
        }

        if (showDamage) {
            float age = (gameTime - damage.startedAt()) + event.getPartialTick();
            float rise = Math.min(18.0F, age) * 0.75F;
            int alpha = Mth.clamp(Math.round(255.0F * (1.0F - age / DAMAGE_LIFETIME_TICKS)), 0, 255);
            int color = (alpha << 24) | 0x00FFB45F;
            String text = String.format("-%.1f", damage.amount());
            pose.pushPose();
            pose.translate(0.0, -0.55 - rise * 0.025, 0.0);
            float impactScale = 1.0F + Mth.clamp(
                    damage.amount() / Math.max(1.0F, entity.getMaxHealth()) * 0.4F, 0.0F, 0.35F);
            pose.scale(impactScale, impactScale, 1.0F);
            font.drawInBatch(text, -font.width(text) * 0.5F, -20.0F, color, true,
                    pose.last().pose(), buffers, Font.DisplayMode.NORMAL, (alpha / 2) << 24, light);
            pose.popPose();
        }
        pose.popPose();
    }

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
            route = mc.font.plainSubstrByWidth(route, width - 18);
            gui.drawString(mc.font, route, x + 9, y + 46, 0xFFF5C16A, false);
        }

        drawMeter(gui, x + 9, y + 60, 82, "HP", player.getHealth() / Math.max(1.0F, player.getMaxHealth()), 0xFFE45B5B);
        drawMeter(gui, x + 111, y + 60, 82, "FOOD", player.getFoodData().getFoodLevel() / 20.0F, 0xFFE5B84C);

        if (IsoConfig.V.showAimIndicator.get()) drawAimIndicator(mc, gui);
        drawTargetInfo(mc, gui);
    }

    /** Detecta perda real de HP no cliente e cria um popup temporario por entidade. */
    private static DamagePopup observeDamage(LivingEntity entity, long gameTime) {
        if (lastDamageCleanup != Long.MIN_VALUE && gameTime < lastDamageCleanup) {
            // Um novo mundo pode iniciar com gameTime menor; nao misture amostras antigas.
            HEALTH_SAMPLES.clear();
            DAMAGE_POPUPS.clear();
            lastDamageCleanup = Long.MIN_VALUE;
        }

        UUID id = entity.getUUID();
        float health = entity.getHealth();
        HealthSample previous = HEALTH_SAMPLES.put(id, new HealthSample(health, gameTime));

        if (previous != null && health < previous.health() - 0.05F) {
            float amount = previous.health() - health;
            DamagePopup current = DAMAGE_POPUPS.get(id);
            if (current != null && current.startedAt() == gameTime) {
                DAMAGE_POPUPS.put(id, new DamagePopup(current.amount() + amount, gameTime));
            } else {
                DAMAGE_POPUPS.put(id, new DamagePopup(amount, gameTime));
            }
        }

        if (lastDamageCleanup == Long.MIN_VALUE || gameTime - lastDamageCleanup >= 40L) {
            lastDamageCleanup = gameTime;
            HEALTH_SAMPLES.entrySet().removeIf(entry -> gameTime - entry.getValue().lastSeenAt() > 200L);
            DAMAGE_POPUPS.entrySet().removeIf(entry ->
                    gameTime - entry.getValue().startedAt() >= DAMAGE_LIFETIME_TICKS);
        }
        return DAMAGE_POPUPS.get(id);
    }

    private record HealthSample(float health, long lastSeenAt) {}
    private record DamagePopup(float amount, long startedAt) {}

    private static int healthColor(float ratio) {
        if (ratio > 0.60F) return 0xFF55D68B;
        if (ratio > 0.30F) return 0xFFF5A524;
        return 0xFFE45B5B;
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

        String titlePrefix = "ALVO  ·  ";
        String detail = String.format("%.1f / %.1f HP  ·  %.1fm", target.getHealth(),
                target.getMaxHealth(), Math.sqrt(target.distanceToSqr(mc.player)));
        int width = Math.max(148, Math.min(260,
                Math.max(mc.font.width(titlePrefix) + mc.font.width(target.getDisplayName()) + 16,
                        mc.font.width(detail) + 16)));
        int x = gui.guiWidth() - width - 10;
        int y = 10;
        gui.fill(x, y, x + width, y + 42, 0xC90E1116);
        gui.fill(x, y, x + width, y + 2, 0xFFF5A524);
        String name = mc.font.plainSubstrByWidth(target.getDisplayName().getString(),
                width - mc.font.width(titlePrefix) - 16);
        gui.drawString(mc.font, titlePrefix + name, x + 8, y + 6, 0xFFFFC15A, false);
        float ratio = Mth.clamp(target.getHealth() / Math.max(1.0F, target.getMaxHealth()), 0.0F, 1.0F);
        gui.fill(x + 8, y + 19, x + width - 8, y + 25, 0xFF303741);
        int healthWidth = Math.max(0, Math.min(width - 16, Math.round((width - 16) * ratio)));
        if (healthWidth > 0) gui.fill(x + 8, y + 19, x + 8 + healthWidth, y + 25, healthColor(ratio));
        gui.drawString(mc.font, detail, x + 8, y + 29, 0xFF9EA8B5, false);
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
