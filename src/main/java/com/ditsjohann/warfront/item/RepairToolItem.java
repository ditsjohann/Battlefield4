package com.ditsjohann.warfront.item;

import com.ditsjohann.warfront.combat.Teams;
import com.ditsjohann.warfront.entity.VehicleEntity;
import com.ditsjohann.warfront.progression.Scoring;
import com.ditsjohann.warfront.registry.ModComponents;
import com.ditsjohann.warfront.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Hold right-click on a vehicle to repair it. Overheats with sustained use. */
public class RepairToolItem extends GadgetItem {
    private static final Map<UUID, Float> REPAIRED = new WeakHashMap<>();

    public RepairToolItem() {
        super(0, "Hold right-click on a vehicle to repair it. Watch the heat gauge.");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (s.getOrDefault(ModComponents.HEAT.get(), 0) >= 100) return InteractionResultHolder.fail(s);
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel sl) || !(user instanceof Player p)) return;
        int heat = stack.getOrDefault(ModComponents.HEAT.get(), 0);
        if (heat >= 100) {
            p.stopUsingItem();
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.get("dry_fire"), SoundSource.PLAYERS, 0.8f, 0.6f);
            return;
        }
        VehicleEntity tank = target(p);
        if (tank == null) return;
        stack.set(ModComponents.HEAT.get(), heat + 1);
        if (tank.repair(0.45f)) {
            Vec3 at = tank.position().add(0, 1.2, 0);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 3, 0.8, 0.4, 0.8, 0.15);
            if (remaining % 12 == 0) sl.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.get("repair_loop"), SoundSource.PLAYERS, 0.6f, 1f);
            float total = REPAIRED.merge(p.getUUID(), 0.45f, Float::sum);
            if (total >= 10f && p instanceof ServerPlayer sp && Teams.allies(sp, tank)) {
                REPAIRED.put(p.getUUID(), 0f);
                Scoring.award(sp, 20, "VEHICLE REPAIR");
            }
        }
    }

    private static VehicleEntity target(Player p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(4.5));
        AABB box = new AABB(eye, end).inflate(1);
        VehicleEntity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : p.level().getEntities(p, box, e -> e instanceof VehicleEntity)) {
            if (e.getBoundingBox().inflate(0.3).clip(eye, end).isPresent() || e.getBoundingBox().inflate(0.3).contains(eye)) {
                double d = e.distanceToSqr(p);
                if (d < bd) {
                    bd = d;
                    best = (VehicleEntity) e;
                }
            }
        }
        return best;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity e, int slot, boolean selected) {
        if (level.isClientSide || level.getGameTime() % 4 != 0) return;
        int heat = stack.getOrDefault(ModComponents.HEAT.get(), 0);
        boolean using = e instanceof LivingEntity le && le.isUsingItem() && le.getUseItem() == stack;
        if (heat > 0 && !using) stack.set(ModComponents.HEAT.get(), Math.max(0, heat - 3));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.getOrDefault(ModComponents.HEAT.get(), 0) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * stack.getOrDefault(ModComponents.HEAT.get(), 0) / 100f);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xFF5A2A;
    }
}
