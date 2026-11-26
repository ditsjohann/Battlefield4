package com.ditsjohann.warfront.client.render;

import com.ditsjohann.warfront.Warfront;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;

/**
 * Immediate-mode textured boxes on the 64x64 "parts" swatch atlas. Used for procedurally built
 * models (tank, flags, gadgets) so they need no hand-made UV layouts.
 */
public final class BoxDraw {
    public static final ResourceLocation PARTS = Warfront.id("textures/entity/parts.png");
    public static final int GUNMETAL = 0, STEEL = 1, POLYMER = 2, TAN = 3, WOOD = 4, OLIVE = 5, BRIGHT_STEEL = 6, LENS = 7,
            RED = 8, TANK_GREEN = 9, TANK_TAN = 10, RUBBER = 11, ORANGE = 12, WHITE = 13, TEAM_BLUE = 14, TEAM_RED = 15;

    public static void box(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                           int swatch, int tint, int light, int overlay) {
        float u0 = ((swatch % 4) * 16 + 2) / 64f, v0 = ((swatch / 4) * 16 + 2) / 64f;
        float u1 = u0 + 12 / 64f, v1 = v0 + 12 / 64f;
        int r = (tint >> 16) & 255, g = (tint >> 8) & 255, b = tint & 255, a = (tint >>> 24) == 0 ? 255 : tint >>> 24;
        // -Y
        quad(pose, vc, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, u0, v0, u1, v1, r, g, b, a, light, overlay);
        // +Y
        quad(pose, vc, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, u0, v0, u1, v1, r, g, b, a, light, overlay);
        // -Z
        quad(pose, vc, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, u0, v0, u1, v1, r, g, b, a, light, overlay);
        // +Z
        quad(pose, vc, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, u0, v0, u1, v1, r, g, b, a, light, overlay);
        // -X
        quad(pose, vc, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, u0, v0, u1, v1, r, g, b, a, light, overlay);
        // +X
        quad(pose, vc, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, u0, v0, u1, v1, r, g, b, a, light, overlay);
    }

    /** Box centred on (cx, cz) horizontally, spanning y0..y1. */
    public static void centered(PoseStack.Pose pose, VertexConsumer vc, float cx, float y0, float cz, float w, float h, float d,
                                int swatch, int tint, int light, int overlay) {
        box(pose, vc, cx - w / 2, y0, cz - d / 2, cx + w / 2, y0 + h, cz + d / 2, swatch, tint, light, overlay);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc,
                             float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz, float u0, float v0, float u1, float v1, int r, int g, int b, int a, int light, int overlay) {
        vc.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x3, y3, z3).setColor(r, g, b, a).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    private BoxDraw() {}
}
