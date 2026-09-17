package com.gamer.data.process;

/** 子进程文本输出。 */
@FunctionalInterface
public interface ProcessOutput {

    /**
     * @param line
     *            不含换行符的输出行
     * @throws Exception
     *             消费失败
     */
    void accept(String line) throws Exception;
}
