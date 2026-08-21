#!/usr/bin/env python3
import base64, datetime, email.message, json, os, smtplib, subprocess
from pathlib import Path

ENV = Path('/etc/server-deploy/server.env')
STATE = Path('/var/lib/server-deploy/alert-state.json')


def cfg():
    data = {}
    if ENV.exists():
        for line in ENV.read_text(errors='ignore').splitlines():
            if '=' in line:
                k, v = line.split('=', 1)
                data[k] = v
    return data


def run(cmd):
    try:
        return subprocess.run(cmd, capture_output=True, text=True, timeout=10).stdout.strip()
    except Exception:
        return ''


def mail(subject, body):
    c = cfg()
    auth = c.get('SMTP_AUTH_B64', '')
    if not all((c.get('SMTP_HOST'), c.get('SMTP_USER'), auth, c.get('REPORT_RECIPIENT'))):
        return False
    msg = email.message.EmailMessage()
    msg['Subject'] = subject
    msg['From'] = c['SMTP_USER']
    msg['To'] = c['REPORT_RECIPIENT']
    msg.set_content(body)
    with smtplib.SMTP_SSL(c['SMTP_HOST'], int(c.get('SMTP_PORT', '465')), timeout=20) as s:
        s.login(c['SMTP_USER'], base64.b64decode(auth).decode())
        s.send_message(msg)
    return True


def load_state():
    if not STATE.exists():
        return set()
    try:
        return set(json.loads(STATE.read_text()).get('failed', []))
    except Exception:
        return set()


def save_state(failed):
    STATE.parent.mkdir(parents=True, exist_ok=True)
    STATE.write_text(json.dumps({'failed': sorted(failed)}, ensure_ascii=False))


def unit_installed(unit):
    return bool(run(['systemctl', 'cat', unit]))


def collect_failed():
    failed = []
    for unit in ('x-ui.service', 'nginx.service', 'fail2ban.service'):
        if not unit_installed(unit):
            continue
        if run(['systemctl', 'is-active', unit]) != 'active':
            failed.append(unit)
    # game-server hub 进程（非 systemd）
    hub = run(['pgrep', '-f', 'build/hub/hub.jar'])
    weball = run(['pgrep', '-f', 'weball[.]jar'])
    web = run(['pgrep', '-f', 'build/web/Web.jar'])
    if not hub and not weball and not web:
        failed.append('hub/web')
    disk = run(['df', '-P', '/'])
    if disk:
        try:
            if int(disk.splitlines()[-1].split()[4].rstrip('%')) >= 90:
                failed.append('disk>=90%')
        except (IndexError, ValueError):
            pass
    return failed


def main():
    current = set(collect_failed())
    previous = load_state()
    if current == previous:
        if current:
            print('alert unchanged: ' + ','.join(sorted(current)))
        return

    now = datetime.datetime.now(datetime.timezone.utc).isoformat()
    if current:
        body = (
            '异常项目：\n'
            + '\n'.join('- ' + x for x in sorted(current))
            + '\n\n'
            + run(['systemctl', '--failed', '--no-legend'])
        )
        sent = mail('[服务器告警] ' + now, body)
        print(('alert sent: ' if sent else 'alert (mail skipped): ') + ','.join(sorted(current)))
    else:
        body = '此前异常已恢复正常。\n\n上次异常：\n' + '\n'.join('- ' + x for x in sorted(previous))
        sent = mail('[服务器恢复] ' + now, body)
        print('recovered' + ('' if sent else ' (mail skipped)'))
    save_state(current)


if __name__ == '__main__':
    main()
