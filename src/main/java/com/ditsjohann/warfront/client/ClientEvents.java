package com.ditsjohann.warfront.client;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.client.render.FirstPersonGun;
import com.ditsjohann.warfront.client.render.Tracers;
import com.ditsjohann.warfront.client.screen.DeployScreen;
import com.ditsjohann.warfront.entity.TankEntity;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.registry.ModItems;
import com.ditsjohann.warfront.weapon.EffectiveStats;
import com.ditsjohann.warfront.weapon.GunItem;
import com.ditsjohann.warfront.weapon.GunLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus client hooks: input, camera, FOV, hand rendering, world rendering. */
@Mod.EventBusSubscriber(modid = Warfront.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        AutoTest.tick(mc);
        if (mc.player == null || mc.level == null) return;
        ClientState.tick();
        Tracers.tick();
        GunController.tick();
        while (KeyBinds.RELOAD.consumeClick()) WarfrontNet.toServer(new SimplePackets.Action(SimplePackets.Action.RELOAD));
        while (KeyBinds.FIRE_MODE.consumeClick()) WarfrontNet.toServer(new SimplePackets.Action(SimplePackets.Action.FIRE_MODE));
        while (KeyBinds.SPOT.consumeClick()) WarfrontNet.toServer(new SimplePackets.Action(SimplePackets.Action.SPOT));
        while (KeyBinds.DEPLOY.consumeClick()) {
            if (ClientState.inMatch()) WarfrontNet.toServer(new SimplePackets.Action(SimplePackets.Action.OPEN_DEPLOY));
        }
        if (ClientState.deployCountdown == 0 && mc.screen == null && ClientState.inMatch()) {
            ClientState.deployCountdown = -1;
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent.Pre event) {
        GunController.applyRecoil(event.getTimer().getRealtimeDeltaTicks());
    }

    /** Guns and tanks own the mouse buttons: cancel vanilla attack/use while holding one. */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        boolean gun = p.getMainHandItem().getItem() instanceof GunItem;
        if (gun || GunController.drivingTank() || ClientState.downed) {
            if (event.isAttack() || event.isUseItem()) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (ClientState.downed) {
            var in = event.getInput();
            in.forwardImpulse *= 0.25f;
            in.leftImpulse *= 0.25f;
            in.jumping = false;
            in.shiftKeyDown = false;
        } else if (GunController.ads > 0.5f) {
            // aiming slows you down
            var in = event.getInput();
            in.forwardImpulse *= 0.6f;
            in.leftImpulse *= 0.6f;
        }
    }

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        ItemStack s = p.getMainHandItem();
        float zoom = 1f;
        if (s.getItem() instanceof GunItem g) zoom = EffectiveStats.of(g.type, GunLogic.loadout(s, g.type)).zoom();
        else if (p.isUsingItem() && p.getUseItem().is(ModItems.FIELD_BINOCULARS.get())) zoom = 6f;
        float a = GunController.ease(GunController.ads);
        if (a > 0) event.setNewFovModifier(event.getFovModifier() * (1f / (1f + (zoom - 1f) * a)));
        // sprinting FOV stays vanilla; the gun model sells the sprint
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        float shake = ClientState.shake + ClientState.suppression * 0.25f;
        if (shake <= 0) return;
        double t = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + event.getPartialTick();
        event.setPitch(event.getPitch() + (float) (Math.sin(t * 2.7) * shake * 0.6));
        event.setYaw(event.getYaw() + (float) (Math.cos(t * 3.1) * shake * 0.5));
        event.setRoll(event.getRoll() + (float) (Math.sin(t * 1.9) * shake * 0.8));
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        FirstPersonGun.onRenderHand(event);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) Tracers.render(event);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        GunController.restoreSensitivity(Minecraft.getInstance());
        ClientState.reset();
        Tracers.clear();
    }

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        // respawn flow: if a deploy screen was requested while the death screen was up, show it next
        if (event.getNewScreen() == null && ClientState.deployCountdown > 0 && ClientState.inMatch()) {
            event.setNewScreen(new DeployScreen());
        }
    }

    static boolean inTank(LocalPlayer p) {
        return p.getVehicle() instanceof TankEntity;
    }

    private ClientEvents() {}
}
