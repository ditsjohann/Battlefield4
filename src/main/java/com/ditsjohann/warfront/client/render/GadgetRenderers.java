package com.ditsjohann.warfront.client.render;

import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.entity.C4Entity;
import com.ditsjohann.warfront.entity.SpawnBeaconEntity;
import com.ditsjohann.warfront.entity.SupplyBagEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import static com.ditsjohann.warfront.client.render.BoxDraw.*;

/** Box-built renderers for small placed gadgets. */
public final class GadgetRenderers {
    public static class C4 extends EntityRenderer<C4Entity> {
        public C4(EntityRendererProvider.Context ctx) { super(ctx); }

        @Override
        public ResourceLocation getTextureLocation(C4Entity e) { return PARTS; }

        @Override
        public void render(C4Entity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            ps.pushPose();
            Direction face = e.face();
            switch (face) {
                case DOWN -> ps.mulPose(Axis.XP.rotationDegrees(180));
                case NORTH -> ps.mulPose(Axis.XP.rotationDegrees(-90));
                case SOUTH -> ps.mulPose(Axis.XP.rotationDegrees(90));
                case WEST -> ps.mulPose(Axis.ZP.rotationDegrees(90));
                case EAST -> ps.mulPose(Axis.ZP.rotationDegrees(-90));
                default -> {}
            }
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(PARTS));
            int ov = OverlayTexture.NO_OVERLAY;
            PoseStack.Pose p = ps.last();
            box(p, vc, -0.18f, 0, -0.12f, 0.18f, 0.1f, 0.12f, TAN, 0xFFFFFFFF, light, ov);
            box(p, vc, -0.19f, 0.03f, -0.13f, 0.19f, 0.06f, 0.13f, POLYMER, 0xFFFFFFFF, light, ov);
            box(p, vc, 0.04f, 0.1f, -0.06f, 0.14f, 0.15f, 0.05f, POLYMER, 0xFFFFFFFF, light, ov);
            boolean blink = (e.tickCount / 10) % 2 == 0;
            box(p, vc, 0.07f, 0.15f, -0.02f, 0.1f, 0.17f, 0.01f, RED, 0xFFFFFFFF, blink ? 0xF000F0 : light, ov);
            ps.popPose();
            super.render(e, yaw, pt, ps, buf, light);
        }
    }

    public static class Bag extends EntityRenderer<SupplyBagEntity> {
        public Bag(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 0.3f;
        }

        @Override
        public ResourceLocation getTextureLocation(SupplyBagEntity e) { return PARTS; }

        @Override
        public void render(SupplyBagEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(e.getId() * 37 % 360));
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(PARTS));
            int ov = OverlayTexture.NO_OVERLAY;
            PoseStack.Pose p = ps.last();
            int body = e.isAmmo() ? OLIVE : WHITE;
            box(p, vc, -0.25f, 0, -0.17f, 0.25f, 0.28f, 0.17f, body, 0xFFFFFFFF, light, ov);
            box(p, vc, -0.12f, 0.28f, -0.03f, 0.12f, 0.34f, 0.03f, POLYMER, 0xFFFFFFFF, light, ov);
            if (e.isAmmo()) {
                for (int i = 0; i < 3; i++) box(p, vc, -0.15f + i * 0.11f, 0.05f, -0.175f, -0.09f + i * 0.11f, 0.22f, -0.17f, ORANGE, 0xFFFFFFFF, light, ov);
            } else {
                box(p, vc, -0.04f, 0.06f, -0.175f, 0.04f, 0.22f, -0.17f, RED, 0xFFFFFFFF, light, ov);
                box(p, vc, -0.1f, 0.1f, -0.175f, 0.1f, 0.18f, -0.17f, RED, 0xFFFFFFFF, light, ov);
            }
            ps.popPose();
            super.render(e, yaw, pt, ps, buf, light);
        }
    }

    public static class Beacon extends EntityRenderer<SpawnBeaconEntity> {
        public Beacon(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 0.3f;
        }

        @Override
        public ResourceLocation getTextureLocation(SpawnBeaconEntity e) { return PARTS; }

        @Override
        public void render(SpawnBeaconEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(PARTS));
            int ov = OverlayTexture.NO_OVERLAY;
            PoseStack.Pose p = ps.last();
            Faction f = e.getFaction();
            int team = f == Faction.LEGION ? TEAM_RED : TEAM_BLUE;
            box(p, vc, -0.16f, 0, -0.12f, 0.16f, 0.5f, 0.12f, GUNMETAL, 0xFFFFFFFF, light, ov);
            box(p, vc, -0.17f, 0.35f, -0.13f, 0.17f, 0.42f, 0.13f, team, 0xFFFFFFFF, light, ov);
            box(p, vc, -0.015f, 0.5f, -0.015f, 0.015f, 0.95f, 0.015f, POLYMER, 0xFFFFFFFF, light, ov);
            boolean blink = (e.tickCount / 10) % 2 == 0;
            box(p, vc, -0.035f, 0.95f, -0.035f, 0.035f, 1.0f, 0.035f, RED, 0xFFFFFFFF, blink ? 0xF000F0 : light, ov);
            super.render(e, yaw, pt, ps, buf, light);
        }
    }

    private GadgetRenderers() {}
}
