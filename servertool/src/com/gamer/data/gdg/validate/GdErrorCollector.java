package com.gamer.data.gdg.validate;

import java.util.ArrayList;
import java.util.List;

/**
 * GD 校验错误收集器：整本 Excel 最多记录 {@link #MAX_ERRORS} 条，满额后中止。
 */
public class GdErrorCollector {

    /** 整本 Excel 最多记录的错误条数 */
    public static final int MAX_ERRORS = 5;

    /** 已收集的错误文案 */
    private final List<String> errors = new ArrayList<>();

    /** 是否因错误条数超限而中止 */
    private boolean aborted;

    /**
     * 追加一条错误；满 {@link #MAX_ERRORS} 条后置 aborted 并返回 false。
     *
     * @param message
     *            错误描述
     * @return 是否可继续扫描
     */
    public synchronized boolean addError(String message) {
        if (aborted) {
            return false;
        }
        errors.add(message);
        if (errors.size() >= MAX_ERRORS) {
            aborted = true;
        }
        return !aborted;
    }

    /**
     * 追加错误；满额时抛 {@link GdValidationAbortException} 以中断 SAX。
     *
     * @param message
     *            错误描述
     * @throws GdValidationAbortException
     *             满额中止
     */
    public void addErrorOrAbort(String message) throws GdValidationAbortException {
        if (!addError(message)) {
            throw new GdValidationAbortException();
        }
    }

    /**
     * 是否存在已记录错误。
     *
     * @return 是否有错误
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * 是否应停止继续扫描。
     *
     * @return 是否停止
     */
    public boolean shouldStop() {
        return aborted;
    }

    /**
     * 获取已收集的错误列表（只读副本）。
     *
     * @return 错误文案列表
     */
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }
}
