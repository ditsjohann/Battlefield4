package com.ditsjohann.warfront.client.render;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.client.ClientState;
import com.ditsjohann.warfront.client.GunController;
import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.registry.ModComponents;
import com.ditsjohann.warfront.weapon.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderHandEvent;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;

/**
 * First-person weapon presentation (replaces vanilla's item-in-hand pass while holding a gun).
 *
 * <p>Layers, outermost first: hip/ADS anchor, walk cycle and breathing, spring-damped turn sway,
 * jump/land reaction, sprint carry, reload choreography, bolt/pump cycling, and recoil (kick,
 * climb, roll). The magazine is a separate model so reloads physically pull and seat it, and the
 * arms are drawn with faction uniform sleeves and tactical gloves.
 */
public final class FirstPersonGun {
    private static final ResourceLocation FLASH = Warfront.id("textures/entity/muzzle_flash.png");
    /** One gun-model unit in camera space (models render at 0.45 scale; 1 unit = 1/16 block). */
    public static final float S = 0.45f / 16f;
    /** Read by the "warfront:nomag" item property: the first-person body renders without its magazine. */
    public static boolean renderingMagless;
    private static final Map<GunType, ModelResourceLocation> MAG_MODELS = new EnumMap<>(GunType.class);

    private static float sprint, sprintPrev;
    private static float swayX, swayY, lastYaw, lastPitch;
    private static float land, landPrev, airLift;
    private static boolean wasOnGround = true;
    private static long lastFrameNanos;

    /** A mantle started: dip and push the weapon aside briefly. */
    public static void mantle() {
        land = Math.min(1.4f, land + 1.2f);
    }

    public static ModelResourceLocation magModel(GunType t) {
        return MAG_MODELS.computeIfAbsent(t, k -> new ModelResourceLocation(Warfront.id("item/gun/" + k.id + "_mag"), "standalone"));
    }

    public static float sightHeight(GunType type, Attachment optic) {
        float base = switch (type) {
            case P12 -> 11.3f;
            case SG12 -> 11.2f;
            case HMG240 -> 12.25f;
            default -> 12.25f;
        };
        return switch (optic) {
            case RED_DOT -> 12.7f + (type == GunType.HMG240 ? 0.5f : type == GunType.SR338 ? -0.2f : 0);
            case HOLO -> 13.05f + (type == GunType.HMG240 ? 0.5f : 0);
            case PRISM_4X -> 13.1f + (type == GunType.HMG240 ? 0.5f : 0);
            case SNIPER_8X -> 13.0f;
            default -> base + (type == GunType.HMG240 ? 0.6f : 0);
        };
    }

    /** True while a high-magnification scope fills the screen (HUD draws the scope overlay). */
    public static boolean scoped() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ItemStack s = mc.player.getMainHandItem();
        if (!(s.getItem() instanceof GunItem g)) return false;
        Attachment optic = GunLogic.loadout(s, g.type).optic();
        return GunController.ads > 0.92f && optic.zoom >= 4f && mc.options.getCameraType().isFirstPerson();
    }

    // ------------------------------------------------------------------ animation state per frame
    private static void updateMotion(LocalPlayer p, float pt) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastFrameNanos) / 1e9f);
        lastFrameNanos = now;
        float yaw = p.getViewYRot(pt), pitch = p.getViewXRot(pt);
        float dYaw = Mth.wrapDegrees(yaw - lastYaw), dPitch = pitch - lastPitch;
        lastYaw = yaw;
        lastPitch = pitch;
        // spring toward the opposite of the turn: the gun lags behind the view
        float k = 1f - (float) Math.exp(-dt * 9f);
        swayX = Mth.lerp(k, swayX, Mth.clamp(-dYaw * 0.9f, -6f, 6f));
        swayY = Mth.lerp(k, swayY, Mth.clamp(-dPitch * 0.9f, -6f, 6f));
        // landing dip / jump lift
        landPrev = land;
        boolean onGround = p.onGround();
        if (onGround && !wasOnGround) land = Math.min(1.2f, land + 0.6f + (float) Math.max(0, -p.getDeltaMovement().y) * 1.5f);
        wasOnGround = onGround;
        land *= (float) Math.exp(-dt * 7f);
        airLift = Mth.lerp(k * 0.5f, airLift, onGround ? 0f : (float) Mth.clamp(p.getDeltaMovement().y, -0.6, 0.6));
    }

    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        ItemStack main = p.getMainHandItem();
        boolean holdingGun = main.getItem() instanceof GunItem;
        if (event.getHand() == InteractionHand.OFF_HAND) {
            if (holdingGun) event.setCanceled(true);
            return;
        }
        if (!holdingGun) return;
        event.setCanceled(true);
        if (scoped()) return;
        GunType type = ((GunItem) main.getItem()).type;
        Loadout loadout = GunLogic.loadout(main, type);
        float pt = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource buf = event.getMultiBufferSource();
        int light = event.getPackedLight();
        updateMotion(p, pt);

        float ads = GunController.ease(Mth.lerp(pt, GunController.adsPrev, GunController.ads));
        float hip = 1f - ads;
        float kick = Mth.lerp(pt, GunController.kickPrev, GunController.kick);
        sprintPrev = sprint;
        sprint = Mth.lerp(0.12f, sprint, p.isSprinting() && ads < 0.1f && !GunLogic.reloading(main) ? 1f : 0f);
        float sp = Mth.lerp(pt, sprintPrev, sprint);
        Reload rl = reload(main, type, p, pt);
        float bolt = GunController.boltTimer > 0 ? 1f - (GunController.boltTimer - pt) / 20f : 0f;
        float pump = GunController.pumpTimer > 0 ? Mth.sin((1f - (GunController.pumpTimer - pt) / 12f) * Mth.PI) : 0f;
        float equip = event.getEquipProgress();
        float time = (p.tickCount + pt);
        float walk = p.walkDist + (p.walkDist - p.walkDistO) * pt;
        float bobAmt = Mth.lerp(pt, p.oBob, p.bob);

        float sight = sightHeight(type, loadout.optic());
        boolean pistol = type.category == WeaponCategory.PISTOL;
        // ---- anchor: hip pose sits low-right and angled slightly inward; ADS centres the sight line
        float hx = pistol ? 0.22f : 0.3f, hy = pistol ? -0.23f : -0.25f, hz = pistol ? -0.58f : -0.64f;
        float ax = 0f, ay = -(sight - 8f) * S, az = pistol ? -0.36f : -0.32f;
        ps.pushPose();
        ps.translate(Mth.lerp(ads, hx, ax), Mth.lerp(ads, hy, ay) - equip * 0.55f, Mth.lerp(ads, hz, az));
        ps.mulPose(Axis.YP.rotationDegrees(6.0f * hip));
        ps.mulPose(Axis.XP.rotationDegrees((pistol ? 1.5f : 5.0f) * hip));
        ps.mulPose(Axis.ZP.rotationDegrees(2.0f * hip));

        // ---- walk cycle (figure eight), breathing, turn sway, jump/land
        float walkPhase = walk * Mth.PI;
        float moveAmp = Math.min(1f, bobAmt * 6f) * (1f - 0.75f * ads);
        ps.translate(Mth.sin(walkPhase) * 0.016f * moveAmp, -Math.abs(Mth.cos(walkPhase)) * 0.014f * moveAmp, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(walkPhase) * 1.6f * moveAmp));
        float breath = 1f - 0.8f * ads;
        ps.translate(0, Mth.sin(time * 0.06f) * 0.0045f * breath, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.033f) * 0.5f * breath));
        float swayK = 1f - 0.7f * ads;
        ps.mulPose(Axis.YP.rotationDegrees(swayX * 0.55f * swayK));
        ps.mulPose(Axis.XP.rotationDegrees(swayY * 0.55f * swayK));
        ps.translate(swayX * 0.0025f * swayK, -swayY * 0.0022f * swayK, 0);
        float landNow = Mth.lerp(pt, landPrev, land);
        ps.translate(0, -landNow * 0.045f - airLift * 0.03f, 0);
        ps.mulPose(Axis.XP.rotationDegrees(-landNow * 4f + airLift * 6f));

        // ---- sprint carry: gun rolled in, muzzle down-left, bouncing with the stride
        if (sp > 0.01f) {
            float run = Mth.sin(walkPhase * 1.0f);
            ps.translate(-0.05f * sp, -0.03f * sp + Math.abs(run) * -0.02f * sp, 0.04f * sp);
            ps.mulPose(Axis.YP.rotationDegrees(30f * sp + run * 3f * sp));
            ps.mulPose(Axis.ZP.rotationDegrees(14f * sp + run * 4f * sp));
            ps.mulPose(Axis.XP.rotationDegrees(-10f * sp));
        }

        // ---- reload choreography: present the magazine well
        if (rl.tilt > 0) {
            ps.translate(-0.08f * rl.tilt, -0.01f * rl.tilt, 0.03f * rl.tilt);
            ps.mulPose(Axis.ZP.rotationDegrees(-24f * rl.tilt));
            ps.mulPose(Axis.YP.rotationDegrees(10f * rl.tilt));
            ps.mulPose(Axis.XP.rotationDegrees(6f * rl.tilt + rl.jolt * -4f));
        }
        // ---- bolt cycle (bolt-action): roll the rifle as the hand works the bolt
        float boltEnv = bolt > 0 ? Mth.sin(Math.min(1f, bolt) * Mth.PI) : 0f;
        ps.mulPose(Axis.ZP.rotationDegrees(12f * boltEnv));
        ps.translate(0, -0.015f * boltEnv, 0.01f * boltEnv);

        // ---- recoil: rearward punch, muzzle climb, a random roll per shot
        float kickBack = kick * (pistol ? 0.05f : 0.065f) * (1f - 0.4f * ads);
        float climb = kick * (type.category == WeaponCategory.SNIPER ? 8f : type == GunType.SG12 ? 9f : 3.2f) * (1f - 0.55f * ads);
        ps.translate(0, kick * 0.008f, kickBack);
        ps.mulPose(Axis.XP.rotationDegrees(climb));
        ps.mulPose(Axis.ZP.rotationDegrees(GunController.lastShotRoll * kick));

        // ---- arms (in gun space, unscaled)
        Faction faction = ClientState.myFaction();
        // right hand: on the pistol grip, or on the bolt during a bolt cycle
        Vector3f rightHand = gunPoint(8.6f, 6.3f, pistol ? 10.8f : 12.5f);
        if (type == GunType.SR338 && bolt > 0) {
            float b = Math.min(1f, bolt);
            float reach = Mth.clamp(b * 4f, 0, 1) * Mth.clamp((1f - b) * 4f, 0, 1);
            float pull = Mth.sin(Mth.clamp((b - 0.25f) * 2f, 0, 1) * Mth.PI);
            Vector3f boltPos = gunPoint(11.2f, 10.6f + pull * 0.6f, 12.7f + pull * 3.2f);
            rightHand.lerp(boltPos, reach);
        }
        ArmRenderer.draw(ps, buf, light, rightHand, new Vector3f(0.42f, -0.62f, 0.75f), faction, false);
        // left hand: handguard / pump, or the magazine during reloads
        Vector3f leftHand = leftHandRest(type, pump);
        if (rl.handOnMag > 0) leftHand.lerp(gunPoint(8f + rl.mag.x, 4.0f + rl.mag.y, 8.4f + rl.mag.z), rl.handOnMag);
        if (rl.handOnCharger > 0) leftHand.lerp(gunPoint(7.6f, 11.6f, 14.2f + rl.chargerPull * 1.6f), rl.handOnCharger);
        ArmRenderer.draw(ps, buf, light, leftHand, new Vector3f(-0.55f, -0.5f, 0.68f), faction, true);

        // ---- the weapon: body without magazine, then the magazine at its animated offset
        renderingMagless = true;
        try {
            mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(p, main, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false, ps, buf, light);
        } finally {
            renderingMagless = false;
        }
        if (rl.magVisible) {
            BakedModel magModel = mc.getModelManager().getModel(magModel(type));
            ps.pushPose();
            Vector3f off = type == GunType.SG12 ? new Vector3f(0, 0, pump * 2.4f) : rl.mag;
            ps.translate(off.x * S, off.y * S, off.z * S);
            if (rl.magSpin != 0) {
                // spin around the magazine's own centre as it drops
                Vector3f c = gunPoint(8f, 5f, 8.2f);
                ps.translate(c.x, c.y, c.z);
                ps.mulPose(Axis.XP.rotationDegrees(rl.magSpin));
                ps.translate(-c.x, -c.y, -c.z);
            }
            mc.getItemRenderer().render(main, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false, ps, buf, light, OverlayTexture.NO_OVERLAY, magModel);
            ps.popPose();
        }
        if (GunController.muzzleFlash > 0) renderFlash(ps, buf, type, p.getRandom().nextFloat(), GunController.muzzleFlash);
        ps.popPose();
    }

    /** Camera-space position of a point given in gun-model units (relative to the current pose). */
    private static Vector3f gunPoint(float x, float y, float z) {
        return new Vector3f((x - 8f) * S, (y - 8f) * S, (z - 8f) * S);
    }

    private static Vector3f leftHandRest(GunType type, float pump) {
        return switch (type) {
            case P12 -> gunPoint(7.2f, 6.2f, 11.2f);
            case SG12 -> gunPoint(7.6f, 7.0f, -2.5f + pump * 2.4f);
            case HMG240 -> gunPoint(7.6f, 6.8f, -2.0f);
            case SR338 -> gunPoint(7.6f, 6.4f, -1.0f);
            case CX9 -> gunPoint(7.6f, 6.6f, -0.3f);
            case KV12 -> gunPoint(7.6f, 6.4f, -1.5f);
            default -> gunPoint(7.6f, 6.6f, -2.0f);
        };
    }

    // ------------------------------------------------------------------ reload choreography
    private static final class Reload {
        float tilt, handOnMag, handOnCharger, chargerPull, magSpin, jolt;
        boolean magVisible = true;
        Vector3f mag = new Vector3f();
    }

    private static float seg(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0f, 1f);
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }

    /** Keyframed reload: tilt in, mag out and away, new mag from the pouch, seat it, (charge), tilt out. */
    private static Reload reload(ItemStack stack, GunType type, LocalPlayer p, float pt) {
        Reload r = new Reload();
        Long end = stack.get(ModComponents.RELOAD_END.get());
        if (end == null) return r;
        int len = stack.getOrDefault(ModComponents.RELOAD_LENGTH.get(), 40);
        float t = Mth.clamp(1f - (end - (p.level().getGameTime() + pt)) / len, 0, 1);
        boolean empty = len == type.stats.reloadEmptyTicks && type.stats.reloadEmptyTicks != type.stats.reloadTicks;
        float in = smooth(seg(t, 0f, 0.14f)), out = smooth(1f - seg(t, 0.86f, 1f));
        r.tilt = Math.min(in, out);
        if (type == GunType.SG12) {
            // shells fed one by one under the receiver
            float cycles = 4f;
            float c = (t - 0.15f) / 0.7f * cycles;
            if (t > 0.15f && t < 0.85f) {
                float ph = c - (float) Math.floor(c);
                r.handOnMag = Mth.sin(ph * Mth.PI);
                r.mag.set(0.6f, -2.5f + 3.2f * Mth.sin(ph * Mth.PI), 6.5f);
            }
            return r;
        }
        // drop direction follows the magazine well (pistol mags drop along the grip)
        Vector3f dropDir = type == GunType.P12 ? new Vector3f(0, -1f, 0.42f) : new Vector3f(-0.35f, -1f, 0.1f);
        float out1 = smooth(seg(t, 0.12f, 0.3f));         // old mag drops
        float fetch = smooth(seg(t, 0.32f, 0.56f));       // new mag comes up from the pouch
        float seat = smooth(seg(t, 0.56f, 0.66f));        // and is seated
        if (t < 0.32f) {
            r.mag.set(dropDir).mul(out1 * 16f);
            r.magSpin = out1 * 25f;
            r.handOnMag = smooth(seg(t, 0.08f, 0.16f)) * (1f - out1 * 0.6f);
            r.magVisible = out1 < 0.98f;
        } else if (t < 0.66f) {
            float far = 1f - fetch;
            r.mag.set(-1.2f * far, -14f * far - 2.2f * (1f - seat), 3f * far);
            r.mag.y += 2.2f * seat * (1f - fetch < 0.01f ? 1 : 0);
            r.handOnMag = 1f;
            r.jolt = seat > 0.9f ? 1f - seat : 0;
        } else {
            r.mag.set(0, 0, 0);
            r.handOnMag = 1f - smooth(seg(t, 0.66f, 0.74f));
            r.jolt = Math.max(0, 1f - (t - 0.66f) * 25f);
        }
        if (empty && type != GunType.SR338) {
            float go = smooth(seg(t, 0.72f, 0.78f)), back = 1f - smooth(seg(t, 0.84f, 0.9f));
            r.handOnCharger = Math.min(go, back);
            r.chargerPull = smooth(seg(t, 0.78f, 0.82f)) * (1f - smooth(seg(t, 0.82f, 0.85f)));
        }
        return r;
    }

    // ------------------------------------------------------------------ muzzle flash
    private static void renderFlash(PoseStack ps, MultiBufferSource buf, GunType type, float rnd, int ticks) {
        float muzzleZ = switch (type) {
            case AR17 -> -13.8f;
            case KV12 -> -14.8f;
            case CX9 -> -8.8f;
            case HMG240 -> -17.6f;
            case SR338 -> -23.2f;
            case M7 -> -17.8f;
            case SG12 -> -14.2f;
            case P12 -> 3.0f;
        };
        float muzzleY = type == GunType.P12 ? 9.85f : type == GunType.SR338 ? 9.35f : type == GunType.KV12 ? 9.4f : 9.9f;
        float big = switch (type.category) {
            case LMG, SNIPER, SHOTGUN -> 1.5f;
            case PISTOL -> 0.8f;
            default -> 1.1f;
        };
        ps.pushPose();
        ps.translate(0, (muzzleY - 8) * S, (muzzleZ - 8) * S - 0.01f);
        ps.mulPose(Axis.ZP.rotationDegrees(rnd * 360));
        float size = (0.06f + rnd * 0.05f) * big * (ticks > 1 ? 1f : 0.6f);
        PoseStack.Pose pose = ps.last();
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(FLASH));
        int lgt = 0xF000F0;
        quad(vc, pose, -size, -size, 0, size, -size, 0, size, size, 0, -size, size, 0, lgt);
        quad(vc, pose, 0, -size * 0.55f, 0, 0, -size * 0.55f, -size * 3.4f, 0, size * 0.55f, -size * 3.4f, 0, size * 0.55f, 0, lgt);
        quad(vc, pose, -size * 0.55f, 0, 0, -size * 0.55f, 0, -size * 3.4f, size * 0.55f, 0, -size * 3.4f, size * 0.55f, 0, 0, lgt);
        ps.popPose();
    }

    static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
                     float x2, float y2, float z2, float x3, float y3, float z3, int light) {
        float[][] v = {{x0, y0, z0, 0, 1}, {x1, y1, z1, 1, 1}, {x2, y2, z2, 1, 0}, {x3, y3, z3, 0, 0}};
        for (float[] a : v) vc.addVertex(pose, a[0], a[1], a[2]).setColor(255, 255, 255, 255).setUv(a[3], a[4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        for (int i = 3; i >= 0; i--) {
            float[] a = v[i];
            vc.addVertex(pose, a[0], a[1], a[2]).setColor(255, 255, 255, 255).setUv(a[3], a[4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
        }
    }

    private FirstPersonGun() {}
}
