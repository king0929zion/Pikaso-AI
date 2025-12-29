document.addEventListener('DOMContentLoaded', () => {
    // State
    let currentView = 'chat';
    let viewStack = []; // For navigation history
    
    const chatHistory = [
        { id: 1, date: '今天 10:23', preview: '帮我打开 Bilibili 并搜索...' },
        { id: 2, date: '昨天 15:45', preview: '如何配置 AutoGLM 环境？' },
        { id: 3, date: '12月27日', preview: '测试无障碍服务权限' }
    ];

    const scripts = [
        { name: '12306.js', desc: '自动抢票脚本', size: '2.4KB' },
        { name: 'bilibili_tools.ts', desc: 'B站视频下载与签到', size: '5.1KB' },
        { name: 'douyin_download.js', desc: '抖音无水印下载', size: '3.2KB' },
        { name: 'jd_auto_buy.js', desc: '京东自动下单', size: '4.0KB' },
        { name: 'wechat_reply.ts', desc: '微信自动回复助手', size: '6.8KB' }
    ];

    // DOM Elements
    const sidebar = document.querySelector('.sidebar');
    const sidebarOverlay = document.querySelector('.sidebar-overlay');
    const menuBtn = document.querySelector('.menu-btn');
    const backBtn = document.querySelector('.back-btn');
    const views = document.querySelectorAll('.view');
    const navItems = document.querySelectorAll('.nav-item');
    const chatInput = document.querySelector('.chat-input');
    const sendBtn = document.querySelector('.send-btn');
    const chatContainer = document.querySelector('.chat-container');
    const emptyState = document.querySelector('.empty-state');
    const historyListContainer = document.querySelector('.history-list');
    const scriptListContainer = document.querySelector('.script-list');
    
    // New Header Elements
    const newChatTrigger = document.querySelector('.new-chat-trigger');
    const modelSelector = document.querySelector('.model-selector');

    // --- Navigation & Sidebar ---

    function toggleSidebar(show) {
        if (show) {
            sidebar.classList.add('active');
            sidebarOverlay.classList.add('active');
        } else {
            sidebar.classList.remove('active');
            sidebarOverlay.classList.remove('active');
        }
    }

    if (menuBtn) menuBtn.addEventListener('click', () => toggleSidebar(true));
    if (sidebarOverlay) sidebarOverlay.addEventListener('click', () => toggleSidebar(false));

    // Global navigation function
    window.navigateTo = function(viewId) {
        // Push current view to stack if it's not a sidebar navigation
        if (!['chat', 'tools', 'scripts', 'logs', 'help', 'settings'].includes(viewId)) {
            viewStack.push(currentView);
            updateHeader(true);
        } else {
            viewStack = []; // Clear stack on main nav switch
            updateHeader(false);
        }

        // Full Screen Mode Logic (Hide Header for Settings Sub-pages)
        if (viewId.startsWith('settings-') || viewId.startsWith('tool-') || viewId === 'script-editor') {
            document.body.classList.add('full-screen-mode');
        } else {
            document.body.classList.remove('full-screen-mode');
        }

        // Update UI
        views.forEach(view => {
            if (view.id === `view-${viewId}`) {
                view.classList.add('active');
            } else {
                view.classList.remove('active');
            }
        });

        // Update Sidebar Active State
        navItems.forEach(item => {
            if (item.dataset.view === viewId) {
                item.classList.add('active');
            } else {
                item.classList.remove('active');
            }
        });

        currentView = viewId;
        
        // Close sidebar on mobile after selection
        if (window.innerWidth < 768) {
            toggleSidebar(false);
        }
    };

    window.goBack = function() {
        if (viewStack.length > 0) {
            const prevView = viewStack.pop();
            
            // Full Screen Mode Logic (Restore Header if returning to main view)
            if (!prevView.startsWith('settings-') && !prevView.startsWith('tool-') && prevView !== 'script-editor') {
                document.body.classList.remove('full-screen-mode');
            }

            // Manually switch view without pushing to stack
            views.forEach(view => {
                if (view.id === `view-${prevView}`) {
                    view.classList.add('active');
                } else {
                    view.classList.remove('active');
                }
            });
            currentView = prevView;
            
            // Check if we are back to a main view
            if (['chat', 'tools', 'scripts', 'logs', 'help', 'settings'].includes(prevView)) {
                updateHeader(false);
                // Update sidebar active state
                navItems.forEach(item => {
                    if (item.dataset.view === prevView) {
                        item.classList.add('active');
                    } else {
                        item.classList.remove('active');
                    }
                });
            }
        }
    };

    // Helper for Prompt Editor
    window.insertVar = function(variable) {
        const textarea = document.querySelector('#view-settings-prompts textarea');
        if (textarea) {
            const start = textarea.selectionStart;
            const end = textarea.selectionEnd;
            const text = textarea.value;
            textarea.value = text.substring(0, start) + variable + text.substring(end);
            textarea.focus();
            textarea.selectionStart = textarea.selectionEnd = start + variable.length;
        }
    };

    function updateHeader(showBack) {
        if (window.innerWidth < 768) {
            if (showBack) {
                menuBtn.style.display = 'none';
                backBtn.style.display = 'flex';
            } else {
                menuBtn.style.display = 'flex';
                backBtn.style.display = 'none';
            }
        }
    }

    if (backBtn) {
        backBtn.addEventListener('click', window.goBack);
    }

    navItems.forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            const viewId = item.dataset.view;
            if (viewId) navigateTo(viewId);
        });
    });

    // --- Chat Functionality ---

    function addMessage(text, type) {
        if (emptyState.style.display !== 'none') {
            emptyState.style.display = 'none';
        }

        const bubble = document.createElement('div');
        bubble.classList.add('chat-bubble', type);
        bubble.textContent = text;
        
        chatContainer.appendChild(bubble);
        chatContainer.scrollTop = chatContainer.scrollHeight;
    }

    function handleSend() {
        const text = chatInput.value.trim();
        if (!text) return;

        addMessage(text, 'user');
        chatInput.value = '';

        setTimeout(() => {
            const responses = [
                "好的，正在为您执行该操作。",
                "我明白了，请稍等片刻。",
                "正在启动相关应用...",
                "已为您找到相关信息。",
                "请确认您的无障碍权限已开启。"
            ];
            const randomResponse = responses[Math.floor(Math.random() * responses.length)];
            addMessage(randomResponse, 'ai');
        }, 1000);
    }

    if (sendBtn) sendBtn.addEventListener('click', handleSend);
    if (chatInput) chatInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') handleSend();
    });

    document.querySelectorAll('.chip').forEach(chip => {
        chip.addEventListener('click', () => {
            chatInput.value = chip.textContent;
            handleSend();
        });
    });

    function startNewChat() {
        chatContainer.innerHTML = '';
        chatContainer.appendChild(emptyState);
        emptyState.style.display = 'flex';
        navigateTo('chat');
    }

    document.querySelector('.new-chat-btn').addEventListener('click', startNewChat);
    if (newChatTrigger) newChatTrigger.addEventListener('click', startNewChat);

    if (modelSelector) {
        modelSelector.addEventListener('click', () => {
            // Simple toggle for demo
            const current = modelSelector.querySelector('span').textContent;
            const next = current === 'GPT-4o' ? 'Claude 3.5' : 'GPT-4o';
            modelSelector.querySelector('span').textContent = next;
            showToast(`Switched to ${next}`);
        });
    }

    // --- Scripts List ---
    function renderScripts() {
        if (!scriptListContainer) return;
        scriptListContainer.innerHTML = '';
        scripts.forEach(script => {
            const el = document.createElement('div');
            el.classList.add('script-item');
            el.innerHTML = `
                <div>
                    <div class="script-name">${script.name}</div>
                    <div class="script-meta">${script.desc} • ${script.size}</div>
                </div>
                <span class="material-icons-round" style="color: var(--md-sys-color-outline);">chevron_right</span>
            `;
            el.addEventListener('click', () => {
                navigateTo('script-editor');
            });
            scriptListContainer.appendChild(el);
        });
    }
    renderScripts();

    // --- Settings & Tools ---

    document.querySelectorAll('.switch').forEach(sw => {
        sw.addEventListener('click', () => {
            sw.classList.toggle('on');
            const isActive = sw.classList.contains('on');
            const label = sw.parentElement.querySelector('.settings-title').textContent;
            showToast(`${label} ${isActive ? '已开启' : '已关闭'}`);
        });
    });

    // --- History ---
    
    function renderHistory() {
        historyListContainer.innerHTML = '';
        chatHistory.forEach(item => {
            const el = document.createElement('div');
            el.classList.add('history-item');
            el.innerHTML = `
                <div class="history-date">${item.date}</div>
                <div class="history-preview">${item.preview}</div>
            `;
            el.addEventListener('click', () => {
                showToast('加载历史记录...');
                navigateTo('chat');
                chatContainer.innerHTML = '';
                addMessage(item.preview, 'user');
                addMessage("这是历史记录回放。", 'ai');
            });
            historyListContainer.appendChild(el);
        });
    }
    
    renderHistory();

    // --- Utilities ---

    window.showToast = function(message) {
        const toast = document.createElement('div');
        toast.style.position = 'fixed';
        toast.style.bottom = '24px';
        toast.style.left = '50%';
        toast.style.transform = 'translateX(-50%)';
        toast.style.backgroundColor = '#323232';
        toast.style.color = 'white';
        toast.style.padding = '12px 24px';
        toast.style.borderRadius = '99px';
        toast.style.fontSize = '14px';
        toast.style.zIndex = '1000';
        toast.style.opacity = '0';
        toast.style.transition = 'opacity 0.3s';
        toast.innerText = message;
        
        document.body.appendChild(toast);
        
        requestAnimationFrame(() => {
            toast.style.opacity = '1';
        });

        setTimeout(() => {
            toast.style.opacity = '0';
            setTimeout(() => toast.remove(), 300);
        }, 2000);
    }
});
