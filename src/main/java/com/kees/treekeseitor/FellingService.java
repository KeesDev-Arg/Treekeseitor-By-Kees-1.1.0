package com.kees.treekeseitor;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class FellingService {

    private final TreekeseitorPlugin plugin;

    /** Talados en curso, uno por jugador (evita solaparlos). */
    private final Map<UUID, FellTask> tasks = new HashMap<>();

    /**
     * Jugadores a los que estamos rompiéndoles un tronco AHORA MISMO.
     * Sirve de guardia anti-recursión: Player#breakBlock dispara un BlockBreakEvent real
     * (necesario para Jobs y para las protecciones) que volvería a entrar en nuestro listener.
     */
    private final Set<UUID> breaking = new HashSet<>();

    public FellingService(TreekeseitorPlugin plugin) {
        this.plugin = plugin;
    }

    public void cancelAll() {
        for (FellTask task : new ArrayList<>(tasks.values())) {
            try {
                task.finish(false);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Error cerrando un talado: " + ex.getMessage());
            }
        }
        tasks.clear();
        breaking.clear();
    }

    public void tryFell(Player player, Block origin) {
        UUID id = player.getUniqueId();
        if (breaking.contains(id) || tasks.containsKey(id)) return;

        Settings s = plugin.settings();
        if (s.disabledWorlds().contains(origin.getWorld().getName())) return;
        if (!player.hasPermission("treekeseitor.use")) return;
        if (!plugin.isEnabledFor(player)) return;

        GameMode gm = player.getGameMode();
        if (gm == GameMode.SPECTATOR) return;
        if (gm == GameMode.CREATIVE && !s.workInCreative()) return;

        switch (s.mode()) {
            case SNEAK_DISABLES -> { if (player.isSneaking()) return; }
            case SNEAK_ENABLES -> { if (!player.isSneaking()) return; }
            case ALWAYS -> { }
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!toolAllowed(tool, s)) return;

        TreeScanner.Tree tree = TreeScanner.scan(origin, s);
        if (tree.isEmpty()) return;

        // ¿El tronco roto es la base del árbol? (para replantar)
        boolean wasBase = !Tag.LOGS.isTagged(origin.getRelative(BlockFace.DOWN).getType());
        String species = TreeScanner.species(origin.getType());

        FellTask task = new FellTask(player, origin, tree, s, wasBase, species);
        tasks.put(id, task);
        task.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Recoge los drops de los troncos que rompe el Treekeseitor (y del tronco original) para
     * soltarlos juntos al final. Además de ordenar, reduce muchísimo la cantidad de entidades
     * de ítem en el mundo (que es lo que más pesa).
     */
    public void captureDrops(BlockDropItemEvent event) {
        if (plugin.settings().dropMode() == Settings.DropMode.NATURAL) return;

        UUID id = event.getPlayer().getUniqueId();
        FellTask task = tasks.get(id);
        if (task == null) return;

        boolean ours = breaking.contains(id) || event.getBlock().equals(task.origin);
        if (!ours) return;

        for (Item item : event.getItems()) {
            task.buffer.add(item.getItemStack());
        }
        event.getItems().clear(); // lo que quitamos de la lista no se suelta
    }

    /**
     * UN sonido y UN estallido de partículas por tanda (no por bloque), solo para los jugadores
     * cercanos que no los hayan desactivado con /treekeseitor effects.
     */
    private void playEffects(Location loc, BlockData data, String soundKey, float basePitch) {
        Settings s = plugin.settings();
        if (!s.effectsEnabled() || loc == null || loc.getWorld() == null) return;
        boolean sound = s.effectsSound() && soundKey != null && !soundKey.isBlank();
        boolean particles = s.effectsParticles() && data != null && s.effectsParticleCount() > 0;
        if (!sound && !particles) return;

        float pitch = basePitch + ThreadLocalRandom.current().nextFloat() * 0.2f;
        for (Player p : loc.getWorld().getNearbyPlayers(loc, s.effectsRadius())) {
            if (!plugin.effectsEnabledFor(p)) continue;
            if (sound) {
                p.playSound(loc, soundKey, SoundCategory.BLOCKS, s.effectsVolume(), pitch);
            }
            if (particles) {
                p.spawnParticle(Particle.BLOCK, loc, s.effectsParticleCount(), 0.4, 0.4, 0.4, 0.0, data);
            }
        }
    }

    private static boolean toolAllowed(ItemStack tool, Settings s) {
        if (!s.requireAxe()) return true;
        return tool != null && Tag.ITEMS_AXES.isTagged(tool.getType());
    }

    private static boolean lowDurability(ItemStack tool, Settings s) {
        if (tool == null || !(tool.getItemMeta() instanceof Damageable d)) return false;
        int max = d.hasMaxDamage() ? d.getMaxDamage() : tool.getType().getMaxDurability();
        if (max <= 0) return false;
        return max - d.getDamage() <= s.minDurability();
    }

    /** Junta ítems iguales en pilas de hasta su máximo. */
    private static List<ItemStack> merge(List<ItemStack> items) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) continue;
            ItemStack rest = item.clone();
            for (ItemStack m : merged) {
                if (rest.getAmount() <= 0) break;
                if (m.isSimilar(rest) && m.getAmount() < m.getMaxStackSize()) {
                    int move = Math.min(m.getMaxStackSize() - m.getAmount(), rest.getAmount());
                    m.setAmount(m.getAmount() + move);
                    rest.setAmount(rest.getAmount() - move);
                }
            }
            if (rest.getAmount() > 0) merged.add(rest);
        }
        return merged;
    }

    private final class FellTask extends BukkitRunnable {
        private final Player player;
        private final Block origin;
        private final Iterator<Block> logIt;
        private final Iterator<Block> leafIt;
        private final Settings s;
        private final boolean wasBase;
        private final String species;
        private final List<ItemStack> buffer = new ArrayList<>();
        private boolean finished;
        private Location fxLoc;      // último bloque de la tanda (para los efectos)
        private BlockData fxData;

        FellTask(Player player, Block origin, TreeScanner.Tree tree, Settings s,
                 boolean wasBase, String species) {
            this.player = player;
            this.origin = origin;
            this.logIt = tree.logs().iterator();
            this.leafIt = tree.leaves().iterator();
            this.s = s;
            this.wasBase = wasBase;
            this.species = species;
        }

        @Override
        public void run() {
            if (!player.isOnline() || !player.getWorld().equals(origin.getWorld())) {
                finish(false);
                return;
            }

            // --- Fase 1: troncos ---
            int budget = s.blocksPerTick() <= 0 ? Integer.MAX_VALUE : s.blocksPerTick();
            while (budget-- > 0 && logIt.hasNext()) {
                ItemStack tool = player.getInventory().getItemInMainHand();
                if (!toolAllowed(tool, s)) { // cambió de ítem o se le rompió el hacha
                    finish(false);
                    return;
                }
                if (s.protectTool() && lowDurability(tool, s)) {
                    player.sendActionBar(plugin.msg("tool-low", "<yellow>Tu hacha casi se rompe."));
                    finish(false);
                    return;
                }

                Block b = logIt.next();
                if (!Tag.LOGS.isTagged(b.getType())) continue; // ya lo rompió alguien más

                fxLoc = b.getLocation().add(0.5, 0.5, 0.5);
                fxData = b.getBlockData();

                UUID id = player.getUniqueId();
                breaking.add(id);
                try {
                    // Dispara un BlockBreakEvent real (Jobs lo cuenta, las protecciones lo validan),
                    // aplica drops y daño a la herramienta como si el jugador lo hubiera picado.
                    player.breakBlock(b);
                } finally {
                    breaking.remove(id);
                }
            }
            if (fxLoc != null) {
                playEffects(fxLoc, fxData, s.soundLogs(), 0.9f);
                fxLoc = null;
                fxData = null;
            }
            if (logIt.hasNext()) return;

            // --- Fase 2: hojas ---
            int leafBudget = s.leavesPerTick() <= 0 ? Integer.MAX_VALUE : s.leavesPerTick();
            while (leafBudget-- > 0 && leafIt.hasNext()) {
                breakLeaf(leafIt.next());
            }
            if (fxLoc != null) {
                playEffects(fxLoc, fxData, s.soundLeaves(), 1.0f);
                fxLoc = null;
                fxData = null;
            }
            if (!leafIt.hasNext()) {
                finish(true);
            }
        }

        /** Simula la caída natural de una hoja (evento de decay + drops normales). */
        private void breakLeaf(Block b) {
            Material type = b.getType();
            if (!TreeScanner.isNaturalLeaf(b, type)) return; // ya no es una hoja natural

            LeavesDecayEvent decay = new LeavesDecayEvent(b);
            plugin.getServer().getPluginManager().callEvent(decay);
            if (decay.isCancelled()) return;

            fxLoc = b.getLocation().add(0.5, 0.5, 0.5);
            fxData = b.getBlockData();

            if (!s.leavesDrops()) {
                b.setType(Material.AIR);
            } else if (s.dropMode() == Settings.DropMode.NATURAL) {
                b.breakNaturally();
            } else {
                buffer.addAll(b.getDrops()); // saplings, palos, manzanas...
                b.setType(Material.AIR);
            }
        }

        void finish(boolean completed) {
            if (finished) return;
            finished = true;
            try {
                cancel();
            } catch (IllegalStateException ignored) {
                // la tarea ya no estaba programada
            }
            tasks.remove(player.getUniqueId());
            flush();
            if (completed) {
                playEffects(origin.getLocation().add(0.5, 0.5, 0.5), null, s.soundFinish(), 0.7f);
            }
            if (completed && s.replant() && wasBase) {
                replant();
            }
        }

        /** Suelta (o entrega) todo lo recogido, ya unido en pilas completas. */
        private void flush() {
            if (buffer.isEmpty()) return;
            List<ItemStack> stacks = merge(buffer);
            buffer.clear();

            World world = origin.getWorld();
            if (s.dropMode() == Settings.DropMode.PLAYER && player.isOnline()) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(stacks.toArray(new ItemStack[0]));
                for (ItemStack rest : leftover.values()) {
                    world.dropItem(player.getLocation(), rest);
                }
                return;
            }

            Location spot = origin.getLocation().add(0.5, 0.5, 0.5);
            for (ItemStack stack : stacks) {
                world.dropItem(spot, stack, item -> item.setVelocity(new Vector(0, 0, 0)));
            }
        }

        private void replant() {
            if (!origin.getType().isAir()) return;
            if (!Tag.DIRT.isTagged(origin.getRelative(BlockFace.DOWN).getType())) return;

            String name = species.equals("mangrove") ? "mangrove_propagule" : species + "_sapling";
            Material sapling = Material.matchMaterial(name);
            if (sapling == null || !sapling.isBlock()) return;

            BlockData data = sapling.createBlockData();
            if (origin.canPlace(data)) {
                origin.setBlockData(data);
            }
        }
    }
}
