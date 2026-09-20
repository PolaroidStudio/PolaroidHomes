package me.juancayc.polaroidhomes.config;

import me.juancayc.polaroidhomes.menu.DefaultMenuTemplates;
import me.juancayc.polaroidhomes.menu.MenuElement;
import me.juancayc.polaroidhomes.menu.MenuTemplate;
import me.juancayc.polaroidhomes.menu.MenuTemplateReader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The promise this layer makes to an operator: a menu.yml they broke costs them their layout, not
 * their server. Nothing here may throw, disable anything, or hand back a half-built window.
 */
class MenuConfigTest {

    /** Collects what would have gone to the console, so the assertions can read it. */
    private static final class Recorder extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        List<String> at(Level level) {
            return records.stream()
                    .filter(record -> record.getLevel().equals(level))
                    .map(LogRecord::getMessage)
                    .toList();
        }
    }

    private Recorder recorder;
    private Logger logger;

    private Logger logger() {
        recorder = new Recorder();
        logger = Logger.getLogger("menu-config-test-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.addHandler(recorder);
        return logger;
    }

    private static MenuTemplate.Result readHomes(String yaml) {
        return MenuTemplateReader.read(
                YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml))
                        .getConfigurationSection("homes"),
                45, MenuElement.HOME_SLOT, DefaultMenuTemplates.HOMES_ELEMENTS);
    }

    @Test
    @DisplayName("a broken section falls back to the shipped layout instead of failing")
    void aBrokenSectionFallsBackToTheDefault() {
        MenuTemplate fallback = DefaultMenuTemplates.homes();
        MenuTemplate.Result broken = readHomes("""
                homes:
                  rows:
                    - "HHHH"
                  elements:
                    'H': { type: home-slot }
                """);

        MenuTemplate chosen = MenuConfig.select(broken, "homes", fallback, logger());

        assertSame(fallback, chosen, "the renderer must still get a usable layout");
        assertEquals(45, chosen.slotsPerPage());
    }

    @Test
    @DisplayName("every reason a section was rejected reaches the console, plus what to do")
    void everyErrorIsReported() {
        MenuTemplate.Result broken = readHomes("""
                homes:
                  rows:
                    - "HHHHHHHH"
                  elements:
                    'H': { type: nonsense }
                """);

        MenuConfig.select(broken, "homes", DefaultMenuTemplates.homes(), logger());

        List<String> severe = recorder.at(Level.SEVERE);
        assertEquals(broken.errors().size() + 1, severe.size(),
                "one line per error, plus the line saying the built-in layout took over");
        assertTrue(severe.stream().allMatch(line -> line.contains("menu.yml 'homes'")),
                () -> "every line must name the file and section: " + severe);
        assertTrue(severe.getLast().contains("/homes reload"),
                "the operator has to be told how to retry");
    }

    @Test
    @DisplayName("a valid section is used as written, and its warnings are still reported")
    void aValidSectionIsUsedAndStillWarns() {
        MenuTemplate.Result parsed = readHomes("""
                homes:
                  rows:
                    - "HHHHHHHHH"
                    - "<###I###>"
                  elements:
                    'H': { type: home-slot }
                    '#': { type: filler, item: BLACK_STAINED_GLASS_PANE }
                    '<': { type: previous-page, item: ARROW }
                    '>': { type: next-page, item: ARROW }
                    'I': { type: info, item: PAPER }
                    'X': { type: close, item: BARRIER }
                """);

        MenuTemplate chosen = MenuConfig.select(
                parsed, "homes", DefaultMenuTemplates.homes(), logger());

        assertEquals(9, chosen.slotsPerPage());
        assertEquals(18, chosen.inventorySize());
        assertTrue(recorder.at(Level.SEVERE).isEmpty(), "a valid layout must not log an error");
        assertTrue(recorder.at(Level.WARNING).stream()
                        .anyMatch(line -> line.contains("elements.X")),
                () -> "the unused element is still worth a warning: "
                        + recorder.at(Level.WARNING));
    }
}
