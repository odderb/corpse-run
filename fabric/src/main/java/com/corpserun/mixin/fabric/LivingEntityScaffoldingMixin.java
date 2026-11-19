package com.corpserun.mixin.fabric;

import com.corpserun.CorpseRun;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla hardcodes "sneaking inside scaffolding slides you down" to Blocks.SCAFFOLDING. Make ethereal blocks count
 * too. (NeoForge has an isScaffolding hook for this, which EtherealBlock overrides.)
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityScaffoldingMixin {

    @WrapOperation(method = "handleOnClimbable", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
    private boolean corpserun$etherealIsScaffolding(BlockState state, Block block, Operation<Boolean> original) {
        return original.call(state, block) || (block == Blocks.SCAFFOLDING && state.is(CorpseRun.ETHEREAL_BLOCK.get()));
    }
}
