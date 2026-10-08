package com.abyssia.item.scanner;

import com.abyssia.progress.WreckProgress;
import com.abyssia.registry.ModWrecks;
import com.abyssia.worldgen.deposit.OreDeposit;
import com.abyssia.worldgen.deposit.OreDepositManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * LS01 lidar scanner: hold right-click for {@link #SCAN_TICKS}, then the ore deposits (OreDepositManager) within
 * {@link #RADIUS} blocks are reported in chat, nearest first: mineral, scale class, ore left, bearing and distance.
 * WRK01: a wreck core under the crosshair is analysed (WreckProgress); enough of them unlock the habitat constructor.
 */
public class LidarScannerItem extends Item
{
    public static final int SCAN_TICKS = 60, COOLDOWN_TICKS = 40, RADIUS = 128, MAX_RESULTS = 5;
    private static final String MSG = "message.abyssia.lidar_scanner.";

    public LidarScannerItem(Properties props) { super(props); }

    public static void register(DeferredRegister<Item> items, List<RegistryObject<? extends Item>> tab)
    {
        tab.add(items.register("lidar_scanner",
                () -> new LidarScannerItem(new Item.Properties().rarity(Rarity.RARE).stacksTo(1))));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining)
    {
        int elapsed = SCAN_TICKS - remaining;
        if (level.isClientSide)
        {
            if (elapsed % 4 == 0)
            {
                Vec3 look = entity.getLookAngle();
                Vec3 at = entity.getEyePosition().add(look.scale(0.9)).add(0, -0.25, 0);
                level.addParticle(ParticleTypes.GLOW, at.x, at.y, at.z, look.x * 0.15, look.y * 0.15, look.z * 0.15);
            }
        }
        else if (elapsed % 12 == 0)
        {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CONDUIT_AMBIENT_SHORT,
                    SoundSource.PLAYERS, 0.5F, 1.0F + 0.6F * elapsed / SCAN_TICKS);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity)
    {
        if (!level.isClientSide && entity instanceof ServerPlayer player)
        {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CONDUIT_ACTIVATE,
                    SoundSource.PLAYERS, 0.7F, 1.6F);
            reportDeposits(player, (ServerLevel) level);
            reportWreck(player, (ServerLevel) level);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }
        return stack;
    }

    /**
     * Chat report: the deposit under the crosshair first (also on the action bar), then the nearest other deposits that
     * still hold ore (horizontal distance within {@link #RADIUS}).
     */
    public static void reportDeposits(ServerPlayer player, ServerLevel level)
    {
        Vec3 here = player.position();
        BlockPos pos = player.blockPosition();
        OreDeposit target = lookedAt(player, level);
        List<OreDeposit> found = OreDepositManager.findNearby(level, pos, RADIUS).stream()
                .filter(d -> d.remainingOre() > 0 && horizontal(here, d.center()) <= RADIUS
                        && (target == null || !d.depositId().equals(target.depositId())))
                .sorted(Comparator.comparingDouble(d -> d.center().distSqr(pos)))
                .limit(MAX_RESULTS).toList();
        if (target != null)
        {
            Component line = Component.translatable(MSG + "target", entry(target, here, pos)).withStyle(ChatFormatting.GREEN);
            player.sendSystemMessage(line);
            player.displayClientMessage(line, true);
        }
        if (found.isEmpty())
        {
            if (target == null)
                player.sendSystemMessage(Component.translatable(MSG + "none", RADIUS).withStyle(ChatFormatting.GRAY));
            return;
        }
        player.sendSystemMessage(Component.translatable(MSG + "header", found.size()).withStyle(ChatFormatting.AQUA));
        for (OreDeposit d : found)
            player.sendSystemMessage(entry(d, here, pos).copy().withStyle(ChatFormatting.GRAY));
    }

    /** WRK01: analyses the wreck core the crosshair rests on (same reach as {@link #lookedAt}). */
    public static void reportWreck(ServerPlayer player, ServerLevel level)
    {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(RADIUS));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS || !level.getBlockState(hit.getBlockPos()).is(ModWrecks.WRECK_CORE.get())) return;
        BlockPos core = hit.getBlockPos();
        int need = WreckProgress.required();
        if (!WreckProgress.add(player, core))
        {
            player.sendSystemMessage(Component.translatable("message.abyssia.wreck.known").withStyle(ChatFormatting.GRAY));
            return;
        }
        int n = WreckProgress.count(player);
        player.sendSystemMessage(Component.translatable("message.abyssia.wreck.analyzed", n, need).withStyle(ChatFormatting.GREEN));
        level.sendParticles(ParticleTypes.GLOW, core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5, 20, 0.7, 0.7, 0.7, 0.05);
        level.playSound(null, core, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.3F);
        if (n == need) player.sendSystemMessage(Component.translatable("message.abyssia.wreck.unlocked").withStyle(ChatFormatting.AQUA));
    }

    /** The deposit whose region contains the first solid block along the look vector (null: looking at nothing of one). */
    @Nullable
    public static OreDeposit lookedAt(ServerPlayer player, ServerLevel level)
    {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(RADIUS));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return null;
        Vec3 at = hit.getLocation();
        return OreDepositManager.findNearby(level, hit.getBlockPos(), 2).stream()
                .filter(d -> d.bounds().inflate(1.0).contains(at))
                .min(Comparator.comparingDouble(d -> d.center().distSqr(hit.getBlockPos()))).orElse(null);
    }

    /** One report line: mineral, scale class, ore left, bearing, distance, height difference. */
    private static Component entry(OreDeposit d, Vec3 here, BlockPos pos)
    {
        Component mineral = BuiltInRegistries.BLOCK.get(d.mineralId()).getName();
        Component size = Component.translatable(MSG + "size." + d.size().getSerializedName());
        int percent = Math.round(100.0F * d.remainingOre() / Math.max(1, d.totalOre()));
        return Component.translatable(MSG + "entry", mineral.copy().withStyle(ChatFormatting.WHITE), size,
                d.remainingOre(), d.totalOre(), percent, bearing(here, d.center()), Math.round(horizontal(here, d.center())),
                signed(d.center().getY() - pos.getY()));
    }

    private static double horizontal(Vec3 from, BlockPos to)
    {
        return Math.hypot(to.getX() + 0.5 - from.x, to.getZ() + 0.5 - from.z);
    }

    /** 8-way compass word (north = -Z). */
    private static Component bearing(Vec3 from, BlockPos to)
    {
        double angle = Math.toDegrees(Math.atan2(to.getX() + 0.5 - from.x, -(to.getZ() + 0.5 - from.z)));
        int index = Mth.floor((angle + 360.0 + 22.5) / 45.0) % 8;
        return Component.translatable(MSG + "dir." + index);
    }

    private static String signed(int v) { return v > 0 ? "+" + v : Integer.toString(v); }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag)
    {
        lines.add(Component.translatable("tooltip.abyssia.lidar_scanner.use").withStyle(ChatFormatting.GRAY));
    }

    @Override public int getUseDuration(ItemStack stack) { return SCAN_TICKS; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged)
    {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer)
    {
        consumer.accept(LidarScannerClient.EXTENSIONS);
    }
}
