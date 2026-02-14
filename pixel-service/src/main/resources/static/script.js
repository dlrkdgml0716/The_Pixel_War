// --- 설정 ---
const GRID_SIZE = 0.0003;
const COOLDOWN_TIME = 5;
const MIN_ZOOM = 9;
const MAX_ZOOM = 17;
const EPSILON = 0.0000001;
const EDGE_THRESHOLD = 50;
const SCROLL_SPEED = 15;
const KOREA_BOUNDS = new naver.maps.LatLngBounds(
    new naver.maps.LatLng(32.80, 124.60),
    new naver.maps.LatLng(38.55, 132.00)
);

// --- 상태 변수 ---
let isAttackMode = false;
let pixelMap = new Map();
let cachedHeatmapData = [];
let guildBlueprint = { url: "", lat: 0, lng: 0, img: null, isVisible: true }; // 🔥 [추가] 청사진 정보
let myNickname = null;
let isLoggedIn = false;
let isCooldown = false;
let cooldownInterval = null;
let isEdgeScrollEnabled = false;

// --- 지도 초기화 ---
const map = new naver.maps.Map('map', {
    center: new naver.maps.LatLng(37.3595704, 127.105399),
    zoom: 16, minZoom: MIN_ZOOM, maxZoom: MAX_ZOOM, maxBounds: KOREA_BOUNDS,
    draggable: true,
    scrollWheel: true, disableDoubleClickZoom: true, tileTransition: true,
    logoControl: false, mapDataControl: false, scaleControl: false
});

const minimap = new naver.maps.Map('mini-map-view', {
    center: map.getCenter(), zoom: MIN_ZOOM, minZoom: MIN_ZOOM, maxZoom: MIN_ZOOM,
    disableInteraction: true, logoControl: false, mapDataControl: false, scaleControl: false, zoomControl: false, mapTypeControl: false
});

naver.maps.Event.addListener(map, 'center_changed', function(center) { minimap.setCenter(center); });

// --- 랭킹 로직 ---
function fetchRanks() {
    fetch('/api/ranks')
        .then(res => res.json())
        .then(data => {
            const list = document.getElementById('rank-list');
            list.innerHTML = '';
            if (data.length === 0) {
                list.innerHTML = '<div style="text-align:center; font-size:12px; color:#666;">No Data</div>';
                return;
            }
            data.forEach(r => {
                const rankClass = r.rank <= 3 ? `rank-${r.rank}` : '';
                const html = `
                    <div class="rank-item">
                        <span class="rank-num ${rankClass}">${r.rank}</span>
                        <span class="rank-name">${r.nickname}</span>
                        <span class="rank-score">${r.score}</span>
                    </div>
                `;
                list.innerHTML += html;
            });
        })
        .catch(console.error);
}
setInterval(fetchRanks, 3000);
fetchRanks();

// --- 화면 고정 및 엣지 스크롤 ---
const cameraLockBtn = document.getElementById('cameraLockBtn');
cameraLockBtn.addEventListener('click', () => {
    isEdgeScrollEnabled = !isEdgeScrollEnabled;
    if (isEdgeScrollEnabled) {
        map.setOptions({ draggable: false });
        cameraLockBtn.innerText = "🔓";
        cameraLockBtn.classList.remove('active-lock');
    } else {
        if (isAttackMode) {
            map.setOptions({ draggable: false });
        } else {
            map.setOptions({ draggable: true });
        }
        cameraLockBtn.innerText = "🔒";
        cameraLockBtn.classList.add('active-lock');
    }
});

let scrollX = 0, scrollY = 0, isScrolling = false;
document.addEventListener('mousemove', (e) => {
    if (!isEdgeScrollEnabled) return;
    const w = window.innerWidth, h = window.innerHeight;
    const x = e.clientX, y = e.clientY;
    scrollX = 0; scrollY = 0;
    if (x < EDGE_THRESHOLD) scrollX = -SCROLL_SPEED;
    if (x > w - EDGE_THRESHOLD) scrollX = SCROLL_SPEED;
    if (y < EDGE_THRESHOLD) scrollY = -SCROLL_SPEED;
    if (y > h - EDGE_THRESHOLD) scrollY = SCROLL_SPEED;

    if (scrollX !== 0 || scrollY !== 0) { if (!isScrolling) { isScrolling = true; performEdgeScroll(); } }
    else { isScrolling = false; }
});
function performEdgeScroll() {
    if (!isScrolling || !isEdgeScrollEnabled) return;
    map.panBy(new naver.maps.Point(scrollX, scrollY));
    requestAnimationFrame(performEdgeScroll);
}

// --- 캔버스 & 픽셀 드로잉 ---
const canvas = document.getElementById('pixelCanvas');
const ctx = canvas.getContext('2d');
const previewCanvas = document.getElementById('previewCanvas');
const previewCtx = previewCanvas.getContext('2d');
const heatmapCanvas = document.getElementById('heatmapCanvas');
const heatmapCtx = heatmapCanvas.getContext('2d');

let isDrawing = false, needsRedraw = false;

function scheduleDraw() { needsRedraw = true; if (!isDrawing) { isDrawing = true; requestAnimationFrame(drawLoop); } }

function drawLoop() {
    if (needsRedraw) {
        drawPixels();
        if (isHeatmapMode && cachedHeatmapData.length > 0) {
            drawHeatmap(cachedHeatmapData);
        }
        needsRedraw = false;
        requestAnimationFrame(drawLoop);
    } else {
        isDrawing = false;
    }
}

function resizeCanvas() {
    const size = map.getSize();
    if (size.width === 0 || size.height === 0) return;
    canvas.width = size.width; canvas.height = size.height;
    previewCanvas.width = size.width; previewCanvas.height = size.height;
    heatmapCanvas.width = size.width; heatmapCanvas.height = size.height;
    scheduleDraw();
    if(isHeatmapMode) loadHeatmap();
}
window.addEventListener('resize', resizeCanvas);

function drawPixels() {
    const projection = map.getProjection(), bounds = map.getBounds();
    if (!bounds || !projection) return;
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const center = map.getCenter();
    const centerOffset = projection.fromCoordToOffset(center);
    const nextGridOffset = projection.fromCoordToOffset(new naver.maps.LatLng(center.lat() + GRID_SIZE, center.lng() + GRID_SIZE));
    let pixelW = Math.max(Math.abs(nextGridOffset.x - centerOffset.x), 3);
    let pixelH = Math.max(Math.abs(nextGridOffset.y - centerOffset.y), 3);
    if (map.getZoom() < 14) { pixelW += 1; pixelH += 1; }
    const tlOffset = projection.fromCoordToOffset(new naver.maps.LatLng(bounds.getNE().lat(), bounds.getSW().lng()));

    // 🗺️ [추가] 청사진(오버레이) 그리기
    if (guildBlueprint.isVisible && guildBlueprint.img && guildBlueprint.url !== "") {
        const bpLatLng = new naver.maps.LatLng(guildBlueprint.lat, guildBlueprint.lng);
        // 이미지가 화면 근처에 있을 때 렌더링 시도
        if (bounds.hasLatLng(bpLatLng) || true) {
            const bpOffset = projection.fromCoordToOffset(bpLatLng);
            const x = Math.floor(bpOffset.x - tlOffset.x);
            const y = Math.floor(bpOffset.y - tlOffset.y);

            // 이미지 크기를 지도 배율에 맞춤
            const imgW = guildBlueprint.img.width * pixelW;
            const imgH = guildBlueprint.img.height * pixelH;

            ctx.globalAlpha = 0.3; // 도안은 반투명하게
            ctx.drawImage(guildBlueprint.img, x, y, imgW, imgH);
            ctx.globalAlpha = 1.0; // 다시 롤백
        }
    }

    // 픽셀 그리기
    ctx.beginPath();
    pixelMap.forEach((p) => {
        if (bounds.hasLatLng(new naver.maps.LatLng(p.lat, p.lng))) {
            const latLng = new naver.maps.LatLng(p.lat + GRID_SIZE, p.lng);
            const pOffset = projection.fromCoordToOffset(latLng);
            ctx.fillStyle = p.color;
            ctx.fillRect(Math.floor(pOffset.x - tlOffset.x), Math.floor(pOffset.y - tlOffset.y), Math.ceil(pixelW), Math.ceil(pixelH));
        }
    });
}

naver.maps.Event.addListener(map, 'mousemove', function(e) {
    if (!isAttackMode) { previewCtx.clearRect(0, 0, previewCanvas.width, previewCanvas.height); return; }
    const projection = map.getProjection(), bounds = map.getBounds();
    if (!projection || !bounds) return;
    const snapLat = Math.floor((e.coord.lat() + EPSILON) / GRID_SIZE) * GRID_SIZE;
    const snapLng = Math.floor((e.coord.lng() + EPSILON) / GRID_SIZE) * GRID_SIZE;
    if (!KOREA_BOUNDS.hasLatLng(new naver.maps.LatLng(snapLat, snapLng))) return;
    const center = map.getCenter();
    const centerOffset = projection.fromCoordToOffset(center);
    const nextGridOffset = projection.fromCoordToOffset(new naver.maps.LatLng(center.lat() + GRID_SIZE, center.lng() + GRID_SIZE));
    let pixelW = Math.max(Math.abs(nextGridOffset.x - centerOffset.x), 3);
    let pixelH = Math.max(Math.abs(nextGridOffset.y - centerOffset.y), 3);

    previewCtx.clearRect(0, 0, previewCanvas.width, previewCanvas.height);

    const latLng = new naver.maps.LatLng(snapLat + GRID_SIZE, snapLng);
    const pOffset = projection.fromCoordToOffset(latLng);
    const tlOffset = projection.fromCoordToOffset(new naver.maps.LatLng(bounds.getNE().lat(), bounds.getSW().lng()));
    const color = document.getElementById('colorPicker').value;
    const r = parseInt(color.substring(1, 3), 16), g = parseInt(color.substring(3, 5), 16), b = parseInt(color.substring(5, 7), 16);
    previewCtx.fillStyle = `rgba(${r}, ${g}, ${b}, 0.5)`;
    previewCtx.strokeStyle = "white"; previewCtx.lineWidth = 1;
    const px = Math.floor(pOffset.x - tlOffset.x), py = Math.floor(pOffset.y - tlOffset.y);
    previewCtx.fillRect(px, py, Math.ceil(pixelW), Math.ceil(pixelH));
    previewCtx.strokeRect(px, py, Math.ceil(pixelW), Math.ceil(pixelH));
});

function fetchVisiblePixels() {
    const bounds = map.getBounds();
    if (!bounds) return;
    const sw = bounds.getSW(), ne = bounds.getNE();
    fetch(`/api/pixels?minLat=${sw.lat()}&maxLat=${ne.lat()}&minLng=${sw.lng()}&maxLng=${ne.lng()}`)
        .then(res => res.json())
        .then(data => {
            if (Array.isArray(data)) {
                data.forEach(p => {
                    const snapLat = (Math.floor((p.lat + EPSILON) / GRID_SIZE) * GRID_SIZE).toFixed(6);
                    const snapLng = (Math.floor((p.lng + EPSILON) / GRID_SIZE) * GRID_SIZE).toFixed(6);
                    pixelMap.set(`${snapLat},${snapLng}`, { ...p, lat: parseFloat(snapLat), lng: parseFloat(snapLng) });
                });
                scheduleDraw();
            }
        }).catch(console.warn);
}
naver.maps.Event.addListener(map, 'idle', fetchVisiblePixels);
naver.maps.Event.addListener(map, 'init', fetchVisiblePixels);
naver.maps.Event.addListener(map, 'center_changed', scheduleDraw);
naver.maps.Event.addListener(map, 'zoom_changed', scheduleDraw);

function updatePixelData(pixel) {
    const snapLat = (Math.floor((pixel.lat + EPSILON) / GRID_SIZE) * GRID_SIZE).toFixed(6);
    const snapLng = (Math.floor((pixel.lng + EPSILON) / GRID_SIZE) * GRID_SIZE).toFixed(6);
    pixelMap.set(`${snapLat},${snapLng}`, { ...pixel, lat: parseFloat(snapLat), lng: parseFloat(snapLng) });
    scheduleDraw();
    fetchRanks();
}

// --- WebSocket & 채팅 통합 ---
const socket = new SockJS('/ws-pixel');
const stompClient = Stomp.over(socket);
const roomId = "1";

stompClient.connect({}, () => {
    stompClient.subscribe('/sub/pixel', (msg) => updatePixelData(JSON.parse(msg.body)));
    stompClient.subscribe('/sub/chat/room/' + roomId, function (chatMessage) {
        appendChatMessage(JSON.parse(chatMessage.body));
    });
    if (isLoggedIn && myNickname) {
        sendChatMessage('ENTER', '');
        document.getElementById('chatInput').disabled = false;
        document.getElementById('chatSendBtn').disabled = false;
    }
});

function appendChatMessage(message) {
    const chatBox = document.getElementById('chat-messages');
    const msgDiv = document.createElement('div');
    if (message.type === 'ENTER') {
        msgDiv.className = 'msg-system';
        msgDiv.innerText = message.message;
    } else {
        msgDiv.className = 'msg-item';
        msgDiv.innerHTML = `<span class="msg-sender">${message.sender}:</span><span class="msg-text">${message.message}</span>`;
    }
    chatBox.appendChild(msgDiv);
    chatBox.scrollTop = chatBox.scrollHeight;
}

function sendChatMessage(type, text) {
    if (!stompClient || !isLoggedIn) return;
    stompClient.send("/pub/chat/message", {}, JSON.stringify({
        type: type,
        roomId: roomId,
        sender: myNickname,
        message: text
    }));
}

const chatInput = document.getElementById('chatInput');
const chatSendBtn = document.getElementById('chatSendBtn');

chatSendBtn.addEventListener('click', () => {
    const msg = chatInput.value;
    if (msg.trim() !== '') {
        sendChatMessage('TALK', msg);
        chatInput.value = '';
    }
});

chatInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') {
        const msg = chatInput.value;
        if (msg.trim() !== '') {
            sendChatMessage('TALK', msg);
            chatInput.value = '';
        }
    }
});

const chatUi = document.getElementById('ui-chat');
const chatHeader = document.getElementById('chat-header');

chatHeader.addEventListener('click', () => {
    chatUi.classList.toggle('minimized');
    const chatBox = document.getElementById('chat-messages');
    setTimeout(() => {
        chatBox.scrollTop = chatBox.scrollHeight;
    }, 300);
});

// --- 쿨타임 및 클릭 로직 ---
function startCooldown(seconds) {
    isCooldown = true;
    const display = document.getElementById('ui-cooldown-overlay');
    const timerText = document.getElementById('timerText');
    display.style.display = 'flex';
    document.getElementById('modeBtn').style.opacity = '0.5';
    let remaining = seconds; timerText.innerText = remaining;
    if (cooldownInterval) clearInterval(cooldownInterval);
    cooldownInterval = setInterval(() => {
        remaining--; timerText.innerText = remaining;
        if (remaining <= 0) {
            clearInterval(cooldownInterval);
            isCooldown = false;
            display.style.display = 'none';
            document.getElementById('modeBtn').style.opacity = '1';
        }
    }, 1000);
}

naver.maps.Event.addListener(map, 'click', function(e) {
    if (!isAttackMode) return;
    if (!isLoggedIn) { alert("로그인이 필요합니다!"); return; }
    if (isCooldown) {
        const hud = document.getElementById('ui-cooldown-overlay');
        hud.style.transform = 'translateX(-50%) scale(1.1)';
        setTimeout(() => hud.style.transform = 'translateX(-50%) scale(1)', 100);
        return;
    }
    const snapLat = Math.floor((e.coord.lat() + EPSILON) / GRID_SIZE) * GRID_SIZE;
    const snapLng = Math.floor((e.coord.lng() + EPSILON) / GRID_SIZE) * GRID_SIZE;
    if (!KOREA_BOUNDS.hasLatLng(new naver.maps.LatLng(snapLat, snapLng))) { alert("서비스 지역이 아닙니다."); return; }
    const color = document.getElementById('colorPicker').value;
    const newPixel = { lat: snapLat, lng: snapLng, color: color, userId: myNickname };
    updatePixelData(newPixel);
    fetch('/api/pixels', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(newPixel) })
    .then(res => res.text()).then(result => {
        if (result === "SUCCESS" || result === "성공") { startCooldown(COOLDOWN_TIME); }
        else if (result.includes("쿨타임")) {
            const remaining = result.match(/\d+/) ? parseInt(result.match(/\d+/)[0]) : 5;
            startCooldown(remaining);
            pixelMap.delete(`${snapLat.toFixed(6)},${snapLng.toFixed(6)}`); scheduleDraw();
        } else { alert(result); pixelMap.delete(`${snapLat.toFixed(6)},${snapLng.toFixed(6)}`); scheduleDraw(); }
    }).catch(err => { console.error(err); pixelMap.delete(`${snapLat.toFixed(6)},${snapLng.toFixed(6)}`); scheduleDraw(); });
});

const modeBtn = document.getElementById('modeBtn');
const myLocBtn = document.getElementById('myLocBtn');
const mapDiv = document.getElementById('map');

modeBtn.addEventListener('click', () => {
    isAttackMode = !isAttackMode;
    if (isAttackMode) {
        modeBtn.innerHTML = "⚔️ 공격 모드";
        modeBtn.className = "btn-main-action mode-attack";
        map.setOptions({ draggable: false });
        mapDiv.classList.add('attack-cursor');
    } else {
        modeBtn.innerHTML = "📍 이동 모드";
        modeBtn.className = "btn-main-action mode-move";
        if(isEdgeScrollEnabled) {
            map.setOptions({ draggable: false });
        } else {
            map.setOptions({ draggable: true });
        }
        mapDiv.classList.remove('attack-cursor');
        previewCtx.clearRect(0, 0, previewCanvas.width, previewCanvas.height);
    }
});

myLocBtn.addEventListener('click', () => {
    if (!navigator.geolocation) { alert("위치 정보 미지원"); return; }
    navigator.geolocation.getCurrentPosition(
        (pos) => {
            const loc = new naver.maps.LatLng(pos.coords.latitude, pos.coords.longitude);
            if (KOREA_BOUNDS.hasLatLng(loc)) { map.setCenter(loc); map.setZoom(16); }
            else alert("서비스 지역 밖입니다.");
        },
        () => alert("위치 정보를 가져올 수 없습니다.")
    );
});

fetch('/api/user/me').then(res => res.ok ? res.json() : Promise.reject()).then(user => {
    isLoggedIn = true; myNickname = user.nickname || "User";
    document.getElementById('login-area').classList.add('hidden');
    document.getElementById('user-info').classList.remove('hidden');
    document.getElementById('nickname-display').innerText = myNickname;
    document.getElementById('chatInput').disabled = false;
    document.getElementById('chatSendBtn').disabled = false;
    if(stompClient && stompClient.connected) {
        sendChatMessage('ENTER', '');
    }
}).catch(() => { isLoggedIn = false; document.getElementById('login-area').classList.remove('hidden'); document.getElementById('user-info').classList.add('hidden'); });

setTimeout(resizeCanvas, 500);

let isHeatmapMode = false;
const heatmapBtn = document.getElementById('heatmapBtn');

heatmapBtn.addEventListener('click', () => {
    isHeatmapMode = !isHeatmapMode;
    if (isHeatmapMode) {
        heatmapBtn.classList.add('active-heat');
        loadHeatmap();
    } else {
        heatmapBtn.classList.remove('active-heat');
        heatmapCtx.clearRect(0, 0, heatmapCanvas.width, heatmapCanvas.height);
    }
});

function loadHeatmap() {
    if (!isHeatmapMode) return;
    fetch('/api/pixels/hot')
        .then(res => res.json())
        .then(data => {
            cachedHeatmapData = data;
            drawHeatmap(cachedHeatmapData);
        })
        .catch(console.error);
}

function drawHeatmap(hotPixels) {
    if (!isHeatmapMode) return;
    heatmapCtx.clearRect(0, 0, heatmapCanvas.width, heatmapCanvas.height);
    const projection = map.getProjection();
    const bounds = map.getBounds();

    heatmapCtx.filter = 'blur(8px)';
    heatmapCtx.globalCompositeOperation = 'lighter';

    hotPixels.forEach(p => {
        const score = parseInt(p.color);
        const latLng = new naver.maps.LatLng(p.lat, p.lng);
        if (bounds.hasLatLng(latLng)) {
            const pOffset = projection.fromCoordToOffset(latLng);
            const tl = projection.fromCoordToOffset(new naver.maps.LatLng(bounds.getNE().lat(), bounds.getSW().lng()));
            const px = Math.floor(pOffset.x - tl.x);
            const py = Math.floor(pOffset.y - tl.y);
            const radius = Math.min(score * 2, 40) + 10;
            heatmapCtx.beginPath();
            heatmapCtx.arc(px, py, radius, 0, Math.PI * 2);
            if (score > 50) heatmapCtx.fillStyle = "rgba(255, 255, 255, 0.8)";
            else if (score > 20) heatmapCtx.fillStyle = "rgba(255, 255, 0, 0.6)";
            else heatmapCtx.fillStyle = "rgba(255, 0, 0, 0.4)";
            heatmapCtx.fill();
        }
    });

    heatmapCtx.filter = 'none';
    heatmapCtx.globalCompositeOperation = 'source-over';
}

naver.maps.Event.addListener(map, 'idle', () => {
    if(isHeatmapMode) loadHeatmap();
});

// --- 🛡️ 길드 시스템 및 🗺️ 청사진 로직 ---
const guildBtn = document.getElementById('guildBtn');
const guildModal = document.getElementById('guild-modal');
const closeGuildBtn = document.getElementById('closeGuildBtn');

const viewNoGuild = document.getElementById('view-no-guild');
const viewHasGuild = document.getElementById('view-has-guild');

// 모달 열기/닫기
guildBtn.addEventListener('click', () => {
    if(!isLoggedIn) { alert("로그인이 필요합니다."); return; }
    guildModal.classList.remove('hidden');
    checkMyGuildStatus();
});
closeGuildBtn.addEventListener('click', () => guildModal.classList.add('hidden'));

// 탭 전환
window.showTab = function(tabName) {
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(content => content.classList.add('hidden'));

    if (tabName === 'list') {
        document.getElementById('tab-guild-list').classList.remove('hidden');
        document.querySelector('.tab-btn:nth-child(1)').classList.add('active');
        loadGuildList();
    } else {
        document.getElementById('tab-guild-create').classList.remove('hidden');
        document.querySelector('.tab-btn:nth-child(2)').classList.add('active');
    }
};

// [핵심] 내 길드 상태 확인 및 청사진 로드
function checkMyGuildStatus() {
    fetch('/api/guilds/my')
        .then(res => res.json())
        .then(data => {
            if (data.hasGuild === false) {
                viewNoGuild.classList.remove('hidden');
                viewHasGuild.classList.add('hidden');
                guildBlueprint.url = ""; // 길드가 없으면 도안 초기화
                scheduleDraw();
                loadGuildList();
            } else {
                viewNoGuild.classList.add('hidden');
                viewHasGuild.classList.remove('hidden');

                document.getElementById('my-guild-name').innerText = data.name;
                document.getElementById('my-guild-desc').innerText = data.description;
                document.getElementById('my-guild-master').innerText = data.masterName + (data.isMaster ? " (나)" : "");
                document.getElementById('my-guild-count').innerText = `${data.memberCount} / ${data.maxMembers}`;

                // 🗺️ 청사진 UI 표시 (길드장에게만 입력 폼 노출)
                const setupArea = document.getElementById('blueprint-setup-area');
                if (data.isMaster) {
                    setupArea.classList.remove('hidden');
                    // 파일 업로드 창은 이전 URL을 표시할 수 없으므로 비워둡니다.
                    document.getElementById('blueprintLatInput').value = data.blueprintLat || "";
                    document.getElementById('blueprintLngInput').value = data.blueprintLng || "";
                } else {
                    setupArea.classList.add('hidden');
                }

                // 🗺️ 서버에서 받은 도안 정보 저장 및 렌더링
                if (data.blueprintUrl && data.blueprintUrl !== guildBlueprint.url) {
                    guildBlueprint.url = data.blueprintUrl;
                    guildBlueprint.lat = data.blueprintLat;
                    guildBlueprint.lng = data.blueprintLng;

                    const img = document.getElementById('blueprintImage');

                    // 🚨 [수정됨] 프록시 제거! S3 URL 직통 연결!
                    img.src = data.blueprintUrl;

                    img.onload = () => {
                        guildBlueprint.img = img;
                        scheduleDraw(); // 이미지 로드 완료 시 화면 갱신
                    };
                    img.onerror = () => {
                        console.warn("도안 이미지를 불러올 수 없습니다.");
                        guildBlueprint.img = null;
                    };
                }
            }
        })
        .catch(console.error);
}

// 길드 목록 불러오기
function loadGuildList() {
    const container = document.getElementById('guild-list-container');
    container.innerHTML = '<div style="text-align:center; color:#888; margin-top:20px;">로딩 중...</div>';

    fetch('/api/guilds')
        .then(res => res.json())
        .then(data => {
            container.innerHTML = '';
            if (data.length === 0) {
                container.innerHTML = '<div style="text-align:center; color:#666; margin-top:50px;">생성된 길드가 없습니다.<br>첫 번째 길드장이 되어보세요! 👑</div>';
                return;
            }
            data.forEach(g => {
                const div = document.createElement('div');
                div.className = 'guild-item';

                const isFull = g.memberCount >= g.maxMembers;
                const btnHtml = isFull
                    ? `<button class="btn-join disabled" disabled>만원</button>`
                    : `<button class="btn-join" onclick="joinGuild(${g.id})">가입</button>`;

                div.innerHTML = `
                    <div class="g-info">
                        <span class="g-name">${g.name}</span>
                        <div style="font-size:11px; color:#aaa;">
                            <span>${g.description}</span> • <span style="color:#4caf50;">${g.memberCount}/${g.maxMembers}명</span>
                        </div>
                    </div>
                    ${btnHtml}
                `;
                container.appendChild(div);
            });
        })
        .catch(console.error);
}

// 길드 생성하기
document.getElementById('createGuildActionBtn').addEventListener('click', () => {
    const name = document.getElementById('guildNameInput').value;
    const desc = document.getElementById('guildDescInput').value;
    if (!name.trim()) { alert("길드 이름을 입력해주세요."); return; }

    fetch('/api/guilds', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: name, description: desc })
    })
    .then(res => res.text())
    .then(msg => {
        if (msg === 'SUCCESS') {
            alert("길드가 창설되었습니다! 🎉");
            document.getElementById('guildNameInput').value = '';
            document.getElementById('guildDescInput').value = '';
            checkMyGuildStatus();
        } else if (msg === 'ALREADY_HAS_GUILD') {
            alert("이미 가입된 길드가 있습니다.");
        } else {
            alert("생성 실패: " + msg);
        }
    })
    .catch(console.error);
});

// 길드 가입하기
window.joinGuild = function(guildId) {
    if (!confirm("정말 이 길드에 가입하시겠습니까?")) return;

    fetch(`/api/guilds/${guildId}/join`, { method: 'POST' })
    .then(res => res.text())
    .then(msg => {
        if (msg === 'SUCCESS') {
            alert("가입되었습니다! ⚔️");
            checkMyGuildStatus();
        } else if (msg === 'GUILD_FULL') {
            alert("길드 정원이 꽉 찼습니다.");
        } else if (msg === 'ALREADY_HAS_GUILD') {
            alert("이미 가입한 길드가 있습니다.");
        } else {
            alert(msg);
        }
    })
    .catch(console.error);
};

// 길드 탈퇴하기
window.leaveGuild = function() {
    if (!confirm("정말 탈퇴하시겠습니까?\n(길드장이면 다음 멤버에게 권한이 위임되며,\n마지막 멤버일 경우 길드가 삭제됩니다.)")) return;

    fetch('/api/guilds/leave', { method: 'POST' })
    .then(res => res.text())
    .then(msg => {
        if (msg === 'SUCCESS' || msg === 'GUILD_DELETED') {
            alert(msg === 'GUILD_DELETED' ? "마지막 멤버가 떠나 길드가 삭제되었습니다." : "탈퇴했습니다.");
            checkMyGuildStatus();
        } else {
            alert("오류: " + msg);
        }
    })
    .catch(console.error);
};

// 🗺️ 청사진 토글 스위치 이벤트
document.getElementById('blueprintToggle').addEventListener('change', (e) => {
    guildBlueprint.isVisible = e.target.checked;
    scheduleDraw(); // 켜고 끌 때마다 화면 갱신
});

// 🚨 [수정됨] 🗺️ 길드장 청사진 S3 업로드 저장 로직 (FormData 사용)
document.getElementById('saveBlueprintBtn').addEventListener('click', () => {
    const fileInput = document.getElementById('blueprintFileInput');
    const lat = document.getElementById('blueprintLatInput').value;
    const lng = document.getElementById('blueprintLngInput').value;

    // 예외 처리 (파일과 좌표가 있는지 검사)
    if (!fileInput.files || fileInput.files.length === 0) {
        alert("업로드할 도안 이미지를 선택해주세요.");
        return;
    }
    if (!lat || !lng) {
        alert("도안이 위치할 좌표(위도, 경도)를 입력해주세요.");
        return;
    }

    // 파일 전송을 위한 폼 데이터 객체 생성
    const formData = new FormData();
    formData.append("file", fileInput.files[0]); // 컨트롤러의 @RequestParam("file")과 일치해야 함
    formData.append("lat", parseFloat(lat));
    formData.append("lng", parseFloat(lng));

    // 버튼 비활성화 (업로드 중 중복 클릭 방지)
    const saveBtn = document.getElementById('saveBlueprintBtn');
    saveBtn.innerText = "업로드 중...";
    saveBtn.disabled = true;

    fetch('/api/guilds/blueprint', {
        method: 'POST',
        // 주의: FormData를 사용할 때는 Content-Type을 수동으로 설정하지 않습니다! (브라우저가 자동 설정)
        body: formData
    })
    .then(res => res.text())
    .then(msg => {
        if (msg === 'SUCCESS' || msg.startsWith('http')) {
            alert("도안이 성공적으로 S3에 업로드되어 길드원들과 공유됩니다!");
            checkMyGuildStatus(); // 다시 정보를 불러와서 지도에 즉시 렌더링
        } else {
            alert("저장 실패: " + msg);
        }
    })
    .catch(err => {
        console.error(err);
        alert("업로드 중 네트워크 오류가 발생했습니다.");
    })
    .finally(() => {
        saveBtn.innerText = "도안 저장 (길드장 전용)";
        saveBtn.disabled = false;
    });
});