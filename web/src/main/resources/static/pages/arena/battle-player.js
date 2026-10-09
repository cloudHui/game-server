(function (root, factory) {
    const BattlePlayer = factory();
    if (typeof module === 'object' && module.exports) module.exports = BattlePlayer;
    else root.ArenaBattlePlayer = BattlePlayer;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    'use strict';

    function BattlePlayer(options) {
        options = options || {};
        this.render = options.render || function () {};
        this.schedule = options.schedule || function (fn, delay) { return setTimeout(fn, delay); };
        this.cancel = options.cancel || function (id) { clearTimeout(id); };
        this.interval = options.interval || 160;
        this.events = [];
        this.cursor = 0;
        this.speed = 1;
        this.playing = false;
        this.timer = null;
    }

    BattlePlayer.prototype.load = function (events) {
        this.pause();
        this.events = Array.isArray(events) ? events.slice() : [];
        this.cursor = 0;
        return this.status();
    };

    BattlePlayer.prototype.setSpeed = function (speed) {
        speed = Number(speed);
        this.speed = speed === 2 || speed === 4 ? speed : 1;
        return this.speed;
    };

    BattlePlayer.prototype.play = function () {
        if (this.playing || this.cursor >= this.events.length) return this.status();
        this.playing = true;
        this.tick();
        return this.status();
    };

    BattlePlayer.prototype.tick = function () {
        const self = this;
        if (!this.playing) return;
        if (this.cursor >= this.events.length) {
            this.playing = false;
            this.timer = null;
            return;
        }
        this.render(this.events[this.cursor++]);
        if (this.cursor >= this.events.length) {
            this.playing = false;
            this.timer = null;
            return;
        }
        this.timer = this.schedule(function () { self.tick(); }, this.interval / this.speed);
    };

    BattlePlayer.prototype.pause = function () {
        this.playing = false;
        if (this.timer !== null) this.cancel(this.timer);
        this.timer = null;
        return this.status();
    };

    BattlePlayer.prototype.skip = function () {
        this.pause();
        while (this.cursor < this.events.length) this.render(this.events[this.cursor++]);
        return this.status();
    };

    BattlePlayer.prototype.replay = function () {
        this.pause();
        this.cursor = 0;
        return this.play();
    };

    BattlePlayer.prototype.status = function () {
        return {playing: this.playing, cursor: this.cursor, total: this.events.length,
            finished: this.events.length > 0 && this.cursor >= this.events.length};
    };

    return BattlePlayer;
}));
