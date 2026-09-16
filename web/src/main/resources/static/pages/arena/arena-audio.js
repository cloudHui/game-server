/**
 * @file arena-audio.js
 * @description 仙侠修真 Web Audio 程序合成音效引擎（零外链依赖，纯程序实时合成）。
 */
(function (root, factory) {
    if (typeof module === 'object' && module.exports) module.exports = factory();
    else root.ArenaAudio = factory();
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    'use strict';

    var ctx = null;
    var muted = false;

    /**
     * 获取或懒加载当前浏览器的 AudioContext 实例。
     * @returns {AudioContext|null}
     */
    function getCtx() {
        if (!ctx) {
            var AudioCtx = window.AudioContext || window.webkitAudioContext;
            if (AudioCtx) ctx = new AudioCtx();
        }
        if (ctx && ctx.state === 'suspended') {
            ctx.resume().catch(function () {});
        }
        return ctx;
    }

    /**
     * 仙侠音效合成器对象。
     */
    var ArenaAudio = {
        /**
         * 设置是否静音。
         * @param {boolean} m 是否静音
         */
        setMuted: function (m) {
            muted = !!m;
        },

        /**
         * 获取当前是否处于静音状态。
         * @returns {boolean}
         */
        isMuted: function () {
            return muted;
        },

        /**
         * 切换静音开关状态。
         * @returns {boolean} 切换后的静音状态
         */
        toggleMute: function () {
            muted = !muted;
            return muted;
        },

        /**
         * 飞剑划破长空呼啸声 (正弦波指数衰减)。
         */
        whoosh: function () {
            if (muted) return;
            var c = getCtx(); if (!c) return;
            try {
                var osc = c.createOscillator();
                var gain = c.createGain();
                var now = c.currentTime;
                osc.type = 'sine';
                osc.frequency.setValueAtTime(800, now);
                osc.frequency.exponentialRampToValueAtTime(200, now + 0.18);
                gain.gain.setValueAtTime(0.2, now);
                gain.gain.linearRampToValueAtTime(0.001, now + 0.18);
                osc.connect(gain);
                gain.connect(c.destination);
                osc.start(now);
                osc.stop(now + 0.2);
            } catch (e) {}
        },

        /**
         * 金石相击 / 剑气命中护盾受击声 (三角波碰撞音)。
         */
        clash: function () {
            if (muted) return;
            var c = getCtx(); if (!c) return;
            try {
                var osc = c.createOscillator();
                var gain = c.createGain();
                var now = c.currentTime;
                osc.type = 'triangle';
                osc.frequency.setValueAtTime(520, now);
                osc.frequency.exponentialRampToValueAtTime(140, now + 0.12);
                gain.gain.setValueAtTime(0.25, now);
                gain.gain.linearRampToValueAtTime(0.01, now + 0.12);
                osc.connect(gain);
                gain.connect(c.destination);
                osc.start(now);
                osc.stop(now + 0.14);
            } catch (e) {}
        },

        /**
         * 暴击 / 九天神雷破空轰鸣声 (锯齿波重低音)。
         */
        thunder: function () {
            if (muted) return;
            var c = getCtx(); if (!c) return;
            try {
                var osc = c.createOscillator();
                var gain = c.createGain();
                var now = c.currentTime;
                osc.type = 'sawtooth';
                osc.frequency.setValueAtTime(180, now);
                osc.frequency.exponentialRampToValueAtTime(40, now + 0.35);
                gain.gain.setValueAtTime(0.35, now);
                gain.gain.linearRampToValueAtTime(0.01, now + 0.35);
                osc.connect(gain);
                gain.connect(c.destination);
                osc.start(now);
                osc.stop(now + 0.38);
            } catch (e) {}
        },

        /**
         * 仙缘获得 / 灵药服用 / 进阶成功清脆灵石音 (四和弦叠加升调)。
         */
        ding: function () {
            if (muted) return;
            var c = getCtx(); if (!c) return;
            try {
                var now = c.currentTime;
                [523.25, 659.25, 783.99, 1046.5].forEach(function (freq, i) {
                    var osc = c.createOscillator();
                    var gain = c.createGain();
                    var t = now + i * 0.05;
                    osc.type = 'sine';
                    osc.frequency.setValueAtTime(freq, t);
                    gain.gain.setValueAtTime(0.18, t);
                    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.25);
                    osc.connect(gain);
                    gain.connect(c.destination);
                    osc.start(t);
                    osc.stop(t + 0.28);
                });
            } catch (e) {}
        }
    };

    return ArenaAudio;
}));
