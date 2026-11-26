package com.ditsjohann.warfront.client;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.client.render.*;
import com.ditsjohann.warfront.combat.Teams;
import com.ditsjohann.warfront.registry.ModBlockEntities;
import com.ditsjohann.warfront.registry.ModEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import com.ditsjohann.warfront.registry.ModItems;
import com.ditsjohann.warfront.weapon.GunLogic;
import com.ditsjohann.warfront.weapon.GunType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Mod-bus client registration: renderers, keys and HUD layers. */
@Mod.EventBusSubscriber(modid = Warfront.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WarfrontClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        Teams.clientPlayerLookup = ClientState::factionOf;
        event.enqueueWork(() -> {
            for (GunType t : GunType.values()) {
                ItemProperties.register(ModItems.gun(t), Warfront.id("optic"),
                        (stack, level, entity, seed) -> GunLogic.loadout(stack, t).optic().ordinal() / 10f);
                ItemProperties.register(ModItems.gun(t), Warfront.id("suppressed"),
                        (stack, level, entity, seed) -> GunLogic.loadout(stack, t).suppressed() ? 1f : 0f);
            }
        });
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBinds.RELOAD);
        event.register(KeyBinds.FIRE_MODE);
        event.register(KeyBinds.SPOT);
        event.register(KeyBinds.DEPLOY);
        event.register(KeyBinds.SCOREBOARD);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SOLDIER.get(), SoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.TANK.get(), TankRenderer::new);
        event.registerEntityRenderer(ModEntities.FRAG_GRENADE.get(), ctx -> new ThrownItemRenderer<>(ctx, 0.8f, true));
        event.registerEntityRenderer(ModEntities.ROCKET.get(), RocketRenderer::new);
        event.registerEntityRenderer(ModEntities.C4.get(), GadgetRenderers.C4::new);
        event.registerEntityRenderer(ModEntities.SUPPLY_BAG.get(), GadgetRenderers.Bag::new);
        event.registerEntityRenderer(ModEntities.SPAWN_BEACON.get(), GadgetRenderers.Beacon::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CAPTURE_POINT.get(), FlagRenderers.CapturePoint::new);
        event.registerBlockEntityRenderer(ModBlockEntities.HQ_BEACON.get(), FlagRenderers.Hq::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SoldierRenderer.LAYER, SoldierRenderer::createLayer);
    }

    @SubscribeEvent
    public static void registerOverlays(AddGuiOverlayLayersEvent event) {
        ForgeLayeredDraw draw = event.getLayeredDraw();
        draw.addConditionTo(ForgeLayeredDraw.PRE_SLEEP_STACK, ForgeLayeredDraw.CROSSHAIR,
                () -> !GunController.holdingGun() && !GunController.drivingTank());
        draw.add(Warfront.id("hud"), HudRenderer::render);
    }

    private WarfrontClient() {}
}
