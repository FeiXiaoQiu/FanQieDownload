(function () {
    "use strict";

    const params = new URLSearchParams(location.search || "");
    const bookId = String(params.get("book_id") || params.get("id") || "").trim();
    const titleFromQ = params.get("title") || "";
    const authorFromQ = params.get("author") || "";

    const el = {
        bookTitle: document.getElementById("bookTitle"),
        bookMeta: document.getElementById("bookMeta"),
        chapterTitle: document.getElementById("chapterTitle"),
        status: document.getElementById("status"),
        content: document.getElementById("content"),
        catalog: document.getElementById("catalog"),
        catalogBtn: document.getElementById("catalogBtn"),
        prevBtn: document.getElementById("prevBtn"),
        nextBtn: document.getElementById("nextBtn"),
        prevBtn2: document.getElementById("prevBtn2"),
        nextBtn2: document.getElementById("nextBtn2"),
    };

    let chapters = [];
    let index = 0;
    let loadSeq = 0;

    function setStatus(text, isError) {
        if (!el.status) return;
        el.status.hidden = false;
        el.status.textContent = text;
        el.status.style.color = isError ? "#c0392b" : "#555";
    }

    function escapeHtml(s) {
        return String(s || "")
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    /** 按换行分段，每段首行缩进两字 */
    function renderChapterBody(node, raw) {
        if (!node) return;
        let s = String(raw || "")
            .replace(/\r\n/g, "\n")
            .replace(/\r/g, "\n")
            .replace(/[ \t\u3000]+\n/g, "\n")
            .replace(/\n{3,}/g, "\n\n")
            .trim();
        if (!s) {
            node.innerHTML = '<p class="reader-p reader-empty">（本章无内容）</p>';
            return;
        }
        const parts = s.split(/\n+/).map(function (p) {
            return p.replace(/^\s+|\s+$/g, "");
        }).filter(Boolean);
        if (!parts.length) {
            node.innerHTML = '<p class="reader-p reader-empty">（本章无内容）</p>';
            return;
        }
        node.innerHTML = parts.map(function (p) {
            return '<p class="reader-p">' + escapeHtml(p) + "</p>";
        }).join("");
    }

    function updateNav() {
        const canPrev = index > 0;
        const canNext = index < chapters.length - 1;
        [el.prevBtn, el.prevBtn2].forEach(function (b) {
            if (b) b.disabled = !canPrev;
        });
        [el.nextBtn, el.nextBtn2].forEach(function (b) {
            if (b) b.disabled = !canNext;
        });
        if (el.catalog) {
            Array.prototype.forEach.call(el.catalog.querySelectorAll("button"), function (btn, i) {
                if (i === index) btn.classList.add("active");
                else btn.classList.remove("active");
            });
        }
    }

    function renderCatalog() {
        if (!el.catalog) return;
        el.catalog.innerHTML = "";
        chapters.forEach(function (ch, i) {
            const btn = document.createElement("button");
            btn.type = "button";
            btn.textContent = (i + 1) + ". " + (ch.title || ("第 " + (i + 1) + " 章"));
            btn.addEventListener("click", function () {
                el.catalog.classList.remove("open");
                loadChapter(i);
            });
            el.catalog.appendChild(btn);
        });
    }

    async function loadChapter(i) {
        if (!chapters.length) return;
        index = Math.max(0, Math.min(chapters.length - 1, i));
        const ch = chapters[index];
        const seq = ++loadSeq;
        updateNav();
        el.chapterTitle.textContent = ch.title || ("第 " + (index + 1) + " 章");
        el.content.hidden = true;
        setStatus("正在加载第 " + (index + 1) + " / " + chapters.length + " 章…");
        try {
            const client = window.FanqieBrowserClient;
            const got = await client.getContent(ch.item_id);
            if (seq !== loadSeq) return;
            const chapTitle = (got && got.title) || ch.title || ("第 " + (index + 1) + " 章");
            el.chapterTitle.textContent = chapTitle;
            renderChapterBody(el.content, (got && got.text) || "");
            el.content.hidden = false;
            el.status.hidden = true;
            document.title = chapTitle + " · " + (titleFromQ || bookId);
            try {
                window.scrollTo(0, 0);
            } catch (e) { /* ignore */ }
        } catch (e) {
            if (seq !== loadSeq) return;
            setStatus("本章加载失败：" + (e.message || e), true);
            el.content.hidden = true;
        }
    }

    async function init() {
        if (!bookId || !/^\d{6,}$/.test(bookId)) {
            el.bookTitle.textContent = "无效书籍";
            setStatus("缺少有效 book_id，请从搜索列表点「新开页阅读」进入。", true);
            return;
        }
        el.bookTitle.textContent = titleFromQ || ("书籍 " + bookId);
        el.bookMeta.textContent = [authorFromQ, "ID " + bookId].filter(Boolean).join(" · ");

        const client = window.FanqieBrowserClient;
        if (!client) {
            setStatus("阅读组件未加载", true);
            return;
        }
        try {
            if (typeof client.detectSameOriginProxy === "function") {
                await client.detectSameOriginProxy();
            }
            if (typeof client.probeHosts === "function") {
                try { await client.probeHosts(); } catch (e) { /* ignore */ }
            }
            if (!titleFromQ && typeof client.getInfo === "function") {
                try {
                    const info = await client.getInfo(bookId);
                    if (info && info.title) el.bookTitle.textContent = info.title;
                    if (info && info.author) {
                        el.bookMeta.textContent = [info.author, "ID " + bookId].filter(Boolean).join(" · ");
                    }
                } catch (e) { /* ignore */ }
            }
            setStatus("正在获取目录…");
            chapters = await client.getCatalog(bookId);
            chapters = (chapters || []).filter(function (c) { return c && c.item_id; });
            if (!chapters.length) {
                setStatus("目录为空，无法阅读。", true);
                return;
            }
            el.bookMeta.textContent =
                [el.bookMeta.textContent, "共 " + chapters.length + " 章"].filter(Boolean).join(" · ");
            renderCatalog();
            await loadChapter(0);
        } catch (e) {
            setStatus("打开阅读失败：" + (e.message || e), true);
        }
    }

    if (el.catalogBtn) {
        el.catalogBtn.addEventListener("click", function () {
            if (el.catalog) el.catalog.classList.toggle("open");
        });
    }
    function bindPrev(btn) {
        if (btn) btn.addEventListener("click", function () { loadChapter(index - 1); });
    }
    function bindNext(btn) {
        if (btn) btn.addEventListener("click", function () { loadChapter(index + 1); });
    }
    bindPrev(el.prevBtn);
    bindPrev(el.prevBtn2);
    bindNext(el.nextBtn);
    bindNext(el.nextBtn2);

    init();
})();
