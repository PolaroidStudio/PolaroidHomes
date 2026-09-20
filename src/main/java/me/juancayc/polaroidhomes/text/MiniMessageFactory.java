package me.juancayc.polaroidhomes.text;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;

/**
 * Builds the single MiniMessage instance the whole plugin renders through.
 *
 * <p>Sharing one instance is what lets a server owner write a Nexo glyph tag into a menu title and
 * have it resolve identically in chat, in an item name and in lore. Creating instances inline
 * would mean each call site has a different set of tags, which shows up as a tag that works in one
 * place and prints literally in another.
 */
public final class MiniMessageFactory {

    private MiniMessageFactory() {
    }

    public static MiniMessage build() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Nexo")) {
            return MiniMessage.miniMessage();
        }
        try {
            return MiniMessage.builder()
                    .tags(TagResolver.builder()
                            .resolver(TagResolver.standard())
                            .resolver(NexoBridge.glyphResolver())
                            .build())
                    .build();
        } catch (Throwable ignored) {
            // Nexo is present but its glyph API moved. Degrading to standard MiniMessage loses
            // glyphs and nothing else, which is far better than refusing to enable.
            return MiniMessage.miniMessage();
        }
    }

    /** Keeps the direct Nexo reference out of every other class, so the soft dependency stays soft. */
    private static final class NexoBridge {
        private static TagResolver glyphResolver() {
            return com.nexomc.nexo.glyphs.GlyphTag.INSTANCE.getRESOLVER();
        }
    }
}
