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
    private static final float THICK_MAIN  = 0.06f; // épaisseur des verticales
    private static final float THICK_RING  = 0.03f; // épaisseur des traits horizontaux
    private static final int   RING_STEP   = 8;     // un anneau tous les N blocs
    private static final int   RING_RANGE  = 16;    // hauteur de grille +/- autour du joueur
    private static final boolean SEE_THROUGH = false; // true = visible à travers les blocs

    private static final int   LINE_BOTTOM = -64;
    private static final int   LINE_TOP    = 320;
    private static final float CROSS_SIZE  = 1.0f;
    private static final float GROUND_OFFSET = 0.05f;

    private static final float[] COL_MAIN   = {1.0f, 1.0f, 0.0f, 0.90f}; // jaune vif
    private static final float[] COL_RING   = {1.0f, 0.85f, 0.2f, 0.40f}; // jaune pâle
    private static final float[] COL_GROUND = {1.0f, 0.35f, 0.1f, 0.95f}; // orange
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

        int pcx = (int) Math.floor(camPos.x) >> 4;
        int pcz = (int) Math.floor(camPos.z) >> 4;
        int feetY = (int) Math.floor(client.player.getY());

        long tick = world.getTime();
        if (tick - lastGroundScan >= 20) {
            lastGroundScan = tick;
            int i = 0;
            for (int cx = pcx; cx <= pcx + 1; cx++) {
                for (int cz = pcz; cz <= pcz + 1; cz++) {
                    cachedGroundY[i++] = getGroundBelow(world, cx * 16, feetY, cz * 16);
                }
            }
        }

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

        double x0 = pcx * 16.0, x1 = x0 + 16.0;
        double z0 = pcz * 16.0, z1 = z0 + 16.0;

        // ---- 4 verticales aux coins ----
        seg(buffer, m, x0, LINE_BOTTOM, z0, x0, LINE_TOP, z0, COL_MAIN, THICK_MAIN);
        seg(buffer, m, x1, LINE_BOTTOM, z0, x1, LINE_TOP, z0, COL_MAIN, THICK_MAIN);
        seg(buffer, m, x0, LINE_BOTTOM, z1, x0, LINE_TOP, z1, COL_MAIN, THICK_MAIN);
        seg(buffer, m, x1, LINE_BOTTOM, z1, x1, LINE_TOP, z1, COL_MAIN, THICK_MAIN);

        // ---- Anneaux horizontaux reliant les verticales ----
        int rMin = Math.max(LINE_BOTTOM, feetY - RING_RANGE);
        int rMax = Math.min(LINE_TOP,    feetY + RING_RANGE);
        for (int y = alignUp(rMin, RING_STEP); y <= rMax; y += RING_STEP) {
            ring(buffer, m, x0, x1, z0, z1, y, COL_RING, THICK_RING);
        }

        // ---- Anneau au sol + croix aux coins ----
        double gy0 = cachedGroundY[0] + GROUND_OFFSET;
        ring(buffer, m, x0, x1, z0, z1, gy0, COL_GROUND, THICK_MAIN);

        int i = 0;
        for (int cx = pcx; cx <= pcx + 1; cx++) {
            for (int cz = pcz; cz <= pcz + 1; cz++) {
                double gx = cx * 16.0, gz = cz * 16.0;
                double gy = cachedGroundY[i++] + GROUND_OFFSET;
                seg(buffer, m, gx, gy, gz - CROSS_SIZE, gx, gy, gz + CROSS_SIZE, COL_GROUND, THICK_MAIN);
                seg(buffer, m, gx - CROSS_SIZE, gy, gz, gx + CROSS_SIZE, gy, gz, COL_GROUND, THICK_MAIN);
            }
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        if (SEE_THROUGH) RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void ring(BufferBuilder bb, Matrix4f m,
                             double x0, double x1, double z0, double z1,
                             double y, float[] c, float t) {
        seg(bb, m, x0, y, z0, x1, y, z0, c, t);
        seg(bb, m, x0, y, z1, x1, y, z1, c, t);
        seg(bb, m, x0, y, z0, x0, y, z1, c, t);
        seg(bb, m, x1, y, z0, x1, y, z1, c, t);
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

    private static void seg(BufferBuilder bb, Matrix4f m,
                            double ax, double ay, double az,
                            double bx, double by, double bz,
                            float[] c, float t) {
        double h = t / 2.0;
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
