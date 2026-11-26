package com.ditsjohann.warfront.client.render;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.block.CapturePointBlockEntity;
import com.ditsjohann.warfront.block.HqBeaconBlockEntity;
import com.ditsjohann.warfront.client.ClientState;
import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.match.MatchSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import static com.ditsjohann.warfront.client.render.BoxDraw.*;

/** Flag poles: the cloth is raised by capture control and coloured friendly/enemy relative to the viewer. */
public final class FlagRenderers {
    private static final ResourceLocation CLOTH = Warfront.id("textures/entity/flag_cloth.png");

    static int relativeColor(int owner) {
        if (owner < 0) return 0xFFE8E8E8;
        Faction mine = ClientState.myFaction();
        if (mine == null) return owner == 0 ? 0xFF2F6FD8 : 0xFFD83A2E;
        return owner == mine.ordinal() ? 0xFF2F6FD8 : 0xFFD83A2E;
    }

    static void pole(PoseStack ps, MultiBufferSource buf, int light, float height) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(PARTS));
        int ov = OverlayTexture.NO_OVERLAY;
        PoseStack.Pose p = ps.last();
        box(p, vc, 0.44f, 0.18f, 0.44f, 0.56f, height, 0.56f, BRIGHT_STEEL, 0xFFFFFFFF, light, ov);
        box(p, vc, 0.41f, height, 0.41f, 0.59f, height + 0.12f, 0.59f, STEEL, 0xFFFFFFFF, light, ov);
    }

    /** A waving cloth made of vertical strips. */
    static void cloth(PoseStack ps, MultiBufferSource buf, int light, float y, int color, float time) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(CLOTH));
        PoseStack.Pose p = ps.last();
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        int strips = 10;
        float len = 1.8f, h = 1.1f;
        for (int i = 0; i < strips; i++) {
            float t0 = (float) i / strips, t1 = (float) (i + 1) / strips;
            float x0 = 0.56f + t0 * len, x1 = 0.56f + t1 * len;
            float z0 = 0.5f + Mth.sin(time * 0.25f - t0 * 5f) * 0.18f * t0;
            float z1 = 0.5f + Mth.sin(time * 0.25f - t1 * 5f) * 0.18f * t1;
            float sag0 = -t0 * t0 * 0.12f, sag1 = -t1 * t1 * 0.12f;
            float u0 = t0, u1 = t1;
            for (int side = 0; side < 2; side++) {
                float nz = side == 0 ? 1 : -1;
                if (side == 0) {
                    v(vc, p, x0, y + sag0, z0, u0, 1, r, g, b, light, nz);
                    v(vc, p, x1, y + sag1, z1, u1, 1, r, g, b, light, nz);
                    v(vc, p, x1, y + h + sag1, z1, u1, 0, r, g, b, light, nz);
                    v(vc, p, x0, y + h + sag0, z0, u0, 0, r, g, b, light, nz);
                } else {
                    v(vc, p, x0, y + h + sag0, z0, u0, 0, r, g, b, light, nz);
                    v(vc, p, x1, y + h + sag1, z1, u1, 0, r, g, b, light, nz);
                    v(vc, p, x1, y + sag1, z1, u1, 1, r, g, b, light, nz);
                    v(vc, p, x0, y + sag0, z0, u0, 1, r, g, b, light, nz);
                }
            }
        }
    }

    private static void v(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float vv, int r, int g, int b, int light, float nz) {
        vc.addVertex(p, x, y, z).setColor(r, g, b, 255).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, nz);
    }

    public static class CapturePoint implements BlockEntityRenderer<CapturePointBlockEntity> {
        public CapturePoint(BlockEntityRendererProvider.Context ctx) {}

        @Override
        public void render(CapturePointBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            float control = 0;
            int owner = -1;
            for (MatchSnapshot.Flag f : ClientState.match.flags()) {
                if (f.pos().equals(be.getBlockPos())) {
                    control = f.control();
                    owner = f.owner();
                }
            }
            float top = 7.0f;
            pole(ps, buf, light, top);
            // flag hangs at the top when held, sinks toward the base as it is neutralised
            float raise = Math.abs(control);
            int color = owner >= 0 ? relativeColor(owner) : control == 0 ? 0xFFE8E8E8 : relativeColor(control > 0 ? 0 : 1);
            float y = 0.6f + raise * (top - 1.8f);
            float time = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + pt;
            cloth(ps, buf, light, y, color, time);
        }

        @Override
        public boolean shouldRenderOffScreen(CapturePointBlockEntity be) {
            return true;
        }

        @Override
        public int getViewDistance() {
            return 256;
        }
    }

    public static class Hq implements BlockEntityRenderer<HqBeaconBlockEntity> {
        public Hq(BlockEntityRendererProvider.Context ctx) {}

        @Override
        public void render(HqBeaconBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            pole(ps, buf, light, 8.5f);
            float time = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + pt;
            cloth(ps, buf, light, 7.2f, relativeColor(be.faction().ordinal()), time);
        }

        @Override
        public boolean shouldRenderOffScreen(HqBeaconBlockEntity be) {
            return true;
        }

        @Override
        public int getViewDistance() {
            return 256;
        }
    }

    private FlagRenderers() {}
}
