package com.corpserun.block;

import com.corpserun.CorpseRun;
import com.corpserun.config.CorpseRunConfig;
import com.corpserun.ghost.GhostData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;

/**
 * Placement and cleanup of a ghost's ethereal blocks. Each ghost has at most {@code etherealBlockLimit}; placing
 * another removes the oldest, and all of them go when the ghost resurrects. Positions are saved with the player (for
 * the order and the limit) and in {@link EtherealBlockData} (which blocks are still wanted). Removing a block never
 * loads its chunk: blocks in unloaded chunks are only dropped from EtherealBlockData and clear themselves later.
 */
public final class EtherealBlocks {

    private EtherealBlocks() {
    }

    private static Deque<GlobalPos> placed(ServerPlayer player) {
        return ((GhostData) player).corpserun$getEtherealBlocks();
    }

    /** Ghost right-clicked a block face with an empty main hand: place one against that face. */
    public static InteractionResult tryPlace(ServerPlayer player, ServerLevel level, BlockHitResult hit) {
        int limit = CorpseRunConfig.etherealBlockLimit();
        if (!CorpseRunConfig.etherealBlocks() || limit <= 0) {
            return InteractionResult.FAIL;
        }

        BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
        // Air only: never replaces plants, fluids or anything else.
        if (!level.isInWorldBounds(pos) || !level.getBlockState(pos).isAir() || !level.mayInteract(player, pos)) {
            return InteractionResult.FAIL;
        }

        BlockState state = CorpseRun.ETHEREAL_BLOCK.get().defaultBlockState();
        level.setBlock(pos, state, 3);
        GlobalPos placedPos = GlobalPos.of(level.dimension(), pos);
        EtherealBlockData.get(player.server).add(placedPos);
        SoundType sound = state.getSoundType();
        // No client-side prediction for this placement, so the placing ghost gets the sound from here too.
        playForGhostViewers(player, sound.getPlaceSound(), (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, true);

        Deque<GlobalPos> placed = placed(player);
        // An older entry for this spot (its block was replaced and later cleared) would remove this new block when
        // it's evicted: drop it, so the spot is only listed once, as the newest.
        placed.remove(placedPos);
        placed.addLast(placedPos);
        while (placed.size() > limit) {
            remove(player.server, placed.pollFirst());
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Plays an ethereal block sound (placing) only for players who can see the ghost (other
     * ghosts, or everyone when ghostsVisibleToLiving is on), like the block itself: living players neither see nor
     * hear ethereal blocks.
     *
     * @param includeGhost also send it to the ghost itself; false when its own client already plays it
     */
    public static void playForGhostViewers(ServerPlayer ghost, SoundEvent sound, float volume, float pitch,
                                           double x, double y, double z, boolean includeGhost) {
        Holder<SoundEvent> event = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        double range = sound.getRange(volume);
        long seed = ghost.getRandom().nextLong();
        for (ServerPlayer listener : ghost.serverLevel().players()) {
            if ((listener != ghost || includeGhost) && CorpseRun.canSee(listener, ghost)
                    && listener.distanceToSqr(x, y, z) <= range * range) {
                listener.connection.send(new ClientboundSoundPacket(event, SoundSource.BLOCKS, x, y, z, volume, pitch, seed));
            }
        }
    }

    public static void removeAll(ServerPlayer player) {
        Deque<GlobalPos> placed = placed(player);
        while (!placed.isEmpty()) {
            remove(player.server, placed.pollFirst());
        }
    }

    /**
     * Releases a block. If its chunk is loaded it's cleared right away (if it still holds an ethereal block; it may
     * have been replaced since); otherwise it clears itself once its chunk loads again.
     */
    private static void remove(MinecraftServer server, GlobalPos pos) {
        EtherealBlockData.get(server).remove(pos);
        ServerLevel level = server.getLevel(pos.dimension());
        if (level == null) {
            return;
        }
        BlockPos blockPos = pos.pos();
        LevelChunk chunk = level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(blockPos.getX()),
                SectionPos.blockToSectionCoord(blockPos.getZ()));
        if (chunk != null && chunk.getBlockState(blockPos).is(CorpseRun.ETHEREAL_BLOCK.get())) {
            level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    public static ListTag save(Collection<GlobalPos> placed) {
        ListTag list = new ListTag();
        for (GlobalPos pos : placed) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", pos.dimension().location().toString());
            tag.putLong("Pos", pos.pos().asLong());
            list.add(tag);
        }
        return list;
    }

    public static void load(CompoundTag parent, String key, Deque<GlobalPos> into) {
        into.clear();
        ListTag list = parent.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ResourceKey<Level> dimension =
                    ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension")));
            into.addLast(GlobalPos.of(dimension, BlockPos.of(tag.getLong("Pos"))));
        }
    }

    public static Deque<GlobalPos> newDeque() {
        return new ArrayDeque<>();
    }
}
