package me.juancayc.polaroidhomes.menu;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The YAML side of the layout: the shapes an operator can write that are wrong before the layout
 * rules ever see them, plus proof that the menu.yml this jar ships actually parses. A shipped file
 * that does not parse would mean every fresh install silently runs on the fallback.
 */
class MenuTemplateReaderTest {

    private static YamlConfiguration yaml(String content) {
        return YamlConfiguration.loadConfiguration(new java.io.StringReader(content));
    }

    private static MenuTemplate.Result readHomes(String content) {
        return MenuTemplateReader.read(yaml(content).getConfigurationSection("homes"), 45,
                MenuElement.HOME_SLOT, DefaultMenuTemplates.HOMES_ELEMENTS);
    }

    @Test
    @DisplayName("the menu.yml shipped in the jar parses into both layouts")
    void shippedFileParses() throws Exception {
        YamlConfiguration config;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("menu.yml")) {
            assertNotNull(in, "menu.yml must be on the resource path, and so in the jar");
            config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        int cap = config.getInt("max-displayed-slots", -1);
        assertEquals(45, cap, "the shipped cap is what the shipped layout is validated against");

        MenuTemplate.Result homes = MenuTemplateReader.read(
                config.getConfigurationSection("homes"), cap,
                MenuElement.HOME_SLOT, DefaultMenuTemplates.HOMES_ELEMENTS);
        assertTrue(homes.isValid(), () -> "shipped homes layout rejected: " + homes.errors());
        assertTrue(homes.warnings().isEmpty(),
                () -> "the shipped file must not warn on a fresh install: " + homes.warnings());

        MenuTemplate.Result icons = MenuTemplateReader.read(
                config.getConfigurationSection("icons"), 1,
                MenuElement.ICON_SLOT, DefaultMenuTemplates.ICON_ELEMENTS);
        assertTrue(icons.isValid(), () -> "shipped icons layout rejected: " + icons.errors());
        assertTrue(icons.warnings().isEmpty(), () -> icons.warnings().toString());

        // The shipped file and the in-code fallback must draw the same window, or an operator who
        // hits the fallback silently gets a different menu than the one they were editing.
        assertEquals(DefaultMenuTemplates.homes().homeSlots(), homes.template().homeSlots());
        assertEquals(DefaultMenuTemplates.homes().slots(), homes.template().slots());
        assertEquals(DefaultMenuTemplates.icons().slots(), icons.template().slots());
    }

    @Test
    @DisplayName("a missing section is rejected rather than treated as an empty layout")
    void missingSectionIsRejected() {
        MenuTemplate.Result result = MenuTemplateReader.read(null, 45,
                MenuElement.HOME_SLOT, DefaultMenuTemplates.HOMES_ELEMENTS);

        assertFalse(result.isValid());
        assertTrue(result.errors().getFirst().contains("missing"));
    }

    @Test
    @DisplayName("an element keyed by more than one character is rejected")
    void multiCharacterKeyIsRejected() {
        MenuTemplate.Result result = readHomes("""
                homes:
                  rows:
                    - "HHHHHHHHH"
                  elements:
                    'HH':
                      type: home-slot
                """);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("single character")),
                () -> result.errors().toString());
    }

    @Test
    @DisplayName("an unknown element type is rejected, naming what was written")
    void unknownTypeIsRejected() {
        MenuTemplate.Result result = readHomes("""
                homes:
                  rows:
                    - "HHHHHHHHH"
                  elements:
                    'H':
                      type: teleport-everyone
                """);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream()
                        .anyMatch(error -> error.contains("teleport-everyone")),
                () -> result.errors().toString());
    }

    @Test
    @DisplayName("an element with no type is rejected rather than drawn as a blank slot")
    void missingTypeIsRejected() {
        MenuTemplate.Result result = readHomes("""
                homes:
                  rows:
                    - "HHHHHHHHH"
                  elements:
                    'H':
                      item: DIRT
                """);

        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("an item reference is carried through untouched, whatever plugin it names")
    void itemReferencesAreCarriedThrough() {
        // Resolution happens at render, through the existing item layer. Parsing must not second
        // guess a reference for a plugin that is simply not installed on this machine.
        MenuTemplate.Result result = readHomes("""
                homes:
                  rows:
                    - "HHHHHHHHH"
                    - "<###I###>"
                  elements:
                    'H': { type: home-slot }
                    '#': { type: filler, item: "nexo:my_pane" }
                    '<': { type: previous-page, item: ARROW }
                    '>': { type: next-page, item: ARROW }
                    'I': { type: info, item: "itemsadder:suite:counter", custom-model-data: 12 }
                """);

        assertTrue(result.isValid(), () -> result.errors().toString());
        assertEquals("nexo:my_pane",
                result.template().definition(MenuElement.FILLER).item());
        assertEquals("itemsadder:suite:counter",
                result.template().definition(MenuElement.INFO).item());
        assertEquals(Integer.valueOf(12),
                result.template().definition(MenuElement.INFO).modelData());
    }

    @Test
    @DisplayName("element problems and layout problems are reported together")
    void allProblemsAreReportedAtOnce() {
        // An operator who has to restart once per error gives up. Everything wrong with the file is
        // on the console after the first startup.
        MenuTemplate.Result result = readHomes("""
                homes:
                  rows:
                    - "HHHHHHH"
                  elements:
                    'H':
                      type: nonsense
                """);

        assertFalse(result.isValid());
        assertTrue(result.errors().size() >= 2,
                () -> "expected both the bad type and the short row: " + result.errors());
    }
}
