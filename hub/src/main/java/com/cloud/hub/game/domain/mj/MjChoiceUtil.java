package com.cloud.hub.game.domain.mj;

import proto.ConstProto;

/**
 * claim \u64cd\u4f5c\u679a\u4e3e\u5de5\u5177\u3002
 *
 * <p>\u5c06 {@link ConstProto.Operation} \u8f6c\u4e3a\u4e2d\u6587\u540d\uff0c\u96c6\u4e2d\u7ef4\u62a4\uff0c
 * \u907f\u514d MjClaimDetector / MjClaimExecutor \u7b49\u591a\u5904\u5404\u81ea\u91cd\u590d\u5b9a\u4e49\u76f8\u540c switch\u3002
 * \u65b0\u589e\u64cd\u4f5c\u7c7b\u578b\u65f6\u53ea\u9700\u5728\u6b64\u5904\u8ffd\u52a0\u4e00\u4e2a case\u3002
 */
final class MjChoiceUtil {

    private MjChoiceUtil() {
    }

    /**
     * \u64cd\u4f5c\u679a\u4e3e\u8f6c\u4e2d\u6587\uff0c\u7528\u4e8e\u5f55\u50cf\u8bb0\u5f55\u4e0e\u65e5\u5fd7\u3002
     *
     * @param choice \u9ebb\u5c06\u64cd\u4f5c\u679a\u4e3e
     * @return \u4e2d\u6587\u540d\u79f0\uff1b\u672a\u77e5\u7c7b\u578b\u56de\u9000\u5230\u679a\u4e3e\u82f1\u6587\u540d
     */
    static String choiceName(ConstProto.Operation choice) {
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
