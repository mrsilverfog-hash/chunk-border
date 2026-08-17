package net.tropimon.chunkborders;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class ChunkBordersRenderer {

    // ====================== RÉGLAGES ======================
    private static final float THICKNESS     = 0.07f; // épaisseur des traits (en blocs)
    private static final int   CHUNK_RADIUS  = 1;     // 1 = grille 3x3 autour du joueur
    private static final int   Y_RANGE       = 48;    // hauteur affichée au-dessus/en dessous
    private static final int   VERTICAL_STEP = 4;     // espacement des verticales sur un bord
    private static final int   RING_STEP     = 4;     // espacement vertical des traits horizontaux

    // couleurs RGBA (0.0 - 1.0)
    private static final float[] COLOR_CORNER = {1.00f, 0.92f, 0.15f, 0.95f}; // jaune : coins de chunk
    private static final float[] COLOR_EDGE   = {0.25f, 0.60f, 1.00f, 0.80f}; // bleu  : verticales de bord
    private static final float[] COLOR_RING   = {0.20f, 1.00f, 0.90f, 0.55f}; // cyan  : traits horizontaux
    // ======================================================

    private static double camX, camY, camZ;

    public static void render(WorldRenderContext context) {
        if (!ChunkBordersClient.showBorders) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        Camera camera = context.camera();
        Vec3d camPos = camera.getPos();
        camX = camPos.x;
        camY = camPos.y;
        camZ = camPos.z;

        // MatrixStack neuf : indispensable pour la compatibilité Iris
        MatrixStack matrices = new MatrixStack();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // ---- Zone couverte ----
        int pcx = MathHelper.floor(mc.player.getX()) >> 4;
        int pcz = MathHelper.floor(mc.player.getZ()) >> 4;

        int minX = (pcx - CHUNK_RADIUS) << 4;
        int maxX = (pcx + CHUNK_RADIUS + 1) << 4;
        int minZ = (pcz - CHUNK_RADIUS) << 4;
        int maxZ = (pcz + CHUNK_RADIUS + 1) << 4;

        int py = MathHelper.floor(mc.player.getY());
        int minY = Math.max(mc.world.getBottomY(), py - Y_RANGE);
        int maxY = Math.min(mc.world.getTopY(), py + Y_RANGE);

        // ---- Plans X (bords est/ouest des chunks) ----
        for (int bx = minX; bx <= maxX; bx += 16) {
            for (int z = minZ; z <= maxZ; z += VERTICAL_STEP) {
                boolean corner = Math.floorMod(z, 16) == 0;
                drawLine(buffer, matrix, bx, minY, z, bx, maxY, z,
                        corner ? COLOR_CORNER : COLOR_EDGE);
            }
            for (int y = alignUp(minY, RING_STEP); y <= maxY; y += RING_STEP) {
                drawLine(buffer, matrix, bx, y, minZ, bx, y, maxZ, COLOR_RING);
            }
            drawLine(buffer, matrix, bx, minY, minZ, bx, minY, maxZ, COLOR_RING);
            drawLine(buffer, matrix, bx, maxY, minZ, bx, maxY, maxZ, COLOR_RING);
        }

        // ---- Plans Z (bords nord/sud des chunks) ----
        for (int bz = minZ; bz <= maxZ; bz += 16) {
            for (int x = minX; x <= maxX; x += VERTICAL_STEP) {
                if (Math.floorMod(x, 16) == 0) continue; // déjà tracée par les plans X
                drawLine(buffer, matrix, x, minY, bz, x, maxY, bz, COLOR_EDGE);
            }
            for (int y = alignUp(minY, RING_STEP); y <= maxY; y += RING_STEP) {
                drawLine(buffer, matrix, minX, y, bz, maxX, y, bz, COLOR_RING);
            }
            drawLine(buffer, matrix, minX, minY, bz, maxX, minY, bz, COLOR_RING);
            drawLine(buffer, matrix, minX, maxY, bz, maxX, maxY, bz, COLOR_RING);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        // ---- Restauration de l'état ----
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static int alignUp(int value, int step) {
        return (int) (Math.ceil(value / (double) step) * step);
    }

    /** Trace un segment axis-aligned sous forme de boîte fine (épaisseur réelle en 3D). */
    private static void drawLine(BufferBuilder bb, Matrix4f m,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 float[] c) {
        double h = THICKNESS / 2.0;
        float ax = (float) (Math.min(x1, x2) - h - camX);
        float ay = (float) (Math.min(y1, y2) - h - camY);
        float az = (float) (Math.min(z1, z2) - h - camZ);
        float bx = (float) (Math.max(x1, x2) + h - camX);
        float by = (float) (Math.max(y1, y2) + h - camY);
        float bz = (float) (Math.max(z1, z2) + h - camZ);
        drawBox(bb, m, ax, ay, az, bx, by, bz, c);
    }

    private static void drawBox(BufferBuilder bb, Matrix4f m,
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
