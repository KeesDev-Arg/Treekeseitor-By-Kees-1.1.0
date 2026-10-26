package com.kees.treekeseitor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TreekeseitorCommand implements CommandExecutor, TabCompleter {

    private final TreekeseitorPlugin plugin;

    public TreekeseitorCommand(TreekeseitorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "toggle" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.msg("players-only", "<red>Solo jugadores."));
                    return true;
                }
                if (!player.hasPermission("treekeseitor.toggle")) {
                    player.sendMessage(plugin.msg("no-permission", "<red>Sin permiso."));
                    return true;
                }
                boolean enabled = plugin.toggle(player);
                player.sendMessage(enabled
                        ? plugin.msg("enabled", "<green>Treekeseitor activado.")
                        : plugin.msg("disabled", "<red>Treekeseitor desactivado."));
            }
            case "effects", "efectos" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.msg("players-only", "<red>Solo jugadores."));
                    return true;
                }
                if (!player.hasPermission("treekeseitor.effects")) {
                    player.sendMessage(plugin.msg("no-permission", "<red>Sin permiso."));
                    return true;
                }
                boolean enabled = plugin.toggleEffects(player);
                player.sendMessage(enabled
                        ? plugin.msg("effects-enabled", "<green>Sonidos y partículas <bold>activados</bold>.")
                        : plugin.msg("effects-disabled", "<red>Sonidos y partículas <bold>desactivados</bold>."));
            }
            case "reload" -> {
                if (!sender.hasPermission("treekeseitor.reload")) {
                    sender.sendMessage(plugin.msg("no-permission", "<red>Sin permiso."));
                    return true;
                }
                plugin.reloadSettings();
                sender.sendMessage(plugin.msg("reloaded", "<green>Configuración recargada."));
            }
            default -> sender.sendMessage(plugin.msg("usage", "<gray>Uso: /treekeseitor [toggle|effects|reload]"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if (sender.hasPermission("treekeseitor.toggle") && "toggle".startsWith(prefix)) out.add("toggle");
            if (sender.hasPermission("treekeseitor.effects") && "effects".startsWith(prefix)) out.add("effects");
            if (sender.hasPermission("treekeseitor.reload") && "reload".startsWith(prefix)) out.add("reload");
        }
        return out;
    }
}
