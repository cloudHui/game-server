package com.cloud.hub.lobby.admin;

import model.tablemodel.TableModel;
import model.tablemodel.TableModelJson;

import java.util.Map;

/** 管理员机器人测试的规则覆盖；生命周期开关由服务端固定。 */
public final class AdminRobotMatchRules {
    private AdminRobotMatchRules() {
    }

    public static TableModel create(TableModel source, Map<String, String> input) {
        TableModel model = TableModelJson.parse(TableModelJson.toJson(source));
        if (model == null) throw new IllegalArgumentException("规则无效");
        model.setTotalRounds(value(input, "totalRounds", 3, 1, 20));
        model.setBaseScore(value(input, "baseScore", source.getBaseScore(), 1, 100));
        model.setMaxFan(value(input, "maxFan", source.getMaxFan(), 1, 128));
        if (model.getType() == 1) {
            model.setAllowChi(flag(input, "allowChi", source.getAllowChi()));
            model.setAllowDianPao(flag(input, "allowDianPao", source.getAllowDianPao()));
            model.setAllowGang(flag(input, "allowGang", source.getAllowGang()));
            model.setAllowSevenPairs(flag(input, "allowSevenPairs", source.getAllowSevenPairs()));
            model.setAllowMultiHu(flag(input, "allowMultiHu", source.getAllowMultiHu()));
        }
        model.setAutoNextRound(1);
        model.setAutoPlay(1);
        return model;
    }

    private static int flag(Map<String, String> input, String key, int fallback) {
        return value(input, key, fallback, 0, 1);
    }

    private static int value(Map<String, String> input, String key, int fallback, int min, int max) {
        String raw = input.get(key);
        int value;
        try {
            value = raw == null ? fallback : Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " 格式错误");
        }
        if (value < min || value > max) throw new IllegalArgumentException(key + " 超出范围");
        return value;
    }
}
