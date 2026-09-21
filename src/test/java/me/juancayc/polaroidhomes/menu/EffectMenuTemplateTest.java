package me.juancayc.polaroidhomes.menu;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two effect windows' layouts, and the one invariant that is easy to break by hand.
 *
 * <p>{@link DefaultMenuTemplates} exists because a broken menu.yml has to fall back to something,
 * and the thing it falls back to has to look like what the file shipped. Those two live in
 * different languages, so nothing but a test keeps them in step: an operator who broke their file
 * would otherwise get a fallback that quietly draws a different window from the one they had.
 */
class EffectMenuTemplateTest {

    private static YamlConfiguration shippedMenu() {
        InputStream in = EffectMenuTemplateTest.class.getResourceAsStream("/menu.yml");
        assertNotNull(in, "menu.yml must be on the test classpath");
        return YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    // --- The built-in layouts are valid ---------------------------------------

    @Test
    void theBuiltInPickerParses() {
        MenuTemplate picker = DefaultMenuTemplates.effectPicker();

        assertEquals(3, picker.rows());
        assertEquals(27, picker.inventorySize());
        assertTrue(picker.has(MenuElement.EFFECT_ANIMATIONS));
        assertTrue(picker.has(MenuElement.EFFECT_PARTICLES));
        assertTrue(picker.has(MenuElement.EFFECT_NONE));
        assertTrue(picker.has(MenuElement.EFFECT_BACK));
    }

    @Test
    void theBuiltInListParsesAndCanPage() {
        MenuTemplate list = DefaultMenuTemplates.effectList();

        assertEquals(36, list.slotsPerPage());
        assertTrue(list.has(MenuElement.PREVIOUS_PAGE));
        assertTrue(list.has(MenuElement.NEXT_PAGE),
                "a catalog can outgrow one page, so the list must be able to page");
        assertTrue(list.has(MenuElement.EFFECT_NONE));
        assertTrue(list.has(MenuElement.EFFECT_BACK));
    }

    @Test
    void theBuiltInHomesGridCarriesTheEffectsButton() {
        assertTrue(DefaultMenuTemplates.homes().has(MenuElement.EFFECTS),
                "the effects button is the only way into the catalog");
    }

    /**
     * The picker names a button as its grid element, because {@link MenuTemplate} requires the named
     * element to appear at least once and this window has no content slots. That is a workaround, so
     * it is pinned: if it ever stops holding, the picker falls back to the built-in layout on every
     * startup and nobody notices, because the built-in layout looks the same.
     */
    @Test
    void thePickersGridElementTrickKeepsItValid() {
        MenuTemplate picker = DefaultMenuTemplates.effectPicker();

        assertEquals(1, picker.slotsPerPage(),
                "exactly one animations button, standing in for a content slot");
        assertFalse(picker.has(MenuElement.PREVIOUS_PAGE),
                "the picker holds two fixed choices, so it must never need to page");
        assertFalse(picker.has(MenuElement.NEXT_PAGE));
    }

    // --- The shipped file matches them ----------------------------------------

    @Test
    void theShippedPickerSectionMatchesTheBuiltInOne() {
        MenuTemplate.Result parsed = MenuTemplateReader.read(
                shippedMenu().getConfigurationSection("effects"), 1,
                MenuElement.EFFECT_ANIMATIONS, DefaultMenuTemplates.EFFECT_PICKER_ELEMENTS);

        assertTrue(parsed.isValid(), () -> "the shipped layout must parse: " + parsed.errors());
        assertEquals(DefaultMenuTemplates.effectPicker().slots(), parsed.template().slots());
    }

    @Test
    void theShippedListSectionMatchesTheBuiltInOne() {
        MenuTemplate.Result parsed = MenuTemplateReader.read(
                shippedMenu().getConfigurationSection("effect-list"), 1,
                MenuElement.EFFECT_SLOT, DefaultMenuTemplates.EFFECT_LIST_ELEMENTS);

        assertTrue(parsed.isValid(), () -> "the shipped layout must parse: " + parsed.errors());
        assertEquals(DefaultMenuTemplates.effectList().slots(), parsed.template().slots());
    }

    @Test
    void theShippedHomesSectionMatchesTheBuiltInOne() {
        MenuTemplate.Result parsed = MenuTemplateReader.read(
                shippedMenu().getConfigurationSection("homes"), 45,
                MenuElement.HOME_SLOT, DefaultMenuTemplates.HOMES_ELEMENTS);

        assertTrue(parsed.isValid(), () -> "the shipped layout must parse: " + parsed.errors());
        assertEquals(DefaultMenuTemplates.homes().slots(), parsed.template().slots());
    }

    // --- Element names --------------------------------------------------------

    @Test
    void everyNewElementResolvesFromTheNameMenuYmlUses() {
        assertEquals(MenuElement.EFFECTS, MenuElement.byKey("effects"));
        assertEquals(MenuElement.EFFECT_ANIMATIONS, MenuElement.byKey("effect-animations"));
        assertEquals(MenuElement.EFFECT_PARTICLES, MenuElement.byKey("effect-particles"));
        assertEquals(MenuElement.EFFECT_NONE, MenuElement.byKey("effect-none"));
        assertEquals(MenuElement.EFFECT_BACK, MenuElement.byKey("effect-back"));
        assertEquals(MenuElement.EFFECT_SLOT, MenuElement.byKey("effect-slot"));
        // Underscores are folded, so an operator who typed the Java spelling still gets a menu.
        assertEquals(MenuElement.EFFECT_SLOT, MenuElement.byKey("effect_slot"));
    }

    /**
     * An element one menu understands must not be silently accepted by another: a picker button
     * placed on the homes grid would draw an item that does nothing.
     */
    @Test
    void aPickerElementIsRejectedOnTheHomesGrid() {
        MenuTemplate.Result parsed = MenuTemplate.parse(
                java.util.List.of("AHHHHHHHH"),
                java.util.Map.of(),
                java.util.Map.of('A', MenuElement.EFFECT_ANIMATIONS, 'H', MenuElement.HOME_SLOT),
                1, MenuElement.HOME_SLOT, DefaultMenuTemplates.HOMES_ELEMENTS);

        assertTrue(parsed.errors().stream()
                .anyMatch(error -> error.contains("effect-animations")));
    }
}
