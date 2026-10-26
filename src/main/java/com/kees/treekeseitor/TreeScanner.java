package com.kees.treekeseitor;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Busca los troncos y las hojas de un árbol y verifica que sea un árbol natural. */
final class TreeScanner {

    /** Resultado del escaneo. {@code logs} NO incluye el tronco de origen. */
    record Tree(List<Block> logs, List<Block> leaves) {
        static final Tree EMPTY = new Tree(List.of(), List.of());

        boolean isEmpty() {
            return logs.isEmpty();
        }
    }

    private record Node(Block block, int dist) {}

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private TreeScanner() {}

    static Tree scan(Block origin, Settings s) {
        World world = origin.getWorld();
        String species = species(origin.getType());
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();

        Set<Long> visited = new HashSet<>();
        Set<Long> leaves = new HashSet<>();
        List<Block> logs = new ArrayList<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();

        visited.add(pack(ox, oy, oz));
        queue.add(origin);
        boolean full = false;

        while (!queue.isEmpty() && !full) {
            Block cur = queue.poll();
            for (int dx = -1; dx <= 1 && !full; dx++) {
                for (int dy = -1; dy <= 1 && !full; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        int x = cur.getX() + dx, y = cur.getY() + dy, z = cur.getZ() + dz;
                        if (!visited.add(pack(x, y, z))) continue;
                        if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
                        // No cargar chunks nuevos por escanear.
                        if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

                        Block n = world.getBlockAt(x, y, z);
                        Material type = n.getType();

                        if (Tag.LOGS.isTagged(type)) {
                            if (s.sameSpeciesOnly() && !species(type).equals(species)) continue;
                            if (s.onlyAbove() && y < oy) continue;
                            if (Math.abs(x - ox) > s.maxRadius() || Math.abs(z - oz) > s.maxRadius()) continue;
                            if (y - oy > s.maxHeight()) continue;

                            logs.add(n);
                            queue.add(n);
                            if (logs.size() >= s.maxLogs()) {
                                full = true;
                                break;
                            }
                        } else if (isNaturalLeaf(n, type)) {
                            leaves.add(pack(x, y, z));
                        }
                    }
                }
            }
        }

        if (logs.isEmpty() || leaves.size() < s.minLeaves()) {
            return Tree.EMPTY;
        }

        logs.sort(Comparator.comparingDouble(b -> distSq(b, ox, oy, oz)));
        List<Block> leafBlocks = s.leavesEnabled() && s.leavesMax() > 0
                ? collectLeaves(origin, logs, s)
                : List.of();
        return new Tree(logs, leafBlocks);
    }

    /**
     * Recorre las hojas hacia afuera desde los troncos. Una hoja natural guarda su distancia al
     * tronco más cercano (1..7): solo tomamos las que están a exactamente +1 de la anterior, o sea,
     * las que dependen de ESTE árbol. Las hojas sostenidas por otro árbol quedan intactas.
     */
    private static List<Block> collectLeaves(Block origin, List<Block> logs, Settings s) {
        World world = origin.getWorld();
        Set<Long> seen = new HashSet<>();
        List<Block> result = new ArrayList<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();

        seen.add(pack(origin.getX(), origin.getY(), origin.getZ()));
        queue.add(new Node(origin, 0));
        for (Block log : logs) {
            seen.add(pack(log.getX(), log.getY(), log.getZ()));
            queue.add(new Node(log, 0));
        }

        while (!queue.isEmpty() && result.size() < s.leavesMax()) {
            Node node = queue.poll();
            int next = node.dist() + 1;
            if (next > 6) continue;

            for (BlockFace face : FACES) {
                Block n = node.block().getRelative(face);
                long key = pack(n.getX(), n.getY(), n.getZ());
                if (seen.contains(key)) continue;
                if (!world.isChunkLoaded(n.getX() >> 4, n.getZ() >> 4)) continue;

                Material type = n.getType();
                boolean ok;
                if (Tag.LEAVES.isTagged(type)) {
                    ok = n.getBlockData() instanceof Leaves l
                            && !l.isPersistent()
                            && l.getDistance() == next;
                } else if (isWart(type)) {
                    ok = next <= 4; // árboles del Nether: sin "distancia", limitamos por niveles
                } else {
                    ok = false;
                }
                if (!ok) continue;

                seen.add(key);
                result.add(n);
                queue.add(new Node(n, next));
                if (result.size() >= s.leavesMax()) break;
            }
        }
        return result;
    }

    /** "stripped_dark_oak_log" -> "dark_oak", "crimson_stem" -> "crimson", "oak_wood" -> "oak". */
    static String species(Material type) {
        String n = type.name().toLowerCase(Locale.ROOT);
        if (n.startsWith("stripped_")) n = n.substring("stripped_".length());
        for (String suffix : new String[]{"_log", "_wood", "_stem", "_hyphae"}) {
            if (n.endsWith(suffix)) {
                return n.substring(0, n.length() - suffix.length());
            }
        }
        return n;
    }

    static boolean isWart(Material type) {
        return type == Material.NETHER_WART_BLOCK || type == Material.WARPED_WART_BLOCK;
    }

    static boolean isNaturalLeaf(Block b, Material type) {
        if (isWart(type)) {
            return true; // "hojas" de los árboles del Nether
        }
        if (Tag.LEAVES.isTagged(type)) {
            // Las hojas puestas por jugadores son persistentes; las naturales no.
            return !(b.getBlockData() instanceof Leaves leaves) || !leaves.isPersistent();
        }
        return false;
    }

    private static double distSq(Block b, int x, int y, int z) {
        double dx = b.getX() - x, dy = b.getY() - y, dz = b.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static long pack(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL);
    }
}
