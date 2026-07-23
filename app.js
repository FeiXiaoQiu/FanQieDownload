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

        /** 封面 CDN 常防盗链；优先经同源/远程 API 代理 */
        function resolveCoverUrl(raw) {
            let u = String(raw || '').trim();
            if (!u) return '';
            // bytecdn 常 403，换成 byteimg 同源资源
            if (u.indexOf('bytecdn.cn') !== -1 && u.indexOf('novel-pic/') !== -1) {
                const m = /novel-pic\/([^~?/]+)/.exec(u);
                if (m) {
                    u = 'https://p3-novel.byteimg.com/img/novel-pic/' + m[1] + '~tplv-tt-cs0:440:440.image';
                }
            }
            const base = REMOTE_API_BASE || (
                (location.protocol === 'http:' || location.protocol === 'https:') ? location.origin : ''
            );
            // 有可用 API 时走代理，避免浏览器直连被防盗链
            if (base && (API_PROXY_READY || REMOTE_API_BASE || /vercel\.app$/.test(location.host))) {
                return base + '/api/proxy?url=' + encodeURIComponent(u);
            }
            if (base && API_PROXY_READY !== false) {
                return base + '/api/proxy?url=' + encodeURIComponent(u);
            }
            return u;
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
                box.style.display = 'block';
                box.innerHTML = '<div class="search-empty">搜索中...</div>';
            }
            try {
                const backend = await detectBackend();
                await ensureSameOriginProxy();
                let books = [];
                // 优先同源 /api/search（Vercel / Node 都快）
                let searched = false;
                try {
                    const ctrl = new AbortController();
                    const t = setTimeout(function () { ctrl.abort(); }, 20000);
                    const resp = await fetch(apiUrl('/api/search?q=' + encodeURIComponent(input)), {
                        signal: ctrl.signal,
                        cache: 'no-store',
                    });
                    clearTimeout(t);
                    if (resp.ok) {
                        const data = await resp.json();
                        if (data && data.code === 0 && (data.books || []).length) {
                            books = data.books || [];
                            searched = true;
                        } else if (data && data.code === 0) {
                            books = [];
                            searched = true;
                        } else if (backend) {
                            throw new Error((data && data.message) || '搜索失败');
                        }
                    }
                } catch (e) {
                    if (backend) throw e;
                }
                if (!searched) {
                    if (!window.FanqieBrowserClient) throw new Error('浏览器客户端未加载');
                    const data = await window.FanqieBrowserClient.search(input);
                    if (!data || data.code !== 0) {
                        throw new Error((data && data.message) || '搜索失败');
                    }
                    books = data.books || [];
                }
                if (!books.length) {
                    box.innerHTML = '<div class="search-empty">未找到相关书籍</div>';
                    showResult('未找到相关书籍', 'warning');
                    return;
                }
                box.innerHTML = books.map(function(b) {
                    const title = b.title || '未知';
                    const meta = [b.author || '', b.category || ''].filter(Boolean).join(' · ');
                    const desc = String(b.abstract || '').replace(/\s+/g, ' ').trim();
                    const coverSrc = resolveCoverUrl(b.thumb_url || b.thumb_uri || '');
                    const img = coverSrc
                        ? '<img class="search-cover" src="' + escapeHtml(coverSrc) + '" alt="" loading="lazy" decoding="async" referrerpolicy="no-referrer" onerror="this.onerror=null;this.src=\'\';this.classList.add(\'search-cover-fail\');this.alt=\'无封面\'">'
                        : '<div class="search-cover search-cover-ph" aria-hidden="true">🍅</div>';
                    return '<div class="search-item" data-id="' + escapeHtml(b.book_id) + '" data-title="' + escapeHtml(title) + '">' +
                        img +
                        '<div class="search-item-body">' +
                        '<div class="search-item-title">' + escapeHtml(title) + '</div>' +
                        (meta ? '<div class="search-item-meta">' + escapeHtml(meta) + '</div>' : '') +
                        (desc ? '<div class="search-item-desc">' + escapeHtml(desc.slice(0, 120)) + (desc.length > 120 ? '…' : '') + '</div>' : '') +
                        '</div></div>';
                }).join('');
                Array.prototype.forEach.call(box.querySelectorAll('.search-item'), function(el) {
                    el.addEventListener('click', function() {
                        const id = el.getAttribute('data-id');
                        const title = el.getAttribute('data-title') || id;
                        document.getElementById('bookId').value = id;
                        showResult('已选择《' + title + '》，开始下载...', 'info');
                        executeDownload(id, title);
                    });
                });
            } catch (e) {
                box.innerHTML = '<div class="search-empty">搜索失败：' + escapeHtml(e.message || String(e)) + '</div>';
                showResult('搜索失败：' + e.message, 'error');
            }
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

        // 内嵌精简版 Worker，复制即可粘贴到 Cloudflare
        const WORKER_SOURCE = [
            'export default {',
            '  async fetch(request) {',
            '    const cors = {',
            '      "Access-Control-Allow-Origin": "*",',
            '      "Access-Control-Allow-Methods": "GET,HEAD,OPTIONS",',
            '      "Access-Control-Allow-Headers": "*",',
            '      "Access-Control-Max-Age": "86400",',
            '    };',
            '    if (request.method === "OPTIONS") {',
            '      return new Response(null, { status: 204, headers: cors });',
            '    }',
            '    const reqUrl = new URL(request.url);',
            '    let target = reqUrl.searchParams.get("url") || reqUrl.searchParams.get("u");',
            '    if (!target) {',
            '      return new Response(JSON.stringify({ ok: true, usage: "GET /?url=" }), {',
            '        status: 200,',
            '        headers: Object.assign({ "Content-Type": "application/json" }, cors),',
            '      });',
            '    }',
            '    let dest;',
            '    try { dest = new URL(target); } catch (e) {',
            '      return new Response(JSON.stringify({ error: "invalid url" }), {',
            '        status: 400, headers: Object.assign({ "Content-Type": "application/json" }, cors)',
            '      });',
            '    }',
            '    if (dest.protocol !== "http:" && dest.protocol !== "https:") {',
            '      return new Response(JSON.stringify({ error: "protocol not allowed" }), {',
            '        status: 400, headers: Object.assign({ "Content-Type": "application/json" }, cors)',
            '      });',
            '    }',
            '    try {',
            '      const upstream = await fetch(dest.toString(), {',
            '        method: "GET",',
            '        headers: { Accept: "application/json,text/plain,*/*" },',
            '        redirect: "follow",',
            '      });',
            '      const body = await upstream.arrayBuffer();',
            '      const headers = new Headers(cors);',
            '      headers.set("Content-Type", upstream.headers.get("Content-Type") || "application/json; charset=utf-8");',
            '      headers.set("Cache-Control", "no-store");',
            '      return new Response(body, { status: upstream.status, headers: headers });',
            '    } catch (e) {',
            '      return new Response(JSON.stringify({ error: "upstream failed", message: String(e) }), {',
            '        status: 502, headers: Object.assign({ "Content-Type": "application/json" }, cors)',
            '      });',
            '    }',
            '  },',
            '};',
        ].join('\n');

        function toggleProxySetup() {
            const body = document.getElementById('proxySetupBody');
            const btn = document.getElementById('proxySetupToggle');
            if (!body) return;
            const open = body.hasAttribute('hidden');
            if (open) {
                body.removeAttribute('hidden');
                if (btn) btn.textContent = '收起加速设置';
                initProxyPanel();
            } else {
                body.setAttribute('hidden', '');
                if (btn) btn.textContent = '搜索太慢 / 没结果？点这里看解决办法（不用 Cloudflare）';
            }
        }

        function initProxyPanel() {
            const box = document.getElementById('workerCodeBox');
            if (box && !box.value) box.value = WORKER_SOURCE;
            const input = document.getElementById('proxyUrlInput');
            if (input && window.FanqieBrowserClient) {
                const cur = window.FanqieBrowserClient.getCorsProxy() || '';
                if (!input.value) input.value = cur;
            }
            updateProxyHint();
        }

        async function testDirectAccess() {
            const hint = document.getElementById('proxyHint');
            if (hint) {
                hint.textContent = '正在测试直连节点…';
                hint.style.color = '#3742fa';
            }
            const hosts = (window.FanqieBrowserClient && window.FanqieBrowserClient.FIXED_HOSTS) || [
                'http://110.42.57.146:4018',
            ];
            let okHost = '';
            for (let i = 0; i < hosts.length; i++) {
                const host = hosts[i];
                try {
                    const ctrl = new AbortController();
                    const t = setTimeout(function () { ctrl.abort(); }, 6000);
                    const resp = await fetch(host + '/content?item_id=7580458932431225368', {
                        signal: ctrl.signal,
                        cache: 'no-store',
                        mode: 'cors',
                        headers: { Accept: 'application/json' },
                    });
                    clearTimeout(t);
                    const text = await resp.text();
                    if (!resp.ok || !text || text.charAt(0) === '<') continue;
                    JSON.parse(text);
                    okHost = host;
                    break;
                } catch (e) {
                    /* try next */
                }
            }
            if (okHost) {
                try { localStorage.setItem('fq_pref_proxy', 'direct'); } catch (e) {}
                showResult('直连成功（' + okHost + '）。扩展已生效，可直接搜索', 'success');
                if (hint) {
                    hint.textContent = '直连可用：' + okHost + '（扩展模式）';
                    hint.style.color = '#2ed573';
                }
                await refreshNodeStatus();
            } else {
                showResult('直连失败。请确认已启用 Allow CORS / CORS Unblock，或改用本机「一键启动」', 'warning');
                if (hint) {
                    hint.textContent = '直连失败。推荐下载 ZIP 后双击「一键启动.bat」在本机打开 127.0.0.1:8787';
                    hint.style.color = '#ffa502';
                }
            }
        }

        function updateProxyHint() {
            const hint = document.getElementById('proxyHint');
            if (!hint) return;
            const cur = (window.FanqieBrowserClient && window.FanqieBrowserClient.getCorsProxy()) || '';
            if (cur) {
                hint.textContent = '已配置代理：' + cur;
                hint.style.color = '#2ed573';
            } else {
                let pref = '';
                try { pref = localStorage.getItem('fq_pref_proxy') || ''; } catch (e) {}
                if (pref === 'direct') {
                    hint.textContent = '当前优先直连（扩展模式）。失败时再试公共通道。';
                    hint.style.color = '#2ed573';
                } else {
                    hint.textContent = '纯网页模式依赖公共通道，经常失败；推荐本机「一键启动」或装 CORS 扩展。';
                    hint.style.color = '#666';
                }
            }
        }

        async function copyWorkerCode() {
            initProxyPanel();
            const box = document.getElementById('workerCodeBox');
            const text = (box && box.value) || WORKER_SOURCE;
            try {
                if (navigator.clipboard && navigator.clipboard.writeText) {
                    await navigator.clipboard.writeText(text);
                } else if (box) {
                    box.focus();
                    box.select();
                    document.execCommand('copy');
                }
                showResult('Worker 代码已复制，去 Cloudflare 粘贴并 Deploy', 'success');
            } catch (e) {
                if (box) {
                    box.focus();
                    box.select();
                }
                showResult('请长按/全选代码框手动复制', 'warning');
            }
        }

        async function saveProxyUrl() {
            const input = document.getElementById('proxyUrlInput');
            let url = ((input && input.value) || '').trim().replace(/\/$/, '');
            if (!url) {
                showResult('请先粘贴 workers.dev 地址', 'warning');
                return;
            }
            if (!/^https?:\/\//i.test(url)) url = 'https://' + url;
            try {
                const u = new URL(url);
                url = u.origin;
            } catch (e) {
                showResult('地址格式不对，应类似 https://xxx.workers.dev', 'error');
                return;
            }
            if (window.FanqieBrowserClient) {
                window.FanqieBrowserClient.setCorsProxy(url);
            } else {
                try { localStorage.setItem('fq_cors_proxy', url); } catch (e) {}
            }
            if (input) input.value = url;
            const hint = document.getElementById('proxyHint');
            if (hint) {
                hint.textContent = '正在测试代理…';
                hint.style.color = '#3742fa';
            }
            try {
                const testTarget = encodeURIComponent('http://110.42.57.146:4018/content?item_id=7580458932431225368');
                const ctrl = new AbortController();
                const t = setTimeout(function () { ctrl.abort(); }, 15000);
                const resp = await fetch(url + '/?url=' + testTarget, {
                    signal: ctrl.signal,
                    cache: 'no-store',
                    headers: { Accept: 'application/json' },
                });
                clearTimeout(t);
                const text = await resp.text();
                if (!resp.ok) throw new Error('HTTP ' + resp.status);
                if (!text || text.charAt(0) === '<') throw new Error('返回不是 JSON');
                JSON.parse(text);
                showResult('代理可用，已保存。现在可以搜索了', 'success');
                if (hint) {
                    hint.textContent = '代理测试通过：' + url;
                    hint.style.color = '#2ed573';
                }
                await refreshNodeStatus();
            } catch (e) {
                showResult('已保存，但测试失败：' + (e.message || e) + '。请确认 Worker 已 Deploy 且地址正确', 'warning');
                if (hint) {
                    hint.textContent = '已保存 ' + url + '，测试未通过，可稍后重试搜索';
                    hint.style.color = '#ffa502';
                }
            }
        }

        function clearProxyUrl() {
            if (window.FanqieBrowserClient) {
                window.FanqieBrowserClient.setCorsProxy('');
            } else {
                try { localStorage.removeItem('fq_cors_proxy'); } catch (e) {}
            }
            const input = document.getElementById('proxyUrlInput');
            if (input) input.value = '';
            updateProxyHint();
            showResult('已清除自建代理，将改用公共通道', 'info');
            refreshNodeStatus();
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
                        const setup = document.getElementById('proxySetup');
                        if (setup) setup.style.display = 'none';
                        return;
                    }
                }
                await ensureSameOriginProxy();
                if (API_PROXY_READY && window.FanqieBrowserClient) {
                    const snap = await window.FanqieBrowserClient.probeHosts();
                    if (REMOTE_API_BASE) {
                        el.textContent = '模式：远程加速 API · 搜索应较快（页面可继续用本域名）';
                    } else {
                        el.textContent = '模式：在线加速（同源 API）· 节点 ' + snap.alive + '/' + snap.total + ' · 搜索应较快';
                    }
                    updateProxyHint();
                    return;
                }
                if (window.FanqieBrowserClient) {
                    const snap = await window.FanqieBrowserClient.probeHosts();
                    if (snap.customProxy) {
                        el.textContent = '模式：静态 · 自建代理 · 节点 ' + snap.alive + '/' + snap.total + ' 在线';
                    } else {
                        el.textContent = '模式：静态 · 公共通道易拥堵 · 建议用 Vercel 部署本仓库或本机启动';
                    }
                    updateProxyHint();
                    return;
                }
                el.textContent = '静态模式就绪';
            } catch (e) {
                el.textContent = '公共通道拥堵，请点下方查看解决办法（本机运行最稳）';
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
        }

        // 执行下载：有 Node 走后端；GitHub Pages 走 browser-client
        async function executeDownload(bookId, userInput) {
            const downloadBtn = document.getElementById('downloadBtn');
            const cancelBtn = document.getElementById('cancelBtn');
            const loadingSection = document.getElementById('loadingSection');
            const resultSection = document.getElementById('resultSection');
            const searchResults = document.getElementById('searchResults');
            
            resetUI();
            if (searchResults) searchResults.style.display = 'none';
            
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
                document.getElementById('resultIcon').textContent = '❌';
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
            resultIcon.textContent = '✅';
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
            resultIcon.textContent = '⚠️';
            resultTitle.textContent = '没有检测到ID哦';
            resultMessage.innerHTML = '<span style="font-size: 0.9em; color: var(--gray); display: block; margin-bottom: 10px;">(ó﹏ò｡)</span>请用户您降级<br><span style="color: var(--primary); font-weight: bold; font-size: 1.2em;">番茄免费小说</span>';
            
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
                    resultIcon.textContent = '✅';
                    resultTitle.textContent = '下载成功';
                    break;
                case 'error':
                    resultCard.classList.add('result-error');
                    resultIcon.textContent = '❌';
                    resultTitle.textContent = '下载失败';
                    break;
                case 'warning':
                    resultCard.classList.add('result-warning');
                    resultIcon.textContent = '⚠️';
                    resultTitle.textContent = '注意';
                    break;
                default:
                    resultCard.classList.add('result-success');
                    resultIcon.textContent = '💡';
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
            
            // 滚动到顶部
            window.scrollTo(0, 0);
            
            // 添加输入框焦点效果
            bookIdInput.addEventListener('focus', function() {
                this.parentElement.style.transform = 'translateY(-2px)';
            });
            
            bookIdInput.addEventListener('blur', function() {
                this.parentElement.style.transform = 'translateY(0)';
            });
            
            // 显示欢迎信息
            setTimeout(() => {
                const deviceType = isMobile ? '手机' : isTablet ? '平板' : '电脑';
                showResult(`欢迎使用番茄小说下载器！当前设备：${deviceType}，已自动适配最佳显示效果！`, 'info');
            }, 1000);
            
            // 添加按钮点击动画
            document.querySelectorAll('.btn').forEach(btn => {
                btn.addEventListener('click', function() {
                    this.style.transform = 'scale(0.95)';
                    setTimeout(() => {
                        this.style.transform = '';
                    }, 200);
                });
            });
            
            // 添加定期清理任务（每30秒清理一次可能残留的弹窗）
            setInterval(cleanupStalePopups, 30000);
            
            // 监听窗口大小变化，实时适配
            let resizeTimer;
            window.addEventListener('resize', function() {
                clearTimeout(resizeTimer);
                resizeTimer = setTimeout(function() {
                    // 在窗口调整后更新布局
                    const container = document.getElementById('mainContainer');
                    container.style.transform = 'scale(0.99)';
                    setTimeout(() => {
                        container.style.transform = 'scale(1)';
                    }, 100);
                }, 200);
            });
        });
        
        // 修复页面位置问题
        window.addEventListener('load', function() {
            if (window.scrollY > 0) {
                window.scrollTo(0, 0);
            }
            
            setTimeout(() => {
                window.scrollTo(0, 0);
            }, 100);
        });
