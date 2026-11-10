package com.ae2conveniencefeatures.mixin.ae;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.ae2conveniencefeatures.client.crafting.RecentRepo;
import com.ae2conveniencefeatures.client.crafting.RecentTerminalClient;
import com.ae2conveniencefeatures.client.crafting.RecentTerminalLayout;
import com.ae2conveniencefeatures.crafting.RecentCraftHistory;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.slots.VirtualMEMonitorableSlot;
import appeng.client.gui.slots.VirtualMEPinSlot;
import appeng.client.gui.slots.VirtualMESlot;

/** Place recent rows above existing native pins using the same clickable virtual slots. */
@Mixin(value = GuiMEMonitorable.class, remap = false)
public abstract class RecentTerminalMixin extends AEBaseGui implements RecentTerminalLayout {

    @Shadow
    protected VirtualMEPinSlot[] pinSlots;

    @Shadow
    protected VirtualMEMonitorableSlot[] monitorableSlots;

    @Shadow
    private int perRow;

    @Shadow
    private void setScrollBar() {
        throw new AssertionError();
    }

    @Unique
    private final Map<VirtualMESlot, Integer> ae2cf$shifts = new IdentityHashMap<>();

    protected RecentTerminalMixin(Container container) {
        super(container);
    }

    @Override
    public void ae2cf$refreshLayout(boolean scrollToTop) {
        if (monitorableSlots == null || monitorableSlots.length == 0) return;
        setScrollBar();
        if (scrollToTop) getScrollBar().setCurrentScroll(0);
        ae2cf$positionSlots();
    }

    @Inject(method = "initGui()V", at = @At("HEAD"), remap = true)
    private void ae2cf$restorePositions(CallbackInfo callback) {
        for (Map.Entry<VirtualMESlot, Integer> shifted : ae2cf$shifts.entrySet()) {
            VirtualMESlot slot = shifted.getKey();
            slot.setY(slot.getY() - shifted.getValue());
        }
        ae2cf$shifts.clear();
    }

    @Inject(method = "initGui()V", at = @At("TAIL"), remap = true)
    private void ae2cf$initialized(CallbackInfo callback) {
        RecentRepo repo = RecentTerminalClient.repo(this);
        if (repo != null) repo.ae2cf$refresh();
        ae2cf$refreshLayout(false);
    }

    @Inject(method = "drawScreen(IIF)V", at = @At("HEAD"), remap = true)
    private void ae2cf$beforeDraw(int x, int y, float partialTick, CallbackInfo callback) {
        ae2cf$positionSlots();
    }

    @Inject(method = "mouseWheelEvent(III)Z", at = @At("RETURN"))
    private void ae2cf$scrolled(int x, int y, int wheel, CallbackInfoReturnable<Boolean> callback) {
        ae2cf$positionSlots();
    }

    @Unique
    private void ae2cf$positionSlots() {
        if (pinSlots == null || pinSlots.length == 0
            || monitorableSlots == null
            || monitorableSlots.length == 0
            || perRow <= 0) return;
        RecentRepo repo = RecentTerminalClient.repo(this);
        int prefix = repo == null ? 0 : repo.ae2cf$prefixSlots();
        if (prefix == 0 && ae2cf$shifts.isEmpty()) return;
        int scroll = getScrollBar().getCurrentScroll();
        int visibleRows = RecentCraftHistory.visibleRows(prefix, perRow, scroll, monitorableSlots.length);
        int monitorTop = Integer.MAX_VALUE;
        int pinTop = Integer.MAX_VALUE;
        for (VirtualMESlot slot : monitorableSlots) monitorTop = Math.min(monitorTop, ae2cf$baseY(slot));
        for (VirtualMESlot slot : pinSlots) pinTop = Math.min(pinTop, ae2cf$baseY(slot));
        int pinHeight = Math.max(0, monitorTop - pinTop);
        for (VirtualMESlot slot : monitorableSlots) {
            boolean recent = slot.getSlotIndex() + scroll * perRow < prefix;
            ae2cf$shift(slot, recent ? -pinHeight : 0);
        }
        for (VirtualMESlot slot : pinSlots) ae2cf$shift(slot, visibleRows * 18);
    }

    @Unique
    private int ae2cf$baseY(VirtualMESlot slot) {
        return slot.getY() - ae2cf$shifts.getOrDefault(slot, 0);
    }

    @Unique
    private void ae2cf$shift(VirtualMESlot slot, int amount) {
        int previous = ae2cf$shifts.getOrDefault(slot, 0);
        if (previous == amount) return;
        slot.setY(slot.getY() + amount - previous);
        if (amount == 0) ae2cf$shifts.remove(slot);
        else ae2cf$shifts.put(slot, amount);
    }
}
