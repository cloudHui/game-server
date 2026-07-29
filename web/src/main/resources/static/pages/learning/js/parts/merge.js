/** 合并学习页各分包的 data/computed/methods */
window.LearningParts = window.LearningParts || [];
window.LearningMerge = function (base) {
  var parts = window.LearningParts || [];
  parts.forEach(function (part) {
    if (part.data) Object.assign(base.data, typeof part.data === 'function' ? part.data() : part.data);
    if (part.computed) Object.assign(base.computed, part.computed);
    if (part.methods) Object.assign(base.methods, part.methods);
  });
  return base;
};
window.LearningRegister = function (part) {
  window.LearningParts.push(part);
};
