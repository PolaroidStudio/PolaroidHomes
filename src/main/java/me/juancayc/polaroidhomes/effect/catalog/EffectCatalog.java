package me.juancayc.polaroidhomes.effect.catalog;

import me.juancayc.polaroidhomes.config.EffectSettings;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every effect an operator has declared, in both categories.
 *
 * <p>Parsed once per reload and then read-only. A malformed entry is dropped with a message naming
 * the entry and the field, never silently repaired: the catalog is a price list, and an entry that
 * quietly became something else is a product that does not match what a player paid for. The rest
 * of the catalog still loads, so one typo costs one effect rather than the whole feature.
 *
 * <p>Ordering is the order the operator wrote, because that is the order the menu pages through and
 * an operator arranging a shop expects their file to be the arrangement.
 */
public final class EffectCatalog {

    private final Map<String, EffectEntry> byKey;
    private final List<String> problems;

    private EffectCatalog(Map<String, EffectEntry> byKey, List<String> problems) {
        this.byKey = byKey;
        this.problems = problems;
    }

    /** An empty catalog. A server with no declared effects simply has nothing to equip. */
    public static EffectCatalog empty() {
        return new EffectCatalog(Map.of(), List.of());
    }

    /**
     * Reads both category sections of {@code teleport-effects}.
     *
     * @param effects the {@code teleport-effects} section, possibly null
     */
    public static EffectCatalog from(@Nullable ConfigurationSection effects) {
        Map<String, EffectEntry> entries = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (EffectCategory category : EffectCategory.values()) {
            readCategory(effects, category, entries, problems);
        }
        return new EffectCatalog(entries, List.copyOf(problems));
    }

    private static void readCategory(@Nullable ConfigurationSection effects,
                                     EffectCategory category,
                                     Map<String, EffectEntry> entries,
                                     List<String> problems) {
        ConfigurationSection section =
                effects == null ? null : effects.getConfigurationSection(category.configKey());
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection raw = section.getConfigurationSection(id);
            if (raw == null) {
                problems.add(path(category, id) + " is not a section, so it declares no effect.");
                continue;
            }
            String normalized = normalizeId(id);
            if (normalized == null) {
                problems.add(path(category, id) + " is not a usable id. Use lowercase letters, "
                        + "numbers and underscores only: the id becomes the permission node "
                        + category.permission("<id>") + ", and a node with a space or a colon in it "
                        + "cannot be granted.");
                continue;
            }
            EffectEntry entry = read(category, normalized, raw, problems);
            if (entry == null) {
                continue;
            }
            String key = key(category, normalized);
            if (entries.put(key, entry) != null) {
                // Two spellings of one id in one category, e.g. `Portal` and `portal`. The second
                // wins, which is YAML's own behaviour for a duplicate key, but it is said out loud
                // because one of the two is a product nobody can reach.
                problems.add(path(category, id) + " repeats an id already declared in this "
                        + "category, so only the last one is used.");
            }
        }
    }

    private static @Nullable EffectEntry read(EffectCategory category,
                                              String id,
                                              ConfigurationSection raw,
                                              List<String> problems) {
        String displayName = raw.getString("display-name", "");
        if (displayName.isBlank()) {
            problems.add(path(category, id) + " has no 'display-name', so its menu button would be "
                    + "unlabelled.");
            return null;
        }
        String icon = raw.getString("icon", "");
        if (icon.isBlank()) {
            problems.add(path(category, id) + " has no 'icon', so its menu button has nothing to "
                    + "draw.");
            return null;
        }

        EffectSettings entry = half(category, id, raw, "entry", problems);
        EffectSettings arrival = half(category, id, raw, "arrival", problems);
        if (entry == null || arrival == null) {
            return null;
        }
        return new EffectEntry(id, category, displayName, icon, entry, arrival);
    }

    /**
     * Reads the {@code entry} or {@code arrival} half of one entry.
     *
     * <p>Both halves are required. An entry with only a departure would move the player and then
     * stop mid-effect, which reads as the plugin failing rather than as a shorter effect.
     */
    private static @Nullable EffectSettings half(EffectCategory category,
                                                 String id,
                                                 ConfigurationSection raw,
                                                 String key,
                                                 List<String> problems) {
        ConfigurationSection section = raw.getConfigurationSection(key);
        if (section == null) {
            problems.add(path(category, id) + " has no '" + key + "' block. Every effect declares "
                    + "both an 'entry' and an 'arrival'.");
            return null;
        }
        return switch (category) {
            case ANIMATION -> animation(category, id, key, section, problems);
            case PARTICLE -> particle(category, id, key, section, problems);
        };
    }

    private static @Nullable EffectSettings animation(EffectCategory category,
                                                      String id,
                                                      String key,
                                                      ConfigurationSection section,
                                                      List<String> problems) {
        String model = section.getString("model", "");
        String animation = section.getString("animation", "");
        if (model.isBlank() || animation.isBlank()) {
            problems.add(path(category, id) + "." + key + " needs both a 'model' and an "
                    + "'animation'. Model Engine cannot play a model without naming the animation "
                    + "on it.");
            return null;
        }
        if (!section.isSet("duration")) {
            problems.add(path(category, id) + "." + key + " has no 'duration'. Model Engine does "
                    + "not report an animation's length, so the plugin cannot work it out: declare "
                    + "how many seconds the animation runs for.");
            return null;
        }
        return EffectSettings.animation(model, animation, section.getDouble("duration"));
    }

    private static @Nullable EffectSettings particle(EffectCategory category,
                                                     String id,
                                                     String key,
                                                     ConfigurationSection section,
                                                     List<String> problems) {
        String name = section.getString("particle", "");
        Particle particle = EffectSettings.parseParticle(name);
        if (particle == null) {
            problems.add(path(category, id) + "." + key + " names the particle '" + name
                    + "', which this Minecraft version does not have. Particle names change between "
                    + "releases; check the current list.");
            return null;
        }
        if (!section.isSet("count")) {
            problems.add(path(category, id) + "." + key + " has no 'count', so it would draw no "
                    + "particles at all.");
            return null;
        }
        int count = section.getInt("count");
        if (count <= 0) {
            problems.add(path(category, id) + "." + key + " declares a 'count' of " + count
                    + ", so it would draw nothing. Use a positive number.");
            return null;
        }
        return EffectSettings.particle(particle, count, section.getDouble("radius", 1.0D));
    }

    /**
     * Folds an id to its storage form, or null when it cannot be one.
     *
     * <p>The id becomes a permission node, so the characters a permission node cannot carry are
     * rejected here rather than producing a node nobody can grant. It is also the database key, and
     * folding case is what keeps {@code Portal} in the file and {@code portal} in a stored row from
     * being two different products.
     */
    static @Nullable String normalizeId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String folded = id.trim().toLowerCase(Locale.ROOT);
        for (int index = 0; index < folded.length(); index++) {
            char symbol = folded.charAt(index);
            boolean usable = (symbol >= 'a' && symbol <= 'z')
                    || (symbol >= '0' && symbol <= '9')
                    || symbol == '_' || symbol == '-';
            if (!usable) {
                return null;
            }
        }
        return folded;
    }

    private static String key(EffectCategory category, String id) {
        return category.storageKey() + ':' + id;
    }

    private static String path(EffectCategory category, String id) {
        return "teleport-effects." + category.configKey() + "." + id;
    }

    /** Every entry, in declaration order, of one category. */
    public List<EffectEntry> byCategory(EffectCategory category) {
        List<EffectEntry> result = new ArrayList<>();
        for (EffectEntry entry : byKey.values()) {
            if (entry.category() == category) {
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }

    /**
     * The entries of one category the server can actually render.
     *
     * <p>What the menu lists. An animation entry on a server without Model Engine is not here, so a
     * player never sees, buys or equips something that cannot play.
     */
    public List<EffectEntry> available(EffectCategory category, boolean modelEngineAvailable) {
        List<EffectEntry> result = new ArrayList<>();
        for (EffectEntry entry : byCategory(category)) {
            if (entry.isAvailable(modelEngineAvailable)) {
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }

    /** One entry, or null when nothing in the catalog matches. */
    public @Nullable EffectEntry find(@Nullable EffectCategory category, @Nullable String id) {
        if (category == null || id == null) {
            return null;
        }
        String normalized = normalizeId(id);
        return normalized == null ? null : byKey.get(key(category, normalized));
    }

    /** How many entries parsed, across both categories. */
    public int size() {
        return byKey.size();
    }

    public boolean isEmpty() {
        return byKey.isEmpty();
    }

    /** Everything an operator got wrong, phrased for the console. Empty when the file is clean. */
    public List<String> problems() {
        return problems;
    }
}
