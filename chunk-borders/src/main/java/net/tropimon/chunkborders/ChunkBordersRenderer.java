package net.tropimon.chunkborders;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class ChunkBordersRenderer {

    // Couleur : jaune vif
    private static final float R = 1.0f;
    private static final float G = 1.0f;
    private static final float B = 0.0f;
    private static final float A = 0.9f;

    private static final int LINE_BOTTOM = -64;
    private static final int LINE_TOP = 320;

    // Longueur des bras de la croix horizontale (en blocs)
    private static final float CROSS_SIZE = 1.0f;

    public static void render(WorldRenderContext context) {
        if (!ChunkBordersClient.showBorders) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Camera camera = context.camera();
        Vec3d camPos = camera.getPos();

        int playerChunkX = (int) Math.floor(camPos.x) >> 4;
        int playerChunkZ = (int) Math.floor(camPos.z) >> 4;

        int radius = 8;

        Matrix4f viewMatrix = context.matrixStack().peek().getPositionMatrix();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.lineWidth(2.0f);

        Tessellator tessellator = Tessellator.getInstance();

        // Dessiner un "+" à chaque coin de chunk dans le rayon
        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius + 1; cx++) {
            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius + 1; cz++) {
                drawCornerCross(tessellator, viewMatrix, camPos, cx * 16.0, cz * 16.0);
            }
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void drawCornerCross(Tessellator tessellator, Matrix4f viewMatrix,
                                         Vec3d camPos, double worldX, double worldZ) {
        float x = (float)(worldX - camPos.x);
        float z = (float)(worldZ - camPos.z);
        float bottom = (float)(LINE_BOTTOM - camPos.y);
        float top    = (float)(LINE_TOP    - camPos.y);

        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        // Ligne verticale
        buffer.vertex(viewMatrix, x, bottom, z).color(R, G, B, A);
        buffer.vertex(viewMatrix, x, top,    z).color(R, G, B, A);

        // Bras horizontal Nord-Sud (le long de Z)
        buffer.vertex(viewMatrix, x, bottom, z - CROSS_SIZE).color(R, G, B, A);
        buffer.vertex(viewMatrix, x, bottom, z + CROSS_SIZE).color(R, G, B, A);

        // Bras horizontal Est-Ouest (le long de X)
        buffer.vertex(viewMatrix, x - CROSS_SIZE, bottom, z).color(R, G, B, A);
        buffer.vertex(viewMatrix, x + CROSS_SIZE, bottom, z).color(R, G, B, A);

        // Même croix à mi-hauteur (y=64) pour mieux voir en hauteur
        float mid = (float)(64 - camPos.y);
        buffer.vertex(viewMatrix, x, mid, z - CROSS_SIZE).color(R, G, B, A * 0.6f);
        buffer.vertex(viewMatrix, x, mid, z + CROSS_SIZE).color(R, G, B, A * 0.6f);
        buffer.vertex(viewMatrix, x - CROSS_SIZE, mid, z).color(R, G, B, A * 0.6f);
        buffer.vertex(viewMatrix, x + CROSS_SIZE, mid, z).color(R, G, B, A * 0.6f);

        // Même croix en haut
        buffer.vertex(viewMatrix, x, top, z - CROSS_SIZE).color(R, G, B, A * 0.4f);
        buffer.vertex(viewMatrix, x, top, z + CROSS_SIZE).color(R, G, B, A * 0.4f);
        buffer.vertex(viewMatrix, x - CROSS_SIZE, top, z).color(R, G, B, A * 0.4f);
        buffer.vertex(viewMatrix, x + CROSS_SIZE, top, z).color(R, G, B, A * 0.4f);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}
