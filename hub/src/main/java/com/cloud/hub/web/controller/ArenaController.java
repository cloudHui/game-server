package com.cloud.hub.web.controller;

import com.cloud.hub.common.annotation.RequiresLogin;
import com.cloud.hub.framework.security.SecurityUtils;
import com.cloud.hub.game.arena.ArenaBattleEngine;
import com.cloud.hub.game.arena.ArenaBattleEngine.Hero;
import com.cloud.hub.game.arena.ArenaRules;
import com.cloud.hub.web.arena.ArenaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 剑气除魔 HTTP 控制器。
 * <p>
 * 对外提供修仙图鉴、角色修行快照、修行行为操作及回合制对战仿真接口。
 *
 * @author cloud
 */
@RestController
@RequestMapping("/api/arena")
public class ArenaController {

    private final ArenaRepository repository;

    public ArenaController(ArenaRepository repository) {
        this.repository = repository;
    }

    /** 预设仙修名册（包含金/红/橙/紫四阶修士设定） */
    private static final List<Hero> HEROES = Arrays.asList(
            new Hero("jianhuang", "无极剑皇", "金", "强攻", 8500, 1180, 380, 610, "万剑归宗", 260, 15, 20),
            new Hero("leizun", "九霄雷尊", "金", "控制", 7600, 960, 360, 590, "神霄万劫", 230, 28, 15),
            new Hero("yaohuang", "长生药皇", "金", "坚守", 9800, 700, 500, 420, "万古长春", 150, 9, 0),
            new Hero("luocha", "血海罗刹", "金", "强攻", 8200, 1080, 330, 540, "血海无涯", 240, 12, 28),
            new Hero("xingjun", "紫微星君", "金", "坚守", 9000, 880, 460, 510, "周天星陨", 205, 22, 0),
            new Hero("kongming", "空明道祖", "金", "控制", 8000, 900, 400, 650, "六道禁法", 180, 38, 0),
            new Hero("taixu", "太虚剑主", "红", "强攻", 6500, 820, 300, 520, "一剑破万法", 210, 18, 10),
            new Hero("chixiao", "赤霄真君", "橙", "强攻", 5600, 760, 210, 480, "赤霄焚天", 195, 8, 12),
            new Hero("zhenyue", "镇岳尊者", "橙", "坚守", 7200, 510, 390, 350, "不动山河", 125, 0, 0),
            new Hero("mingwang", "不动明王", "橙", "坚守", 7800, 500, 430, 320, "明王净世", 135, 16, 0),
            new Hero("qinglan", "青岚剑君", "紫", "灵巧", 5200, 620, 240, 560, "青岚九式", 175, 12, 0),
            new Hero("xuanshuang", "玄霜仙子", "紫", "控制", 5400, 590, 260, 500, "玄霜封脉", 145, 32, 0),
            new Hero("jinghong", "惊鸿影", "紫", "灵巧", 4900, 680, 220, 650, "无影绝杀", 165, 10, 18),
            new Hero("lingxi", "灵犀药师", "紫", "控制", 6000, 540, 280, 440, "万木回春", 120, 7, 0)
    );

    /**
     * 获取剑气除魔全局图鉴元数据（英雄列表、四大神通道法、六阶神兽、四阶属性丹及装备）。
     *
     * @return 游戏配置图鉴全集
     */
    @GetMapping("/catalog")
    public Map<String, Object> catalog() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("heroes", HEROES);
        out.put("ruleVersion", "arena-v2-infinite");
        out.put("maxFloors", ArenaRules.MAX_TOWER_FLOORS);
        out.put("maxPills", ArenaRules.MAX_PILLS_TOTAL);

        // 四大神通道法
        List<Map<String, Object>> skills = new ArrayList<>();
        skills.add(mapOf("id", "silence", "name", "太虚封魔咒", "tag", "沉默打断", "desc", "35% 几率沉默敌人2回合，封禁大招与被动回血，专克高攻与自愈怪"));
        skills.add(mapOf("id", "pierce", "name", "九天破甲决", "tag", "高攻破甲", "desc", "无视目标 50% 防御，暴击伤害 +60%，刀刀真伤斩碎铁壁"));
        skills.add(mapOf("id", "defense", "name", "金刚不坏身", "tag", "霸体高防", "desc", "最终伤害减免 40%，开局获 30% 生命护盾，受击反震 15% 伤害"));
        skills.add(mapOf("id", "heal", "name", "青帝长生引", "tag", "吸血回生", "desc", "造成伤害 40% 转化为吸血，回合结束自愈 10% 最大生命值"));
        out.put("skills", skills);

        // 六大坐骑神兽
        List<Map<String, Object>> beasts = new ArrayList<>();
        beasts.add(mapOf("tier", 1, "name", "踏云鹿", "icon", "🦌", "talent", "【踏云步】身法闪避率 +10%"));
        beasts.add(mapOf("tier", 2, "name", "幽冥白虎", "icon", "🐯", "talent", "【杀伐煞】全队暴击伤害 +35%"));
        beasts.add(mapOf("tier", 3, "name", "九天朱雀", "icon", "🦅", "talent", "【涅槃火】阵亡时 100% 涅槃重生"));
        beasts.add(mapOf("tier", 4, "name", "辟邪玄武", "icon", "🐢", "talent", "【玄武甲】开局提供 25% 护体灵盾"));
        beasts.add(mapOf("tier", 5, "name", "太初青龙", "icon", "🐲", "talent", "【龙威镇】敌方全体攻击削减 20%"));
        beasts.add(mapOf("tier", 6, "name", "鸿蒙麒麟", "icon", "🦄", "talent", "【祥瑞辟】全属性 +25%，挂机收益翻倍"));
        out.put("beasts", beasts);

        // 四阶属性丹
        List<Map<String, Object>> pills = new ArrayList<>();
        pills.add(mapOf("tier", 1, "name", "洗髓丹", "source", "镇魔塔首通", "max", 10, "stat", "气血+400, 攻击+40"));
        pills.add(mapOf("tier", 2, "name", "聚灵丹", "source", "镇魔塔首通", "max", 10, "stat", "气血+800, 攻击+80, 防御+30"));
        pills.add(mapOf("tier", 3, "name", "九幽化虚丹", "source", "兽神塔首通", "max", 15, "stat", "气血+1500, 攻击+150, 防御+60"));
        pills.add(mapOf("tier", 4, "name", "太清飞升神丹", "source", "仙缘购买(8仙缘/颗)", "max", 15, "stat", "气血+3000, 攻击+300, 防御+120, 暴击+1%"));
        out.put("pills", pills);

        out.put("gear", Arrays.asList("诛仙断界剑", "诛仙剑袍", "诛仙冠", "诛仙剑印", "玄武镇海刃", "玄武神甲", "玄武冕", "玄武定海珠", "天机羽扇", "天机云裳", "天机星冠", "天机罗盘"));
        return out;
    }

    /**
     * 获取当前登录玩家的实时修仙修行状态。
     *
     * @return 玩家状态数据
     */
    @GetMapping("/state")
    @RequiresLogin
    public ResponseEntity<?> state() throws Exception {
        return ResponseEntity.ok(repository.state(SecurityUtils.getUserId()));
    }

    /**
     * 处理玩家修行行为操作（如升级洞府、挑战塔、服丹等）。
     *
     * @param body 操作参数包
     * @return 更新后的玩家状态
     */
    @PostMapping("/action")
    @RequiresLogin
    public ResponseEntity<?> action(@RequestBody Map<String, Object> body) throws Exception {
        String action = String.valueOf(body.get("action"));
        String id = body.get("id") == null ? "" : String.valueOf(body.get("id"));
        int count = body.get("count") instanceof Number ? ((Number) body.get("count")).intValue() : 1;
        return ResponseEntity.ok(repository.action(SecurityUtils.getUserId(), action, id, count, System.currentTimeMillis()));
    }

    /**
     * 执行回合制战斗模拟并返回事件流水。
     *
     * @param attacker 攻击方英雄 ID
     * @param defender 防守方英雄 ID
     * @param skill 携带神通 ID
     * @param stage 关卡阶段编号
     * @param seed 随机种子
     * @return 仿真战斗回放与结算
     */
    @GetMapping("/battle")
    @RequiresLogin
    public ResponseEntity<?> battle(@RequestParam(defaultValue = "jianhuang") String attacker,
                                    @RequestParam(defaultValue = "leizun") String defender,
                                    @RequestParam(defaultValue = "pierce") String skill,
                                    @RequestParam(defaultValue = "0") int stage,
                                    @RequestParam(defaultValue = "42") long seed) {
        Hero a = find(attacker);
        Hero d = find(defender);
        ArenaBattleEngine.BattleResult result = ArenaBattleEngine.simulate(a, d, skill, stage, seed);
        return ResponseEntity.ok(result.toMap());
    }

    private Hero find(String id) {
        for (Hero h : HEROES) {
            if (h.id.equals(id)) {
                return h;
            }
        }
        return HEROES.get(0);
    }

    private static Map<String, Object> mapOf(Object... kvs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            m.put(String.valueOf(kvs[i]), kvs[i + 1]);
        }
        return m;
    }
}
