package com.cloud.hub.web.arena;

import com.cloud.hub.game.arena.crafting.CraftingRules;
import com.cloud.hub.web.arena.repository.ArenaInventoryRepository;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 竞技场炼丹与器物合成服务。
 * <p>
 * 提供玩家配方列表查询及根据配方规则消耗原料与灵币进行物品合成的功能。
 *
 * @author cloud
 */
@Service
public class ArenaCraftService {

    private final ArenaInventoryRepository items;

    public ArenaCraftService(ArenaInventoryRepository items) {
        this.items = items;
    }

    /**
     * 查询玩家已拥有的物品库存和所有可合成的配方列表。
     *
     * @param uid 玩家 ID
     * @return 物品库存和配方全景
     * @throws SQLException 数据库异常
     */
    public Map<String, Object> view(long uid) throws SQLException {
        items.seed(uid);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items.list(uid));
        out.put("recipes", CraftingRules.recipes());
        return out;
    }

    /**
     * 执行指定配方的物品合成。
     *
     * @param uid      玩家 ID
     * @param recipeId 配方 ID
     * @return 合成后的最新视图
     * @throws SQLException 数据库异常
     */
    public Map<String, Object> craft(long uid, String recipeId) throws SQLException {
        CraftingRules.Recipe recipe = CraftingRules.recipe(recipeId);
        items.seed(uid);
        items.craft(uid, recipe.input, recipe.inputCount, recipe.output, recipe.outputCount, recipe.coinCost);
        return view(uid);
    }
}
