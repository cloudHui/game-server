// Service-worker registration + update flow.
//
// Keeps an installed app current without anyone clearing site data:
//  • registers the SW (with updateViaCache:"none" so the script is always
//    revalidated),
//  • checks for a new version on load and whenever the tab is refocused,
//  • when a new worker has installed and one already controls the page, shows a
//    small "update" toast — one tap applies it and reloads once.
//
// The SW never calls clients.claim() (to keep every load's module graph
// version-consistent — see sw.js), so we drive the post-update reload here:
// after the user taps update, the waiting worker skipWaiting()s and moves to
// "activated"; we reload once then, so the fresh navigation loads entirely from
// the new version. (controllerchange is kept as a belt-and-braces fallback.)

let toastShown = false;
let applying = false;

function reloadOnce() {
    if (applying) return;
    applying = true;
    window.location.reload();
}

export function registerServiceWorker() {
    if (!("serviceWorker" in navigator)) return;

    window.addEventListener("load", async () => {
        // Was the page already controlled by a SW when it loaded? If not, this is a
        // first install and we must NOT reload when it takes control.
        const hadController = !!navigator.serviceWorker.controller;

        let reg;
        try {
            reg = await navigator.serviceWorker.register("sw.js", {updateViaCache: "none"});
        } catch {
            return;
        }

        // Fallback: if the browser ever does switch controllers, reload once.
        navigator.serviceWorker.addEventListener("controllerchange", () => {
            if (!hadController) return;
            reloadOnce();
        });

        // A worker may already be waiting from a previous visit.
        if (reg.waiting && navigator.serviceWorker.controller) showUpdateToast(reg.waiting);

        // A worker that installs while this page is open.
        reg.addEventListener("updatefound", () => {
            const sw = reg.installing;
            if (!sw) return;
            sw.addEventListener("statechange", () => {
                if (sw.state === "installed" && navigator.serviceWorker.controller) {
                    showUpdateToast(sw);
                }
            });
        });

        // Check now, and again each time the app is brought back to the foreground.
        const check = () => reg.update().catch(() => {
        });
        check();
        document.addEventListener("visibilitychange", () => {
            if (document.visibilityState === "visible") check();
        });
    });
}

function showUpdateToast(worker) {
    if (toastShown) return;
    toastShown = true;

    const toast = document.createElement("button");
    toast.type = "button";
    toast.className = "update-toast";
    toast.setAttribute("aria-live", "polite");
    toast.innerHTML = '<span aria-hidden="true">🔄</span> New version — tap to update';
    toast.addEventListener("click", () => {
        toast.disabled = true;
        toast.textContent = "Updating…";
        // Reload as soon as the new worker takes over (it activates after
        // skipWaiting). This gives a single, all-new module graph.
        worker.addEventListener("statechange", () => {
            if (worker.state === "activated") reloadOnce();
        });
        if (worker.state === "activated") reloadOnce();
        worker.postMessage({type: "SKIP_WAITING"});
    });

    document.body.appendChild(toast);
}
