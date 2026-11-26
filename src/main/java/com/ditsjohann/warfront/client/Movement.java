package com.ditsjohann.warfront.client;

import com.ditsjohann.warfront.client.render.FirstPersonGun;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

/**
 * BF4 infantry movement on the client: prone (toggle), mantling onto 2-high ledges, and sniper
 * scope sway with hold-breath (sprint key while scoped). Movement stays client-authoritative like
 * vanilla; prone is mirrored to the server so the pose (hitbox, eye height) is real for everyone.
 */
public final class Movement {
    public static boolean prone;
    /** Breath remaining 0..1 while holding it; exhausted forces a recovery period. */
    public static float breath = 1f;
    public static boolean holdingBreath;
    private static int exhausted;
    private static boolean jumpWasDown;
    private static int mantleTicks;
    private static float swayX, swayY;
    private static long swayStartNanos = System.nanoTime();

    public static void tick(Minecraft mc, LocalPlayer p) {
        // ---- prone toggle
        while (KeyBinds.PRONE.consumeClick()) setProne(p, !prone);
        if (prone && (p.isPassenger() || p.isInWater() || p.getAbilities().flying || ClientState.downed || p.isSprinting() && !p.isCrouching())) setProne(p, false);
        if (prone) p.setForcedPose(Pose.SWIMMING);

        // ---- mantle: jump into a 2-high ledge with room above
        boolean jump = mc.options.keyJump.isDown() && mc.screen == null;
        if (jump && !jumpWasDown) {
            if (prone) {
                setProne(p, false);
            } else if (p.onGround() && p.input.forwardImpulse > 0 && p.getVehicle() == null && canMantle(p)) {
                Vec3 f = Vec3.directionFromRotation(0, p.getYRot());
                p.setDeltaMovement(f.x * 0.16, 0.62, f.z * 0.16);
                mantleTicks = 10;
                p.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.6f, 0.8f);
                FirstPersonGunHooks.mantle();
            }
        }
        jumpWasDown = jump;
        if (mantleTicks > 0) {
            mantleTicks--;
            // carry over the lip once the feet clear it
            if (mantleTicks < 6 && !p.onGround()) {
                Vec3 f = Vec3.directionFromRotation(0, p.getYRot());
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(f.x * 0.22, v.y, f.z * 0.22);
            }
        }

        // ---- hold breath while scoped (sprint key)
        boolean scoped = FirstPersonGun.scoped();
        boolean want = scoped && mc.options.keySprint.isDown() && exhausted == 0;
        if (want && breath > 0) {
            if (!holdingBreath) p.playSound(ModSounds.get("breath_in"), 0.5f, 1f);
            holdingBreath = true;
            breath = Math.max(0, breath - 1f / 100f);
            if (breath <= 0) {
                exhausted = 50;
                holdingBreath = false;
                p.playSound(ModSounds.get("breath_out"), 0.7f, 0.9f);
            }
        } else {
            if (holdingBreath) p.playSound(ModSounds.get("breath_out"), 0.5f, 1f);
            holdingBreath = false;
            if (exhausted > 0) exhausted--;
            breath = Math.min(1f, breath + 1f / 80f);
        }
    }

    private static boolean canMantle(LocalPlayer p) {
        Vec3 f = Vec3.directionFromRotation(0, p.getYRot());
        BlockPos feet = p.blockPosition();
        BlockPos ahead = BlockPos.containing(p.getX() + f.x * 0.9, p.getY() + 0.1, p.getZ() + f.z * 0.9);
        if (ahead.equals(feet)) return false;
        var level = p.level();
        boolean lowSolid = !level.getBlockState(ahead).getCollisionShape(level, ahead).isEmpty();
        boolean midSolid = !level.getBlockState(ahead.above()).getCollisionShape(level, ahead.above()).isEmpty();
        // only true ledges vanilla jumping can't take: 2 high, with 2 free blocks above, and headroom here
        if (!midSolid) return false;
        for (BlockPos q : new BlockPos[]{ahead.above(2), ahead.above(3), feet.above(2), feet.above(3)}) {
            if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) return false;
        }
        return lowSolid || midSolid;
    }

    public static void setProne(LocalPlayer p, boolean on) {
        if (prone == on) return;
        if (on && (p.isPassenger() || p.isInWater())) return;
        prone = on;
        p.setForcedPose(on ? Pose.SWIMMING : null);
        p.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.5f, on ? 0.8f : 1.1f);
        WarfrontNet.toServer(new SimplePackets.Action(on ? SimplePackets.Action.PRONE_ON : SimplePackets.Action.PRONE_OFF));
    }

    public static void reset() {
        prone = false;
        breath = 1f;
        exhausted = 0;
        holdingBreath = false;
    }

    public static boolean isProne(LocalPlayer p) {
        return prone && p.getPose() == Pose.SWIMMING && !p.isInWater();
    }

    /**
     * Scope sway, applied per frame as small view rotations (so it's real aim drift, not a visual
     * offset). Prone and crouch steady the rifle; holding breath nearly stops it; running out of
     * breath makes it worse for a moment.
     */
    public static void applySway(LocalPlayer p) {
        boolean scoped = FirstPersonGun.scoped();
        float t = (System.nanoTime() - swayStartNanos) / 1e9f;
        float amp = 0;
        if (scoped) {
            amp = isProne(p) ? 0.12f : p.isCrouching() ? 0.28f : 0.45f;
            if (holdingBreath) amp *= 0.08f;
            if (exhausted > 0) amp *= 2.2f;
            amp *= 1 + ClientState.suppression;
        }
        float nx = (Mth.sin(t * 0.85f) + 0.4f * Mth.sin(t * 2.1f)) * amp;
        float ny = (Mth.sin(t * 1.3f) * Mth.cos(t * 0.45f)) * amp * 0.8f;
        if (!scoped) {
            // ease back out when leaving the scope rather than snapping
            nx = swayX * 0.8f;
            ny = swayY * 0.8f;
            if (Math.abs(nx) < 1e-3 && Math.abs(ny) < 1e-3) nx = ny = 0;
        }
        float dx = nx - swayX, dy = ny - swayY;
        swayX = nx;
        swayY = ny;
        if (scoped && (dx != 0 || dy != 0)) p.turn(dx / 0.15, dy / 0.15);
    }

    private Movement() {}

    /** Small indirection so the first-person renderer can react to a mantle. */
    static final class FirstPersonGunHooks {
        static void mantle() {
            FirstPersonGun.mantle();
        }
    }
}
