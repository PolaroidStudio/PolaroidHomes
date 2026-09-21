package me.juancayc.polaroidhomes.edit;

import me.juancayc.polaroidhomes.edit.HomeNameRules.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the rename validation rules.
 *
 * <p>A name this class lets through is written into somebody else's database, and the failure mode
 * of a bad one is not an exception but a home that exists under a name no command can address. That
 * is why every rule has a case here rather than a comment.
 */
class HomeNameRulesTest {

    private static final List<String> EXISTING = List.of("base", "mine", "shop");

    @Test
    void anOrdinaryNameIsAccepted() {
        assertEquals(Result.OK, HomeNameRules.check("cabin", "base", EXISTING));
        assertTrue(HomeNameRules.check("cabin", "base", EXISTING).isOk());
    }

    @Test
    void lettersDigitsUnderscoreAndHyphenAreAllAccepted() {
        assertEquals(Result.OK, HomeNameRules.check("Base_2-b", "old", EXISTING));
    }

    @Test
    void anEmptyNameIsRefused() {
        assertEquals(Result.EMPTY, HomeNameRules.check("", "base", EXISTING));
        assertEquals(Result.EMPTY, HomeNameRules.check("   ", "base", EXISTING));
        assertEquals(Result.EMPTY, HomeNameRules.check(null, "base", EXISTING));
    }

    @Test
    void aNameLongerThanTheLimitIsRefused() {
        String tooLong = "a".repeat(HomeNameRules.MAX_LENGTH + 1);

        assertEquals(Result.TOO_LONG, HomeNameRules.check(tooLong, "base", EXISTING));
    }

    @Test
    void aNameExactlyAtTheLimitIsAccepted() {
        String atLimit = "a".repeat(HomeNameRules.MAX_LENGTH);

        assertEquals(Result.OK, HomeNameRules.check(atLimit, "base", EXISTING));
    }

    /**
     * A space splits {@code /home &lt;name&gt;} into two arguments, so a home named with one is
     * unreachable by command even though the backend stored it happily.
     */
    @Test
    void aSpaceInsideTheNameIsRefused() {
        assertEquals(Result.ILLEGAL_CHARACTERS, HomeNameRules.check("my base", "old", EXISTING));
    }

    /** A dot is HuskHomes' own {@code owner.home} delimiter. */
    @Test
    void aDotIsRefused() {
        assertEquals(Result.ILLEGAL_CHARACTERS, HomeNameRules.check("my.base", "old", EXISTING));
    }

    @Test
    void aColonIsRefusedBecauseItReadsAsACommandNamespace() {
        assertEquals(Result.ILLEGAL_CHARACTERS, HomeNameRules.check("ess:base", "old", EXISTING));
    }

    @Test
    void miniMessagePunctuationIsRefused() {
        assertEquals(Result.ILLEGAL_CHARACTERS,
                HomeNameRules.check("<red>base", "old", EXISTING));
        assertEquals(Result.ILLEGAL_CHARACTERS,
                HomeNameRules.check("<click:run_command:/op me>", "old", EXISTING));
    }

    @Test
    void nonAsciiIsRefusedSoAnOperatorCanAlwaysPrintTheName() {
        assertEquals(Result.ILLEGAL_CHARACTERS, HomeNameRules.check("cabaña", "old", EXISTING));
        assertEquals(Result.ILLEGAL_CHARACTERS, HomeNameRules.check("家", "old", EXISTING));
    }

    @Test
    void aNameAlreadyTakenIsRefused() {
        assertEquals(Result.DUPLICATE, HomeNameRules.check("shop", "base", EXISTING));
    }

    /**
     * EssentialsX folds a home name to lowercase, so "Shop" and "shop" are one home there.
     * Accepting the second as a rename would silently destroy one of them.
     */
    @Test
    void aDuplicateIsDetectedCaseInsensitively() {
        assertEquals(Result.DUPLICATE, HomeNameRules.check("SHOP", "base", EXISTING));
        assertEquals(Result.DUPLICATE, HomeNameRules.check("Shop", "base", EXISTING));
    }

    @Test
    void renamingToTheHomesOwnNameReportsUnchangedRatherThanDuplicate() {
        assertEquals(Result.UNCHANGED, HomeNameRules.check("base", "base", EXISTING));
        assertEquals(Result.UNCHANGED, HomeNameRules.check("BASE", "base", EXISTING));
    }

    @Test
    void theUnchangedCheckRunsBeforeTheDuplicateOne() {
        // "base" is in EXISTING and is also the current name. Reporting it as a duplicate would
        // tell the player another home has the name, which is false and confusing.
        assertEquals(Result.UNCHANGED, HomeNameRules.check("base", "base", EXISTING));
    }

    @Test
    void surroundingWhitespaceIsStrippedBeforeEveryCheck() {
        assertEquals(Result.OK, HomeNameRules.check("  cabin  ", "base", EXISTING));
        assertEquals(Result.DUPLICATE, HomeNameRules.check("  shop  ", "base", EXISTING));
        assertEquals("cabin", HomeNameRules.normalize("  cabin  "));
    }

    /**
     * Case is left alone on purpose: EssentialsX folds it and HuskHomes does not, so folding here
     * would make this plugin's icon key disagree with HuskHomes' own name for the same home.
     */
    @Test
    void normalizeDoesNotFoldCase() {
        assertEquals("MyCabin", HomeNameRules.normalize("  MyCabin  "));
    }

    @Test
    void aNullOrEmptyExistingListIsHandled() {
        assertEquals(Result.OK, HomeNameRules.check("cabin", "base", null));
        assertEquals(Result.OK, HomeNameRules.check("cabin", "base", List.of()));
    }

    @Test
    void everyResultCarriesItsOwnMessageKey() {
        for (Result result : Result.values()) {
            assertTrue(result.messageKey() != null && !result.messageKey().isBlank(),
                    result + " has no message key, so the player would be told nothing");
        }
    }
}
