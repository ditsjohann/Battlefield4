package com.ditsjohann.warfront.client.render;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.registry.ModSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-only combat dressing: ejected brass that tumbles and clinks, bullet-hole decals with
 * debris puffs where rounds strike blocks, muzzle flashes for other shooters and barrel smoke.
 */
public final class WeaponEffects {
    private static final ResourceLocation HOLE = Warfront.id("textures/entity/bullet_hole.png");
    private static final ResourceLocation FLASH = Warfront.id("textures/entity/muzzle_flash.png");
    private static final RandomSource RNG = RandomSource.create();

    private static final class Casing {
        Vec3 prev, pos, vel;
        float rot, rotPrev, spin;
        int age, bounces;
        final boolean shell;

        Casing(Vec3 pos, Vec3 vel, boolean shell) {
            this.prev = pos;
            this.pos = pos;
            this.vel = vel;
            this.shell = shell;
            this.spin = 25 + RNG.nextFloat() * 40;
        }
    }

    private record Decal(Vec3 pos, Direction face, float rot, float size, long born) {}

    private static final class Flash {
        final Vec3 pos;
        int age;

        Flash(Vec3 pos) { this.pos = pos; }
    }

    private static final List<Casing> CASINGS = new ArrayList<>();
    private static final ArrayDeque<Decal> DECALS = new ArrayDeque<>();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static float barrelHeat;

    // ------------------------------------------------------------------ spawning
    /** Ejects a casing from the right of the player's view (first-person ejection port). */
    public static void ejectLocal(boolean shell, boolean ads) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 look = mc.player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 origin = mc.player.getEyePosition().add(look.scale(ads ? 0.55 : 0.62)).add(right.scale(ads ? 0.14 : 0.3)).add(up.scale(ads ? -0.07 : -0.15));
        Vec3 vel = right.scale(0.13 + RNG.nextFloat() * 0.05).add(up.scale(0.08 + RNG.nextFloat() * 0.04)).add(look.scale(-0.015))
                .add(mc.player.getDeltaMovement());
        addCasing(origin, vel, shell);
        barrelHeat = Math.min(1f, barrelHeat + 0.045f);
    }

    public static void addCasing(Vec3 pos, Vec3 vel, boolean shell) {
        if (CASINGS.size() > 160) CASINGS.remove(0);
        CASINGS.add(new Casing(pos, vel, shell));
    }

    /** A remote shooter fired from roughly this eye position along dir. */
    public static void remoteShot(Vec3 origin, Vec3 dir, boolean suppressed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 d = dir.normalize();
        Vec3 right = d.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 muzzle = origin.add(d.scale(0.95)).add(right.scale(0.22)).add(0, -0.22, 0);
        if (!suppressed && muzzle.distanceToSqr(mc.player.getEyePosition()) < 160 * 160) {
            if (FLASHES.size() < 64) FLASHES.add(new Flash(muzzle));
            mc.level.addParticle(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, d.x * 0.03, 0.01, d.z * 0.03);
        }
        if (origin.distanceToSqr(mc.player.position()) < 24 * 24) {
            Vec3 port = origin.add(d.scale(0.45)).add(right.scale(0.2)).add(0, -0.2, 0);
            addCasing(port, right.scale(0.1).add(0, 0.08, 0), false);
        }
    }

    /** A tracer struck a block: decal, dust and chips. */
    public static void impact(BlockHitResult hit, Vec3 vel) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockPos bp = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(bp);
        if (state.isAir()) return;
        Direction face = hit.getDirection();
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 p = hit.getLocation();
        if (state.isSolidRender(mc.level, bp) && mc.player != null && p.distanceToSqr(mc.player.position()) < 96 * 96) {
            if (DECALS.size() > 300) DECALS.pollFirst();
            DECALS.add(new Decal(p.add(n.scale(0.004 + RNG.nextFloat() * 0.002)), face, RNG.nextFloat() * 360, 0.07f + RNG.nextFloat() * 0.03f, mc.level.getGameTime()));
        }
        BlockParticleOption chips = new BlockParticleOption(ParticleTypes.BLOCK, state);
        for (int i = 0; i < 5; i++) {
            Vec3 v = n.scale(0.08 + RNG.nextFloat() * 0.1).add((RNG.nextFloat() - 0.5) * 0.12, RNG.nextFloat() * 0.1, (RNG.nextFloat() - 0.5) * 0.12);
            mc.level.addParticle(chips, p.x, p.y, p.z, v.x, v.y, v.z);
        }
        mc.level.addParticle(ParticleTypes.POOF, p.x + n.x * 0.1, p.y + n.y * 0.1, p.z + n.z * 0.1, n.x * 0.02, 0.01, n.z * 0.02);
    }

    public static void clear() {
        CASINGS.clear();
        DECALS.clear();
        FLASHES.clear();
        barrelHeat = 0;
    }

    // ------------------------------------------------------------------ simulation
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Iterator<Casing> it = CASINGS.iterator();
        while (it.hasNext()) {
            Casing c = it.next();
            if (++c.age > 200) {
                it.remove();
                continue;
            }
            c.prev = c.pos;
            c.rotPrev = c.rot;
            if (c.bounces >= 4) {
                continue;
            }
            Vec3 next = c.pos.add(c.vel);
            BlockHitResult hit = mc.level.clip(new ClipContext(c.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
                c.pos = hit.getLocation().add(n.scale(0.01));
                double vn = c.vel.dot(n);
                c.vel = c.vel.subtract(n.scale(vn * 1.45)).scale(0.45);
                c.spin *= 0.5f;
                if (c.bounces < 2 && c.vel.lengthSqr() > 0.0004) {
                    mc.level.playLocalSound(c.pos.x, c.pos.y, c.pos.z, ModSounds.get("casing"), SoundSource.PLAYERS,
                            c.shell ? 0.35f : 0.25f, (c.shell ? 0.7f : 1f) + RNG.nextFloat() * 0.2f, false);
                }
                c.bounces++;
                if (c.vel.lengthSqr() < 0.0002) c.bounces = 4;
            } else c.pos = next;
            c.vel = c.vel.add(0, -0.04, 0).scale(0.98);
            c.rot += c.spin;
        }
        Iterator<Flash> fi = FLASHES.iterator();
        while (fi.hasNext()) if (++fi.next().age > 1) fi.remove();
        long now = mc.level.getGameTime();
        while (!DECALS.isEmpty() && now - DECALS.peekFirst().born() > 20 * 60) DECALS.pollFirst();
        // barrel smoke wisps after sustained fire
        if (barrelHeat > 0.55f && mc.player != null && RNG.nextFloat() < barrelHeat * 0.25f && mc.options.getCameraType().isFirstPerson()
                && com.ditsjohann.warfront.client.GunController.ads < 0.5f) {
            Vec3 look = mc.player.getLookAngle();
            Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 m = mc.player.getEyePosition().add(look.scale(1.25)).add(right.scale(0.3)).add(0, -0.2, 0);
            mc.level.addParticle(ParticleTypes.WHITE_SMOKE, m.x, m.y, m.z, 0, 0.012, 0);
        }
        barrelHeat = Math.max(0, barrelHeat - 0.012f);
    }

    // ------------------------------------------------------------------ rendering
    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (CASINGS.isEmpty() && DECALS.isEmpty() && FLASHES.isEmpty())) return;
        Camera cam = event.getCamera();
        Vec3 c = cam.getPosition();
        float pt = event.getPartialTick();
        PoseStack ps = new PoseStack();
        ps.mulPose(cam.rotation().conjugate(new org.joml.Quaternionf()));
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        if (!DECALS.isEmpty()) {
            VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(HOLE));
            for (Decal d : DECALS) {
                if (d.pos.distanceToSqr(c) > 64 * 64) continue;
                int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(d.pos.add(Vec3.atLowerCornerOf(d.face.getNormal()).scale(0.3))));
                ps.pushPose();
                ps.translate(d.pos.x - c.x, d.pos.y - c.y, d.pos.z - c.z);
                ps.mulPose(d.face.getRotation());          // +Y → face normal
                ps.mulPose(Axis.YP.rotationDegrees(d.rot));
                float s = d.size;
                PoseStack.Pose pose = ps.last();
                long age = mc.level.getGameTime() - d.born();
                int a = age > 20 * 50 ? (int) Mth.clamp(255 - (age - 20 * 50) * 255 / 200, 0, 255) : 255;
                vert(vc, pose, -s, 0, -s, 0, 0, a, light);
                vert(vc, pose, -s, 0, s, 0, 1, a, light);
                vert(vc, pose, s, 0, s, 1, 1, a, light);
                vert(vc, pose, s, 0, -s, 1, 0, a, light);
                ps.popPose();
            }
            buf.endBatch(RenderType.entityTranslucent(HOLE));
        }
        if (!CASINGS.isEmpty()) {
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutout(BoxDraw.PARTS));
            for (Casing k : CASINGS) {
                Vec3 p = k.prev.lerp(k.pos, pt);
                double dc = p.distanceToSqr(c);
                if (dc > 32 * 32 || dc < 0.45 * 0.45) continue;
                int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p));
                ps.pushPose();
                ps.translate(p.x - c.x, p.y - c.y, p.z - c.z);
                float r = Mth.lerp(pt, k.rotPrev, k.rot);
                ps.mulPose(Axis.YP.rotationDegrees(r * 0.7f + k.age));
                ps.mulPose(Axis.XP.rotationDegrees(k.bounces >= 4 ? 90 : r));
                float len = k.shell ? 0.075f : 0.042f, rad = k.shell ? 0.016f : 0.009f;
                int tint = k.shell ? 0xFFB03028 : 0xFFE0B048;
                BoxDraw.box(ps.last(), vc, -rad, -rad, -len / 2, rad, rad, len / 2, BoxDraw.BRIGHT_STEEL, tint, light, OverlayTexture.NO_OVERLAY);
                if (k.shell) BoxDraw.box(ps.last(), vc, -rad * 1.1f, -rad * 1.1f, len / 2 - 0.015f, rad * 1.1f, rad * 1.1f, len / 2, BoxDraw.BRIGHT_STEEL, 0xFFE0B048, light, OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
            buf.endBatch(RenderType.entityCutout(BoxDraw.PARTS));
        }
        if (!FLASHES.isEmpty()) {
            VertexConsumer vc = buf.getBuffer(RenderType.eyes(FLASH));
            for (Flash f : FLASHES) {
                ps.pushPose();
                ps.translate(f.pos.x - c.x, f.pos.y - c.y, f.pos.z - c.z);
                ps.mulPose(cam.rotation());
                ps.mulPose(Axis.ZP.rotationDegrees(RNG.nextFloat() * 360));
                float s = 0.22f + RNG.nextFloat() * 0.12f;
                PoseStack.Pose pose = ps.last();
                FirstPersonGun.quad(vc, pose, -s, -s, 0, s, -s, 0, s, s, 0, -s, s, 0, 0xF000F0);
                ps.popPose();
            }
            buf.endBatch(RenderType.eyes(FLASH));
        }
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int a, int light) {
        vc.addVertex(pose, x, y, z).setColor(255, 255, 255, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }

    private WeaponEffects() {}
}
