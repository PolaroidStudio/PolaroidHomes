package me.juancayc.polaroidhomes.provider;

import me.juancayc.polaroidhomes.provider.hook.EssentialsHomeProvider;
import me.juancayc.polaroidhomes.provider.hook.HuskHomesProvider;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * The supported providers, in the order {@code auto} prefers them.
 *
 * <p>Modelled on the item layer's {@code ItemManager.withDefaultHooks()}: one place lists every hook,
 * and adding a backend means adding a class and one line here. Nothing else in the plugin names a
 * concrete provider.
 *
 * <p>The order is the {@code auto} priority and is fixed rather than configurable, because it has to
 * be deterministic: two servers with the same plugins installed must choose the same backend, or a
 * configuration that works on one silently reads the wrong homes on the other. EssentialsX comes
 * first because it is the historical default of this plugin — a server that has had both installed
 * since before the {@code hooks} section existed keeps the backend its icons were recorded against.
 * An operator who wants the other one names it explicitly.
 */
public final class HomeProviders {

    private HomeProviders() {
    }

    public static List<HomeProvider> candidates(Plugin plugin) {
        return List.of(
                new EssentialsHomeProvider(plugin),
                new HuskHomesProvider(plugin));
    }

    /** Resolves {@code hooks.home-provider} against what is installed on this server. */
    public static ProviderSelection select(Plugin plugin, String configured) {
        return ProviderSelection.resolve(configured, candidates(plugin));
    }
}
