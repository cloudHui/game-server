package game.arena.crafting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CraftingRulesTest {
    @Test
    public void recipeExposesKnownMaterialAndCost() {
        CraftingRules.Recipe recipe = CraftingRules.recipe("qi_pill");
        assertEquals("herb", recipe.input);
        assertEquals(3, recipe.inputCount);
        assertEquals(100, recipe.coinCost);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownRecipeIsRejected() {
        CraftingRules.recipe("unknown");
    }
}
