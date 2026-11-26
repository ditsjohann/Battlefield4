package com.ditsjohann.warfront.client.render;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.client.ClientState;
import com.ditsjohann.warfront.client.GunController;
import com.ditsjohann.warfront.registry.ModComponents;
import com.ditsjohann.warfront.weapon.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderHandEvent;

/**
 * First-person weapon presentation, rendered by hand instead of vanilla's item-in-hand pass so
 * the gun can aim down sights, kick, cycle bolts, tilt through reloads and lower while sprinting.
 */
public final class FirstPersonGun {
    private static final ResourceLocation FLASH = Warfront.id("textures/entity/muzzle_flash.png");
    /** Model units: the gun is rendered at 0.45 scale; one model unit = 1/16 block. */
    private static final float S = 0.45f / 16f;
    private static float sprint, sprintPrev;

    public static float sightHeight(GunType type, Attachment optic) {
        float base = switch (type) {
            case P12 -> 11.3f;
            case SG12 -> 11.2f;
            default -> 12.25f;
        };
        return switch (optic) {
            case RED_DOT -> 12.7f;
            case HOLO -> 13.0f;
            case PRISM_4X -> 13.1f;
            case SNIPER_8X -> 13.0f;
            default -> base;
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
        GunItem gun = (GunItem) main.getItem();
        GunType type = gun.type;
        Loadout loadout = GunLogic.loadout(main, type);
        float pt = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource buf = event.getMultiBufferSource();
        int light = event.getPackedLight();

        float ads = GunController.ease(Mth.lerp(pt, GunController.adsPrev, GunController.ads));
        float kick = Mth.lerp(pt, GunController.kickPrev, GunController.kick);
        sprintPrev = sprint;
        sprint = Mth.lerp(0.15f, sprint, p.isSprinting() && ads < 0.1f ? 1f : 0f);
        float sp = Mth.lerp(pt, sprintPrev, sprint);
        float reload = reloadCurve(main, p, pt);
        float bolt = GunController.boltTimer > 0 ? Mth.sin((20 - GunController.boltTimer + pt) / 20f * Mth.PI) : 0;
        float equip = event.getEquipProgress();

        float sight = sightHeight(type, loadout.optic());
        // hip and ADS anchor positions (camera space)
        float hx = 0.27f, hy = -0.30f, hz = -0.56f;
        float ax = 0f, ay = -(sight - 8f) * S, az = -0.34f;
        if (type.category == WeaponCategory.PISTOL) {
            hx = 0.24f;
            hy = -0.27f;
            hz = -0.48f;
            az = -0.38f;
        }
        ps.pushPose();
        ps.translate(Mth.lerp(ads, hx, ax), Mth.lerp(ads, hy, ay) - equip * 0.5f, Mth.lerp(ads, hz, az));
        // inertia from turning: subtle lag
        float yawLag = Mth.wrapDegrees(p.getViewYRot(pt) - Mth.lerp(pt, p.yBobO, p.yBob)) * 0.08f;
        float pitchLag = (p.getViewXRot(pt) - Mth.lerp(pt, p.xBobO, p.xBob)) * 0.08f;
        ps.mulPose(Axis.YP.rotationDegrees(-yawLag * (1 - ads * 0.8f)));
        ps.mulPose(Axis.XP.rotationDegrees(-pitchLag * (1 - ads * 0.8f)));
        // recoil kick: back, up and tilt
        ps.translate(0, kick * 0.01f, kick * 0.06f * (1 - 0.45f * ads));
        ps.mulPose(Axis.XP.rotationDegrees(kick * (type.category == WeaponCategory.SNIPER ? 9f : 3.5f) * (1 - 0.6f * ads)));
        // sprint carry
        ps.translate(-0.08f * sp, -0.06f * sp, 0.04f * sp);
        ps.mulPose(Axis.YP.rotationDegrees(38f * sp));
        ps.mulPose(Axis.ZP.rotationDegrees(-14f * sp));
        ps.mulPose(Axis.XP.rotationDegrees(-8f * sp));
        // reload: roll the gun, dip it
        ps.translate(0.02f * reload, -0.12f * reload, 0.05f * reload);
        ps.mulPose(Axis.XP.rotationDegrees(-22f * reload));
        ps.mulPose(Axis.ZP.rotationDegrees(28f * reload));
        // bolt cycle
        ps.mulPose(Axis.ZP.rotationDegrees(10f * bolt));
        ps.translate(0, -0.02f * bolt, 0.02f * bolt);

        renderArms(mc, p, ps, buf, light, type, ads, reload);
        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(p, main, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false, ps, buf, light);
        if (GunController.muzzleFlash > 0) renderFlash(ps, buf, type, loadout, p.getRandom().nextFloat());
        ps.popPose();
    }

    private static float reloadCurve(ItemStack stack, LocalPlayer p, float pt) {
        Long end = stack.get(ModComponents.RELOAD_END.get());
        if (end == null) return 0;
        int len = stack.getOrDefault(ModComponents.RELOAD_LENGTH.get(), 40);
        float t = 1f - (end - (p.level().getGameTime() + pt)) / len;
        t = Mth.clamp(t, 0, 1);
        // ease in, hold, ease out
        float in = Mth.clamp(t / 0.2f, 0, 1), out = Mth.clamp((1 - t) / 0.2f, 0, 1);
        return GunController.ease(Math.min(in, out));
    }

    /** Draws the player's arms gripping the weapon (pistol grip + handguard). */
    private static void renderArms(Minecraft mc, AbstractClientPlayer p, PoseStack ps, MultiBufferSource buf, int light, GunType type, float ads, float reload) {
        if (!(mc.getEntityRenderDispatcher().getRenderer(p) instanceof PlayerRenderer pr)) return;
        boolean pistol = type.category == WeaponCategory.PISTOL;
        // right hand on the pistol grip: grip sits around model (8, 6, 12)
        ps.pushPose();
        ps.translate(0, (6 - 8) * S, (12.5f - 8) * S);
        ps.mulPose(Axis.XP.rotationDegrees(-75));
        ps.mulPose(Axis.ZP.rotationDegrees(0));
        ps.translate(0.0f, 0.62f, 0.02f);
        ps.scale(0.55f, 0.85f, 0.55f);
        ps.translate(5f / 16f, 0, 0); // undo the arm pivot so the hand sits at the origin
        pr.renderRightHand(ps, buf, light, p);
        ps.popPose();
        // left hand on the handguard (moves to the magazine during reloads)
        float hz = pistol ? 11f : -1.5f;
        float hy = pistol ? 6.5f : 8.4f;
        hz = Mth.lerp(reload, hz, 7.5f);
        hy = Mth.lerp(reload, hy, 4.5f);
        ps.pushPose();
        ps.translate(-0.2f * S * 16 * 0.0f, (hy - 8) * S, (hz - 8) * S);
        ps.mulPose(Axis.YP.rotationDegrees(pistol ? 25 : 18));
        ps.mulPose(Axis.XP.rotationDegrees(-82));
        ps.translate(-0.02f, 0.66f, 0.0f);
        ps.scale(0.55f, 0.95f, 0.55f);
        ps.translate(-5f / 16f, 0, 0);
        pr.renderLeftHand(ps, buf, light, p);
        ps.popPose();
    }

    private static void renderFlash(PoseStack ps, MultiBufferSource buf, GunType type, Loadout l, float rnd) {
        float muzzleZ = switch (type) {
            case AR17 -> -12f;
            case KV12 -> -13f;
            case CX9 -> -8f;
            case HMG240 -> -16f;
            case SR338 -> -20f;
            case M7 -> -16f;
            case SG12 -> -12f;
            case P12 -> 3.5f;
        };
        float muzzleY = type == GunType.P12 ? 9.9f : type == GunType.SG12 ? 9.9f : 9.5f;
        ps.pushPose();
        ps.translate(0, (muzzleY - 8) * S, (muzzleZ - 8) * S - 0.02f);
        ps.mulPose(Axis.ZP.rotationDegrees(rnd * 360));
        float size = 0.07f + rnd * 0.05f;
        PoseStack.Pose pose = ps.last();
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(FLASH));
        int lgt = 0xF000F0;
        // two crossed quads facing the camera and from the side
        quad(vc, pose, -size, -size, 0, size, -size, 0, size, size, 0, -size, size, 0, lgt);
        quad(vc, pose, 0, -size * 0.6f, 0, 0, -size * 0.6f, -size * 3, 0, size * 0.6f, -size * 3, 0, size * 0.6f, 0, lgt);
        quad(vc, pose, -size * 0.6f, 0, 0, -size * 0.6f, 0, -size * 3, size * 0.6f, 0, -size * 3, size * 0.6f, 0, 0, lgt);
        ps.popPose();
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, int light) {
        vc.addVertex(pose, x0, y0, z0).setColor(255, 255, 255, 255).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        vc.addVertex(pose, x1, y1, z1).setColor(255, 255, 255, 255).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        vc.addVertex(pose, x2, y2, z2).setColor(255, 255, 255, 255).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        vc.addVertex(pose, x3, y3, z3).setColor(255, 255, 255, 255).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        // back face
        vc.addVertex(pose, x3, y3, z3).setColor(255, 255, 255, 255).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
        vc.addVertex(pose, x2, y2, z2).setColor(255, 255, 255, 255).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
        vc.addVertex(pose, x1, y1, z1).setColor(255, 255, 255, 255).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
        vc.addVertex(pose, x0, y0, z0).setColor(255, 255, 255, 255).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
    }

    static boolean unused() {
        return ClientState.downed;
    }

    private FirstPersonGun() {}
}
