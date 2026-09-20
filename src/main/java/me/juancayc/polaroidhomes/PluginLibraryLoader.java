package me.juancayc.polaroidhomes;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

/**
 * Pulls the database libraries at load time.
 *
 * <p>Paper plugins have no {@code libraries:} key — that belongs to Spigot's {@code plugin.yml}
 * and is silently ignored here. Declare this class through {@code loader:} in paper-plugin.yml.
 *
 * <p>Keeping the jar free of these also avoids shipping sqlite-jdbc's per-platform natives, and
 * stops the plugin from shadowing a driver version the operator already runs.
 *
 * <p>MySQL is fetched unconditionally so switching {@code data.yml}'s {@code type} to mysql only
 * needs a restart, never a different jar.
 */
public final class PluginLibraryLoader implements PluginLoader {

    private static final String HIKARI = "com.zaxxer:HikariCP:7.0.2";
    private static final String SQLITE = "org.xerial:sqlite-jdbc:3.50.3.0";
    private static final String MYSQL = "com.mysql:mysql-connector-j:9.3.0";

    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpath) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());

        resolver.addDependency(new Dependency(new DefaultArtifact(HIKARI), null));
        resolver.addDependency(new Dependency(new DefaultArtifact(SQLITE), null));
        resolver.addDependency(new Dependency(new DefaultArtifact(MYSQL), null));

        classpath.addLibrary(resolver);
    }
}
