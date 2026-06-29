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
let guildBlueprint = { url: "", lat: 0, lng: 0, img: null, isVisible: true };
let myNickname = null;
let isLoggedIn = false;
let isCooldown = false;
let cooldownInterval = null;
let isEdgeScrollEnabled = false;

// 🛠️ 도안 편집 모드용 변수
let bpEditMode = false;
let bpTempFile = null;
let bpTempImg = new Image();
let bpTempScale = 1;
let editLat = 0;
let editLng = 0;
let currentBpLat = 0;
let currentBpLng = 0;
let isDraggingBp = false;
let dragOffset = { lat: 0, lng: 0 };

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

                const item = document.createElement('div');
                item.className = 'rank-item';

                const numSpan = document.createElement('span');
                numSpan.className = `rank-num ${rankClass}`;
                numSpan.textContent = r.rank;

                const nameSpan = document.createElement('span');
                nameSpan.className = 'rank-name';
                nameSpan.textContent = r.nickname; // ✅ XSS 방어

                const scoreSpan = document.createElement('span');
                scoreSpan.className = 'rank-score';
                scoreSpan.textContent = r.score;

                item.appendChild(numSpan);
                item.appendChild(nameSpan);
                item.appendChild(scoreSpan);
                list.appendChild(item);
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
        map.setOptions({ draggable: !isAttackMode });
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
    ctx.imageSmoothingEnabled = true;

    const center = map.getCenter();
    const tlOffset = projection.fromCoordToOffset(new naver.maps.LatLng(bounds.getNE().lat(), bounds.getSW().lng()));

    const centerOffset = projection.fromCoordToOffset(center);
    const nextGridOffset = projection.fromCoordToOffset(new naver.maps.LatLng(center.lat() + GRID_SIZE, center.lng() + GRID_SIZE));
    const cellW = Math.abs(nextGridOffset.x - centerOffset.x);
    const cellH = Math.abs(nextGridOffset.y - centerOffset.y);

    let bp = null;
    let targetLat, targetLng, targetScale;

    if (bpEditMode && bpTempImg.src) {
            bp = bpTempImg;

            const gridsWide = bpTempImg.naturalWidth * bpTempScale;
            const gridsHigh = bpTempImg.naturalHeight * bpTempScale;

            const latShift = (gridsHigh / 2) * GRID_SIZE;
            const lngShift = (gridsWide / 2) * GRID_SIZE;

            const adjustedCenterLat = center.lat() + latShift;
            const adjustedCenterLng = center.lng() - lngShift;

            currentBpLat = Math.floor((adjustedCenterLat + EPSILON) / GRID_SIZE) * GRID_SIZE;
            currentBpLng = Math.floor((adjustedCenterLng + EPSILON) / GRID_SIZE) * GRID_SIZE;

            targetLat = currentBpLat;
            targetLng = currentBpLng;
            targetScale = bpTempScale;

        } else if (guildBlueprint.isVisible && guildBlueprint.img && guildBlueprint.url !== "") {
        bp = guildBlueprint.img;
        targetLat = guildBlueprint.lat; targetLng = guildBlueprint.lng;
        targetScale = 1;
        try {
            const urlObj = new URL(guildBlueprint.url);
            const s = urlObj.searchParams.get('scale');
            if (s) targetScale = parseInt(s);
        } catch(e) {}
    }

    if (bp && bp.complete) {
        const imgW = bp.naturalWidth * cellW * targetScale;
        const imgH = bp.naturalHeight * cellH * targetScale;

        const startLatLng = new naver.maps.LatLng(targetLat + GRID_SIZE, targetLng);
        const startOffset = projection.fromCoordToOffset(startLatLng);
        const x = startOffset.x - tlOffset.x;
        const y = startOffset.y - tlOffset.y;

        ctx.save();
        ctx.globalAlpha = bpEditMode ? 0.7 : 0.4;
        ctx.drawImage(bp, x, y, imgW, imgH);

        if (bpEditMode) {
            ctx.strokeStyle = "lime"; ctx.lineWidth = 2;
            ctx.strokeRect(x, y, imgW, imgH);
            ctx.beginPath();
            ctx.moveTo(canvas.width/2 - 20, canvas.height/2); ctx.lineTo(canvas.width/2 + 20, canvas.height/2);
            ctx.moveTo(canvas.width/2, canvas.height/2 - 20); ctx.lineTo(canvas.width/2, canvas.height/2 + 20);
            ctx.stroke();
        }
        ctx.restore();
    }

    pixelMap.forEach((p) => {
        if (bounds.hasLatLng(new naver.maps.LatLng(p.lat, p.lng))) {
            const latLng = new naver.maps.LatLng(p.lat + GRID_SIZE, p.lng);
            const pOffset = projection.fromCoordToOffset(latLng);
            const px = Math.floor(pOffset.x - tlOffset.x);
            const py = Math.floor(pOffset.y - tlOffset.y);
            ctx.fillStyle = p.color;
            ctx.fillRect(px, py, cellW, cellH);
        }
    });
}


// --- 픽셀 프리뷰 & 업데이트 로직 ---
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
        msgDiv.textContent = message.message; // ✅ XSS 방어
    } else {
        msgDiv.className = 'msg-item';
        const senderSpan = document.createElement('span');
        senderSpan.className = 'msg-sender';
        senderSpan.textContent = message.sender + ':'; // ✅ XSS 방어

        const textSpan = document.createElement('span');
        textSpan.className = 'msg-text';
        textSpan.textContent = message.message; // ✅ XSS 방어

        msgDiv.appendChild(senderSpan);
        msgDiv.appendChild(textSpan);
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
    const key = `${snapLat.toFixed(6)},${snapLng.toFixed(6)}`;
    const previousPixel = pixelMap.get(key);
    updatePixelData(newPixel);
    fetch('/api/pixels', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(newPixel) })
    .then(res => res.text()).then(result => {
        if (result === "SUCCESS" || result === "성공") { startCooldown(COOLDOWN_TIME); }
        else if (result.includes("쿨타임")) {
            const remaining = result.match(/\d+/) ? parseInt(result.match(/\d+/)[0]) : 5;
            startCooldown(remaining);
            previousPixel ? pixelMap.set(key, previousPixel) : pixelMap.delete(key); scheduleDraw();
        } else { alert(result); previousPixel ? pixelMap.set(key, previousPixel) : pixelMap.delete(key); scheduleDraw(); }
    }).catch(err => { console.error(err); previousPixel ? pixelMap.set(key, previousPixel) : pixelMap.delete(key); scheduleDraw(); });
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
        map.setOptions({ draggable: !isEdgeScrollEnabled });
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
    document.getElementById('nickname-display').textContent = myNickname; // ✅ XSS 방어
    document.getElementById('chatInput').disabled = false;
    document.getElementById('chatSendBtn').disabled = false;
    if(stompClient && stompClient.connected) { sendChatMessage('ENTER', ''); }
}).catch(() => { isLoggedIn = false; document.getElementById('login-area').classList.remove('hidden'); document.getElementById('user-info').classList.add('hidden'); });

setTimeout(resizeCanvas, 500);

// --- 히트맵 로직 ---
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
        .then(data => { cachedHeatmapData = data; drawHeatmap(cachedHeatmapData); })
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
        const score = p.hitCount;
        const latLng = new naver.maps.LatLng(p.lat, p.lng);
        if (bounds.hasLatLng(latLng)) {
            const pOffset = projection.fromCoordToOffset(latLng);
            const tl = projection.fromCoordToOffset(new naver.maps.LatLng(bounds.getNE().lat(), bounds.getSW().lng()));
            const px = Math.floor(pOffset.x - tl.x), py = Math.floor(pOffset.y - tl.y);
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

// --- 🛡️ 길드 시스템 & 🗺️ 청사진 로직 ---
const guildBtn = document.getElementById('guildBtn');
const guildModal = document.getElementById('guild-modal');
const closeGuildBtn = document.getElementById('closeGuildBtn');

guildBtn.addEventListener('click', () => {
    if(!isLoggedIn) { alert("로그인이 필요합니다."); return; }
    guildModal.classList.remove('hidden');
    checkMyGuildStatus();
});
closeGuildBtn.addEventListener('click', () => guildModal.classList.add('hidden'));

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

function checkMyGuildStatus() {
    fetch('/api/guilds/my')
        .then(res => res.json())
        .then(data => {
            if (data.hasGuild === false) {
                document.getElementById('view-no-guild').classList.remove('hidden');
                document.getElementById('view-has-guild').classList.add('hidden');
                guildBlueprint.url = ""; scheduleDraw(); loadGuildList();
            } else {
                document.getElementById('view-no-guild').classList.add('hidden');
                document.getElementById('view-has-guild').classList.remove('hidden');
                document.getElementById('my-guild-name').textContent = data.name; // ✅ XSS 방어
                document.getElementById('my-guild-desc').textContent = data.description; // ✅ XSS 방어
                document.getElementById('my-guild-master').textContent = data.masterName + (data.isMaster ? " (나)" : ""); // ✅ XSS 방어
                document.getElementById('my-guild-count').textContent = `${data.memberCount} / ${data.maxMembers}`;

                if (data.isMaster) {
                    document.getElementById('blueprint-setup-area').classList.remove('hidden');
                    if (data.blueprintUrl) document.getElementById('deleteBlueprintBtn').classList.remove('hidden');
                    else document.getElementById('deleteBlueprintBtn').classList.add('hidden');
                } else { document.getElementById('blueprint-setup-area').classList.add('hidden'); }

                if (data.blueprintUrl && data.blueprintUrl !== guildBlueprint.url) {
                    guildBlueprint.url = data.blueprintUrl;
                    guildBlueprint.lat = data.blueprintLat;
                    guildBlueprint.lng = data.blueprintLng;
                    const img = document.getElementById('blueprintImage');
                    img.src = data.blueprintUrl;
                    img.onload = () => { guildBlueprint.img = img; scheduleDraw(); };
                } else if (!data.blueprintUrl) { guildBlueprint.url = ""; guildBlueprint.img = null; scheduleDraw(); }
            }
        }).catch(console.error);
}

function loadGuildList() {
    const container = document.getElementById('guild-list-container');
    container.innerHTML = '로딩 중...';
    fetch('/api/guilds').then(res => res.json()).then(data => {
        container.innerHTML = '';
        if (data.length === 0) { container.innerHTML = '생성된 길드가 없습니다.'; return; }
        data.forEach(g => {
            const div = document.createElement('div');
            div.className = 'guild-item';

            const infoDiv = document.createElement('div');
            infoDiv.className = 'g-info';

            const nameSpan = document.createElement('span');
            nameSpan.className = 'g-name';
            nameSpan.textContent = g.name; // ✅ XSS 방어

            const descDiv = document.createElement('div');
            descDiv.className = 'g-desc';
            descDiv.textContent = `${g.description} • ${g.memberCount}/${g.maxMembers}명`; // ✅ XSS 방어

            infoDiv.appendChild(nameSpan);
            infoDiv.appendChild(descDiv);

            // 버튼은 유저 입력값 없으므로 innerHTML 사용 가능
            const btnDiv = document.createElement('div');
            if (g.memberCount >= g.maxMembers) {
                btnDiv.innerHTML = `<button class="btn-join disabled" disabled>만원</button>`;
            } else {
                btnDiv.innerHTML = `<button class="btn-join" onclick="joinGuild(${g.id})">가입</button>`;
            }

            div.appendChild(infoDiv);
            div.appendChild(btnDiv);
            container.appendChild(div);
        });
    });
}

document.getElementById('createGuildActionBtn').addEventListener('click', () => {
    const name = document.getElementById('guildNameInput').value;
    const desc = document.getElementById('guildDescInput').value;
    if (!name.trim()) return;
    fetch('/api/guilds', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, description: desc }) })
    .then(res => res.text()).then(msg => { if (msg === 'SUCCESS') checkMyGuildStatus(); else alert(msg); });
});

window.joinGuild = (id) => { if (confirm("가입하시겠습니까?")) fetch(`/api/guilds/${id}/join`, { method: 'POST' }).then(() => checkMyGuildStatus()); };
window.leaveGuild = () => { if (confirm("탈퇴하시겠습니까?")) fetch('/api/guilds/leave', { method: 'POST' }).then(() => checkMyGuildStatus()); };
document.getElementById('blueprintToggle').addEventListener('change', (e) => { guildBlueprint.isVisible = e.target.checked; scheduleDraw(); });

// 🗺️ 도안 배치 로직
// 1. 도안 배치 시작 버튼
document.getElementById('startEditBlueprintBtn').addEventListener('click', () => {
    const fileInput = document.getElementById('blueprintFileInput');
    if (!fileInput.files[0]) return alert("이미지 파일을 먼저 선택하세요!");

    bpTempFile = fileInput.files[0];
    const reader = new FileReader();
    reader.onload = (e) => {
        bpTempImg.src = e.target.result;
        bpTempImg.onload = () => {
            bpEditMode = true;
            bpMoveListener = naver.maps.Event.addListener(map, 'center_changed', scheduleDraw);

            document.getElementById('guild-modal').classList.add('hidden');
            document.getElementById('blueprint-edit-ui').classList.remove('hidden');
            scheduleDraw();
        };
    };
    reader.readAsDataURL(bpTempFile);
});

// 2. 크기 조절 슬라이더
document.getElementById('blueprintScaleSlider').addEventListener('input', (e) => {
    bpTempScale = parseInt(e.target.value);
    document.getElementById('scaleValueDisplay').innerText = bpTempScale + "배";
    scheduleDraw();
});

// 3. 최종 저장 버튼
document.getElementById('confirmBlueprintBtn').addEventListener('click', () => {
    const snapLat = currentBpLat;
    const snapLng = currentBpLng;

    const formData = new FormData();
    formData.append("file", bpTempImg.src === "" ? null : bpTempFile);
    formData.append("lat", snapLat);
    formData.append("lng", snapLng);
    formData.append("scale", bpTempScale);

    const saveBtn = document.getElementById('confirmBlueprintBtn');
    saveBtn.innerText = "업로드 중..."; saveBtn.disabled = true;

    fetch('/api/guilds/blueprint', {
        method: 'POST',
        body: formData
    })
    .then(res => res.text())
    .then(msg => {
        if (msg === 'SUCCESS' || msg.startsWith('http')) {
            alert("도안이 지도 중앙 위치에 성공적으로 저장되었습니다!");
            if (bpMoveListener) naver.maps.Event.removeListener(bpMoveListener);
            exitBpEditMode();
            checkMyGuildStatus();
        } else { alert("저장 실패: " + msg); }
    })
    .catch(console.error)
    .finally(() => {
        saveBtn.innerText = "이 위치에 저장";
        saveBtn.disabled = false;
    });
});

// 4. 배치 취소 버튼
document.getElementById('cancelBlueprintBtn').addEventListener('click', () => {
    if (bpMoveListener) naver.maps.Event.removeListener(bpMoveListener);
    exitBpEditMode();
});

function exitBpEditMode() {
    bpEditMode = false;
    document.getElementById('blueprint-edit-ui').classList.add('hidden');
    document.getElementById('guild-modal').classList.remove('hidden');
    scheduleDraw();
}

// 5. 도안 삭제 로직 (길드장 전용)
document.getElementById('deleteBlueprintBtn').addEventListener('click', () => {
    if (!confirm("정말 등록된 길드 도안을 삭제하시겠습니까?")) return;

    fetch('/api/guilds/blueprint', { method: 'DELETE' })
    .then(res => res.text())
    .then(msg => {
        if (msg === 'SUCCESS' || msg === '성공') {
            alert("도안이 삭제되었습니다.");
            guildBlueprint.url = "";
            guildBlueprint.img = null;
            scheduleDraw();
            checkMyGuildStatus();
        } else { alert("삭제 실패: " + msg); }
    })
    .catch(console.error);
});