package net.tropimon.chunkborders;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class ChunkBordersRenderer {

    // Couleur des bordures : jaune vif
    private static final float R = 1.0f;
    private static final float G = 1.0f;
    private static final float B = 0.0f;
    private static final float A = 0.8f;

    // Hauteur des lignes (du bas du monde au sommet)
    private static final int LINE_BOTTOM = -64;
    private static final int LINE_TOP = 320;

    public static void render(WorldRenderContext context) {
        if (!ChunkBordersClient.showBorders) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Camera camera = context.camera();
        Vec3d camPos = camera.getPos();

        // Position du chunk du joueur
        int playerChunkX = (int) Math.floor(camPos.x) >> 4;
        int playerChunkZ = (int) Math.floor(camPos.z) >> 4;

        // Afficher les bordures dans un rayon de 8 chunks
        int radius = 8;

        Matrix4f viewMatrix = context.matrixStack().peek().getPositionMatrix();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.lineWidth(2.0f);

        Tessellator tessellator = Tessellator.getInstance();

        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                drawChunkBorder(tessellator, viewMatrix, camPos, cx, cz);
            }
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void drawChunkBorder(Tessellator tessellator, Matrix4f viewMatrix,
                                         Vec3d camPos, int chunkX, int chunkZ) {
        // Coordonnées monde du coin du chunk
        double x = chunkX * 16.0 - camPos.x;
        double z = chunkZ * 16.0 - camPos.z;
        double x2 = x + 16.0;
        double z2 = z + 16.0;

        float bottom = (float)(LINE_BOTTOM - camPos.y);
        float top    = (float)(LINE_TOP    - camPos.y);

        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        // 4 arêtes verticales du chunk
        drawVerticalLine(buffer, viewMatrix, (float)x,  bottom, top, (float)z,  R, G, B, A);
        drawVerticalLine(buffer, viewMatrix, (float)x2, bottom, top, (float)z,  R, G, B, A);
        drawVerticalLine(buffer, viewMatrix, (float)x,  bottom, top, (float)z2, R, G, B, A);
        drawVerticalLine(buffer, viewMatrix, (float)x2, bottom, top, (float)z2, R, G, B, A);

        // Lignes horizontales au sol (y=64) et en hauteur (y=128)
        int[] yLevels = {(int)(64 - camPos.y), (int)(128 - camPos.y)};
        for (int y : yLevels) {
            buffer.vertex(viewMatrix, (float)x,  y, (float)z ).color(R, G, B, A * 0.4f);
            buffer.vertex(viewMatrix, (float)x2, y, (float)z ).color(R, G, B, A * 0.4f);
            buffer.vertex(viewMatrix, (float)x2, y, (float)z ).color(R, G, B, A * 0.4f);
            buffer.vertex(viewMatrix, (float)x2, y, (float)z2).color(R, G, B, A * 0.4f);
            buffer.vertex(viewMatrix, (float)x2, y, (float)z2).color(R, G, B, A * 0.4f);
            buffer.vertex(viewMatrix, (float)x,  y, (float)z2).color(R, G, B, A * 0.4f);
            buffer.vertex(viewMatrix, (float)x,  y, (float)z2).color(R, G, B, A * 0.4f);
            buffer.vertex(viewMatrix, (float)x,  y, (float)z ).color(R, G, B, A * 0.4f);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void drawVerticalLine(BufferBuilder buffer, Matrix4f matrix,
                                          float x, float bottom, float top, float z,
                                          float r, float g, float b, float a) {
        buffer.vertex(matrix, x, bottom, z).color(r, g, b, a);
        buffer.vertex(matrix, x, top,    z).color(r, g, b, a);
    }
}
