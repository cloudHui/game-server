// Service worker — installable & offline, and (critically) a *version-consistent*
// module graph on every load.
//
// The subtle failure this design prevents: with hashless ES modules, if a single
// document load is served partly by an old worker/cache and partly by a new one,
// you get errors like "does not provide an export named DUSK_WORLD" — because
// e.g. a fresh runner.js is paired with a stale cosmetics.js. Two things cause
// that mix, and we eliminate both:
//   1. clients.claim() can hand an *in-flight* document to a new worker mid-load
//      (some modules from the old cache, some from the new). → We never claim;
//      a document is controlled by exactly one worker for its whole life, and
//      control only changes on a fresh navigation (reload).
//   2. A global caches.match() (or stale-while-revalidate writing a second cache)
//      can shadow one version's file with another's. → For JS/CSS we look up
//      ONLY in this worker's own versioned caches, so one worker always serves
//      one self-consistent version.
//
// Updates are applied by the page (update.js): tap the toast → the new worker
// skipWaiting()s and activates → the page reloads once into an all-new graph.
// We keep the previous version's caches around so a page still controlled by the
// old worker is never stranded; older versions are pruned on activate.
//
// HTML is network-first (fresh entry point online, cached shell offline); media
// is cache-first (large & stable).
const VERSION = "v9";
const VNUM = 9; // numeric form of VERSION, for pruning old caches
const SHELL_CACHE = `escape-run-shell-${VERSION}`;
const RUNTIME_CACHE = `escape-run-runtime-${VERSION}`;

// Core files precached so a freshly-installed PWA works offline immediately.
// (Audio is intentionally excluded — it's fetched and cached on first use.)
const SHELL = [
    "./",
    "index.html",
    "manifest.webmanifest",
    "assets/icon.svg",
    "assets/icon-192.png",
    "assets/icon-512.png",
    "src/ui/styles.css",
    "src/ui/dom.js",
    "src/main.js",
    "src/util.js",
    "src/engine/renderer.js",
    "src/engine/loop.js",
    "src/engine/input.js",
    "src/engine/audio.js",
    "src/engine/voice.js",
    "src/engine/particles.js",
    "src/engine/update.js",
    "src/progress/save.js",
    "src/progress/settings.js",
    "src/progress/mastery.js",
    "src/progress/scheduler.js",
    "src/progress/daily.js",
    "src/progress/quests.js",
    "src/progress/achievements.js",
    "src/progress/unlocks.js",
    "src/skills/index.js",
    "src/skills/counting.js",
    "src/skills/comparison.js",
    "src/skills/arithmetic.js",
    "src/skills/patterning.js",
    "src/skills/bonds.js",
    "src/skills/groups.js",
    "src/skills/shapes.js",
    "src/skills/fractions.js",
    "src/game/runner.js",
    "src/game/hud.js",
    "src/game/menu.js",
    "src/game/onboarding.js",
    "src/game/mascot.js",
    "src/game/teach.js",
    "src/game/map.js",
    "src/game/garage.js",
    "src/game/goals.js",
    "src/game/trophies.js",
    "src/game/balloons.js",
    "src/game/reveal.js",
    "src/game/results.js",
    "src/game/parentzone.js",
    "src/game/cosmetics.js",
];

self.addEventListener("install", (event) => {
    // Precache the shell, but don't skipWaiting — the page decides when to update.
    event.waitUntil(caches.open(SHELL_CACHE).then((c) => c.addAll(SHELL)));
});

// The page posts this when the user taps "update".
self.addEventListener("message", (event) => {
    if (event.data && event.data.type === "SKIP_WAITING") self.skipWaiting();
});

self.addEventListener("activate", (event) => {
    event.waitUntil(
        (async () => {
            const keys = await caches.keys();
            await Promise.all(
                keys.map((k) => {
                    if (k === SHELL_CACHE || k === RUNTIME_CACHE) return Promise.resolve();
                    const m = k.match(/^escape-run-(?:shell|runtime)-v(\d+)$/);
                    // Keep the immediately-previous version too, so a document still
                    // controlled by the old worker can finish loading consistently.
                    if (m && +m[1] >= VNUM - 1) return Promise.resolve();
                    return caches.delete(k);
                })
            );
            // NOTE: intentionally NO clients.claim(). Claiming can switch the
            // controller of an in-flight document mid-load and mix module versions
            // (see header). Control transfers only on the next navigation; the page
            // reloads itself after an update (update.js).
        })()
    );
});

const isHTML = (req) =>
    req.mode === "navigate" || (req.headers.get("accept") || "").includes("text/html");
const isMedia = (url) => /\.(mp3|ogg|wav|png|svg|jpe?g|webp|gif|woff2?)$/i.test(url.pathname);

self.addEventListener("fetch", (event) => {
    const req = event.request;
    if (req.method !== "GET") return;
    const url = new URL(req.url);
    if (url.origin !== self.location.origin) return; // don't touch cross-origin

    if (isHTML(req)) event.respondWith(networkFirst(req));
    else if (isMedia(url)) event.respondWith(cacheFirst(req));
    else event.respondWith(cacheFirstVersioned(req)); // JS / CSS / JSON
});

function cacheable(res) {
    return res && res.status === 200 && res.type === "basic";
}

async function put(req, res) {
    if (cacheable(res)) {
        const cache = await caches.open(RUNTIME_CACHE);
        cache.put(req, res.clone());
    }
    return res;
}

// Network-first with timeout + offline fallback — for HTML navigations.
async function networkFirst(req, timeoutMs = 4000) {
    try {
        const res = await Promise.race([
            fetch(req),
            new Promise((_, reject) => setTimeout(() => reject(new Error("timeout")), timeoutMs)),
        ]);
        return await put(req, res);
    } catch {
        return (await caches.match(req)) || (await caches.match("index.html")) || Response.error();
    }
}

// Cache-first — for large, stable media.
async function cacheFirst(req) {
    const cached = await caches.match(req);
    if (cached) return cached;
    try {
        return await put(req, await fetch(req));
    } catch {
        return Response.error();
    }
}

// Look up ONLY in THIS worker's own version caches (never a cross-version global
// match) so one worker can't serve a mix of module versions in a single load.
async function matchVersioned(req) {
    const shell = await caches.open(SHELL_CACHE);
    const s = await shell.match(req);
    if (s) return s;
    const rt = await caches.open(RUNTIME_CACHE);
    return rt.match(req);
}

// Cache-first, version-pinned — for JS/CSS/JSON. Updates arrive atomically via a
// new worker + version bump (never a half-applied refresh of individual files).
async function cacheFirstVersioned(req) {
    const hit = await matchVersioned(req);
    if (hit) return hit;
    try {
        return await put(req, await fetch(req));
    } catch {
        return (await caches.match(req)) || Response.error();
    }
}
