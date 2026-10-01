package com.abyssia.thermal;

import com.abyssia.Abyssia;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.List;

/** {@code /abyssia ventfields [range]}: lists vent fields near the executing position (ops only; for testing). */
@EventBusSubscriber(modid = Abyssia.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ThermalVentCommand
{
    private ThermalVentCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal(Abyssia.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("ventfields")
                        .executes(ctx -> list(ctx, 1000))
                        .then(Commands.argument("range", IntegerArgumentType.integer(16, 10000))
                                .executes(ctx -> list(ctx, IntegerArgumentType.getInteger(ctx, "range"))))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx, int range)
    {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        List<ThermalVentField> fields = ThermalVentManager.fieldsNear(level, pos, range);
        ctx.getSource().sendSuccess(() -> Component.literal(fields.size() + " vent field(s) within " + range + " blocks"), false);
        for (ThermalVentField f : fields)
        {
            StringBuilder types = new StringBuilder();
            for (ThermalVentField.Vent v : f.vents()) types.append(' ').append(v.type().getSerializedName()).append('/').append(v.age().name().toLowerCase()).append(':').append(v.height()).append('@').append(v.x()).append(',').append(v.z());
            ctx.getSource().sendSuccess(() -> Component.literal(f.kind() + " at " + f.centerX() + " " + f.centerZ() + " r=" + f.radius()
                    + " vents=" + f.vents().size() + types), false);
        }
        return fields.size();
    }
}
