#!/usr/bin/env python3
import argparse
import base64
import datetime
import email.message
import ipaddress
import json
import os
import re
import shutil
import smtplib
import socket
import subprocess
from collections import Counter
from pathlib import Path

ENV = Path('/etc/server-deploy/server.env')
STATE_DIR = Path('/var/lib/server-deploy')
STATE_FILE = STATE_DIR / 'traffic-state.json'
DEFAULT_UNITS = ('x-ui', 'nginx', 'fail2ban')
DEFAULT_PUBLIC_PORTS = ('22', '80', '443', '2096', '7393', '7443')
KNOWN_LOCAL_PROCS = (
    'sshd', 'ssh', 'nginx', 'x-ui', 'xray', 'xray-linux', 'xray-linux-amd64',
    'xray-linux-amd6', 'fail2ban', 'java', 'systemd-resolve', 'systemd-resolved',
)
KERNEL_COMM = re.compile(
    r'^\[|kthread|kworker|kswapd|kcompactd|rcu_|migration/|cpuhp/|ksoftirq|jbd2|systemd-journal'
)


def cfg():
    data = {}
    if ENV.exists():
        for line in ENV.read_text(errors='ignore').splitlines():
            if '=' in line and not line.strip().startswith('#'):
                k, v = line.split('=', 1)
                data[k.strip()] = v.strip()
    return data


def run(cmd, timeout=15):
    try:
        return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout).stdout.strip()
    except Exception as e:
        return str(e)


def size(n):
    n = float(max(n, 0))
    for unit in ('B', 'KB', 'MB', 'GB', 'TB'):
        if n < 1024:
            return f'{n:.1f} {unit}'
        n /= 1024
    return f'{n:.1f} PB'


def fmt_traffic(rx, tx):
    return f'入站 {size(rx)}，出站 {size(tx)}，合计 {size(rx + tx)}'


def split_words(value, default=()):
    items = [x for x in (value or '').split() if x]
    return items or list(default)


def load_state():
    if not STATE_FILE.exists():
        return {'traffic_history': []}
    try:
        return json.loads(STATE_FILE.read_text())
    except Exception:
        return {'traffic_history': []}


def save_state(state):
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    STATE_FILE.write_text(json.dumps(state, ensure_ascii=False, indent=2) + '\n')
    os.chmod(STATE_FILE, 0o600)


def selected_iface(c):
    iface = c.get('NET_IFACE', '')
    if iface and Path(f'/sys/class/net/{iface}/statistics/rx_bytes').exists():
        return iface
    route = run(['ip', 'route', 'get', '1.1.1.1'])
    match = re.search(r'\bdev\s+(\S+)', route)
    if match:
        return match.group(1)
    for line in Path('/proc/net/dev').read_text().splitlines()[2:]:
        name = line.split(':', 1)[0].strip()
        if name and name != 'lo':
            return name
    return 'eth0'


def read_counters(iface):
    base = Path(f'/sys/class/net/{iface}/statistics')
    return {
        'interface': iface,
        'rx': int((base / 'rx_bytes').read_text().strip()),
        'tx': int((base / 'tx_bytes').read_text().strip()),
    }


def vnstat_day_month(iface):
    if not shutil.which('vnstat'):
        return None, None
    try:
        data = json.loads(run(['vnstat', '-i', iface, '--json']))
    except Exception:
        return None, None
    traffic = None
    for item in data.get('interfaces', []):
        if item.get('name') == iface:
            traffic = item.get('traffic', {})
            break
    if traffic is None and data.get('interfaces'):
        traffic = data['interfaces'][0].get('traffic', {})
    if not traffic:
        return None, None

    today = datetime.date.today()
    yesterday = today - datetime.timedelta(days=1)
    y_rx = y_tx = None
    for day in traffic.get('day', []):
        d = day.get('date', {})
        try:
            day_date = datetime.date(int(d['year']), int(d['month']), int(d['day']))
        except Exception:
            continue
        if day_date == yesterday:
            y_rx, y_tx = int(day.get('rx', 0)), int(day.get('tx', 0))
            break
    m_rx = m_tx = None
    for month in traffic.get('month', []):
        d = month.get('date', {})
        try:
            if int(d['year']) == today.year and int(d['month']) == today.month:
                m_rx, m_tx = int(month.get('rx', 0)), int(month.get('tx', 0))
                break
        except Exception:
            continue
    return (y_rx, y_tx) if y_rx is not None else None, (m_rx, m_tx) if m_rx is not None else None


def update_traffic_history(state, current, update_state):
    """Advance daily baseline at most once per UTC day when sending the report."""
    if not update_state:
        return
    now = datetime.datetime.now(datetime.timezone.utc)
    today_key = now.date().isoformat()
    if state.get('last_report_date') == today_key:
        return
    yesterday = (now.date() - datetime.timedelta(days=1)).isoformat()
    previous = state.get('last_traffic')
    history = state.setdefault('traffic_history', [])
    if previous and not any(item.get('date') == yesterday for item in history):
        prev_rx, prev_tx = int(previous.get('rx', 0)), int(previous.get('tx', 0))
        if current['rx'] >= prev_rx and current['tx'] >= prev_tx:
            rx_delta = current['rx'] - prev_rx
            tx_delta = current['tx'] - prev_tx
        else:
            rx_delta, tx_delta = current['rx'], current['tx']
        history.append({'date': yesterday, 'rx': rx_delta, 'tx': tx_delta})
        state['traffic_history'] = history[-400:]
    state['last_traffic'] = {'time': now.isoformat(), **current}
    state['last_report_date'] = today_key


def traffic_lines(c, state, update_state):
    iface = selected_iface(c)
    try:
        current = read_counters(iface)
    except Exception:
        return ['昨天: 无法读取网卡计数', '本月: 无法读取网卡计数']

    update_traffic_history(state, current, update_state)
    today = datetime.date.today()
    yesterday = (today - datetime.timedelta(days=1)).isoformat()
    month_prefix = today.strftime('%Y-%m')

    y_pair, m_pair = vnstat_day_month(iface)
    history = state.get('traffic_history', [])
    if y_pair is None:
        y_items = [item for item in history if item.get('date') == yesterday]
        if y_items:
            y_pair = (sum(int(i['rx']) for i in y_items), sum(int(i['tx']) for i in y_items))
    if m_pair is None:
        m_items = [item for item in history if str(item.get('date', '')).startswith(month_prefix)]
        if m_items:
            m_pair = (sum(int(i['rx']) for i in m_items), sum(int(i['tx']) for i in m_items))

    lines = []
    if y_pair:
        lines.append(f'昨天(UTC {yesterday}): {fmt_traffic(*y_pair)}')
    else:
        lines.append(f'昨天(UTC {yesterday}): 暂无完整记录（次日开始按日累计）')
    if m_pair:
        lines.append(f'本月(UTC {month_prefix}): {fmt_traffic(*m_pair)}')
    else:
        lines.append(f'本月(UTC {month_prefix}): 暂无完整记录')
    return lines


def allowed_login(ip, c):
    whitelist = set(split_words(c.get('SSH_WHITELIST', '')))
    if ip in whitelist:
        return True
    try:
        addr = ipaddress.ip_address(ip)
    except ValueError:
        return False
    for net in split_words(c.get('SSH_WHITELIST_NETS', '')):
        try:
            if addr in ipaddress.ip_network(net, strict=False):
                return True
        except ValueError:
            continue
    return False


def collect_ssh(c, period='24 hours ago'):
    text = run(['journalctl', '-u', 'ssh', '-u', 'sshd', '--since', period, '--no-pager', '-o', 'short-iso'], timeout=30)
    accepted_re = re.compile(r'Accepted (\S+) for (\S+) from ([0-9a-fA-F:.]+) port')
    fail_re = re.compile(r'(Invalid user|Failed password|Too many authentication failures)')
    accepted = []
    failed = 0
    for line in text.splitlines():
        m = accepted_re.search(line)
        if m:
            accepted.append({'method': m.group(1), 'user': m.group(2), 'ip': m.group(3)})
            continue
        if fail_re.search(line):
            failed += 1
    non_key = [x for x in accepted if x['method'] != 'publickey']
    unknown = [x for x in accepted if not allowed_login(x['ip'], c)]
    root_ok = [x for x in accepted if x['user'] == 'root']
    sources = Counter(x['ip'] for x in accepted)
    return {
        'accepted': accepted,
        'failed': failed,
        'non_key': non_key,
        'unknown': unknown,
        'root_ok': root_ok,
        'sources': sources,
    }


def collect_fail2ban(period='24 hours ago'):
    ban_total = 0
    log = Path('/var/log/fail2ban.log')
    since = datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(hours=24)
    if log.exists():
        try:
            for line in log.read_text(errors='replace').splitlines():
                try:
                    ts = datetime.datetime.strptime(line[:19], '%Y-%m-%d %H:%M:%S').replace(tzinfo=datetime.timezone.utc)
                except Exception:
                    continue
                if ts < since:
                    continue
                if re.search(r'\[sshd\]\s+Ban\s+', line):
                    ban_total += 1
        except PermissionError:
            pass
    if ban_total == 0:
        out = run(['journalctl', '-u', 'fail2ban', '--since', period, '--no-pager'], timeout=20)
        ban_total = len(re.findall(r'\bBan\s+\d+\.\d+\.\d+\.\d+', out))
    status = run(['fail2ban-client', 'status', 'sshd'])
    current = 0
    m = re.search(r'Currently banned:\s*(\d+)', status)
    if m:
        current = int(m.group(1))
    return ban_total, current


def service_line(c):
    units = split_words(c.get('EXPECTED_UNITS', ''), DEFAULT_UNITS)
    parts = []
    bad = []
    for name in units:
        unit = name if name.endswith('.service') else f'{name}.service'
        if not run(['systemctl', 'cat', unit]):
            parts.append(f'{name}:未安装')
            continue
        active = run(['systemctl', 'is-active', unit]) or 'unknown'
        parts.append(f'{name}:{active}')
        if active != 'active':
            bad.append(name)
    return ' / '.join(parts), bad


def resource_line():
    disk = run(['df', '-h', '/']).splitlines()
    disk_s = disk[-1] if len(disk) >= 2 else '未知'
    # df -h: Filesystem Size Used Avail Use% Mounted
    fields = disk_s.split()
    disk_txt = f'{fields[2]}/{fields[1]}（{fields[4]}）' if len(fields) >= 5 else disk_s
    mem = run(['free', '-h']).splitlines()
    mem_txt = '未知'
    for line in mem:
        if line.lower().startswith('mem:'):
            cols = line.split()
            if len(cols) >= 3:
                mem_txt = f'{cols[2]}/{cols[1]}'
            break
    who = run(['who']) or '无'
    who_s = '; '.join(who.splitlines()[:3]) if who != '无' else '无'
    return f'磁盘 {disk_txt}｜内存 {mem_txt}｜当前登录 {who_s}'


def top_processes(limit=5):
    text = run(['ps', '-eo', 'pid,comm,%cpu,%mem', '--sort=-%cpu'])
    rows = []
    for line in text.splitlines()[1:]:
        parts = line.split(None, 3)
        if len(parts) < 4:
            continue
        pid, comm, cpu, mem = parts[0], parts[1], parts[2], parts[3]
        if KERNEL_COMM.search(comm):
            continue
        if comm in {'ps', 'top'} or 'server_audit_report' in comm:
            continue
        rows.append(f'{comm}  CPU {cpu}% MEM {mem}%')
        if len(rows) >= limit:
            break
    return rows or ['无']


def is_known_local_proc(proc):
    lower = (proc or '').lower()
    return any(name in lower for name in KNOWN_LOCAL_PROCS)


def unexpected_public(c):
    expected = set(split_words(c.get('EXPECTED_PUBLIC_PORTS', ''), DEFAULT_PUBLIC_PORTS))
    text = run(['ss', '-lntup'])
    hits = []
    for line in text.splitlines():
        if 'LISTEN' not in line:
            continue
        cols = line.split()
        if len(cols) < 5:
            continue
        addr = cols[4]
        if addr.startswith('127.') or addr.startswith('[::1]') or '%lo' in addr:
            continue
        port = addr.rsplit(':', 1)[-1].strip('[]')
        if port in expected:
            continue
        proc = '未知进程'
        m = re.search(r'users:\(\("([^"]+)"', line)
        if m:
            proc = m.group(1)
        # 本机预期服务换端口时只记端口清单，不额外告警
        if is_known_local_proc(proc):
            continue
        item = f'tcp {addr} ({proc}): 非本机预期服务对外监听，建议核查'
        if item not in hits:
            hits.append(item)
    return hits[:10]


def certificate_line(c):
    path = c.get('CERT_PATH', '')
    if not path:
        domain = c.get('DOMAIN', '')
        if domain:
            path = f'/etc/letsencrypt/live/{domain}/fullchain.pem'
    if not path or not Path(path).exists():
        return '未配置证书'
    out = run(['openssl', 'x509', '-enddate', '-noout', '-in', path])
    if not out.startswith('notAfter='):
        return '无法读取'
    try:
        expires = datetime.datetime.strptime(out.split('=', 1)[1], '%b %d %H:%M:%S %Y %Z').replace(tzinfo=datetime.timezone.utc)
        days = (expires.date() - datetime.datetime.now(datetime.timezone.utc).date()).days
        return f'{expires.strftime("%Y-%m-%d")}（剩 {days} 天）'
    except ValueError:
        return out.split('=', 1)[1]


def expiry_line(c):
    raw = c.get('SERVER_EXPIRY', '')
    if not raw:
        return '未配置'
    try:
        exp = datetime.date.fromisoformat(raw)
        days = (exp - datetime.date.today()).days
        return f'{exp.isoformat()}（剩 {days} 天）'
    except ValueError:
        return raw


def host_line(c):
    public_ip = c.get('PUBLIC_IP') or run(['hostname', '-I']).split()[:1]
    if isinstance(public_ip, list):
        public_ip = public_ip[0] if public_ip else '未知'
    private_ip = c.get('PRIVATE_IP') or ''
    location = c.get('SERVER_LOCATION') or ''
    parts = [socket.gethostname(), public_ip]
    if private_ip:
        parts.append(private_ip)
    if location:
        parts.append(location)
    return '｜'.join(parts)


def build_report(update_state=True):
    c = cfg()
    state = load_state()
    now = datetime.datetime.now(datetime.timezone.utc)
    today = now.date()
    ssh = collect_ssh(c)
    ban_today, ban_current = collect_fail2ban()
    services, bad_services = service_line(c)
    unexpected = unexpected_public(c)
    traffic = traffic_lines(c, state, update_state)
    if update_state:
        save_state(state)

    alerts = []
    if ssh['unknown']:
        alerts.append('发现非白名单 IP 成功登录')
    if ssh['non_key']:
        alerts.append('发现非 publickey 登录')
    if ssh['root_ok']:
        alerts.append('发现 root 成功登录')
    if bad_services:
        alerts.append('服务异常: ' + ', '.join(bad_services))
    if unexpected:
        alerts.append('发现非预期对外监听')

    status = '异常' if alerts else '正常'
    subject = f'[服务器日报] {status} - {today.isoformat()}'

    all_key = bool(ssh['accepted']) and not ssh['non_key'] and not ssh['unknown']
    if not ssh['accepted']:
        ssh_summary = f'SSH 成功 0 次｜扫描/失败 {ssh["failed"]}｜今日封禁 {ban_today}，当前封禁 {ban_current}'
    elif all_key:
        ssh_summary = (
            f'SSH 成功 {len(ssh["accepted"])} 次（均白名单密钥）｜'
            f'扫描/失败 {ssh["failed"]}｜今日封禁 {ban_today}，当前封禁 {ban_current}'
        )
    else:
        ssh_summary = (
            f'SSH 成功 {len(ssh["accepted"])} 次｜非密钥 {len(ssh["non_key"])}｜'
            f'非白名单 {len(ssh["unknown"])}｜扫描/失败 {ssh["failed"]}｜'
            f'今日封禁 {ban_today}，当前封禁 {ban_current}'
        )

    lines = [
        f'服务器日报 - {now:%Y-%m-%d %H:%M:%S UTC}',
        '=' * 48,
        f'结论: {status}',
        f'到期: 服务器 {expiry_line(c)}｜证书 {certificate_line(c)}',
        f'主机: {host_line(c)}',
        '',
        '流量',
    ]
    lines.extend(f'- {x}' for x in traffic)
    lines += [
        '',
        '状态',
        f'- 服务: {services}',
        f'- 资源: {resource_line()}',
        f'- 安全: {ssh_summary}',
        '',
        '进程 Top 5',
    ]
    lines.extend(f'- {x}' for x in top_processes())
    lines += ['', '对外异常']
    if unexpected:
        lines.extend(f'- {x}' for x in unexpected)
    else:
        lines.append('- 无（未发现非预期对外监听或非本机可疑服务）')

    if alerts:
        lines += ['', '异常项']
        lines.extend(f'- {x}' for x in alerts)
        ssh_alerts = {'发现非白名单 IP 成功登录', '发现非 publickey 登录', '发现 root 成功登录'}
        if ssh['sources'] and ssh_alerts.intersection(alerts):
            lines += ['', '成功登录来源（异常相关）']
            for ip, count in ssh['sources'].most_common(10):
                flag = '白名单' if allowed_login(ip, c) else '非白名单'
                lines.append(f'- {ip}: {count} 次，{flag}')

    return subject, '\n'.join(lines)


def send(subject, text, c):
    auth = c.get('SMTP_AUTH_B64', '')
    if not all((c.get('SMTP_HOST'), c.get('SMTP_USER'), auth, c.get('REPORT_RECIPIENT'))):
        return False
    msg = email.message.EmailMessage()
    msg['Subject'] = subject
    msg['From'] = c['SMTP_USER']
    msg['To'] = c['REPORT_RECIPIENT']
    msg.set_content(text)
    password = base64.b64decode(auth).decode()
    with smtplib.SMTP_SSL(c['SMTP_HOST'], int(c.get('SMTP_PORT', '465')), timeout=30) as server:
        server.login(c['SMTP_USER'], password)
        server.send_message(msg)
    return True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--no-email', action='store_true')
    args = parser.parse_args()
    # preview should not advance traffic baselines
    update_state = not args.no_email
    subject, body = build_report(update_state=update_state)
    print(subject)
    print(body)
    if args.no_email:
        return
    c = cfg()
    print('日报发送成功' if send(subject, body, c) else 'SMTP 未完整配置，跳过发送')


if __name__ == '__main__':
    main()
