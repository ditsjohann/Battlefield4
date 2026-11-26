package com.ditsjohann.warfront.client.render;

import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.entity.TankEntity;
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

/**
 * MBT-9 Juggernaut. Built from boxes: hull with glacis and side skirts, animated tracks and road
 * wheels, a rotating turret with reactive armour, team stripe, commander hatch and antenna, and
 * an elevating gun with muzzle brake.
 */
public class TankRenderer extends EntityRenderer<TankEntity> {
    public TankRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 2.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(TankEntity e) {
        return PARTS;
    }

    @Override
    public void render(TankEntity t, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(PARTS));
        int ov = OverlayTexture.NO_OVERLAY;
        Faction f = t.getFaction();
        int camo = f == Faction.LEGION ? TANK_TAN : TANK_GREEN;
        int stripe = f == Faction.LEGION ? TEAM_RED : f == Faction.COALITION ? TEAM_BLUE : WHITE;
        int tint = t.disabled() ? 0xFF8A8A8A : 0xFFFFFFFF;
        float hullYaw = Mth.rotLerp(pt, t.yRotO, t.getYRot());
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-hullYaw));
        PoseStack.Pose pose = ps.last();
        // ---- tracks
        float phase = t.trackPhase * 2.2f;
        for (int side = -1; side <= 1; side += 2) {
            float x0 = side * 1.25f, x1 = side * 1.72f;
            float lo = Math.min(x0, x1), hi = Math.max(x0, x1);
            box(pose, vc, lo, 0.0f, -2.75f, hi, 0.12f, 2.75f, RUBBER, tint, light, ov); // ground run
            box(pose, vc, lo, 0.86f, -2.6f, hi, 0.98f, 2.6f, RUBBER, tint, light, ov); // top run
            box(pose, vc, lo, 0.12f, 2.6f, hi, 0.86f, 2.9f, RUBBER, tint, light, ov); // front idler wrap
            box(pose, vc, lo, 0.12f, -2.85f, hi, 0.86f, -2.6f, RUBBER, tint, light, ov); // rear sprocket wrap
            // moving track grousers
            for (int i = 0; i < 14; i++) {
                float z = -2.7f + ((i * 0.4f + phase) % 5.6f + 5.6f) % 5.6f;
                if (z > 2.7f) continue;
                box(pose, vc, lo - 0.02f, -0.02f, z, hi + 0.02f, 0.03f, z + 0.12f, STEEL, tint, light, ov);
                box(pose, vc, lo - 0.02f, 0.97f, -z, hi + 0.02f, 1.01f, -z + 0.12f, STEEL, tint, light, ov);
            }
            // road wheels with spinning hubs
            for (int i = 0; i < 6; i++) {
                float z = -2.2f + i * 0.88f;
                float cxw = side * 1.49f;
                box(pose, vc, cxw - 0.25f, 0.12f, z - 0.32f, cxw + 0.25f, 0.76f, z + 0.32f, GUNMETAL, tint, light, ov);
                ps.pushPose();
                ps.translate(side * 1.75f, 0.44f, z);
                ps.mulPose(Axis.XP.rotationDegrees(-phase * 60f));
                box(ps.last(), vc, -0.03f, -0.12f, -0.12f, 0.03f, 0.12f, 0.12f, BRIGHT_STEEL, tint, light, ov);
                ps.popPose();
            }
            // side skirt
            box(pose, vc, side > 0 ? 1.7f : -1.82f, 0.55f, -2.6f, side > 0 ? 1.82f : -1.7f, 1.12f, 2.5f, camo, tint, light, ov);
            box(pose, vc, side > 0 ? 1.18f : -1.84f, 1.0f, -2.8f, side > 0 ? 1.84f : -1.18f, 1.08f, 2.85f, camo, tint, light, ov); // fender
        }
        // ---- hull
        box(pose, vc, -1.22f, 0.35f, -2.6f, 1.22f, 1.15f, 2.6f, camo, tint, light, ov);
        box(pose, vc, -1.25f, 1.15f, -2.5f, 1.25f, 1.35f, 1.9f, camo, tint, light, ov); // deck
        ps.pushPose(); // glacis plate
        ps.translate(0, 1.15f, 1.9f);
        ps.mulPose(Axis.XP.rotationDegrees(-62));
        box(ps.last(), vc, -1.22f, 0, -0.02f, 1.22f, 0.8f, 0.18f, camo, tint, light, ov);
        ps.popPose();
        box(pose, vc, -1.2f, 0.4f, 2.6f, 1.2f, 0.95f, 2.75f, GUNMETAL, tint, light, ov); // lower front
        box(pose, vc, -0.9f, 1.15f, -2.75f, -0.4f, 1.45f, -2.5f, GUNMETAL, tint, light, ov); // exhausts
        box(pose, vc, 0.4f, 1.15f, -2.75f, 0.9f, 1.45f, -2.5f, GUNMETAL, tint, light, ov);
        box(pose, vc, -1.1f, 1.35f, -2.4f, 1.1f, 1.42f, -1.6f, STEEL, tint, light, ov); // engine grille
        box(pose, vc, -0.35f, 1.35f, 1.4f, 0.35f, 1.48f, 1.85f, GUNMETAL, tint, light, ov); // driver hatch
        box(pose, vc, 0.9f, 1.35f, 1.5f, 1.1f, 1.55f, 1.7f, LENS, 0xFFFFFFFF, light, ov); // headlight
        box(pose, vc, -1.1f, 1.35f, 1.5f, -0.9f, 1.55f, 1.7f, LENS, 0xFFFFFFFF, light, ov);
        // ---- turret
        float turret = Mth.wrapDegrees(t.turretYaw() - hullYaw);
        ps.pushPose();
        ps.translate(0, 1.35f, -0.25f);
        ps.mulPose(Axis.YP.rotationDegrees(-turret));
        PoseStack.Pose tp = ps.last();
        box(tp, vc, -1.05f, 0, -1.35f, 1.05f, 0.72f, 1.05f, camo, tint, light, ov);
        box(tp, vc, -0.85f, 0.72f, -1.2f, 0.85f, 0.85f, 0.85f, camo, tint, light, ov); // roof
        box(tp, vc, -0.95f, 0.05f, -1.9f, 0.95f, 0.65f, -1.35f, camo, tint, light, ov); // bustle
        // angled cheeks
        for (int s = -1; s <= 1; s += 2) {
            ps.pushPose();
            ps.translate(s * 0.75f, 0, 1.05f);
            ps.mulPose(Axis.YP.rotationDegrees(s * 28));
            box(ps.last(), vc, -0.32f, 0.02f, -0.05f, 0.32f, 0.68f, 0.45f, camo, tint, light, ov);
            ps.popPose();
            // reactive armour bricks + team stripe
            for (int i = 0; i < 3; i++) {
                float z = -1.1f + i * 0.62f;
                box(tp, vc, s > 0 ? 1.05f : -1.17f, 0.18f, z, s > 0 ? 1.17f : -1.05f, 0.55f, z + 0.52f, camo, 0xFFD8D8D8, light, ov);
            }
            box(tp, vc, s > 0 ? 1.04f : -1.06f, 0.6f, -1.3f, s > 0 ? 1.06f : -1.04f, 0.7f, 0.6f, stripe, 0xFFFFFFFF, light, ov);
            // smoke launchers
            for (int i = 0; i < 3; i++) {
                box(tp, vc, s * 0.95f - 0.06f, 0.45f + i * 0.1f, 0.6f, s * 0.95f + 0.06f, 0.53f + i * 0.1f, 0.85f, GUNMETAL, tint, light, ov);
            }
        }
        box(tp, vc, 0.25f, 0.85f, -0.6f, 0.75f, 1.05f, -0.1f, GUNMETAL, tint, light, ov); // commander cupola
        box(tp, vc, 0.3f, 1.05f, -0.55f, 0.7f, 1.1f, -0.15f, STEEL, tint, light, ov);
        box(tp, vc, -0.7f, 0.85f, -0.5f, -0.3f, 1.25f, -0.2f, STEEL, tint, light, ov); // sight box
        box(tp, vc, -0.68f, 0.95f, -0.19f, -0.32f, 1.15f, -0.17f, LENS, 0xFFFFFFFF, light, ov);
        box(tp, vc, -0.9f, 0.85f, -1.75f, -0.86f, 3.2f, -1.71f, POLYMER, tint, light, ov); // antenna
        box(tp, vc, -0.95f, 3.1f, -1.8f, -0.81f, 3.2f, -1.66f, stripe, 0xFFFFFFFF, light, ov); // pennant tip
        // ---- gun
        ps.pushPose();
        ps.translate(0, 0.42f, 1.05f);
        ps.mulPose(Axis.XP.rotationDegrees(t.barrelPitch()));
        PoseStack.Pose gp = ps.last();
        box(gp, vc, -0.38f, -0.24f, 0, 0.38f, 0.24f, 0.45f, GUNMETAL, tint, light, ov); // mantlet
        box(gp, vc, -0.1f, -0.1f, 0.45f, 0.1f, 0.1f, 3.4f, GUNMETAL, tint, light, ov); // barrel
        box(gp, vc, -0.14f, -0.14f, 1.6f, 0.14f, 0.14f, 2.3f, STEEL, tint, light, ov); // fume extractor
        box(gp, vc, -0.16f, -0.13f, 3.35f, 0.16f, 0.13f, 3.65f, STEEL, tint, light, ov); // muzzle brake
        box(gp, vc, 0.26f, -0.05f, 0.4f, 0.34f, 0.03f, 1.1f, GUNMETAL, tint, light, ov); // coax
        ps.popPose();
        ps.popPose();
        ps.popPose();
        super.render(t, yaw, pt, ps, buf, light);
    }
}
