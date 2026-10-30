package dev.isoforge;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class IsoConfig {
    public static final ForgeConfigSpec SPEC;
    public static final Values V;

    static {
        Pair<Values, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Values::new);
        V = pair.getLeft();
        SPEC = pair.getRight();
    }

    private IsoConfig() {}

    public static final class Values {
        // ---- camera ----
        public final ForgeConfigSpec.BooleanValue enabledByDefault;
        public final ForgeConfigSpec.DoubleValue pitch;
        public final ForgeConfigSpec.DoubleValue startYaw;
        public final ForgeConfigSpec.DoubleValue distance;
        public final ForgeConfigSpec.DoubleValue zoomDefault;
        public final ForgeConfigSpec.DoubleValue zoomMin;
        public final ForgeConfigSpec.DoubleValue zoomMax;
        public final ForgeConfigSpec.DoubleValue smoothing;
        public final ForgeConfigSpec.BooleanValue scrollNeedsCtrl;

        // ---- input ----
        public final ForgeConfigSpec.BooleanValue moveRelativeToCamera;
        public final ForgeConfigSpec.BooleanValue hideHand;
        public final ForgeConfigSpec.BooleanValue hideCrosshair;
        public final ForgeConfigSpec.BooleanValue showAimIndicator;
        public final ForgeConfigSpec.BooleanValue clickToMove;

        // ---- reveal ----
        public final ForgeConfigSpec.BooleanValue revealEnabled;
        public final ForgeConfigSpec.IntValue ceilingScan;
        public final ForgeConfigSpec.IntValue ceilingRadius;
        public final ForgeConfigSpec.BooleanValue leavesAreRoof;
        public final ForgeConfigSpec.BooleanValue roomAwareCutaway;
        public final ForgeConfigSpec.IntValue debounceTicks;
        public final ForgeConfigSpec.IntValue anchorStep;
        public final ForgeConfigSpec.DoubleValue tubeNear;
        public final ForgeConfigSpec.DoubleValue tubeFar;
        public final ForgeConfigSpec.DoubleValue tubeRamp;
        public final ForgeConfigSpec.DoubleValue tubeStart;
        public final ForgeConfigSpec.IntValue revealReach;
        public final ForgeConfigSpec.IntValue rebuildsPerTick;

        // ---- HUD ----
        public final ForgeConfigSpec.BooleanValue showNameplates;
        public final ForgeConfigSpec.BooleanValue showHealthBars;
        public final ForgeConfigSpec.IntValue nameplateRange;

        Values(ForgeConfigSpec.Builder b) {
            b.push("camera");
            enabledByDefault = b.comment("Liga o modo isometrico automaticamente ao entrar no mundo.")
                    .define("enabledByDefault", true);
            pitch = b.comment("Inclinacao da camera em graus (35.264 = isometrico verdadeiro, 30 = dimetrico).")
                    .defineInRange("pitch", 35.264, 10.0, 80.0);
            startYaw = b.comment("Angulo horizontal inicial (45 = diagonal). A camera gira em passos de 90.")
                    .defineInRange("startYaw", 45.0, -360.0, 360.0);
            distance = b.comment("Distancia da camera ao jogador. Quanto maior, mais proximo de ortografico (FOV menor).")
                    .defineInRange("distance", 64.0, 24.0, 160.0);
            zoomDefault = b.comment("Altura visivel da tela, em blocos.")
                    .defineInRange("zoomDefault", 20.0, 4.0, 80.0);
            zoomMin = b.defineInRange("zoomMin", 8.0, 2.0, 80.0);
            zoomMax = b.defineInRange("zoomMax", 44.0, 4.0, 120.0);
            smoothing = b.comment("Suavizacao do giro (0.05 = lento, 0.6 = quase instantaneo).")
                    .defineInRange("smoothing", 0.25, 0.05, 1.0);
            scrollNeedsCtrl = b.comment("Zoom so com Ctrl + scroll (senao o scroll continua trocando a hotbar).")
                    .define("scrollNeedsCtrl", true);
            b.pop();

            b.push("input");
            moveRelativeToCamera = b.comment("WASD anda relativo a camera (e nao a direcao do cursor).")
                    .define("moveRelativeToCamera", true);
            hideHand = b.define("hideHand", true);
            hideCrosshair = b.define("hideCrosshair", true);
            showAimIndicator = b.comment("Mostra o indicador do alvo no cursor.").define("showAimIndicator", true);
            clickToMove = b.comment("Permite Ctrl + clique direito para caminhar ate o ponto selecionado.").define("clickToMove", true);
            b.pop();

            b.push("reveal");
            revealEnabled = b.comment("Liga o dollhouse reveal.")
                    .define("revealEnabled", true);
            ceilingScan = b.comment("Ate quantos blocos acima da cabeca procurar um teto.")
                    .defineInRange("ceilingScan", 24, 3, 64);
            ceilingRadius = b.comment("Raio horizontal (blocos) do corte do teto/andares de cima.")
                    .defineInRange("ceilingRadius", 12, 3, 48);
            leavesAreRoof = b.comment("Copas de arvores contam como teto.")
                    .define("leavesAreRoof", false);
            roomAwareCutaway = b.comment("Detecta o espaco conectado do comodo e recorta o teto coluna por coluna.")
                    .define("roomAwareCutaway", true);
            debounceTicks = b.comment("Ticks que um novo teto precisa ficar estavel antes de aplicar (evita piscar).")
                    .defineInRange("debounceTicks", 3, 0, 20);
            anchorStep = b.comment("O reveal so acompanha o jogador a cada N blocos (menos reconstrucao de chunks = menos lag).")
                    .defineInRange("anchorStep", 2, 1, 6);
            tubeNear = b.comment("Raio do tubo de visao perto do jogador (blocos). Deve ser maior que anchorStep.")
                    .defineInRange("tubeNear", 3.0, 0.5, 12.0);
            tubeFar = b.comment("Raio do tubo de visao longe do jogador, perto da camera.")
                    .defineInRange("tubeFar", 6.0, 0.5, 24.0);
            tubeRamp = b.comment("Distancia (blocos) ao longo do tubo ate chegar no raio tubeFar.")
                    .defineInRange("tubeRamp", 24.0, 2.0, 80.0);
            tubeStart = b.comment("Distancia minima do jogador ate onde o tubo comeca a esconder blocos.")
                    .defineInRange("tubeStart", 1.2, 0.0, 6.0);
            revealReach = b.comment("Comprimento do tubo de visao a partir do jogador, em blocos.")
                    .defineInRange("revealReach", 48, 8, 120);
            rebuildsPerTick = b.comment("Maximo de secoes de chunk reconstruidas por tick pelo reveal.")
                    .defineInRange("rebuildsPerTick", 24, 1, 256);
            b.pop();

            b.push("hud");
            showNameplates = b.comment("Mostra os nomes de criaturas hostis e da entidade selecionada.")
                    .define("showNameplates", true);
            showHealthBars = b.comment("Mostra barras e valores de vida nos nameplates.")
                    .define("showHealthBars", true);
            nameplateRange = b.comment("Distancia maxima, em blocos, para nameplates de criaturas hostis.")
                    .defineInRange("nameplateRange", 20, 8, 48);
            b.pop();
        }
    }
}
