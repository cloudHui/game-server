(function (w) {
    'use strict';

    function byId(id) {
        return document.getElementById(id);
    }

    function escapeHtml(value) {
        var node = document.createElement('div');
        node.textContent = value == null ? '' : String(value);
        return node.innerHTML;
    }

    function numberText(value, digits) {
        if (value === null || value === undefined || String(value).trim() === '') return '--';
        var number = Number(value);
        return isFinite(number) ? number.toFixed(digits == null ? 2 : digits) : '--';
    }

    function numberOrNull(value) {
        if (value === null || value === undefined || String(value).trim() === '') return null;
        var number = Number(value);
        return isFinite(number) && number >= 0 ? number : null;
    }

    function nonNegativeInteger(raw, label, max) {
        var limit = max == null ? 99999 : max;
        raw = String(raw == null ? '' : raw).trim();
        if (!raw || raw.length > String(limit).length || !/^\d+$/.test(raw) || Number(raw) > limit) {
            throw new Error(label + '必须是 0 到 ' + limit + ' 的整数');
        }
        return Number(raw);
    }

    w.AdminTools = {
        byId: byId,
        escapeHtml: escapeHtml,
        numberText: numberText,
        numberOrNull: numberOrNull,
        nonNegativeInteger: nonNegativeInteger
    };
})(window);
