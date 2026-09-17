package com.gamer.data.ui;

import java.awt.Color;
import java.awt.Font;

/**
 * 共用外观色板与字体。仅供 {@link ViewUi} 同包类使用。
 */
final class ViewPalette {

    /** 正文。 */
    static final Font FONT = new Font("微软雅黑", Font.PLAIN, 12);
    /** 分组标题。 */
    static final Font FONT_B = new Font("微软雅黑", Font.BOLD, 12);
    /** 边线。 */
    static final Color LINE = new Color(210, 216, 224);
    /** 卡片底。 */
    static final Color CARD = new Color(248, 250, 252);
    /** 页底。 */
    static final Color PAGE = new Color(242, 245, 249);
    /** 标题色。 */
    static final Color TITLE = new Color(55, 71, 90);
    /** 状态条底。 */
    static final Color STATUS = new Color(232, 238, 246);
    /** 提示字色。 */
    static final Color HINT = new Color(110, 118, 130);
    /** 日志底。 */
    static final Color LOG = new Color(252, 253, 254);
    /** 危险操作字色。 */
    static final Color DANGER = new Color(160, 40, 40);
    /** 暂停等次要实心按钮。 */
    static final Color WARN = new Color(196, 132, 40);
    /** 结束等实心危险按钮。 */
    static final Color HALT = new Color(168, 48, 48);
    /** 链接 / 当前步骤。 */
    static final Color LINK = new Color(30, 110, 190);
    /** 表格选中行。 */
    static final Color SELECT = new Color(201, 222, 242);

    private ViewPalette() {}
}
