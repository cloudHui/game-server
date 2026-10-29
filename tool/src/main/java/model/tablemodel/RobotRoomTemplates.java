package model.tablemodel;

/**
 * 系统内置快速机器人房间模板。
 *
 * <p>
 * 经典多人房（1/2/5/11/12）统一由 {@code TableModel.xlsx} 配表维护；
 * 进桌自动补满机器人的快速体验房（9001..9020）在此统一定义，供 lobby 与 game 共享。
 * </p>
 */
public final class RobotRoomTemplates {

    /** 麻将快速机器人房模板 ID */
    public static final int MAHJONG_ROOM_ID = 9001;
    /** 斗地主（叫地主）快速机器人房模板 ID */
    public static final int DOU_DIZHU_ROOM_ID = 9002;
    /** 斗地主（抢地主）快速机器人房模板 ID */
    public static final int DOU_DIZHU_ROB_ROOM_ID = 9003;
    /** 跑得快快速机器人房模板 ID */
    public static final int PAO_DE_KUAI_ROOM_ID = 9010;
    /** 拖拉机快速机器人房模板 ID */
    public static final int TRACTOR_ROOM_ID = 9011;
    /** 牛牛快速机器人房模板 ID */
    public static final int NIUNIU_ROOM_ID = 9020;

    private RobotRoomTemplates() {
    }

    /**
     * 判断指定房间模板是否为进桌自动补满机器人的快速房。
     *
     * @param roomId 房间模板 ID
     * @return true 表示自动补满机器人的快速房
     */
    public static boolean isRobotRoom(int roomId) {
        return roomId == MAHJONG_ROOM_ID || roomId == DOU_DIZHU_ROOM_ID || roomId == DOU_DIZHU_ROB_ROOM_ID
                || roomId == PAO_DE_KUAI_ROOM_ID || roomId == TRACTOR_ROOM_ID || roomId == NIUNIU_ROOM_ID;
    }

    /**
     * 麻将快速房：4 人 13 张。
     *
     * @return 麻将快速房模板实例
     */
    public static TableModel mahjong() {
        TableModel m = quickBase(MAHJONG_ROOM_ID, 1, 4, 13, 0, 0);
        m.setAllowChi(1);
        m.setAllowDianPao(1);
        m.setAllowPeng(1);
        m.setAllowGang(1);
        m.setAllowHu(1);
        m.setAllowSevenPairs(1);
        m.setGangScore(1);
        m.setAllowGangMing(1);
        m.setAllowGangAn(1);
        m.setAllowGangBu(1);
        return m;
    }

    /**
     * 斗地主（叫地主）快速房：3 人 17 张手牌 + 3 张底牌。
     *
     * @return 斗地主快速房模板实例
     */
    public static TableModel douDiZhu() {
        return quickBase(DOU_DIZHU_ROOM_ID, 2, 3, 17, 3, 0);
    }

    /**
     * 斗地主（抢地主）快速房：叫地主后进入逆时针抢地主。
     *
     * @return 斗地主抢地主快速房模板实例
     */
    public static TableModel douDiZhuRob() {
        return quickBase(DOU_DIZHU_ROB_ROOM_ID, 2, 3, 17, 3, 1);
    }

    /**
     * 跑得快快速房：3 人 16 张。
     *
     * @return 跑得快快速房模板实例
     */
    public static TableModel paoDeKuai() {
        return quickBase(PAO_DE_KUAI_ROOM_ID, 3, 3, 16, 0, 0);
    }

    /**
     * 拖拉机快速房：4 人 25 张 + 8 张底牌。
     *
     * @return 拖拉机快速房模板实例
     */
    public static TableModel tractor() {
        return quickBase(TRACTOR_ROOM_ID, 4, 4, 25, 8, 0);
    }

    /**
     * 牛牛快速房：4 人 5 张明牌抢庄。
     *
     * @return 牛牛快速房模板实例
     */
    public static TableModel niuniu() {
        return quickBase(NIUNIU_ROOM_ID, 5, 4, 5, 0, 0);
    }

    /**
     * 向配置管理器统一注册所有快速机器人房模板。
     *
     * @param manager 配置注册门面回调
     */
    public static void register(TableConfigManagerFacade manager) {
        manager.put(mahjong());
        manager.put(douDiZhu());
        manager.put(douDiZhuRob());
        manager.put(paoDeKuai());
        manager.put(tractor());
        manager.put(niuniu());
    }

    private static TableModel quickBase(int id, int type, int seatNum, int cardNum, int exCardNum, int subType) {
        TableModel m = new TableModel();
        m.setId(id);
        m.setType(type);
        m.setSeatNum(seatNum);
        m.setCardNum(cardNum);
        m.setExCardNum(exCardNum);
        m.setBaseScore(1);
        m.setMaxFan(16);
        m.setGameSubType(subType);
        m.setTotalRounds(4);
        m.setAutoNextRound(1);
        m.setAutoPlay(1);
        return m;
    }

    /**
     * 让 lobby/game 的配置管理器保持最小耦合。
     */
    public interface TableConfigManagerFacade {
        /**
         * 注册单个房间配置模板。
         *
         * @param model 房间模板实例
         */
        void put(TableModel model);
    }
}
