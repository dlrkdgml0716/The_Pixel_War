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

// 🛠️ 도안 편집 모드용 변수 (정밀 수정)
let bpEditMode = false;
let bpTempFile = null;
let bpTempImg = new Image();
let bpTempScale = 1;
let editLat = 0;
let editLng = 0;
let isDraggingBp = false;
let dragOffsetLat = 0;
let dragOffsetLng = 0;

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
                list.innerHTML += `<div class="rank-item"><span class="rank-num ${rankClass}">${r.rank}</span><span class="rank-name">${r.nickname}</span><span class="rank-score">${r.score}</span></div>`;
            });
        }).catch(console.error);
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
    if ((scrollX !== 0 || scrollY !== 0) && !isScrolling) { isScrolling = true; performEdgeScroll(); }
    else if (scrollX === 0 && scrollY === 0) { isScrolling = false; }
});
function performEdgeScroll() {
    if (!isScrolling || !isEdgeScrollEnabled) return;
    map.panBy(new naver.maps.Point(scrollX, scrollY));
    requestAnimationFrame(performEdgeScroll);
}

// --- 캔버스 설정 ---
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
        if (isHeatmapMode && cachedHeatmapData.length > 0) drawHeatmap(cachedHeatmapData);
        needsRedraw = false;
        requestAnimationFrame(drawLoop);
    } else { isDrawing = false; }
}

function resizeCanvas() {
    const size = map.getSize();
    canvas.width = size.width; canvas.height = size.height;
    previewCanvas.width = size.width; previewCanvas.height = size.height;
    heatmapCanvas.width = size.width; heatmapCanvas.height = size.height;
    scheduleDraw();
}
window.addEventListener('resize', resizeCanvas);

// 🗺️ 메인 렌더링 함수
function drawPixels() {
    const projection = map.getProjection();
    const bounds = map.getBounds();
    if (!bounds || !projection) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.imageSmoothingEnabled = false;

    const center = map.getCenter();
    const p1 = projection.fromCoordToOffset(center);
    const p2 = projection.fromCoordToOffset(new naver.maps.LatLng(center.lat() + GRID_SIZE, center.lng() + GRID_SIZE));
    const cellW = Math.abs(p2.x - p1.x);
    const cellH = Math.abs(p2.y - p1.y);
    const tlOffset = projection.fromCoordToOffset(new naver.maps.LatLng(bounds.getNE().lat(), bounds.getSW().lng()));

    let bp = null;
    let targetLat, targetLng, targetScale;

    if (bpEditMode && bpTempImg.src) {
        bp = bpTempImg; targetLat = editLat; targetLng = editLng; targetScale = bpTempScale * 0.25; // 기본 1배 사이즈 축소
    } else if (guildBlueprint.isVisible && guildBlueprint.img && guildBlueprint.url !== "") {
        bp = guildBlueprint.img; targetLat = guildBlueprint.lat; targetLng = guildBlueprint.lng;
        let scaleVal = 1;
        try {
            const scaleParam = new URL(guildBlueprint.url).searchParams.get('scale');
            if (scaleParam) scaleVal = parseInt(scaleParam);
        } catch(e) {}
        targetScale = scaleVal * 0.25;
    }

    if (bp && bp.complete) {
        const iw = bp.naturalWidth, ih = bp.naturalHeight;
        const imgW = iw * cellW * targetScale;
        const imgH = ih * cellH * targetScale;
        const startLat = targetLat + (GRID_SIZE * (ih * targetScale) / 2);
        const startLng = targetLng - (GRID_SIZE * (iw * targetScale) / 2);
        const startOffset = projection.fromCoordToOffset(new naver.maps.LatLng(startLat, startLng));

        ctx.save();
        ctx.globalAlpha = bpEditMode ? 0.7 : 0.4;
        ctx.drawImage(bp, Math.floor(startOffset.x - tlOffset.x), Math.floor(startOffset.y - tlOffset.y), imgW, imgH);
        if (bpEditMode) {
            ctx.strokeStyle = "#00FF00"; ctx.lineWidth = 2;
            ctx.strokeRect(Math.floor(startOffset.x - tlOffset.x), Math.floor(startOffset.y - tlOffset.y), imgW, imgH);
        }
        ctx.restore();
    }

    pixelMap.forEach((p) => {
        if (bounds.hasLatLng(new naver.maps.LatLng(p.lat, p.lng))) {
            const pOffset = projection.fromCoordToOffset(new naver.maps.LatLng(p.lat + GRID_SIZE, p.lng));
            ctx.fillStyle = p.color;
            ctx.fillRect(Math.floor(pOffset.x - tlOffset.x), Math.floor(pOffset.y - tlOffset.y), Math.ceil(cellW), Math.ceil(cellH));
        }
    });
}

// 🖱️ 도안 드래그 이동 (캔버스 레이어 활용)
const previewContainer = document.getElementById('previewCanvas');
previewContainer.style.pointerEvents = "auto";

previewContainer.addEventListener('mousedown', (e) => {
    if (!bpEditMode) return;
    isDraggingBp = true;
    const rect = previewContainer.getBoundingClientRect();
    const mouseCoord = map.getProjection().fromOffsetToCoord(new naver.maps.Point(e.clientX - rect.left, e.clientY - rect.top));
    dragOffsetLat = mouseCoord.lat() - editLat;
    dragOffsetLng = mouseCoord.lng() - editLng;
});

previewContainer.addEventListener('mousemove', (e) => {
    if (!bpEditMode || !isDraggingBp) return;
    const rect = previewContainer.getBoundingClientRect();
    const mouseCoord = map.getProjection().fromOffsetToCoord(new naver.maps.Point(e.clientX - rect.left, e.clientY - rect.top));
    editLat = Math.round((mouseCoord.lat() - dragOffsetLat) / GRID_SIZE) * GRID_SIZE;
    editLng = Math.round((mouseCoord.lng() - dragOffsetLng) / GRID_SIZE) * GRID_SIZE;
    scheduleDraw();
});

window.addEventListener('mouseup', () => { isDraggingBp = false; });

// --- 픽셀 프리뷰 & 업데이트 ---
naver.maps.Event.addListener(map, 'mousemove', function(e) {
    if (!isAttackMode || isDraggingBp) { previewCtx.clearRect(0, 0, previewCanvas.width, previewCanvas.height); return; }
    const projection = map.getProjection(), bounds = map.getBounds();
    const snapLat = Math.floor((e.coord.lat() + EPSILON) / GRID_SIZE) * GRID_SIZE;
    const snapLng = Math.floor((e.coord.lng() + EPSILON) / GRID_SIZE) * GRID_SIZE;
    if (!KOREA_BOUNDS.hasLatLng(new naver.maps.LatLng(snapLat, snapLng))) return;

    const p1 = projection.fromCoordToOffset(map.getCenter());
    const p2 = projection.fromCoordToOffset(new naver.maps.LatLng(map.getCenter().lat() + GRID_SIZE, map.getCenter().lng() + GRID_SIZE));
    const pw = Math.ceil(Math.abs(p2.x - p1.x)), ph = Math.ceil(Math.abs(p2.y - p1.y));

    previewCtx.clearRect(0, 0, previewCanvas.width, previewCanvas.height);
    const pOffset = projection.fromCoordToOffset(new naver.maps.LatLng(snapLat + GRID_SIZE, snapLng));
    const tlOffset = projection.fromCoordToOffset(new naver.maps.LatLng(bounds.getNE().lat(), bounds.getSW().lng()));
    const color = document.getElementById('colorPicker').value;
    previewCtx.fillStyle = `rgba(${parseInt(color.slice(1,3),16)}, ${parseInt(color.slice(3,5),16)}, ${parseInt(color.slice(5,7),16)}, 0.5)`;
    previewCtx.fillRect(Math.floor(pOffset.x - tlOffset.x), Math.floor(pOffset.y - tlOffset.y), pw, ph);
});

function fetchVisiblePixels() {
    const bounds = map.getBounds(); if (!bounds) return;
    const sw = bounds.getSW(), ne = bounds.getNE();
    fetch(`/api/pixels?minLat=${sw.lat()}&maxLat=${ne.lat()}&minLng=${sw.lng()}&maxLng=${ne.lng()}`)
        .then(res => res.json()).then(data => {
            data.forEach(p => {
                const sl = (Math.floor((p.lat + EPSILON) / GRID_SIZE) * GRID_SIZE).toFixed(6);
                const sg = (Math.floor((p.lng + EPSILON) / GRID_SIZE) * GRID_SIZE).toFixed(6);
                pixelMap.set(`${sl},${sg}`, { ...p, lat: parseFloat(sl), lng: parseFloat(sg) });
            });
            scheduleDraw();
        });
}
naver.maps.Event.addListener(map, 'idle', fetchVisiblePixels);
naver.maps.Event.addListener(map, 'center_changed', scheduleDraw);
naver.maps.Event.addListener(map, 'zoom_changed', scheduleDraw);

// --- WebSocket & 통신 ---
const socket = new SockJS('/ws-pixel');
const stompClient = Stomp.over(socket);
stompClient.connect({}, () => {
    stompClient.subscribe('/sub/pixel', (msg) => {
        const p = JSON.parse(msg.body);
        pixelMap.set(`${p.lat.toFixed(6)},${p.lng.toFixed(6)}`, p);
        scheduleDraw();
    });
    stompClient.subscribe('/sub/chat/room/1', (chat) => appendChatMessage(JSON.parse(chat.body)));
    if (isLoggedIn) stompClient.send("/pub/chat/message", {}, JSON.stringify({type:'ENTER', roomId:'1', sender:myNickname}));
});

function appendChatMessage(m) {
    const b = document.getElementById('chat-messages');
    const d = document.createElement('div');
    d.className = m.type === 'ENTER' ? 'msg-system' : 'msg-item';
    d.innerHTML = m.type === 'ENTER' ? m.message : `<span class="msg-sender">${m.sender}:</span><span class="msg-text">${m.message}</span>`;
    b.appendChild(d); b.scrollTop = b.scrollHeight;
}

// --- 길드 & 도안 시스템 ---
const guildBtn = document.getElementById('guildBtn');
const guildModal = document.getElementById('guild-modal');
guildBtn.addEventListener('click', () => { if(!isLoggedIn) return alert("로그인 필요"); guildModal.classList.remove('hidden'); checkMyGuildStatus(); });
document.getElementById('closeGuildBtn').addEventListener('click', () => guildModal.classList.add('hidden'));

function checkMyGuildStatus() {
    fetch('/api/guilds/my').then(res => res.json()).then(data => {
        if (!data.hasGuild) {
            document.getElementById('view-no-guild').classList.remove('hidden');
            document.getElementById('view-has-guild').classList.add('hidden');
            loadGuildList();
        } else {
            document.getElementById('view-no-guild').classList.add('hidden');
            document.getElementById('view-has-guild').classList.remove('hidden');
            document.getElementById('my-guild-name').innerText = data.name;
            if (data.isMaster) document.getElementById('blueprint-setup-area').classList.remove('hidden');
            if (data.blueprintUrl && data.blueprintUrl !== guildBlueprint.url) {
                guildBlueprint.url = data.blueprintUrl; guildBlueprint.lat = data.blueprintLat; guildBlueprint.lng = data.blueprintLng;
                const img = document.getElementById('blueprintImage'); img.src = data.blueprintUrl;
                img.onload = () => { guildBlueprint.img = img; scheduleDraw(); };
            }
        }
    });
}

document.getElementById('startEditBlueprintBtn').addEventListener('click', () => {
    const f = document.getElementById('blueprintFileInput').files[0]; if(!f) return;
    bpTempFile = f;
    const r = new FileReader();
    r.onload = (e) => {
        bpTempImg.src = e.target.result;
        bpTempImg.onload = () => {
            bpEditMode = true;
            const c = map.getCenter();
            editLat = Math.round(c.lat() / GRID_SIZE) * GRID_SIZE;
            editLng = Math.round(c.lng() / GRID_SIZE) * GRID_SIZE;
            guildModal.classList.add('hidden');
            document.getElementById('blueprint-edit-ui').classList.remove('hidden');
            scheduleDraw();
        };
    };
    r.readAsDataURL(f);
});

function exitBpEditMode() {
    bpEditMode = false; document.getElementById('blueprint-edit-ui').classList.add('hidden');
    guildModal.classList.remove('hidden'); scheduleDraw();
}

document.getElementById('confirmBlueprintBtn').addEventListener('click', () => {
    const fd = new FormData(); fd.append("file", bpTempFile); fd.append("lat", editLat); fd.append("lng", editLng); fd.append("scale", bpTempScale);
    fetch('/api/guilds/blueprint', { method: 'POST', body: fd }).then(res => res.text()).then(() => { exitBpEditMode(); checkMyGuildStatus(); });
});

document.getElementById('blueprintScaleSlider').addEventListener('input', (e) => {
    bpTempScale = parseInt(e.target.value); document.getElementById('scaleValueDisplay').innerText = bpTempScale + "배"; scheduleDraw();
});

// --- 초기 사용자 확인 ---
fetch('/api/user/me').then(res => res.json()).then(u => {
    isLoggedIn = true; myNickname = u.nickname;
    document.getElementById('login-area').classList.add('hidden');
    document.getElementById('user-info').classList.remove('hidden');
    document.getElementById('nickname-display').innerText = myNickname;
    document.getElementById('chatInput').disabled = false; document.getElementById('chatSendBtn').disabled = false;
}).catch(() => {});

// --- 기타 버튼 이벤트 ---
document.getElementById('modeBtn').addEventListener('click', function() {
    isAttackMode = !isAttackMode;
    this.innerHTML = isAttackMode ? "⚔️ 공격 모드" : "📍 이동 모드";
    this.className = isAttackMode ? "btn-main-action mode-attack" : "btn-main-action mode-move";
    map.setOptions({ draggable: !isAttackMode });
    document.getElementById('map').classList.toggle('attack-cursor', isAttackMode);
});

document.getElementById('myLocBtn').addEventListener('click', () => {
    navigator.geolocation.getCurrentPosition(p => {
        const l = new naver.maps.LatLng(p.coords.latitude, p.coords.longitude);
        if (KOREA_BOUNDS.hasLatLng(l)) { map.setCenter(l); map.setZoom(16); }
    });
});

setTimeout(resizeCanvas, 500);