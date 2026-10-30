package dev.isoforge;

import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(IsoForge.MOD_ID)
public final class IsoForge {
    public static final String MOD_ID = "isoforge";

    public IsoForge() {
        ModLoadingContext ctx = ModLoadingContext.get();
        ctx.registerConfig(ModConfig.Type.CLIENT, IsoConfig.SPEC);
        // Mod somente de cliente: servidores sem ele aceitam o jogador normalmente.
        ctx.registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (remote, isServer) -> true));
    }
}
