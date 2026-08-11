package com.ktb.chatapp.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannedWordCheckerTest {

    private static final Path WORD_LIST_PATH =
            Path.of("src/main/resources/fake_banned_words_10k.txt");
    private static final List<String> LOADED_WORDS = loadDictionary();
    private static final Set<String> BANNED_WORDS = new HashSet<>(LOADED_WORDS);

    private static List<String> loadDictionary() {
        try {
            List<String> words =
                    Files.readAllLines(WORD_LIST_PATH).stream()
                            .map(String::trim)
                            .filter(word -> !word.isEmpty())
                            .toList();
            if (words.isEmpty()) {
                throw new IllegalStateException("Banned word dictionary must not be empty.");
            }
            return words;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load banned word dictionary.", e);
        }
    }

    @Test
    void containsBannedWord_detectsExactWord() {
        BannedWordChecker checker = new BannedWordChecker(BANNED_WORDS);
        assertTrue(checker.containsBannedWord(LOADED_WORDS.getFirst()));
    }

    @Test
    void containsBannedWord_detectsWordEmbeddedInMessage() {
        BannedWordChecker checker = new BannedWordChecker(BANNED_WORDS);
        String message = "prefix-" + LOADED_WORDS.getFirst() + "-suffix";
        assertTrue(checker.containsBannedWord(message));
    }

    @Test
    void containsBannedWord_returnsFalseForCleanOrEmptyInput() {
        BannedWordChecker checker = new BannedWordChecker(BANNED_WORDS);
        assertFalse(checker.containsBannedWord("safe message without banned tokens"));
        assertFalse(checker.containsBannedWord(null));
        assertFalse(checker.containsBannedWord("   "));
    }

    @Test
    void containsBannedWord_preservesCaseInsensitiveKoreanAndSubstringBehavior() {
        BannedWordChecker checker = new BannedWordChecker(Set.of("BadWord", "금칙어"));

        assertTrue(checker.containsBannedWord("prefix-BADword-suffix"));
        assertTrue(checker.containsBannedWord("이문장에는금칙어가포함됨"));
        assertFalse(checker.containsBannedWord("bad word와 금칙 어는 분리되어 있음"));
    }

    @Test
    void containsBannedWord_matchesNaiveImplementationForRandomInputs() {
        Set<String> dictionary = Set.of("abc", "BCd", "한글", "테스트", "xy", "가나");
        BannedWordChecker checker = new BannedWordChecker(dictionary);
        Set<String> normalized = dictionary.stream()
                .map(word -> word.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        String alphabet = "abcDxyz가나다한글테스트 123";
        Random random = new Random(20260812L);

        for (int sample = 0; sample < 2_000; sample++) {
            int length = random.nextInt(80);
            StringBuilder message = new StringBuilder(length);
            for (int index = 0; index < length; index++) {
                message.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            String candidate = message.toString();
            boolean expected = normalized.stream()
                    .anyMatch(candidate.toLowerCase(Locale.ROOT)::contains);
            org.junit.jupiter.api.Assertions.assertEquals(
                    expected,
                    checker.containsBannedWord(candidate),
                    () -> "Mismatch for: " + candidate);
        }
    }
}
