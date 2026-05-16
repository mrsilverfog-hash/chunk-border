package net.tropimon.chunkborders;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ChunkBordersClient implements ClientModInitializer {

    private static KeyBinding toggleKey;
    public static boolean showBorders = false;

    @Override
    public void onInitializeClient() {
        // Enregistrer la touche F9
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "Bordures de chunks",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            "Chunk Borders"
        ));

        // Détecter l'appui sur F9
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                showBorders = !showBorders;
            }
        });

        // Afficher les bordures
        WorldRenderEvents.LAST.register(ChunkBordersRenderer::render);
    }
}
