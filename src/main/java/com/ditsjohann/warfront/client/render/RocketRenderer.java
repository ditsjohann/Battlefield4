package com.ditsjohann.warfront.client.render;

import com.ditsjohann.warfront.entity.RocketEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import static com.ditsjohann.warfront.client.render.BoxDraw.*;

public class RocketRenderer extends EntityRenderer<RocketEntity> {
    public RocketRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(RocketEntity e) {
        return PARTS;
    }

    @Override
    public void render(RocketEntity r, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(Mth.lerp(pt, r.yRotO, r.getYRot())));
        ps.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(pt, r.xRotO, r.getXRot())));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(PARTS));
        int ov = OverlayTexture.NO_OVERLAY;
        PoseStack.Pose p = ps.last();
        if (r.kind() == RocketEntity.Kind.ROCKET) {
            box(p, vc, -0.07f, -0.07f, -0.35f, 0.07f, 0.07f, 0.2f, OLIVE, 0xFFFFFFFF, light, ov);
            box(p, vc, -0.05f, -0.05f, 0.2f, 0.05f, 0.05f, 0.42f, GUNMETAL, 0xFFFFFFFF, light, ov);
            box(p, vc, -0.12f, -0.01f, -0.35f, 0.12f, 0.01f, -0.25f, GUNMETAL, 0xFFFFFFFF, light, ov);
            box(p, vc, -0.01f, -0.12f, -0.35f, 0.01f, 0.12f, -0.25f, GUNMETAL, 0xFFFFFFFF, light, ov);
        } else {
            box(p, vc, -0.06f, -0.06f, -0.25f, 0.06f, 0.06f, 0.25f, BRIGHT_STEEL, 0xFFFFE0A0, 0xF000F0, ov);
        }
        ps.popPose();
        super.render(r, yaw, pt, ps, buf, light);
    }
}
