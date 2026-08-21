package com.cloud.hub.game.arena.crafting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CraftingRulesTest {
    @Test
    public void recipeHasIndependentInputAndCost() {
        CraftingRules.Recipe recipe = CraftingRules.recipe("qi_pill");
        assertEquals("herb", recipe.input);
        assertEquals(3, recipe.inputCount);
        assertEquals(100, recipe.coinCost);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownRecipe() {
        CraftingRules.recipe("unknown");
    }
}
