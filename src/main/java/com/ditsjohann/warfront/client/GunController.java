package com.ditsjohann.warfront.client;

import com.ditsjohann.warfront.client.render.Tracers;
import com.ditsjohann.warfront.entity.VehicleEntity;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.registry.ModItems;
import com.ditsjohann.warfront.registry.ModSounds;
import com.ditsjohann.warfront.weapon.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Local weapon handling: trigger logic per fire mode, client-predicted ammo, aim-down-sights,
 * the BF4 spread/bloom model and smooth camera recoil. The server re-validates every shot.
 */
public final class GunController {
    public static float ads, adsPrev;
    public static float bloom;
    public static float kick, kickPrev;
    public static int muzzleFlash;
    public static int boltTimer, pumpTimer;
    /** Random per-shot roll (degrees) applied with the visual kick. */
    public static float lastShotRoll;
    /** Pitch the recoil has climbed during the current string of fire; half is recovered afterwards. */
    private static float climbed;
    private static boolean autoReloadSent;
    private static float cooldown;
    private static int burstLeft;
    private static boolean triggerWasDown, useWasDown;
    private static int clientAmmo = -1, lastServerAmmo = -1, trackedSlot = -1;
    private static ItemStack trackedItem = ItemStack.EMPTY;
    private static float pendingPitch, pendingYaw;
    private static long lastShotTick = -100;
    private static double savedSensitivity = -1;
    private static boolean wasAimingPacket;
    private static int giveUpHold;
    private static CameraType cameraBeforeTank;
    private static boolean wasInTank;
    private static final RandomSource RNG = RandomSource.create();

    public static int displayAmmo(ItemStack stack) {
        return clientAmmo >= 0 && stack == Minecraft.getInstance().player.getMainHandItem() ? clientAmmo : GunLogic.ammo(stack);
    }

    public static float currentSpread(EffectiveStats eff, LocalPlayer p) {
        GunType.Stats s = eff.type().stats;
        float base = Mth.lerp(ease(ads), eff.hipSpread(), eff.adsSpread());
        double horiz = p.getDeltaMovement().horizontalDistance();
        if (horiz > 0.03) base *= s.moveMult;
        if (Movement.isProne(p)) base *= 0.5f;
        else if (p.isCrouching()) base *= 0.75f;
        if (!p.onGround() && p.getVehicle() == null) base *= 2.5f;
        base += bloom * (ads > 0.5f ? 0.6f : 1f);
        base *= 1 + ClientState.suppression * 1.2f;
        return base;
    }

    public static float ease(float t) {
        return t * t * (3 - 2 * t);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        adsPrev = ads;
        kickPrev = kick;
        kick *= 0.62f;
        if (muzzleFlash > 0) muzzleFlash--;
        if (pumpTimer > 0) {
            pumpTimer--;
            if (pumpTimer == 6) com.ditsjohann.warfront.client.render.WeaponEffects.ejectLocal(true, ads > 0.5f);
        }
        if (boltTimer > 0) {
            boltTimer--;
            if (boltTimer == 11) com.ditsjohann.warfront.client.render.WeaponEffects.ejectLocal(false, ads > 0.5f);
            if (boltTimer == 12) p.playSound(ModSounds.get("reload_bolt"), 0.6f, 1.1f);
        }
        handleDowned(mc, p);
        if (handleVehicle(mc, p)) return;

        ItemStack stack = p.getMainHandItem();
        boolean binos = p.isUsingItem() && p.getUseItem().is(ModItems.FIELD_BINOCULARS.get());
        if (!(stack.getItem() instanceof GunItem gun)) {
            ads = Math.max(0, ads - 0.25f);
            if (binos) ads = Math.min(1, ads + 0.35f);
            clientAmmo = -1;
            restoreSensitivity(mc);
            sendAim(false);
            return;
        }
        GunType type = gun.type;
        EffectiveStats eff = EffectiveStats.of(type, GunLogic.loadout(stack, type));

        // ---- client-predicted ammo
        int serverAmmo = GunLogic.ammo(stack);
        int slot = p.getInventory().selected;
        if (slot != trackedSlot || stack.getItem() != trackedItem.getItem() || clientAmmo < 0) {
            clientAmmo = serverAmmo;
            trackedSlot = slot;
            trackedItem = stack;
            cooldown = 4; // weapon raise time
            bloom = 0;
        } else if (serverAmmo != lastServerAmmo) {
            clientAmmo = serverAmmo > lastServerAmmo ? serverAmmo : Math.min(clientAmmo, serverAmmo);
        }
        lastServerAmmo = serverAmmo;
        boolean reloading = GunLogic.reloading(stack);

        // ---- aim down sights
        boolean canAct = mc.screen == null && !ClientState.downed;
        boolean wantAds = canAct && mc.options.keyUse.isDown() && !reloading;
        if (wantAds && p.isSprinting()) p.setSprinting(false);
        float rate = 0.16f * eff.adsSpeed();
        ads = Mth.clamp(ads + (wantAds ? rate : -rate * 1.4f), 0, 1);
        sendAim(wantAds);
        applySensitivity(mc, eff.zoom());

        // ---- spread recovery
        bloom = Math.max(0, bloom - type.stats.spreadDecay);
        if (cooldown > 0) cooldown -= 1;

        // ---- trigger
        boolean trigger = canAct && mc.options.keyAttack.isDown();
        FireMode mode = GunLogic.fireMode(stack, type);
        boolean pressed = trigger && !triggerWasDown;
        triggerWasDown = trigger;
        if (reloading) {
            burstLeft = 0;
            autoReloadSent = false;
            return;
        }
        // BF4 behaviour: running dry starts the reload on its own once the trigger is released or after a beat
        long nowT = p.level().getGameTime();
        if (clientAmmo == 0 && GunLogic.reserve(stack) > 0 && !autoReloadSent && nowT - lastShotTick >= 4 && (!trigger || nowT - lastShotTick >= 8)) {
            autoReloadSent = true;
            WarfrontNet.toServer(new SimplePackets.Action(SimplePackets.Action.RELOAD));
            return;
        }
        if (clientAmmo > 0) autoReloadSent = false;
        if (pressed && clientAmmo <= 0 && (autoReloadSent || GunLogic.reserve(stack) <= 0)) {
            p.playSound(ModSounds.get("dry_fire"), 0.6f, 1f);
            return;
        }
        if (pressed && clientAmmo <= 0) {
            p.playSound(ModSounds.get("dry_fire"), 0.6f, 1f);
            WarfrontNet.toServer(new SimplePackets.Action(SimplePackets.Action.RELOAD));
            return;
        }
        if (mode == FireMode.BURST && pressed && burstLeft <= 0) burstLeft = type.stats.burst;
        boolean wantShot = switch (mode) {
            case AUTO -> trigger;
            case BURST -> burstLeft > 0;
            case SEMI, BOLT -> pressed;
        };
        if (trigger && p.isSprinting()) p.setSprinting(false);
        if (wantShot && cooldown <= 0 && clientAmmo > 0 && boltTimer <= 0) {
            fire(mc, p, stack, eff, mode);
            cooldown += type.stats.shotInterval();
            if (cooldown < 0) cooldown = 0;
            if (mode == FireMode.BURST) burstLeft--;
        } else if (cooldown < 0) cooldown = 0;
        if (clientAmmo <= 0 && trigger && type.category != WeaponCategory.SHOTGUN) burstLeft = 0;
    }

    private static void fire(Minecraft mc, LocalPlayer p, ItemStack stack, EffectiveStats eff, FireMode mode) {
        GunType type = eff.type();
        long now = p.level().getGameTime();
        float spread = currentSpread(eff, p);
        Vec3 look = p.getLookAngle();
        Vec3 dir = type.stats.pellets > 1 ? look : GunLogic.applySpread(look, spread, RNG);
        float yaw = (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG);
        float pitch = (float) -(Math.asin(Mth.clamp(dir.y, -1, 1)) * Mth.RAD_TO_DEG);
        WarfrontNet.toServer(new SimplePackets.Shoot(yaw, pitch));
        clientAmmo--;
        // ---- local feedback (others get these from the server)
        String sound = eff.suppressed() ? "suppressed_shot" : type.stats.sound;
        p.level().playLocalSound(p.getX(), p.getEyeY(), p.getZ(), ModSounds.get(sound), SoundSource.PLAYERS, 1.3f,
                type.stats.pitch * (0.97f + RNG.nextFloat() * 0.06f), false);
        Vec3 eye = p.getEyePosition();
        for (int i = 0; i < type.stats.pellets; i++) {
            Vec3 d = type.stats.pellets > 1 ? GunLogic.applySpread(look, type.stats.adsSpread, RNG) : dir;
            // offset the visual tracer origin to the gun's muzzle so it doesn't start in your eye
            Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 origin = eye.add(look.scale(0.9)).add(right.scale(ads > 0.5f ? 0.0 : 0.18)).add(0, ads > 0.5f ? -0.08 : -0.16, 0);
            Tracers.add(origin, d.scale(eff.velocity()), type.stats.gravity, type.stats.tracerColor);
        }
        if (clientAmmo <= type.stats.magSize / 4 && clientAmmo >= 0) {
            p.level().playLocalSound(p.getX(), p.getEyeY(), p.getZ(), ModSounds.get("ammo_low"), SoundSource.PLAYERS, 0.5f, 1f + RNG.nextFloat() * 0.1f, false);
        }
        muzzleFlash = eff.suppressed() ? 0 : 2;
        kick = 1f;
        // ---- recoil
        boolean first = now - lastShotTick > 6;
        float up = eff.recoilUp() * (0.9f + RNG.nextFloat() * 0.1f) * (first ? eff.firstShotMult() : 1f);
        float side = (RNG.nextFloat() * 2 - 1) * eff.recoilSide();
        float mult = 0.72f;
        if (Movement.isProne(p)) mult *= eff.bipod() ? 0.25f : 0.6f;
        else if (p.isCrouching()) mult *= eff.bipod() ? 0.3f : 0.85f;
        if (ads < 0.5f) mult *= 1.15f;
        pendingPitch += up * mult;
        climbed += up * mult;
        lastShotRoll = (RNG.nextFloat() * 2 - 1) * (type.category == WeaponCategory.PISTOL ? 4f : 2.5f);
        if (mode != FireMode.BOLT && type != GunType.SG12) com.ditsjohann.warfront.client.render.WeaponEffects.ejectLocal(false, ads > 0.5f);
        pendingYaw += side * mult;
        bloom = Math.min(type.stats.spreadMax, bloom + type.stats.spreadPerShot);
        lastShotTick = now;
        if (mode == FireMode.BOLT) boltTimer = 20;
        if (type == GunType.SG12) pumpTimer = 12;
    }

    /** Applies pending recoil smoothly over a few frames. Called every render frame. */
    public static void applyRecoil(float frameTicks) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        long since = p.level().getGameTime() - lastShotTick;
        if (since > 3 && climbed > 0.01f && Math.abs(pendingPitch) < 0.05f) {
            // recover about half of the climb, eased, once the string of fire ends
            float target = climbed * 0.5f;
            float step = Math.min(target, Math.max(0.02f, target * 0.18f * Math.max(0.2f, frameTicks) * 3f));
            p.turn(0, step / 0.15);
            climbed -= step * 2f;
            if (climbed < 0.01f) climbed = 0;
        }
        if (since > 40) climbed = 0;
        if (Math.abs(pendingPitch) < 0.002f && Math.abs(pendingYaw) < 0.002f) {
            pendingPitch = pendingYaw = 0;
            return;
        }
        float f = Math.min(1f, 0.55f * Math.max(0.2f, frameTicks) * 3f);
        float dp = pendingPitch * f, dy = pendingYaw * f;
        pendingPitch -= dp;
        pendingYaw -= dy;
        // Entity.turn scales by 0.15 and keeps the previous-rotation fields consistent
        p.turn(dy / 0.15, -dp / 0.15);
    }

    private static void sendAim(boolean aiming) {
        if (aiming != wasAimingPacket) {
            wasAimingPacket = aiming;
            WarfrontNet.toServer(new SimplePackets.Action(aiming ? SimplePackets.Action.AIM_ON : SimplePackets.Action.AIM_OFF));
        }
    }

    /** Lowers mouse sensitivity while zoomed so magnified optics stay controllable. */
    private static void applySensitivity(Minecraft mc, float zoom) {
        if (ads > 0.6f && zoom >= 1.9f) {
            if (savedSensitivity < 0) savedSensitivity = mc.options.sensitivity().get();
            double s = savedSensitivity;
            double k = Math.cbrt(1.0 / zoom * 1.25);
            double target = ((s * 0.6 + 0.2) * k - 0.2) / 0.6;
            mc.options.sensitivity().set(Mth.clamp(target, 0.0, 1.0));
        } else restoreSensitivity(mc);
    }

    public static void restoreSensitivity(Minecraft mc) {
        if (savedSensitivity >= 0) {
            mc.options.sensitivity().set(savedSensitivity);
            savedSensitivity = -1;
        }
    }

    private static void handleDowned(Minecraft mc, LocalPlayer p) {
        if (!ClientState.downed) {
            giveUpHold = 0;
            return;
        }
        if (mc.options.keyJump.isDown() && mc.screen == null) {
            if (++giveUpHold == 30) WarfrontNet.toServer(new SimplePackets.Action(SimplePackets.Action.BLEED_OUT));
        } else giveUpHold = 0;
    }

    public static float giveUpProgress() {
        return Math.min(1f, giveUpHold / 30f);
    }

    /**
     * Vehicle controls for drivers and gunners. Returns true when the player's mouse buttons belong
     * to the vehicle (personal weapon handling suspended). Deck riders keep their own guns.
     */
    private static boolean handleVehicle(Minecraft mc, LocalPlayer p) {
        boolean crew = p.getVehicle() instanceof VehicleEntity v && v.usesVehicleControls(p);
        if (crew != wasInTank) {
            wasInTank = crew;
            if (crew) {
                cameraBeforeTank = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            } else if (cameraBeforeTank != null) {
                mc.options.setCameraType(cameraBeforeTank);
                cameraBeforeTank = null;
            }
        }
        if (!crew) return false;
        boolean canAct = mc.screen == null;
        boolean attack = canAct && mc.options.keyAttack.isDown();
        boolean use = canAct && mc.options.keyUse.isDown();
        if (attack && !triggerWasDown) WarfrontNet.toServer(new SimplePackets.VehicleInput(VehicleEntity.IN_ATTACK_PRESS));
        if (attack != triggerWasDown) WarfrontNet.toServer(new SimplePackets.VehicleInput(attack ? VehicleEntity.IN_ATTACK_ON : VehicleEntity.IN_ATTACK_OFF));
        if (use != useWasDown) WarfrontNet.toServer(new SimplePackets.VehicleInput(use ? VehicleEntity.IN_USE_ON : VehicleEntity.IN_USE_OFF));
        triggerWasDown = attack;
        useWasDown = use;
        ads = Math.max(0, ads - 0.25f);
        restoreSensitivity(mc);
        return true;
    }

    public static boolean holdingGun() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.getMainHandItem().getItem() instanceof GunItem;
    }

    public static boolean drivingTank() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.getVehicle() instanceof VehicleEntity v && v.usesVehicleControls(p);
    }

    private GunController() {}
}
