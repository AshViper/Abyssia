package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import com.abyssia.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * SUB02 submarine item: right-click places one where the player looks (on the water / ground like a boat; under water
 * on the block in reach or 4 blocks ahead), facing the player's way. The energy rides in the abyssia:energy data
 * component (Forge 1.20: the "Energy" tag); a stack without it (fresh from the crafting table) is full.
 */
public class SubmarineItem extends Item
{
    public static final int BAR_COLOR = 0x00E5FF;

    public SubmarineItem(Properties properties)
    {
        super(properties);
    }

    public static int getEnergy(ItemStack stack)
    {
        int cap = capacity(stack);
        Integer energy = stack.get(ModDataComponents.ENERGY.get());
        return energy == null ? Submarine.capacity() : Mth.clamp(energy, 0, cap);
    }

    /** SUB03: installed upgrades {Hull, Battery, Thruster, Utility} (empty compound when none) */
    public static CompoundTag getUpgrades(ItemStack stack)
    {
        CompoundTag tag = stack.get(ModDataComponents.SUBMARINE_UPGRADES.get());
        return tag == null ? new CompoundTag() : tag.copy();
    }

    /** battery size of this stack (150,000 FE with a high-capacity battery installed) */
    public static int capacity(ItemStack stack)
    {
        return Submarine.capacity((SubmarineUpgrades.mask(getUpgrades(stack)) & SubmarineUpgrades.bit(SubmarineUpgrades.BATTERY)) != 0);
    }

    public static void setEnergy(ItemStack stack, int energy)
    {
        stack.set(ModDataComponents.ENERGY.get(), Math.max(0, energy));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        boolean underwater = player.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value());
        HitResult hit = getPlayerPOVHitResult(level, player, underwater ? ClipContext.Fluid.NONE : ClipContext.Fluid.ANY);
        Vec3 at;
        if (hit.getType() == HitResult.Type.BLOCK) at = hit.getLocation();
        else if (underwater) at = player.getEyePosition().add(player.getLookAngle().scale(4.0)).subtract(0.0, 1.1, 0.0);
        else return InteractionResultHolder.pass(stack);

        Submarine sub = VehicleContent.SUBMARINE.get().create(level);
        if (sub == null) return InteractionResultHolder.fail(stack);
        sub.setYRot(player.getYRot());
        sub.yRotO = player.getYRot();
        Vec3 back = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z).normalize().scale(1.6);
        boolean placed = false;
        for (Vec3 pos : new Vec3[]{at, at.subtract(back), at.add(0.0, 1.0, 0.0), at.subtract(back).add(0.0, 1.0, 0.0)})
        {
            sub.setPos(pos);
            if (level.noCollision(sub, sub.getBoundingBox()))
            {
                placed = true;
                break;
            }
        }
        if (!placed) return InteractionResultHolder.fail(stack);
        if (!level.isClientSide)
        {
            SubmarineUpgrades.load(getUpgrades(stack), sub.upgrades());   // first: the battery decides how much energy fits
            sub.setEnergy(getEnergy(stack));
            level.addFreshEntity(sub);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, sub.position());
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isBarVisible(ItemStack stack)
    {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack)
    {
        return Math.round(13.0f * getEnergy(stack) / Math.max(1, capacity(stack)));
    }

    @Override
    public int getBarColor(ItemStack stack)
    {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag)
    {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        int energy = getEnergy(stack), cap = capacity(stack);
        lines.add(Component.translatable("tooltip." + Abyssia.MODID + ".submarine.energy", nf.format(energy), nf.format(cap))
                .withStyle(energy > 0 ? ChatFormatting.AQUA : ChatFormatting.RED));
        CompoundTag installed = getUpgrades(stack);
        int mask = SubmarineUpgrades.mask(installed);
        for (int i = 0; i < SubmarineUpgrades.SLOTS; i++)
        {
            net.minecraft.resources.ResourceLocation id = (mask & SubmarineUpgrades.bit(i)) == 0 ? null
                    : net.minecraft.resources.ResourceLocation.tryParse(installed.getString(SubmarineUpgrades.KEYS[i]));
            if (id != null)
                lines.add(Component.translatable("tooltip." + Abyssia.MODID + ".submarine.upgrade",
                        Component.translatable("item." + id.getNamespace() + "." + id.getPath())).withStyle(ChatFormatting.BLUE));
        }
        lines.add(Component.translatable("tooltip." + Abyssia.MODID + ".submarine.controls").withStyle(ChatFormatting.GRAY));
    }
}
