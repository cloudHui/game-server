/**
 * 字母/数字射击共用语音：数字中文读法、字母英文读名。
 */
(function (w) {
    var DIGITS = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九'];
    var UNITS = ['', '十', '百', '千'];

    /** 将 0-9999 转为中文读法（儿童认数常用写法） */
    function toChinese(n) {
        n = Math.floor(Number(n));
        if (!isFinite(n) || n < 0 || n > 9999) return String(n);
        if (n < 10) return DIGITS[n];
        if (n < 20) return n === 10 ? '十' : '十' + DIGITS[n % 10];

        var s = String(n);
        var parts = [];
        var zeroPending = false;
        for (var i = 0; i < s.length; i++) {
            var d = Number(s[i]);
            var unit = UNITS[s.length - 1 - i];
            if (d === 0) {
                zeroPending = parts.length > 0;
                continue;
            }
            if (zeroPending) {
                parts.push('零');
                zeroPending = false;
            }
            parts.push(DIGITS[d] + unit);
        }
        return parts.join('') || '零';
    }

    function pickVoice(langPrefix) {
        if (!w.speechSynthesis) return null;
        var voices = w.speechSynthesis.getVoices() || [];
        var re = new RegExp('^' + langPrefix, 'i');
        return voices.find(function (v) {
            return re.test(v.lang);
        }) || null;
    }

    function speak(text, lang, rate) {
        if (!w.speechSynthesis || !text) return;
        try {
            w.speechSynthesis.cancel();
            var u = new SpeechSynthesisUtterance(String(text));
            u.lang = lang || 'zh-CN';
            u.rate = rate == null ? 1 : rate;
            var voice = pickVoice((lang || 'zh').slice(0, 2));
            if (voice) u.voice = voice;
            w.speechSynthesis.speak(u);
        } catch (e) { /* 部分环境禁用语音 */
        }
    }

    function speakNumber(n) {
        speak(toChinese(n), 'zh-CN', 0.9);
    }

    /** 朗读英文字母名（用 letter X 提升 TTS 稳定性） */
    function speakLetter(ch) {
        var up = String(ch || '').toUpperCase();
        if (!/^[A-Z]$/.test(up)) return;
        speak('letter ' + up, 'en-US', 0.95);
    }

    function letterLabel(ch) {
        var up = String(ch || '').toUpperCase();
        return /^[A-Z]$/.test(up) ? up : String(ch || '');
    }

    function speakPraise(text) {
        speak(text || '你太棒了', 'zh-CN', 1);
    }

    if (w.speechSynthesis) {
        w.speechSynthesis.getVoices();
        if (typeof w.speechSynthesis.onvoiceschanged !== 'undefined') {
            w.speechSynthesis.onvoiceschanged = function () {
                pickVoice('zh');
            };
        }
    }

    var api = {
        toChinese: toChinese,
        letterLabel: letterLabel,
        speakNumber: speakNumber,
        speakLetter: speakLetter,
        speakPraise: speakPraise,
        speak: speak
    };
    w.MiniFireSpeech = api;
    // 兼容数字射击旧命名
    w.NumberFireSpeech = api;
})(window);
