package com.abyssia.habitat.relay;

import com.abyssia.Abyssia;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * WR01 test hooks:
 * <ul>
 *     <li>{@code /abyssia relay list}: every link (ends, distance, efficiency, state, recent delivered FE/t, endpoint
 *     kinds) and the unlinked relays of this level</li>
 *     <li>{@code /abyssia relay place <pos> [facing]}: places a registered relay (both halves, waterlogged where the
 *     cell is water) without the constructor; not recorded as a built unit (no constructor dismantle)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Abyssia.MODID)
public final class RelayCommand
{
    private RelayCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal(Abyssia.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("relay")
                        .then(Commands.literal("list").executes(RelayCommand::list))
                        .then(Commands.literal("place")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> place(ctx, "north"))
                                        .then(Commands.argument("facing", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("north", "east", "south", "west"), b))
                                                .executes(ctx -> place(ctx, StringArgumentType.getString(ctx, "facing"))))))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx)
    {
        List<String> lines = RelayNetwork.describe(ctx.getSource().getLevel());
        for (String line : lines) ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        return RelayNetwork.get(ctx.getSource().getLevel()).links().size();
    }

    private static int place(CommandContext<CommandSourceStack> ctx, String facingName) throws CommandSyntaxException
    {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        Direction facing = Direction.byName(facingName);
        if (facing == null || facing.getAxis().isVertical())
        {
            ctx.getSource().sendFailure(Component.literal("facing must be north / east / south / west"));
            return 0;
        }
        BlockPos up = pos.above();
        if (!RelayEntry.free(level.getBlockState(pos)) || !RelayEntry.free(level.getBlockState(up)))
        {
            ctx.getSource().sendFailure(Component.literal("Both cells must be air or water: " + pos.toShortString() + " / " + up.toShortString()));
            return 0;
        }
        level.setBlock(pos, RelayEntry.lower(facing, RelayEntry.wet(level, pos)), Block.UPDATE_ALL);
        level.setBlock(up, RelayEntry.upper(facing, RelayEntry.wet(level, up)), Block.UPDATE_ALL);
        ctx.getSource().sendSuccess(() -> Component.literal("Relay placed at " + pos.toShortString() + " facing " + facing.getName()), true);
        return 1;
    }
}
