#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""番茄小说搜索与下载代理服务"""

from __future__ import annotations

import json
import os
import random
import re
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from html import unescape
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, quote, unquote, urlparse
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent
CHARSET_PATH = ROOT / "charset.json"
INDEX_PATH = ROOT / "index.html"
PORT = int(os.environ.get("PORT", "8787"))
HOST = os.environ.get("HOST", "0.0.0.0")
MAX_WORKERS = int(os.environ.get("MAX_WORKERS", "6"))
CHAPTER_DELAY = float(os.environ.get("CHAPTER_DELAY", "0.05"))

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)
SEARCH_URLS = [
    "https://api-lf.fanqiesdk.com/api/novel/channel/homepage/search/search/v1/",
    "https://novel.snssdk.com/api/novel/channel/homepage/search/search/v1/",
]
# 第三方正文节点（Tomato 生态公开状态页同款），可获取 Web 锁定章全文
CONTENT_API_HOSTS = [
    h.strip().rstrip("/")
    for h in os.environ.get(
        "CONTENT_API_HOSTS",
        "http://110.42.57.146:4018,"
        "http://81.70.223.143:6897,"
        "http://43.143.149.30:8008,"
        "http://59.110.160.171:5007,"
        "http://103.43.9.59",
    ).split(",")
    if h.strip()
]
CODE_RANGES = [[58344, 58715], [58345, 58716]]
CONTENT_HOST_LOCK = threading.Lock()
CONTENT_HOST_IDX = 0

with open(CHARSET_PATH, "r", encoding="utf-8") as f:
    CHARSET = json.load(f)

JOBS: Dict[str, Dict[str, Any]] = {}
JOBS_LOCK = threading.Lock()
COOKIE_LOCK = threading.Lock()
COOKIE_STATE = {"value": None, "ts": 0.0}


def http_get(url: str, headers: Optional[Dict[str, str]] = None, timeout: int = 20) -> Tuple[int, bytes, Dict[str, str]]:
    h = {
        "User-Agent": UA,
        "Accept": "*/*",
        "Accept-Encoding": "identity",
        "Referer": "https://fanqienovel.com/",
    }
    if headers:
        h.update(headers)
    req = Request(url, headers=h)
    with urlopen(req, timeout=timeout) as resp:
        return resp.status, resp.read(), dict(resp.headers)


def decode_content(content: str, mode: int = 0) -> str:
    result = []
    lo, hi = CODE_RANGES[mode]
    table = CHARSET[mode]
    for char in content:
        uni = ord(char)
        if lo <= uni <= hi:
            bias = uni - lo
            if 0 <= bias < len(table) and table[bias] != "?":
                result.append(table[bias])
            else:
                result.append(char)
        else:
            result.append(char)
    return "".join(result)


def html_to_text(html: str) -> str:
    s = re.sub(r"<br\s*/?>", "\n", html, flags=re.I)
    s = re.sub(r"</p\s*>", "\n", s, flags=re.I)
    s = re.sub(r"<[^>]+>", "", s)
    s = unescape(s)
    lines = [ln.strip() for ln in s.splitlines()]
    return "\n".join(ln for ln in lines if ln)


def pick_decode_mode(content: str) -> int:
    d0 = decode_content(content, 0)
    d1 = decode_content(content, 1)
    # prefer mode that yields more CJK and fewer private-use leftovers
    def score(s: str) -> int:
        cjk = sum(1 for c in s if "\u4e00" <= c <= "\u9fff")
        priv = sum(1 for c in s if 0xE000 <= ord(c) <= 0xF8FF or 58344 <= ord(c) <= 58716)
        return cjk * 2 - priv * 5

    return 0 if score(d0) >= score(d1) else 1


def extract_content_from_reader_html(html: str) -> Tuple[str, str, Dict[str, Any]]:
    title = ""
    meta: Dict[str, Any] = {
        "locked": False,
        "word_number": 0,
        "preview_only": False,
    }
    locked = re.search(r'"isChapterLock"\s*:\s*true', html)
    meta["locked"] = bool(locked)
    wn = re.search(r'"chapterWordNumber"\s*:\s*"?(\d+)"?', html)
    if wn:
        meta["word_number"] = int(wn.group(1))

    mt = re.search(r'muye-reader-title[^>]*>(.*?)</', html, re.S)
    if mt:
        title = re.sub(r"<[^>]+>", "", mt.group(1)).strip()
    if not title:
        mt = re.search(r'"chapterData"\s*:\s*\{[^}]*?"title"\s*:\s*"([^"]+)"', html)
        if mt:
            title = mt.group(1)
    if not title:
        mt = re.search(r'"title"\s*:\s*"(第[^"]+)"', html)
        if mt:
            title = mt.group(1)

    # 正确匹配 JSON 字符串，避免非贪婪截断
    m = re.search(r'"content"\s*:\s*"((?:\\.|[^"\\])*)"', html)
    raw = ""
    if m:
        try:
            raw = json.loads('"' + m.group(1) + '"')
        except Exception:
            try:
                raw = m.group(1).encode("utf-8").decode("unicode_escape")
            except Exception:
                raw = m.group(1)
    if not raw:
        parts = re.findall(
            r'class="[^"]*muye-reader-content[^"]*"[^>]*>(.*?)</div>',
            html,
            re.S,
        )
        if parts:
            paras = re.findall(r"<p[^>]*>(.*?)</p>", parts[0], re.S)
            raw = "\n".join(
                re.sub(r"<[^>]+>", "", unescape(p)).strip() for p in paras
            )

    if not raw:
        return title, "", meta

    mode = pick_decode_mode(raw)
    text = html_to_text(decode_content(raw, mode))
    # Web 端锁定章通常只返回约 200 字试读
    if meta["locked"] or (
        meta["word_number"] > 800 and len(text) < max(300, meta["word_number"] * 0.25)
    ):
        meta["preview_only"] = True
    return title, text, meta


def get_cookie(force: bool = False) -> str:
    with COOKIE_LOCK:
        if (
            not force
            and COOKIE_STATE["value"]
            and time.time() - COOKIE_STATE["ts"] < 3600
        ):
            return COOKIE_STATE["value"]
        value = f"novel_web_id={random.randint(6 * 10**18, 9 * 10**18 - 1)}"
        COOKIE_STATE["value"] = value
        COOKIE_STATE["ts"] = time.time()
        return value


def _pick_content_hosts() -> List[str]:
    global CONTENT_HOST_IDX
    hosts = [h for h in CONTENT_API_HOSTS if h]
    if not hosts:
        return []
    with CONTENT_HOST_LOCK:
        start = CONTENT_HOST_IDX % len(hosts)
        CONTENT_HOST_IDX += 1
    return hosts[start:] + hosts[:start]


def fetch_chapter_via_third_party(item_id: str) -> Tuple[str, str]:
    """通过第三方 /content 接口获取完整正文（含 Web 锁定章）。"""
    last_err = None
    for host in _pick_content_hosts():
        url = f"{host}/content?item_id={item_id}"
        try:
            _, body, _ = http_get(
                url,
                headers={"Accept": "application/json", "Referer": host + "/"},
                timeout=18,
            )
            if not body:
                last_err = "empty body"
                continue
            data = json.loads(body.decode("utf-8", "ignore") or "{}")
            if data.get("code") not in (0, "0", None):
                last_err = data.get("msg") or data.get("message") or str(data.get("code"))
                continue
            payload = data.get("data") or {}
            if isinstance(payload, dict) and item_id in payload:
                payload = payload[item_id] or {}
            raw = ""
            title = ""
            if isinstance(payload, dict):
                raw = payload.get("content") or payload.get("text") or ""
                title = payload.get("title") or payload.get("chapter_title") or ""
            elif isinstance(payload, str):
                raw = payload
            if not raw or len(raw) < 30:
                last_err = "short content"
                continue
            # 第三方一般返回明文 HTML，偶发带字体加密
            mode = pick_decode_mode(raw)
            text = html_to_text(decode_content(raw, mode))
            if len(text) < 30:
                last_err = "decode short"
                continue
            return title, text
        except Exception as e:
            last_err = str(e)
            continue
    raise RuntimeError(last_err or "第三方正文接口全部失败")


def fetch_chapter_via_web(item_id: str, book_id: str = "") -> Tuple[str, str, Dict[str, Any]]:
    cookie = get_cookie()
    url = f"https://fanqienovel.com/reader/{item_id}"
    headers = {
        "User-Agent": UA,
        "Accept": "text/html,application/xhtml+xml",
        "Accept-Encoding": "identity",
        "Referer": f"https://fanqienovel.com/page/{book_id}" if book_id else "https://fanqienovel.com/",
        "Cookie": cookie,
    }
    last_err = None
    for attempt in range(3):
        try:
            _, body, _ = http_get(url, headers=headers, timeout=20)
            html = body.decode("utf-8", "ignore")
            title, text, meta = extract_content_from_reader_html(html)
            if len(text) > 20:
                return title, text, meta
            cookie = get_cookie(force=True)
            headers["Cookie"] = cookie
            last_err = "empty content"
        except Exception as e:
            last_err = str(e)
            time.sleep(0.3 * (attempt + 1))
    raise RuntimeError(f"网页 reader 失败: {last_err}")


def fetch_chapter(item_id: str, book_id: str = "") -> Tuple[str, str, Dict[str, Any]]:
    """优先第三方完整正文，失败再回退官网 reader（可能仅试读）。"""
    # 1) 第三方完整正文
    try:
        title, text = fetch_chapter_via_third_party(item_id)
        return title, text, {"locked": False, "preview_only": False, "source": "third_party"}
    except Exception as third_err:
        pass

    # 2) 官网 reader 回退
    title, text, meta = fetch_chapter_via_web(item_id, book_id)
    meta["source"] = "web"
    if meta.get("preview_only"):
        tip = (
            f"\n\n【本章为网页端试读/锁定章节，仅获取到部分正文"
            f"（约 {len(text)} 字"
            f"{'，官方字数 ' + str(meta['word_number']) if meta.get('word_number') else ''}）。"
            f"第三方正文接口暂不可用。】"
        )
        text = text + tip
    return title, text, meta


def search_books(query: str, offset: int = 0) -> Dict[str, Any]:
    q = quote(query)
    last_err = None
    for base in SEARCH_URLS:
        url = f"{base}?aid=1967&offset={offset}&q={q}"
        try:
            # no Origin header - servers reject Origin
            _, body, _ = http_get(url, headers={"Accept": "application/json"}, timeout=15)
            data = json.loads(body.decode("utf-8"))
            if data.get("code") != 0:
                last_err = data.get("message") or str(data.get("code"))
                continue
            ret = data.get("data", {}).get("ret_data") or []
            books = []
            for it in ret:
                books.append(
                    {
                        "book_id": str(it.get("book_id") or ""),
                        "title": it.get("title") or "",
                        "author": it.get("author") or "",
                        "abstract": it.get("abstract") or "",
                        "thumb_url": it.get("thumb_url") or "",
                        "score": it.get("score") or "",
                        "category": it.get("category") or "",
                        "creation_status": it.get("creation_status"),
                    }
                )
            return {
                "code": 0,
                "query": query,
                "offset": offset,
                "next_offset": data.get("data", {}).get("offset", offset + len(books)),
                "has_more": bool(data.get("data", {}).get("has_more")),
                "books": books,
            }
        except Exception as e:
            last_err = str(e)
            continue
    return {"code": -1, "message": f"搜索失败: {last_err}", "books": []}


def get_directory(book_id: str) -> Dict[str, Any]:
    url = f"https://fanqienovel.com/api/reader/directory/detail?bookId={book_id}"
    _, body, _ = http_get(url, headers={"Accept": "application/json"}, timeout=20)
    data = json.loads(body.decode("utf-8") or "{}")
    if data.get("code") not in (0, None) and "data" not in data:
        raise RuntimeError(data.get("message") or "目录获取失败")
    d = data.get("data") or {}
    chapters: List[Dict[str, Any]] = []
    cl = d.get("chapterListWithVolume") or []
    if cl and isinstance(cl[0], list):
        for vol in cl:
            for ch in vol:
                chapters.append(
                    {
                        "item_id": str(ch.get("itemId") or ch.get("item_id") or ""),
                        "title": ch.get("title") or "",
                        "volume_name": ch.get("volume_name") or "",
                        "need_pay": ch.get("needPay") or 0,
                    }
                )
    else:
        for item_id in d.get("allItemIds") or []:
            chapters.append({"item_id": str(item_id), "title": "", "volume_name": "", "need_pay": 0})
    return {
        "book_id": book_id,
        "chapter_count": len(chapters),
        "chapters": chapters,
        "volume_names": d.get("volumeNameList") or [],
    }


def get_book_meta(book_id: str) -> Dict[str, str]:
    meta = {"book_id": book_id, "title": f"小说{book_id}", "author": "未知", "abstract": ""}
    try:
        url = f"https://fanqienovel.com/page/{book_id}"
        _, body, _ = http_get(url, timeout=20)
        html = body.decode("utf-8", "ignore")
        m = re.search(r'<script type="application/ld\+json">(.*?)</script>', html, re.S)
        if m:
            try:
                ld = json.loads(m.group(1))
                meta["title"] = ld.get("name") or meta["title"]
                authors = ld.get("author") or []
                if authors and isinstance(authors, list):
                    if isinstance(authors[0], dict):
                        meta["author"] = authors[0].get("name") or meta["author"]
                    elif isinstance(authors[0], str):
                        meta["author"] = authors[0]
                meta["abstract"] = ld.get("description") or ""
            except Exception:
                pass
        t = re.search(r"<title>(.*?)</title>", html)
        if t:
            title = t.group(1).split("_")[0].strip()
            title = re.sub(r"完整版在线免费阅读$", "", title)
            if title and (meta["title"].startswith("小说") or len(title) < len(meta["title"]) + 5):
                meta["title"] = title
        if meta["author"] == "未知":
            am = re.search(r'author["\']?\s*[:=]\s*["\']([^"\']+)["\']', html)
            if am:
                meta["author"] = am.group(1)
        if not meta["abstract"]:
            dm = re.search(r'<meta name="description" content="([^"]+)"', html)
            if dm:
                meta["abstract"] = dm.group(1)
    except Exception:
        pass
    return meta


def safe_filename(name: str) -> str:
    name = re.sub(r'[\\/:*?"<>|]+', "_", name).strip() or "novel"
    return name[:80]


def run_download_job(job_id: str, book_id: str, max_chapters: int = 0) -> None:
    with JOBS_LOCK:
        job = JOBS[job_id]
        job["status"] = "running"
        job["message"] = "获取书籍信息..."
        job["progress"] = 1

    try:
        meta = get_book_meta(book_id)
        with JOBS_LOCK:
            job["title"] = meta["title"]
            job["author"] = meta["author"]
            job["message"] = "获取目录..."
            job["progress"] = 3

        directory = get_directory(book_id)
        chapters = directory["chapters"]
        if max_chapters and max_chapters > 0:
            chapters = chapters[:max_chapters]
        total = len(chapters)
        if total == 0:
            raise RuntimeError("章节列表为空，可能书籍不存在或暂不支持")

        with JOBS_LOCK:
            job["total"] = total
            job["done"] = 0
            job["message"] = f"开始下载，共 {total} 章"
            job["progress"] = 5

        results: List[Optional[Tuple[str, str]]] = [None] * total
        errors: List[str] = []
        preview_count = 0
        lock = threading.Lock()
        done_count = 0

        def worker(idx: int, ch: Dict[str, Any]) -> None:
            nonlocal done_count, preview_count
            item_id = ch["item_id"]
            title = ch.get("title") or f"第{idx + 1}章"
            try:
                got_title, text, ch_meta = fetch_chapter(item_id, book_id)
                if got_title:
                    title = got_title
                if not text:
                    raise RuntimeError("空正文")
                results[idx] = (title, text)
                if ch_meta.get("preview_only"):
                    with lock:
                        preview_count += 1
                        errors.append(f"{idx + 1}:{item_id}:网页端仅试读")
            except Exception as e:
                results[idx] = (title, f"【本章获取失败: {e}】")
                with lock:
                    errors.append(f"{idx + 1}:{item_id}:{e}")
            finally:
                with lock:
                    done_count += 1
                    cur = done_count
                with JOBS_LOCK:
                    job["done"] = cur
                    job["progress"] = 5 + int(cur / total * 90)
                    job["message"] = f"下载中 {cur}/{total}"
                if CHAPTER_DELAY:
                    time.sleep(CHAPTER_DELAY)

        with ThreadPoolExecutor(max_workers=MAX_WORKERS) as ex:
            futs = [ex.submit(worker, i, ch) for i, ch in enumerate(chapters)]
            for fut in as_completed(futs):
                fut.result()

        lines = [
            meta["title"],
            f"作者：{meta['author']}",
            f"书籍ID：{book_id}",
            f"章节数：{total}",
            "",
            "=" * 40,
            "",
        ]
        if meta.get("abstract"):
            lines.insert(4, f"简介：{meta['abstract'][:500]}")
        if preview_count:
            lines.insert(
                5,
                f"说明：有 {preview_count} 章在网页端为锁定/试读，仅含部分正文；完整正文需番茄 App 或 SVIP。",
            )

        for i, item in enumerate(results):
            if not item:
                continue
            title, text = item
            lines.append(title)
            lines.append("")
            lines.append(text)
            lines.append("")
            lines.append("-" * 30)
            lines.append("")

        content = "\n".join(lines)
        filename = f"{safe_filename(meta['title'])}-{book_id}.txt"
        out_path = ROOT / "downloads" / f"{job_id}.txt"
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(content, encoding="utf-8")

        with JOBS_LOCK:
            job["status"] = "done"
            job["progress"] = 100
            full_ok = total - preview_count - sum(
                1 for e in errors if "获取失败" in e or "失败" in e
            )
            # errors 含试读标注，分开统计
            hard_errors = [e for e in errors if "仅试读" not in e]
            if preview_count and not hard_errors:
                job["message"] = (
                    f"下载完成（完整 {total - preview_count}/{total} 章，"
                    f"{preview_count} 章为网页试读）"
                )
            elif hard_errors:
                job["message"] = f"下载完成（{len(hard_errors)} 章失败，{preview_count} 章试读）"
            else:
                job["message"] = "下载完成"
            job["filename"] = filename
            job["filepath"] = str(out_path)
            job["size"] = out_path.stat().st_size
            job["errors"] = errors[:30]
            job["error_count"] = len(hard_errors)
            job["preview_count"] = preview_count
            job["full_count"] = total - preview_count
    except Exception as e:
        with JOBS_LOCK:
            job["status"] = "error"
            job["message"] = str(e)
            job["progress"] = 0


class Handler(BaseHTTPRequestHandler):
    server_version = "FanqieProxy/1.0"

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"[{self.log_date_time_string()}] {self.address_string()} {fmt % args}")

    def _cors(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def _json(self, code: int, payload: Any) -> None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self._cors()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _text(self, code: int, text: str, content_type: str = "text/plain; charset=utf-8") -> None:
        data = text.encode("utf-8")
        self.send_response(code)
        self._cors()
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _file(self, path: Path, filename: str, content_type: str) -> None:
        data = path.read_bytes()
        self.send_response(200)
        self._cors()
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header(
            "Content-Disposition",
            f"attachment; filename*=UTF-8''{quote(filename)}",
        )
        self.end_headers()
        self.wfile.write(data)

    def do_OPTIONS(self) -> None:
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path
        qs = parse_qs(parsed.query)

        if path in ("/", "/index.html"):
            if INDEX_PATH.exists():
                data = INDEX_PATH.read_bytes()
                self.send_response(200)
                self._cors()
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)
            else:
                self._text(404, "index.html not found")
            return

        if path == "/api/health":
            self._json(200, {"ok": True, "service": "fanqie-proxy"})
            return

        if path == "/api/search":
            q = (qs.get("q") or qs.get("query") or [""])[0].strip()
            offset = int((qs.get("offset") or ["0"])[0] or 0)
            if not q:
                self._json(400, {"code": -1, "message": "缺少搜索关键词 q"})
                return
            self._json(200, search_books(q, offset))
            return

        if path.startswith("/api/book/"):
            book_id = path[len("/api/book/") :].strip("/")
            if not book_id.isdigit():
                self._json(400, {"code": -1, "message": "无效 book_id"})
                return
            try:
                meta = get_book_meta(book_id)
                directory = get_directory(book_id)
                self._json(
                    200,
                    {
                        "code": 0,
                        **meta,
                        "chapter_count": directory["chapter_count"],
                        "volume_names": directory["volume_names"],
                    },
                )
            except Exception as e:
                self._json(500, {"code": -1, "message": str(e)})
            return

        if path.startswith("/api/job/"):
            rest = path[len("/api/job/") :].strip("/")
            parts = rest.split("/")
            job_id = parts[0]
            with JOBS_LOCK:
                job = JOBS.get(job_id)
            if not job:
                self._json(404, {"code": -1, "message": "任务不存在"})
                return
            if len(parts) > 1 and parts[1] == "file":
                if job.get("status") != "done" or not job.get("filepath"):
                    self._json(400, {"code": -1, "message": "文件未就绪"})
                    return
                self._file(Path(job["filepath"]), job.get("filename") or f"{job_id}.txt", "text/plain; charset=utf-8")
                return
            self._json(
                200,
                {
                    "code": 0,
                    "job_id": job_id,
                    "status": job.get("status"),
                    "progress": job.get("progress", 0),
                    "message": job.get("message", ""),
                    "done": job.get("done", 0),
                    "total": job.get("total", 0),
                    "title": job.get("title", ""),
                    "author": job.get("author", ""),
                    "filename": job.get("filename"),
                    "size": job.get("size"),
                    "error_count": job.get("error_count", 0),
                    "preview_count": job.get("preview_count", 0),
                    "full_count": job.get("full_count", 0),
                },
            )
            return

        if path == "/api/download":
            book_id = (qs.get("book_id") or qs.get("id") or [""])[0].strip()
            if not book_id.isdigit():
                self._json(400, {"code": -1, "message": "缺少有效 book_id"})
                return
            max_chapters = int((qs.get("max_chapters") or ["0"])[0] or 0)
            job_id = uuid.uuid4().hex[:12]
            with JOBS_LOCK:
                JOBS[job_id] = {
                    "status": "queued",
                    "progress": 0,
                    "message": "排队中",
                    "book_id": book_id,
                    "created_at": time.time(),
                }
            t = threading.Thread(
                target=run_download_job,
                args=(job_id, book_id, max_chapters),
                daemon=True,
            )
            t.start()
            self._json(200, {"code": 0, "job_id": job_id, "book_id": book_id})
            return

        self._json(404, {"code": -1, "message": "not found"})

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/api/download":
            length = int(self.headers.get("Content-Length") or 0)
            raw = self.rfile.read(length) if length else b"{}"
            try:
                body = json.loads(raw.decode("utf-8") or "{}")
            except Exception:
                body = {}
            book_id = str(body.get("book_id") or body.get("id") or "").strip()
            if not book_id.isdigit():
                self._json(400, {"code": -1, "message": "缺少有效 book_id"})
                return
            max_chapters = int(body.get("max_chapters") or 0)
            job_id = uuid.uuid4().hex[:12]
            with JOBS_LOCK:
                JOBS[job_id] = {
                    "status": "queued",
                    "progress": 0,
                    "message": "排队中",
                    "book_id": book_id,
                    "created_at": time.time(),
                }
            t = threading.Thread(
                target=run_download_job,
                args=(job_id, book_id, max_chapters),
                daemon=True,
            )
            t.start()
            self._json(200, {"code": 0, "job_id": job_id, "book_id": book_id})
            return
        self._json(404, {"code": -1, "message": "not found"})


def main() -> None:
    (ROOT / "downloads").mkdir(exist_ok=True)
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"Fanqie proxy running at http://{HOST}:{PORT}")
    server.serve_forever()


if __name__ == "__main__":
    main()
