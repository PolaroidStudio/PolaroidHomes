package me.juancayc.polaroidhomes.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads {@code lang/messages_<language>.yml} and turns its keys into components.
 *
 * <p>Each key is either a plain MiniMessage string or a {@code {text, hover, click}} section. The
 * English file shipped in the jar is installed as the configuration defaults, so a translation
 * that is missing a key resolves to English without any per-key fallback code.
 */
public final class MessageService {

    private final Plugin plugin;
    private FileConfiguration messages;
    private Component prefix = Component.empty();

    public MessageService(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.messages = loadMessages();
        this.prefix = ColorFormats.parse(messages.getString("prefix", ""));
    }

    private FileConfiguration loadMessages() {
        saveResourceIfMissing("lang/messages_en.yml");

        String language = plugin.getConfig().getString("language", "en");
        File file = new File(plugin.getDataFolder(), "lang/messages_" + language + ".yml");
        if (!file.exists()) {
            saveResourceIfMissing("lang/messages_" + language + ".yml");
        }
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "lang/messages_en.yml");
        }

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        // setDefaults against the jar copy is the whole fallback mechanism: standard Bukkit
        // behaviour, so a half-finished translation degrades key by key instead of all at once.
        try (InputStream in = plugin.getResource("lang/messages_en.yml")) {
            if (in != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
            // A missing base file only costs the fallback; the active language still loads.
        }
        return loaded;
    }

    private void saveResourceIfMissing(String path) {
        if (plugin.getResource(path) != null && !new File(plugin.getDataFolder(), path).exists()) {
            plugin.saveResource(path, false);
        }
    }

    /** The raw string behind a key, defaults included. Used by the menu renderer. */
    public String raw(String key, String fallback) {
        return messages.getString(key, fallback);
    }

    /** The raw string list behind a key. Empty when the key is absent. */
    public List<String> rawList(String key) {
        return new ArrayList<>(messages.getStringList(key));
    }

    /** Builds a component from a key, substituting pairwise replacements before deserializing. */
    public Component build(String key, String... replacements) {
        ConfigurationSection section = messages.getConfigurationSection(key);

        String text;
        String hover = null;
        String click = null;

        if (section != null) {
            text = section.getString("text", key);
            hover = section.getString("hover", null);
            click = section.getString("click", null);
        } else {
            text = messages.getString(key, key);
        }

        text = applyReplacements(text, replacements);
        hover = applyReplacements(hover, replacements);
        click = applyReplacements(click, replacements);

        Component component = ColorFormats.parse(text);
        if (hover != null && !hover.isBlank()) {
            component = component.hoverEvent(HoverEvent.showText(ColorFormats.parse(hover)));
        }
        if (click != null && !click.isBlank()) {
            component = ClickActionParser.apply(component, click, "");
        }
        return component;
    }

    /**
     * Builds a component meant to sit inside an item name or lore line.
     *
     * <p>Vanilla renders every item display string in italics unless the component says otherwise,
     * so a menu built without this reset reads as a slanted mess no matter what the lang file says.
     */
    public Component item(String key, String... replacements) {
        return build(key, replacements).decoration(TextDecoration.ITALIC, false);
    }

    /** Same as {@link #item} but for a literal string rather than a message key. */
    public Component itemLine(String raw, String... replacements) {
        return ColorFormats.parse(applyReplacements(raw, replacements))
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Sends with the brand prefix. Players get the full component; console gets plain text. */
    public void send(CommandSender sender, String key, String... replacements) {
        Component body = build(key, replacements);
        if (sender instanceof Player player) {
            player.sendMessage(prefix.append(body));
        } else {
            sender.sendMessage(PlainTextComponentSerializer.plainText().serialize(body));
        }
    }

    /**
     * Sends without the prefix. Rows inside a list use this: repeating the banner on every line of
     * a help block is the fastest way to make a plugin look cheap.
     */
    public void sendRaw(CommandSender sender, String key, String... replacements) {
        Component body = build(key, replacements);
        if (sender instanceof Player player) {
            player.sendMessage(body);
        } else {
            sender.sendMessage(PlainTextComponentSerializer.plainText().serialize(body));
        }
    }

    public Component prefix() {
        return prefix;
    }

    private static String applyReplacements(String text, String[] replacements) {
        if (text == null || replacements == null || replacements.length == 0) {
            return text;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }

    /**
     * Escapes a value that a player controls, so it cannot open tags in the message that carries it.
     *
     * <p>A home named {@code <click:run_command:/op me>} would otherwise ship a working click event
     * to whoever reads the message it is substituted into.
     */
    public static String escape(String value) {
        return value == null ? "" : ColorFormats.mm().escapeTags(value);
    }
}
