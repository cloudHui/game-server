package com.cloud.hub.game.domain.cards;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 棋牌扑克与麻将牌面可读格式化工具类。
 * <p>
 * 为管理后台透视监控、运维日志切片与排障调试提供通用的牌面解析能力：
 * <ul>
 *   <li>扑克牌（斗地主、跑得快、拖拉机）：百位为花色，个十位为点数，区分红黑花色与大小王；</li>
 *   <li>麻将牌：百位为花色（万、条、筒、风、箭），个位为点数（1~9、东南西北、中发白）。</li>
 * </ul>
 * </p>
 *
 * @author cloud
 */
public final class CardFormatter {

    private CardFormatter() {
    }

    /**
     * 将牌面数值转换为带结构化元数据的字典对象（便于前端直观渲染花色与色彩）。
     *
     * @param gameType 玩法类型（1=麻将, 2=斗地主, 3=跑得快, 4=拖拉机）
     * @param cardId   牌 ID
     * @return 包含 id、name、suit、val、color、tag 的字典
     */
    public static Map<String, Object> toCardMap(int gameType, int cardId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", cardId);

        if (gameType == 1) {
            // 麻将
            int suit = cardId / 100;
            int val = cardId % 100;
            map.put("type", "mj");
            map.put("suit", suit);
            map.put("val", val);
            map.put("name", formatMahjong(cardId));
            map.put("color", getMahjongColor(suit));
        } else {
            // 扑克（斗地主、跑得快、拖拉机）
            int suit = cardId / 100;
            int val = cardId % 100;
            map.put("type", "poker");
            map.put("suit", suit);
            map.put("val", val);
            map.put("name", formatPoker(cardId));
            boolean isRed = (suit == 1 || suit == 3 || cardId == 517); // 方块、红桃、大王为红色
            map.put("color", isRed ? "red" : "black");
        }
        return map;
    }

    /**
     * 格式化扑克牌面（如 "黑桃A", "红桃K", "大王"）。
     *
     * @param cardId 扑克牌 ID
     * @return 中文显示名称
     */
    public static String formatPoker(int cardId) {
        if (cardId == 516) {
            return "小王";
        }
        if (cardId == 517) {
            return "大王";
        }

        int suit = cardId / 100;
        int val = cardId % 100;

        String suitName;
        switch (suit) {
            case 1:
                suitName = "方块";
                break;
            case 2:
                suitName = "梅花";
                break;
            case 3:
                suitName = "红桃";
                break;
            case 4:
                suitName = "黑桃";
                break;
            default:
                suitName = "未知";
                break;
        }

        String valName;
        switch (val) {
            case 11:
                valName = "J";
                break;
            case 12:
                valName = "Q";
                break;
            case 13:
                valName = "K";
                break;
            case 14:
                valName = "A";
                break;
            case 15:
                valName = "2";
                break;
            default:
                valName = String.valueOf(val);
                break;
        }

        return suitName + valName;
    }

    /**
     * 格式化麻将牌面（如 "1万", "5条", "9筒", "东风", "红中"）。
     *
     * @param tileId 麻将牌 ID
     * @return 中文显示名称
     */
    public static String formatMahjong(int tileId) {
        int suit = tileId / 100;
        int val = tileId % 100;

        switch (suit) {
            case 1:
                return val + "万";
            case 2:
                return val + "条";
            case 3:
                return val + "筒";
            case 4:
                switch (val) {
                    case 1:
                        return "东风";
                    case 2:
                        return "南风";
                    case 3:
                        return "西风";
                    case 4:
                        return "北风";
                    default:
                        return "风" + val;
                }
            case 5:
                switch (val) {
                    case 1:
                        return "红中";
                    case 2:
                        return "发财";
                    case 3:
                        return "白板";
                    default:
                        return "箭" + val;
                }
            default:
                return "未知牌(" + tileId + ")";
        }
    }

    private static String getMahjongColor(int suit) {
        switch (suit) {
            case 1:
                return "red";   // 万字通常为红色
            case 2:
                return "green"; // 条子通常为绿色
            case 3:
                return "blue";  // 筒子通常为蓝色
            case 5:
                return "purple";// 箭牌中发白通常为紫色/深色
            default:
                return "black";
        }
    }
}
