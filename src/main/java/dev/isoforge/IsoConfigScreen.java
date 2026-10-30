package dev.isoforge;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import dev.isoforge.camera.CameraRig;
import dev.isoforge.reveal.IsoReveal;

/** Painel de configuracao em jogo; as alteracoes tem efeito imediato e sao gravadas no TOML do cliente. */
public final class IsoConfigScreen extends Screen {
    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private static final int PANEL_HEIGHT = 450;

    public IsoConfigScreen(Screen parent) {
        super(Component.literal("Isocraft Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        stepperLabels.clear();
        clearWidgets();
        panelWidth = Math.min(520, this.width - 28);
        panelX = (this.width - panelWidth) / 2;
        panelY = Math.max(12, (this.height - PANEL_HEIGHT) / 2);
        int gap = 8;
        int columnWidth = (panelWidth - 44 - gap) / 2;
        int left = panelX + 18;
        int right = left + columnWidth + gap;
        int y = panelY + 72;

        addToggle(left, y, columnWidth, "Modo isometrico", () -> IsoClient.active(),
                () -> IsoClient.setActive(!IsoClient.active()));
        addToggle(right, y, columnWidth, "Room Reveal", IsoReveal::userEnabled,
                () -> IsoReveal.setUserEnabled(!IsoReveal.userEnabled()));
        y += 31;

        addToggle(left, y, columnWidth, "Reveal por comodos", IsoConfig.V.roomAwareCutaway::get,
                () -> change(IsoConfig.V.roomAwareCutaway, !IsoConfig.V.roomAwareCutaway.get()));
        addToggle(right, y, columnWidth, "Click-to-Move", IsoConfig.V.clickToMove::get,
                () -> change(IsoConfig.V.clickToMove, !IsoConfig.V.clickToMove.get()));
        y += 31;

        addToggle(left, y, columnWidth, "WASD relativo a camera", IsoConfig.V.moveRelativeToCamera::get,
                () -> change(IsoConfig.V.moveRelativeToCamera, !IsoConfig.V.moveRelativeToCamera.get()));
        addToggle(right, y, columnWidth, "Indicador de mira", IsoConfig.V.showAimIndicator::get,
                () -> change(IsoConfig.V.showAimIndicator, !IsoConfig.V.showAimIndicator.get()));
        y += 31;

        addToggle(left, y, columnWidth, "Ocultar mao", IsoConfig.V.hideHand::get,
                () -> change(IsoConfig.V.hideHand, !IsoConfig.V.hideHand.get()));
        addToggle(right, y, columnWidth, "Ocultar crosshair", IsoConfig.V.hideCrosshair::get,
                () -> change(IsoConfig.V.hideCrosshair, !IsoConfig.V.hideCrosshair.get()));
        y += 42;

        addToggle(left, y, columnWidth, "Nameplates ARPG", IsoConfig.V.showNameplates::get,
                () -> change(IsoConfig.V.showNameplates, !IsoConfig.V.showNameplates.get()));
        addToggle(right, y, columnWidth, "Barras de vida", IsoConfig.V.showHealthBars::get,
                () -> change(IsoConfig.V.showHealthBars, !IsoConfig.V.showHealthBars.get()));
        y += 31;

        addStepper(left, y, columnWidth, "Inclinacao", () -> String.format("%.1f°", CameraRig.pitch()),
                () -> CameraRig.setPitch(CameraRig.pitch() - 2.0),
                () -> CameraRig.setPitch(CameraRig.pitch() + 2.0));
        addStepper(right, y, columnWidth, "Zoom", () -> String.format("%.1f blocos", CameraRig.zoomNow()),
                () -> CameraRig.zoomBy(-2.0), () -> CameraRig.zoomBy(2.0));
        y += 31;

        addStepper(left, y, columnWidth, "Distancia da camera", () -> String.format("%.0f", IsoConfig.V.distance.get()),
                () -> change(IsoConfig.V.distance, Math.max(24.0, IsoConfig.V.distance.get() - 4.0)),
                () -> change(IsoConfig.V.distance, Math.min(160.0, IsoConfig.V.distance.get() + 4.0)));
        addStepper(right, y, columnWidth, "Raio do reveal", () -> Integer.toString(IsoConfig.V.ceilingRadius.get()),
                () -> change(IsoConfig.V.ceilingRadius, Math.max(3, IsoConfig.V.ceilingRadius.get() - 1)),
                () -> change(IsoConfig.V.ceilingRadius, Math.min(48, IsoConfig.V.ceilingRadius.get() + 1)));
        y += 38;

        addRenderableWidget(Button.builder(Component.literal("Restaurar padroes"), b -> resetDefaults())
                .bounds(left, y, columnWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fechar"), b -> onClose())
                .bounds(right, y, columnWidth, 20).build());
    }

    private void addToggle(int x, int y, int width, String label, java.util.function.BooleanSupplier state, Runnable action) {
        Button button = Button.builder(toggleText(label, state.getAsBoolean()), b -> {
            action.run();
            b.setMessage(toggleText(label, state.getAsBoolean()));
        }).bounds(x, y, width, 20).build();
        addRenderableWidget(button);
    }

    private void addStepper(int x, int y, int width, String label,
                            java.util.function.Supplier<String> value,
                            Runnable decrease, Runnable increase) {
        int small = 24;
        int middle = width - small * 2 - 8;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> decrease.run())
                .bounds(x, y, small, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> increase.run())
                .bounds(x + width - small, y, small, 20).build());
        // O valor central e desenhado por render(), para que o texto acompanhe os ajustes sem recriar widgets.
        stepperLabels.add(new StepperLabel(x + small + 4, y + 6, middle, label, value));
    }

    private final java.util.List<StepperLabel> stepperLabels = new java.util.ArrayList<>();

    private static Component toggleText(String label, boolean enabled) {
        return Component.literal(label + ": " + (enabled ? "ON" : "OFF"));
    }

    private static <T> void change(ForgeConfigSpec.ConfigValue<T> value, T newValue) {
        value.set(newValue);
        value.save();
    }

    private void resetDefaults() {
        change(IsoConfig.V.enabledByDefault, IsoConfig.V.enabledByDefault.getDefault());
        change(IsoConfig.V.pitch, IsoConfig.V.pitch.getDefault());
        change(IsoConfig.V.startYaw, IsoConfig.V.startYaw.getDefault());
        change(IsoConfig.V.distance, IsoConfig.V.distance.getDefault());
        change(IsoConfig.V.zoomDefault, IsoConfig.V.zoomDefault.getDefault());
        change(IsoConfig.V.zoomMin, IsoConfig.V.zoomMin.getDefault());
        change(IsoConfig.V.zoomMax, IsoConfig.V.zoomMax.getDefault());
        change(IsoConfig.V.smoothing, IsoConfig.V.smoothing.getDefault());
        change(IsoConfig.V.scrollNeedsCtrl, IsoConfig.V.scrollNeedsCtrl.getDefault());
        change(IsoConfig.V.revealEnabled, IsoConfig.V.revealEnabled.getDefault());
        change(IsoConfig.V.ceilingRadius, IsoConfig.V.ceilingRadius.getDefault());
        change(IsoConfig.V.ceilingScan, IsoConfig.V.ceilingScan.getDefault());
        change(IsoConfig.V.leavesAreRoof, IsoConfig.V.leavesAreRoof.getDefault());
        change(IsoConfig.V.roomAwareCutaway, IsoConfig.V.roomAwareCutaway.getDefault());
        change(IsoConfig.V.debounceTicks, IsoConfig.V.debounceTicks.getDefault());
        change(IsoConfig.V.anchorStep, IsoConfig.V.anchorStep.getDefault());
        change(IsoConfig.V.tubeNear, IsoConfig.V.tubeNear.getDefault());
        change(IsoConfig.V.tubeFar, IsoConfig.V.tubeFar.getDefault());
        change(IsoConfig.V.tubeRamp, IsoConfig.V.tubeRamp.getDefault());
        change(IsoConfig.V.tubeStart, IsoConfig.V.tubeStart.getDefault());
        change(IsoConfig.V.revealReach, IsoConfig.V.revealReach.getDefault());
        change(IsoConfig.V.rebuildsPerTick, IsoConfig.V.rebuildsPerTick.getDefault());
        change(IsoConfig.V.clickToMove, IsoConfig.V.clickToMove.getDefault());
        change(IsoConfig.V.moveRelativeToCamera, IsoConfig.V.moveRelativeToCamera.getDefault());
        change(IsoConfig.V.showAimIndicator, IsoConfig.V.showAimIndicator.getDefault());
        change(IsoConfig.V.hideHand, IsoConfig.V.hideHand.getDefault());
        change(IsoConfig.V.hideCrosshair, IsoConfig.V.hideCrosshair.getDefault());
        change(IsoConfig.V.showNameplates, IsoConfig.V.showNameplates.getDefault());
        change(IsoConfig.V.showHealthBars, IsoConfig.V.showHealthBars.getDefault());
        change(IsoConfig.V.nameplateRange, IsoConfig.V.nameplateRange.getDefault());
        IsoReveal.setUserEnabled(IsoConfig.V.revealEnabled.get());
        CameraRig.reset();
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, this.width, this.height, 0x76070A0E);
        gui.fill(panelX, panelY, panelX + panelWidth, panelY + PANEL_HEIGHT, 0xF014171C);
        gui.fill(panelX, panelY, panelX + panelWidth, panelY + 2, 0xFFF5A524);
        gui.fill(panelX + 16, panelY + 50, panelX + panelWidth - 16, panelY + 51, 0xFF343B45);
        gui.drawString(this.font, "ISOCRAFT", panelX + 18, panelY + 16, 0xFFF5A524, false);
        gui.drawString(this.font, "Configuracoes da camera e da visibilidade", panelX + 18, panelY + 32, 0xFFB9C0C9, false);
        gui.drawString(this.font, "CAMERA / CONTROLE", panelX + 18, panelY + 59, 0xFFE7EAF0, false);

        for (StepperLabel label : stepperLabels) {
            String text = label.title() + "  " + label.value().get();
            int textWidth = this.font.width(text);
            gui.drawString(this.font, text, label.x() + Math.max(0, (label.width() - textWidth) / 2),
                    label.y(), 0xFFE3E7EC, false);
        }
        super.render(gui, mouseX, mouseY, partialTick);
        gui.drawString(this.font, "Ctrl + clique direito: mover  |  F6: abrir este painel  |  Z/X: girar",
                panelX + 18, panelY + PANEL_HEIGHT - 21, 0xFF929BA7, false);
    }

    @Override
    protected void rebuildWidgets() {
        stepperLabels.clear();
        super.rebuildWidgets();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record StepperLabel(int x, int y, int width, String title,
                                java.util.function.Supplier<String> value) {}
}
