package com.kees.treekeseitor;

import org.bukkit.Tag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;

public final class FellListener implements Listener {

    private final FellingService felling;

    public FellListener(FellingService felling) {
        this.felling = felling;
    }

    /**
     * MONITOR + ignoreCancelled: nos aseguramos de que protecciones (WorldGuard, claims, etc.)
     * ya aprobaron el primer tronco antes de talar el resto. Jobs también escucha en esta fase.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!Tag.LOGS.isTagged(event.getBlock().getType())) {
            return;
        }
        felling.tryFell(event.getPlayer(), event.getBlock());
    }

    /** Junta los drops del talado en un solo lugar (HIGHEST: después de otros plugins de drops). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        felling.captureDrops(event);
    }
}
