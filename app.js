// ========== 一言核心功能 ==========
        // 内置兜底（主源：/Speech/Speech.txt，失败时用这批）
        const DAILY_MESSAGES = [
            "无论你去哪里，你总是在那里。——村上春树",
            "你要做一个不动声色的大人了。——村上春树",
            "世界上只有一种英雄主义，就是在认清生活真相之后依然热爱生活。——罗曼·罗兰",
            "当你凝视深渊时，深渊也在凝视你。——尼采",
            "人生如逆旅，我亦是行人。——苏轼",
            "凡是过往，皆为序章。——莎士比亚",
            "我们都在阴沟里，但仍有人仰望星空。——王尔德",
            "黑夜给了我黑色的眼睛，我却用它寻找光明。——顾城",
            "一个人可以被毁灭，但不能被打败。——海明威",
            "路漫漫其修远兮，吾将上下而求索。——屈原",
            "星星会说谎，但我会陪着你。——原神·温迪",
            "风带来故事的种子，时间使之发芽。——原神·温迪",
            "契约既定，不可违逆。——原神·钟离",
            "尘世皆苦，你我皆凡。——原神·钟离",
            "花开有时，花落有期。——原神·纳西妲",
            "愿此行终抵群星。——崩坏：星穹铁道",
            "开拓，是向未知伸出的手。——崩坏：星穹铁道",
            "世间美好，值得见证。——崩坏：星穹铁道·三月七",
            "即使命运已被书写，也要亲手翻开下一页。——崩坏：星穹铁道",
            "流萤说，哪怕只是余烬，也想照亮谁。——崩坏：星穹铁道",
            "即使身负原罪，也要向着光走去。——崩坏3",
            "在崩坏的时代，英雄也会流泪。——崩坏3",
            "即使世界残酷，也要温柔地活着。——尼尔：自动人形",
            "死亡不是终点，放弃才是。——黑暗之魂",
            "心有猛虎，细嗅蔷薇。——萨松",
            "万物皆有裂痕，那是光照进来的地方。——莱昂纳德·科恩",
            "真正重要的东西，眼睛是看不见的。——小王子",
            "希望是附丽于存在的，有存在，便有希望。——鲁迅",
            "上善若水，水善利万物而不争。——老子",
            "现在，就是最好的开始。"
        ];
        
        // 存储活动的弹窗
        let activePopups = [];
        // 最大弹窗数量限制
        const MAX_POPUPS = 8;
        // 弹窗计数器（用于生成唯一ID）
        let popupCounter = 0;
        // 防止重复初始化的标志
        let initialPopupShown = false;
        
        // 清理过时弹窗（确保不会残留）
        function cleanupStalePopups() {
            const currentTime = Date.now();
            const maxAge = 10000; // 10秒最大寿命
            
            for (let i = activePopups.length - 1; i >= 0; i--) {
                const popupInfo = activePopups[i];
                const popupElement = document.getElementById(popupInfo.id);
                
                // 如果弹窗元素不存在，清理记录
                if (!popupElement) {
                    // 清除定时器
                    if (popupInfo.timer) clearTimeout(popupInfo.timer);
                    if (popupInfo.interval) clearInterval(popupInfo.interval);
                    activePopups.splice(i, 1);
                    continue;
                }
                
                // 如果弹窗存在但已经超时，强制关闭
                if (currentTime - popupInfo.createdTime > maxAge) {
                    forceClosePopup(popupInfo.id, i);
                }
            }
        }
        
        // 强制关闭弹窗（用于清理残留弹窗）
        function forceClosePopup(popupId, index = -1) {
            const popupElement = document.getElementById(popupId);
            if (!popupElement) return;
            
            // 清除可能的定时器
            let popupIndex = index;
            if (popupIndex === -1) {
                popupIndex = activePopups.findIndex(p => p.id === popupId);
            }
            
            if (popupIndex !== -1) {
                const popupInfo = activePopups[popupIndex];
                if (popupInfo) {
                    if (popupInfo.timer) clearTimeout(popupInfo.timer);
                    if (popupInfo.interval) clearInterval(popupInfo.interval);
                }
            }
            
            // 直接移除元素
            if (popupElement.parentNode) {
                popupElement.parentNode.removeChild(popupElement);
            }
            
            // 从数组中移除记录
            if (popupIndex !== -1) {
                activePopups.splice(popupIndex, 1);
            }
        }
        
        // 关闭最早的一个弹窗
        function closeOldestPopup() {
            if (activePopups.length === 0) return;
            
            // 找到最旧的弹窗
            const oldestPopup = activePopups.reduce((oldest, current) => {
                return current.createdTime < oldest.createdTime ? current : oldest;
            });
            
            if (oldestPopup) {
                closeDailyPopup(oldestPopup.id);
            }
        }
        
        // 加载一言
        async function loadDailyMessage() {
            try {
                // 尝试从文件加载
                const response = await fetch('/Speech/Speech.txt');
                if (!response.ok) throw new Error('文件加载失败');
                
                const text = await response.text();
                const lines = text.split('\n')
                    .map(line => line.trim())
                    .filter(line => line.length > 0);
                
                if (lines.length > 0) {
                    // 返回文件中的随机消息
                    return lines[Math.floor(Math.random() * lines.length)];
                }
            } catch (error) {
                // 文件加载失败，使用默认消息

            }
            
            // 返回默认消息
            return DAILY_MESSAGES[Math.floor(Math.random() * DAILY_MESSAGES.length)];
        }
        
        // 创建一言弹窗
        async function createDailyPopup(message = null) {
            // 清理可能残留的弹窗
            cleanupStalePopups();
            
            // 限制最大弹窗数量
            if (activePopups.length >= MAX_POPUPS) {
                // 自动关闭最早的一个弹窗
                closeOldestPopup();
            }
            
            // 获取消息
            const popupMessage = message || await loadDailyMessage();
            
            // 创建弹窗ID（使用计数器确保唯一性）
            popupCounter++;
            const popupId = 'daily-popup-' + popupCounter + '-' + Date.now();
            
            // 创建弹窗元素
            const popup = document.createElement('div');
            popup.className = 'daily-popup';
            popup.id = popupId;
            popup.dataset.popupId = popupId;
            popup.innerHTML = `
                <div class="popup-header">
                    <div class="popup-title">
                        <i>💬</i>
                        一言
                    </div>
                    <button class="popup-close" data-popup-id="${popupId}">×</button>
                </div>
                <div class="popup-content">
                    ${popupMessage}
                </div>
                <div class="popup-timer-container">
                    <div class="popup-timer">
                        <div class="timer-bar" id="timer-bar-${popupId}" style="width: 100%"></div>
                    </div>
                    <div class="timer-text">
                        <i>⏱️</i>
                        <span class="time-number" id="time-number-${popupId}">5</span>秒后关闭
                    </div>
                </div>
            `;
            
            // 添加到容器
            const container = document.getElementById('popupContainer');
            if (container) {
                container.appendChild(popup);
                
                // 滚动到最新消息
                setTimeout(() => {
                    container.scrollTop = container.scrollHeight;
                }, 100);
            }
            
            // 存储弹窗信息
            const popupInfo = {
                id: popupId,
                element: popup,
                timer: null,
                interval: null,
                startTime: Date.now(),
                createdTime: Date.now(),
                isClosing: false
            };
            
            activePopups.push(popupInfo);
            
            // 开始倒计时
            startSmoothCountdown(popupInfo);
            
            return popupId;
        }
        
        // 开始平滑倒计时
        function startSmoothCountdown(popupInfo) {
            const timerBar = document.getElementById(`timer-bar-${popupInfo.id}`);
            const timeNumber = document.getElementById(`time-number-${popupInfo.id}`);
            
            if (!timerBar || !timeNumber) {
                // 如果元素不存在，清理这个弹窗
                const index = activePopups.findIndex(p => p.id === popupInfo.id);
                if (index !== -1) {
                    activePopups.splice(index, 1);
                }
                return;
            }
            
            // 清除之前的定时器
            if (popupInfo.interval) {
                clearInterval(popupInfo.interval);
            }
            if (popupInfo.timer) {
                clearTimeout(popupInfo.timer);
            }
            
            const duration = 5000; // 5秒 = 5000毫秒
            const startTime = Date.now();
            
            // 立即更新一次显示
            updateTimerDisplay(popupInfo.id, duration, duration);
            
            // 每100毫秒更新一次（比每秒更新更平滑）
            popupInfo.interval = setInterval(() => {
                // 检查弹窗是否还在数组和DOM中
                const popupIndex = activePopups.findIndex(p => p.id === popupInfo.id);
                const popupElement = document.getElementById(popupInfo.id);
                
                if (popupIndex === -1 || !popupElement || popupInfo.isClosing) {
                    clearInterval(popupInfo.interval);
                    return;
                }
                
                const elapsed = Date.now() - startTime;
                const remaining = Math.max(0, duration - elapsed);
                
                // 更新显示
                updateTimerDisplay(popupInfo.id, remaining, duration);
                
                // 倒计时结束
                if (remaining <= 0) {
                    clearInterval(popupInfo.interval);
                    // 确保进度条完全归零
                    if (timerBar) {
                        timerBar.style.width = '0%';
                    }
                    // 关闭弹窗
                    closeDailyPopup(popupInfo.id);
                }
            }, 100);
            
            // 设置5秒后自动关闭的备用定时器
            popupInfo.timer = setTimeout(() => {
                if (timerBar) {
                    timerBar.style.width = '0%';
                }
                closeDailyPopup(popupInfo.id);
            }, duration);
        }
        
        // 更新倒计时显示
        function updateTimerDisplay(popupId, remainingMs, totalMs) {
            const timerBar = document.getElementById(`timer-bar-${popupId}`);
            const timeNumber = document.getElementById(`time-number-${popupId}`);
            
            if (!timerBar || !timeNumber) return;
            
            // 确保剩余时间不会变成负数
            const safeRemainingMs = Math.max(0, remainingMs);
            
            // 计算百分比和剩余秒数
            const percentage = (safeRemainingMs / totalMs) * 100;
            const remainingSeconds = Math.ceil(safeRemainingMs / 1000);
            
            // 更新进度条（确保不会超过100%或低于0%）
            timerBar.style.width = Math.max(0, Math.min(100, percentage)) + '%';
            
            // 更新数字显示（确保不会显示负数）
            timeNumber.textContent = Math.max(0, remainingSeconds);
        }
        
        // 关闭一言弹窗
        function closeDailyPopup(popupId) {
            const popupIndex = activePopups.findIndex(p => p.id === popupId);
            if (popupIndex === -1) return;
            
            const popupInfo = activePopups[popupIndex];
            
            // 防止重复关闭
            if (popupInfo.isClosing) return;
            popupInfo.isClosing = true;
            
            const popupElement = document.getElementById(popupId);
            
            // 清除定时器
            if (popupInfo.timer) {
                clearTimeout(popupInfo.timer);
                popupInfo.timer = null;
            }
            if (popupInfo.interval) {
                clearInterval(popupInfo.interval);
                popupInfo.interval = null;
            }
            
            // 确保进度条完全归零
            const timerBar = document.getElementById(`timer-bar-${popupId}`);
            if (timerBar) {
                timerBar.style.width = '0%';
            }
            
            if (!popupElement) {
                // 如果元素不存在，直接从数组中移除
                activePopups.splice(popupIndex, 1);
                return;
            }
            
            // 添加关闭动画
            popupElement.classList.add('closing');
            
            // 动画完成后移除元素
            setTimeout(() => {
                if (popupElement.parentNode) {
                    try {
                        popupElement.parentNode.removeChild(popupElement);
                    } catch (e) {

                    }
                }
                
                // 从数组中移除
                const finalIndex = activePopups.findIndex(p => p.id === popupId);
                if (finalIndex !== -1) {
                    activePopups.splice(finalIndex, 1);
                }
            }, 400);
        }
        
        // 增强关闭按钮的事件处理
        document.addEventListener('click', function(e) {
            const closeButton = e.target.closest('.popup-close');
            if (closeButton) {
                const popupId = closeButton.dataset.popupId;
                if (popupId) {
                    closeDailyPopup(popupId);
                    e.stopPropagation();
                }
            }
        });
        
        // 页面卸载时清理所有定时器
        window.addEventListener('beforeunload', function() {
            activePopups.forEach(popupInfo => {
                if (popupInfo.timer) clearTimeout(popupInfo.timer);
                if (popupInfo.interval) clearInterval(popupInfo.interval);
            });
            activePopups = [];
        });
        
        // 显示初始一言（安全版，防止重复显示）
        async function showInitialDailyMessage() {
            if (initialPopupShown) return;
            initialPopupShown = true;
            
            const initialMessage = await loadDailyMessage();
            createDailyPopup(initialMessage);
        }
        
        // ========== 自动检测和下载功能 ==========
        
        // 防抖函数 - 防止频繁触发
        function debounce(func, wait) {
            let timeout;
            return function executedFunction(...args) {
                const later = () => {
                    clearTimeout(timeout);
                    func(...args);
                };
                clearTimeout(timeout);
                timeout = setTimeout(later, wait);
            };
        }
        
        // 同源优先；Pages 无 API 时回退到 Vercel 远程（也可 localStorage 自定义）
        const DEFAULT_REMOTE_APIS = [
            'https://fan-qie-download.vercel.app',
        ];
        let API_BASE = (function() {
            if (location.protocol === 'http:' || location.protocol === 'https:') {
                return '';
            }
            return 'http://127.0.0.1:8787';
        })();
        let REMOTE_API_BASE = '';

        let USE_BACKEND = null; // null=探测中, true=完整 Node 任务, false=静态
        let API_PROXY_READY = null; // 同源或远程 /api/proxy
        let staticJobActive = false;

        function apiUrl(path) {
            if (API_BASE) return API_BASE + path;
            if (REMOTE_API_BASE) return REMOTE_API_BASE + path;
            return path;
        }

        function readRemoteOverride() {
            try {
                if (window.FQ_API_BASE) return String(window.FQ_API_BASE).replace(/\/$/, '');
                const v = localStorage.getItem('fq_api_base');
                if (v) return String(v).replace(/\/$/, '');
            } catch (e) { /* ignore */ }
            return '';
        }

        async function probeApiBase(base, timeoutMs) {
            const ctrl = new AbortController();
            const timer = setTimeout(function () { ctrl.abort(); }, timeoutMs || 4000);
            try {
                const resp = await fetch(base + '/api/health', {
                    method: 'GET',
                    cache: 'no-store',
                    signal: ctrl.signal,
                    mode: 'cors',
                });
                clearTimeout(timer);
                if (!resp.ok) return null;
                return await resp.json();
            } catch (e) {
                clearTimeout(timer);
                return null;
            }
        }

        async function detectBackend() {
            if (USE_BACKEND !== null) return USE_BACKEND;
            // 1) 同源
            try {
                const data = await probeApiBase(location.origin, 2500);
                if (data && data.ok) {
                    const fullNode = data.runtime === 'node';
                    const vercelLike = (
                        data.runtime === 'vercel-serverless' ||
                        data.service === 'fanqie-proxy-vercel' ||
                        data.mode === 'proxy'
                    );
                    USE_BACKEND = fullNode;
                    if (vercelLike || fullNode) {
                        API_BASE = '';
                        REMOTE_API_BASE = '';
                        API_PROXY_READY = true;
                        bindProxyBase(location.origin + '/api/proxy');
                    }
                    return USE_BACKEND;
                }
            } catch (e) { /* ignore */ }

            USE_BACKEND = false;

            // 2) 用户指定 / 默认远程 Vercel
            const candidates = [];
            const override = readRemoteOverride();
            if (override) candidates.push(override);
            for (let i = 0; i < DEFAULT_REMOTE_APIS.length; i++) {
                if (candidates.indexOf(DEFAULT_REMOTE_APIS[i]) === -1) {
                    candidates.push(DEFAULT_REMOTE_APIS[i]);
                }
            }
            for (let i = 0; i < candidates.length; i++) {
                const base = candidates[i];
                if (!base) continue;
                // 与当前页同源则跳过（已测过）
                try {
                    if (new URL(base).host === location.host) continue;
                } catch (e) { continue; }
                const data = await probeApiBase(base, 5000);
                if (data && data.ok) {
                    REMOTE_API_BASE = base;
                    API_PROXY_READY = true;
                    bindProxyBase(base + '/api/proxy');
                    try { localStorage.setItem('fq_api_base', base); } catch (e) {}
                    return USE_BACKEND;
                }
            }

            // 3) 再探测同源 proxy 路径
            if (API_PROXY_READY === null) {
                try {
                    await ensureSameOriginProxy();
                } catch (e) {
                    API_PROXY_READY = false;
                }
            }
            return USE_BACKEND;
        }

        function bindProxyBase(proxyUrl) {
            try {
                if (window.FanqieBrowserClient && window.FanqieBrowserClient.setCorsProxy) {
                    const cur = window.FanqieBrowserClient.getCorsProxy() || '';
                    // 已有且不是公共代理残留时保留用户自定义
                    if (cur && cur.indexOf('/api/proxy') === -1 && cur.indexOf('vercel.app') === -1) {
                        return;
                    }
                    window.FanqieBrowserClient.setCorsProxy(proxyUrl);
                } else {
                    localStorage.setItem('fq_cors_proxy', proxyUrl);
                }
            } catch (e) { /* ignore */ }
        }

        function autoBindSameOriginProxy() {
            if (typeof location === 'undefined') return;
            if (location.protocol !== 'http:' && location.protocol !== 'https:') return;
            bindProxyBase(location.origin + '/api/proxy');
        }

        async function ensureSameOriginProxy() {
            if (API_PROXY_READY) return true;
            try {
                const ctrl = new AbortController();
                const t = setTimeout(function () { ctrl.abort(); }, 3000);
                const resp = await fetch((REMOTE_API_BASE || location.origin) + '/api/proxy', {
                    method: 'GET',
                    cache: 'no-store',
                    signal: ctrl.signal,
                    mode: 'cors',
                });
                clearTimeout(t);
                if (!resp.ok) throw new Error('no proxy');
                const data = await resp.json();
                if (data && (data.ok || data.usage)) {
                    API_PROXY_READY = true;
                    if (REMOTE_API_BASE) bindProxyBase(REMOTE_API_BASE + '/api/proxy');
                    else autoBindSameOriginProxy();
                    return true;
                }
            } catch (e) {
                if (!REMOTE_API_BASE) API_PROXY_READY = false;
            }
            return !!API_PROXY_READY;
        }

        // 自动检测输入并触发下载
        function autoDetectAndDownload() {
            const userInput = document.getElementById('bookId').value.trim();
            if (!userInput) return;
            
            const bookId = extractBookId(userInput);
            
            if (!bookId) {
                // 非 ID：尝试书名搜索
                if (/[\u4e00-\u9fa5a-zA-Z]/.test(userInput) && userInput.length >= 1) {
                    searchByName(userInput);
                }
                return;
            }
            
            // 如果是链接（输入内容不等于提取的ID），则自动下载
            if (userInput !== bookId) {
                showResult(`检测到分享链接，已提取小说ID：${bookId}，开始自动下载...`, 'info');
                setTimeout(() => {
                    executeDownload(bookId, userInput);
                }, 800);
            }
        }
        
        // 提取小说ID函数
        function extractBookId(input) {
            if (!input || input.trim() === '') return null;
            
            let processedInput = input.trim();
            // 仅当包含链接或纯数字时才去中文
            if (/https?:\/\//i.test(processedInput) || /book_id=/.test(processedInput)) {
                processedInput = processedInput.replace(/[\u4e00-\u9fa5]/g, '');
            }
            
            const urlPattern = /book_id=(\d+)/;
            const urlMatch = processedInput.match(urlPattern);
            if (urlMatch && urlMatch[1]) {
                return urlMatch[1];
            }

            const pagePattern = /(?:fanqienovel\.com\/page\/|\/page\/)(\d{10,})/;
            const pageMatch = input.match(pagePattern);
            if (pageMatch && pageMatch[1]) {
                return pageMatch[1];
            }
            
            const pureNumberPattern = /^(\d{10,20})$/;
            const pureNumberMatch = input.trim().match(pureNumberPattern);
            if (pureNumberMatch) {
                return pureNumberMatch[1];
            }

            // 链接混杂文本时再尝试长数字
            if (/https?:\/\//i.test(input) || /book_id=/.test(input)) {
                const longIdPattern = /\d{15,20}/;
                const longIdMatch = processedInput.match(longIdPattern);
                if (longIdMatch) {
                    return longIdMatch[0];
                }
            }
            
            const commonPatterns = [
                /id=(\d{10,})/,
                /novel\/(\d{10,})/,
                /book\/(\d{10,})/,
                /read\/(\d{10,})/
            ];
            
            for (const pattern of commonPatterns) {
                const match = input.match(pattern);
                if (match && match[1]) {
                    return match[1];
                }
            }
            
            return null;
        }

        function escapeHtml(str) {
            return String(str || '')
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;');
        }

        /** 封面 CDN 常防盗链：bytecdn→byteimg，并优先经 /api/proxy */
        function resolveCoverUrl(raw) {
            let u = String(raw || '').trim();
            if (!u) return '';
            if (u.indexOf('bytecdn.cn') !== -1 && u.indexOf('novel-pic/') !== -1) {
                const m = /novel-pic\/([^~?/]+)/.exec(u);
                if (m) {
                    // 列表缩略图用小图，加快加载
                    u = 'https://p3-novel.byteimg.com/img/novel-pic/' + m[1] + '~tplv-tt-cs0:120:160.image';
                }
            } else if (u.indexOf('byteimg.com') !== -1 && u.indexOf('~tplv-') !== -1) {
                u = u.replace(/~tplv-[^.]+\.image/, '~tplv-tt-cs0:120:160.image');
            }
            // 已是代理 URL 则不再包一层
            if (u.indexOf('/api/proxy?') !== -1) return u;
            const base = REMOTE_API_BASE || (
                (location.protocol === 'http:' || location.protocol === 'https:') ? location.origin : ''
            );
            // 有页面源且探测到代理、或默认远程 Vercel：一律走代理
            if (base) {
                const proxyBase = REMOTE_API_BASE || base;
                if (API_PROXY_READY || REMOTE_API_BASE || /vercel\.app$/.test(location.host) || API_PROXY_READY !== false) {
                    return proxyBase + '/api/proxy?url=' + encodeURIComponent(u);
                }
            }
            // Pages 未探测到时，硬编码回退到 Vercel 代理拉封面
            return 'https://fan-qie-download.vercel.app/api/proxy?url=' + encodeURIComponent(u);
        }

        let lastSearchBooks = [];
        let lastSearchQuery = '';
        let lastSearchNextOffset = 0;
        let lastSearchHasMore = false;
        let searchLoadingMore = false;
        let batchDownloadQueue = [];
        let batchDownloadRunning = false;
        let previewBook = null;

        async function fetchSearchPage(query, offset) {
            const backend = await detectBackend();
            await ensureSameOriginProxy();
            try {
                const ctrl = new AbortController();
                const t = setTimeout(function () { ctrl.abort(); }, 20000);
                const url = apiUrl(
                    '/api/search?q=' + encodeURIComponent(query) +
                    '&offset=' + encodeURIComponent(String(offset || 0))
                );
                const resp = await fetch(url, { signal: ctrl.signal, cache: 'no-store' });
                clearTimeout(t);
                if (resp.ok) {
                    const data = await resp.json();
                    if (data && data.code === 0) {
                        return {
                            books: data.books || [],
                            next_offset: data.next_offset != null
                                ? data.next_offset
                                : (offset || 0) + (data.books || []).length,
                            has_more: Boolean(data.has_more),
                            source: data.source || 'api',
                        };
                    }
                    if (backend) {
                        throw new Error((data && data.message) || '搜索失败');
                    }
                }
            } catch (e) {
                if (backend) throw e;
            }
            if (offset > 0) {
                return { books: [], next_offset: offset, has_more: false, source: 'none' };
            }
            if (!window.FanqieBrowserClient) throw new Error('浏览器客户端未加载');
            const data = await window.FanqieBrowserClient.search(query);
            if (!data || data.code !== 0) {
                throw new Error((data && data.message) || '搜索失败');
            }
            const books = data.books || [];
            return {
                books: books,
                next_offset: books.length,
                has_more: books.length >= 7,
                source: data.source || 'static',
            };
        }

        async function searchByName(forceQuery) {
            const input = (forceQuery || document.getElementById('bookId').value || '').trim();
            const box = document.getElementById('searchResults');
            if (!input) {
                showResult('请输入书名后再搜索', 'warning');
                return;
            }
            const asId = extractBookId(input);
            if (asId && /^\d{10,}$/.test(input.trim())) {
                executeDownload(asId, input);
                return;
            }
            if (box) {
                box.style.display = 'flex';
                box.innerHTML = '<div class="search-empty">搜索中...</div>';
            }
            try {
                const page = await fetchSearchPage(input, 0);
                const books = page.books || [];
                if (!books.length) {
                    lastSearchBooks = [];
                    lastSearchQuery = input;
                    lastSearchNextOffset = 0;
                    lastSearchHasMore = false;
                    box.innerHTML = '<div class="search-empty">未找到相关书籍</div>';
                    showResult('未找到相关书籍', 'warning');
                    return;
                }
                lastSearchBooks = books.slice();
                lastSearchQuery = input;
                lastSearchNextOffset = page.next_offset || books.length;
                lastSearchHasMore = Boolean(page.has_more);
                renderSearchList(box, lastSearchBooks);
                showResult(
                    '已加载 ' + books.length + ' 本' +
                    (lastSearchHasMore ? '，可继续加载更多' : '') +
                    ' · 点卡片预览，勾选后可批量下载',
                    'info'
                );
            } catch (e) {
                lastSearchBooks = [];
                lastSearchHasMore = false;
                box.innerHTML = '<div class="search-empty">搜索失败：' + escapeHtml(e.message || String(e)) + '</div>';
                showResult('搜索失败：' + e.message, 'error');
            }
        }

        async function loadMoreSearch() {
            if (searchLoadingMore || !lastSearchHasMore || !lastSearchQuery) return;
            const box = document.getElementById('searchResults');
            if (!box) return;
            const listEl = box.querySelector('.search-list');
            const moreBtn = box.querySelector('#searchMoreBtn');
            const scrollTop = listEl ? listEl.scrollTop : 0;
            searchLoadingMore = true;
            if (moreBtn) {
                moreBtn.disabled = true;
                moreBtn.textContent = '加载中…';
            }
            try {
                const page = await fetchSearchPage(lastSearchQuery, lastSearchNextOffset);
                const incoming = page.books || [];
                const seen = new Set(lastSearchBooks.map(function (b) { return String(b.book_id); }));
                const appended = [];
                for (let i = 0; i < incoming.length; i++) {
                    const b = incoming[i];
                    const id = String(b.book_id || '');
                    if (!id || seen.has(id)) continue;
                    seen.add(id);
                    lastSearchBooks.push(b);
                    appended.push(b);
                }
                lastSearchNextOffset = page.next_offset != null
                    ? page.next_offset
                    : lastSearchNextOffset + incoming.length;
                lastSearchHasMore = Boolean(page.has_more) && incoming.length > 0;

                // 只追加新条目，避免整表重绘把滚动条弹回顶部
                if (listEl && appended.length) {
                    const frag = document.createDocumentFragment();
                    const tmp = document.createElement('div');
                    tmp.innerHTML = appended.map(function (b) {
                        return buildSearchItemHtml(b, false);
                    }).join('');
                    while (tmp.firstChild) {
                        const node = tmp.firstChild;
                        frag.appendChild(node);
                        if (node.nodeType === 1) bindSearchItemEl(node);
                    }
                    listEl.appendChild(frag);
                }

                updateSearchMoreFooter(box);
                updateSearchSelectionUi();
                if (listEl) listEl.scrollTop = scrollTop;

                if (!appended.length && !lastSearchHasMore) {
                    showResult('没有更多结果了', 'info');
                } else {
                    showResult(
                        '已累计 ' + lastSearchBooks.length + ' 本' +
                        (lastSearchHasMore ? '，还可继续加载' : ''),
                        'info'
                    );
                }
            } catch (e) {
                showResult('加载更多失败：' + (e.message || e), 'error');
                if (moreBtn) {
                    moreBtn.disabled = false;
                    moreBtn.textContent = '加载更多';
                }
            } finally {
                searchLoadingMore = false;
            }
        }

        function findSearchBook(bookId) {
            const id = String(bookId || '');
            for (let i = 0; i < lastSearchBooks.length; i++) {
                if (String(lastSearchBooks[i].book_id) === id) return lastSearchBooks[i];
            }
            return null;
        }

        function getSelectedSearchBooks() {
            const box = document.getElementById('searchResults');
            if (!box) return [];
            const out = [];
            Array.prototype.forEach.call(box.querySelectorAll('.search-item-check:checked'), function (cb) {
                const item = cb.closest('.search-item');
                if (!item) return;
                const id = item.getAttribute('data-id');
                const title = item.getAttribute('data-title') || id;
                const b = findSearchBook(id) || { book_id: id, title: title };
                out.push(b);
            });
            return out;
        }

        function updateSearchSelectionUi() {
            const box = document.getElementById('searchResults');
            if (!box) return;
            const checks = box.querySelectorAll('.search-item-check');
            let n = 0;
            Array.prototype.forEach.call(checks, function (cb) {
                const item = cb.closest('.search-item');
                if (cb.checked) {
                    n += 1;
                    if (item) item.classList.add('is-selected');
                } else if (item) {
                    item.classList.remove('is-selected');
                }
            });
            const countEl = box.querySelector('#searchSelectCount');
            if (countEl) {
                countEl.textContent = '已选 ' + n + ' 本 / 共 ' + (checks.length || lastSearchBooks.length) + ' 本';
            }
            const all = box.querySelector('#searchCheckAll');
            if (all) {
                all.checked = checks.length > 0 && n === checks.length;
                all.indeterminate = n > 0 && n < checks.length;
            }
            const batchBtn = box.querySelector('#batchDownloadBtn');
            if (batchBtn) {
                batchBtn.disabled = n === 0 || batchDownloadRunning;
                batchBtn.innerHTML = n > 1
                    ? '<span>批量下载 (' + n + ')</span>'
                    : '<span>下载所选</span>';
            }
        }

        function buildSearchItemHtml(b, checked) {
            const title = b.title || '未知';
            const meta = [b.author || '', b.category || ''].filter(Boolean).join(' · ');
            const desc = formatAbstract(b.abstract, 120);
            const coverSrc = resolveCoverUrl(b.thumb_url || b.thumb_uri || '');
            const isChecked = !!checked;
            const img = coverSrc
                ? '<img class="search-cover" src="' + escapeHtml(coverSrc) + '" alt="" loading="lazy" decoding="async" referrerpolicy="no-referrer" onerror="this.onerror=null;this.src=\'\';this.classList.add(\'search-cover-fail\');this.alt=\'无封面\'">'
                : '<div class="search-cover search-cover-ph" aria-hidden="true"></div>';
            return (
                '<div class="search-item' + (isChecked ? ' is-selected' : '') + '" data-id="' + escapeHtml(b.book_id) + '" data-title="' + escapeHtml(title) + '">' +
                '<input type="checkbox" class="search-item-check" aria-label="选择《' + escapeHtml(title) + '》"' + (isChecked ? ' checked' : '') + '>' +
                '<button type="button" class="search-item-main" data-action="preview">' +
                img +
                '<div class="search-item-body">' +
                '<div class="search-item-title">' + escapeHtml(title) + '</div>' +
                (meta ? '<div class="search-item-meta">' + escapeHtml(meta) + '</div>' : '') +
                (desc ? '<div class="search-item-desc">' + escapeHtml(desc.slice(0, 120)) + (desc.length > 120 ? '…' : '') + '</div>' : '') +
                '</div></button>' +
                '<div class="search-item-actions">' +
                '<button type="button" class="search-item-dl" data-action="download">下载</button>' +
                '</div></div>'
            );
        }

        function bindSearchItemEl(el) {
            if (!el || el.nodeType !== 1) return;
            const id = el.getAttribute('data-id');
            const title = el.getAttribute('data-title') || id;
            const book = findSearchBook(id) || { book_id: id, title: title };
            const cb = el.querySelector('.search-item-check');
            if (cb) {
                cb.addEventListener('click', function (e) { e.stopPropagation(); });
                cb.addEventListener('change', updateSearchSelectionUi);
            }
            const main = el.querySelector('[data-action="preview"]');
            if (main) {
                main.addEventListener('click', function () {
                    openBookPreview(book);
                });
            }
            const dl = el.querySelector('[data-action="download"]');
            if (dl) {
                dl.addEventListener('click', function (e) {
                    e.stopPropagation();
                    document.getElementById('bookId').value = id;
                    closeBookPreview();
                    showResult('开始下载《' + title + '》…', 'info');
                    executeDownload(id, title);
                });
            }
        }

        function updateSearchMoreFooter(box) {
            if (!box) return;
            let wrap = box.querySelector('.search-more-wrap');
            if (!wrap) {
                wrap = document.createElement('div');
                wrap.className = 'search-more-wrap';
                box.appendChild(wrap);
            }
            if (lastSearchHasMore) {
                let moreBtn = wrap.querySelector('#searchMoreBtn');
                // 尽量复用已有按钮，避免 DOM 替换导致失焦/页面跳顶
                if (!moreBtn) {
                    wrap.innerHTML = '<button type="button" class="search-more-btn" id="searchMoreBtn">加载更多</button>';
                    moreBtn = wrap.querySelector('#searchMoreBtn');
                    if (moreBtn) moreBtn.addEventListener('click', loadMoreSearch);
                } else {
                    moreBtn.disabled = false;
                    moreBtn.textContent = '加载更多';
                }
            } else if (lastSearchBooks.length) {
                wrap.innerHTML = '<button type="button" class="search-more-btn" disabled>已全部加载（' +
                    lastSearchBooks.length + ' 本）</button>';
            } else {
                wrap.innerHTML = '';
            }
        }

        function renderSearchList(box, books, keepSelectedIds) {
            if (!box) return;
            box.style.display = 'flex';
            const selectedSet = new Set((keepSelectedIds || []).map(String));
            const rows = books.map(function (b) {
                return buildSearchItemHtml(b, selectedSet.has(String(b.book_id)));
            }).join('');

            box.innerHTML =
                '<div class="search-toolbar">' +
                '<label class="search-check-all"><input type="checkbox" id="searchCheckAll"> 全选</label>' +
                '<span class="search-toolbar-count" id="searchSelectCount">已选 0 本 / 共 ' + books.length + ' 本</span>' +
                '<button type="button" class="btn btn-secondary" id="batchDownloadBtn" disabled><span>下载所选</span></button>' +
                '</div>' +
                '<div class="search-list">' + rows + '</div>' +
                '<div class="search-more-wrap"></div>';

            updateSearchMoreFooter(box);

            const all = box.querySelector('#searchCheckAll');
            if (all) {
                all.addEventListener('change', function () {
                    Array.prototype.forEach.call(box.querySelectorAll('.search-item-check'), function (cb) {
                        cb.checked = all.checked;
                    });
                    updateSearchSelectionUi();
                });
            }
            Array.prototype.forEach.call(box.querySelectorAll('.search-item'), bindSearchItemEl);
            const batchBtn = box.querySelector('#batchDownloadBtn');
            if (batchBtn) {
                batchBtn.addEventListener('click', function () {
                    startBatchDownload(getSelectedSearchBooks());
                });
            }
            updateSearchSelectionUi();
        }

        function formatAbstract(raw, maxLen) {
            let s = String(raw || '')
                .replace(/\r\n/g, '\n')
                .replace(/\r/g, '\n')
                .replace(/[ \t]+\n/g, '\n')
                .replace(/\n{3,}/g, '\n\n')
                .trim();
            if (!s) return '';
            if (maxLen && s.length > maxLen) {
                s = s.slice(0, maxLen).replace(/\s+$/, '') + '…';
            }
            return s;
        }

        let previewContentSeq = 0;

        function openBookPreview(book) {
            previewBook = book || null;
            const mask = document.getElementById('bookPreviewMask');
            if (!mask || !book) return;
            const title = book.title || '未知';
            const meta = [book.author || '', book.category || '', book.score ? ('评分 ' + book.score) : '']
                .filter(Boolean).join(' · ');
            const desc = formatAbstract(book.abstract) || '暂无简介';
            const coverSrc = resolveCoverUrl(book.thumb_url || book.thumb_uri || '');
            document.getElementById('previewTitle').textContent = title;
            document.getElementById('previewMeta').textContent = meta || '—';
            document.getElementById('previewId').textContent = 'ID：' + (book.book_id || '—');
            document.getElementById('previewDesc').textContent = desc;
            const bodyEl = document.getElementById('previewBody');
            if (bodyEl) {
                bodyEl.textContent = '正在加载正文试读…';
            }
            const cover = document.getElementById('previewCover');
            const ph = document.getElementById('previewCoverPh');
            if (cover && ph) {
                if (coverSrc) {
                    cover.hidden = false;
                    ph.hidden = true;
                    cover.alt = title;
                    cover.onerror = function () {
                        cover.hidden = true;
                        ph.hidden = false;
                    };
                    cover.src = coverSrc;
                } else {
                    cover.hidden = true;
                    cover.removeAttribute('src');
                    ph.hidden = false;
                }
            }
            mask.hidden = false;
            document.body.style.overflow = 'hidden';
            const previewEl = mask.querySelector('.book-preview');
            if (previewEl) previewEl.scrollTop = 0;
            loadPreviewChapterText(book);
        }

        async function loadPreviewChapterText(book) {
            const bodyEl = document.getElementById('previewBody');
            if (!bodyEl || !book || !book.book_id) return;
            const seq = ++previewContentSeq;
            const client = window.FanqieBrowserClient;
            if (!client || typeof client.getCatalog !== 'function' || typeof client.getContent !== 'function') {
                bodyEl.textContent = '当前环境无法加载正文，可点「新开页阅读」或直接下载。';
                return;
            }
            try {
                if (typeof client.detectSameOriginProxy === 'function') {
                    await client.detectSameOriginProxy();
                }
                const chapters = await client.getCatalog(String(book.book_id));
                if (seq !== previewContentSeq) return;
                if (!chapters || !chapters.length) {
                    bodyEl.textContent = '暂无章节目录，无法试读正文。';
                    return;
                }
                // 试读前 2 章（有的第一章是简介/推荐）
                const take = chapters.slice(0, 2);
                const parts = [];
                for (let i = 0; i < take.length; i++) {
                    const ch = take[i];
                    if (!ch.item_id) continue;
                    try {
                        const got = await client.getContent(ch.item_id);
                        if (seq !== previewContentSeq) return;
                        const chapTitle = (got && got.title) || ch.title || ('第 ' + (i + 1) + ' 章');
                        const text = (got && got.text) || '';
                        if (text) {
                            parts.push('【' + chapTitle + '】\n\n' + text.trim());
                        }
                    } catch (e) {
                        parts.push('【' + (ch.title || ('第 ' + (i + 1) + ' 章')) + '】\n\n（本章加载失败：' + (e.message || e) + '）');
                    }
                }
                if (seq !== previewContentSeq) return;
                if (!parts.length) {
                    bodyEl.textContent = '正文加载失败，可点「新开页阅读」重试，或直接下载全书。';
                    return;
                }
                const more = chapters.length > take.length
                    ? '\n\n—— 共 ' + chapters.length + ' 章，点「新开页阅读」继续看 ——'
                    : '';
                bodyEl.textContent = parts.join('\n\n────────\n\n') + more;
            } catch (e) {
                if (seq !== previewContentSeq) return;
                bodyEl.textContent = '正文试读失败：' + (e.message || e);
            }
        }

        function openReaderPage(book) {
            const b = book || previewBook;
            if (!b || !b.book_id) {
                showResult('没有可阅读的书籍', 'warning');
                return;
            }
            const q = new URLSearchParams();
            q.set('book_id', String(b.book_id));
            if (b.title) q.set('title', String(b.title));
            if (b.author) q.set('author', String(b.author));
            const url = 'reader.html?' + q.toString();
            window.open(url, '_blank', 'noopener,noreferrer');
        }

        function closeBookPreview() {
            const mask = document.getElementById('bookPreviewMask');
            if (mask) mask.hidden = true;
            previewBook = null;
            document.body.style.overflow = '';
        }

        function downloadFromPreview() {
            if (!previewBook || !previewBook.book_id) {
                showResult('没有可下载的书籍', 'warning');
                return;
            }
            const id = String(previewBook.book_id);
            const title = previewBook.title || id;
            document.getElementById('bookId').value = id;
            closeBookPreview();
            showResult('开始下载《' + title + '》…', 'info');
            executeDownload(id, title);
        }

        function startBatchDownload(books) {
            const list = (books || []).filter(function (b) { return b && b.book_id; });
            if (!list.length) {
                showResult('请先勾选要下载的书', 'warning');
                return;
            }
            if (batchDownloadRunning || staticJobActive || currentJobId) {
                showResult('当前有下载任务进行中，请稍后再批量下载', 'warning');
                return;
            }
            batchDownloadQueue = list.slice();
            showResult('已加入批量队列：' + list.length + ' 本，将依次下载', 'info');
            runNextBatchDownload();
        }

        function runNextBatchDownload() {
            if (batchDownloadRunning) return;
            if (!batchDownloadQueue.length) {
                showResult('批量下载队列已全部开始处理', 'success');
                updateSearchSelectionUi();
                return;
            }
            const next = batchDownloadQueue.shift();
            const id = String(next.book_id);
            const title = next.title || id;
            document.getElementById('bookId').value = id;
            batchDownloadRunning = true;
            updateSearchSelectionUi();
            showResult(
                '批量下载中：《' + title + '》（剩余 ' + batchDownloadQueue.length + ' 本）',
                'info'
            );
            Promise.resolve(executeDownload(id, title)).catch(function () {
                /* finishDownloadUi 会继续队列 */
            });
        }
        
        // 显示示例
        function showExample() {
            const bookIdInput = document.getElementById('bookId');
            bookIdInput.value = '抽象职校生存手册';
            
            // 聚焦输入框并选中文本
            bookIdInput.focus();
            bookIdInput.select();
            
            showResult('示例书名已填入，点击「搜索书名」试试', 'info');
            searchByName('抽象职校生存手册');
        }
        
        // 开始下载
        function startDownload() {
            const userInput = document.getElementById('bookId').value.trim();
            const bookId = extractBookId(userInput);
            
            if (!bookId) {
                // 当作书名搜索
                if (userInput) {
                    searchByName(userInput);
                    return;
                }
                showDowngradePrompt();
                return;
            }
            
            if (userInput !== bookId && !/^\d+$/.test(userInput.trim())) {
                showResult(`已从输入中提取小说ID：${bookId}，开始下载...`, 'info');
                setTimeout(() => {
                    executeDownload(bookId, userInput);
                }, 400);
                return;
            }
            
            executeDownload(bookId, userInput);
        }
        
        let currentJobId = null;

        function getDownloadOptions() {
            const start = Number(document.getElementById('startChapter')?.value || 0) || 0;
            const end = Number(document.getElementById('endChapter')?.value || 0) || 0;
            const resume = document.getElementById('resumeCache')
                ? document.getElementById('resumeCache').checked
                : true;
            return { start_chapter: start, end_chapter: end, resume };
        }

        async function cancelCurrentJob() {
            if (staticJobActive && window.FanqieBrowserClient) {
                window.FanqieBrowserClient.requestCancel();
                showResult('已请求取消当前任务…', 'info');
                return;
            }
            if (!currentJobId) return;
            try {
                await fetch(apiUrl('/api/job/' + currentJobId + '/cancel'), { method: 'POST' });
                showResult('已请求取消当前任务…', 'info');
            } catch (e) {
                showResult('取消失败：' + (e.message || e), 'error');
            }
        }

        async function refreshNodeStatus() {
            const el = document.getElementById('nodeStatus');
            if (!el) return;
            try {
                const backend = await detectBackend();
                if (backend) {
                    const resp = await fetch(apiUrl('/api/nodes'));
                    const data = await resp.json();
                    if (data && data.code === 0) {
                        el.textContent = '模式：Node 后端 · 节点 ' + data.alive + '/' + data.total + ' 在线';
                        return;
                    }
                }
                await ensureSameOriginProxy();
                if (API_PROXY_READY) {
                    if (REMOTE_API_BASE) {
                        el.textContent = '模式：远程 API 加速 · 搜索可用';
                    } else {
                        el.textContent = '模式：在线加速（同源 API）· 搜索可用';
                    }
                    return;
                }
                if (window.FanqieBrowserClient) {
                    el.textContent = '模式：静态 · 可用本机一键启动获得完整下载';
                    return;
                }
                el.textContent = '静态模式就绪';
            } catch (e) {
                el.textContent = '服务探测中…';
            }
        }

        function finishDownloadUi(ok) {
            const downloadBtn = document.getElementById('downloadBtn');
            const cancelBtn = document.getElementById('cancelBtn');
            const loadingSection = document.getElementById('loadingSection');
            const resultSection = document.getElementById('resultSection');
            loadingSection.style.display = 'none';
            resultSection.style.display = 'block';
            resultSection.classList.add('show');
            downloadBtn.disabled = false;
            downloadBtn.innerHTML = '<i>🚀</i><span>开始下载</span>';
            if (cancelBtn) cancelBtn.style.display = 'none';
            currentJobId = null;
            staticJobActive = false;
            const wasBatch = batchDownloadRunning;
            batchDownloadRunning = false;
            if (wasBatch && batchDownloadQueue.length) {
                setTimeout(function () { runNextBatchDownload(); }, 600);
            } else {
                updateSearchSelectionUi();
            }
        }

        // 执行下载：有 Node 走后端；GitHub Pages 走 browser-client
        async function executeDownload(bookId, userInput) {
            const downloadBtn = document.getElementById('downloadBtn');
            const cancelBtn = document.getElementById('cancelBtn');
            const loadingSection = document.getElementById('loadingSection');
            const resultSection = document.getElementById('resultSection');
            resetUI();
            // 保留搜索列表，方便预览后继续勾选 / 批量
            
            downloadBtn.disabled = true;
            downloadBtn.innerHTML = '<i>⏳</i><span>处理中...</span>';
            if (cancelBtn) cancelBtn.style.display = 'inline-flex';
            
            loadingSection.style.display = 'block';
            resultSection.style.display = 'none';
            document.getElementById('downgradeBtn').style.display = 'none';
            
            updateProgress(5, '准备下载...');

            try {
                const opts = getDownloadOptions();
                const backend = await detectBackend();

                if (!backend) {
                    if (!window.FanqieBrowserClient) throw new Error('浏览器客户端未加载（browser-client.js）');
                    staticJobActive = true;
                    updateProgress(8, '静态模式：固定 5 节点拉取章节...');
                    const st = await window.FanqieBrowserClient.downloadBook({
                        bookId: bookId,
                        start_chapter: opts.start_chapter,
                        end_chapter: opts.end_chapter,
                        resume: opts.resume,
                        onProgress: function(pct, msg) { updateProgress(pct, msg); }
                    });
                    finishDownloadUi();
                    const downloadLink = document.getElementById('downloadLink');
                    const manualHint = document.getElementById('manualHint');
                    downloadLink.href = st.url;
                    downloadLink.download = st.filename;
                    downloadLink.style.display = 'inline-block';
                    manualHint.style.display = 'block';
                    if (st.status === 'done') attemptAutoDownload(st.url, st.filename);
                    if (st.status === 'cancelled') {
                        const resultCard = document.getElementById('resultCard');
                        document.getElementById('resultIcon').textContent = '⏹';
                        document.getElementById('resultTitle').textContent = '已取消';
                        document.getElementById('resultMessage').textContent = st.message || '已取消';
                        resultCard.className = 'result-card result-error';
                    } else {
                        showDownloadSuccess(bookId, st.title || userInput, st);
                    }
                    return;
                }

                const params = new URLSearchParams({ book_id: bookId });
                if (opts.start_chapter > 0) params.set('start_chapter', String(opts.start_chapter));
                if (opts.end_chapter > 0) params.set('end_chapter', String(opts.end_chapter));
                if (!opts.resume) params.set('resume', '0');

                const createResp = await fetch(apiUrl('/api/download?' + params.toString()));
                const createData = await createResp.json();
                if (!createData || createData.code !== 0 || !createData.job_id) {
                    throw new Error((createData && createData.message) || '创建任务失败');
                }
                const jobId = createData.job_id;
                currentJobId = jobId;
                updateProgress(8, '任务已创建，开始拉取章节...');

                while (true) {
                    await new Promise(r => setTimeout(r, 800));
                    const stResp = await fetch(apiUrl('/api/job/' + jobId));
                    const st = await stResp.json();
                    if (!st || st.code !== 0) {
                        throw new Error((st && st.message) || '查询任务失败');
                    }
                    const pct = Math.max(8, Math.min(99, st.progress || 0));
                    const msg = st.message || '下载中...';
                    const detail = st.total ? `${msg}（${st.done || 0}/${st.total}）` : msg;
                    updateProgress(pct, detail);

                    if (st.status === 'done' || st.status === 'cancelled') {
                        const fileUrl = apiUrl('/api/job/' + jobId + '/file');
                        const fileName = st.filename || (`番茄小说-${bookId}.txt`);
                        finishDownloadUi();

                        if (st.filepath || st.filename) {
                            const downloadLink = document.getElementById('downloadLink');
                            const manualHint = document.getElementById('manualHint');
                            downloadLink.href = fileUrl;
                            downloadLink.download = fileName;
                            downloadLink.style.display = 'inline-block';
                            manualHint.style.display = 'block';
                            if (st.status === 'done') attemptAutoDownload(fileUrl, fileName);
                        }

                        if (st.status === 'cancelled') {
                            const resultCard = document.getElementById('resultCard');
                            document.getElementById('resultIcon').textContent = '⏹';
                            document.getElementById('resultTitle').textContent = '已取消';
                            document.getElementById('resultMessage').textContent = st.message || '任务已取消（可再次下载续传）';
                            resultCard.className = 'result-card result-error';
                        } else {
                            showDownloadSuccess(bookId, st.title || userInput, st);
                        }
                        break;
                    }
                    if (st.status === 'error') {
                        throw new Error(st.message || '下载失败');
                    }
                }
            } catch (e) {
                finishDownloadUi();
                const resultCard = document.getElementById('resultCard');
                document.getElementById('resultIcon').textContent = '';
                document.getElementById('resultTitle').textContent = '下载失败';
                document.getElementById('resultMessage').textContent = e.message || String(e);
                resultCard.className = 'result-card result-error';
                document.getElementById('downloadLink').style.display = 'none';
                document.getElementById('manualHint').style.display = 'none';
            }
        }
        
        // 显示下载成功消息
        function showDownloadSuccess(bookId, title, st) {
            const resultCard = document.getElementById('resultCard');
            const resultTitle = document.getElementById('resultTitle');
            const resultMessage = document.getElementById('resultMessage');
            const resultIcon = document.getElementById('resultIcon');
            const resultSection = document.getElementById('resultSection');
            
            resultCard.className = 'result-card result-success';
            resultIcon.textContent = '';
            resultTitle.textContent = '下载完成';
            const name = title || ('ID: ' + bookId);
            const bits = [];
            if (st && st.preview_count) bits.push(`${st.preview_count} 章网页端仅试读`);
            if (st && st.error_count) bits.push(`${st.error_count} 章获取失败已标注`);
            if (st && st.full_count != null && st.total) bits.push(`完整 ${st.full_count}/${st.total}`);
            const errHint = bits.length ? `（${bits.join('，')}）` : '';
            resultMessage.textContent = `《${name}》已生成 TXT${errHint}`;
            
            resultSection.style.display = 'block';
            resultSection.classList.add('show');
        }
        
        // 显示降级版本提示
        function showDowngradePrompt() {
            const resultCard = document.getElementById('resultCard');
            const resultTitle = document.getElementById('resultTitle');
            const resultMessage = document.getElementById('resultMessage');
            const resultIcon = document.getElementById('resultIcon');
            const resultSection = document.getElementById('resultSection');
            const downloadLink = document.getElementById('downloadLink');
            const downgradeBtn = document.getElementById('downgradeBtn');
            const manualHint = document.getElementById('manualHint');
            
            resultCard.className = 'result-card result-warning';
            resultIcon.textContent = '';
            resultTitle.textContent = '未识别到小说 ID';
            resultMessage.textContent = '可降级安装「番茄免费小说」后从分享链接再试，或直接输入数字 ID。';
            
            downloadLink.style.display = 'none';
            manualHint.style.display = 'none';
            downgradeBtn.style.display = 'inline-block';
            
            resultSection.style.display = 'block';
            resultSection.classList.add('show');
        }
        
        // 模拟下载过程
        function simulateDownloadProcess(onComplete) {
            let progress = 10;
            const steps = [
                { target: 25, text: '正在连接服务器...' },
                { target: 45, text: '正在获取小说信息...' },
                { target: 65, text: '正在解析章节内容...' },
                { target: 85, text: '正在生成下载文件...' },
                { target: 95, text: '正在准备下载链接...' }
            ];
            
            let currentStep = 0;
            
            const interval = setInterval(() => {
                if (currentStep < steps.length) {
                    const step = steps[currentStep];
                    progress += 2;
                    
                    if (progress >= step.target) {
                        updateProgress(progress, step.text);
                        currentStep++;
                    } else {
                        updateProgress(progress, step.text);
                    }
                } else {
                    clearInterval(interval);
                    setTimeout(() => {
                        onComplete();
                    }, 100);
                }
            }, 100);
        }
        
        // 更新进度
        function updateProgress(percent, text) {
            const progressBar = document.getElementById('progressBar');
            const loadingText = document.getElementById('loadingText');
            
            progressBar.style.width = `${percent}%`;
            loadingText.textContent = text;
            
            document.getElementById('progressContainer').style.display = 'block';
        }
        
        // 尝试自动下载
        function attemptAutoDownload(url, filename) {
            setTimeout(() => {
                try {
                    const link = document.createElement('a');
                    link.href = url;
                    link.download = filename;
                    link.style.display = 'none';
                    document.body.appendChild(link);
                    
                    link.click();
                    
                    setTimeout(() => {
                        document.body.removeChild(link);
                    }, 1000);
                    
                    setTimeout(() => {
                        const resultMessage = document.getElementById('resultMessage');
                        if (resultMessage) {
                            resultMessage.innerHTML = `小说《ID: ${filename.match(/\d+/)[0]}》下载准备完成！<br><small style="color: var(--success);">自动下载已触发，请检查下载文件夹</small>`;
                        }
                    }, 1500);
                    
                } catch (error) {
                    const resultMessage = document.getElementById('resultMessage');
                    if (resultMessage) {
                        resultMessage.innerHTML = `小说《ID: ${filename.match(/\d+/)[0]}》下载准备完成！<br><small style="color: var(--warning);">自动下载被阻止，请点击上方手动下载按钮</small>`;
                    }
                }
            }, 500);
        }
        
        // 显示结果
        function showResult(message, type = 'info') {
            const resultCard = document.getElementById('resultCard');
            const resultTitle = document.getElementById('resultTitle');
            const resultMessage = document.getElementById('resultMessage');
            const resultIcon = document.getElementById('resultIcon');
            const resultSection = document.getElementById('resultSection');
            const downloadLink = document.getElementById('downloadLink');
            const downgradeBtn = document.getElementById('downgradeBtn');
            const manualHint = document.getElementById('manualHint');
            
            resultCard.className = 'result-card';
            switch (type) {
                case 'success':
                    resultCard.classList.add('result-success');
                    resultIcon.textContent = '';
                    resultTitle.textContent = '下载成功';
                    break;
                case 'error':
                    resultCard.classList.add('result-error');
                    resultIcon.textContent = '';
                    resultTitle.textContent = '下载失败';
                    break;
                case 'warning':
                    resultCard.classList.add('result-warning');
                    resultIcon.textContent = '';
                    resultTitle.textContent = '注意';
                    break;
                default:
                    resultCard.classList.add('result-success');
                    resultIcon.textContent = '';
                    resultTitle.textContent = '提示';
            }
            
            resultMessage.textContent = message;
            resultSection.style.display = 'block';
            resultSection.classList.add('show');
            
            downgradeBtn.style.display = 'none';
            downloadLink.style.display = 'none';
            manualHint.style.display = 'none';
            
            if (type === 'info') {
                setTimeout(() => {
                    resultSection.classList.remove('show');
                    setTimeout(() => {
                        resultSection.style.display = 'none';
                    }, 400);
                }, 3000);
            }
        }
        
        // 重置界面
        function resetUI() {
            document.getElementById('loadingSection').style.display = 'none';
            const resultSection = document.getElementById('resultSection');
            resultSection.style.display = 'none';
            resultSection.classList.remove('show');
            document.getElementById('progressBar').style.width = '0%';
            document.getElementById('downloadLink').style.display = 'none';
            document.getElementById('downgradeBtn').style.display = 'none';
            document.getElementById('manualHint').style.display = 'none';
        }
        
        // 页面加载完成后的初始化
        document.addEventListener('DOMContentLoaded', async function() {
            
            // 显示主容器
            const container = document.getElementById('mainContainer');
            container.classList.add('loaded');
            
            // 检测设备类型
            const isMobile = /iPhone|iPad|iPod|Android/i.test(navigator.userAgent);
            const isTablet = /iPad|Android(?!.*Mobile)/i.test(navigator.userAgent) || 
                           (window.innerWidth >= 768 && window.innerWidth <= 1024);
            
            
            // 为输入框添加自动检测功能
            const bookIdInput = document.getElementById('bookId');
            const debouncedAutoDetect = debounce(autoDetectAndDownload, 800);
            bookIdInput.addEventListener('input', debouncedAutoDetect);
            
            // 输入框回车触发下载
            bookIdInput.addEventListener('keydown', function(e) {
                if (e.key === 'Enter') {
                    startDownload();
                }
            });
            
            // 500ms后显示初始一言（安全版）
            setTimeout(() => {
                showInitialDailyMessage();
            }, 500);

            refreshNodeStatus();
            setInterval(refreshNodeStatus, 60000);

            const previewMask = document.getElementById('bookPreviewMask');
            const previewDl = document.getElementById('previewDownloadBtn');
            const previewReader = document.getElementById('previewReaderBtn');
            const previewClose = document.getElementById('previewCloseBtn');
            const previewClose2 = document.getElementById('previewCloseBtn2');
            if (previewDl) previewDl.addEventListener('click', downloadFromPreview);
            if (previewReader) {
                previewReader.addEventListener('click', function () {
                    openReaderPage(previewBook);
                });
            }
            if (previewClose) previewClose.addEventListener('click', closeBookPreview);
            if (previewClose2) previewClose2.addEventListener('click', closeBookPreview);
            if (previewMask) {
                previewMask.addEventListener('click', function (e) {
                    if (e.target === previewMask) closeBookPreview();
                });
            }
            document.addEventListener('keydown', function (e) {
                if (e.key === 'Escape') closeBookPreview();
            });
            
            // 滚动到顶部
            window.scrollTo(0, 0);
            
            // 添加输入框焦点效果
            bookIdInput.addEventListener('focus', function() {
                this.parentElement.style.transform = 'translateY(-2px)';
            });
            
            bookIdInput.addEventListener('blur', function() {
                this.parentElement.style.transform = 'translateY(0)';
            });
            
            setInterval(cleanupStalePopups, 30000);
        });
