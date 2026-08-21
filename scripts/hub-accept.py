#!/usr/bin/env python3
"""Hub 验收：HTTP + WebSocket 走与浏览器相同的 action 契约。"""
from __future__ import annotations

import json
import os
import ssl
import sys
import time
import threading
import urllib.error
import urllib.request
from io import BytesIO

import websocket

BASE = os.environ.get("HUB_BASE", "http://127.0.0.1:8081/Baa3SVlpDCyPh9T9v0").rstrip("/")
WS = BASE.replace("http://", "ws://").replace("https://", "wss://") + "/ws/game"
ADMIN_PASS = os.environ.get("HUB_ADMIN_PASS", "admin12345")
TIMEOUT = int(os.environ.get("HUB_TIMEOUT", "180"))


def http(method, path, body=None, session=None, headers=None, files=None):
    url = BASE + path
    hdrs = {"Accept": "application/json"}
    data = None
    if session:
        hdrs["Cookie"] = "sessionId=" + session
        hdrs["X-Session-Token"] = session
    if files:
        boundary = "----HubBoundary7MA4YWxkTrZu0gW"
        buf = BytesIO()
        for name, filename, content, ctype in files:
            buf.write(("--%s\r\n" % boundary).encode())
            buf.write(('Content-Disposition: form-data; name="%s"; filename="%s"\r\n' % (name, filename)).encode())
            buf.write(("Content-Type: %s\r\n\r\n" % ctype).encode())
            buf.write(content)
            buf.write(b"\r\n")
        buf.write(("--%s--\r\n" % boundary).encode())
        data = buf.getvalue()
        hdrs["Content-Type"] = "multipart/form-data; boundary=" + boundary
    elif body is not None:
        data = json.dumps(body).encode()
        hdrs["Content-Type"] = "application/json"
    if headers:
        hdrs.update(headers)
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    opener_args = {"timeout": 30}
    if url.startswith("https"):
        opener_args["context"] = ssl.create_default_context()
    try:
        with urllib.request.urlopen(req, **opener_args) as resp:
            raw = resp.read().decode("utf-8", "replace")
            code = resp.getcode()
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        code = e.code
    parsed = json.loads(raw) if raw.strip().startswith("{") or raw.strip().startswith("[") else {"raw": raw}
    return code, parsed


def http_raw(method, path, session=None):
    url = BASE + path
    hdrs = {}
    if session:
        hdrs["Cookie"] = "sessionId=" + session
        hdrs["X-Session-Token"] = session
    req = urllib.request.Request(url, headers=hdrs, method=method)
    opener_args = {"timeout": 30}
    if url.startswith("https"):
        opener_args["context"] = ssl.create_default_context()
    try:
        with urllib.request.urlopen(req, **opener_args) as resp:
            return resp.getcode(), resp.read(), resp.headers.get("Content-Type", "")
    except urllib.error.HTTPError as e:
        return e.code, e.read(), e.headers.get("Content-Type", "") if e.headers else ""


def login(username, password):
    code, body = http("POST", "/api/auth/login", {"username": username, "password": password})
    if code != 200 or body.get("code") != 0:
        raise SystemExit("login failed %s %s" % (username, body))
    return body


class GameWs:
    def __init__(self, session_id, path="/ws/game"):
        self.session_id = session_id
        self.seq = 0
        self.pending = {}
        self.pushes = []
        self.lock = threading.Lock()
        url = BASE.replace("http://", "ws://").replace("https://", "wss://") + path
        self.ws = websocket.create_connection(url, timeout=20, sslopt={"cert_reqs": ssl.CERT_NONE})
        self.alive = True
        threading.Thread(target=self._read, daemon=True).start()

    def _read(self):
        while self.alive:
            try:
                raw = self.ws.recv()
                if not raw:
                    return
            except Exception:
                return
            try:
                msg = json.loads(raw)
            except Exception:
                continue
            with self.lock:
                seq = msg.get("seq")
                if seq:
                    fut = self.pending.pop(seq, None)
                    if fut is not None:
                        fut.append(msg)
                        continue
                self.pushes.append(msg)

    def send(self, action, data=None, wait=True):
        self.seq += 1
        seq = self.seq
        holder = []
        if wait:
            with self.lock:
                self.pending[seq] = holder
        self.ws.send(json.dumps({"action": action, "seq": seq, "data": data or {}}))
        if not wait:
            return None
        deadline = time.time() + 15
        while time.time() < deadline:
            with self.lock:
                if holder:
                    return holder[0]
            time.sleep(0.02)
        raise TimeoutError("ws %s seq=%s" % (action, seq))

    def wait_push(self, pred, timeout=TIMEOUT):
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self.lock:
                for msg in self.pushes:
                    if pred(msg):
                        return msg
            time.sleep(0.05)
        raise TimeoutError("push timeout")

    def close(self):
        self.alive = False
        try:
            self.ws.close()
        except Exception:
            pass


def check(name, cond, detail=""):
    if cond:
        print("PASS", name)
        return True
    print("FAIL", name, detail)
    return False


def main():
    results = []
    code, caps = http("GET", "/api/capabilities")
    results.append(check("capabilities-ready", code == 200 and caps.get("ready") is True, caps))
    results.append(check("capabilities-no-center", caps.get("center") is False, caps))

    admin = login("admin", ADMIN_PASS)
    admin_sid = admin["sessionId"]
    results.append(check("admin-login", admin.get("isAdmin") is True, admin))

    code, invite = http("POST", "/api/admin/invites",
                        {"sessionId": admin_sid, "note": "accept", "maxUses": 20, "expiresDays": 1},
                        session=admin_sid)
    token = (invite.get("invite") or {}).get("token")
    results.append(check("create-invite", code == 200 and bool(token), invite))

    users = []
    for i in range(3):
        name = "accp%d" % (int(time.time()) % 100000 + i)
        code, body = http("POST", "/api/auth/register",
                          {"username": name, "password": "pass1234", "nickname": name, "invite": token})
        results.append(check("register-" + name, code == 200 and body.get("code") == 0, body))
        users.append(body)

    created = http("POST", "/api/rooms/create",
                   {"sessionId": users[0]["sessionId"], "mode": "custom", "gameType": 2,
                    "seatNum": 3, "totalRounds": 1, "autoPlay": 0},
                   session=users[0]["sessionId"])[1]
    results.append(check("create-custom-ddz", created.get("code") == 0 and created.get("tableId"), created))
    sockets = []
    table_id = created.get("tableId")
    if table_id:
        room_id = created.get("roomId")
        joined2 = http("POST", "/api/rooms/join",
                       {"sessionId": users[1]["sessionId"], "roomId": room_id},
                       session=users[1]["sessionId"])[1]
        results.append(check("join-custom-second", joined2.get("code") == 0, joined2))
        results.append(check("multiplayer-same-table", joined2.get("tableId") == table_id, joined2))
        for user in users[:2]:
            ws = GameWs(user["sessionId"])
            auth = ws.send("auth", {"sessionId": user["sessionId"]})
            results.append(check("ws-auth-%s" % user["username"], auth.get("code") == 0, auth))
            enter = ws.send("enterTable", {"tableId": table_id})
            results.append(check("enter-%s" % user["username"], enter.get("code") == 0, enter))
            sockets.append(ws)
        illegal = sockets[0].send("op", {"choice": 99999})
        results.append(check("illegal-op", illegal.get("code") != 0 or illegal.get("action") == "error", illegal))
        refresh = sockets[0].send("refreshTable", {"tableId": table_id})
        results.append(check("refresh-table", refresh.get("code") == 0, refresh))
        sockets[0].close()
        reconnect = GameWs(users[0]["sessionId"])
        reconnect.send("auth", {"sessionId": users[0]["sessionId"]})
        again = reconnect.send("enterTable", {"tableId": table_id})
        results.append(check("reconnect-enter", again.get("code") == 0, again))
        sockets[0] = reconnect
        dup = GameWs(users[0]["sessionId"])
        dup_auth = dup.send("auth", {"sessionId": users[0]["sessionId"]})
        results.append(check("duplicate-login-auth", dup_auth.get("code") == 0, dup_auth))
        dup.close()
        try:
            sockets[0].wait_push(lambda m: m.get("seq") == 0 and m.get("action") in (
                "notGameResult", "notRoundResult", "notResult", "notState", "notOp", "notCard", "seatUpdate"
            ), timeout=8)
            results.append(check("ddz-human-push", True))
        except TimeoutError:
            try:
                refresh2 = sockets[0].send("refreshTable", {"tableId": table_id})
                results.append(check("ddz-human-in-progress", refresh2.get("code") == 0, refresh2))
            except Exception as error:
                results.append(check("ddz-human-in-progress", False, str(error)))
    else:
        results.append(check("join-custom-second", False, "no table"))
        results.append(check("multiplayer-same-table", False, "no table"))

    rooms = [
        ("mahjong", 9001),
        ("ddz", 9002),
        ("pdk", 9010),
        ("tractor", 9011),
    ]
    before = http("GET", "/api/admin/replays?sessionId=%s&page=1&size=5" % admin_sid, session=admin_sid)[1]
    before_n = len(before.get("replays") or before.get("items") or [])
    for name, room_id in rooms:
        code, created = http("POST", "/api/admin/robot-matches",
                             {"sessionId": admin_sid, "roomId": room_id, "totalRounds": 1},
                             session=admin_sid)
        results.append(check("robot-%s" % name, code == 200 and created.get("code", 0) == 0
                             and created.get("tableId"), created))
        deadline = time.time() + TIMEOUT
        found = False
        while time.time() < deadline:
            code, page = http("GET", "/api/admin/replays?sessionId=%s&page=1&size=50" % admin_sid,
                              session=admin_sid)
            items = page.get("replays") or page.get("items") or []
            if len(items) > before_n:
                found = True
                break
            time.sleep(2)
        results.append(check("replay-%s" % name, found, "replays did not grow"))
        before_n = max(before_n, len(items) if found else before_n)

    code, page = http("GET", "/api/admin/replays?sessionId=%s&page=1&size=20" % admin_sid, session=admin_sid)
    items = page.get("replays") or page.get("items") or []
    results.append(check("replay-list", code == 200 and len(items) >= 1, page))

    # 图片上传压力：10 张有效 8x8 PNG
    def make_png(w=8, h=8):
        import struct, zlib
        def chunk(tag, data):
            return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)
        raw = b"".join(b"\x00" + (b"\xff\x00\x00" * w) for _ in range(h))
        return (b"\x89PNG\r\n\x1a\n"
                + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9))
                + chunk(b"IEND", b""))
    png = make_png()
    ok_uploads = 0
    errors = []

    def upload_one(i):
        nonlocal ok_uploads
        try:
            c, body = http("POST", "/api/photos/upload", session=admin_sid,
                           files=[("files", "t%d.png" % i, png, "image/png")])
            if c == 200 and (body.get("successCount") or 0) >= 1:
                ok_uploads += 1
            else:
                errors.append(body)
        except Exception as e:
            errors.append(str(e))

    threads = [threading.Thread(target=upload_one, args=(i,)) for i in range(10)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    results.append(check("photo-upload-pressure", ok_uploads >= 8, errors[:3]))

    # 并发桌：再开 3 桌机器人
    conc = 0
    for room_id in (9002, 9010, 9011):
        code, created = http("POST", "/api/admin/robot-matches",
                             {"sessionId": admin_sid, "roomId": room_id, "totalRounds": 1},
                             session=admin_sid)
        if created.get("tableId"):
            conc += 1
    results.append(check("concurrent-robot-tables", conc == 3, conc))

    code, login_old = http("POST", "/api/login", {"username": "admin", "password": ADMIN_PASS})
    results.append(check("legacy-login-api", code == 200 and login_old.get("code") == 0, login_old))
    if login_old.get("sessionId"):
        admin = login_old
        admin_sid = admin["sessionId"]

    code, users_page = http("GET", "/api/admin/users?sessionId=%s" % admin_sid, session=admin_sid)
    listed = users_page.get("users") or []
    results.append(check("admin-users", code == 200 and users_page.get("code") == 0 and len(listed) >= 3, users_page))

    code, tables = http("GET", "/api/admin/tables?sessionId=%s" % admin_sid, session=admin_sid)
    results.append(check("admin-tables", code == 200 and tables.get("code") == 0, tables))

    code, records = http("GET", "/api/admin/records?sessionId=%s&page=1&size=20" % admin_sid, session=admin_sid)
    recs = records.get("records") if isinstance(records.get("records"), list) else []
    results.append(check("admin-records", code == 200 and records.get("code") == 0, records))

    if items:
        first = items[0]
        date = first.get("date")
        name = first.get("name")
        code, detail = http("GET", "/api/admin/replays/detail?sessionId=%s&date=%s&name=%s"
                            % (admin_sid, date, name), session=admin_sid)
        results.append(check("replay-detail", code == 200 and detail.get("code") == 0
                             and detail.get("content"), detail if isinstance(detail, dict) else detail))
        replay_code = first.get("replayCode") or ("%s/%s" % (date, name))
        code, by_code = http("GET", "/api/admin/replays/code?sessionId=%s&code=%s"
                             % (admin_sid, urllib.request.quote(replay_code, safe="")), session=admin_sid)
        results.append(check("replay-code", code == 200 and by_code.get("code") == 0, by_code))
    else:
        results.append(check("replay-detail", False, "no replay"))
        results.append(check("replay-code", False, "no replay"))

    code, listed_photos = http("GET", "/api/photos?page=1&pageSize=24", session=admin_sid)
    photo_items = listed_photos.get("items") or []
    results.append(check("photo-list", code == 200 and len(photo_items) >= 1, listed_photos))
    if photo_items:
        pid = photo_items[0].get("id")
        code, one = http("GET", "/api/photos/%s" % pid, session=admin_sid)
        results.append(check("photo-one", code == 200 and one.get("id") == pid, one))
        tcode, tbytes, ttype = http_raw("GET", "/api/photos/%s/thumbnail" % pid, session=admin_sid)
        results.append(check("photo-thumbnail", tcode == 200 and tbytes[:2] == b"\xff\xd8", ttype))
        ocode, obytes, otype = http_raw("GET", "/api/photos/%s/original" % pid, session=admin_sid)
        results.append(check("photo-original", ocode == 200 and obytes[:8] == b"\x89PNG\r\n\x1a\n", otype))
        code, renamed = http("PATCH", "/api/photos/%s" % pid, {"displayName": "accept-renamed"},
                             session=admin_sid)
        results.append(check("photo-rename", code == 200 and renamed.get("success") is True, renamed))
        code, after = http("GET", "/api/photos/%s" % pid, session=admin_sid)
        results.append(check("photo-renamed-name", after.get("displayName") == "accept-renamed", after))
        code, deleted = http("DELETE", "/api/photos/%s" % pid, session=admin_sid)
        results.append(check("photo-delete", code == 200 and deleted.get("success") is True, deleted))
        dcode, _, _ = http_raw("GET", "/api/photos/%s" % pid, session=admin_sid)
        results.append(check("photo-deleted", dcode == 404, dcode))
    else:
        for name in ("photo-one", "photo-thumbnail", "photo-original", "photo-rename",
                     "photo-renamed-name", "photo-delete", "photo-deleted"):
            results.append(check(name, False, "no photo"))

    learn_h = {"X-Session-Token": admin_sid}
    code, lhealth = http("GET", "/api/learning/health", session=admin_sid, headers=learn_h)
    results.append(check("learning-health", code == 200 and lhealth.get("status") == "ok", lhealth))
    code, lme = http("GET", "/api/learning/auth/me", session=admin_sid, headers=learn_h)
    results.append(check("learning-me", code == 200 and lme.get("username") == "admin", lme))
    code, words = http("GET", "/api/learning/words", session=admin_sid, headers=learn_h)
    results.append(check("learning-words", code == 200 and isinstance(words, list) and len(words) >= 1, words if not isinstance(words, list) else len(words)))
    code, rec = http("POST", "/api/learning/records",
                     {"subject": "数学", "module": "口算", "stage": "幼小衔接", "total": 10, "correct": 8,
                      "durationSeconds": 12}, session=admin_sid, headers=learn_h)
    results.append(check("learning-record-add", code == 200 and rec.get("id"), rec))
    code, recs_l = http("GET", "/api/learning/records", session=admin_sid, headers=learn_h)
    results.append(check("learning-record-list", code == 200 and isinstance(recs_l, list) and len(recs_l) >= 1, recs_l if not isinstance(recs_l, list) else len(recs_l)))
    code, mist = http("POST", "/api/learning/mistakes",
                      {"subject": "数学", "question": "1+1", "userAnswer": "3", "correctAnswer": "2"},
                      session=admin_sid, headers=learn_h)
    results.append(check("learning-mistake-add", code == 200 and mist.get("id"), mist))
    code, mists = http("GET", "/api/learning/mistakes", session=admin_sid, headers=learn_h)
    results.append(check("learning-mistake-list", code == 200 and isinstance(mists, list) and len(mists) >= 1, mists if not isinstance(mists, list) else len(mists)))
    code, stats = http("GET", "/api/learning/stats", session=admin_sid, headers=learn_h)
    results.append(check("learning-stats", code == 200 and isinstance(stats, dict), stats))
    code, lib = http("GET", "/api/learning/library/status", session=admin_sid, headers=learn_h)
    results.append(check("learning-library", code == 200 and isinstance(lib, dict), lib))
    code, ferr = http("POST", "/api/learning/auth/frontend-error", {}, session=admin_sid, headers=learn_h)
    results.append(check("learning-frontend-error", code == 200, ferr))

    arena_h = {"Authorization": "Bearer " + admin.get("token")}
    code, catalog = http("GET", "/api/arena/catalog", session=admin_sid)
    heroes = catalog.get("heroes") if isinstance(catalog, dict) else None
    results.append(check("arena-catalog", code == 200 and isinstance(heroes, list) and len(heroes) >= 1, catalog))
    code, astate = http("GET", "/api/arena/state", session=admin_sid, headers=arena_h)
    results.append(check("arena-state", code == 200 and "liquid" in astate, astate))
    code, grotto = http("POST", "/api/arena/action", {"action": "grotto"}, session=admin_sid, headers=arena_h)
    results.append(check("arena-grotto", code == 200 and "liquid" in grotto, grotto))
    code, battle = http("GET", "/api/arena/battle?attacker=jianhuang&defender=leizun&seed=42",
                       session=admin_sid)
    results.append(check("arena-battle", code == 200 and battle.get("winner") and battle.get("events"), battle))
    code, journey = http("GET", "/api/arena/journey", session=admin_sid, headers=arena_h)
    results.append(check("arena-journey", code == 200 and "stamina" in journey, journey))
    code, explore = http("POST", "/api/arena/journey", {"map": 1, "runs": 1}, session=admin_sid, headers=arena_h)
    results.append(check("arena-explore", code == 200 and isinstance(explore, dict), explore))
    code, library = http("GET", "/api/arena/library", session=admin_sid, headers=arena_h)
    recipes = library.get("recipes") if isinstance(library, dict) else None
    results.append(check("arena-craft-view", code == 200 and recipes is not None, library))
    code, crafted = http("POST", "/api/arena/library/craft", {"recipeId": "qi_pill"},
                         session=admin_sid, headers=arena_h)
    results.append(check("arena-craft", code == 200 and isinstance(crafted, dict), crafted))
    code, aplayers = http("GET", "/api/admin/arena/players?sessionId=%s" % admin_sid, session=admin_sid)
    results.append(check("arena-admin-players", code == 200, aplayers))

    mini_a = GameWs(users[1]["sessionId"], "/ws/mini")
    mini_b = GameWs(users[2]["sessionId"], "/ws/mini")
    a_auth = mini_a.send("auth", {"sessionId": users[1]["sessionId"]})
    b_auth = mini_b.send("auth", {"sessionId": users[2]["sessionId"]})
    results.append(check("mini-auth-a", a_auth.get("code") == 0, a_auth))
    results.append(check("mini-auth-b", b_auth.get("code") == 0, b_auth))
    queued = mini_a.send("match", {"game": "gomoku"})
    results.append(check("mini-queue", queued.get("code") == 0 and (queued.get("data") or {}).get("status") == "queued", queued))
    matched = mini_b.send("match", {"game": "gomoku"})
    results.append(check("mini-match", matched.get("code") == 0 and (matched.get("data") or {}).get("status") == "matched", matched))
    try:
        mini_a.wait_push(lambda m: m.get("action") == "matched", timeout=8)
        results.append(check("mini-matched-push", True))
    except TimeoutError as error:
        results.append(check("mini-matched-push", False, str(error)))
    moved = mini_a.send("move", {"x": 7, "y": 7})
    results.append(check("mini-move", moved.get("code") == 0, moved))
    mini_a.close()
    mini_b.close()

    code, shell = http("GET", "/api/admin/shell?sessionId=%s" % admin_sid, session=admin_sid)
    results.append(check("shell-cwd", code == 200 and shell.get("code") == 0 and shell.get("cwd"), shell))
    code, pwd = http("POST", "/api/admin/shell", {"sessionId": admin_sid, "command": "pwd"}, session=admin_sid)
    results.append(check("shell-pwd", code == 200 and pwd.get("code") == 0 and pwd.get("output"), pwd))
    code, denied = http("POST", "/api/admin/shell",
                        {"sessionId": users[0]["sessionId"], "command": "pwd"},
                        session=users[0]["sessionId"])
    results.append(check("shell-deny-non-admin", code == 200 and denied.get("code") == 403, denied))

    target = users[2]
    code, disabled = http("POST", "/api/admin/users/enable",
                          {"sessionId": admin_sid, "userId": target["userId"], "enabled": False},
                          session=admin_sid)
    results.append(check("user-disable", code == 200 and disabled.get("code") == 0, disabled))
    code, blocked = http("POST", "/api/auth/login",
                         {"username": target["username"], "password": "pass1234"})
    results.append(check("user-disabled-login", code == 200 and blocked.get("code") != 0, blocked))
    code, enabled = http("POST", "/api/admin/users/enable",
                         {"sessionId": admin_sid, "userId": target["userId"], "enabled": True},
                         session=admin_sid)
    results.append(check("user-enable", code == 200 and enabled.get("code") == 0, enabled))
    code, restored = http("POST", "/api/auth/login",
                          {"username": target["username"], "password": "pass1234"})
    results.append(check("user-enabled-login", code == 200 and restored.get("code") == 0, restored))

    for page in ("/pages/learning/index.html", "/pages/mini/gomoku/index.html",
                 "/pages/mini/chess/index.html", "/pages/arena/index.html",
                 "/pages/photos/index.html"):
        pcode, pbytes, _ = http_raw("GET", page)
        results.append(check("page-%s" % page.rsplit("/", 2)[-2], pcode == 200 and b"<html" in pbytes.lower(), pcode))

    for ws in sockets:
        ws.close()

    failed = [r for r in results if r is False]
    print("SUMMARY passed=%d failed=%d" % (len(results) - len(failed), len(failed)))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
