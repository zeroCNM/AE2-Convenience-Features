package com.ae2conveniencefeatures.mixin.ae;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.ae2conveniencefeatures.client.crafting.RecentRepo;
import com.ae2conveniencefeatures.client.crafting.RecentTerminalClient;
import com.ae2conveniencefeatures.client.crafting.RecentTerminalLayout;
import com.ae2conveniencefeatures.crafting.RecentCraftHistory;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.me.ItemRepo;
import appeng.util.item.AEItemStack;

/** Decorate only the display view. The native storage list and persistent pins are untouched. */
@Mixin(value = ItemRepo.class, remap = false)
public abstract class RecentItemRepoMixin implements RecentRepo {

    @Shadow
    @Final
    private ArrayList<IAEStack<?>> view;

    @Shadow
    @Final
    private IItemList<IAEStack<?>> list;

    @Shadow
    @Final
    private ISortSource sortSrc;

    @Shadow
    private int rowSize;

    @Shadow
    public abstract void updateView();

    @Unique
    private List<IAEStack<?>> ae2cf$normalView;

    @Unique
    private int ae2cf$prefixSlots;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2cf$register(IScrollSource scroll, ISortSource owner, CallbackInfo callback) {
        RecentTerminalClient.bindRepo(owner, this);
    }

    @Inject(method = "updateView()V", at = @At("HEAD"))
    private void ae2cf$restoreView(CallbackInfo callback) {
        // Native Shift-pause iterates its old view. Never let our null padding enter that path.
        if (ae2cf$normalView != null) {
            view.clear();
            view.addAll(ae2cf$normalView);
            ae2cf$normalView = null;
        }
        ae2cf$prefixSlots = 0;
    }

    @Inject(method = "updateView()V", at = @At("TAIL"))
    private void ae2cf$prepend(CallbackInfo callback) {
        List<IAEStack<?>> recent = new ArrayList<>();
        for (ItemStack item : RecentTerminalClient.items(sortSrc)) {
            IAEStack<?> stored = list.findPrecise(AEItemStack.create(item));
            if (stored != null && (stored.getStackSize() > 0 || stored.isCraftable())) recent.add(stored);
        }
        if (!recent.isEmpty()) {
            ae2cf$normalView = new ArrayList<>(view);
            ae2cf$prefixSlots = RecentCraftHistory.prepend(view, recent, rowSize);
        }
        if (sortSrc instanceof RecentTerminalLayout layout) layout.ae2cf$refreshLayout(false);
    }

    @Override
    public void ae2cf$refresh() {
        updateView();
    }

    @Override
    public int ae2cf$prefixSlots() {
        return ae2cf$prefixSlots;
    }
}
