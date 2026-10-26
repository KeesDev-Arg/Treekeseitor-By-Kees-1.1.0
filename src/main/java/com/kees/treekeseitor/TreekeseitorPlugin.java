package com.kees.treekeseitor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class TreekeseitorPlugin extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private NamespacedKey disabledKey;
    private NamespacedKey effectsOffKey;
    private Settings settings;
    private FellingService felling;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        disabledKey = new NamespacedKey(this, "disabled");
        effectsOffKey = new NamespacedKey(this, "effects_off");
        reloadSettings();

        felling = new FellingService(this);
        getServer().getPluginManager().registerEvents(new FellListener(felling), this);

        PluginCommand cmd = getCommand("treekeseitor");
        if (cmd != null) {
            TreekeseitorCommand handler = new TreekeseitorCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        if (getServer().getPluginManager().isPluginEnabled("Jobs")) {
            getLogger().info("Jobs detectado: cada tronco talado se rompe con un BlockBreakEvent "
                    + "real, así que el trabajo Woodcutter lo cuenta/paga normalmente.");
        }
    }

    @Override
    public void onDisable() {
        if (felling != null) {
            felling.cancelAll();
        }
    }

    public void reloadSettings() {
        reloadConfig();
        settings = Settings.load(getConfig());
    }

    public Settings settings() {
        return settings;
    }

    public FellingService felling() {
        return felling;
    }

    /** Mensaje de config.yml (sección messages) ya parseado con MiniMessage. */
    public Component msg(String key, String fallback) {
        return MM.deserialize(getConfig().getString("messages." + key, fallback));
    }

    public boolean isEnabledFor(Player player) {
        return !player.getPersistentDataContainer()
                .getOrDefault(disabledKey, PersistentDataType.BOOLEAN, false);
    }

    /** ¿Este jugador quiere ver/oír los efectos del Treekeseitor? (por defecto sí) */
    public boolean effectsEnabledFor(Player player) {
        return !player.getPersistentDataContainer()
                .getOrDefault(effectsOffKey, PersistentDataType.BOOLEAN, false);
    }

    /** Alterna los efectos del jugador. Devuelve true si quedaron ACTIVADOS. */
    public boolean toggleEffects(Player player) {
        boolean nowEnabled = !effectsEnabledFor(player);
        if (nowEnabled) {
            player.getPersistentDataContainer().remove(effectsOffKey);
        } else {
            player.getPersistentDataContainer().set(effectsOffKey, PersistentDataType.BOOLEAN, true);
        }
        return nowEnabled;
    }

    /** Alterna el estado del jugador. Devuelve true si quedó ACTIVADO. */
    public boolean toggle(Player player) {
        boolean nowEnabled = !isEnabledFor(player);
        if (nowEnabled) {
            player.getPersistentDataContainer().remove(disabledKey);
        } else {
            player.getPersistentDataContainer().set(disabledKey, PersistentDataType.BOOLEAN, true);
        }
        return nowEnabled;
    }
}
