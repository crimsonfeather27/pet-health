// ===================== 全局状态 =====================
const AppState = {
    currentSection: 'home',
    currentUser: null,
    token: localStorage.getItem('pethealth_token') || null,
    petCache: [],
    postCache: [],
};

// ===================== 通用 API 封装 =====================
async function api(method, url, body = null) {
    const opts = {
        method,
        headers: { 'Content-Type': 'application/json' },
    };
    // 自动携带 Token（登录/注册接口本身除外）
    if (AppState.token && !url.endsWith('/login') && !url.endsWith('/register')) {
        opts.headers['Authorization'] = 'Bearer ' + AppState.token;
    }
    if (body) opts.body = JSON.stringify(body);

    const res = await fetch(url, opts);
    if (res.status === 401) {
        // Token 失效，清登录态
        AppState.token = null;
        AppState.currentUser = null;
        localStorage.removeItem('pethealth_token');
        updateUserSection();
        throw new Error('登录已失效，请重新登录');
    }
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json();
    if (json.code !== 200) throw new Error(json.message || '请求失败');
    return json.data;
}
const apiGet    = (url)         => api('GET',    url);
const apiPost   = (url, body)   => api('POST',   url, body);
const apiPut    = (url, body)   => api('PUT',    url, body);
const apiDelete = (url)         => api('DELETE', url);

// ===================== 页面切换 =====================
// 可通过 URL hash 直达的区块集合
const HASH_SECTIONS = ['home','pets','health-records','ai-diagnosis','community','nutrition','reminders','notifications','my-posts'];

function showSection(sectionId) {
    document.querySelectorAll('main > section').forEach(s => s.classList.remove('active'));
    const target = document.getElementById(sectionId);
    if (target) target.classList.add('active');
    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
    const activeLink = document.querySelector(`.nav-link[href="#${sectionId}"]`);
    if (activeLink) activeLink.classList.add('active');

    AppState.currentSection = sectionId;
    loadSectionData(sectionId);
    refreshUnreadBadge();   // 每次切页刷新未读徽标（登录后导航常驻显示）
    refreshIcons();

    // 同步 URL hash，保证刷新后停留在当前页面（仅在区块值不一致时更新）
    if (HASH_SECTIONS.includes(sectionId) && location.hash !== `#${sectionId}`) {
        location.hash = sectionId;
    }
}

async function loadSectionData(id) {
    try {
        switch (id) {
            case 'home':        await loadHome(); break;
            case 'pets':        await loadPets(); break;
            case 'community':   await loadPosts(); break;
            case 'health-records':  await loadHealthRecords(); break;
            case 'ai-diagnosis':    await loadAIDiagnosisPage(); break;
            case 'nutrition':   await loadNutrition(); break;
            case 'reminders':   await loadReminders(); break;
            case 'notifications':   await loadNotifications(); break;
            case 'my-posts':    await loadMyPosts(); break;
        }
    } catch (e) {
        console.warn(`加载 ${id} 数据失败（可能后端未启动）:`, e);
    }
}

// ===================== AI 诊断页面初始化 =====================
async function loadAIDiagnosisPage() {
    const sel = document.getElementById('diag-pet-select');
    if (!sel) return;

    // 填充前临时禁用，避免用户交互期间选项为空
    sel.disabled = true;

    // 1. 确保宠物缓存已就绪（兜底：从接口拉取）
    if (!Array.isArray(AppState.petCache) || AppState.petCache.length === 0) {
        try {
            AppState.petCache = await apiGet('/api/pets');
        } catch (e) {
            console.warn('AI诊断页加载宠物列表失败：', e);
            AppState.petCache = [];
        }
    }

    // 2. 构建选项列表（先数据，后写DOM）
    const pets = Array.isArray(AppState.petCache) ? AppState.petCache : [];
    let optionsHtml = '';
    if (pets.length === 0) {
        optionsHtml = '<option value="">（请先在宠物档案页添加宠物）</option>';
    } else {
        optionsHtml = '<option value="">请选择宠物</option>' +
            pets.map(p => {
                const parts = [p.name];
                if (p.species) parts.push(p.species);
                if (p.breed) parts.push(p.breed);
                if (p.ageMonths != null) parts.push(p.ageMonths + '个月');
                return `<option value="${p.id}">${parts.join(' · ')}</option>`;
            }).join('');
    }

    // 3. 一次性写入 DOM
    sel.innerHTML = optionsHtml;

    // 4. 默认选中第一只宠物（如果已有缓存）
    if (!sel.value && pets.length > 0) {
        sel.value = pets[0].id;
    }

    // 5. 填充完成，恢复可用
    sel.disabled = false;
}

// ===================== 首页加载 =====================
async function loadHome() {
    try {
        const hotPosts = await apiGet('/api/posts/hot?limit=5');
        const container = document.getElementById('hot-posts-list');
        if (container) container.innerHTML = hotPosts.map(p => `
            <div class="hot-post-item" onclick="goToPost('${p.id}')">
                <span class="hot-title">${p.title}</span>
                <span class="hot-meta">${p.replyCount || 0}回复</span>
            </div>
        `).join('');
    } catch (e) { /* 后端未启动时显示占位符 */ }

    try {
        const dueReminders = await apiGet('/api/reminders/due?days=7');
        const container = document.getElementById('due-reminders-list');
        if (container && dueReminders.length > 0) {
            container.innerHTML = dueReminders.map(r => `
                <div class="reminder-item">
                    <span class="reminder-icon"><i data-lucide="bell"></i></span>
                    <span>${r.title} — 还有 ${r.daysLeft} 天</span>
                </div>
            `).join('');
        }
    } catch (e) { /* reminders 微服务未启动时忽略 */ }
}

// ===================== 登录注册（Modal） =====================
function showLoginModal() {
    const html = `
        <div class="modal" id="login-modal">
            <div class="modal-content">
                <h3>登录 PetHealth</h3>
                <div class="form-group">
                    <label>用户名</label>
                    <input id="login-username" type="text" placeholder="demo">
                </div>
                <div class="form-group">
                    <label>密码</label>
                    <input id="login-password" type="password" placeholder="123456">
                </div>
                <button class="btn btn-primary full-width" onclick="doLogin()">登录</button>
                <p class="modal-hint">测试账号: demo / 123456</p>
                <button class="modal-close" onclick="closeModal('login-modal')">✕</button>
            </div>
        </div>`;
    openModal(html);
}

function showRegisterModal() {
    const html = `
        <div class="modal" id="register-modal">
            <div class="modal-content">
                <h3>注册 PetHealth</h3>
                <div class="form-group">
                    <label>用户名</label>
                    <input id="reg-username" type="text" placeholder="你的昵称">
                </div>
                <div class="form-group">
                    <label>邮箱</label>
                    <input id="reg-email" type="email" placeholder="you@example.com">
                </div>
                <div class="form-group">
                    <label>密码</label>
                    <input id="reg-password" type="password" placeholder="至少 6 位">
                </div>
                <button class="btn btn-primary full-width" onclick="doRegister()">注册</button>
                <button class="modal-close" onclick="closeModal('register-modal')">✕</button>
            </div>
        </div>`;
    openModal(html);
}

function openModal(html) { document.body.insertAdjacentHTML('beforeend', html); }
function closeModal(id)  { document.getElementById(id)?.remove(); }

async function doLogin() {
    const username = document.getElementById('login-username').value;
    const password = document.getElementById('login-password').value;
    try {
        const data = await apiPost('/api/users/login', { username, password });
        // 新响应结构：{token, user}
        AppState.token = data.token;
        AppState.currentUser = data.user;
        localStorage.setItem('pethealth_token', data.token);
        closeModal('login-modal');
        updateUserSection();
        refreshUnreadBadge();
        showToast(`欢迎回来，${data.user.username}！`);
    } catch (e) {
        showToast(e.message, 'error');
    }
}

async function doRegister() {
    const username = document.getElementById('reg-username').value;
    const email    = document.getElementById('reg-email').value;
    const password = document.getElementById('reg-password').value;
    try {
        await apiPost('/api/users/register', { username, email, password });
        closeModal('register-modal');
        showToast('注册成功！请登录');
        showLoginModal();
    } catch (e) {
        showToast(e.message, 'error');
    }
}

function updateUserSection() {
    const section = document.getElementById('user-section');
    if (!section) return;
    if (!AppState.currentUser) {
        section.innerHTML = `
            <button class="btn btn-secondary" onclick="showLoginModal()">登录</button>
            <button class="btn btn-primary" onclick="showRegisterModal()">注册</button>`;
    } else {
        const u = AppState.currentUser;
        const avatarUrl = u.avatar && u.avatar.startsWith('/') ? u.avatar : (u.avatar || '');
        section.innerHTML = `
            <div class="user-dropdown">
                <div class="user-profile-entry" onclick="toggleUserDropdown(event)" title="个人中心">
                    ${avatarUrl
                        ? `<img class="user-avatar" src="${avatarUrl}" alt="头像" onerror="this.style.display='none'; this.nextElementSibling.style.display='flex'">`
                        : ''}
                    <span class="user-avatar user-avatar-fallback" style="${avatarUrl ? 'display:none' : 'display:flex'}">${(u.username || 'U').charAt(0).toUpperCase()}</span>
                    <span class="user-welcome">${u.username}</span>
                    <span class="user-caret">▾</span>
                </div>
                <div class="user-dropdown-menu" id="user-dropdown-menu" style="display:none">
                    <div class="user-dropdown-item" onclick="showProfileModal()">我的信息</div>
                    <div class="user-dropdown-item" onclick="showSection('my-posts')">我的帖子</div>
                    <div class="user-dropdown-item" onclick="showSection('notifications')">消息<span id="notif-badge" class="notif-badge" style="display:none">0</span></div>
                    <div class="user-dropdown-item user-dropdown-item-danger" onclick="doLogout()">退出</div>
                </div>
            </div>`;
    }
}

/**
 * 展开/收起用户下拉菜单
 */
function toggleUserDropdown(e) {
    e.stopPropagation();
    const menu = document.getElementById('user-dropdown-menu');
    if (menu) menu.style.display = menu.style.display === 'block' ? 'none' : 'block';
}

/**
 * 关闭用户下拉菜单
 */
function closeUserDropdown() {
    const menu = document.getElementById('user-dropdown-menu');
    if (menu) menu.style.display = 'none';
}

// 点击页面其它区域时收起用户下拉菜单
document.addEventListener('click', () => closeUserDropdown());

async function doLogout() {
    try {
        await apiPost('/api/users/logout', {});
    } catch (e) { /* ignore */ }
    AppState.token = null;
    AppState.currentUser = null;
    localStorage.removeItem('pethealth_token');
    updateUserSection();
    showToast('已退出登录');
}

// ===================== 个人中心 =====================
/**
 * 打开个人中心弹窗：头像展示/上传 + 邮箱/手机号修改
 */
function showProfileModal() {
    if (!AppState.currentUser) return;
    const u = AppState.currentUser;
    const avatarUrl = u.avatar && u.avatar.startsWith('/') ? u.avatar : (u.avatar || '');
    const html = `
        <div class="modal" id="profile-modal">
            <div class="modal-content">
                <button class="modal-close" onclick="closeModal('profile-modal')">✕</button>
                <h3><i data-lucide="user"></i> 个人中心</h3>
                <div class="profile-avatar-row">
                    <div class="profile-avatar-wrap" id="profile-avatar-wrap">
                        ${avatarUrl
                            ? `<img id="profile-avatar-img" src="${avatarUrl}" alt="头像">`
                            : `<div id="profile-avatar-img" class="profile-avatar-fallback">${(u.username || 'U').charAt(0).toUpperCase()}</div>`}
                    </div>
                    <div class="profile-avatar-actions">
                        <button class="btn btn-primary btn-tiny" onclick="document.getElementById('avatar-file-input').click()"><i data-lucide="camera"></i> 上传头像</button>
                        <p class="profile-hint">支持 png/jpg/gif/webp，≤ 5MB</p>
                    </div>
                    <input type="file" id="avatar-file-input" accept="image/*" style="display:none" onchange="handleAvatarSelect(this)">
                </div>
                <div class="form-group">
                    <label>用户名</label>
                    <input type="text" value="${u.username}" disabled style="background:#f5f5f5">
                </div>
                <div class="form-row">
                    <div class="form-group" style="flex:1">
                        <label>邮箱</label>
                        <input id="profile-email" type="email" value="${u.email || ''}" placeholder="name@example.com">
                    </div>
                    <div class="form-group" style="flex:1">
                        <label>手机号</label>
                        <input id="profile-phone" type="text" value="${u.phone || ''}" placeholder="选填">
                    </div>
                </div>
                <div class="modal-actions">
                    <button class="btn" onclick="closeModal('profile-modal')">取消</button>
                    <button class="btn btn-primary" onclick="saveProfile()"><i data-lucide="save"></i> 保存资料</button>
                </div>
            </div>
        </div>`;
    openModal(html);
}

/**
 * 加载当前用户发布的帖子（「我的帖子」页面）
 */
async function loadMyPosts() {
    const container = document.getElementById('my-posts-container');
    if (!container) return;
    if (!AppState.currentUser) {
        container.innerHTML = `<div class="empty-hint">请先登录后查看我的帖子</div>`;
        return;
    }
    try {
        const page = await apiGet('/api/posts?page=0&size=50');
        const posts = (page.content || page || [])
            .filter(p => p.authorId === AppState.currentUser.id);
        container.innerHTML = renderMyPosts(posts);
    } catch (e) {
        container.innerHTML = `<div class="empty-hint">帖子加载失败：${e.message}</div>`;
    }
}

function renderMyPosts(posts) {
    if (!posts || posts.length === 0) {
        return `<div class="empty-hint">还没有发布过帖子 — 点击右上角「发布新帖」创建</div>`;
    }
    return posts.map(p => {
        const time = p.createdAt ? new Date(p.createdAt).toLocaleDateString('zh-CN') : '';
        return `
        <div class="my-post-item">
            <div class="my-post-info">
                <span class="post-category">${categoryIcon(p.category)}${p.category || 'GENERAL'}</span>
                <span class="my-post-title">${p.title}</span>
            </div>
            <div class="my-post-meta">
                <span><i data-lucide="thumbs-up"></i> ${p.likeCount || 0}</span>
                <span><i data-lucide="message-circle"></i> ${p.replyCount || 0}</span>
                <span>${time}</span>
                <button class="btn btn-tiny btn-secondary" onclick="goToPost('${p.id}')">查看</button>
            </div>
        </div>`;
    }).join('');
}

/**
 * 选择头像文件：本地预览 → 上传 → 更新全局用户 + 顶栏头像
 */
async function handleAvatarSelect(input) {
    const file = input.files && input.files[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) { showToast('请选择图片文件', 'error'); return; }
    if (file.size > 5 * 1024 * 1024) { showToast('图片大小不能超过 5MB', 'error'); return; }

    // 本地预览（先即时反馈，上传成功后再持久化）
    const img = document.getElementById('profile-avatar-img');
    if (img) {
        if (img.tagName === 'IMG') {
            img.src = URL.createObjectURL(file);
        } else {
            const wrap = document.getElementById('profile-avatar-wrap');
            const newImg = document.createElement('img');
            newImg.id = 'profile-avatar-img';
            newImg.src = URL.createObjectURL(file);
            newImg.alt = '头像';
            wrap.innerHTML = '';
            wrap.appendChild(newImg);
        }
    }

    try {
        const user = await uploadAvatarFile(file);
        AppState.currentUser = user;
        showToast('头像上传成功');
        updateUserSection();
    } catch (e) {
        showToast('头像上传失败：' + e.message, 'error');
        // 失败时重开弹窗恢复原头像展示
        showProfileModal();
    }
}

/**
 * 上传头像到 /api/users/avatar（multipart/form-data）
 * 注意：不能走 api() 封装（其强制 Content-Type: application/json），需手动带 token 用 fetch
 */
async function uploadAvatarFile(file) {
    const fd = new FormData();
    fd.append('file', file);
    const res = await fetch('/api/users/avatar', {
        method: 'POST',
        headers: AppState.token ? { 'Authorization': 'Bearer ' + AppState.token } : {},
        body: fd
    });
    const json = await res.json();
    if (json.code !== 200) throw new Error(json.message || '上传失败');
    return json.data; // 更新后的 User（password 已置空）
}

/**
 * 保存资料（邮箱/手机号）→ PUT /api/users/{id}
 */
async function saveProfile() {
    const u = AppState.currentUser;
    if (!u) return;
    const email = document.getElementById('profile-email')?.value?.trim() || '';
    const phone = document.getElementById('profile-phone')?.value?.trim() || '';
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        showToast('邮箱格式不正确', 'error');
        return;
    }
    try {
        const user = await apiPut(`/api/users/${u.id}`, { email, phone });
        AppState.currentUser = user;
        showToast('资料已保存');
        closeModal('profile-modal');
        updateUserSection();
    } catch (e) {
        showToast('保存失败：' + e.message, 'error');
    }
}

// 页面刷新时若有 Token，自动调 /me 恢复登录态
async function restoreLoginState() {
    if (!AppState.token) {
        updateUserSection();
        return;
    }
    try {
        const user = await apiGet('/api/users/me');
        AppState.currentUser = user;
        updateUserSection();
        refreshUnreadBadge();
    } catch (e) {
        // Token 失效，清登录态
        AppState.token = null;
        localStorage.removeItem('pethealth_token');
        updateUserSection();
    }
}

// ===================== 帖子列表 + 热门榜 =====================
async function loadPosts() {
    const container = document.getElementById('posts-container');
    try {
        const page = await apiGet('/api/posts?page=0&size=20');
        const posts = page.content || page;
        AppState.postCache = posts;

        // 批量查询当前用户对所有帖子的点赞状态（减少请求次数）
        const likeStatusMap = {};
        const userId = AppState.currentUser?.id;
        if (userId && posts.length > 0) {
            for (const p of posts) {
                try {
                    const liked = await apiGet(`/api/likes/check?targetType=POST&targetId=${p.id}`);
                    likeStatusMap[p.id] = !!liked;
                } catch (e) { /* 忽略单个查询失败 */ }
            }
        }

        container.innerHTML = posts.map(p => {
            const liked = !!likeStatusMap[p.id];
            const likeClass = liked ? 'btn-liked' : '';
            return `
            <div class="post-card card-glow">
                <div class="post-header">
                    <span class="post-category">${categoryIcon(p.category)}${p.category || 'GENERAL'}</span>
                    <span class="post-author">by ${p.authorName}</span>
                </div>
                <h3 class="post-title">${p.title}</h3>
                <p class="post-content">${truncate(p.content, 100)}</p>
                <div class="post-meta">
                    <span><i data-lucide="eye"></i> ${p.viewCount || 0}</span>
                    <button class="btn-tiny btn-like ${likeClass}" onclick="event.stopPropagation(); toggleLikePost('${p.id}', this, true)">
                        <i data-lucide="thumbs-up"></i> <span class="like-count">${p.likeCount || 0}</span>
                    </button>
                    <span><i data-lucide="message-circle"></i> ${p.replyCount || 0}</span>
                    <button class="btn-tiny btn-primary" onclick="goToPost('${p.id}')">查看详情 →</button>
                </div>
            </div>
        `;
        }).join('');
    } catch (e) {
        container.innerHTML = `<p class="empty-hint"><i data-lucide="lightbulb"></i> 后端还没启动，或者还没有帖子 —— 点击右上角「发布新帖」即可创建</p>`;
    }
}

async function goToPost(postId) {
    showSection('community');
    try {
        const post = await apiGet(`/api/posts/${postId}`);
        const replies = await apiGet(`/api/replies/post/${postId}`);
        showPostDetailModal(post, replies);
    } catch (e) { console.warn(e); }
}

async function showPostDetailModal(post, replies) {
    // 先异步获取点赞状态，再渲染 Modal
    let isLiked = false;
    if (AppState.currentUser?.id) {
        try { isLiked = !!await apiGet(`/api/likes/check?targetType=POST&targetId=${post.id}`); } catch (e) {}
    }

    // 是否为帖子作者（决定能否删除）
    const isAuthor = AppState.currentUser &&
        (AppState.currentUser.id === post.authorId || AppState.currentUser.username === post.authorName);

    const repliesHtml = (replies || []).length === 0
        ? `<p class="empty-hint">暂无回复，快来抢沙发～</p>`
        : replies.map(r => {
            // ① 权限判定（前端引导，服务端为最终裁定）
            const isReplyAuthor = AppState.currentUser &&
                (AppState.currentUser.id === r.authorId || AppState.currentUser.username === r.authorName);
            // ② 采纳徽章（服务端回写 isAccepted=true 时高亮）
            const acceptedBadge = r.isAccepted
                ? `<span class="pet-badge" style="background:#c6e7cd"><i data-lucide="target"></i> 已采纳</span>`
                : '';
            // ③ 删除按钮（仅回复作者）
            const delBtn = isReplyAuthor
                ? `<button class="btn-tiny btn-danger" onclick="deleteReply('${r.id}','${post.id}')"><i data-lucide="trash-2"></i> 删除</button>`
                : '';
            // ④ 采纳按钮（仅帖子作者 + 该回复尚未被采纳）
            const acceptBtn = (isAuthor && !r.isAccepted)
                ? `<button class="btn-tiny btn-primary" onclick="acceptReply('${r.id}','${post.id}')"><i data-lucide="target"></i> 采纳此回复</button>`
                : '';
            // 操作区：仅当至少有一个按钮或徽章时才渲染
            const hasActions = delBtn || acceptBtn || acceptedBadge;
            const actionsHtml = hasActions ? `
                <div class="reply-actions">
                    ${acceptedBadge}
                    <div class="reply-action-buttons">
                        ${acceptBtn}
                        ${delBtn}
                    </div>
                </div>` : '';
            return `
            <div class="reply-item ${r.isAccepted ? 'reply-item-accepted' : ''}">
                <div class="reply-avatar">${(r.authorName || 'U').charAt(0).toUpperCase()}</div>
                <div class="reply-body">
                    <p class="reply-author">
                        ${r.authorName || '匿名'} <span class="reply-time">${r.createdAt || ''}</span>
                    </p>
                    <p class="reply-content">${r.content || ''}</p>
                    ${actionsHtml}
                </div>
            </div>`;
        }).join('');

    const replyBox = AppState.currentUser
        ? `<div class="form-group" style="margin-top:1rem">
              <textarea id="post-reply-input" rows="2" placeholder="说点什么吧…"></textarea>
              <div class="modal-actions" style="margin-top:0.5rem">
                  <button class="btn btn-primary btn-tiny" onclick="submitReply('${post.id}')"><i data-lucide="message-circle"></i> 发表回复</button>
              </div>
           </div>`
        : `<p class="empty-hint" style="margin-top:1rem">请先登录后参与讨论</p>`;

    const likeBtnClass = isLiked ? 'btn-liked' : '';

    // 帖子作者操作区：编辑 + 删除（仅作者可见）
    const actionBtns = isAuthor
        ? `<button class="btn btn-primary btn-tiny" onclick="editPost('${post.id}')">编辑</button>
           <button class="btn btn-danger btn-tiny" onclick="deletePost('${post.id}')">删除帖子</button>`
        : '';

    const html = `
        <div class="modal post-detail-modal" id="post-detail-modal">
            <div class="modal-content modal-content-wide">
                <button class="modal-close" onclick="closeModal('post-detail-modal')">✕</button>
                <h3>${post.title}</h3>
                <p class="post-author">作者: ${post.authorName || '-'}  ·  ${post.createdAt || ''}</p>
                <p class="post-category">${categoryIcon(post.category)}${post.category || '未分类'} · ${post.petSpecies || ''}</p>
                ${post.tags && post.tags.length ? `<p class="post-tags">${post.tags.map(t => `<span class="tag">${t}</span>`).join('')}</p>` : ''}
                <div class="post-content-box">${post.content || ''}</div>
                <div class="post-stats">
                    <span><i data-lucide="eye"></i> <span id="detail-view-count">${post.viewCount || 0}</span></span>
                    <button id="detail-like-btn" class="btn-tiny btn-like ${likeBtnClass}" onclick="toggleLikePost('${post.id}', this, false)">
                        <i data-lucide="thumbs-up"></i> <span class="like-count" id="detail-like-count">${post.likeCount || 0}</span>
                    </button>
                    <span id="detail-reply-count"><i data-lucide="message-circle"></i> ${post.replyCount || 0}</span>
                </div>
                <div class="modal-actions" style="margin:1rem 0 0">
                    ${actionBtns}
                </div>
                <h4 style="margin: 1rem 0 0.5rem;"><i data-lucide="message-circle"></i> 回复 (<span id="reply-count-num">${(replies || []).length}</span>)</h4>
                <div class="replies-list" id="replies-list-container">${repliesHtml}</div>
                ${replyBox}
            </div>
        </div>`;
    openModal(html);
}

async function submitReply(postId) {
    const ta = document.getElementById('post-reply-input');
    if (!ta) return;
    const content = ta.value.trim();
    if (!content) { showToast('回复内容不能为空', 'error'); return; }
    try {
        await apiPost('/api/replies', {
            postId,
            content,
            authorId:  AppState.currentUser?.id  || 'demo',
            authorName: AppState.currentUser?.username || 'demo'
        });
        showToast('回复成功');
        closeModal('post-detail-modal');
        // 重新打开以刷新回复
        const post = await apiGet(`/api/posts/${postId}`);
        const replies = await apiGet(`/api/replies/post/${postId}`);
        showPostDetailModal(post, replies);
    } catch (e) {
        showToast('回复失败：' + e.message, 'error');
    }
}

// ============================================================
// 回复管理：渲染 + 局部刷新 + 删除 + 采纳
// ============================================================

/**
 * 纯函数：根据帖子对象 + 回复列表，生成与 showPostDetailModal 规则完全一致的 reply-item HTML
 * 复用给「初次渲染」和「操作后刷新」，避免代码分裂导致按钮/权限/徽章不一致
 */
function renderRepliesHtml(post, replies) {
    if (!replies || replies.length === 0) {
        return `<p class="empty-hint">暂无回复，快来抢沙发～</p>`;
    }
    const isPostAuthor = AppState.currentUser &&
        (AppState.currentUser.id === post.authorId || AppState.currentUser.username === post.authorName);
    return replies.map(r => {
        const isReplyAuthor = AppState.currentUser &&
            (AppState.currentUser.id === r.authorId || AppState.currentUser.username === r.authorName);
        const acceptedBadge = r.isAccepted
            ? `<span class="pet-badge" style="background:#c6e7cd"><i data-lucide="target"></i> 已采纳</span>`
            : '';
        const delBtn = isReplyAuthor
            ? `<button class="btn-tiny btn-danger" onclick="deleteReply('${r.id}','${post.id}')"><i data-lucide="trash-2"></i> 删除</button>`
            : '';
        const acceptBtn = (isPostAuthor && !r.isAccepted)
            ? `<button class="btn-tiny btn-primary" onclick="acceptReply('${r.id}','${post.id}')"><i data-lucide="target"></i> 采纳此回复</button>`
            : '';
        const hasActions = delBtn || acceptBtn || acceptedBadge;
        const actionsHtml = hasActions ? `
            <div class="reply-actions">
                ${acceptedBadge}
                <div class="reply-action-buttons">
                    ${acceptBtn}
                    ${delBtn}
                </div>
            </div>` : '';
        return `
        <div class="reply-item ${r.isAccepted ? 'reply-item-accepted' : ''}">
            <div class="reply-avatar">${(r.authorName || 'U').charAt(0).toUpperCase()}</div>
            <div class="reply-body">
                <p class="reply-author">
                    ${r.authorName || '匿名'} <span class="reply-time">${r.createdAt || ''}</span>
                </p>
                <p class="reply-content">${r.content || ''}</p>
                ${actionsHtml}
            </div>
        </div>`;
    }).join('');
}

/**
 * 局部刷新帖子详情 Modal 内的回复列表 + 计数（不关闭 Modal，体验无闪烁）
 */
async function refreshRepliesList(postId) {
    try {
        const replies = await apiGet(`/api/replies/post/${postId}`);
        const post = await apiGet(`/api/posts/${postId}`);
        const container = document.getElementById('replies-list-container');
        const countSpan = document.getElementById('reply-count-num');
        if (container) container.innerHTML = renderRepliesHtml(post, replies);
        if (countSpan) countSpan.textContent = String(replies.length || 0);
        // 同步帖子本身的 replyCount（避免详情统计数字不同步）
        const replyCountSpan = document.getElementById('detail-reply-count');
        if (replyCountSpan) {
            replyCountSpan.innerHTML = `<i data-lucide="message-circle"></i> ${replies.length || 0}`;
            refreshIcons();
        }
    } catch (e) {
        showToast('刷新回复失败：' + e.message, 'error');
    }
}

/**
 * 删除回复（敏感操作 → 严格遵循经验 100024945：唯一 confirm 守卫 + 取消即终止 + 所有副作用在同一分支）
 */
async function deleteReply(replyId, postId) {
    if (!replyId || !postId) return;
    // ① 唯一 confirm 守卫，结果收敛为布尔值
    const ok = confirm('确定删除这条回复吗？删除后无法恢复。');
    // ② 取消 → 立即 return 封口，任何副作用都不会执行
    if (!ok) return;
    // ③ 真分支内：发起请求 → toast → 局部刷新列表（计数和按钮同步更新）
    try {
        await apiDelete(`/api/replies/${replyId}`);
        showToast('已删除回复');
        await refreshRepliesList(postId);
    } catch (e) {
        showToast('删除失败：' + e.message, 'error');
    }
}

/**
 * 采纳回复（风险低 → 无 confirm，直接调用后端标记为最佳答案）
 */
async function acceptReply(replyId, postId) {
    if (!replyId || !postId) return;
    try {
        await apiPost(`/api/replies/${replyId}/accept`, {});
        showToast('已采纳为最佳回答');
        await refreshRepliesList(postId);
    } catch (e) {
        showToast('采纳失败：' + e.message, 'error');
    }
}

// ===================== 发帖 / 编辑帖子 =====================
/**
 * 打开发帖 / 编辑表单
 * @param {string} [postId] 有值 = 编辑态（拉取帖子回填）；无值 = 新建态
 */
async function showPostFormModal(postId) {
    if (!AppState.currentUser) { showToast('请先登录后发帖', 'error'); showLoginModal(); return; }

    // 编辑态：先拉取帖子数据用于回填
    let post = null;
    if (postId) {
        try {
            post = await apiGet(`/api/posts/${postId}`);
        } catch (e) {
            showToast('加载帖子失败：' + e.message, 'error');
            return;
        }
    }
    const isEdit = !!post;

    // 回填值（新建态使用默认值；编辑态取自帖子）
    const initTitle    = isEdit ? (post.title || '') : '';
    const initContent  = isEdit ? (post.content || '') : '';
    const initCategory = isEdit ? (post.category || 'GENERAL') : 'GENERAL';
    const initSpecies  = isEdit ? (post.petSpecies || '') : '';
    const initTags     = (isEdit && Array.isArray(post.tags)) ? post.tags.join(',') : '';

    // 属性转义（避免标题/标签含引号或 < > 破坏 HTML）
    const esc = s => String(s ?? '').replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

    // 分类下拉（编辑态标记 selected；原生 <option> 不支持内嵌 HTML 图标，图标展示在卡片/详情的分类徽章处）
    const categoryOptions = [
        ['GENERAL', '综合讨论'], ['HEALTH', '健康医疗'],
        ['NUTRITION', '喂养营养'], ['TRAINING', '训练行为'],
        ['SHOW', '萌宠展示'], ['QUESTION', '求助问答']
    ].map(([v, label]) => `<option value="${v}" ${v === initCategory ? 'selected' : ''}>${label}</option>`).join('');

    // 关联品类下拉（编辑态标记 selected）
    const speciesOptions = [
        ['', '不指定'], ['DOG', '狗狗'], ['CAT', '猫咪'],
        ['RABBIT', '兔子'], ['BIRD', '鸟类'], ['OTHER', '其他']
    ].map(([v, label]) => `<option value="${v}" ${v === initSpecies ? 'selected' : ''}>${label}</option>`).join('');

    const html = `
        <div class="modal" id="post-form-modal">
            <div class="modal-content modal-content-wide">
                <button class="modal-close" onclick="closeModal('post-form-modal')">✕</button>
                <h3>${isEdit ? '编辑帖子' : '发布新帖'}</h3>
                <input type="hidden" id="post-form-id" value="${isEdit ? post.id : ''}">
                <div class="form-row">
                    <div class="form-group" style="flex:2">
                        <label>标题 <span style="color:var(--danger)">*</span></label>
                        <input id="post-form-title" type="text" placeholder="2-100字" maxlength="100" value="${esc(initTitle)}">
                    </div>
                    <div class="form-group">
                        <label>分类</label>
                        <select id="post-form-category">${categoryOptions}</select>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>标签（逗号分隔，最多5个）</label>
                        <input id="post-form-tags" type="text" placeholder="例如: 疫苗,驱虫,英短" value="${esc(initTags)}">
                    </div>
                    <div class="form-group">
                        <label>关联宠物品类</label>
                        <select id="post-form-species">${speciesOptions}</select>
                    </div>
                </div>
                <div class="form-group">
                    <label>正文内容 <span style="color:var(--danger)">*</span> <span id="post-form-count">${initContent.length} / 5000</span></label>
                    <textarea id="post-form-content" rows="8" placeholder="分享你的故事或问题吧（5-5000字）" maxlength="5000">${esc(initContent)}</textarea>
                </div>
                <div class="modal-actions">
                    <button class="btn" onclick="closeModal('post-form-modal')">取消</button>
                    <button class="btn btn-primary" onclick="submitPost()">${isEdit ? '<i data-lucide="save"></i> 保存修改' : '<i data-lucide="rocket"></i> 发布'}</button>
                </div>
            </div>
        </div>`;
    openModal(html);

    // 字数统计（初始值直接显示已回填长度）
    setTimeout(() => {
        const contentEl = document.getElementById('post-form-content');
        const countEl = document.getElementById('post-form-count');
        if (contentEl && countEl) {
            contentEl.addEventListener('input', () => {
                countEl.textContent = `${contentEl.value.length} / 5000`;
            });
        }
    }, 50);
}

/**
 * 从帖子详情弹窗进入编辑（先关闭详情，再打开编辑表单）
 */
async function editPost(postId) {
    closeModal('post-detail-modal');
    await showPostFormModal(postId);
}

/**
 * 提交帖子：编辑态 PUT /api/posts/{id}，新建态 POST /api/posts
 * 通过隐藏字段 post-form-id 区分两种状态
 */
async function submitPost() {
    const postId = (document.getElementById('post-form-id')?.value || '').trim();
    const isEdit = !!postId;
    const title = val('post-form-title').trim();
    const content = val('post-form-content').trim();
    const category = val('post-form-category') || 'GENERAL';
    const petSpecies = val('post-form-species') || '';
    const tagsStr = val('post-form-tags').trim();

    if (title.length < 2 || title.length > 100) { showToast('标题长度需在 2-100 字', 'error'); return; }
    if (content.length < 5 || content.length > 5000) { showToast('内容长度需在 5-5000 字', 'error'); return; }

    const tags = tagsStr
        ? tagsStr.split(/[,，]/).map(t => t.trim()).filter(Boolean).slice(0, 5)
        : [];

    try {
        if (isEdit) {
            // 编辑态：PUT 更新（authorId/authorName 保持原作者身份，同时满足 @Valid 必填校验）
            await apiPut(`/api/posts/${postId}`, {
                title, content, category, petSpecies, tags,
                authorId:   AppState.currentUser.id,
                authorName: AppState.currentUser.username
            });
            showToast('保存修改成功');
            closeModal('post-form-modal');
            // 重新拉取并打开详情弹窗，保持用户上下文（内容/标题已更新）
            const updated = await apiGet(`/api/posts/${postId}`);
            const replies = await apiGet(`/api/replies/post/${postId}`);
            showPostDetailModal(updated, replies);
        } else {
            // 新建态：POST 创建（补 authorId / authorName）
            await apiPost('/api/posts', {
                title, content, category, petSpecies, tags,
                authorId:   AppState.currentUser.id,
                authorName: AppState.currentUser.username
            });
            showToast('发布成功');
            closeModal('post-form-modal');
            await loadPosts();
        }
    } catch (e) {
        showToast((isEdit ? '保存失败：' : '发布失败：') + e.message, 'error');
    }
}

// ===================== 点赞 / 删除帖子 =====================
/**
 * 点赞/取消点赞
 * @param {string} postId 帖子ID
 * @param {HTMLElement} btnEl 当前点击的按钮元素（列表或详情中）
 * @param {boolean} syncCardAndDetail 是否同时同步列表卡片与详情弹窗中的另一个点赞按钮
 */
async function toggleLikePost(postId, btnEl, syncCardAndDetail) {
    if (!AppState.currentUser) { showToast('请先登录后点赞', 'error'); showLoginModal(); return; }
    btnEl.disabled = true;
    try {
        const countSpan = btnEl.querySelector('.like-count');
        const currentCount = parseInt(countSpan?.textContent || '0', 10);
        const currentlyLiked = btnEl.classList.contains('btn-liked');

        if (currentlyLiked) {
            // 取消点赞
            await apiDelete(`/api/likes/POST/${postId}`);
            btnEl.classList.remove('btn-liked');
            if (countSpan) countSpan.textContent = Math.max(0, currentCount - 1);
        } else {
            // 点赞
            await apiPost('/api/likes', { targetType: 'POST', targetId: postId });
            btnEl.classList.add('btn-liked');
            if (countSpan) countSpan.textContent = currentCount + 1;
        }

        // 同步另一个位置的按钮（列表↔详情）
        if (syncCardAndDetail) {
            // 从详情中同步
            const otherBtn = document.getElementById('detail-like-btn');
            if (otherBtn) {
                const otherCount = otherBtn.querySelector('.like-count');
                if (btnEl.classList.contains('btn-liked')) otherBtn.classList.add('btn-liked'); else otherBtn.classList.remove('btn-liked');
                if (otherCount) otherCount.textContent = countSpan.textContent;
            }
        } else {
            // 同步列表中所有对应帖子的卡片按钮
            document.querySelectorAll(`.post-card .btn-like`).forEach(cardBtn => {
                // 找同一个帖子的卡片按钮 —— 从 onclick 参数中解析 postId
                const oc = cardBtn.getAttribute('onclick') || '';
                if (oc.includes(`'${postId}'`)) {
                    const c = cardBtn.querySelector('.like-count');
                    if (btnEl.classList.contains('btn-liked')) cardBtn.classList.add('btn-liked'); else cardBtn.classList.remove('btn-liked');
                    if (c) c.textContent = countSpan.textContent;
                }
            });
        }
    } catch (e) {
        showToast((e.message || '操作失败'), 'error');
    } finally {
        btnEl.disabled = false;
    }
}

async function deletePost(postId) {
    if (!confirm('确定删除该帖子？此操作不可恢复。')) return;
    try {
        await apiDelete(`/api/posts/${postId}`);
        showToast('删除成功');
        closeModal('post-detail-modal');
        await loadPosts();
    } catch (e) {
        showToast('删除失败：' + e.message, 'error');
    }
}

// ===================== 宠物档案 =====================
async function loadPets() {
    const container = document.getElementById('pets-container');
    try {
        const pets = await apiGet('/api/pets');
        AppState.petCache = pets;
        container.innerHTML = pets.map(p => `
            <div class="pet-card card-glow card-shine">
                <div class="pet-card-main" onclick="showPetDetail('${p.id}')">
                    <div class="pet-avatar">${petEmoji(p.species)}</div>
                    <h3>${p.name}</h3>
                    <p class="pet-meta">${p.species || ''} · ${p.breed || ''}</p>
                    <p class="pet-meta">${p.gender || ''}${p.neutered == null ? '' : (p.neutered ? ' · 已绝育' : ' · 未绝育')}</p>
                    ${p.vaccines && p.vaccines.length ?
                        `<span class="pet-badge"><i data-lucide="syringe"></i> ${p.vaccines.length} 疫苗</span>` : ''}
                </div>
                <div class="pet-card-actions">
                    <button class="btn-tiny btn-secondary" title="编辑" onclick="event.stopPropagation(); showPetFormModal('${p.id}')"><i data-lucide="pen-square"></i></button>
                    <button class="btn-tiny btn-danger" title="删除" onclick="event.stopPropagation(); deletePet('${p.id}')"><i data-lucide="trash-2"></i></button>
                </div>
            </div>
        `).join('');
    } catch (e) {
        container.innerHTML = `
            <div class="pet-card card-glow">
                <h3>示例宠物</h3>
                <p class="pet-meta">DOG · 柯基</p>
                <p class="pet-meta">MALE</p>
                <p class="empty-hint">（宠物档案微服务暂未接入）</p>
            </div>`;
    }
}

function petEmoji(species) {
    const s = (species || '').toLowerCase();
    if (s.includes('cat') || s.includes('猫')) return '<i data-lucide="cat"></i>';
    if (s.includes('dog') || s.includes('狗')) return '<i data-lucide="dog"></i>';
    if (s.includes('rabbit') || s.includes('兔')) return '<i data-lucide="rabbit"></i>';
    if (s.includes('bird') || s.includes('鸟')) return '<i data-lucide="bird"></i>';
    if (s.includes('fish') || s.includes('鱼')) return '<i data-lucide="fish"></i>';
    return '<i data-lucide="paw-print"></i>';
}

// ===================== 宠物 CRUD =====================
function showPetFormModal(petId) {
    const isEdit = !!petId;
    const pet = isEdit ? AppState.petCache.find(p => p.id === petId) : null;

    // 未登录时用 demo 身份
    const owner = AppState.currentUser;
    if (!owner) {
        showToast('请先登录后再添加宠物', 'error');
        showLoginModal();
        return;
    }

    const html = `
        <div class="modal" id="pet-form-modal">
            <div class="modal-content modal-content-wide">
                <button class="modal-close" onclick="closeModal('pet-form-modal')">✕</button>
                <h3>${isEdit ? '编辑宠物信息' : '添加新宠物'}</h3>
                <input type="hidden" id="pf-id" value="${pet?.id || ''}" />
                <div class="form-row">
                    <div class="form-group">
                        <label>宠物名 *</label>
                        <input id="pf-name" type="text" value="${pet?.name || ''}" placeholder="如：豆豆" />
                    </div>
                    <div class="form-group">
                        <label>物种 *</label>
                        <select id="pf-species">
                            <option value="cat" ${pet?.species === 'cat' ? 'selected' : ''}>猫 cat</option>
                            <option value="dog" ${pet?.species === 'dog' ? 'selected' : ''}>狗 dog</option>
                            <option value="rabbit" ${pet?.species === 'rabbit' ? 'selected' : ''}>兔 rabbit</option>
                            <option value="bird" ${pet?.species === 'bird' ? 'selected' : ''}>鸟 bird</option>
                            <option value="other" ${pet?.species && !['cat','dog','rabbit','bird'].includes(pet.species) ? 'selected' : ''}>其他 other</option>
                        </select>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>品种</label>
                        <input id="pf-breed" type="text" value="${pet?.breed || ''}" placeholder="如：柯基 / 英短" />
                    </div>
                    <div class="form-group">
                        <label>性别</label>
                        <select id="pf-gender">
                            <option value="公" ${pet?.gender === '公' ? 'selected' : ''}>公</option>
                            <option value="母" ${pet?.gender === '母' ? 'selected' : ''}>母</option>
                            <option value="未知" ${(!pet?.gender || pet?.gender === '未知') ? 'selected' : ''}>未知</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label>是否绝育</label>
                        <select id="pf-neutered">
                            <option value="" ${pet?.neutered == null ? 'selected' : ''}>未设置</option>
                            <option value="true" ${pet?.neutered === true ? 'selected' : ''}>是（已绝育）</option>
                            <option value="false" ${pet?.neutered === false ? 'selected' : ''}>否（未绝育）</option>
                        </select>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>出生日期</label>
                        <input id="pf-birthday" type="date" value="${pet?.birthday || ''}" />
                    </div>
                    <div class="form-group">
                        <label>头像 URL（可选）</label>
                        <input id="pf-avatar" type="text" value="${pet?.avatar || ''}" placeholder="留空使用默认 emoji" />
                    </div>
                </div>
                <div class="form-group">
                    <label>简介</label>
                    <textarea id="pf-description" rows="2" placeholder="如：活泼好动的小短腿">${pet?.description || ''}</textarea>
                </div>
                <div class="modal-actions">
                    <button class="btn btn-secondary" onclick="closeModal('pet-form-modal')">取消</button>
                    <button class="btn btn-primary" onclick="submitPetForm()">${isEdit ? '保存修改' : '创建宠物'}</button>
                </div>
            </div>
        </div>`;
    openModal(html);
}

async function submitPetForm() {
    const id = document.getElementById('pf-id').value;
    const name = document.getElementById('pf-name').value.trim();
    const species = document.getElementById('pf-species').value;
    if (!name) { showToast('宠物名不能为空', 'error'); return; }

    const body = {
        name,
        species,
        breed: document.getElementById('pf-breed').value.trim() || null,
        gender: document.getElementById('pf-gender').value,
        neutered: (() => { const v = document.getElementById('pf-neutered').value; return v === '' ? null : v === 'true'; })(),
        birthday: document.getElementById('pf-birthday').value || null,
        avatar: document.getElementById('pf-avatar').value.trim() || null,
        description: document.getElementById('pf-description').value.trim() || null,
        ownerId: AppState.currentUser?.id || 'demo',
        ownerName: AppState.currentUser?.username || 'demo',
    };

    try {
        if (id) {
            await apiPut(`/api/pets/${id}`, body);
            showToast('宠物信息已更新');
        } else {
            await apiPost('/api/pets', body);
            showToast('宠物创建成功');
        }
        closeModal('pet-form-modal');
        // 关闭可能已打开的详情弹窗，避免显示旧数据
        closeModal('pet-detail-modal');
        await loadPets();
    } catch (e) {
        showToast('保存失败：' + e.message, 'error');
    }
}

async function deletePet(petId) {
    const pet = AppState.petCache.find(p => p.id === petId);
    if (!confirm(`确认删除「${pet?.name || '此宠物'}」？\n该操作不可恢复，相关疫苗/驱虫/体检/就诊记录也将一并删除。`)) return;
    try {
        await apiDelete(`/api/pets/${petId}`);
        showToast('已删除宠物');
        closeModal('pet-detail-modal');
        await loadPets();
    } catch (e) {
        showToast('删除失败：' + e.message, 'error');
    }
}

function showPetDetail(petId) {
    const pet = AppState.petCache.find(p => p.id === petId);
    if (!pet) {
        showToast('未找到宠物信息', 'error');
        return;
    }

    // 基本信息 + 头部操作按钮
    const ageText = pet.birthday ? calcAge(pet.birthday) : '未知';
    const basicInfo = `
        <div class="pet-detail-header">
            <div class="pet-detail-avatar">${petEmoji(pet.species)}</div>
            <div class="pet-detail-info">
                <h3>${pet.name}</h3>
                <p class="pet-meta">${pet.species || '-'} · ${pet.breed || '-'} · ${pet.gender || '-'}</p>
                <p class="pet-meta">生日: ${pet.birthday || '-'} (${ageText})</p>
                ${pet.description ? `<p class="pet-desc">${pet.description}</p>` : ''}
            </div>
            <div class="pet-detail-actions">
                <button class="btn-tiny btn-secondary" title="编辑基本信息" onclick="closeModal('pet-detail-modal'); showPetFormModal('${pet.id}')"><i data-lucide="pen-square"></i> 编辑</button>
                <button class="btn-tiny btn-danger" title="删除宠物" onclick="deletePet('${pet.id}')">删除</button>
            </div>
        </div>`;

    // 疫苗时间轴
    const vaccines = pet.vaccines || [];
    const vaccineTimeline = `
        <div class="tab-toolbar">
            <button class="btn btn-primary btn-tiny" onclick="showRecordFormModal('${pet.id}','vaccine')">添加疫苗</button>
        </div>
        ${vaccines.length === 0
            ? `<p class="empty-hint">暂无疫苗记录</p>`
            : `<div class="timeline">${vaccines.map((v, i) => {
                const dueClass = v.nextDueAt && new Date(v.nextDueAt) <= new Date() ? 'overdue' : '';
                return `
                <div class="timeline-item ${dueClass}">
                    <div class="timeline-dot"><i data-lucide="syringe"></i></div>
                    <div class="timeline-content">
                        <h4>${v.name || '未命名疫苗'}</h4>
                        <p class="timeline-date">接种: ${v.vaccinatedAt || '-'}</p>
                        ${v.nextDueAt ? `<p class="timeline-due">下次: ${v.nextDueAt}</p>` : ''}
                        ${v.vetClinic ? `<p class="timeline-meta">医院: ${v.vetClinic}</p>` : ''}
                        ${v.notes ? `<p class="timeline-meta">备注: ${v.notes}</p>` : ''}
                        <div class="timeline-actions">
                            <button class="btn-tiny btn-secondary" onclick="showRecordFormModal('${pet.id}','vaccine',${i})"><i data-lucide="pen-square"></i></button>
                            <button class="btn-tiny btn-danger" onclick="deleteRecord('${pet.id}','vaccine',${i})"><i data-lucide="trash-2"></i></button>
                        </div>
                    </div>
                </div>`;
            }).join('')}</div>`}
    `;

    // 驱虫记录
    const dewormings = pet.dewormings || [];
    const dewormingList = `
        <div class="tab-toolbar">
            <button class="btn btn-primary btn-tiny" onclick="showRecordFormModal('${pet.id}','deworming')">添加驱虫</button>
        </div>
        ${dewormings.length === 0
            ? `<p class="empty-hint">暂无驱虫记录</p>`
            : `<div class="timeline">${dewormings.map((d, i) => {
                const dueClass = d.nextDueAt && new Date(d.nextDueAt) <= new Date() ? 'overdue' : '';
                return `
                <div class="timeline-item ${dueClass}">
                    <div class="timeline-dot"><i data-lucide="worm"></i></div>
                    <div class="timeline-content">
                        <h4>${d.type || '驱虫'} · ${d.medicine || '-'}</h4>
                        <p class="timeline-date">驱虫: ${d.dewormedAt || '-'}</p>
                        ${d.nextDueAt ? `<p class="timeline-due">下次: ${d.nextDueAt}</p>` : ''}
                        ${d.notes ? `<p class="timeline-meta">备注: ${d.notes}</p>` : ''}
                        <div class="timeline-actions">
                            <button class="btn-tiny btn-secondary" onclick="showRecordFormModal('${pet.id}','deworming',${i})"><i data-lucide="pen-square"></i></button>
                            <button class="btn-tiny btn-danger" onclick="deleteRecord('${pet.id}','deworming',${i})"><i data-lucide="trash-2"></i></button>
                        </div>
                    </div>
                </div>`;
            }).join('')}</div>`}
    `;

    // 体检记录
    const checkups = pet.checkups || [];
    const checkupList = `
        <div class="tab-toolbar">
            <button class="btn btn-primary btn-tiny" onclick="showRecordFormModal('${pet.id}','checkup')">添加体检</button>
        </div>
        ${checkups.length === 0
            ? `<p class="empty-hint">暂无体检记录</p>`
            : `<div class="timeline">${checkups.map((c, i) => `
                <div class="timeline-item">
                    <div class="timeline-dot"><i data-lucide="heart-pulse"></i></div>
                    <div class="timeline-content">
                        <h4>${c.clinic || '体检'}</h4>
                        <p class="timeline-date">日期: ${c.checkedAt || '-'}</p>
                        ${c.vetName ? `<p class="timeline-meta">兽医: ${c.vetName}</p>` : ''}
                        ${c.result ? `<p class="timeline-meta">结果: ${c.result}</p>` : ''}
                        ${c.abnormalItems && c.abnormalItems.length ? `<p class="timeline-meta"><i data-lucide="alert-triangle"></i> 异常: ${c.abnormalItems.join(', ')}</p>` : ''}
                        <div class="timeline-actions">
                            <button class="btn-tiny btn-secondary" onclick="showRecordFormModal('${pet.id}','checkup',${i})"><i data-lucide="pen-square"></i></button>
                            <button class="btn-tiny btn-danger" onclick="deleteRecord('${pet.id}','checkup',${i})"><i data-lucide="trash-2"></i></button>
                        </div>
                    </div>
                </div>`).join('')}</div>`}
    `;

    // 就诊记录
    const visits = pet.medicalVisits || [];
    const visitList = `
        <div class="tab-toolbar">
            <button class="btn btn-primary btn-tiny" onclick="showRecordFormModal('${pet.id}','medicalVisit')">添加就诊</button>
        </div>
        ${visits.length === 0
            ? `<p class="empty-hint">暂无就诊记录</p>`
            : `<div class="timeline">${visits.map((v, i) => `
                <div class="timeline-item">
                    <div class="timeline-dot"><i data-lucide="hospital"></i></div>
                    <div class="timeline-content">
                        <h4>${v.reason || '就诊'}</h4>
                        <p class="timeline-date">日期: ${v.visitedAt || '-'}</p>
                        ${v.diagnosis ? `<p class="timeline-meta">诊断: ${v.diagnosis}</p>` : ''}
                        ${v.treatment ? `<p class="timeline-meta">治疗: ${v.treatment}</p>` : ''}
                        <div class="timeline-actions">
                            <button class="btn-tiny btn-secondary" onclick="showRecordFormModal('${pet.id}','medicalVisit',${i})"><i data-lucide="pen-square"></i></button>
                            <button class="btn-tiny btn-danger" onclick="deleteRecord('${pet.id}','medicalVisit',${i})"><i data-lucide="trash-2"></i></button>
                        </div>
                    </div>
                </div>`).join('')}</div>`}
    `;

    const html = `
        <div class="modal pet-detail-modal" id="pet-detail-modal">
            <div class="modal-content modal-content-wide">
                <button class="modal-close" onclick="closeModal('pet-detail-modal')">✕</button>
                ${basicInfo}
                <div class="tab-headers">
                    <button class="tab-btn active" onclick="switchPetTab(event, 'tab-vaccines')"><i data-lucide="syringe"></i> 疫苗 (${vaccines.length})</button>
                    <button class="tab-btn" onclick="switchPetTab(event, 'tab-dewormings')"><i data-lucide="worm"></i> 驱虫 (${dewormings.length})</button>
                    <button class="tab-btn" onclick="switchPetTab(event, 'tab-checkups')"><i data-lucide="heart-pulse"></i> 体检 (${checkups.length})</button>
                    <button class="tab-btn" onclick="switchPetTab(event, 'tab-visits')"><i data-lucide="hospital"></i> 就诊 (${visits.length})</button>
                </div>
                <div class="tab-content active" id="tab-vaccines">${vaccineTimeline}</div>
                <div class="tab-content" id="tab-dewormings">${dewormingList}</div>
                <div class="tab-content" id="tab-checkups">${checkupList}</div>
                <div class="tab-content" id="tab-visits">${visitList}</div>
            </div>
        </div>`;
    openModal(html);
}

function switchPetTab(evt, tabId) {
    // 只在当前弹窗范围内切换，避免影响其它弹窗的 tab
    const modal = document.getElementById('pet-detail-modal');
    if (!modal) return;
    modal.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    modal.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
    // Tab 按钮内含 Lucide 图标，点击图标时 evt.target 会指向 svg，必须用 currentTarget 定位到按钮本身
    evt.currentTarget.classList.add('active');
    modal.querySelector(`#${tabId}`).classList.add('active');
}

// ===================== 健康 record CRUD（疫苗/驱虫/体检/就诊） =====================
const RECORD_FIELD_MAP = {
    vaccine:       { listKey: 'vaccines',     title: '疫苗', icon: 'syringe' },
    deworming:     { listKey: 'dewormings',   title: '驱虫', icon: 'worm' },
    checkup:       { listKey: 'checkups',     title: '体检', icon: 'heart-pulse' },
    medicalVisit:  { listKey: 'medicalVisits',title: '就诊', icon: 'hospital' },
};

function showRecordFormModal(petId, recordType, recordIdx) {
    const meta = RECORD_FIELD_MAP[recordType];
    if (!meta) { showToast('未知记录类型', 'error'); return; }

    const pet = AppState.petCache.find(p => p.id === petId);
    if (!pet) { showToast('未找到宠物', 'error'); return; }

    const list = pet[meta.listKey] || [];
    const isEdit = recordIdx != null && recordIdx >= 0 && recordIdx < list.length;
    const rec = isEdit ? list[recordIdx] : {};

    let fieldsHtml = '';
    if (recordType === 'vaccine') {
        fieldsHtml = `
            <div class="form-group"><label>疫苗名称 *</label><input id="rf-name" type="text" value="${rec.name || ''}" placeholder="如：猫三联" /></div>
            <div class="form-row">
                <div class="form-group"><label>接种日期</label><input id="rf-vaccinatedAt" type="date" value="${rec.vaccinatedAt || ''}" /></div>
                <div class="form-group"><label>下次到期</label><input id="rf-nextDueAt" type="date" value="${rec.nextDueAt || ''}" /></div>
            </div>
            <div class="form-group"><label>接种医院</label><input id="rf-vetClinic" type="text" value="${rec.vetClinic || ''}" /></div>
            <div class="form-group"><label>备注</label><input id="rf-notes" type="text" value="${rec.notes || ''}" /></div>`;
    } else if (recordType === 'deworming') {
        fieldsHtml = `
            <div class="form-row">
                <div class="form-group"><label>驱虫类型</label>
                    <select id="rf-type">
                        <option value="体内驱虫" ${rec.type === '体内驱虫' ? 'selected' : ''}>体内驱虫</option>
                        <option value="体外驱虫" ${rec.type === '体外驱虫' ? 'selected' : ''}>体外驱虫</option>
                        <option value="体内外驱虫" ${rec.type === '体内外驱虫' ? 'selected' : ''}>体内外驱虫</option>
                    </select>
                </div>
                <div class="form-group"><label>药品名</label><input id="rf-medicine" type="text" value="${rec.medicine || ''}" placeholder="如：拜宠清" /></div>
            </div>
            <div class="form-row">
                <div class="form-group"><label>驱虫日期</label><input id="rf-dewormedAt" type="date" value="${rec.dewormedAt || ''}" /></div>
                <div class="form-group"><label>下次到期</label><input id="rf-nextDueAt" type="date" value="${rec.nextDueAt || ''}" /></div>
            </div>
            <div class="form-group"><label>备注</label><input id="rf-notes" type="text" value="${rec.notes || ''}" /></div>`;
    } else if (recordType === 'checkup') {
        fieldsHtml = `
            <div class="form-row">
                <div class="form-group"><label>体检日期</label><input id="rf-checkedAt" type="date" value="${rec.checkedAt || ''}" /></div>
                <div class="form-group"><label>医院</label><input id="rf-clinic" type="text" value="${rec.clinic || ''}" /></div>
            </div>
            <div class="form-group"><label>兽医姓名</label><input id="rf-vetName" type="text" value="${rec.vetName || ''}" /></div>
            <div class="form-group"><label>检查结果</label><textarea id="rf-result" rows="2" placeholder="如：一切正常，体重 5.8kg">${rec.result || ''}</textarea></div>
            <div class="form-group"><label>异常项（逗号分隔）</label><input id="rf-abnormalItems" type="text" value="${(rec.abnormalItems || []).join(', ')}" placeholder="如：牙齿结石, 皮肤红点" /></div>`;
    } else if (recordType === 'medicalVisit') {
        fieldsHtml = `
            <div class="form-group"><label>就诊日期</label><input id="rf-visitedAt" type="date" value="${rec.visitedAt || ''}" /></div>
            <div class="form-group"><label>就诊原因 *</label><input id="rf-reason" type="text" value="${rec.reason || ''}" placeholder="如：食欲下降" /></div>
            <div class="form-group"><label>诊断结果</label><textarea id="rf-diagnosis" rows="2">${rec.diagnosis || ''}</textarea></div>
            <div class="form-group"><label>治疗方案</label><textarea id="rf-treatment" rows="2">${rec.treatment || ''}</textarea></div>`;
    }

    const html = `
        <div class="modal" id="record-form-modal">
            <div class="modal-content">
                <button class="modal-close" onclick="closeModal('record-form-modal')">✕</button>
                <h3><i data-lucide="${meta.icon}"></i> ${isEdit ? '编辑' : '添加'}${meta.title}记录</h3>
                <p class="pet-meta">宠物：${pet.name}</p>
                <input type="hidden" id="rf-petId" value="${petId}" />
                <input type="hidden" id="rf-type-key" value="${recordType}" />
                <input type="hidden" id="rf-idx" value="${isEdit ? recordIdx : -1}" />
                ${fieldsHtml}
                <div class="modal-actions">
                    <button class="btn btn-secondary" onclick="closeModal('record-form-modal')">取消</button>
                    <button class="btn btn-primary" onclick="submitRecordForm()">${isEdit ? '保存修改' : '添加记录'}</button>
                </div>
            </div>
        </div>`;
    openModal(html);
}

async function submitRecordForm() {
    const petId    = document.getElementById('rf-petId').value;
    const typeKey  = document.getElementById('rf-type-key').value;
    const idx      = parseInt(document.getElementById('rf-idx').value, 10);
    const meta = RECORD_FIELD_MAP[typeKey];
    if (!meta) return;

    // 构造记录对象
    let rec = {};
    if (typeKey === 'vaccine') {
        rec = {
            name: val('rf-name'),
            vaccinatedAt: val('rf-vaccinatedAt'),
            nextDueAt: val('rf-nextDueAt'),
            vetClinic: val('rf-vetClinic'),
            notes: val('rf-notes'),
        };
        if (!rec.name) { showToast('疫苗名称不能为空', 'error'); return; }
    } else if (typeKey === 'deworming') {
        rec = {
            type: val('rf-type'),
            medicine: val('rf-medicine'),
            dewormedAt: val('rf-dewormedAt'),
            nextDueAt: val('rf-nextDueAt'),
            notes: val('rf-notes'),
        };
    } else if (typeKey === 'checkup') {
        const abnormal = val('rf-abnormalItems').split(/[,，]/).map(s => s.trim()).filter(Boolean);
        rec = {
            checkedAt: val('rf-checkedAt'),
            vetName: val('rf-vetName'),
            clinic: val('rf-clinic'),
            result: val('rf-result'),
            abnormalItems: abnormal,
        };
    } else if (typeKey === 'medicalVisit') {
        rec = {
            visitedAt: val('rf-visitedAt'),
            reason: val('rf-reason'),
            diagnosis: val('rf-diagnosis'),
            treatment: val('rf-treatment'),
        };
        if (!rec.reason) { showToast('就诊原因不能为空', 'error'); return; }
    }

    // 从缓存取最新宠物数据，修改对应列表，整体 PUT
    const pet = AppState.petCache.find(p => p.id === petId);
    if (!pet) { showToast('宠物不存在', 'error'); return; }

    const list = [...(pet[meta.listKey] || [])];
    if (idx >= 0) {
        list[idx] = rec;                                  // 编辑
    } else {
        list.push(rec);                                  // 新增
    }

    try {
        await apiPut(`/api/pets/${petId}`, { [meta.listKey]: list });
        showToast(`${meta.title}记录已${idx >= 0 ? '更新' : '添加'}`);
        closeModal('record-form-modal');
        await refreshPetDetail(petId);
    } catch (e) {
        showToast('保存失败：' + e.message, 'error');
    }
}

async function deleteRecord(petId, recordType, recordIdx) {
    const meta = RECORD_FIELD_MAP[recordType];
    if (!meta) return;
    if (!confirm(`确认删除这条${meta.title}记录？`)) return;

    const pet = AppState.petCache.find(p => p.id === petId);
    if (!pet) return;

    const list = (pet[meta.listKey] || []).filter((_, i) => i !== recordIdx);
    try {
        await apiPut(`/api/pets/${petId}`, { [meta.listKey]: list });
        showToast(`${meta.title}记录已删除`);
        await refreshPetDetail(petId);
    } catch (e) {
        showToast('删除失败：' + e.message, 'error');
    }
}

// 重新拉取宠物详情并刷新弹窗
async function refreshPetDetail(petId) {
    try {
        const updated = await apiGet(`/api/pets/${petId}`);
        // 同步更新缓存
        const idx = AppState.petCache.findIndex(p => p.id === petId);
        if (idx >= 0) AppState.petCache[idx] = updated;
        else AppState.petCache.push(updated);

        // 同步刷新列表卡片
        loadPets();

        // 如果详情弹窗还开着，重新渲染
        if (document.getElementById('pet-detail-modal')) {
            closeModal('pet-detail-modal');
            showPetDetail(petId);
        }
    } catch (e) {
        console.warn('刷新宠物详情失败：', e);
    }
}

function val(id) {
    const el = document.getElementById(id);
    return el ? el.value.trim() : '';
}

function calcAge(birthday) {
    const b = new Date(birthday);
    const now = new Date();
    const years = now.getFullYear() - b.getFullYear();
    const months = now.getMonth() - b.getMonth();
    const totalMonths = years * 12 + months;
    if (totalMonths < 12) return `${Math.max(0, totalMonths)} 个月`;
    return `${years} 岁${months > 0 ? ' ' + months + ' 个月' : ''}`;
}

// ===================== 健康记录 + ECharts 趋势图 =====================
// ===================== 健康记录添加 =====================

// 记录类型 → 默认单位 / 数值步长 映射
const HR_TYPE_UNITS = {
    '体重': { unit: 'kg',    step: '0.1',  placeholder: '例如：4.5' },
    '体温': { unit: '℃',    step: '0.1',  placeholder: '例如：38.5' },
    '心率': { unit: '次/分', step: '1',    placeholder: '例如：120' },
    '饮食': { unit: 'g',     step: '1',    placeholder: '例如：150' },
    '排便': { unit: '次',    step: '1',    placeholder: '例如：2' },
    '运动': { unit: '分钟',  step: '1',    placeholder: '例如：30' },
    '其他': { unit: '',      step: '0.01', placeholder: '自定义数值' }
};

async function showHealthRecordFormModal(recordId = null) {
    if (!AppState.currentUser) { showToast('请先登录后添加记录', 'error'); showLoginModal(); return; }

    // 宠物缓存兜底
    if (!Array.isArray(AppState.petCache) || AppState.petCache.length === 0) {
        try { AppState.petCache = await apiGet('/api/pets'); } catch (e) { AppState.petCache = []; }
    }
    const pets = AppState.petCache || [];
    if (pets.length === 0) { showToast('请先在宠物档案页添加宠物', 'error'); return; }

    // 编辑模式：拉取记录回填数据
    let record = null;
    if (recordId) {
        try {
            record = await apiGet(`/api/health-records/${recordId}`);
        } catch (e) {
            showToast('加载记录失败：' + e.message, 'error');
            return;
        }
        if (!record) { showToast('健康记录不存在', 'error'); return; }
    }
    const isEdit = !!record;

    // 默认选中：编辑用记录的宠物，否则用健康记录页当前选中的宠物
    const currentPetId = record?.petId || document.getElementById('hr-pet-select')?.value || pets[0].id;
    const petOptions = pets.map(p =>
        `<option value="${p.id}" ${p.id === currentPetId ? 'selected' : ''}>${p.name} · ${p.species || ''} · ${p.breed || ''}</option>`).join('');

    // 默认记录时间 = 编辑用记录时间，否则现在（datetime-local 格式）
    const pad = n => String(n).padStart(2, '0');
    const toLocalInput = d => `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
    const defaultTime = record?.recordedAt ? toLocalInput(new Date(record.recordedAt)) : toLocalInput(new Date());

    const typeOptions = Object.keys(HR_TYPE_UNITS).map(t =>
        `<option value="${t}" ${record?.recordType === t ? 'selected' : ''}>${t}</option>`).join('');
    const editValue = record?.value?.value != null ? record.value.value : '';
    const editUnit  = record?.value?.unit ?? (HR_TYPE_UNITS[record?.recordType]?.unit ?? 'kg');

    const html = `
        <div class="modal" id="hr-form-modal">
            <div class="modal-content">
                <button class="modal-close" onclick="closeModal('hr-form-modal')">✕</button>
                <h3>${isEdit ? '编辑健康记录' : '添加健康记录'}</h3>
                <input type="hidden" id="hrf-id" value="${isEdit ? record.id : ''}">
                <div class="form-row">
                    <div class="form-group" style="flex:1">
                        <label>宠物 <span style="color:var(--danger)">*</span></label>
                        <select id="hrf-pet">${petOptions}</select>
                    </div>
                    <div class="form-group" style="flex:1">
                        <label>记录类型 <span style="color:var(--danger)">*</span></label>
                        <select id="hrf-type" onchange="onHrTypeChange()">${typeOptions}</select>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group" style="flex:1">
                        <label>数值 <span style="color:var(--danger)">*</span></label>
                        <input id="hrf-value" type="number" step="0.1" value="${editValue}" placeholder="例如：4.5">
                    </div>
                    <div class="form-group" style="flex:1">
                        <label>单位</label>
                        <input id="hrf-unit" type="text" value="${editUnit}" maxlength="10">
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group" style="flex:1">
                        <label>记录时间 <span style="color:var(--danger)">*</span></label>
                        <input id="hrf-time" type="datetime-local" value="${defaultTime}">
                    </div>
                </div>
                <div class="form-group">
                    <label>备注</label>
                    <textarea id="hrf-notes" rows="2" maxlength="200" placeholder="可选，例如：饭后两小时测量（最多200字）">${record?.notes || ''}</textarea>
                </div>
                <div class="modal-actions">
                    <button class="btn" onclick="closeModal('hr-form-modal')">取消</button>
                    <button class="btn btn-primary" onclick="submitHealthRecord()">${isEdit ? '保存修改' : '保存记录'}</button>
                </div>
            </div>
        </div>`;
    openModal(html);
}

// 切换记录类型时联动默认单位与步长
function onHrTypeChange() {
    const conf = HR_TYPE_UNITS[document.getElementById('hrf-type')?.value];
    if (conf) {
        document.getElementById('hrf-unit').value = conf.unit;
        document.getElementById('hrf-value').step = conf.step;
        document.getElementById('hrf-value').placeholder = conf.placeholder;
    }
}

async function submitHealthRecord() {
    const id     = val('hrf-id');
    const petId  = val('hrf-pet');
    const type   = val('hrf-type');
    const rawVal = val('hrf-value').trim();
    const unit   = val('hrf-unit').trim();
    const time   = val('hrf-time');
    const notes  = val('hrf-notes').trim();

    if (!petId)  { showToast('请选择宠物', 'error'); return; }
    if (!type)   { showToast('请选择记录类型', 'error'); return; }
    if (rawVal === '' || isNaN(Number(rawVal))) { showToast('请填写有效数值', 'error'); return; }
    if (!time)   { showToast('请选择记录时间', 'error'); return; }

    const payload = {
        petId,
        ownerId: AppState.currentUser.id,
        recordType: type,
        value: { value: Number(rawVal), ...(unit ? { unit } : {}) },
        recordedAt: time,          // datetime-local 本地时间，后端 LocalDateTime 直接解析
        notes: notes || null
    };

    try {
        if (id) {
            await apiPut(`/api/health-records/${id}`, payload);
            showToast('健康记录已更新');
        } else {
            await apiPost('/api/health-records', payload);
            showToast('健康记录已添加');
        }
        closeModal('hr-form-modal');
        // 同步健康记录页宠物下拉并刷新趋势 + 列表
        const sel = document.getElementById('hr-pet-select');
        if (sel) sel.value = petId;
        await loadHealthTrend();
    } catch (e) {
        showToast((id ? '更新失败：' : '添加失败：') + (e.message || '未知错误'), 'error');
    }
}

async function deleteHealthRecord(recordId) {
    if (!recordId) return;
    if (!confirm('确认删除这条健康记录？删除后不可恢复。')) return;
    try {
        await apiDelete(`/api/health-records/${recordId}`);
        showToast('健康记录已删除');
        closeModal('hr-detail-modal');  // 若从详情弹窗进入，一并关闭
        await loadHealthTrend();
    } catch (e) {
        showToast('删除失败：' + (e.message || '未知错误'), 'error');
    }
}

async function loadHealthRecords() {
    // 1. 同步宠物下拉框选项
    const sel = document.getElementById('hr-pet-select');
    if (sel) {
        const cached = AppState.petCache || [];
        if (cached.length === 0) {
            try { AppState.petCache = await apiGet('/api/pets'); } catch (e) { /* 忽略 */ }
        }
        const pets = AppState.petCache || [];
        sel.innerHTML = pets.length === 0
            ? `<option value="">（请先在宠物档案页添加宠物）</option>`
            : `<option value="">请选择宠物</option>` + pets.map(p =>
                `<option value="${p.id}">${p.name} · ${p.species || ''} · ${p.breed || ''}</option>`).join('');

        // 默认选第一只宠物
        if (pets.length > 0 && !sel.value) sel.value = pets[0].id;
    }

    // 2. 渲染趋势 + 记录列表
    await loadHealthTrend();
}

async function loadHealthTrend() {
    const petSelect = document.getElementById('hr-pet-select');
    const periodSel = document.getElementById('hr-period-select');
    const chartDom   = document.getElementById('trend-chart');
    const summaryDom = document.getElementById('hr-stats-summary');
    const listDom    = document.getElementById('health-records-list');

    const petId = petSelect?.value || '';

    // 没选宠物时给个空状态
    if (!petId) {
        if (chartDom) chartDom.innerHTML = `<div class="empty-hint">请先选择宠物</div>`;
        if (summaryDom) summaryDom.innerHTML = '';
        if (listDom) listDom.innerHTML = `<div class="empty-hint">暂无记录</div>`;
        return;
    }

    const period = periodSel?.value || 'weekly';
    const pet = (AppState.petCache || []).find(p => p.id === petId);
    const petName = pet?.name || '宠物';

    // 1. 拉取趋势统计 + 记录列表
    let stats = null, records = [];
    try {
        stats = await apiGet(`/api/health-records/pet/${petId}/trends?period=${period}`);
    } catch (e) { console.warn('趋势统计加载失败：', e); }

    try {
        records = await apiGet(`/api/health-records/pet/${petId}`);
    } catch (e) { console.warn('健康记录加载失败：', e); }

    // 缓存最近一次加载的记录，供指标筛选切换时复用（避免重新请求）
    AppState.hrLastRecords = records;

    // 2. 渲染统计摘要
    if (summaryDom) {
        summaryDom.innerHTML = renderStatsSummary(stats, petName, period);
    }

    // 3. 渲染趋势图
    if (chartDom && window.echarts) {
        renderHealthTrendChart(chartDom, stats, records, petName, period);
    }

    // 4. 渲染记录列表
    if (listDom) {
        listDom.innerHTML = (records && records.length)
            ? records.map(r => renderHealthRecordCard(r)).join('')
            : `<div class="empty-hint">暂无健康记录</div>`;
    }
}

function renderStatsSummary(stats, petName, period) {
    if (!stats) return `<div class="hr-stat-card">统计暂不可用</div>`;
    const periodText = period === 'monthly' ? '本月' : '本周';
    const trendText  = { up: '↑ 上升', down: '↓ 下降', stable: '→ 平稳' };

    const cards = [];
    cards.push(`<div class="hr-stat-card"><span class="hr-stat-label">${petName} · ${periodText}</span><b>${stats.recordCount || 0}</b><span class="hr-stat-sub">条记录</span></div>`);
    if (stats.weightAvg != null) {
        cards.push(`<div class="hr-stat-card"><span class="hr-stat-label">体重均值</span><b>${stats.weightAvg} <small>kg</small></b><span class="hr-stat-sub">范围 ${stats.weightMin}~${stats.weightMax} · ${trendText[stats.weightTrend] || '-'}</span></div>`);
    }
    if (stats.tempAvg != null) {
        cards.push(`<div class="hr-stat-card"><span class="hr-stat-label">体温均值</span><b>${stats.tempAvg} <small>℃</small></b><span class="hr-stat-sub">范围 ${stats.tempMin}~${stats.tempMax}</span></div>`);
    }
    if (stats.recordTypes && stats.recordTypes.length) {
        cards.push(`<div class="hr-stat-card"><span class="hr-stat-label">记录类型</span><b>${stats.recordTypes.join(' / ')}</b><span class="hr-stat-sub">${stats.period === 'empty' ? '该周期内暂无记录' : '已聚合'}</span></div>`);
    }
    return cards.join('');
}

function renderHealthRecordCard(r) {
    const time = r.recordedAt ? new Date(r.recordedAt).toLocaleString('zh-CN') : '-';
    const type = r.recordType || '记录';
    const value = r.value ? (r.value.value != null ? r.value.value : JSON.stringify(r.value)) : '-';
    const unit  = r.value?.unit ? ` ${r.value.unit}` : '';
    return `
        <div class="hr-record-card card-glow" onclick="showHealthRecordDetail('${r.id}')">
            <div class="hr-record-icon">${hrIcon(type)}</div>
            <div class="hr-record-body">
                <h4>${type} <span class="hr-record-value">${value}${unit}</span></h4>
                <p class="hr-record-time">${time}</p>
                ${r.notes ? `<p class="hr-record-notes">${r.notes}</p>` : ''}
            </div>
            <div class="hr-record-actions">
                <button class="btn btn-tiny btn-secondary" onclick="event.stopPropagation(); showHealthRecordFormModal('${r.id}')">编辑</button>
                <button class="btn btn-tiny btn-danger" onclick="event.stopPropagation(); deleteHealthRecord('${r.id}')">删除</button>
            </div>
        </div>`;
}

function hrIcon(type) {
    const t = (type || '').toString();
    if (t.includes('体重')) return '<i data-lucide="weight"></i>';
    if (t.includes('体温')) return '<i data-lucide="thermometer"></i>';
    if (t.includes('心率') || t.includes('脉搏')) return '<i data-lucide="heart"></i>';
    if (t.includes('饮食') || t.includes('食欲')) return '<i data-lucide="utensils"></i>';
    if (t.includes('排便') || t.includes('排泄')) return '<i data-lucide="poop"></i>';
    if (t.includes('运动') || t.includes('活动')) return '<i data-lucide="dumbbell"></i>';
    if (t.includes('疫苗')) return '<i data-lucide="syringe"></i>';
    if (t.includes('驱虫')) return '<i data-lucide="worm"></i>';
    if (t.includes('就诊')) return '<i data-lucide="hospital"></i>';
    if (t.includes('体检')) return '<i data-lucide="heart-pulse"></i>';
    return '<i data-lucide="activity"></i>';
}

function renderHealthTrendChart(dom, stats, records, petName, period) {
    // 重建图表实例，避免残留旧配置
    const old = echarts.getInstanceByDom(dom);
    if (old) old.dispose();
    dom.innerHTML = '';

    // 1. 按统计周期过滤记录（weekly=近7天 / monthly=近30天）
    const rangeDays = period === 'monthly' ? 30 : 7;
    const startTs = Date.now() - rangeDays * 24 * 3600 * 1000;

    // 2. 按记录类型分组，提取"时间-数值"序列（体重、体温、心率等健康指标的实际数值）
    const typeMap = {};
    (records || []).forEach(r => {
        const v = r.value?.value;
        if (v == null || isNaN(Number(v)) || !r.recordedAt) return;
        const ts = new Date(r.recordedAt).getTime();
        if (ts < startTs) return;
        const type = r.recordType || '其他';
        const unit = r.value?.unit || '';
        (typeMap[type] = typeMap[type] || { unit, points: [] }).points.push([ts, Number(v)]);
    });

    const allTypes = Object.keys(typeMap);

    // 3. 渲染指标筛选标签
    const filterDom = document.getElementById('hr-chart-filters');
    if (filterDom && allTypes.length > 0) {
        // 用"取消集合"跟踪用户手动关闭的指标；默认全选，切换宠物/周期后保留用户选择
        if (!AppState.hrDeselectedTypes) AppState.hrDeselectedTypes = new Set();
        const deselected = AppState.hrDeselectedTypes;
        // 清理已不存在的类型
        [...deselected].forEach(t => { if (!allTypes.includes(t)) deselected.delete(t); });

        filterDom.innerHTML = allTypes.map(t => {
            const active = !deselected.has(t);
            const unit = typeMap[t].unit;
            return `<button class="hr-type-tag ${active ? 'active' : ''}" onclick="toggleHrType('${t}')">${t}${unit ? ` (${unit})` : ''}</button>`;
        }).join('');
    }

    // 筛选后的类型：未被用户取消的
    const deselected = AppState.hrDeselectedTypes || new Set();
    const types = allTypes.filter(t => !deselected.has(t));
    if (allTypes.length === 0) {
        if (filterDom) filterDom.innerHTML = '';
        dom.innerHTML = `<div class="empty-hint">该周期内暂无数值型健康记录（体重、体温等）</div>`;
        return;
    }
    if (types.length === 0) {
        dom.innerHTML = `<div class="empty-hint">请至少选择一个指标进行展示</div>`;
        return;
    }

    // 4. 每种指标一条趋势线；量纲不同，各配独立 y 轴（带单位）
    const COLORS = ['#3873B6', '#E6A23C', '#67C23A', '#8B5CF6', '#F56C6C', '#14B8A6'];
    const series = [], yAxes = [];
    types.forEach((type, i) => {
        const { unit, points } = typeMap[type];
        points.sort((a, b) => a[0] - b[0]);
        yAxes.push({
            type: 'value', scale: true,
            name: unit,
            nameTextStyle: { color: '#6DA1D8' },
            position: i === 0 ? 'left' : 'right',
            offset: i === 0 ? 0 : (i - 1) * 52,
            axisLine: { show: false },
            splitLine: { show: i === 0 }
        });
        series.push({
            name: unit ? `${type} (${unit})` : type,
            type: 'line', yAxisIndex: i, data: points,
            smooth: true, symbol: 'circle', symbolSize: 7,
            itemStyle: { color: COLORS[i % COLORS.length] },
            lineStyle: { width: 2.5 },
            ...(types.length === 1 ? { areaStyle: { color: 'rgba(56,115,182,0.08)' } } : {})
        });
    });

    const chart = echarts.init(dom);
    chart.setOption({
        title: { text: `${petName} · 健康指标趋势`, left: 'center' },
        tooltip: { trigger: 'axis' },
        legend: { data: series.map(s => s.name), top: 25 },
        grid: { left: 60, right: 45 + Math.max(0, types.length - 1) * 52, top: 70, bottom: 40 },
        xAxis: { type: 'time', axisLabel: { hideOverlap: true } },
        yAxis: yAxes,
        series
    });

    window.addEventListener('resize', () => echarts.getInstanceByDom(dom)?.resize());
}

/**
 * 切换健康指标筛选：点击标签后重新渲染图表（不重新请求数据）
 */
function toggleHrType(type) {
    if (!AppState.hrDeselectedTypes) AppState.hrDeselectedTypes = new Set();
    const deselected = AppState.hrDeselectedTypes;
    // 切换取消状态
    if (deselected.has(type)) deselected.delete(type);
    else deselected.add(type);

    // 更新标签 active 状态
    const filterDom = document.getElementById('hr-chart-filters');
    if (filterDom) {
        filterDom.querySelectorAll('.hr-type-tag').forEach(btn => {
            const t = btn.textContent.replace(/ \([^)]*\)$/, '');
            btn.classList.toggle('active', !deselected.has(t));
        });
    }

    // 重新渲染图表（用缓存数据，无需重新请求）
    const chartDom = document.getElementById('trend-chart');
    const period = document.getElementById('hr-period-select')?.value || 'weekly';
    const petId = document.getElementById('hr-pet-select')?.value || '';
    const pet = (AppState.petCache || []).find(p => p.id === petId);
    const petName = pet?.name || '宠物';
    renderHealthTrendChart(chartDom, null, AppState.hrLastRecords || [], petName, period);
}

async function showHealthRecordDetail(recordId) {
    let r;
    try {
        r = await apiGet(`/api/health-records/${recordId}`);
    } catch (e) {
        showToast('加载记录失败：' + e.message, 'error');
        return;
    }
    if (!r) return;

    const time = r.recordedAt ? new Date(r.recordedAt).toLocaleString('zh-CN') : '-';
    const type = r.recordType || '记录';
    const value = r.value?.value != null ? r.value.value : (r.value ? JSON.stringify(r.value, null, 2) : '-');
    const unit  = r.value?.unit ? ` ${r.value.unit}` : '';

    const html = `
        <div class="modal" id="hr-detail-modal">
            <div class="modal-content">
                <button class="modal-close" onclick="closeModal('hr-detail-modal')">✕</button>
                <h3>${type}</h3>
                <p class="pet-meta">记录时间：${time}</p>
                <p class="pet-meta">数值：<b>${value}${unit}</b></p>
                ${r.notes ? `<p class="pet-desc">${r.notes}</p>` : ''}
                <div class="modal-actions">
                    <button class="btn btn-secondary" onclick="closeModal('hr-detail-modal')">关闭</button>
                </div>
            </div>
        </div>`;
    openModal(html);
}

// 兼容旧入口（保留旧函数名，避免外部调用断裂）
function renderSampleTrendChart(dom) {
    renderHealthTrendChart(dom, null, [], '示例');
}

// ===================== AI 诊断 =====================
// 文本安全转义工具（LLM 自由文本渲染用）
function escHtml(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g,
        c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function nl2br(s) {
    return escHtml(s).replace(/\n/g, '<br>');
}

async function submitDiagnosis() {
    const petId = document.getElementById('diag-pet-select')?.value || '';
    const symptoms = document.getElementById('diag-symptoms')?.value || '';
    const duration = document.getElementById('diag-duration')?.value || '1天';
    const resultBox = document.getElementById('ai-result');

    if (!symptoms.trim()) {
        showToast('请描述症状哦', 'error');
        return;
    }

    resultBox.innerHTML = '<p class="loading">AI 正在分析中...</p>';

    // 从宠物缓存中取出选中宠物的真实信息（兜底示例数据，避免空请求）
    const pet = (AppState.petCache || []).find(p => p.id === petId) || {};
    const species    = pet.species   || 'CAT';
    const breed      = pet.breed     || '未知';
    const ageMonths  = pet.ageMonths != null ? pet.ageMonths : 12;

    try {
        const data = await apiPost('/api/ai-diagnosis', {
            petId, species, breed, ageMonths,
            symptoms, duration
        });
        const parsed = typeof data === 'string' ? JSON.parse(data) : data;
        const causes   = parsed.possibleCauses;      // 规则引擎=数组；LLM=整段文本
        const redFlags = parsed.redFlags || parsed.dangerSignals;
        const redArr   = Array.isArray(redFlags) ? redFlags : (redFlags ? [redFlags] : []);
        const suggArr  = Array.isArray(parsed.suggestions) ? parsed.suggestions : (parsed.suggestions ? [parsed.suggestions] : []);
        const isRule   = Array.isArray(causes);

        let html = '<div class="ai-result-card">';
        if (isRule) {
            html += '<h4>可能原因：</h4><ul>'
                 + (causes.map(c => `<li><b>${escHtml(c.name)}</b>（概率：${c.probability ?? '-'}）— ${escHtml(c.description || '')}</li>`).join('') || '')
                 + '</ul>';
            html += '<h4>建议：</h4><ol>' + (suggArr.map(s => `<li>${escHtml(s)}</li>`).join('') || '') + '</ol>';
        } else {
            // LLM 自由文本：整段展示
            html += '<h4>AI 分析结果：</h4><div class="ai-text-block">' + nl2br(causes || 'AI 未返回内容') + '</div>';
            if (suggArr.length) html += '<p class="ai-text-sub">' + escHtml(suggArr.join('；')) + '</p>';
        }
        if (redArr.length) {
            html += '<h4>危险信号：</h4><ul>' + redArr.map(s => `<li class="red-flag">${escHtml(s)}</li>`).join('') + '</ul>';
        }
        html += `<p class="disclaimer">${escHtml(parsed.disclaimer) || '本建议仅供参考，不能替代兽医诊断'}</p></div>`;
        resultBox.innerHTML = html;
    } catch (e) {
        resultBox.innerHTML = `<p class="empty-hint">AI 调用失败：${escHtml(e.message)}</p>`;
    }
}

// ===================== AI 健康报告 =====================
async function showHealthReportModal() {
    if (!AppState.currentUser) { showToast('请先登录后生成报告', 'error'); showLoginModal(); return; }

    // 宠物缓存兜底
    if (!Array.isArray(AppState.petCache) || AppState.petCache.length === 0) {
        try { AppState.petCache = await apiGet('/api/pets'); } catch (e) { AppState.petCache = []; }
    }
    const pets = AppState.petCache || [];
    if (pets.length === 0) { showToast('请先在宠物档案页添加宠物', 'error'); return; }

    // 默认选中：健康记录页/AI 页当前选中的宠物
    const currentPetId = document.getElementById('diag-pet-select')?.value
        || document.getElementById('hr-pet-select')?.value
        || pets[0].id;
    const petOptions = pets.map(p =>
        `<option value="${p.id}" ${p.id === currentPetId ? 'selected' : ''}>${p.name} · ${p.species || ''} · ${p.breed || ''}</option>`).join('');

    const html = `
        <div class="modal" id="health-report-modal">
            <div class="modal-content">
                <button class="modal-close" onclick="closeModal('health-report-modal')">✕</button>
                <h3>生成健康报告</h3>
                <div class="form-row">
                    <div class="form-group" style="flex:1">
                        <label>选择宠物 <span style="color:var(--danger)">*</span></label>
                        <select id="report-pet-select">${petOptions}</select>
                    </div>
                    <div class="form-group" style="flex:1">
                        <label>统计周期</label>
                        <select id="report-period-select">
                            <option value="weekly" selected>本周</option>
                            <option value="monthly">本月</option>
                        </select>
                    </div>
                </div>
                <div class="modal-actions" style="margin-bottom:1rem">
                    <button class="btn btn-primary" id="report-generate-btn" onclick="generateHealthReport()">生成报告</button>
                </div>
                <div id="report-content"></div>
            </div>
        </div>`;
    openModal(html);
}

async function generateHealthReport() {
    const petId = val('report-pet-select');
    const period = val('report-period-select') || 'weekly';
    const box = document.getElementById('report-content');
    const btn = document.getElementById('report-generate-btn');
    if (!petId) { showToast('请选择宠物', 'error'); return; }

    btn.disabled = true;
    box.innerHTML = '<p class="loading">报告生成中...</p>';
    try {
        const report = await apiGet(
            `/api/ai-diagnosis/report?ownerId=${AppState.currentUser.id}&petId=${petId}&period=${period}`);
        const text = (report || '').trim() || '该周期内暂无健康记录，无法生成报告。';
        box.innerHTML = `
            <div class="report-card">
                <pre class="report-text"></pre>
                <div class="modal-actions">
                    <button class="btn btn-tiny btn-secondary" onclick="copyReportText()">复制报告</button>
                </div>
            </div>`;
        box.querySelector('.report-text').textContent = text;
    } catch (e) {
        box.innerHTML = `<p class="empty-hint">报告生成失败：${e.message}</p>`;
    } finally {
        btn.disabled = false;
    }
}

function copyReportText() {
    const text = document.querySelector('#report-content .report-text')?.textContent || '';
    if (!text) return;
    navigator.clipboard?.writeText(text).then(
        () => showToast('报告已复制到剪贴板'),
        () => showToast('复制失败，请手动选择文本复制', 'error')
    );
}

// ===================== 营养助手 =====================
async function loadNutrition() {
    const sel = document.getElementById('nt-pet-select');
    if (!sel) return;

    // 确保宠物缓存就绪（兜底从接口拉取）
    if (!Array.isArray(AppState.petCache) || AppState.petCache.length === 0) {
        try {
            AppState.petCache = await apiGet('/api/pets');
        } catch (e) {
            console.warn('营养助手加载宠物列表失败：', e);
            AppState.petCache = [];
        }
    }

    const pets = Array.isArray(AppState.petCache) ? AppState.petCache : [];
    if (pets.length === 0) {
        sel.innerHTML = '<option value="">（请先在宠物档案页添加宠物）</option>';
    } else {
        sel.innerHTML = '<option value="">请选择宠物</option>' + pets.map(p => {
            const parts = [p.name, p.species, p.breed].filter(Boolean);
            return `<option value="${p.id}">${parts.join(' · ')}</option>`;
        }).join('');
    }
    await loadNutritionReport();
}

async function loadNutritionReport() {
    const sel = document.getElementById('nt-pet-select');
    const c = document.getElementById('nutrition-container');
    if (!sel || !c) return;

    const petId = sel.value;
    if (!petId) {
        c.innerHTML = '<div class="empty-hint">请选择宠物，系统将根据档案与体重记录自动计算每日营养需求</div>';
        return;
    }

    const bcsEl = document.getElementById('nt-bcs');
    const bcs = bcsEl ? bcsEl.value : '';
    const url = bcs
        ? `/api/nutrition/${petId}?bcs=${encodeURIComponent(bcs)}`
        : `/api/nutrition/${petId}`;

    c.innerHTML = '<div class="empty-hint">正在计算能量需求...</div>';
    try {
        const report = await apiGet(url);
        renderNutritionReport(c, report);
    } catch (e) {
        c.innerHTML = `<div class="empty-hint">营养报告加载失败：${e.message}</div>`;
    }
}

function renderNutritionReport(c, report) {
    const pet = report.pet || {};
    const w = report.weight;
    const calc = report.calculation;
    const neuteredText = pet.neutered == null ? '未设置绝育' : (pet.neutered ? '已绝育' : '未绝育');
    const ageText = pet.ageMonths != null
        ? (pet.ageMonths >= 12
            ? `${Math.floor(pet.ageMonths / 12)} 岁${pet.ageMonths % 12 ? ` ${pet.ageMonths % 12} 个月` : ''}`
            : `${pet.ageMonths} 个月`)
        : '年龄未知';

    const weightHtml = w
        ? `<p class="nutrition-meta">最新体重 <b>${w.value} ${w.unit || 'kg'}</b>` +
          (w.recordedAt ? `（${new Date(w.recordedAt).toLocaleDateString('zh-CN')}）` : '') + `</p>`
        : '';

    if (!calc) {
        c.innerHTML = `
        <div class="card-glow">
            <h3>${pet.name || '宠物'}</h3>
            <p class="nutrition-meta">${pet.species || ''}${pet.breed ? ' · ' + pet.breed : ''} · ${ageText} · ${neuteredText}</p>
            ${weightHtml}
            <p class="empty-hint" style="margin-top:0.75rem">${report.notice || '暂无法生成营养计算'}</p>
        </div>`;
        return;
    }

    const mer = calc.mer;
    const grams0 = Math.round(mer / (380 / 100) * 10) / 10;
    const bcsOpts = [1, 2, 3, 4, 5, 6, 7, 8, 9].map(n =>
        `<option value="${n}" ${calc.bcs === n ? 'selected' : ''}>${n}${n >= 7 ? '（超重倾向）' : n <= 4 ? '（偏瘦）' : '（正常）'}</option>`
    ).join('');

    c.innerHTML = `
        <div class="card-glow nutrition-report">
            <div class="nutrition-head">
                <h3>${pet.name || '宠物'} · 每日营养需求</h3>
                <p class="nutrition-meta">${pet.species || ''}${pet.breed ? ' · ' + pet.breed : ''} · ${ageText} · ${neuteredText}</p>
                ${weightHtml}
            </div>
            <div class="nutrition-stats">
                <div class="nutrition-stat">
                    <span>静息能量 RER</span>
                    <b>${Math.round(calc.rer)} <small>kcal/天</small></b>
                </div>
                <div class="nutrition-stat">
                    <span>阶段系数</span>
                    <b>${calc.merFactor}<small>${calc.stageLabel}</small></b>
                </div>
                <div class="nutrition-stat">
                    <span>每日能量 MER</span>
                    <b>${Math.round(calc.mer)} <small>kcal/天</small></b>
                </div>
            </div>
            <div class="form-group">
                <label>主粮热量密度</label>
                <select id="nt-kcal-select" onchange="updateFeedingGrams()">
                    <option value="340">340 kcal/100g</option>
                    <option value="360">360 kcal/100g</option>
                    <option value="380" selected>380 kcal/100g（常见干粮）</option>
                    <option value="400">400 kcal/100g</option>
                    <option value="420">420 kcal/100g</option>
                </select>
            </div>
            <div class="nutrition-result-row">
                <span>每日建议喂食量</span>
                <b class="nutrition-grams" id="nt-feeding-grams" data-mer="${mer}">${grams0.toFixed(1)} g/天</b>
            </div>
            <div class="form-group">
                <label>体况评分 BCS（1-9）</label>
                <select id="nt-bcs" onchange="loadNutritionReport()">
                    <option value="">未选择（不调整）</option>
                    ${bcsOpts}
                </select>
                ${calc.bcsTargetKcal != null
                    ? `<p class="nutrition-tip">体重管理目标热量：约 <b>${Math.round(calc.bcsTargetKcal)} kcal/天</b>${calc.bcs >= 7 ? '（当前偏重，建议渐进减量并咨询兽医）' : '（当前偏瘦，建议适量增量）'}</p>`
                    : ''}
            </div>
            <div id="nt-trend-chart" class="nutrition-chart"></div>
            ${report.notice ? `<p class="nutrition-tip">${report.notice}</p>` : ''}
            <p class="nutrition-disclaimer">参考 NRC 2006 / WSAVA 通用估算标准，实际需求因个体代谢与活动量而异，请以兽医建议为准。</p>
        </div>`;

    renderWeightTrend(document.getElementById('nt-trend-chart'), report.trend || [], pet.name || '');
}

function updateFeedingGrams() {
    const sel = document.getElementById('nt-kcal-select');
    const gramsEl = document.getElementById('nt-feeding-grams');
    if (!sel || !gramsEl) return;
    const mer = parseFloat(gramsEl.dataset.mer || '0');
    const kcal = parseFloat(sel.value);
    const g = kcal > 0 && mer > 0 ? Math.round(mer / (kcal / 100) * 10) / 10 : 0;
    gramsEl.textContent = `${g.toFixed(1)} g/天`;
}

function renderWeightTrend(dom, trend, petName) {
    if (!dom) return;
    if (!trend || trend.length === 0) {
        dom.innerHTML = '<p class="nutrition-tip">暂无体重历史记录，记录后即可展示体重趋势</p>';
        return;
    }
    if (!window.echarts) { dom.innerHTML = ''; return; }

    const old = echarts.getInstanceByDom(dom);
    if (old) old.dispose();
    dom.innerHTML = '';

    const chart = echarts.init(dom);
    chart.setOption({
        title: { text: `${petName} · 体重趋势`, left: 'center', textStyle: { fontSize: 14 } },
        tooltip: { trigger: 'axis' },
        grid: { left: 50, right: 20, top: 45, bottom: 35 },
        xAxis: { type: 'category', data: trend.map(t => t.date), axisLabel: { hideOverlap: true } },
        yAxis: { type: 'value', name: 'kg', scale: true },
        series: [{
            name: '体重', type: 'line', smooth: true,
            data: trend.map(t => t.value),
            symbol: 'circle', symbolSize: 6,
            itemStyle: { color: '#3873B6' },
            lineStyle: { width: 2.5 },
            areaStyle: { color: 'rgba(56,115,182,0.08)' }
        }]
    });
    window.addEventListener('resize', () => echarts.getInstanceByDom(dom)?.resize());
}

function closeModal(modalId) {
    const m = document.getElementById(modalId);
    if (m) m.remove();
}

async function loadReminders() {
    const c = document.getElementById('reminders-container');
    if (!c) return;
    const ownerId = AppState.currentUser?.id || 'demo';
    try {
        const reminders = await apiGet(`/api/reminders?ownerId=${ownerId}`);
        AppState.reminderCache = reminders || [];
        if (!reminders || reminders.length === 0) {
            c.innerHTML = `<div class="empty-hint">暂无提醒 — 点击右上角「添加新提醒」创建</div>`;
            return;
        }

        // 反馈强化：进行中（待发送/已发送）置顶，已完成（已确认/已取消）下沉分组展示
        const active  = reminders.filter(r => r.status === 'PENDING' || r.status === 'SENT');
        const finished = reminders.filter(r => r.status !== 'PENDING' && r.status !== 'SENT');
        let html = '';
        if (active.length) {
            html += `<div class="reminder-group-title">进行中</div>` + active.map(renderReminderItem).join('');
        }
        if (finished.length) {
            html += `<div class="reminder-group-title">已完成</div>` + finished.map(renderReminderItem).join('');
        }
        c.innerHTML = html;
    } catch (e) {
        c.innerHTML = `<div class="empty-hint">提醒列表加载失败：${e.message}</div>`;
    }
}

/**
 * 渲染单条提醒卡片
 * 操作按钮按状态渲染：
 * - PENDING             → 编辑 / 确认 / 取消 / 删除
 * - SENT                → 确认 / 删除
 * - ACKNOWLEDGED/CANCELLED → 删除（已完成项仅可清理）
 */
function renderReminderItem(r) {
    const time = r.remindAt ? new Date(r.remindAt).toLocaleString('zh-CN') : '';
    const statusBadge = {
        PENDING:       '<span class="pet-badge" style="background:#fdeab4">待发送</span>',
        SENT:          '<span class="pet-badge" style="background:#b9dbf8">已发送</span>',
        ACKNOWLEDGED:  '<span class="pet-badge" style="background:#c6e7cd">已确认</span>',
        CANCELLED:     '<span class="pet-badge" style="background:#e2e5e9">已取消</span>'
    }[r.status] || `<span class="pet-badge">${r.status}</span>`;

    let actionButtons = '';
    if (r.status === 'PENDING') {
        actionButtons = `
            <div class="reminder-actions">
                <button class="btn btn-tiny btn-secondary" onclick="event.stopPropagation(); showReminderFormModal('${r.id}')">编辑</button>
                <button class="btn btn-tiny btn-primary" onclick="event.stopPropagation(); acknowledgeReminder('${r.id}')">确认</button>
                <button class="btn btn-tiny btn-secondary" onclick="event.stopPropagation(); cancelReminder('${r.id}')">取消</button>
                <button class="btn btn-tiny btn-danger" onclick="event.stopPropagation(); deleteReminder('${r.id}')">删除</button>
            </div>`;
    } else if (r.status === 'SENT') {
        actionButtons = `
            <div class="reminder-actions">
                <button class="btn btn-tiny btn-primary" onclick="event.stopPropagation(); acknowledgeReminder('${r.id}')">确认</button>
                <button class="btn btn-tiny btn-danger" onclick="event.stopPropagation(); deleteReminder('${r.id}')">删除</button>
            </div>`;
    } else {
        actionButtons = `
            <div class="reminder-actions">
                <button class="btn btn-tiny btn-danger" onclick="event.stopPropagation(); deleteReminder('${r.id}')">删除</button>
            </div>`;
    }

    return `
        <div class="reminder-item" data-rid="${r.id}">
            <div class="reminder-item-main">
                <b>${r.title || r.type}</b>
                ${r.description ? `<span class="vet-meta" style="margin-left:0.5rem">${truncate(r.description, 40)}</span>` : ''}
            </div>
            <div class="reminder-item-meta">
                <span class="vet-meta">${r.petName || '通用'}</span>
                <span class="vet-meta">${time}</span>
                ${statusBadge}
                ${actionButtons}
            </div>
        </div>
    `;
}

// ===================== 提醒操作：确认 / 取消 =====================
/**
 * 确认提醒（PENDING / SENT 状态可用）
 * 直接调用后端 acknowledge 接口（风险低，无需二次确认）
 */
async function acknowledgeReminder(id) {
    if (!id) return;
    try {
        await apiPut(`/api/reminders/${id}/acknowledge`, {});
        showToast('提醒已完成，为你点赞');
        await loadReminders();
        flashReminderCard(id);
    } catch (e) {
        showToast('确认失败：' + e.message, 'error');
    }
}

/**
 * 删除提醒（所有状态可用）
 * confirm 二次确认后物理删除；删除后触发 loadReminders 刷新
 */
async function deleteReminder(id) {
    if (!id) return;
    const ok = confirm('确定删除该提醒吗？删除后不可恢复。');
    if (!ok) return;
    try {
        await apiDelete(`/api/reminders/${id}`);
        showToast('提醒已删除');
        await loadReminders();
    } catch (e) {
        showToast('删除失败：' + e.message, 'error');
    }
}

/**
 * 反馈强化：对刚操作完成的提醒卡片加短暂高亮动画（2 秒后自动移除）
 */
function flashReminderCard(id) {
    const el = document.querySelector(`.reminder-item[data-rid="${id}"]`);
    if (!el) return;
    el.classList.add('reminder-flash');
    setTimeout(() => el.classList.remove('reminder-flash'), 2000);
}

/**
 * 取消提醒（仅 PENDING 状态可用）
 * —— 遵循经验 100024945：单一布尔守卫 + 取消即终止 + 所有副作用收敛在确认分支内
 *    confirm() 阻塞式返回作为唯一门槛；false 立即 return；true 分支内发起请求、写 UI、刷新
 */
async function cancelReminder(id) {
    if (!id) return;
    // 1. 确认结果收敛为单一布尔值
    const ok = confirm('确定取消该提醒吗？取消后将不会发送通知，且不可恢复。');
    // 2. 守卫结构封口：false 立即终止，后续所有副作用都不会执行
    if (!ok) return;
    // 3. 所有副作用（请求/toast/刷新）全部放在确认为真的同一分支
    try {
        await apiPut(`/api/reminders/${id}/cancel`, {});
        showToast('已取消提醒');
        await loadReminders();
    } catch (e) {
        showToast('取消失败：' + e.message, 'error');
    }
}

// ===================== 添加提醒 =====================
/**
 * 打开提醒表单 Modal（reminderId 为空 → 新增；否则 → 编辑回填）
 * - 未登录拦截
 * - 宠物下拉框：优先从 AppState.petCache 读取，空则兜底拉取 /api/pets
 * - 新增默认提醒时间：明天 09:00；编辑则回填该提醒的时间
 */
async function showReminderFormModal(reminderId = null) {
    if (!AppState.currentUser) { showToast('请先登录后添加提醒', 'error'); showLoginModal(); return; }

    // 0. 编辑模式：从缓存取出提醒，回填数据
    let editing = null;
    if (reminderId) {
        editing = (AppState.reminderCache || []).find(r => r.id === reminderId) || null;
        if (!editing) { showToast('提醒不存在或已删除', 'error'); return; }
    }

    // 1. 确保宠物缓存就绪（兜底拉取）
    if (!Array.isArray(AppState.petCache) || AppState.petCache.length === 0) {
        try { AppState.petCache = await apiGet('/api/pets'); } catch (e) { AppState.petCache = []; }
    }
    const pets = AppState.petCache || [];

    // 2. 宠物下拉框选项（编辑模式回填选中项）
    let petOptions = '';
    if (pets.length === 0) {
        petOptions = `<option value="">（请先在宠物档案添加宠物）</option>`;
    } else {
        petOptions = `<option value="">不绑定宠物（通用提醒）</option>` +
            pets.map(p => {
                const parts = [p.name];
                if (p.species) parts.push(p.species);
                if (p.breed) parts.push(p.breed);
                const selected = editing && editing.petId === p.id ? 'selected' : '';
                return `<option value="${p.id}" data-pet-name="${p.name}" ${selected}>${parts.join(' · ')}</option>`;
            }).join('');
    }

    // 3. 默认提醒时间：新增 = 明天 09:00；编辑 = 回填原时间（取前 16 位 YYYY-MM-DDTHH:mm）
    const pad = n => String(n).padStart(2, '0');
    let remindAtValue = '';
    if (editing && editing.remindAt) {
        remindAtValue = String(editing.remindAt).slice(0, 16);
    } else {
        const d = new Date();
        d.setDate(d.getDate() + 1);
        d.setHours(9, 0, 0, 0);
        remindAtValue = `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
    }

    // 4. 构造 Modal
    const html = `
        <div class="modal" id="reminder-form-modal">
            <div class="modal-content">
                <button class="modal-close" onclick="closeModal('reminder-form-modal')">✕</button>
                <h3>${editing ? '编辑提醒' : '添加新提醒'}</h3>
                <input type="hidden" id="rf-id" value="${editing ? editing.id : ''}">
                <div class="form-row">
                    <div class="form-group" style="flex:1">
                        <label>提醒类型</label>
                        <select id="rf-type">
                            <option value="VACCINE" ${editing?.type === 'VACCINE' ? 'selected' : ''}>疫苗接种</option>
                            <option value="DEWORMING" ${editing?.type === 'DEWORMING' ? 'selected' : ''}>驱虫</option>
                            <option value="CHECKUP" ${editing?.type === 'CHECKUP' ? 'selected' : ''}>体检复查</option>
                            <option value="MEDICINE" ${editing?.type === 'MEDICINE' ? 'selected' : ''}>服药</option>
                            <option value="FOOD" ${editing?.type === 'FOOD' ? 'selected' : ''}>粮食补给</option>
                            <option value="GROOMING" ${editing?.type === 'GROOMING' ? 'selected' : ''}>美容洗澡</option>
                            <option value="OTHER" ${editing?.type === 'OTHER' || !editing ? 'selected' : ''}>其他</option>
                        </select>
                    </div>
                    <div class="form-group" style="flex:2">
                        <label>提醒标题 <span style="color:var(--danger)">*</span></label>
                        <input id="rf-title" type="text" maxlength="100" value="${editing ? (editing.title || '') : ''}" placeholder="例如：下周六带豆豆打狂犬疫苗">
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group" style="flex:1">
                        <label>关联宠物</label>
                        <select id="rf-pet">${petOptions}</select>
                    </div>
                    <div class="form-group" style="flex:1">
                        <label>提醒时间 <span style="color:var(--danger)">*</span></label>
                        <input id="rf-time" type="datetime-local" value="${remindAtValue}">
                    </div>
                </div>
                <div class="form-group">
                    <label>提前预警（天）</label>
                    <input id="rf-advance" type="number" min="0" max="30" value="${editing?.advanceDays != null ? editing.advanceDays : 1}" placeholder="提前几天开始提醒，例如：1">
                </div>
                <div class="form-group">
                    <label>补充说明</label>
                    <textarea id="rf-desc" rows="3" maxlength="500" placeholder="可选，例如：宠物医院地址、注意事项等（最多500字）">${editing ? (editing.description || '') : ''}</textarea>
                </div>
                <div class="modal-actions">
                    <button class="btn" onclick="closeModal('reminder-form-modal')">取消</button>
                    <button class="btn btn-primary" onclick="submitReminder()">保存提醒</button>
                </div>
            </div>
        </div>`;
    openModal(html);
}

/**
 * 提交提醒（rf-id 为空 → 新增 POST；否则 → 编辑 PUT）
 * - 校验：标题、提醒时间必填；标题长度 2-100
 * - 联动：选了宠物 → 自动携带 petId + petName
 * - ownerId / status: 从登录态自动注入
 * - 成功：toast + 关闭 Modal + loadReminders() 刷新
 */
async function submitReminder() {
    const id    = val('rf-id');
    const title = val('rf-title').trim();
    const type  = val('rf-type')  || 'OTHER';
    const petId = val('rf-pet')   || '';
    const time  = val('rf-time');
    const desc  = val('rf-desc').trim();
    const adv   = parseInt(val('rf-advance'), 10);
    const advanceDays = isNaN(adv) ? null : adv;

    if (!title || title.length < 2 || title.length > 100) {
        showToast('标题长度需在 2-100 字', 'error'); return;
    }
    if (!time) {
        showToast('请选择提醒时间', 'error'); return;
    }
    if (new Date(time).getTime() <= Date.now()) {
        showToast('提醒时间必须晚于当前时间', 'error'); return;
    }

    // 选了宠物 → 自动带出 petName
    let petName = '';
    if (petId) {
        const petOpt = document.querySelector(`#rf-pet option[value="${petId}"]`);
        petName = petOpt?.dataset?.petName
            || ((AppState.petCache || []).find(p => p.id === petId)?.name || '');
    }

    try {
        const payload = {
            title,
            type,
            description: desc || null,
            // 提交本地无时区时间（YYYY-MM-DDTHH:mm:00），与后端 LocalDateTime 直接对应，避免 toISOString 造成的 8 小时时区偏移
            remindAt: `${time}:00`,
            advanceDays,
            notifyMethod: ['INAPP'],
            ownerId: AppState.currentUser.id,
            status: 'PENDING'
        };
        if (petId)   payload.petId   = petId;
        if (petName) payload.petName = petName;
        if (AppState.currentUser.email) payload.email = AppState.currentUser.email;

        if (id) {
            // 编辑模式：更新已有提醒
            await apiPut(`/api/reminders/${id}`, payload);
            showToast('提醒已更新');
            closeModal('reminder-form-modal');
            await loadReminders();
        } else {
            await apiPost('/api/reminders', payload);
            showToast('提醒创建成功');
            closeModal('reminder-form-modal');
            await loadReminders();
        }
    } catch (e) {
        showToast(id ? '更新失败：' + e.message : '创建失败：' + e.message, 'error');
    }
}

// ===================== 消息通知 =====================

/**
 * 刷新导航栏未读徽标（>0 显示红点数字，=0 隐藏）
 */
async function refreshUnreadBadge() {
    const badge = document.getElementById('notif-badge');
    if (!badge) return;
    const userId = AppState.currentUser?.id;
    if (!userId) { badge.style.display = 'none'; return; }
    try {
        const count = await apiGet(`/api/notifications/unread-count?ownerId=${userId}`);
        if (count > 0) {
            badge.textContent = count > 99 ? '99+' : String(count);
            badge.style.display = 'inline-block';
        } else {
            badge.style.display = 'none';
        }
    } catch (e) {
        badge.style.display = 'none';
    }
}

/**
 * 加载站内信列表：未读高亮（可点击标记已读），已读置灰
 */
async function loadNotifications() {
    const container = document.getElementById('notifications-container');
    if (!container) return;
    const ownerId = AppState.currentUser?.id || 'demo';
    try {
        const list = await apiGet(`/api/notifications?ownerId=${ownerId}`);
        if (!list || list.length === 0) {
            container.innerHTML = `<div class="empty-hint">暂无消息通知</div>`;
        } else {
            container.innerHTML = list.map(n => {
                const typeIcon = { REPLY: 'message-circle', ACCEPT: 'target', REMINDER: 'clock', SYSTEM: 'megaphone' }[n.type] || 'megaphone';
                const time = n.createdAt ? new Date(n.createdAt).toLocaleString('zh-CN') : '';
                return `
                <div class="notif-item ${n.isRead ? 'notif-read' : 'notif-unread'}" ${n.isRead ? '' : `onclick="markNotificationRead('${n.id}')"`}>
                    <span class="notif-icon"><i data-lucide="${typeIcon}"></i></span>
                    <div class="notif-body">
                        <p class="notif-title">${n.title || ''}${n.isRead ? '' : '<span class="notif-dot">●</span>'}</p>
                        <p class="notif-content">${n.content || ''}</p>
                        <p class="notif-time">${time}</p>
                    </div>
                    <button class="notif-delete" title="删除" onclick="event.stopPropagation(); deleteNotification('${n.id}')"><i data-lucide="x"></i></button>
                </div>`;
            }).join('');
        }
        await refreshUnreadBadge();
    } catch (e) {
        container.innerHTML = `<div class="empty-hint">消息加载失败：${e.message}</div>`;
    }
}

/**
 * 标记单条已读：局部移除高亮/红点，不整页刷新
 */
async function markNotificationRead(id) {
    try {
        await apiPut(`/api/notifications/${id}/read`, {});
        const items = document.querySelectorAll('.notif-item');
        for (const el of items) {
            if ((el.getAttribute('onclick') || '').includes(id)) {
                el.classList.remove('notif-unread');
                el.classList.add('notif-read');
                el.removeAttribute('onclick');
                const dot = el.querySelector('.notif-dot');
                if (dot) dot.remove();
                break;
            }
        }
        await refreshUnreadBadge();
    } catch (e) {
        showToast('操作失败：' + e.message, 'error');
    }
}

/**
 * 删除单条通知
 */
async function deleteNotification(id) {
    if (!id) return;
    if (!confirm('确认删除这条通知？')) return;
    try {
        await apiDelete(`/api/notifications/${id}`);
        showToast('通知已删除');
        await loadNotifications();
    } catch (e) {
        showToast('删除失败：' + e.message, 'error');
    }
}

/**
 * 全部标记已读
 */
async function markAllNotificationsRead() {
    const ownerId = AppState.currentUser?.id;
    if (!ownerId) return;
    try {
        await apiPut(`/api/notifications/read-all?ownerId=${ownerId}`, {});
        showToast('已全部标记为已读');
        await loadNotifications();
    } catch (e) {
        showToast('操作失败：' + e.message, 'error');
    }
}

// ===================== 工具函数 =====================
function truncate(str, n) { return str && str.length > n ? str.slice(0, n) + '...' : str; }

// 社区分类 → Lucide 图标（卡片/详情的分类徽章用；原生 <option> 不支持内嵌图标）
const CATEGORY_ICONS = {
    HEALTH: 'pill', NUTRITION: 'bone', TRAINING: 'graduation-cap',
    SHOW: 'camera', QUESTION: 'help-circle'
};
function categoryIcon(category) {
    const icon = CATEGORY_ICONS[String(category || '').toUpperCase()];
    return icon ? `<i data-lucide="${icon}"></i> ` : '';
}

// 渲染页面中的 Lucide 图标（先清除已渲染 svg 上的 data-lucide 标记，避免重复替换）
function refreshIcons() {
    if (!window.lucide) return;
    document.querySelectorAll('svg[data-lucide]').forEach(el => el.removeAttribute('data-lucide'));
    window.lucide.createIcons();
}

function showToast(msg, type = 'success') {
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = msg;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 2500);
}

// ===================== 启动入口 =====================
document.addEventListener('DOMContentLoaded', async () => {
    refreshIcons();
    // 动态插入 DOM 后自动渲染新增的 <i data-lucide>（排除 createIcons 生成的 svg，防止循环触发）
    const iconObserver = new MutationObserver(mutations => {
        for (const m of mutations) {
            for (const node of m.addedNodes) {
                if (node.nodeType !== 1 || (node.tagName && node.tagName.toLowerCase() === 'svg')) continue;
                if (node.hasAttribute?.('data-lucide') || node.querySelector?.('[data-lucide]:not(svg)')) {
                    refreshIcons();
                    return;
                }
            }
        }
    });
    iconObserver.observe(document.body, { childList: true, subtree: true });

    // 先恢复登录态，再按 URL hash 切换区块 —— 保证区块数据（提醒/通知/我的帖子等）
    // 加载时能取到正确的 currentUser.id，避免刷新后误加载其他账号的数据
    await restoreLoginState();
    const hashSection = (location.hash || '').replace('#', '');
    showSection(HASH_SECTIONS.includes(hashSection) ? hashSection : 'home');
    // 清理旧的缓存键
    localStorage.removeItem('pethealth_user');
});

// 监听 hash 变化，支持浏览器前进/后退与手动修改地址保持区块同步
window.addEventListener('hashchange', () => {
    const hashSection = (location.hash || '').replace('#', '');
    // 点击导航链接时 onclick 已切换区块，hash 同步变化触发的此处应跳过，避免重复加载
    if (HASH_SECTIONS.includes(hashSection) && hashSection !== AppState.currentSection) showSection(hashSection);
});
