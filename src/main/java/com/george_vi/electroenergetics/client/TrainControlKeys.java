package com.george_vi.electroenergetics.client;

import com.george_vi.electroenergetics.CreateElectroEnergetics;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

/**
 * Rebindable keys of the electric control panel (B-08/D-10/M172):
 * J = vigilance confirm, Enter = engage selection, K = emergency brake,
 * up/down = gear selection, left/right = pantograph selection (2+ pantographs),
 * P = toggle selected pantograph, O = toggle its auto-raise switch.
 */
@EventBusSubscriber(modid = CreateElectroEnergetics.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class TrainControlKeys {

    public static final String CATEGORY = "key.categories.electroenergetics";

    public static final KeyMapping VIGILANCE_CONFIRM = new KeyMapping("key.electroenergetics.train_confirm",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    public static final KeyMapping ENGAGE = new KeyMapping("key.electroenergetics.train_engage",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_ENTER, CATEGORY);
    public static final KeyMapping EMERGENCY_BRAKE = new KeyMapping("key.electroenergetics.train_emergency",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    public static final KeyMapping GEAR_UP = new KeyMapping("key.electroenergetics.train_lever_up",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP, CATEGORY);
    public static final KeyMapping GEAR_DOWN = new KeyMapping("key.electroenergetics.train_lever_down",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, CATEGORY);
    public static final KeyMapping PANTOGRAPH_NEXT = new KeyMapping("key.electroenergetics.pantograph_next",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, CATEGORY);
    public static final KeyMapping PANTOGRAPH_PREV = new KeyMapping("key.electroenergetics.pantograph_prev",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, CATEGORY);
    public static final KeyMapping PANTOGRAPH_TOGGLE = new KeyMapping("key.electroenergetics.pantograph_toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY);
    public static final KeyMapping PANTOGRAPH_AUTO = new KeyMapping("key.electroenergetics.pantograph_auto",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY);

    public static final KeyMapping[] ALL = {
            VIGILANCE_CONFIRM, ENGAGE, EMERGENCY_BRAKE, GEAR_UP, GEAR_DOWN,
            PANTOGRAPH_NEXT, PANTOGRAPH_PREV, PANTOGRAPH_TOGGLE, PANTOGRAPH_AUTO
    };

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping mapping : ALL)
            event.register(mapping);
    }

    /** Whether this GLFW key belongs to the cab, for the KeyboardHandler consumption. */
    public static boolean isCabKey(int glfwKey) {
        for (KeyMapping mapping : ALL) {
            if (mapping.getKey().getType() == InputConstants.Type.KEYSYM
                    && mapping.getKey().getValue() == glfwKey)
                return true;
        }
        return false;
    }
}
