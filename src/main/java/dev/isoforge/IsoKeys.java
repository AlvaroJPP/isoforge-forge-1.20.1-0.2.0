package dev.isoforge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = IsoForge.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class IsoKeys {
    private static final String CATEGORY = "key.categories.isoforge";

    public static final KeyMapping TOGGLE = new KeyMapping("key.isoforge.toggle",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, CATEGORY);
    public static final KeyMapping ROTATE_LEFT = new KeyMapping("key.isoforge.rotate_left",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping ROTATE_RIGHT = new KeyMapping("key.isoforge.rotate_right",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
    public static final KeyMapping TOGGLE_REVEAL = new KeyMapping("key.isoforge.toggle_reveal",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY);
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping("key.isoforge.settings",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, CATEGORY);
    public static final KeyMapping ZOOM_IN = new KeyMapping("key.isoforge.zoom_in",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, CATEGORY);
    public static final KeyMapping ZOOM_OUT = new KeyMapping("key.isoforge.zoom_out",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_MINUS, CATEGORY);
    public static final KeyMapping PITCH_UP = new KeyMapping("key.isoforge.pitch_up",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_UP, CATEGORY);
    public static final KeyMapping PITCH_DOWN = new KeyMapping("key.isoforge.pitch_down",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_DOWN, CATEGORY);

    private IsoKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(ROTATE_LEFT);
        event.register(ROTATE_RIGHT);
        event.register(TOGGLE_REVEAL);
        event.register(OPEN_SETTINGS);
        event.register(ZOOM_IN);
        event.register(ZOOM_OUT);
        event.register(PITCH_UP);
        event.register(PITCH_DOWN);
    }
}
