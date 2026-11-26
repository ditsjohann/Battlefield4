package com.ditsjohann.warfront.client.render;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.entity.SoldierEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;

/** Soldiers use the player model with procedurally painted faction/role uniforms. */
public class SoldierRenderer extends HumanoidMobRenderer<SoldierEntity, PlayerModel<SoldierEntity>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Warfront.id("soldier"), "main");

    public SoldierRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(LAYER), false), 0.5f);
        addLayer(new GearLayer<>(this));
    }

    public static LayerDefinition createLayer() {
        return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
    }

    @Override
    public ResourceLocation getTextureLocation(SoldierEntity e) {
        Faction f = e.getFaction();
        String fac = f == null ? "coalition" : f.skin;
        return Warfront.id("textures/entity/soldier/" + fac + "_" + e.getRole().skin + "_" + (e.getVariant() & 1) + ".png");
    }

    @Override
    public void render(SoldierEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        PlayerModel<SoldierEntity> m = getModel();
        boolean hasGun = !e.getMainHandItem().isEmpty();
        m.rightArmPose = e.isDowned() ? HumanoidModel.ArmPose.EMPTY : hasGun ? HumanoidModel.ArmPose.CROSSBOW_HOLD : HumanoidModel.ArmPose.EMPTY;
        m.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        m.crouching = e.getPose() == Pose.CROUCHING;
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override
    protected void setupRotations(SoldierEntity e, PoseStack ps, float bob, float bodyYaw, float pt, float scale) {
        super.setupRotations(e, ps, bob, bodyYaw, pt, scale);
        if (e.isDowned()) {
            // lying on the back, slightly twisted, slowly breathing
            ps.translate(0, 0.25, 0);
            ps.mulPose(Axis.XP.rotationDegrees(-88));
            ps.mulPose(Axis.ZP.rotationDegrees(12));
            ps.translate(0, -0.9, 0);
            float breath = (float) Math.sin((e.tickCount + pt) * 0.15) * 0.01f;
            ps.scale(1 + breath, 1, 1 + breath);
        }
    }

    @Override
    protected boolean shouldShowName(SoldierEntity e) {
        return super.shouldShowName(e) && !e.isDowned();
    }
}
