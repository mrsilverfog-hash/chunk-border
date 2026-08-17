package net.tropimon.chunkborders;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

public class ChunkBordersRenderer {

    // ====================== RÉGLAGES ======================
    private static final float THICKNESS   = 0.08f; // épaisseur des traits (en blocs)
    private static final int   RING_STEP   = 4;     // espacement vertical des traits horizontaux
    private static final int   RING_RANGE  = 32;    // hauteur de grille au-dessus/en dessous du joueur
    private static final boolean SEE_THROUGH = true; // true = visible à travers les blocs

    private static final int   LINE_BOTTOM = -64;
    private static final int   LINE_TOP    = 320;
    private static final float CROSS_SIZE  = 1.0f;
    private static final float GROUND_OFFSET = 0.05f;

    // couleurs RGBA
    private static final float[] COL_VERTICAL = {1.0f, 1.0f, 0.0f, 0.90f}; // jaune
    private static final float[] COL_RING     = {0.2f, 1.0f, 0.9f, 0.45f}; // cyan
    private static final float[] COL_GROUND   = {1.0f, 0.4f, 0.1f, 0.95f}; // orange
    // ======================================================

    private static final int[] cachedGroundY = new int[4];
    private static long lastGroundScan = -1;

    private static double camX, camY, camZ;

    public static void render(WorldRenderContext context) {
        if (!ChunkBordersClient.showBorders) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Camera camera = context.camera();
        Vec3d camPos = camera.getPos();
        camX = camPos.x;
        camY = camPos.y;
        camZ = camPos.z;
        World world = client.world;

        int playerChunkX = (int) Math.floor(camPos.x) >> 4;
        int playerChunkZ = (int) Math.floor(camPos.z) >> 4;
        int playerFeetY  = (int) Math.floor(client.player.getY());

        // ---- Cache du sol (rescan toutes les secondes) ----
        long tick = world.getTime();
        if (tick - lastGroundScan >= 20) {
            lastGroundScan = tick;
            int i = 0;
            for (int cx = playerChunkX; cx <= playerChunkX + 1; cx++) {
                for (int cz = playerChunkZ; cz <= playerChunkZ + 1; cz++) {
                    cachedGroundY[i++] = getGroundBelow(world, cx * 16, playerFeetY, cz * 16);
                }
            }
        }

        // MatrixStack neuf : compatibilité Iris
        MatrixStack matrices = new MatrixStack();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
        Matrix4f m = matrices.peek().getPositionMatrix();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        if (SEE_THROUGH) RenderSystem.disableDepthTest();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        double x0 = playerChunkX * 16.0;
        double x1 = x0 + 16.0;
        double z0 = playerChunkZ * 16.0;
        double z1 = z0 + 16.0;

        // ---- Les 4 lignes verticales aux coins du chunk ----
        seg(buffer, m, x0, LINE_BOTTOM, z0, x0, LINE_TOP, z0, COL_VERTICAL);
        seg(buffer, m, x1, LINE_BOTTOM, z0, x1, LINE_TOP, z0, COL_VERTICAL);
        seg(buffer, m, x0, LINE_BOTTOM, z1, x0, LINE_TOP, z1, COL_VERTICAL);
        seg(buffer, m, x1, LINE_BOTTOM, z1, x1, LINE_TOP, z1, COL_VERTICAL);

        // ---- Traits horizontaux reliant les verticales ----
        int ringMin = Math.max(LINE_BOTTOM, playerFeetY - RING_RANGE);
        int ringMax = Math.min(LINE_TOP,    playerFeetY + RING_RANGE);
        for (int y = alignUp(ringMin, RING_STEP); y <= ringMax; y += RING_STEP) {
            ring(buffer, m, x0, x1, z0, z1, y, COL_RING);
        }

        // ---- Anneau au niveau du sol + croix aux coins ----
        int groundRingY = cachedGroundY[0];
        ring(buffer, m, x0, x1, z0, z1, groundRingY + GROUND_OFFSET, COL_GROUND);

        int i = 0;
        for (int cx = playerChunkX; cx <= playerChunkX + 1; cx++) {
            for (int cz = playerChunkZ; cz <= playerChunkZ + 1; cz++) {
                double gx = cx * 16.0;
                double gz = cz * 16.0;
                double gy = cachedGroundY[i++] + GROUND_OFFSET;
                seg(buffer, m, gx, gy, gz - CROSS_SIZE, gx, gy, gz + CROSS_SIZE, COL_GROUND);
                seg(buffer, m, gx - CROSS_SIZE, gy, gz, gx + CROSS_SIZE, gy, gz, COL_GROUND);
            }
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        // ---- Restauration ----
        if (SEE_THROUGH) RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Carré horizontal reliant les 4 verticales à une hauteur donnée. */
    private static void ring(BufferBuilder bb, Matrix4f m,
                             double x0, double x1, double z0, double z1,
                             double y, float[] c) {
        seg(bb, m, x0, y, z0, x1, y, z0, c);
        seg(bb, m, x0, y, z1, x1, y, z1, c);
        seg(bb, m, x0, y, z0, x0, y, z1, c);
        seg(bb, m, x1, y, z0, x1, y, z1, c);
    }

    private static int getGroundBelow(World world, int x, int startY, int z) {
        for (int y = startY + 2; y >= LINE_BOTTOM; y--) {
            BlockState state = world.getBlockState(new BlockPos(x, y, z));
            if (!state.isAir()) return y + 1;
        }
        return startY;
    }

    private static int alignUp(int value, int step) {
        return (int) (Math.ceil(value / (double) step) * step);
    }

    /** Segment axis-aligned tracé sous forme de boîte fine. */
    private static void seg(BufferBuilder bb, Matrix4f m,
                            double ax, double ay, double az,
                            double bx, double by, double bz,
                            float[] c) {
        double h = THICKNESS / 2.0;
        float lx = (float) (Math.min(ax, bx) - h - camX);
        float ly = (float) (Math.min(ay, by) - h - camY);
        float lz = (float) (Math.min(az, bz) - h - camZ);
        float hx = (float) (Math.max(ax, bx) + h - camX);
        float hy = (float) (Math.max(ay, by) + h - camY);
        float hz = (float) (Math.max(az, bz) + h - camZ);
        box(bb, m, lx, ly, lz, hx, hy, hz, c);
    }

    private static void box(BufferBuilder bb, Matrix4f m,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            float[] c) {
        float r = c[0], g = c[1], b = c[2], a = c[3];

        bb.vertex(m, x1, y1, z1).color(r, g, b, a);
        bb.vertex(m, x2, y1, z1).color(r, g, b, a);
        bb.vertex(m, x2, y1, z2).color(r, g, b, a);
        bb.vertex(m, x1, y1, z2).color(r, g, b, a);

        bb.vertex(m, x1, y2, z1).color(r, g, b, a);
        bb.vertex(m, x1, y2, z2).color(r, g, b, a);
        bb.vertex(m, x2, y2, z2).color(r, g, b, a);
        bb.vertex(m, x2, y2, z1).color(r, g, b, a);

        bb.vertex(m, x1, y1, z1).color(r, g, b, a);
        bb.vertex(m, x1, y2, z1).color(r, g, b, a);
        bb.vertex(m, x2, y2, z1).color(r, g, b, a);
        bb.vertex(m, x2, y1, z1).color(r, g, b, a);

        bb.vertex(m, x1, y1, z2).color(r, g, b, a);
        bb.vertex(m, x2, y1, z2).color(r, g, b, a);
        bb.vertex(m, x2, y2, z2).color(r, g, b, a);
        bb.vertex(m, x1, y2, z2).color(r, g, b, a);

        bb.vertex(m, x1, y1, z1).color(r, g, b, a);
        bb.vertex(m, x1, y1, z2).color(r, g, b, a);
        bb.vertex(m, x1, y2, z2).color(r, g, b, a);
        bb.vertex(m, x1, y2, z1).color(r, g, b, a);

        bb.vertex(m, x2, y1, z1).color(r, g, b, a);
        bb.vertex(m, x2, y2, z1).color(r, g, b, a);
        bb.vertex(m, x2, y2, z2).color(r, g, b, a);
        bb.vertex(m, x2, y1, z2).color(r, g, b, a);
    }
}
