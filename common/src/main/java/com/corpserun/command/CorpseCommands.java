package com.corpserun.command;

import com.corpserun.ghost.CorpseLocation;
import com.corpserun.ghost.GhostData;
import com.corpserun.ghost.GhostManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /resurrect}, {@code /corpse resurrect} and {@code /corpse reset}, which silently do nothing for players who
 * are not ghosts, and the admin-only {@code /corpse fix <player>}.
 */
public final class CorpseCommands {

    private static final int RESET_COOLDOWN_TICKS = 5 * 20;

    private CorpseCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("resurrect")
                .executes(ctx -> resurrect(ctx.getSource())));

        dispatcher.register(Commands.literal("corpse")
                .then(Commands.literal("resurrect").executes(ctx -> resurrect(ctx.getSource())))
                .then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource())))
                .then(Commands.literal("fix")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> fix(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))));
    }

    /**
     * For admins fixing a stuck ghost (a bug, an unreachable corpse): resurrects them right where they stand,
     * however far from their corpse. Needs permission level 2 (operators, command blocks, the console).
     */
    private static int fix(CommandSourceStack source, ServerPlayer target) {
        if (!target.isAlive()) {
            source.sendFailure(Component.translatableWithFallback("corpserun.command.fix.dead",
                    "%s is dead; fix them once they have respawned", target.getDisplayName()));
            return 0;
        }
        if (!GhostManager.forceResurrect(target)) {
            source.sendFailure(Component.translatableWithFallback("corpserun.command.fix.not_ghost",
                    "%s is not a ghost", target.getDisplayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatableWithFallback("corpserun.command.fix.success",
                "Resurrected %s where they stand", target.getDisplayName()), true);
        return 1;
    }

    private static int resurrect(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !player.isAlive()) {
            return 0;
        }
        CorpseLocation corpse = ((GhostData) player).corpserun$getCorpse();
        if (corpse == null) {
            return 0;
        }

        if (!GhostManager.isInRange(player, corpse)) {
            GhostManager.showStatus(player, Component.translatableWithFallback(
                    "corpserun.message.too_far", "Too far from your corpse to resurrect").withStyle(ChatFormatting.RED));
            return 0;
        }

        GhostManager.resurrect(player);
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !player.isAlive() || !GhostManager.isGhost(player)) {
            return 0;
        }

        GhostData data = (GhostData) player;
        long now = player.server.getTickCount();
        long lastReset = data.corpserun$getLastReset();
        if (lastReset != 0L && now - lastReset >= 0 && now - lastReset < RESET_COOLDOWN_TICKS) {
            long secondsLeft = (RESET_COOLDOWN_TICKS - (now - lastReset) + 19) / 20;
            player.sendSystemMessage(Component.translatableWithFallback(
                    "corpserun.message.reset_cooldown", "Wait %ss before using /corpse reset again", secondsLeft)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // keepInventory=true here only means "don't consume a respawn anchor charge".
        DimensionTransition target = player.findRespawnPositionAndUseSpawnBlock(true, DimensionTransition.DO_NOTHING);
        Vec3 pos = target.pos();
        player.teleportTo(target.newLevel(), pos.x, pos.y, pos.z, target.yRot(), target.xRot());
        player.resetFallDistance();
        data.corpserun$setLastReset(now);
        return 1;
    }
}
