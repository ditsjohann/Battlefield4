package com.ditsjohann.warfront;

import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.registry.*;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * WARFRONT — combined-arms Conquest warfare for Minecraft.
 *
 * <p>Registration lives in {@code registry}; gameplay systems are split by domain:
 * {@code weapon} (ballistics, guns), {@code entity} (soldiers, gadgets, tank),
 * {@code match} (Conquest rules, battlefield generation, deploy), {@code progression}
 * (ranks + unlocks) and {@code client} (HUD, first-person rendering, screens).
 */
@Mod(Warfront.MODID)
public class Warfront {
    public static final String MODID = "warfront";
    public static final Logger LOG = LogUtils.getLogger();

    public Warfront(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModComponents.REGISTER.register(modBus);
        ModSounds.REGISTER.register(modBus);
        ModBlocks.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);
        ModBlockEntities.REGISTER.register(modBus);
        ModEntities.REGISTER.register(modBus);
        ModCreativeTab.REGISTER.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(ModEntities::registerAttributes);
        context.registerConfig(ModConfig.Type.SERVER, WarfrontConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(WarfrontNet::register);
        LOG.info("WARFRONT online. Prepare for deployment.");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
