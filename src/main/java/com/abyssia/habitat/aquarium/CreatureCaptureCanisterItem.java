package com.abyssia.habitat.aquarium;

import com.abyssia.Abyssia;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * BT01g deep-sea creature capture canister. Empty (stacks to 16): right-click a living entity in
 * {@code #abyssia:aquarium_capturable} to catch it (the entity is removed; a wild catch is an adult). Filled (stack 1,
 * NBT {@code Species}, {@code Adult}): right-click an aquarium to release it there, any other block to set it free in
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
        stack.getOrCreateTag().putString(TAG_SPECIES, species);
        stack.getOrCreateTag().putBoolean(TAG_ADULT, adult);
        return stack;
    }

    @Nullable
    public static String species(ItemStack stack)
    {
        return stack.getTag() != null && stack.getTag().contains(TAG_SPECIES) ? stack.getTag().getString(TAG_SPECIES) : null;
    }

    public static boolean adult(ItemStack stack)
    {
        return stack.getTag() == null || !stack.getTag().contains(TAG_ADULT) || stack.getTag().getBoolean(TAG_ADULT);
    }

    /** The registered entity type of a species id, or null. */
    @Nullable
    public static EntityType<?> type(String species)
    {
        ResourceLocation id = ResourceLocation.tryParse(species);
        return id != null && ForgeRegistries.ENTITY_TYPES.containsKey(id) ? ForgeRegistries.ENTITY_TYPES.getValue(id) : null;
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
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        if (id == null) return;
        ItemStack full = filled(id.toString(), true);
        if (target.hasCustomName()) full.setHoverName(target.getCustomName());
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
        if (stack.hasCustomHoverName()) entity.setCustomName(stack.getHoverName());
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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
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
