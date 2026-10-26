package com.kees.treekeseitor;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Configuración ya parseada (inmutable). */
public record Settings(
        ActivationMode mode,
        Set<String> disabledWorlds,
        boolean requireAxe,
        boolean workInCreative,
        int maxLogs,
        int maxRadius,
        int maxHeight,
        boolean onlyAbove,
        boolean sameSpeciesOnly,
        int minLeaves,
        int blocksPerTick,
        boolean protectTool,
        int minDurability,
        boolean replant,
        boolean leavesEnabled,
        int leavesMax,
        int leavesPerTick,
        boolean leavesDrops,
        DropMode dropMode,
        boolean effectsEnabled,
        boolean effectsSound,
        boolean effectsParticles,
        double effectsRadius,
        float effectsVolume,
        int effectsParticleCount,
        String soundLogs,
        String soundLeaves,
        String soundFinish
) {

    public enum ActivationMode { ALWAYS, SNEAK_DISABLES, SNEAK_ENABLES }

    /** ORIGIN = todo en el tronco base, PLAYER = al inventario, NATURAL = cada bloque suelta lo suyo. */
    public enum DropMode { ORIGIN, PLAYER, NATURAL }

    public static Settings load(FileConfiguration c) {
        ActivationMode mode;
        try {
            mode = ActivationMode.valueOf(
                    c.getString("activation-mode", "SNEAK_DISABLES").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            mode = ActivationMode.SNEAK_DISABLES;
        }
        DropMode dropMode;
        try {
            dropMode = DropMode.valueOf(
                    c.getString("drops.mode", "ORIGIN").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            dropMode = DropMode.ORIGIN;
        }
        return new Settings(
                mode,
                new HashSet<>(c.getStringList("disabled-worlds")),
                c.getBoolean("require-axe", true),
                c.getBoolean("work-in-creative", false),
                Math.max(1, c.getInt("max-logs", 200)),
                Math.max(1, c.getInt("max-radius", 10)),
                Math.max(1, c.getInt("max-height", 48)),
                c.getBoolean("only-above", true),
                c.getBoolean("same-species-only", true),
                Math.max(0, c.getInt("min-leaves", 5)),
                Math.max(0, c.getInt("blocks-per-tick", 20)),
                c.getBoolean("protect-tool", true),
                Math.max(0, c.getInt("min-durability", 2)),
                c.getBoolean("replant", false),
                c.getBoolean("leaves.enabled", true),
                Math.max(0, c.getInt("leaves.max", 500)),
                Math.max(0, c.getInt("leaves.per-tick", 40)),
                c.getBoolean("leaves.drops", true),
                dropMode,
                c.getBoolean("effects.enabled", true),
                c.getBoolean("effects.sound.enabled", true),
                c.getBoolean("effects.particles.enabled", true),
                Math.max(1.0, c.getDouble("effects.radius", 32.0)),
                (float) Math.max(0.0, c.getDouble("effects.sound.volume", 1.0)),
                Math.max(0, c.getInt("effects.particles.count", 12)),
                c.getString("effects.sound.logs", "block.wood.break"),
                c.getString("effects.sound.leaves", "block.grass.break"),
                c.getString("effects.sound.finish", "block.wood.fall")
        );
    }
}
