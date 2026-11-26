package com.ditsjohann.warfront.client.render;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.combat.Faction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * First-person arms: a gloved hand and a uniform-sleeved forearm drawn from a hand target back
 * toward the shoulder. Sleeve texture follows the player's faction (armband and cuff included).
 */
public final class ArmRenderer {
    private static final ResourceLocation ARMS = Warfront.id("textures/entity/arms.png");

    /**
     * @param hand       hand position in the current pose
     * @param toShoulder direction from the hand back toward the elbow/shoulder (need not be normalised)
     */
    public static void draw(PoseStack ps, MultiBufferSource buf, int light, Vector3f hand, Vector3f toShoulder, Faction faction, boolean left) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(ARMS));
        Vector3f dir = new Vector3f(toShoulder).normalize();
        ps.pushPose();
        ps.translate(hand.x, hand.y, hand.z);
        ps.mulPose(new Quaternionf().rotationTo(0, 0, 1, dir.x, dir.y, dir.z));
        // roll so the back of the hand faces outward/up
        ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(left ? -25f : 25f));
        PoseStack.Pose pose = ps.last();
        float su = faction == Faction.LEGION ? 32 : 0;
        // glove: palm + knuckle block, slightly flattened
        box(pose, vc, -0.05f, -0.04f, -0.055f, 0.05f, 0.04f, 0.05f, 0, 16, 16, 32, light);
        // fingers wrap the grip, thumb along the side
        box(pose, vc, -0.047f, -0.045f, -0.1f, 0.047f, 0.02f, -0.05f, 16, 16, 32, 22, light);
        box(pose, vc, left ? 0.04f : -0.07f, -0.01f, -0.09f, left ? 0.07f : -0.04f, 0.03f, -0.02f, 32, 16, 48, 22, light);
        // wrist/cuff and forearm sleeve
        box(pose, vc, -0.056f, -0.052f, 0.045f, 0.056f, 0.052f, 0.48f, su, 0, su + 32, 16, light);
        // upper sleeve bulk where it leaves the frame
        box(pose, vc, -0.068f, -0.064f, 0.46f, 0.068f, 0.064f, 0.9f, su, 0, su + 32, 6, light);
        ps.popPose();
    }

    /** Box with one UV rect (pixel coords on the 64x32 sheet) mapped on each face; v runs along z. */
    private static void box(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                            float pu0, float pv0, float pu1, float pv1, int light) {
        float u0 = pu0 / 64f, u1 = pu1 / 64f, v0 = pv0 / 32f, v1 = pv1 / 32f;
        // +Y (top)
        q(pose, vc, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, u0, v0, u1, v1, light);
        // -Y
        q(pose, vc, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, u0, v1, u1, v0, light);
        // +X
        q(pose, vc, x1, y1, z0, x1, y0, z0, x1, y0, z1, x1, y1, z1, 1, 0, 0, u0, v0, u1, v1, light);
        // -X
        q(pose, vc, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1, 0, 0, u0, v0, u1, v1, light);
        // caps
        float um = (u0 + u1) / 2, vm = v0 + (v1 - v0) * 0.2f;
        q(pose, vc, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, u0, v0, um, vm, light);
        q(pose, vc, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, 0, 0, 1, u0, v0, um, vm, light);
    }

    /** Quad a-b-c-d; u runs a→b, v runs b→c. */
    private static void q(PoseStack.Pose pose, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                          float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz,
                          float u0, float v0, float u1, float v1, int light) {
        int ov = OverlayTexture.NO_OVERLAY;
        vc.addVertex(pose, ax, ay, az).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(ov).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, bx, by, bz).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(ov).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, cx, cy, cz).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(ov).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, dx, dy, dz).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(ov).setLight(light).setNormal(pose, nx, ny, nz);
    }

    private ArmRenderer() {}
}
