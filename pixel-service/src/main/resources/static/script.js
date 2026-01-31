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
    let myNickname = null;
    let isLoggedIn = false;
    let isCooldown = false;
    let cooldownInterval = null;
    let isEdgeScrollEnabled = false;

    // --- 지도 초기화 ---
    const map = new naver.maps.Map('map', {
        center: new naver.maps.LatLng(37.3595704, 127.105399),
        zoom: 16, minZoom: MIN_ZOOM, maxZoom: MAX_ZOOM, maxBounds: KOREA_BOUNDS,
        draggable: true, scrollWheel: true, disableDoubleClickZoom: true, tileTransition: true,
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
            map.setOptions({ draggable: true });
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
    let isDrawing = false, needsRedraw = false;

    function scheduleDraw() { needsRedraw = true; if (!isDrawing) { isDrawing = true; requestAnimationFrame(drawLoop); } }
    function drawLoop() { if (needsRedraw) { drawPixels(); needsRedraw = false; requestAnimationFrame(drawLoop); } else { isDrawing = false; } }

    function resizeCanvas() {
        const size = map.getSize();
        if (size.width === 0 || size.height === 0) return;
        canvas.width = size.width; canvas.height = size.height;
        previewCanvas.width = size.width; previewCanvas.height = size.height;
        scheduleDraw();
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
    const roomId = "1"; // 채팅방 ID (단일방)

    stompClient.connect({}, () => {
        // 1. 픽셀 업데이트 구독
        stompClient.subscribe('/sub/pixel', (msg) => updatePixelData(JSON.parse(msg.body)));

        // 2. 채팅 구독
        stompClient.subscribe('/sub/chat/room/' + roomId, function (chatMessage) {
            appendChatMessage(JSON.parse(chatMessage.body));
        });

        // 3. 입장 메시지 (로그인 된 경우만)
        if (isLoggedIn && myNickname) {
            sendChatMessage('ENTER', '');
            document.getElementById('chatInput').disabled = false;
            document.getElementById('chatSendBtn').disabled = false;
        }
    });

    // 채팅 메시지 화면 추가 함수
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

    // 채팅 전송 함수
    function sendChatMessage(type, text) {
        if (!stompClient || !isLoggedIn) return;
        stompClient.send("/pub/chat/message", {}, JSON.stringify({
            type: type,
            roomId: roomId,
            sender: myNickname,
            message: text
        }));
    }

    // 채팅 UI 이벤트 리스너
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

    // --- 채팅 토글 로직 (수정됨) ---
    const chatUi = document.getElementById('ui-chat');
    const chatToggleBtn = document.getElementById('chatToggleBtn');

    // 초기 상태: 열려있음 -> 닫기 아이콘(❌)
    chatToggleBtn.innerHTML = "❌";

    chatToggleBtn.addEventListener('click', () => {
        chatUi.classList.toggle('minimized');

        if (chatUi.classList.contains('minimized')) {
            // 닫힘 -> 말풍선(💬)
            chatToggleBtn.innerHTML = "💬";
        } else {
            // 열림 -> 닫기(❌)
            chatToggleBtn.innerHTML = "❌";
            // 열릴 때 최신 메시지 보기 위해 스크롤 하단 이동
            const chatBox = document.getElementById('chat-messages');
            chatBox.scrollTop = chatBox.scrollHeight;
        }
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
            if(isEdgeScrollEnabled) map.setOptions({ draggable: false });
            else map.setOptions({ draggable: true });
            mapDiv.classList.add('attack-cursor');
        } else {
            modeBtn.innerHTML = "📍 이동 모드";
            modeBtn.className = "btn-main-action mode-move";
            if(isEdgeScrollEnabled) map.setOptions({ draggable: false });
            else map.setOptions({ draggable: true });
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

    // 사용자 정보 로드 및 채팅 활성화
    fetch('/api/user/me').then(res => res.ok ? res.json() : Promise.reject()).then(user => {
        isLoggedIn = true; myNickname = user.nickname || "User";
        document.getElementById('login-area').classList.add('hidden');
        document.getElementById('user-info').classList.remove('hidden');
        document.getElementById('nickname-display').innerText = myNickname;

        // 로그인 성공 시 채팅 입력창 활성화 및 입장 처리
        document.getElementById('chatInput').disabled = false;
        document.getElementById('chatSendBtn').disabled = false;
        if(stompClient && stompClient.connected) {
            sendChatMessage('ENTER', '');
        }
    }).catch(() => { isLoggedIn = false; document.getElementById('login-area').classList.remove('hidden'); document.getElementById('user-info').classList.add('hidden'); });

    setTimeout(resizeCanvas, 500);