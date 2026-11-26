package com.ditsjohann.warfront.client.render;

import com.ditsjohann.warfront.client.ClientState;
import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.entity.SoldierEntity;
import com.ditsjohann.warfront.entity.SoldierRole;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 3D load-bearing gear on soldiers and in-match players: combat helmet with goggles, plate
 * carrier with magazine pouches, a role-specific pack (medic bag, engineer tool roll, ammo can,
 * recon radio with whip antenna) and knee pads. Built from boxes in model-pixel units attached to
 * the humanoid's head/body/leg parts so it follows every animation.
 */
public class GearLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final float P = 1f / 16f;

    public GearLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, T e, float limbSwing, float limbSwingAmount, float pt,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (e.isInvisible()) return;
        Faction faction;
        SoldierRole role;
        if (e instanceof SoldierEntity s) {
            faction = s.getFaction();
            role = s.getRole();
        } else if (e instanceof Player p) {
            faction = ClientState.factionOf(p);
            if (faction == null) return; // only dress players taking part in a match
            role = roleFromWeapon(p);
        } else return;
        if (faction == null) faction = Faction.COALITION;
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(BoxDraw.PARTS));
        int ov = LivingEntityRenderer.getOverlayCoords(e, 0);
        M m = getParentModel();
        boolean legion = faction == Faction.LEGION;
        int shell = legion ? 0xFF3E4434 : 0xFF8C7A58;   // helmet / carrier base colour
        int webbing = legion ? 0xFF2E3328 : 0xFF6E6046;
        int dark = 0xFF2A2B2E;

        // ---- head: helmet, cover band, goggles or headset
        ps.pushPose();
        m.head.translateAndRotate(ps);
        PoseStack.Pose h = ps.last();
        if (role == SoldierRole.RECON) {
            // boonie hat
            b(h, vc, -4.4f, -8.8f, -4.4f, 4.4f, -7.4f, 4.4f, BoxDraw.OLIVE, shell, light, ov);
            b(h, vc, -5.6f, -7.6f, -5.6f, 5.6f, -7.1f, 5.6f, BoxDraw.OLIVE, shell, light, ov);
            b(h, vc, -4.3f, -5.2f, -4.45f, 4.3f, -4.6f, 4.45f, BoxDraw.RUBBER, dark, light, ov); // headset band
            b(h, vc, -4.9f, -4.6f, -1.1f, -4.2f, -2.4f, 1.1f, BoxDraw.POLYMER, dark, light, ov);
        } else {
            b(h, vc, -4.5f, -9.0f, -4.5f, 4.5f, -6.8f, 4.5f, BoxDraw.OLIVE, shell, light, ov);
            b(h, vc, -4.75f, -7.0f, -4.75f, 4.75f, -5.4f, 4.75f, BoxDraw.OLIVE, shell, light, ov);
            b(h, vc, -4.8f, -6.5f, -4.8f, 4.8f, -6.0f, 4.8f, BoxDraw.RUBBER, webbing, light, ov);      // cover band
            b(h, vc, -3.3f, -8.2f, -5.15f, 3.3f, -6.9f, -4.7f, BoxDraw.LENS, 0xFF9AA4AA, light, ov);  // goggles on the brow
            b(h, vc, -1.0f, -8.6f, -5.0f, 1.0f, -7.9f, -4.6f, BoxDraw.POLYMER, dark, light, ov);      // NVG mount
            b(h, vc, -4.95f, -4.8f, -1.0f, -4.5f, -3.0f, 1.0f, BoxDraw.POLYMER, dark, light, ov);     // ear protection
            b(h, vc, 4.5f, -4.8f, -1.0f, 4.95f, -3.0f, 1.0f, BoxDraw.POLYMER, dark, light, ov);
        }
        ps.popPose();

        // ---- body: plate carrier, pouches, pack
        ps.pushPose();
        m.body.translateAndRotate(ps);
        PoseStack.Pose bo = ps.last();
        b(bo, vc, -4.45f, 0.6f, -2.65f, 4.45f, 8.6f, 2.65f, BoxDraw.OLIVE, webbing, light, ov);         // carrier
        b(bo, vc, -3.6f, 1.2f, -2.95f, 3.6f, 7.6f, -2.6f, BoxDraw.OLIVE, shell, light, ov);              // front plate
        for (int i = 0; i < 3; i++) {
            float x0 = -3.5f + i * 2.4f;
            b(bo, vc, x0, 4.8f, -3.75f, x0 + 2.0f, 7.9f, -2.9f, BoxDraw.OLIVE, webbing, light, ov);    // mag pouches
            b(bo, vc, x0 + 0.3f, 4.2f, -3.55f, x0 + 1.7f, 4.8f, -3.1f, BoxDraw.POLYMER, dark, light, ov); // mag tops
        }
        b(bo, vc, -4.0f, 8.4f, -2.8f, 4.0f, 9.6f, 2.8f, BoxDraw.RUBBER, dark, light, ov);                 // belt
        b(bo, vc, 2.2f, 8.6f, -3.3f, 3.8f, 10.6f, -2.6f, BoxDraw.OLIVE, webbing, light, ov);             // grenade pouch
        b(bo, vc, -4.9f, 0.4f, -1.4f, -4.3f, 1.4f, 1.4f, BoxDraw.OLIVE, webbing, light, ov);             // shoulder straps
        b(bo, vc, 4.3f, 0.4f, -1.4f, 4.9f, 1.4f, 1.4f, BoxDraw.OLIVE, webbing, light, ov);
        switch (role) {
            case ASSAULT -> { // medic bag with a cross
                b(bo, vc, -3.4f, 1.6f, 2.6f, 3.4f, 7.8f, 5.2f, BoxDraw.OLIVE, shell, light, ov);
                b(bo, vc, -0.5f, 3.2f, 5.2f, 0.5f, 6.2f, 5.4f, BoxDraw.RED, 0xFFC83228, light, ov);
                b(bo, vc, -1.5f, 4.2f, 5.2f, 1.5f, 5.2f, 5.4f, BoxDraw.RED, 0xFFC83228, light, ov);
            }
            case ENGINEER -> { // tool roll + launcher tube slung diagonally
                b(bo, vc, -3.2f, 2.0f, 2.6f, 3.2f, 6.8f, 4.6f, BoxDraw.OLIVE, webbing, light, ov);
                ps.pushPose();
                ps.translate(0, 4 * P, 5.2f * P);
                ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-35));
                b(ps.last(), vc, -1.1f, -9.0f, -1.1f, 1.1f, 9.0f, 1.1f, BoxDraw.OLIVE, legion ? 0xFF4A5238 : 0xFF6F6A4E, light, ov);
                b(ps.last(), vc, -1.3f, -9.6f, -1.3f, 1.3f, -8.6f, 1.3f, BoxDraw.RUBBER, dark, light, ov);
                ps.popPose();
            }
            case SUPPORT -> { // ammo can and spare belt
                b(bo, vc, -3.6f, 1.4f, 2.6f, 3.6f, 8.0f, 5.6f, BoxDraw.OLIVE, webbing, light, ov);
                b(bo, vc, -2.6f, 6.0f, 5.6f, 2.6f, 9.4f, 7.6f, BoxDraw.TANK_GREEN, 0xFF5A6440, light, ov);
                b(bo, vc, -3.0f, 2.2f, -4.0f, -1.2f, 4.4f, -2.9f, BoxDraw.BRIGHT_STEEL, 0xFFD8B050, light, ov); // brass belt
            }
            case RECON -> { // radio with whip antenna
                b(bo, vc, -2.8f, 1.6f, 2.6f, 2.8f, 7.4f, 4.8f, BoxDraw.POLYMER, dark, light, ov);
                b(bo, vc, 1.6f, -9.0f, 3.6f, 2.0f, 1.6f, 4.0f, BoxDraw.RUBBER, dark, light, ov);
            }
        }
        ps.popPose();

        // ---- knee pads
        for (int side = 0; side < 2; side++) {
            ps.pushPose();
            (side == 0 ? m.rightLeg : m.leftLeg).translateAndRotate(ps);
            b(ps.last(), vc, -2.2f, 4.6f, -2.5f, 2.2f, 7.4f, -1.8f, BoxDraw.POLYMER, dark, light, ov);
            ps.popPose();
        }
    }

    private static void b(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                          int swatch, int tint, int light, int ov) {
        BoxDraw.box(pose, vc, x0 * P, y0 * P, z0 * P, x1 * P, y1 * P, z1 * P, swatch, tint, light, ov);
    }

    private static SoldierRole roleFromWeapon(Player p) {
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getItem(i).getItem() instanceof com.ditsjohann.warfront.weapon.GunItem g) {
                return switch (g.type.category) {
                    case LMG -> SoldierRole.SUPPORT;
                    case SNIPER -> SoldierRole.RECON;
                    case SHOTGUN, CARBINE -> SoldierRole.ENGINEER;
                    default -> SoldierRole.ASSAULT;
                };
            }
        }
        return SoldierRole.ASSAULT;
    }
}
