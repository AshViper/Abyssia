package com.abyssia.vehicle;

import com.abyssia.Abyssia;
import net.minecraft.ChatFormatting;
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
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * SUB02 submarine item: right-click places one where the player looks (on the water / ground like a boat; under water
 * on the block in reach or 4 blocks ahead), facing the player's way. The energy rides in the "Energy" tag; a stack
 * without it (fresh from the crafting table) is full.
 */
public class SubmarineItem extends Item
{
    public static final String TAG_ENERGY = "Energy";
    public static final int BAR_COLOR = 0x00E5FF;

    public SubmarineItem(Properties properties)
    {
        super(properties);
    }

    public static int getEnergy(ItemStack stack)
    {
        int cap = Submarine.capacity();
        return stack.hasTag() && stack.getTag().contains(TAG_ENERGY) ? Mth.clamp(stack.getTag().getInt(TAG_ENERGY), 0, cap) : cap;
    }

    public static void setEnergy(ItemStack stack, int energy)
    {
        stack.getOrCreateTag().putInt(TAG_ENERGY, Math.max(0, energy));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        boolean underwater = player.isEyeInFluidType(ForgeMod.WATER_TYPE.get());
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
        return Math.round(13.0f * getEnergy(stack) / Math.max(1, Submarine.capacity()));
    }

    @Override
    public int getBarColor(ItemStack stack)
    {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag)
    {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        int energy = getEnergy(stack), cap = Submarine.capacity();
        lines.add(Component.translatable("tooltip." + Abyssia.MODID + ".submarine.energy", nf.format(energy), nf.format(cap))
                .withStyle(energy > 0 ? ChatFormatting.AQUA : ChatFormatting.RED));
        lines.add(Component.translatable("tooltip." + Abyssia.MODID + ".submarine.controls").withStyle(ChatFormatting.GRAY));
    }
}
