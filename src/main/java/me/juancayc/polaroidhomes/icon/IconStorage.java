package me.juancayc.polaroidhomes.icon;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Stores the icon chosen for one player's home.
 *
 * <p>Icons are this plugin's only owned state. No supported home provider offers an API for home
 * metadata, so the mapping {@code player uuid + home name -> item reference} lives here and nowhere
 * else. The key is the home's NAME, which is why a rename has to be followed rather than ignored.
 *
 * <p>The interface exists so a SQL backend can replace the YAML one without the menu knowing. Every
 * method is expected to be cheap and callable from the main thread; an implementation that does
 * real I/O buffers it and flushes on its own schedule.
 */
public interface IconStorage {

    /** The item reference chosen for this home, or null when the player never chose one. */
    @Nullable String icon(UUID player, String home);

    /** Sets the icon. A null reference clears it back to the configured default. */
    void setIcon(UUID player, String home, @Nullable String reference);

    /**
     * Moves an icon when the home provider renames a home.
     *
     * <p>Without this the icon orphans: it stays keyed to a name no home has any more, and the
     * renamed home silently loses its picture.
     */
    void renameHome(UUID player, String from, String to);

    /** Drops an icon when its home is deleted, so the store does not grow forever. */
    void deleteHome(UUID player, String home);

    /** Writes anything buffered. Called on the save interval and on disable. */
    void flush();

    /** Releases resources. After this the instance is not used again. */
    void close();
}
