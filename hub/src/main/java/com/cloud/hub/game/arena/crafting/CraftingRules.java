package com.cloud.hub.game.arena.crafting;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 剑气除魔 · 炼器法宝合成纯规则引擎。
 * <p>
 * 纯内存计算，无 HTTP、SQL、系统时间依赖。
 * 管理炼气丹、升星丹、精炼石及青锋法宝等合成配方与材料消耗。
 *
 * @author cloud
 */
public final class CraftingRules {
    /** 全局只读配方字典映射 (配方ID -> 配方规则) */
    private static final Map<String, Recipe> RECIPES = new LinkedHashMap<>();

    static {
        add(new Recipe("qi_pill", "炼气丹", "herb", 3, "pill", 1, 100));
        add(new Recipe("star_pill", "升星丹", "star_dust", 5, "star_pill", 1, 300));
        add(new Recipe("refine_stone", "精炼石", "ore", 4, "refine_stone", 2, 180));
        add(new Recipe("green_sword", "青锋法宝", "ore", 10, "green_sword", 1, 600));
    }

    private CraftingRules() {
    }

    /** 内部注册合成配方 */
    private static void add(Recipe recipe) {
        RECIPES.put(recipe.id, recipe);
    }

    /**
     * 获取当前所有受支持的合成配方集合。
     *
     * @return 不可修改的配方列表视图
     */
    public static Collection<Recipe> recipes() {
        return Collections.unmodifiableCollection(RECIPES.values());
    }

    /**
     * 根据配方唯一标识检索指定配方。
     *
     * @param id 配方唯一标识
     * @return 匹配的配方规则
     * @throws IllegalArgumentException 当配方不存在时抛出
     */
    public static Recipe recipe(String id) {
        Recipe recipe = RECIPES.get(id);
        if (recipe == null) {
            throw new IllegalArgumentException("未知配方: " + id);
        }
        return recipe;
    }

    /**
     * 合成配方定义实体。
     */
    public static final class Recipe {
        /** 配方ID */
        public final String id;
        /** 产物展示名称 */
        public final String name;
        /** 消耗的基础素材类型 */
        public final String input;
        /** 产出的物品类型 */
        public final String output;
        /** 消耗素材数量 */
        public final int inputCount;
        /** 产出物品数量 */
        public final int outputCount;
        /** 消耗灵币数量 */
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
