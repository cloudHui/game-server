/**
 * 数字射击关卡配置：共 55 关，位数由 1→4 递增。
 * digits / targetHits / timeLimit / speed 控制难度曲线。
 */
(function (w) {
  function rangeForDigits(digits) {
    if (digits === 1) return { min: 0, max: 9 };
    if (digits === 2) return { min: 10, max: 99 };
    if (digits === 3) return { min: 100, max: 999 };
    return { min: 1000, max: 9999 };
  }

  function makeLevel(id, digits, stageIndex, stageTotal) {
    const t = stageIndex / Math.max(1, stageTotal - 1);
    const range = rangeForDigits(digits);
    const digitLabel = digits === 1 ? '一位数' : digits === 2 ? '两位数' : digits === 3 ? '三位数' : '四位数';
    return {
      id: id,
      name: '第' + id + '关 · ' + digitLabel,
      digits: digits,
      min: range.min,
      max: range.max,
      // 击中目标数：随关卡略增
      targetHits: Math.min(16, 8 + Math.floor(t * 6) + (digits - 1)),
      timeLimit: Math.max(35, 70 - Math.floor(t * 20) - (digits - 1) * 5),
      fallSpeed: 0.55 + t * 0.55 + (digits - 1) * 0.12,
      spawnRate: Math.max(900, 2800 - Math.floor(t * 900) - (digits - 1) * 250),
      // 一位数最多 9 个同屏，留至少 1 个空位保证还能随机到新数字
      maxOnScreen: Math.min(digits === 1 ? 9 : 8, 3 + Math.floor(t * 3) + Math.floor((digits - 1) / 2)),
      genDelay: Math.max(350, 700 - Math.floor(t * 200)),
      passScore: 80
    };
  }

  function buildLevels() {
    const plan = [
      { digits: 1, count: 12 },
      { digits: 2, count: 15 },
      { digits: 3, count: 14 },
      { digits: 4, count: 14 }
    ];
    const levels = [];
    let id = 1;
    plan.forEach(function (stage) {
      for (let i = 0; i < stage.count; i++) {
        levels.push(makeLevel(id++, stage.digits, i, stage.count));
      }
    });
    return levels;
  }

  w.NumberFireLevels = buildLevels();
})(window);
