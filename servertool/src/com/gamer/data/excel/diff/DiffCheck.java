package com.gamer.data.excel.diff;

import javax.swing.JFrame;

import com.gamer.data.excel.diff.ui.ExcelGDDiffView;
import com.gamer.data.excel.framework.BaseCheck;

/**
 * Excel / GD 对比工具入口（创建 {@link com.gamer.data.excel.diff.ui.ExcelGDDiffView}）。
 *
 * @author liuyunhui
 * @date 2025/12/05
 */
public class DiffCheck extends BaseCheck {
    public DiffCheck() {}

    @Override
    protected JFrame createViewer() {
        return new ExcelGDDiffView(this);
    }

    /**
     * @return StrategyTool
     */
    @Override
    protected String toolName() {
        return "StrategyTool";
    }

}