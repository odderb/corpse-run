package com.corpserun;

import com.corpserun.block.EtherealBlock;
import com.corpserun.config.CorpseRunConfig;
import com.corpserun.entity.CorpseEntity;
import com.corpserun.ghost.GhostCall;
import com.corpserun.ghost.GhostEffect;
import com.corpserun.ghost.GhostManager;
import com.corpserun.ghost.InvigoratedEffect;
import com.corpserun.platform.Services;
import com.corpserun.platform.services.IRegistryHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Common entry point and the mod's registered content. The entries are registered through
 * {@link Services#REGISTRY} when this class is initialised, which {@link #init()} triggers; the loaders call it from
 * their mod initialiser / constructor, while the registries are still open. The fields are never null.
 */
public final class CorpseRun {

    private static final IRegistryHelper REGISTRY = Services.REGISTRY;

    public static final Holder<MobEffect> GHOST = REGISTRY.registerForHolder(Registries.MOB_EFFECT, "ghost", GhostEffect::new);
    public static final Holder<MobEffect> INVIGORATED = REGISTRY.registerForHolder(Registries.MOB_EFFECT, "invigorated", InvigoratedEffect::new);
    public static final Supplier<EntityType<CorpseEntity>> CORPSE = REGISTRY.register(Registries.ENTITY_TYPE, "corpse", CorpseRun::createCorpseType);
    public static final Supplier<Block> ETHEREAL_BLOCK = REGISTRY.register(Registries.BLOCK, "ethereal_block",
            () -> new EtherealBlock(EtherealBlock.defaultProperties()));
    /** Icon-only item (the Ghost effect icon) for the Corpse Run advancement. Not in any creative tab. */
    public static final Supplier<Item> GHOST_ICON = REGISTRY.register(Registries.ITEM, "ghost_icon",
            () -> new Item(new Item.Properties().stacksTo(1)));
    /** Guiding footprint particles: a small heel dot and a bigger toe dot that points the way. */
    public static final Supplier<SimpleParticleType> FOOTPRINT_HEEL = REGISTRY.register(Registries.PARTICLE_TYPE, "footprint_heel", CorpseRun::createSimpleParticle);
    public static final Supplier<SimpleParticleType> FOOTPRINT_TOE = REGISTRY.register(Registries.PARTICLE_TYPE, "footprint_toe", CorpseRun::createSimpleParticle);
    /** Notes coming out of a singing ghost's mouth. */
    public static final Supplier<SimpleParticleType> SINGING = REGISTRY.register(Registries.PARTICLE_TYPE, "singing", CorpseRun::createSimpleParticle);
    /**
     * A ghost's left-click call clips, one sound each (so the server knows which one plays and how long it is),
     * in clip order; see {@link GhostCall#CLIP_COUNT}.
     */
    public static final List<Supplier<SoundEvent>> GHOST_CALL_SOUNDS = IntStream.range(0, GhostCall.CLIP_COUNT)
            .mapToObj(clip -> REGISTRY.<SoundEvent, SoundEvent>register(Registries.SOUND_EVENT, GhostCall.soundName(clip),
                    () -> SoundEvent.createVariableRangeEvent(Constants.id(GhostCall.soundName(clip)))))
            .toList();

    private CorpseRun() {
    }

    /** Registers the content (by initialising this class) and loads the server config. */
    public static void init() {
        CorpseRunConfig.load();
    }

    private static SimpleParticleType createSimpleParticle() {
        // The constructor is protected; the flag lets them show even with the "decreased"/"minimal" particle setting.
        return new SimpleParticleType(true) {
        };
    }

    private static EntityType<CorpseEntity> createCorpseType() {
        return EntityType.Builder.<CorpseEntity>of(CorpseEntity::new, MobCategory.MISC)
                .sized(0.9F, 0.35F)
                .clientTrackingRange(10)
                .updateInterval(20)
                .fireImmune()
                .build(Constants.id("corpse").toString());
    }

    /**
     * Whether {@code viewer} gets to see {@code entity}: ghosts are only shown to other ghosts unless the
     * {@code ghostsVisibleToLiving} config is on. Everything that isn't a ghost is visible as usual.
     */
    public static boolean canSee(Entity viewer, Entity entity) {
        return !GhostManager.isGhost(entity) || viewer == entity || GhostManager.isGhost(viewer)
                || CorpseRunConfig.ghostsVisibleToLiving();
    }

    /**
     * Blocks ghosts may still use: doors, trapdoors, fence gates, buttons and levers (iron doors and trapdoors still
     * need redstone). Checked on both sides.
     */
    public static boolean ghostCanUse(BlockState state) {
        Block block = state.getBlock();
        return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock;
    }
}
