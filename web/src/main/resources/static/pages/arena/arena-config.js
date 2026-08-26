window.ArenaConfig = (() => {
    const tasks = {
        login: ['登录问道', 1, '灵液 200', 10],
        dungeon: ['挑战副本三次', 3, '灵液 500', 20],
        rank: ['仙侣突破或升星', 1, '灵币 300', 15],
        skill: ['功法升级两次', 2, '灵币 450', 10],
        recruit: ['招募仙侣一次', 1, '仙缘 1', 15],
        formation: ['升级战阵一次', 1, '战阵石 120', 10],
        grotto: ['领取洞府一次', 1, '灵液 300', 10],
        arena: ['擂台论剑一次', 1, '灵币 400', 20]
    };
    const bosses = ['赤焰妖', '寒潭蛟', '噬魂鬼', '九尾狐', '魔剑尊', '混沌魔君'];
    const chapters = ['青云山门', '幽都鬼城', '上古遗迹'];
    const activityRewards = {20: '仙缘 1', 45: '战阵石 200', 70: '灵液 1000', 100: '仙缘 3'};

    function findHero(catalog, id) {
        return catalog.heroes.find(hero => hero.id === id) || {
            id, name: id, quality: '紫', role: '修士', hp: 5000, atk: 500, def: 200, skill: '御剑术'
        };
    }

    function heroPower(catalog, playerHero) {
        const hero = findHero(catalog, playerHero.id);
        return Math.floor(hero.hp / 5 + hero.atk * 8 + hero.def * 5
            + (playerHero.rank - 1) * 900 + (playerHero.stars - 1) * 2400
            + (playerHero.skill - 1) * 650);
    }

    return {tasks, bosses, chapters, activityRewards, findHero, heroPower};
})();
