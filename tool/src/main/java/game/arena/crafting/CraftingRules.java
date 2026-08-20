package game.arena.crafting;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 无尽书阁纯配方规则。 */
public final class CraftingRules {
    private CraftingRules() {
    }

    private static final Map<String, Recipe> RECIPES = new LinkedHashMap<>();

    static {
        add(new Recipe("qi_pill", "炼气丹", "herb", 3, "pill", 1, 100));
        add(new Recipe("star_pill", "升星丹", "star_dust", 5, "star_pill", 1, 300));
        add(new Recipe("refine_stone", "精炼石", "ore", 4, "refine_stone", 2, 180));
        add(new Recipe("green_sword", "青锋法宝", "ore", 10, "green_sword", 1, 600));
    }

    private static void add(Recipe recipe) {
        RECIPES.put(recipe.id, recipe);
    }

    public static Collection<Recipe> recipes() {
        return Collections.unmodifiableCollection(RECIPES.values());
    }

    public static Recipe recipe(String id) {
        Recipe recipe = RECIPES.get(id);
        if (recipe == null) {
            throw new IllegalArgumentException("未知配方");
        }
        return recipe;
    }

    public static final class Recipe {
        public final String id;
        public final String name;
        public final String input;
        public final String output;
        public final int inputCount;
        public final int outputCount;
        public final int coinCost;

        public Recipe(String id, String name, String input, int inputCount,
                      String output, int outputCount, int coinCost) {
            this.id = id;
            this.name = name;
            this.input = input;
            this.inputCount = inputCount;
            this.output = output;
            this.outputCount = outputCount;
            this.coinCost = coinCost;
        }
    }
}
