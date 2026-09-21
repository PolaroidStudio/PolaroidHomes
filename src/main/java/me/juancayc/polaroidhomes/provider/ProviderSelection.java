package me.juancayc.polaroidhomes.provider;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The outcome of resolving {@code hooks.home-provider} against what is actually installed.
 *
 * <p>Separate from the providers themselves, and Bukkit-free, because the decision is the part that
 * has to be right: an operator who names a provider that is not installed must be told so by name,
 * not quietly given a different backend. A silent fallback is exactly the bug that started this —
 * the plugin assumed EssentialsX, read an empty home list from it, and rendered a correct-looking
 * empty grid while the player's real homes sat in another plugin.
 *
 * @param chosen  the provider that will be used, null when none could be
 * @param outcome why
 * @param request the raw configured value, echoed back so a log line can quote it
 */
public record ProviderSelection(@Nullable HomeProvider chosen, Outcome outcome, String request) {

    /** The configured value that means "pick whichever supported provider is installed". */
    public static final String AUTO = "auto";

    public enum Outcome {
        /** {@code auto} found one or more installed providers and took the first by priority. */
        AUTO_SELECTED,
        /** The operator named a provider and it is installed. */
        EXPLICIT_SELECTED,
        /** The operator named a provider this build does not support at all. */
        UNKNOWN_NAME,
        /** The operator named a supported provider that is not installed on this server. */
        NAMED_NOT_INSTALLED,
        /** {@code auto} found nothing. The plugin cannot function. */
        NONE_INSTALLED
    }

    /**
     * Resolves the configured value against the candidate list.
     *
     * <p>{@code candidates} is the priority order for {@code auto} and must be deterministic: two
     * servers with the same plugins installed have to choose the same backend, or a config that
     * works on one silently reads the wrong homes on the other.
     */
    public static ProviderSelection resolve(@Nullable String configured,
                                            List<HomeProvider> candidates) {
        String request = configured == null || configured.isBlank()
                ? AUTO
                : configured.trim().toLowerCase(Locale.ROOT);

        if (AUTO.equals(request)) {
            for (HomeProvider candidate : candidates) {
                if (candidate.isAvailable()) {
                    return new ProviderSelection(candidate, Outcome.AUTO_SELECTED, request);
                }
            }
            return new ProviderSelection(null, Outcome.NONE_INSTALLED, request);
        }

        for (HomeProvider candidate : candidates) {
            if (candidate.id().equals(request)) {
                // Installed or not, the answer names this provider and nothing else. Falling through
                // to another backend here is what would make a typo look like a working config.
                return candidate.isAvailable()
                        ? new ProviderSelection(candidate, Outcome.EXPLICIT_SELECTED, request)
                        : new ProviderSelection(null, Outcome.NAMED_NOT_INSTALLED, request);
            }
        }
        return new ProviderSelection(null, Outcome.UNKNOWN_NAME, request);
    }

    public boolean isResolved() {
        return chosen != null;
    }

    /**
     * The line written to the console, phrased for whoever has to fix it.
     *
     * <p>Every unresolved outcome names the file, the key and the values that would work, because the
     * operator reading it is looking at a server that just refused to enable a plugin.
     */
    public String describe(List<HomeProvider> candidates) {
        String supported = String.join(", ", candidates.stream().map(HomeProvider::id).toList());
        return switch (outcome) {
            case AUTO_SELECTED -> "Home provider: " + chosen.pluginName()
                    + " (selected automatically; hooks.home-provider is '" + AUTO + "').";
            case EXPLICIT_SELECTED -> "Home provider: " + chosen.pluginName()
                    + " (named by hooks.home-provider).";
            case UNKNOWN_NAME -> "hooks.home-provider is '" + request
                    + "', which this version does not support. Supported values: " + AUTO + ", "
                    + supported + ".";
            case NAMED_NOT_INSTALLED -> "hooks.home-provider names '" + request
                    + "', but that plugin is not installed or not enabled on this server. "
                    + "PolaroidHomes will not silently read homes from a different backend: install "
                    + "it, name another of " + supported + ", or set hooks.home-provider to '" + AUTO
                    + "'.";
            case NONE_INSTALLED -> "No supported home provider is installed. PolaroidHomes needs one "
                    + "of: " + supported + ". It stores icons and plays effects for homes that "
                    + "another plugin owns, so it cannot run without a home backend.";
        };
    }

    /**
     * A warning for an {@code auto} selection that had more than one backend to choose from.
     *
     * <p>{@code auto} picks by a fixed priority, not by which backend actually holds homes, so on a
     * server running both it can pick the empty one. That reads exactly like a broken menu: the
     * grid opens, the slots are right, and every home is missing. Saying which backend was chosen,
     * and how to choose the other, is the difference between a one-line config fix and an
     * afternoon of debugging.
     *
     * @return the warning, or null when there was nothing ambiguous to warn about
     */
    public @Nullable String ambiguityWarning(List<HomeProvider> candidates) {
        if (outcome != Outcome.AUTO_SELECTED) {
            return null;
        }
        List<String> installed = candidates.stream()
                .filter(HomeProvider::isAvailable)
                .map(HomeProvider::id)
                .toList();
        if (installed.size() < 2) {
            return null;
        }
        return "More than one supported home plugin is installed (" + String.join(", ", installed)
                + ") and hooks.home-provider is '" + AUTO + "', so " + chosen.pluginName()
                + " was chosen by priority — not by which one actually holds your homes. If your "
                + "homes live in another of them, set hooks.home-provider to that one in "
                + "config.yml and run the reload command.";
    }
}
