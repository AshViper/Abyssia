package com.abyssia.item.scanner;

import com.abyssia.research.scan.PlayerScanState;
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
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * LS01 lidar scanner: hold right-click while looking at an ore deposit (OreDepositManager): the deposit under the crosshair is
 * reported (mineral, scale class, ore left, bearing and distance); the deposits around are NOT listed (user, 2026-10-09).
 * AB05: while held, the server scans the target under the crosshair (PlayerScanState, scan_targets data, MK0 distance 16) and
 * finishes it through ResearchManager.scan; the HUD is fed by ScanProgressPayload. Without a target the deposit under the crosshair (if any) is reported after SCAN_TICKS.
 */
public class LidarScannerItem extends Item
{
    public static final int SCAN_TICKS = 60, COOLDOWN_TICKS = 40, RADIUS = 128, MAX_RESULTS = 5;
    private static final String MSG = "message.abyssia.lidar_scanner.";

    public static DeferredItem<Item> LIDAR_SCANNER;

    public LidarScannerItem(Properties props) { super(props); }

    public static void register(DeferredRegister.Items items, List<DeferredItem<? extends Item>> tab)
    {
        LIDAR_SCANNER = items.register("lidar_scanner",
                () -> new LidarScannerItem(new Item.Properties().rarity(Rarity.RARE).stacksTo(1)));
        tab.add(LIDAR_SCANNER);
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
        int elapsed = getUseDuration(stack, entity) - remaining;
        if (level.isClientSide)
        {
            if (elapsed % 4 == 0)
            {
                Vec3 look = entity.getLookAngle();
                Vec3 at = entity.getEyePosition().add(look.scale(0.9)).add(0, -0.25, 0);
                level.addParticle(ParticleTypes.GLOW, at.x, at.y, at.z, look.x * 0.15, look.y * 0.15, look.z * 0.15);
            }
        }
        else if (entity instanceof ServerPlayer player)
        {
            if (elapsed % 12 == 0)
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CONDUIT_AMBIENT_SHORT,
                        SoundSource.PLAYERS, 0.5F, 1.0F + 0.6F * (elapsed % SCAN_TICKS) / SCAN_TICKS);
            if (PlayerScanState.tick(player) != PlayerScanState.Tick.RUNNING)
            {
                // scan complete (ResearchManager.scan ran) or nothing scannable for SCAN_TICKS: deposit list, cooldown, stop
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CONDUIT_ACTIVATE,
                        SoundSource.PLAYERS, 0.7F, 1.6F);
                reportDeposits(player, (ServerLevel) level);
                player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
                player.stopUsingItem();
            }
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft)
    {
        if (!level.isClientSide && entity instanceof ServerPlayer player) PlayerScanState.abort(player);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity)
    {
        if (!level.isClientSide && entity instanceof ServerPlayer player)
        {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CONDUIT_ACTIVATE,
                    SoundSource.PLAYERS, 0.7F, 1.6F);
            reportDeposits(player, (ServerLevel) level);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }
        return stack;
    }

    /** Report of the deposit under the crosshair only (chat and action bar); nothing about the deposits around. */
    public static void reportDeposits(ServerPlayer player, ServerLevel level)
    {
        Vec3 here = player.position();
        BlockPos pos = player.blockPosition();
        OreDeposit target = lookedAt(player, level);
        if (target == null)
        {
            player.sendSystemMessage(Component.translatable(MSG + "none").withStyle(ChatFormatting.GRAY));
            return;
        }
        Component line = Component.translatable(MSG + "target", entry(target, here, pos)).withStyle(ChatFormatting.GREEN);
        player.sendSystemMessage(line);
        player.displayClientMessage(line, true);
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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag)
    {
        lines.add(Component.translatable("tooltip.abyssia.lidar_scanner.use").withStyle(ChatFormatting.GRAY));
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }  // ends by scan completion / release
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged)
    {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }
}
