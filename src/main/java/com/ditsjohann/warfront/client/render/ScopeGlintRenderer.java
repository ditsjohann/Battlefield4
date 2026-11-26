package com.ditsjohann.warfront.client.render;

import com.ditsjohann.warfront.client.ClientState;
import com.ditsjohann.warfront.entity.SoldierEntity;
import com.ditsjohann.warfront.entity.SoldierRole;
import com.ditsjohann.warfront.weapon.WeaponCategory;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Draws scope glints: a bright star at the optic of anyone aiming a high-zoom scope at you.
 * It grows with distance so a distant sniper is still visible, and fades as the scope turns away.
 */
public final class ScopeGlintRenderer {
    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Camera cam = event.getCamera();
        Vec3 c = cam.getPosition();
        float pt = event.getPartialTick();
        Matrix4f m = new Matrix4f().rotation(cam.rotation().conjugate(new Quaternionf()));
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = null;
        double time = mc.level.getGameTime() + pt;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || !(e instanceof LivingEntity le)) continue;
            boolean glint = e instanceof Player ? ClientState.GLINTING.contains(e.getId())
                    : e instanceof SoldierEntity s && s.isAiming() && s.getRole() == SoldierRole.RECON && s.gunType().category == WeaponCategory.SNIPER;
            if (!glint) continue;
            Vec3 eye = le.getEyePosition(pt);
            Vec3 look = le.getViewVector(pt);
            Vec3 toCam = c.subtract(eye);
            double dist = toCam.length();
            if (dist < 6 || dist > 320) continue;
            double facing = look.dot(toCam.normalize());
            if (facing < 0.88) continue;
            Vec3 at = eye.add(look.scale(0.45));
            if (mc.level.clip(new ClipContext(c, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player)).getType() != HitResult.Type.MISS) continue;
            float intensity = (float) ((facing - 0.88) / 0.12);
            float size = (float) (0.08 * Math.pow(dist, 0.75)) * (0.75f + 0.25f * (float) Math.sin(time * 1.3)) * (0.4f + 0.6f * intensity);
            if (vc == null) vc = buf.getBuffer(RenderType.lightning());
            Vec3 rel = at.subtract(c);
            Vec3 right = rel.cross(new Vec3(0, 1, 0)).normalize().scale(size);
            Vec3 up = right.cross(rel).normalize().scale(size);
            int a = Math.round(255 * Math.min(1f, 0.35f + intensity));
            star(vc, m, rel, right, up.scale(0.18), a);
            star(vc, m, rel, up, right.scale(0.18), a);
            star(vc, m, rel, right.scale(0.35), up.scale(0.35), a);
        }
        if (vc != null) buf.endBatch(RenderType.lightning());
    }

    private static void star(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, int a) {
        vc.addVertex(m, (float) (c.x - u.x - v.x), (float) (c.y - u.y - v.y), (float) (c.z - u.z - v.z)).setColor(255, 250, 220, 0);
        vc.addVertex(m, (float) (c.x + u.x - v.x), (float) (c.y + u.y - v.y), (float) (c.z + u.z - v.z)).setColor(255, 250, 220, 0);
        vc.addVertex(m, (float) (c.x + u.x + v.x), (float) (c.y + u.y + v.y), (float) (c.z + u.z + v.z)).setColor(255, 250, 220, a);
        vc.addVertex(m, (float) (c.x - u.x + v.x), (float) (c.y - u.y + v.y), (float) (c.z - u.z + v.z)).setColor(255, 250, 220, a);
        vc.addVertex(m, (float) (c.x - u.x + v.x), (float) (c.y - u.y + v.y), (float) (c.z - u.z + v.z)).setColor(255, 250, 220, a);
        vc.addVertex(m, (float) (c.x + u.x + v.x), (float) (c.y + u.y + v.y), (float) (c.z + u.z + v.z)).setColor(255, 250, 220, a);
        vc.addVertex(m, (float) (c.x + u.x - v.x), (float) (c.y + u.y - v.y), (float) (c.z + u.z - v.z)).setColor(255, 250, 220, 0);
        vc.addVertex(m, (float) (c.x - u.x - v.x), (float) (c.y - u.y - v.y), (float) (c.z - u.z - v.z)).setColor(255, 250, 220, 0);
    }

    private ScopeGlintRenderer() {}
}
