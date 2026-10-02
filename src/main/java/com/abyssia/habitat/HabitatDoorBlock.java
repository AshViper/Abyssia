package com.abyssia.habitat;

import com.abyssia.Abyssia;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** Airlock door: opened by hand, iron door sounds. Doors never let water through, open or not. */
public class HabitatDoorBlock extends DoorBlock
{
    public static final BlockSetType TYPE = new BlockSetType(Abyssia.MODID + ":habitat", true, SoundType.METAL,
            SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
            SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
            SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON);

    public HabitatDoorBlock(Properties properties)
    {
        super(properties, TYPE);
    }
}
