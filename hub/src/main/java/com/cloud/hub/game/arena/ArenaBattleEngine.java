package com.cloud.hub.game.arena;

import java.util.*;

/**
 * 剑气除魔回合制对战仿真引擎。
 * <p>
 * 纯内存计算，负责模拟攻击者与防御者的对战过程。
 * 完整支持：
 * <ul>
 *   <li>通天塔敌方四系词缀（狂暴雷霆、金刚铁壁、枯木逢春、太虚魔障）</li>
 *   <li>玩家四大本命神通道法（破甲流、沉默流、霸体流、吸血流）</li>
 *   <li>怒气大招、身法闪避、会心暴击、眩晕控制与护盾吸收</li>
 * </ul>
 */
public final class ArenaBattleEngine {

    private ArenaBattleEngine() {}

    /**
     * 模拟一场完整的回合制战斗并生成对战事件序列。
     *
     * @param attacker 进攻方修士
     * @param defender 防守方修士/魔怪
     * @param skill    玩家装配的本命神通 ("pierce", "silence", "defense", "heal")
     * @param stage    通天塔层数 (用于匹配敌方词缀，0表示普通擂台切磋)
     * @param seed     随机种子，保证相同战斗可被完全确定性复现
     * @return 对战结算详情，包含胜负结果、事件时间轴列表与修士快照
     */
    public static BattleResult simulate(Hero attacker, Hero defender, String skill, int stage, long seed) {
        Random rng = new Random(seed);
        String archetype = stage > 0 ? ArenaRules.enemyArchetype(stage) : "NORMAL";

        Fighter af = initFighter(attacker, skill, archetype, true);
        Fighter df = initFighter(defender, skill, archetype, false);

        List<Map<String, Object>> events = new ArrayList<>();
        int[] seq = {0};

        addEvent(events, seq, "BATTLE_START", 0, null, null, 0, "神通道法 · " + skillDesc(skill));
        if (!"NORMAL".equals(archetype)) {
            addEvent(events, seq, "STATUS_APPLY", 0, df.hero.id, null, 0, "敌方特性: " + ArenaRules.enemyArchetypeName(archetype));
        }

        boolean aFirst = af.hero.speed >= df.hero.speed;
        int totalRounds = 0;

        for (int round = 1; round <= 30 && af.hp > 0 && df.hp > 0; round++) {
            totalRounds = round;
            execRound(round, af, df, aFirst, skill, archetype, rng, events, seq);
        }

        Hero winner = determineWinner(af, df, attacker, defender);
        addEvent(events, seq, "BATTLE_END", totalRounds, winner.id, null, 0, winner.name + " 斩妖除魔胜出！");

        return new BattleResult("WEB-" + Long.toString(seed, 36), "arena-v2-infinite", attacker, defender, winner.id, events);
    }

    /**
     * 初始化出战修士状态与词缀/神通加成。
     */
    private static Fighter initFighter(Hero h, String skill, String archetype, boolean isAttacker) {
        Fighter f = new Fighter(h);
        if (isAttacker) {
            if ("defense".equals(skill)) {
                f.shield = (long) (h.hp * 0.3); // 金刚不坏：开局获得30%生命护盾
            }
        } else {
            if ("HIGH_ATK".equals(archetype)) {
                f.atkBoost = 1.35; // 狂暴雷霆：攻击放大35%
            } else if ("HIGH_DEF".equals(archetype)) {
                f.defBoost = 2.0;  // 金刚铁壁：防御放大100%
            }
        }
        return f;
    }

    /**
     * 执行单回合战斗流程。
     */
    private static void execRound(int round, Fighter af, Fighter df, boolean aFirst, String skill, String archetype,
                                  Random rng, List<Map<String, Object>> events, int[] seq) {
        addEvent(events, seq, "ROUND_START", round, null, null, round, "第" + round + "回合");
        Fighter[] order = aFirst ? new Fighter[]{af, df} : new Fighter[]{df, af};

        for (Fighter actor : order) {
            if (af.hp <= 0 || df.hp <= 0) break;
            Fighter target = (actor == af) ? df : af;
            execAction(actor, target, round, skill, actor == af, rng, events, seq);
        }

        applyRegen(af, df, skill, archetype, round, events, seq);
    }

    /**
     * 执行单个修士的出手行动。
     */
    private static void execAction(Fighter actor, Fighter target, int round, String skill, boolean isPlayer,
                                   Random rng, List<Map<String, Object>> events, int[] seq) {
        // 眩晕检查
        if (actor.stunned) {
            actor.stunned = false;
            addEvent(events, seq, "ACTION_SKIPPED", round, actor.hero.id, target.hero.id, 0, "眩晕无法行动");
            return;
        }

        boolean silenced = actor.silencedRounds > 0;
        if (silenced) {
            actor.silencedRounds--;
        }

        // 技能施放判定（被沉默则只能普攻）
        boolean active = actor.energy >= 100 && !silenced;
        int mult = active ? actor.hero.multiplier : 100;
        String skillText = active ? actor.hero.skill : (silenced ? "普攻 (神通被沉默)" : "普通攻击");
        if (active) actor.energy = 0;

        addEvent(events, seq, "ATTACK", round, actor.hero.id, target.hero.id, 0, skillText);

        // 闪避判定 (5% 概率)
        if (rng.nextInt(100) < 5) {
            addEvent(events, seq, "MISS", round, actor.hero.id, target.hero.id, 0, "身法闪避");
            return;
        }

        // 伤害计算
        boolean isPierce = isPlayer && "pierce".equals(skill);
        boolean isDefense = (!isPlayer) && "defense".equals(skill);
        boolean isCrit = rng.nextInt(100) < 20;

        long finalDamage = calcDamage(actor, target, mult, isPierce, isDefense, isCrit);

        if (isCrit) {
            addEvent(events, seq, "CRITICAL", round, actor.hero.id, target.hero.id, 0, "暴击");
        }

        // 护盾抵扣
        if (target.shield > 0) {
            if (target.shield >= finalDamage) {
                target.shield -= finalDamage;
                addEvent(events, seq, "STATUS_APPLY", round, target.hero.id, null, finalDamage, "金刚灵盾吸收 " + finalDamage);
                finalDamage = 0;
            } else {
                finalDamage -= target.shield;
                addEvent(events, seq, "STATUS_APPLY", round, target.hero.id, null, target.shield, "金刚灵盾破碎");
                target.shield = 0;
            }
        }

        target.hp = Math.max(0, target.hp - finalDamage);
        addEvent(events, seq, "DAMAGE", round, actor.hero.id, target.hero.id, finalDamage, "剩余气血 " + target.hp);

        // 玩家神通特性触发
        if (isPlayer && "silence".equals(skill) && rng.nextInt(100) < 35 && target.silencedRounds <= 0) {
            target.silencedRounds = 2;
            addEvent(events, seq, "STATUS_APPLY", round, actor.hero.id, target.hero.id, 0, "太虚封魔咒 · 封印沉默 2回合");
        }

        // 吸血与回复判定
        if (isPlayer && "heal".equals(skill) && finalDamage > 0) {
            long lifesteal = finalDamage * 40 / 100;
            actor.hp = Math.min(actor.hero.hp, actor.hp + lifesteal);
            addEvent(events, seq, "HEAL", round, actor.hero.id, actor.hero.id, lifesteal, "青帝长生引 · 吸血");
        } else if (active && actor.hero.lifesteal > 0 && finalDamage > 0) {
            long heal = finalDamage * actor.hero.lifesteal / 100;
            actor.hp = Math.min(actor.hero.hp, actor.hp + heal);
            addEvent(events, seq, "HEAL", round, actor.hero.id, actor.hero.id, heal, "吸血");
        }

        // 眩晕触发判定
        if (active && target.hp > 0 && rng.nextInt(100) < actor.hero.stun) {
            target.stunned = true;
            addEvent(events, seq, "STATUS_APPLY", round, actor.hero.id, target.hero.id, 0, "眩晕");
        }

        // 能量积累
        actor.energy = Math.min(100, actor.energy + 25);
        target.energy = Math.min(100, target.energy + 15);

        if (target.hp == 0) {
            addEvent(events, seq, "DEATH", round, target.hero.id, null, 0, "道体破碎");
        }
    }

    /**
     * 计算攻防数值与减免后的净伤害。
     */
    private static long calcDamage(Fighter actor, Fighter target, int mult, boolean isPierce, boolean isDefense, boolean isCrit) {
        long effectiveAtk = (long) (actor.hero.atk * mult / 100.0 * actor.atkBoost);
        long effectiveDef = (long) (target.hero.def * target.defBoost);
        if (isPierce) {
            effectiveDef = effectiveDef / 2; // 破甲50%
        }

        long dmg = Math.max(10, effectiveAtk - effectiveDef / 2);
        if (isCrit) {
            dmg = isPierce ? (dmg * 21 / 10) : (dmg * 3 / 2);
        }
        if (isDefense) {
            dmg = (long) (dmg * 0.6); // 霸体减伤40%
        }
        return dmg;
    }

    /**
     * 回合结算自愈恢复。
     */
    private static void applyRegen(Fighter af, Fighter df, String skill, String archetype, int round,
                                  List<Map<String, Object>> events, int[] seq) {
        // 敌方特性：枯木逢春
        if ("HIGH_HEAL".equals(archetype) && df.hp > 0 && df.silencedRounds <= 0) {
            long regen = (long) (df.hero.hp * 0.12);
            df.hp = Math.min(df.hero.hp, df.hp + regen);
            addEvent(events, seq, "HEAL", round, df.hero.id, df.hero.id, regen, "枯木逢春 · 恢复气血 " + regen);
        }
        // 玩家神通：长生回春
        if ("heal".equals(skill) && af.hp > 0) {
            long regen = (long) (af.hero.hp * 0.08);
            af.hp = Math.min(af.hero.hp, af.hp + regen);
            addEvent(events, seq, "HEAL", round, af.hero.id, af.hero.id, regen, "长生自愈 +" + regen);
        }
    }

    private static Hero determineWinner(Fighter af, Fighter df, Hero attacker, Hero defender) {
        if (af.hp > 0 && df.hp <= 0) return attacker;
        if (df.hp > 0 && af.hp <= 0) return defender;
        return (af.hp * (long) defender.hp >= df.hp * (long) attacker.hp) ? attacker : defender;
    }

    private static void addEvent(List<Map<String, Object>> es, int[] seq, String type, int round,
                                 String actor, String target, long value, String text) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("seq", seq[0]++);
        e.put("type", type);
        e.put("round", round);
        e.put("actor", actor);
        e.put("target", target);
        e.put("value", value);
        e.put("text", text);
        es.add(e);
    }

    private static String skillDesc(String skill) {
        if ("silence".equals(skill)) return "太虚封魔咒 (沉默打断流)";
        if ("defense".equals(skill)) return "金刚不坏身 (霸体免伤流)";
        if ("heal".equals(skill)) return "青帝长生引 (吸血回春流)";
        return "九天破甲决 (破甲爆发流)";
    }

    /** 战场中动态战斗状态封装 */
    public static final class Fighter {
        public final Hero hero;
        public long hp;
        public long shield = 0;
        public int energy = 0;
        public boolean stunned = false;
        public int silencedRounds = 0;
        public double atkBoost = 1.0;
        public double defBoost = 1.0;

        public Fighter(Hero h) {
            this.hero = h;
            this.hp = h.hp;
        }
    }

    /** 修士基础属性模板 */
    public static final class Hero {
        public final String id;
        public final String name;
        public final String quality;
        public final String role;
        public final long hp;
        public final long atk;
        public final long def;
        public final long speed;
        public final String skill;
        public final int multiplier;
        public final int stun;
        public final int lifesteal;

        public Hero(String id, String name, String quality, String role, long hp, long atk, long def,
                    long speed, String skill, int multiplier, int stun, int lifesteal) {
            this.id = id;
            this.name = name;
            this.quality = quality;
            this.role = role;
            this.hp = hp;
            this.atk = atk;
            this.def = def;
            this.speed = speed;
            this.skill = skill;
            this.multiplier = multiplier;
            this.stun = stun;
            this.lifesteal = lifesteal;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getQuality() { return quality; }
        public String getRole() { return role; }
        public long getHp() { return hp; }
        public long getAtk() { return atk; }
        public long getDef() { return def; }
        public long getSpeed() { return speed; }
        public String getSkill() { return skill; }
        public int getMultiplier() { return multiplier; }
        public int getStun() { return stun; }
        public int getLifesteal() { return lifesteal; }
    }

    /** 对战结算结果封装 */
    public static final class BattleResult {
        public final String battleId;
        public final String ruleVersion;
        public final Hero attacker;
        public final Hero defender;
        public final String winner;
        public final List<Map<String, Object>> events;

        public BattleResult(String battleId, String ruleVersion, Hero attacker, Hero defender, String winner, List<Map<String, Object>> events) {
            this.battleId = battleId;
            this.ruleVersion = ruleVersion;
            this.attacker = attacker;
            this.defender = defender;
            this.winner = winner;
            this.events = events;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("battleId", battleId);
            m.put("ruleVersion", ruleVersion);
            m.put("attacker", attacker);
            m.put("defender", defender);
            m.put("winner", winner);
            m.put("events", events);
            return m;
        }
    }
}
