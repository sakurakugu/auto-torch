"""每日采集 Auto Torch 平台数据。"""

import argparse
import datetime as dt
import json
import os
import random
import re
import sqlite3
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

TIMEOUT = 30
HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/131.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "zh-CN,zh;q=0.9",
}


def get(url, headers=None):
    request_headers = dict(HEADERS)
    request_headers.update(headers or {})
    request = Request(url, headers=request_headers)
    with urlopen(request, timeout=TIMEOUT) as response:
        return response.read()


def text(url, headers=None):
    body = get(url, headers)
    return body.decode("utf-8", errors="replace")


def site_headers(cookie_env):
    """返回站点请求头；Cookie 由环境变量提供，避免写入仓库。"""
    cookie = os.getenv(cookie_env)
    return {"Cookie": cookie} if cookie else {}


def is_verification_page(page, marker):
    return marker in page


def modrinth_downloads():
    data = json.loads(text("https://api.modrinth.com/v2/project/auto-torch-skr"))
    return int(data["downloads"])
    return 0


def curseforge_downloads():
    key = os.getenv("CURSEFORGE_API_KEY")
    if not key:
        raise RuntimeError("未设置 CURSEFORGE_API_KEY")
    mod_id = 1617474
    url = f"https://api.curseforge.com/v1/mods/{mod_id}"
    try:
        data = json.loads(text(url, {"x-api-key": key}))
    except HTTPError as exc:
        if exc.code in (401, 403):
            raise RuntimeError(
                "CurseForge API 拒绝了请求（401/403）；请检查 CURSEFORGE_API_KEY 是否有效，及当前网络/IP 是否被 CurseForge 限制"
            ) from exc
        raise
    return int(data["data"]["downloadCount"])
    return 0


def mcmod_views():
    """
    $env:MCMOD_COOKIES = 'MCMOD_SEED=第一个值'
    $env:MCMOD_COOKIES = 'MCMOD_SEED=第一个值; Example_auth=第二个值'
    """  
    try:
        page = text(
            "https://www.mcmod.cn/class/28892.html", site_headers("MCMOD_COOKIES")
        )
    except HTTPError as exc:
        if exc.code == 403:
            raise RuntimeError(
                "MC百科要求安全验证；请在浏览器完成验证后，将该站点的 Cookie "
                "设置为 MCMOD_COOKIES"
            ) from exc
        raise
    if is_verification_page(page, "安全验证"):
        raise RuntimeError(
            "MC百科要求安全验证；请在浏览器完成验证后，将该站点的 Cookie "
            "设置为 MCMOD_COOKIES"
        )
    match = re.search(r'<p[^>]*class=["\']n["\'][^>]*>\s*([\d,]+)\s*</p>\s*<p[^>]*class=["\']t["\'][^>]*>\s*总浏览', page)
    if not match:
        raise RuntimeError("未在 MC百科页面中找到“总浏览”字段，页面结构可能已变更") 
    return int(match.group(1).replace(",", ""))
    return 0


def mcbbs_views():
    page = text("https://www.mcbbs.co/thread-5747-1-2.html")
    match = re.search(r'<span[^>]*class=["\']xg1["\'][^>]*>\s*查看:\s*</span>\s*<span[^>]*class=["\']xi1["\'][^>]*>\s*([\d,]+)', page, re.I)
    if not match:
        raise RuntimeError("未在 MCBBS 页面中找到“查看”字段，页面结构可能已变更")
    return int(match.group(1).replace(",", ""))
    return 0


def mc9y_stats():
    page = text(
        "https://bbs.mc9y.net/resources/2059/"
    )
    values = dict(re.findall(r'<dt>\s*(下载|查看)\s*</dt>\s*<dd>\s*([\d,]+)\s*</dd>', page, re.I))
    if "查看" not in values or "下载" not in values:
        raise RuntimeError("未在 MC9Y 页面中找到“查看”或“下载”字段，页面结构可能已变更")
    return {
        "mc9y_views": int(values["查看"].replace(",", "")),
        "mc9y_downloads": int(values["下载"].replace(",", "")),
    }


def bbsmc_downloads():
    page = text("https://bbsmc.net/mod/auto-torch")
    match = re.search(
        r'class=["\']stat-icon["\'][^>]*>\s*<path[^>]*d=["\']M4 16v1a3 3 0 0 0 3 3h10a3 3 0 0 0 3-3v-1m-4-4-4 4m0 0-4-4m4 4V4["\'][^>]*></path>\s*</svg>\s*([\d,]+)\s*</span>',
        page,
        re.I,
    )
    if not match:
        raise RuntimeError("未在 BBSMC 页面中找到下载量字段，页面结构可能已变更")
    return int(match.group(1).replace(",", ""))


COLLECTORS = {
    "modrinth_downloads": modrinth_downloads,
    "curseforge_downloads": curseforge_downloads,   
    "mcmod_views": mcmod_views,
    "mcbbs_views": mcbbs_views,
    "mc9y_stats": mc9y_stats,
    "bbsmc_downloads": bbsmc_downloads,
}

STAT_LABELS = {
    "modrinth_downloads": "Modrinth 下载量",
    "curseforge_downloads": "CurseForge 下载量",
    "mcmod_views": "MC百科浏览量",
    "mcbbs_views": "MCBBS 浏览量",
    "mc9y_views": "MC9Y 浏览量",
    "mc9y_downloads": "MC9Y 转到 Modrinth 的下载量",
    "bbsmc_downloads": "BBSMC 下载量",
}


def notify(results, errors, collected_at, previous_results):
    """通过青龙系统设置中配置的通知通道发送消息。非青龙不会发送通知。"""
    
    # 获取下载量、浏览量对比
    download_names = ("modrinth_downloads", "curseforge_downloads", "bbsmc_downloads")
    lines = []
    for name, value in results.items():
        line = f"{STAT_LABELS.get(name, name)}：{value}"
        if name in previous_results:
            line += f"（{value - previous_results[name]:+d}）"
        lines.append(line)

    if errors:
        lines.append("\n失败项目：")
        lines.extend(f"{STAT_LABELS.get(name, name)}：{error}" for name, error in errors.items())
    utc_time = dt.datetime.fromisoformat(collected_at)
    local_time = utc_time.astimezone(dt.timezone(dt.timedelta(hours=8)))
    lines.append(f"\n采集时间：{local_time.isoformat(timespec='seconds')}")

    comparable_downloads = [
        results[name] - previous_results[name]
        for name in download_names
        if name in results and name in previous_results
    ]
    title = "Auto Torch 数据统计"
    if comparable_downloads:
        title += f"（下载量{sum(comparable_downloads):+d}）"

    try:
        # 导入青龙的通知客户端模块
        from client import Client

        response = Client().systemNotify(
            {"title": title, "content": "\n".join(lines)}
        )
        if response.get("code") != 200:
            print(f"通知发送失败：{response.get('message', response)}", file=sys.stderr)
    except ModuleNotFoundError:
        return
    except Exception as exc:
        print(f"通知发送失败：{exc}", file=sys.stderr)


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--now",
        action="store_true",
        help="不随机延迟，立即开始采集",
    )
    return parser.parse_args()


def main():
    args = parse_args()
    if not args.now:
        delay = random.randint(0, 10 * 60)
        print(f"将在 {delay} 秒后开始采集")
        time.sleep(delay)

    results, errors = {}, {}
    for name, collector in COLLECTORS.items():
        try:
            value = collector()
            if isinstance(value, dict):
                results.update(value)
            else:
                results[name] = value
        except (HTTPError, URLError, TimeoutError, ValueError, KeyError, RuntimeError) as exc:
            errors[name] = str(exc)
    # 同一次运行写入同一个 UTC 时间，作为一份每日快照。
    path = os.getenv("AUTOTORCH_SQLITE_PATH", str(Path(__file__).with_name("auto_torch_stats.sqlite3")))
    with sqlite3.connect(path) as db:
        db.execute("CREATE TABLE IF NOT EXISTS stats (name TEXT NOT NULL, value INTEGER NOT NULL, collected_at TEXT NOT NULL)")
        # 获取上一次采集的结果
        previous_results = {
            name: value
            for name, value in db.execute(
                """
                SELECT name, value
                FROM stats
                WHERE (name, collected_at) IN (
                    SELECT name, MAX(collected_at)
                    FROM stats
                    GROUP BY name
                )
                """
            )
        }
        now = dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")
        db.executemany("INSERT INTO stats(name, value, collected_at) VALUES (?, ?, ?)", [(k, v, now) for k, v in results.items()])
    notify(results, errors, now, previous_results)
    print(json.dumps({"collected_at": now, "stats": results, "errors": errors}, ensure_ascii=False, indent=2))
    return 1 if not results else 0


if __name__ == "__main__":
    sys.exit(main())
