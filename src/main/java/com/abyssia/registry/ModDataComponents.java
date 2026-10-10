package com.abyssia.registry;

import com.abyssia.Abyssia;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Item data components (1.20's stack NBT): the stored FE of the electric tools. */
public final class ModDataComponents
{
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE, Abyssia.MODID);

    /** Stored FE (1.20: the "Energy" int tag). */
    public static final Supplier<DataComponentType<Integer>> ENERGY = COMPONENTS.registerComponentType("energy",
            builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** SUB03 submarine item: installed upgrades {Hull, Battery, Thruster, Utility} = item id strings (1.20: the "Upgrades" tag). */
    public static final Supplier<DataComponentType<CompoundTag>> SUBMARINE_UPGRADES = COMPONENTS.registerComponentType("submarine_upgrades",
            builder -> builder.persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG));

    /** Dive tank oxygen left in seconds; absent = full tank. */
    public static final Supplier<DataComponentType<Integer>> OXYGEN = COMPONENTS.registerComponentType("oxygen",
            builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModDataComponents() {}

    public static void register(IEventBus modBus)
    {
        COMPONENTS.register(modBus);
    }
}
