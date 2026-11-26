package com.ditsjohann.warfront.client.render;

import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.entity.BuggyEntity;
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

/** FAV-3 Jackal: open-top tub on four big wheels, roll cage, and a rear pivot-mounted HMG. */
public class BuggyRenderer extends EntityRenderer<BuggyEntity> {
    public BuggyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.3f;
    }

    @Override
    public ResourceLocation getTextureLocation(BuggyEntity e) {
        return PARTS;
    }

    @Override
    public void render(BuggyEntity b, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(PARTS));
        int ov = OverlayTexture.NO_OVERLAY;
        Faction f = b.getFaction();
        int camo = f == Faction.LEGION ? TANK_TAN : TANK_GREEN;
        int stripe = f == Faction.LEGION ? TEAM_RED : f == Faction.COALITION ? TEAM_BLUE : WHITE;
        int tint = b.disabled() ? 0xFF8A8A8A : 0xFFFFFFFF;
        float hullYaw = Mth.rotLerp(pt, b.yRotO, b.getYRot());
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-hullYaw));
        PoseStack.Pose p = ps.last();
        // ---- chassis tub
        box(p, vc, -0.88f, 0.45f, -1.3f, 0.88f, 0.82f, 1.25f, camo, tint, light, ov);
        box(p, vc, -0.8f, 0.45f, 1.25f, 0.8f, 0.7f, 1.6f, camo, tint, light, ov); // nose
        box(p, vc, -0.82f, 0.38f, 1.55f, 0.82f, 0.55f, 1.72f, GUNMETAL, tint, light, ov); // bull bar
        box(p, vc, -0.7f, 0.62f, 1.62f, -0.45f, 0.72f, 1.66f, LENS, 0xFFFFFFFF, light, ov); // headlights
        box(p, vc, 0.45f, 0.62f, 1.62f, 0.7f, 0.72f, 1.66f, LENS, 0xFFFFFFFF, light, ov);
        box(p, vc, -0.89f, 0.6f, -1.1f, -0.87f, 0.72f, 0.9f, stripe, 0xFFFFFFFF, light, ov); // team stripes
        box(p, vc, 0.87f, 0.6f, -1.1f, 0.89f, 0.72f, 0.9f, stripe, 0xFFFFFFFF, light, ov);
        // floor + seats
        box(p, vc, -0.8f, 0.8f, -1.25f, 0.8f, 0.84f, 1.2f, POLYMER, tint, light, ov);
        box(p, vc, -0.65f, 0.84f, -0.05f, -0.15f, 1.0f, 0.45f, POLYMER, tint, light, ov); // driver seat
        box(p, vc, -0.65f, 1.0f, -0.1f, -0.15f, 1.45f, -0.0f, POLYMER, tint, light, ov);
        box(p, vc, 0.15f, 0.84f, -0.05f, 0.65f, 1.0f, 0.45f, POLYMER, tint, light, ov); // passenger seat
        box(p, vc, 0.15f, 1.0f, -0.1f, 0.65f, 1.45f, -0.0f, POLYMER, tint, light, ov);
        box(p, vc, -0.5f, 0.95f, 0.85f, -0.3f, 1.15f, 0.95f, GUNMETAL, tint, light, ov); // steering wheel
        // ---- roll cage
        float cy = 1.95f;
        for (int sx = -1; sx <= 1; sx += 2) {
            float x0 = sx * 0.82f - 0.05f, x1 = sx * 0.82f + 0.05f;
            box(p, vc, x0, 0.82f, 0.75f, x1, cy, 0.85f, GUNMETAL, tint, light, ov);
            box(p, vc, x0, 0.82f, -1.25f, x1, cy, -1.15f, GUNMETAL, tint, light, ov);
            box(p, vc, x0, cy - 0.1f, -1.25f, x1, cy, 0.85f, GUNMETAL, tint, light, ov);
        }
        box(p, vc, -0.87f, cy - 0.1f, 0.75f, 0.87f, cy, 0.85f, GUNMETAL, tint, light, ov);
        box(p, vc, -0.87f, cy - 0.1f, -1.25f, 0.87f, cy, -1.15f, GUNMETAL, tint, light, ov);
        // spare wheel on the back
        box(p, vc, -0.35f, 0.5f, -1.42f, 0.35f, 1.2f, -1.3f, RUBBER, tint, light, ov);
        box(p, vc, -0.12f, 0.73f, -1.45f, 0.12f, 0.97f, -1.42f, BRIGHT_STEEL, tint, light, ov);
        // ---- wheels with spinning hubs
        float phase = b.trackPhase * 1.9f;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float wx = sx * 1.0f, wz = sz * 0.92f;
                box(p, vc, wx - 0.15f, 0.0f, wz - 0.42f, wx + 0.15f, 0.84f, wz + 0.42f, RUBBER, tint, light, ov);
                box(p, vc, wx - 0.12f, 0.1f, wz - 0.34f, wx + 0.12f, 0.74f, wz + 0.34f, RUBBER, 0xFF505050, light, ov);
                ps.pushPose();
                ps.translate(wx + sx * 0.16f, 0.42f, wz);
                ps.mulPose(Axis.XP.rotationDegrees(-phase * 57.3f));
                box(ps.last(), vc, -0.02f, -0.16f, -0.16f, 0.02f, 0.16f, 0.16f, BRIGHT_STEEL, tint, light, ov);
                box(ps.last(), vc, -0.03f, -0.04f, -0.3f, 0.03f, 0.04f, 0.3f, STEEL, tint, light, ov);
                ps.popPose();
                // fender
                box(p, vc, wx - 0.2f, 0.84f, wz - 0.5f, wx + 0.2f, 0.9f, wz + 0.5f, camo, tint, light, ov);
            }
        }
        // ---- rear gun mount
        box(p, vc, -0.1f, 0.84f, -0.65f, 0.1f, 1.45f, -0.45f, GUNMETAL, tint, light, ov);
        ps.pushPose();
        ps.translate(0, 1.55f, -0.55f);
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.wrapDegrees(b.turretYaw() - hullYaw)));
        ps.mulPose(Axis.XP.rotationDegrees(b.barrelPitch()));
        PoseStack.Pose g = ps.last();
        box(g, vc, -0.12f, -0.1f, -0.35f, 0.12f, 0.12f, 0.3f, GUNMETAL, tint, light, ov); // receiver
        box(g, vc, -0.04f, -0.03f, 0.3f, 0.04f, 0.05f, 1.25f, GUNMETAL, tint, light, ov); // barrel
        box(g, vc, -0.06f, -0.05f, 0.55f, 0.06f, 0.07f, 0.85f, STEEL, tint, light, ov); // cooling jacket
        box(g, vc, 0.12f, -0.18f, -0.15f, 0.32f, 0.08f, 0.15f, OLIVE, tint, light, ov); // ammo can
        box(g, vc, -0.4f, -0.05f, 0.25f, 0.4f, 0.4f, 0.29f, camo, tint, light, ov); // gun shield
        box(g, vc, -0.05f, -0.02f, -0.5f, 0.05f, 0.04f, -0.35f, POLYMER, tint, light, ov); // spade grips
        if (b.overheated()) box(g, vc, -0.05f, -0.01f, 0.86f, 0.05f, 0.07f, 1.2f, ORANGE, 0xFFFFFFFF, 0xF000F0, ov); // glowing barrel
        ps.popPose();
        ps.popPose();
        super.render(b, yaw, pt, ps, buf, light);
    }
}
