package com.ditsjohann.warfront.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side tracer simulation. Each round is re-simulated with the same velocity and gravity
 * the server uses, so tracers arc and drop exactly like the real bullet, and drawn as an
 * additive glowing streak.
 */
public final class Tracers {
    private static final class Tracer {
        Vec3 prev, pos, vel;
        final float gravity;
        final int color;
        int age;
        boolean dead;

        Tracer(Vec3 pos, Vec3 vel, float gravity, int color) {
            this.prev = pos;
            this.pos = pos;
            this.vel = vel;
            this.gravity = gravity;
            this.color = color;
        }
    }

    private static final List<Tracer> LIVE = new ArrayList<>();

    public static void add(Vec3 origin, Vec3 vel, float gravity, int color) {
        if (LIVE.size() > 600) LIVE.remove(0);
        LIVE.add(new Tracer(origin, vel, gravity, color));
    }

    public static void clear() {
        LIVE.clear();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Iterator<Tracer> it = LIVE.iterator();
        while (it.hasNext()) {
            Tracer t = it.next();
            if (t.dead || ++t.age > 40) {
                it.remove();
                continue;
            }
            Vec3 next = t.pos.add(t.vel);
            BlockHitResult hit = mc.level.clip(new ClipContext(t.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            t.prev = t.pos;
            if (hit.getType() == HitResult.Type.BLOCK) {
                t.pos = hit.getLocation();
                t.dead = true;
            } else {
                t.pos = next;
            }
            t.vel = t.vel.add(0, -t.gravity, 0).scale(0.995);
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Camera cam = event.getCamera();
        Vec3 c = cam.getPosition();
        float pt = event.getPartialTick();
        Matrix4f m = new Matrix4f().rotation(cam.rotation().conjugate(new Quaternionf()));
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        for (Tracer t : LIVE) {
            Vec3 head = t.prev.lerp(t.pos, t.dead ? 1 : pt);
            Vec3 dir = t.vel.normalize();
            double len = Math.min(t.vel.length() * 0.9, 7.0);
            Vec3 tail = head.subtract(dir.scale(len));
            // don't draw behind the muzzle
            if (tail.distanceToSqr(t.prev) > head.distanceToSqr(t.prev) && t.age <= 1) tail = t.prev;
            Vec3 a = tail.subtract(c), b = head.subtract(c);
            Vec3 seg = b.subtract(a);
            Vec3 side = seg.cross(a).normalize();
            double dist = a.length();
            float w = (float) Math.max(0.012, 0.0045 * dist) * 1.0f;
            Vec3 s = side.scale(w);
            int r = (t.color >> 16) & 255, g = (t.color >> 8) & 255, bl = t.color & 255;
            int alphaHead = 230, alphaTail = 0;
            vc.addVertex(m, (float) (a.x + s.x), (float) (a.y + s.y), (float) (a.z + s.z)).setColor(r, g, bl, alphaTail);
            vc.addVertex(m, (float) (a.x - s.x), (float) (a.y - s.y), (float) (a.z - s.z)).setColor(r, g, bl, alphaTail);
            vc.addVertex(m, (float) (b.x - s.x), (float) (b.y - s.y), (float) (b.z - s.z)).setColor(r, g, bl, alphaHead);
            vc.addVertex(m, (float) (b.x + s.x), (float) (b.y + s.y), (float) (b.z + s.z)).setColor(r, g, bl, alphaHead);
            // bright core
            Vec3 s2 = s.scale(0.35);
            vc.addVertex(m, (float) (a.x + s2.x), (float) (a.y + s2.y), (float) (a.z + s2.z)).setColor(255, 255, 230, 0);
            vc.addVertex(m, (float) (a.x - s2.x), (float) (a.y - s2.y), (float) (a.z - s2.z)).setColor(255, 255, 230, 0);
            vc.addVertex(m, (float) (b.x - s2.x), (float) (b.y - s2.y), (float) (b.z - s2.z)).setColor(255, 255, 230, 255);
            vc.addVertex(m, (float) (b.x + s2.x), (float) (b.y + s2.y), (float) (b.z + s2.z)).setColor(255, 255, 230, 255);
        }
        buf.endBatch(RenderType.lightning());
    }

    private Tracers() {}
}
