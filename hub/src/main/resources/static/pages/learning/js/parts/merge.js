/** 合并学习页各分包的 data/computed/methods */
window.LearningParts = window.LearningParts || [];
window.LearningMerge = function (base) {
    var parts = window.LearningParts || [];
    var dataSources = [base.data];
    base.components = base.components || {};
    parts.forEach(function (part) {
        if (part.data) dataSources.push(part.data);
        if (part.computed) Object.assign(base.computed, part.computed);
        if (part.methods) Object.assign(base.methods, part.methods);
        if (part.components) Object.assign(base.components, part.components);
    });
    base.data = function () {
        var merged = {};
        dataSources.forEach(function (source) {
            Object.assign(merged, typeof source === 'function' ? source.call(this) : source);
        }, this);
        return merged;
    };
    return base;
};
window.LearningRegister = function (part) {
    window.LearningParts.push(part);
};
