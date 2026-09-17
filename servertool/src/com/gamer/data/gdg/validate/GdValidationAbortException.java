package com.gamer.data.gdg.validate;

/**
 * GD 校验错误满额后的受控中止异常（用于中断 SAX 早停）。
 */
public class GdValidationAbortException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * 构造受控中止异常。
     */
    public GdValidationAbortException() {
        super("GD validation aborted");
    }
}
