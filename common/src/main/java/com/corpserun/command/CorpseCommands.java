package com.corpserun.command;

import com.corpserun.ghost.CorpseLocation;
import com.corpserun.ghost.GhostData;
import com.corpserun.ghost.GhostManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /resurrect [accept]}, {@code /corpse resurrect [accept]} and {@code /corpse reset}, which silently do nothing
 * for players who are not ghosts, and the admin-only {@code /corpse fix <player>}.
 * <p>
 * Near the corpse, {@code /resurrect} brings the player back with the Invigorated protection. Anywhere else (the
 * corpse may be out of reach, e.g. on an airship that flew off) it warns first; {@code /resurrect accept} within
 * {@link #ACCEPT_WINDOW_TICKS} then resurrects them where they stand, unprotected.
 */
public final class CorpseCommands {

    private static final int RESET_COOLDOWN_TICKS = 5 * 20;
    /** How long {@code /resurrect accept} stays valid after the away-from-corpse warning. */
    private static final int ACCEPT_WINDOW_TICKS = 10 * 20;

    private CorpseCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("resurrect")
                .executes(ctx -> resurrect(ctx.getSource(), false))
                .then(Commands.literal("accept").executes(ctx -> resurrect(ctx.getSource(), true))));

        dispatcher.register(Commands.literal("corpse")
                .then(Commands.literal("resurrect")
                        .executes(ctx -> resurrect(ctx.getSource(), false))
                        .then(Commands.literal("accept").executes(ctx -> resurrect(ctx.getSource(), true))))
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

    /**
     * @param accept {@code /resurrect accept}: away from the corpse, goes ahead if the warning was given within the
     *               last {@link #ACCEPT_WINDOW_TICKS}; otherwise it just warns again
     */
    private static int resurrect(CommandSourceStack source, boolean accept) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !player.isAlive()) {
            return 0;
        }
        GhostData data = (GhostData) player;
        CorpseLocation corpse = data.corpserun$getCorpse();
        if (corpse == null) {
            return 0;
        }

        if (GhostManager.isInRange(player, corpse)) {
            GhostManager.resurrect(player);
            return 1;
        }

        long now = player.server.getTickCount();
        long promptUntil = data.corpserun$getResurrectPromptUntil();
        if (accept && promptUntil != 0L && now <= promptUntil) {
            GhostManager.resurrectInPlace(player);
            return 1;
        }

        data.corpserun$setResurrectPromptUntil(now + ACCEPT_WINDOW_TICKS);
        player.sendSystemMessage(awayFromCorpseWarning());
        return 0;
    }

    private static Component awayFromCorpseWarning() {
        Component command = Component.literal("/resurrect accept").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/resurrect accept"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatableWithFallback(
                        "corpserun.message.away_from_corpse.hover", "Resurrect here, without protection"))));
        return Component.translatableWithFallback("corpserun.message.away_from_corpse",
                "You aren't near your corpse, resurrecting will keep you here, but the spirit world will no longer keep you safe.. %s?",
                command).withStyle(ChatFormatting.YELLOW);
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

        CorpseLocation corpse = data.corpserun$getCorpse();
        if (corpse == null) {
            return 0;
        }
        GhostManager.ResetTarget target = GhostManager.resetTarget(player, corpse);
        Vec3 pos = target.pos();
        player.teleportTo(target.level(), pos.x, pos.y, pos.z, target.yRot(), target.xRot());
        player.resetFallDistance();
        data.corpserun$setLastReset(now);
        return 1;
    }
}
