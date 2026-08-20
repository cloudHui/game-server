package web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.ResponseEntity;
import web.arena.ArenaRepository;
import web.service.UserService;
import java.util.*;

/** 剑气除魔 Web 适配器：服务端一次性结算，浏览器只播放事件。 */
@RestController
@RequestMapping("/api/arena")
public class ArenaController {
    private final ArenaRepository repository;
    private final UserService users;
    public ArenaController(ArenaRepository repository,UserService users){this.repository=repository;this.users=users;}
    private static final List<Hero> HEROES = Arrays.asList(
        new Hero("jianhuang","无极剑皇","金","强攻",8500,1180,380,610,"万剑归宗",260,15,20),
        new Hero("leizun","九霄雷尊","金","控制",7600,960,360,590,"神霄万劫",230,28,15),
        new Hero("yaohuang","长生药皇","金","坚守",9800,700,500,420,"万古长春",150,9,0),
        new Hero("luocha","血海罗刹","金","强攻",8200,1080,330,540,"血海无涯",240,12,28),
        new Hero("xingjun","紫微星君","金","坚守",9000,880,460,510,"周天星陨",205,22,0),
        new Hero("kongming","空明道祖","金","控制",8000,900,400,650,"六道禁法",180,38,0),
        new Hero("taixu","太虚剑主","红","强攻",6500,820,300,520,"一剑破万法",210,18,10),
        new Hero("chixiao","赤霄真君","橙","强攻",5600,760,210,480,"赤霄焚天",195,8,12),
        new Hero("zhenyue","镇岳尊者","橙","坚守",7200,510,390,350,"不动山河",125,0,0),
        new Hero("mingwang","不动明王","橙","坚守",7800,500,430,320,"明王净世",135,16,0),
        new Hero("qinglan","青岚剑君","紫","灵巧",5200,620,240,560,"青岚九式",175,12,0),
        new Hero("xuanshuang","玄霜仙子","紫","控制",5400,590,260,500,"玄霜封脉",145,32,0),
        new Hero("jinghong","惊鸿影","紫","灵巧",4900,680,220,650,"无影绝杀",165,10,18),
        new Hero("lingxi","灵犀药师","紫","控制",6000,540,280,440,"万木回春",120,7,0)
    );

    @GetMapping("/catalog")
    public Map<String,Object> catalog() {
        Map<String,Object> out=new LinkedHashMap<>(); out.put("heroes",HEROES); out.put("ruleVersion","arena-v1");
        out.put("gear",Arrays.asList("诛仙断界剑","诛仙剑袍","诛仙冠","诛仙剑印","玄武镇海刃","玄武神甲","玄武冕","玄武定海珠","天机羽扇","天机云裳","天机星冠","天机罗盘"));
        return out;
    }

    @GetMapping("/state")
    public ResponseEntity<?> state(@RequestHeader(value="Authorization",required=false) String authorization) {
        UserService.UserInfo user=auth(authorization);if(user==null)return ResponseEntity.status(401).body(error("请先登录"));
        try{return ResponseEntity.ok(repository.state(user.getUserId()));}catch(Exception e){return ResponseEntity.status(500).body(error(e.getMessage()));}
    }

    @PostMapping("/action")
    public ResponseEntity<?> action(@RequestHeader(value="Authorization",required=false) String authorization,@RequestBody Map<String,Object> body) {
        UserService.UserInfo user=auth(authorization);if(user==null)return ResponseEntity.status(401).body(error("请先登录"));
        try{String action=String.valueOf(body.get("action"));String id=body.get("id")==null?"":String.valueOf(body.get("id"));int count=body.get("count") instanceof Number?((Number)body.get("count")).intValue():1;return ResponseEntity.ok(repository.action(user.getUserId(),action,id,count,System.currentTimeMillis()));}
        catch(IllegalArgumentException e){return ResponseEntity.badRequest().body(error(e.getMessage()));}catch(Exception e){return ResponseEntity.status(500).body(error(e.getMessage()));}
    }

    private UserService.UserInfo auth(String header){if(header==null||!header.startsWith("Bearer "))return null;return users.validateToken(header.substring(7).trim());}
    private Map<String,Object> error(String message){Map<String,Object> e=new LinkedHashMap<>();e.put("error",message);return e;}

    @GetMapping("/battle")
    public Map<String,Object> battle(@RequestParam(defaultValue="jianhuang") String attacker,
                                     @RequestParam(defaultValue="leizun") String defender,
                                     @RequestParam(defaultValue="42") long seed) {
        Hero a=find(attacker), d=find(defender); Random rng=new Random(seed); Fighter af=new Fighter(a), df=new Fighter(d);
        List<Map<String,Object>> events=new ArrayList<>(); int seq=0; boolean aFirst=a.speed>=d.speed;
        add(events,seq++,"BATTLE_START",0,null,null,0,"规则 arena-v1");
        int rounds=0;
        for(int round=1;round<=30&&af.hp>0&&df.hp>0;round++) {
            rounds=round; add(events,seq++,"ROUND_START",round,null,null,round,"第"+round+"回合");
            Fighter[] order=aFirst?new Fighter[]{af,df}:new Fighter[]{df,af};
            for(Fighter actor:order) { if(af.hp<=0||df.hp<=0) break; Fighter target=actor==af?df:af;
                if(actor.stunned){actor.stunned=false;add(events,seq++,"ACTION_SKIPPED",round,actor.hero.id,target.hero.id,0,"眩晕");continue;}
                boolean active=actor.energy>=100; int mult=active?actor.hero.multiplier:100; String skill=active?actor.hero.skill:"普通攻击"; if(active)actor.energy=0;
                add(events,seq++,"ATTACK",round,actor.hero.id,target.hero.id,0,skill);
                if(rng.nextInt(100)<5){add(events,seq++,"MISS",round,actor.hero.id,target.hero.id,0,"闪避");continue;}
                long damage=Math.max(1,actor.hero.atk*mult/100-target.hero.def/2); boolean crit=rng.nextInt(100)<20;
                if(crit){damage=damage*3/2;add(events,seq++,"CRITICAL",round,actor.hero.id,target.hero.id,0,"暴击");}
                target.hp=Math.max(0,target.hp-damage); add(events,seq++,"DAMAGE",round,actor.hero.id,target.hero.id,damage,"剩余气血 "+target.hp);
                if(active&&actor.hero.lifesteal>0){long heal=damage*actor.hero.lifesteal/100;actor.hp=Math.min(actor.hero.hp,actor.hp+heal);add(events,seq++,"HEAL",round,actor.hero.id,actor.hero.id,heal,"吸血");}
                if(active&&target.hp>0&&rng.nextInt(100)<actor.hero.stun){target.stunned=true;add(events,seq++,"STATUS_APPLY",round,actor.hero.id,target.hero.id,0,"眩晕");}
                actor.energy=Math.min(100,actor.energy+25); target.energy=Math.min(100,target.energy+15);
                if(target.hp==0)add(events,seq++,"DEATH",round,target.hero.id,null,0,"道体破碎");
            }
        }
        Hero winner=af.hp>0&&df.hp<=0?a:df.hp>0&&af.hp<=0?d:(af.hp*(long)d.hp>=df.hp*(long)a.hp?a:d);
        add(events,seq,"BATTLE_END",rounds,winner.id,null,0,winner.name+"获胜");
        Map<String,Object> out=new LinkedHashMap<>();out.put("battleId","WEB-"+Long.toString(seed,36));out.put("ruleVersion","arena-v1");out.put("attacker",a);out.put("defender",d);out.put("winner",winner.id);out.put("events",events);return out;
    }
    private Hero find(String id){for(Hero h:HEROES)if(h.id.equals(id))return h;return HEROES.get(0);}
    private static void add(List<Map<String,Object>> es,int seq,String type,int round,String actor,String target,long value,String text){Map<String,Object> e=new LinkedHashMap<>();e.put("seq",seq);e.put("type",type);e.put("round",round);e.put("actor",actor);e.put("target",target);e.put("value",value);e.put("text",text);es.add(e);}
    private static class Fighter { final Hero hero; long hp; int energy; boolean stunned; Fighter(Hero h){hero=h;hp=h.hp;} }
    public static class Hero { public final String id,name,quality,role,skill;public final long hp,atk,def,speed;public final int multiplier,stun,lifesteal;Hero(String id,String name,String quality,String role,long hp,long atk,long def,long speed,String skill,int multiplier,int stun,int lifesteal){this.id=id;this.name=name;this.quality=quality;this.role=role;this.hp=hp;this.atk=atk;this.def=def;this.speed=speed;this.skill=skill;this.multiplier=multiplier;this.stun=stun;this.lifesteal=lifesteal;}public String getId(){return id;}public String getName(){return name;}public String getQuality(){return quality;}public String getRole(){return role;}public long getHp(){return hp;}public long getAtk(){return atk;}public long getDef(){return def;}public long getSpeed(){return speed;}public String getSkill(){return skill;}public int getMultiplier(){return multiplier;}public int getStun(){return stun;}public int getLifesteal(){return lifesteal;}}
}
