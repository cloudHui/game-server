package com.cloud.hub.game.domain.mj.claim;

import proto.ConstProto;

/**
 * claim 操作枚举工具。
 *
 * <p>将 {@link ConstProto.Operation} 转为中文名，集中维护，
 * 避免 MjClaimDetector / MjClaimExecutor 等多处各自重复定义相同 switch。
 * 新增操作类型时只需在此处追加一个 case。
 */
public final class MjChoiceUtil {

    private MjChoiceUtil() {
    }

    /**
     * 操作枚举转中文，用于录像记录与日志。
     *
     * @param choice 麻将操作枚举
     * @return 中文名称；未知类型回退到枚举英文名
     */
    public static String choiceName(ConstProto.Operation choice) {
        switch (choice) {
            case MJ_HU:   return "\u80e1";
            case MJ_GANG: return "\u6760";
            case MJ_PENG: return "\u78b0";
            case MJ_CHI:  return "\u5403";
            case MJ_PASS: return "\u8fc7";
            default:      return choice.name();
        }
    }
}
