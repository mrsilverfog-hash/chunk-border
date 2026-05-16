package net.tropimon.chunkborders;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

public class ChunkBordersRenderer {

    private static final float R = 1.0f;
    private static final float G = 1.0f;
    private static final float B = 0.0f;
    private static final float A = 0.9f;

    private static final int LINE_BOTTOM = -64;
    private static final int LINE_TOP = 320;
    private static final float CROSS_SIZE = 1.0f;

    public static void render(WorldRenderContext context) {
        if (!ChunkBordersClient.showBorders) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Camera camera = context.camera();
        Vec3d camPos = camera.getPos();
        World world = client.world;

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

        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius + 1; cx++) {
            for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius + 1; cz++) {
                double worldX = cx * 16.0;
                double worldZ = cz * 16.0;
                int groundY = getGroundY(world, (int)worldX, (int)worldZ, (int)camPos.y);
                drawCornerCross(tessellator, viewMatrix, camPos, worldX, worldZ, groundY);
            }
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    // Cherche la hauteur du sol au plus proche du joueur en Y
    private static int getGroundY(World world, int x, int z, int referenceY) {
        // Chercher vers le bas depuis la position du joueur
        for (int y = referenceY + 5; y >= LINE_BOTTOM; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (!state.isAir() && state.isSolidBlock(world, pos)) {
                return y + 1; // +1 pour être au-dessus du bloc
            }
        }
        return referenceY; // fallback
    }

    private static void drawCornerCross(Tessellator tessellator, Matrix4f viewMatrix,
                                         Vec3d camPos, double worldX, double worldZ, int groundY) {
        float x = (float)(worldX - camPos.x);
        float z = (float)(worldZ - camPos.z);
        float bottom = (float)(LINE_BOTTOM - camPos.y);
        float top    = (float)(LINE_TOP    - camPos.y);
        float ground = (float)(groundY - camPos.y);

        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        // Ligne verticale complète
        buffer.vertex(viewMatrix, x, bottom, z).color(R, G, B, A);
        buffer.vertex(viewMatrix, x, top,    z).color(R, G, B, A);

        // Croix au sol (dynamique selon le terrain)
        buffer.vertex(viewMatrix, x, ground, z - CROSS_SIZE).color(R, G, B, A);
        buffer.vertex(viewMatrix, x, ground, z + CROSS_SIZE).color(R, G, B, A);
        buffer.vertex(viewMatrix, x - CROSS_SIZE, ground, z).color(R, G, B, A);
        buffer.vertex(viewMatrix, x + CROSS_SIZE, ground, z).color(R, G, B, A);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}
