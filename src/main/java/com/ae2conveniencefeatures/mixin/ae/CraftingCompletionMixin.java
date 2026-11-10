package com.ae2conveniencefeatures.mixin.ae;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.ae2conveniencefeatures.AE2ConvenienceFeatures;
import com.ae2conveniencefeatures.Config;
import com.ae2conveniencefeatures.crafting.CompletedCraftsService;
import com.ae2conveniencefeatures.wireless.StackKey;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;

/** Observe native completion after its remaining-task guard, without adding serializable CPU listeners. */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class CraftingCompletionMixin {

    @Shadow
    private boolean isComplete;

    @Unique
    private final Map<String, Map<StackKey, ItemStack>> ae2cf$owners = new LinkedHashMap<>();

    @Inject(method = "submitJob", at = @At("RETURN"))
    private void ae2cf$submitted(IGrid grid, ICraftingJob<?> job, BaseActionSource source, ICraftingRequester requester,
        CallbackInfoReturnable<ICraftingLink> callback) {
        if (callback.getReturnValue() == null) return;
        // submitJob can delegate to mergeJob and return the existing link. Clearing here
        // would erase earlier players; completion, cancellation and reset own the lifecycle.
        ae2cf$remember(job, source);
    }

    @Inject(method = "mergeJob", at = @At("RETURN"))
    private void ae2cf$merged(IGrid grid, ICraftingJob<?> job, BaseActionSource source, ICraftingRequester requester,
        CallbackInfoReturnable<ICraftingLink> callback) {
        if (callback.getReturnValue() != null) ae2cf$remember(job, source);
    }

    @Unique
    private void ae2cf$remember(ICraftingJob<?> job, BaseActionSource source) {
        if (!Config.completedCraftRows || !(source instanceof PlayerSource player)
            || !(job.getOutput() instanceof IAEItemStack output)) return;
        ItemStack item = output.getItemStack();
        ae2cf$owners.computeIfAbsent(player.player.getCommandSenderName(), ignored -> new LinkedHashMap<>())
            .put(new StackKey(item), item.copy());
    }

    @Inject(
        method = "completeJob()V",
        at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    private void ae2cf$completed(CallbackInfo callback) {
        CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;
        // isBusy() is already false once the last item returns. The native completion flag
        // flips only after its listeners, and therefore identifies this first completion.
        if (isComplete) return;
        try {
            if (ae2cf$owners.isEmpty()) {
                // A job restored from native CPU NBT still carries its source player and output.
                IAEStack<?> output = cpu.getFinalMultiOutput();
                if (output instanceof IAEItemStack item) {
                    CompletedCraftsService.completed(cpu.getGrid(), cpu.getSourcePlayer(), item.getItemStack());
                }
            } else {
                for (Map.Entry<String, Map<StackKey, ItemStack>> owner : ae2cf$owners.entrySet()) {
                    for (ItemStack item : owner.getValue()
                        .values()) {
                        CompletedCraftsService.completed(cpu.getGrid(), owner.getKey(), item);
                    }
                }
            }
        } catch (RuntimeException | LinkageError error) {
            AE2ConvenienceFeatures.LOG.warn("Could not record completed crafting display", error);
        } finally {
            ae2cf$owners.clear();
        }
    }

    @Inject(method = { "resetFinalOutput()V", "cancel()V" }, at = @At("HEAD"))
    private void ae2cf$reset(CallbackInfo callback) {
        ae2cf$owners.clear();
    }
}
