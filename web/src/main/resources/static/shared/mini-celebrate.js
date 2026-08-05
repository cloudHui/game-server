(function (w) {
    'use strict';
    var phrases = ['你太棒啦！', '好厉害呀！', '做得真好！', '闪闪发光！'];
    var symbols = ['🌟', '💖', '🌸', '✨', '🎀', '🍬', '⭐', '🌈'];
    var voiceFiles = {
        success: ['1_great.mp3', '2_amazing.mp3', '3_congrats.mp3', '4_best_kid.mp3',
            '05_smart.mp3', '06_done_well.mp3', '07_progress.mp3', '12_more.mp3',
            '13_smart_head.mp3', '14_wow.mp3'],
        milestone: ['09_amazing_kid.mp3', '10_cant_do.mp3', '11_hero.mp3',
            '15_perfect.mp3', '16_star.mp3'],
        encourage: ['08_come_on.mp3']
    };
    var timer = 0;

    function muted() {
        return localStorage.getItem('miniCelebrationMuted') === '1';
    }

    function assetUrl(path) {
        return w.appUrl ? appUrl(path) : path;
    }

    function speakFallback(voice) {
        if (muted() || !w.speechSynthesis || !w.SpeechSynthesisUtterance) return;
        var utterance = new SpeechSynthesisUtterance(voice || '你太棒啦');
        utterance.lang = 'zh-CN';
        utterance.rate = .88;
        utterance.pitch = 1.28;
        w.speechSynthesis.cancel();
        w.speechSynthesis.speak(utterance);
    }

    function nextVoiceFile(tone) {
        var category = voiceFiles[tone] ? tone : 'success';
        var pool = voiceFiles[category];
        var key = 'miniCelebrationVoiceIndex.' + category;
        var index = parseInt(localStorage.getItem(key), 10);
        if (!isFinite(index) || index < 0) index = 0;
        localStorage.setItem(key, String((index + 1) % pool.length));
        return pool[index % pool.length];
    }

    function playSound(voice, tone) {
        if (muted()) return;
        if (tone !== 'encourage') {
            try {
                var audio = new Audio(assetUrl('/pages/mini/escape-run/assets/audio/sfx/win.mp3'));
                audio.volume = .32;
                audio.play().catch(function () {});
            } catch (e) {}
        }
        setTimeout(function () {
            if (muted()) return;
            try {
                var file = nextVoiceFile(tone);
                var praise = new Audio(assetUrl('/shared/audio/celebration/' + file));
                praise.volume = .86;
                praise.onerror = function () { speakFallback(voice); };
                praise.play().catch(function () { speakFallback(voice); });
            } catch (e) {
                speakFallback(voice);
            }
        }, 260);
    }

    function close() {
        clearTimeout(timer);
        var old = document.querySelector('.mini-celebrate-layer');
        if (old) old.remove();
    }

    function play(options) {
        options = options || {};
        close();
        var tone = options.tone || 'success';
        var phrase = options.title || (tone === 'encourage' ? '再试一次吧！' :
            phrases[Math.floor(Math.random() * phrases.length)]);
        var layer = document.createElement('div');
        layer.className = 'mini-celebrate-layer';
        layer.innerHTML = '<div class="mini-celebrate-toast"><button class="mini-celebrate-sound" type="button" aria-label="切换庆祝声音"></button><div class="mini-celebrate-face">' +
            (options.icon || '🏆') + '</div><div class="mini-celebrate-title"></div><div class="mini-celebrate-note"></div></div>';
        layer.querySelector('.mini-celebrate-title').textContent = phrase;
        layer.querySelector('.mini-celebrate-note').textContent = options.note || '恭喜你完成挑战！';
        var sound = layer.querySelector('.mini-celebrate-sound');
        sound.textContent = muted() ? '🔇' : '🔊';
        sound.onclick = function () {
            localStorage.setItem('miniCelebrationMuted', muted() ? '0' : '1');
            sound.textContent = muted() ? '🔇' : '🔊';
        };
        if (!w.matchMedia || !w.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            var particleCount = tone === 'encourage' ? 10 : 34;
            for (var i = 0; i < particleCount; i++) {
                var particle = document.createElement('span');
                particle.className = 'mini-celebrate-particle';
                particle.textContent = symbols[i % symbols.length];
                particle.style.setProperty('--x', (2 + Math.random() * 96) + '%');
                particle.style.setProperty('--size', (14 + Math.random() * 18) + 'px');
                particle.style.setProperty('--time', (1.6 + Math.random() * 1.2) + 's');
                particle.style.setProperty('--delay', (Math.random() * .45) + 's');
                particle.style.setProperty('--spin', ((Math.random() - .5) * 900) + 'deg');
                layer.appendChild(particle);
            }
        }
        document.body.appendChild(layer);
        playSound(options.voice || (tone === 'encourage' ? '加油呀' : '你太棒啦'), tone);
        timer = setTimeout(close, options.duration || 3000);
    }

    w.MiniCelebrate = {play: play, close: close};
})(window);
