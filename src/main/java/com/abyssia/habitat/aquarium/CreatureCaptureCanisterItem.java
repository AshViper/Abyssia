package com.abyssia.habitat.aquarium;

import com.abyssia.Abyssia;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * BT01g deep-sea creature capture canister. Empty (stacks to 16): right-click a living entity in
 * {@code #abyssia:aquarium_capturable} to catch it (the entity is removed; a wild catch is an adult). Filled (stack 1,
 * custom_data {@code Species}, {@code Adult}): right-click an aquarium to release it there, any other block to set it free in
 * the world; either way the empty canister comes back. The catch runs from the EntityInteract event so the hand slot
 * can be swapped safely (Player.interactOn would clear it after interactLivingEntity).
 */
public class CreatureCaptureCanisterItem extends Item
{
    public static final String TAG_SPECIES = "Species";
    public static final String TAG_ADULT = "Adult";
    private static final String MSG = "message." + Abyssia.MODID + ".aquarium.";

    private final boolean filled;

    public CreatureCaptureCanisterItem(boolean filled, Properties properties)
    {
        super(properties);
        this.filled = filled;
    }

    public static ItemStack filled(String species, boolean adult)
    {
        ItemStack stack = new ItemStack(AquariumContent.CANISTER_FILLED.get());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(TAG_SPECIES, species);
            tag.putBoolean(TAG_ADULT, adult);
        });
        return stack;
    }

    @Nullable
    public static String species(ItemStack stack)
    {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(TAG_SPECIES) ? tag.getString(TAG_SPECIES) : null;
    }

    public static boolean adult(ItemStack stack)
    {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return !tag.contains(TAG_ADULT) || tag.getBoolean(TAG_ADULT);
    }

    /** The registered entity type of a species id, or null. */
    @Nullable
    public static EntityType<?> type(String species)
    {
        ResourceLocation id = ResourceLocation.tryParse(species);
        return id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? BuiltInRegistries.ENTITY_TYPE.get(id) : null;
    }

    public static Component speciesName(String species)
    {
        EntityType<?> type = type(species);
        return type != null ? type.getDescription() : Component.literal(species);
    }

    /** Catch: before Entity.interact / Item.interactLivingEntity so nothing else touches the hand afterwards. */
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event)
    {
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (!held.is(AquariumContent.CANISTER.get())) return;
        if (!(event.getTarget() instanceof LivingEntity target) || target instanceof Player || !target.isAlive()) return;
        if (!target.getType().is(AquariumContent.CAPTURABLE)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        if (player.level().isClientSide) return;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        ItemStack full = filled(id.toString(), true);
        if (target.hasCustomName()) full.set(DataComponents.CUSTOM_NAME, target.getCustomName());
        target.playSound(SoundEvents.BUCKET_FILL_FISH, 1.0f, 1.0f);
        target.discard();
        player.setItemInHand(event.getHand(), ItemUtils.createFilledResult(held, player, full, false));
        player.displayClientMessage(Component.translatable(MSG + "captured", target.getType().getDescription()), true);
    }

    /** Filled canister on a block that is not an aquarium (the block handles that): set the creature free. */
    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        if (!filled) return InteractionResult.PASS;
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        String species = species(stack);
        EntityType<?> type = species == null ? null : type(species);
        if (type == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        Entity entity = type.create(level);
        if (entity == null) return InteractionResult.FAIL;
        entity.moveTo(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, level.random.nextFloat() * 360.0f, 0.0f);
        if (stack.has(DataComponents.CUSTOM_NAME)) entity.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
        if (entity instanceof Mob mob) mob.setPersistenceRequired();
        ((ServerLevel) level).addFreshEntity(entity);
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_FISH, SoundSource.NEUTRAL, 1.0f, 1.0f);
        Player player = context.getPlayer();
        if (player != null)
            player.setItemInHand(context.getHand(), ItemUtils.createFilledResult(stack, player, new ItemStack(AquariumContent.CANISTER.get()), false));
        else stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag)
    {
        String species = species(stack);
        if (filled && species != null)
        {
            tooltip.add(Component.translatable(MSG + "tooltip.species", speciesName(species)).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable(MSG + (adult(stack) ? "tooltip.adult" : "tooltip.juvenile")).withStyle(ChatFormatting.GRAY));
        }
        else if (!filled) tooltip.add(Component.translatable(MSG + "tooltip.empty").withStyle(ChatFormatting.GRAY));
    }
}
