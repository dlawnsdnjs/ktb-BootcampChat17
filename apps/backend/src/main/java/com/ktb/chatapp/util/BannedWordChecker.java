package com.ktb.chatapp.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.Assert;

public class BannedWordChecker {
    
    private final List<Node> automaton;
    
    public BannedWordChecker(Set<String> bannedWords) {
        Set<String> normalizedWords =
                bannedWords.stream()
                        .filter(word -> word != null && !word.isBlank())
                        .map(word -> word.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
        Assert.notEmpty(normalizedWords, "Banned words set must not be empty");
        this.automaton = buildAutomaton(normalizedWords);
    }
    
    public boolean containsBannedWord(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        int state = 0;
        for (int index = 0; index < normalizedMessage.length(); index++) {
            char current = normalizedMessage.charAt(index);
            while (state != 0 && !automaton.get(state).transitions.containsKey(current)) {
                state = automaton.get(state).failure;
            }
            state = automaton.get(state).transitions.getOrDefault(current, 0);
            if (automaton.get(state).terminal) {
                return true;
            }
        }
        return false;
    }

    private List<Node> buildAutomaton(Set<String> words) {
        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node());

        for (String word : words) {
            int state = 0;
            for (int index = 0; index < word.length(); index++) {
                char current = word.charAt(index);
                Integer next = nodes.get(state).transitions.get(current);
                if (next == null) {
                    next = nodes.size();
                    nodes.get(state).transitions.put(current, next);
                    nodes.add(new Node());
                }
                state = next;
            }
            nodes.get(state).terminal = true;
        }

        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int child : nodes.getFirst().transitions.values()) {
            queue.add(child);
        }

        while (!queue.isEmpty()) {
            int state = queue.remove();
            Node node = nodes.get(state);
            for (Map.Entry<Character, Integer> transition : node.transitions.entrySet()) {
                char current = transition.getKey();
                int child = transition.getValue();
                int failure = node.failure;
                while (failure != 0 && !nodes.get(failure).transitions.containsKey(current)) {
                    failure = nodes.get(failure).failure;
                }
                nodes.get(child).failure = nodes.get(failure).transitions.getOrDefault(current, 0);
                nodes.get(child).terminal |= nodes.get(nodes.get(child).failure).terminal;
                queue.add(child);
            }
        }

        return List.copyOf(nodes);
    }

    private static final class Node {
        private final Map<Character, Integer> transitions = new HashMap<>();
        private int failure;
        private boolean terminal;
    }
}
