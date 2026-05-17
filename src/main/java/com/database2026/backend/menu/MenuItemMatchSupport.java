package com.database2026.backend.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class MenuItemMatchSupport {

    private static final Pattern MENU_ITEM_SEPARATOR = Pattern.compile("\\s*(/|\\n|,|&)\\s*");

    private MenuItemMatchSupport() {
    }

    public record FoodCandidate(long foodId, String label, String normalizedLabel, int priority) {
    }

    public static List<String> splitMenuItems(String optionName) {
        if (optionName == null || optionName.isBlank()) {
            return List.of();
        }
        List<String> items = MENU_ITEM_SEPARATOR.splitAsStream(optionName)
                .map(MenuItemMatchSupport::cleanDisplayItem)
                .filter(item -> !item.isBlank())
                .toList();
        return items.isEmpty() ? List.of(cleanDisplayItem(optionName)) : items;
    }

    public static Long matchFoodId(String rawItemName, List<FoodCandidate> foodCandidates) {
        List<String> candidates = searchQueries(rawItemName).stream()
                .map(MenuItemMatchSupport::normalize)
                .filter(candidate -> candidate.length() >= 2)
                .distinct()
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }

        return foodCandidates.stream()
                .map(food -> new RankedFood(food.foodId(), matchScore(candidates, food), food.normalizedLabel().length()))
                .filter(food -> food.score() > 0)
                .max(Comparator
                        .comparingInt(RankedFood::score)
                        .thenComparingInt(RankedFood::matchedLength)
                        .thenComparingLong(RankedFood::foodId))
                .map(RankedFood::foodId)
                .orElse(null);
    }

    public static List<String> searchQueries(String rawItemName) {
        String cleaned = cleanDisplayItem(rawItemName);
        if (cleaned.isBlank()) {
            return List.of();
        }

        Set<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, cleaned);

        String withoutSauce = cleaned.replaceAll("\\*.*$", "").trim();
        addCandidate(candidates, withoutSauce);

        String withoutPrefix = withoutSauce.replaceAll("^[가-힣A-Za-z0-9]{1,3}\\)", "").trim();
        addCandidate(candidates, withoutPrefix);

        if (withoutPrefix.endsWith("덮밥") && withoutPrefix.length() > 2) {
            addCandidate(candidates, withoutPrefix.substring(0, withoutPrefix.length() - 2));
        }

        List<String> expanded = new ArrayList<>(candidates);
        for (String candidate : expanded) {
            addCandidate(candidates, candidate.replace("돈까스", "돈가스").replace("까스", "가스"));
            addCandidate(candidates, candidate.replace("돈육", "돼지고기"));
            addCandidate(candidates, candidate.replace("쇠고기", "소고기"));
            addCandidate(candidates, candidate.replace("계란", "달걀"));
            addCandidate(candidates, candidate.replace("달걀", "계란"));
        }

        return candidates.stream()
                .map(String::trim)
                .filter(candidate -> candidate.length() >= 2)
                .distinct()
                .toList();
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "")
                .replace("[", "")
                .replace("]", "")
                .replace("{", "")
                .replace("}", "")
                .replace("·", "")
                .replace(".", "")
                .trim();
    }

    private static int matchScore(List<String> candidates, FoodCandidate food) {
        String foodName = food.normalizedLabel();
        if (foodName.length() < 2) {
            return 0;
        }

        int bestScore = 0;
        for (String candidate : candidates) {
            if (candidate.length() < 2) {
                continue;
            }
            if (candidate.equals(foodName)) {
                bestScore = Math.max(bestScore, 10_000 + food.priority());
            } else if (candidate.contains(foodName)) {
                bestScore = Math.max(bestScore, 5_000 + foodName.length() * 10 + food.priority());
            } else if (containsAllMeaningfulFoodTokens(candidate, food.label())) {
                bestScore = Math.max(bestScore, 4_500 + foodName.length() * 10 + food.priority());
            } else if (foodName.contains(candidate) && candidate.length() >= 3) {
                bestScore = Math.max(bestScore, 3_000 + candidate.length() * 10 + food.priority());
            }
        }
        return bestScore;
    }

    private static boolean containsAllMeaningfulFoodTokens(String candidate, String foodLabel) {
        List<String> tokens = Pattern.compile("[_\\s]+")
                .splitAsStream(foodLabel == null ? "" : foodLabel)
                .map(MenuItemMatchSupport::normalize)
                .filter(token -> token.length() >= 2)
                .toList();
        return tokens.size() >= 2 && tokens.stream().allMatch(candidate::contains);
    }

    private static void addCandidate(Set<String> candidates, String value) {
        if (value != null && !value.isBlank()) {
            candidates.add(value.trim());
        }
    }

    private static String cleanDisplayItem(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("(?i)\\d{2,4}\\s*kcal", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record RankedFood(long foodId, int score, int matchedLength) {
    }
}
