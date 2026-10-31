package com.george_vi.electroenergetics.mixins;

import com.george_vi.electroenergetics.client.TrainControlInput;
import com.george_vi.electroenergetics.client.TrainControlKeys;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the cab's keys reaching anything else while an electric train is being driven
 * (M047: every convenient key is already claimed by another mod, and the player cannot
 * move the conflict out of the way).
 *
 * keyPress is the single point every key press passes through, so cancelling here stops
 * both the vanilla KeyMapping dispatch and NeoForge's event at once. Deliberately narrow:
 * only the cab's own keys, only while actually driving. The cab still reads its keys
 * because TrainControlInput asks GLFW directly.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true, remap = false)
    private void electroEnergetics$consumeCabKeys(long windowPointer, int key, int scanCode,
                                                  int action, int modifiers, CallbackInfo ci) {
        // Only on press and auto-repeat: the release must still get through, or a
        // KeyMapping would be left stuck down for the rest of the session.
        if (action == org.lwjgl.glfw.GLFW.GLFW_RELEASE)
            return;
        if (!TrainControlKeys.isCabKey(key))
            return;
        if (!TrainControlInput.isDrivingElectric())
            return;
        ci.cancel();
    }
}
