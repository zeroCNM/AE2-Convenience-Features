package com.ae2conveniencefeatures.wireless;

import java.util.concurrent.ArrayBlockingQueue;

import net.minecraft.entity.player.EntityPlayerMP;

import com.ae2conveniencefeatures.AE2ConvenienceFeatures;
import com.ae2conveniencefeatures.crafting.CompletedCraftsService;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** 1.7.10 has no assumed 1.8 IThreadListener API: drain a bounded queue on the real server tick. */
public final class ServerTasks {

    private static final ArrayBlockingQueue<Runnable> TASKS = new ArrayBlockingQueue<Runnable>(256);

    public static boolean submit(EntityPlayerMP player, Runnable task) {
        return TASKS.offer(() -> {
            if (player != null && !player.isDead
                && player.worldObj != null
                && player.worldObj.playerEntities.contains(player)) task.run();
        });
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (int i = 0; i < 64; i++) {
            Runnable task = TASKS.poll();
            if (task == null) break;
            execute(task);
        }
        execute(WirelessService::flushChanges);
        execute(CompletedCraftsService::tick);
    }

    static void execute(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException | LinkageError error) {
            // Optional/mod API linkage failures must not escape this mod's tick handler and
            // terminate the server. VM/resource errors are deliberately not swallowed.
            AE2ConvenienceFeatures.LOG.warn("Wireless request failed safely on the server thread", error);
        }
    }
}
