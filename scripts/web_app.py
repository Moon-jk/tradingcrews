import os
import sys
import io
import json
import re
import requests
from datetime import datetime
from flask import Flask, render_template_string, request, jsonify
from google import genai
from playwright.sync_api import sync_playwright
from dotenv import load_dotenv
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload
from google.oauth2 import service_account
# OAuth 방식 제거 (서비스 계정으로 대체)
from google_auth_oauthlib.flow import InstalledAppFlow
from google.auth.transport.requests import Request
import pickle

# 1. 한글 깨짐 방지 및 터미널 출력 지연(버퍼링) 방지
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', line_buffering=True)

app = Flask(__name__)

# ==========================================
# 🔐 보안 세팅: secu.env 파일에서 API 키 불러오기
# ==========================================
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
ENV_PATH = os.path.join(os.path.dirname(CURRENT_DIR), "secu.env")

# 💡 강제 덮어쓰기 옵션 유지 (캐시 무시)
load_dotenv(ENV_PATH, override=True)

API_KEY = os.getenv("GEMINI_API_KEY")
NOTION_API_KEY = os.getenv("NOTION_API_KEY") 
NOTION_DATABASE_ID = os.getenv("NOTION_DATABASE_ID")

# Google Drive API
GOOGLE_CLIENT_ID = os.getenv("GOOGLE_CLIENT_ID")
GOOGLE_CLIENT_SECRET = os.getenv("GOOGLE_CLIENT_SECRET")
GOOGLE_DRIVE_FOLDER_ID = os.getenv("GOOGLE_DRIVE_FOLDER_ID")

# TradingCrews:트루 웹 API (카드뉴스 DB 저장용)
SPRING_API_URL = "http://localhost:8090/infocenter/api/ingest"

if not API_KEY or not NOTION_API_KEY or not NOTION_DATABASE_ID:
    print("❌ 에러: secu.env 파일에서 API 키 또는 노션 정보를 찾을 수 없습니다!")
    sys.exit()

# API 클라이언트 초기화
client = genai.Client(api_key=API_KEY)

# ==========================================
# 📂 폴더 설정
# ==========================================
STATIC_DIR = os.path.join(CURRENT_DIR, "static")
if not os.path.exists(STATIC_DIR):
    os.makedirs(STATIC_DIR)

# ==========================================
# 💡 노션 API 업로드 함수 (공식 안전 모드)
# ==========================================
def upload_to_notion(topic, cards, date_str):
    print(f"\n▶️ [DEBUG] 노션 업로드 시작 - 주제: {topic}", flush=True)
    
    url = "https://api.notion.com/v1/pages"
    headers = {
        "Authorization": f"Bearer {NOTION_API_KEY}",
        "Content-Type": "application/json",
        "Notion-Version": "2022-06-28"
    }

    # 노션 페이지 내부 본문(Block) 구성
    children_blocks = [
        {
            "object": "block",
            "type": "heading_2",
            "heading_2": {"rich_text": [{"type": "text", "text": {"content": "📊 기획안 상세 내용"}}]}
        },
        {
            "object": "block",
            "type": "paragraph",
            "paragraph": {"rich_text": [{"type": "text", "text": {"content": f"📅 생성일자: {date_str}"}}]}
        },
        {"object": "block", "type": "divider", "divider": {}}
    ]

    for card in cards:
        children_blocks.append({
            "object": "block",
            "type": "heading_3",
            "heading_3": {"rich_text": [{"type": "text", "text": {"content": f"📌 {card['page']}P: {card['title']}"}}]}
        })
        children_blocks.append({
            "object": "block",
            "type": "paragraph",
            "paragraph": {"rich_text": [{"type": "text", "text": {"content": f"💬 {card['content']}"}}]}
        })
        children_blocks.append({"object": "block", "type": "divider", "divider": {}})

    # 노션 전송용 데이터 조합 (제목 컬럼명은 '이름'이 기본값)
    data = {
        "parent": {"database_id": NOTION_DATABASE_ID},
        "properties": {
            "이름": { 
                "title": [
                    {"text": {"content": f"[TradingCrews:트루] {topic}"}}
                ]
            }
        },
        "children": children_blocks
    }

    try:
        response = requests.post(url, headers=headers, json=data)
        if response.status_code == 200:
            print("✅ [DEBUG] 노션 데이터베이스 업로드 완벽 성공!", flush=True)
        else:
            print(f"❌ [DEBUG] 노션 업로드 실패 ({response.status_code}): {response.text}", flush=True)
    except Exception as e:
        print(f"❌ [DEBUG] 노션 통신 에러: {e}", flush=True)

def get_drive_service():
    """OAuth 인증 후 드라이브 서비스 객체 반환
    - 고정 포트 8080 사용 → Google Cloud Console에 http://localhost:8080/ 한 번만 등록하면 영구 해결
    - token.pickle으로 재인증 없이 재사용
    """
    SCOPES = ['https://www.googleapis.com/auth/drive.file']
    creds = None
    TOKEN_PATH = os.path.join(CURRENT_DIR, 'token.pickle')

    if os.path.exists(TOKEN_PATH):
        with open(TOKEN_PATH, 'rb') as token:
            creds = pickle.load(token)

    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            client_config = {
                "installed": {
                    "client_id": GOOGLE_CLIENT_ID,
                    "client_secret": GOOGLE_CLIENT_SECRET,
                    "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                    "token_uri": "https://oauth2.googleapis.com/token",
                    "redirect_uris": ["http://localhost:8080/"]
                }
            }
            # ✅ 고정 포트 8080 사용 — Google Cloud Console에 http://localhost:8080/ 등록 필요
            flow = InstalledAppFlow.from_client_config(client_config, SCOPES)
            creds = flow.run_local_server(port=8080)
        with open(TOKEN_PATH, 'wb') as token:
            pickle.dump(creds, token)

    return build('drive', 'v3', credentials=creds)



def get_or_create_folder(service, folder_name):
    """지정된 이름의 폴더를 찾거나 없으면 생성"""
    parent_id = os.getenv("GOOGLE_DRIVE_FOLDER_ID")
    
    # 💡 작은따옴표(')가 이름에 포함된 경우 쿼리가 깨지는 현상 방지 (Escape 처리)
    escaped_name = folder_name.replace("'", "\\'")
    query = f"name = '{escaped_name}' and '{parent_id}' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
    
    results = service.files().list(q=query, fields="files(id)").execute()
    files = results.get('files', [])

    if files:
        return files[0]['id']
    else:
        folder_metadata = {
            'name': folder_name,
            'parents': [parent_id] if parent_id else [],
            'mimeType': 'application/vnd.google-apps.folder'
        }
        folder = service.files().create(body=folder_metadata, fields='id').execute()
        return folder.get('id')
        
# ==========================================
# 🎨 프론트엔드 (웹 화면 HTML & CSS & JS) - [카드라슈 페르소나 적용 완벽 복구버전]
# ==========================================
HTML_TEMPLATE = """
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <title>TradingCrews:트루 - 수석 에디터 '카드라슈'</title>
    <style>
        @import url('https://cdn.jsdelivr.net/gh/orioncactus/pretendard/dist/web/static/pretendard.css');
        
        body {
            margin: 0; padding: 0;
            background-color: #f2f4f6;
            color: #24272a;
            font-family: 'Pretendard', sans-serif;
            min-height: 100vh;
            display: flex; flex-direction: column; align-items: center; justify-content: flex-start;
            overflow-x: hidden;
        }

        /* 🔼 Top Section: Text & Input */
        .top-section {
            width: 100%; max-width: 600px;
            padding: 50px 20px 20px;
            text-align: center;
            z-index: 10;
        }

        .title { font-size: 36px; font-weight: 800; margin: 0 0 10px; color: #111418; }
        .subtitle { font-size: 16px; color: #535a61; margin: 0 0 30px; }

        .input-group { 
            display: flex; gap: 12px; margin-bottom: 20px; 
            background: #fff; padding: 8px; border-radius: 100px;
            box-shadow: 0 8px 16px rgba(0,0,0,0.05);
            transition: 0.3s;
        }
        .input-group:focus-within {
            box-shadow: 0 8px 24px rgba(3, 125, 214, 0.15);
            transform: translateY(-2px);
        }

        input[type="text"] {
            flex: 1; padding: 14px 20px; font-size: 16px;
            border: none; border-radius: 100px; color: #24272a;
            outline: none; background: transparent;
        }
        input::placeholder { color: #a1a5a9; }

        button {
            padding: 14px 28px; font-size: 16px; font-weight: 700;
            background: #037dd6; color: #fff; border: none; border-radius: 100px;
            cursor: pointer; transition: 0.2s; white-space: nowrap;
        }
        button:hover { background: #0260a4; transform: scale(1.02); }

        /* 탑 로딩 상태 표시 (텍스트 제거 후 스피너만 중앙 정렬) */
        .loading-state {
            display: none; align-items: center; justify-content: center;
            margin: 10px auto; color: #037dd6; font-weight: 600; font-size: 15px;
            background: rgba(3, 125, 214, 0.05); padding: 12px; border-radius: 50%;
            width: 18px; height: 18px;
        }
        .spinner {
            width: 18px; height: 18px; border: 3px solid rgba(3, 125, 214, 0.2);
            border-top-color: #037dd6; border-radius: 50%;
            animation: spin 1s linear infinite;
        }
        @keyframes spin { 100% { transform: rotate(360deg); } }

        /* 🖼️ Middle Section: Results */
        #results-container {
            width: 100%; max-width: 1200px;
            /* 여백을 넉넉히 주어 캐릭터와 겹치지 않게 함 */
            padding: 20px 20px 420px 20px; 
            display: none; flex-direction: column; align-items: center;
            z-index: 5;
            box-sizing: border-box;
            animation: slideUp 0.6s ease;
        }
        @keyframes slideUp { from { opacity: 0; transform: translateY(30px); } to { opacity: 1; transform: translateY(0); } }

        .gallery-title { font-size: 20px; font-weight: 700; color: #24272a; margin-bottom: 20px; background: #fff; padding: 10px 20px; border-radius: 20px; box-shadow: 0 4px 10px rgba(0,0,0,0.05);}
        .gallery { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 24px; width: 100%; }
        .gallery img { width: 100%; height: auto; border-radius: 12px; box-shadow: 0 10px 20px rgba(0,0,0,0.1); cursor: pointer; transition: 0.3s; }
        .gallery img:hover { transform: translateY(-6px); box-shadow: 0 16px 30px rgba(0,0,0,0.15); }

        /* 🔽 Bottom Section: Character & Desk */
        .bottom-section {
            position: fixed;
            bottom: 0; left: 50%;
            transform: translateX(-50%);
            width: 100%;
            display: flex; justify-content: center; align-items: flex-end;
            z-index: 15;
            pointer-events: none; /* 클릭 이벤트가 뒤쪽 결과물 스크롤을 방해하지 않게 함 */
        }

        .logo-container {
            width: 100%; height: 180px; /* Base height before scale */
            position: relative;
            perspective: 800px;
            display: flex; flex-direction: column; align-items: center; justify-content: flex-end;
            /* 강아지 크기 2.2배 확대 (화면 하단 기준 축소/확대) */
            transform: scale(2.2);
            transform-origin: bottom center;
        }

        #character-group {
            transform-style: preserve-3d;
            position: relative;
            display: flex; flex-direction: column; align-items: center; justify-content: flex-end;
            margin-bottom: 20px;
            z-index: 2;
            pointer-events: auto; /* 클릭 가능하도록 설정 */
            cursor: pointer;
        }

        /* 💬 말풍선 (팝업) 적용 */
        .speech-bubble {
            position: absolute;
            top: -50px; left: 60px;
            background: #ffffff;
            border: 2px solid #037dd6;
            border-radius: 16px;
            padding: 8px 14px;
            font-size: 11px;
            font-weight: 700;
            color: #037dd6;
            white-space: nowrap;
            box-shadow: 0 6px 16px rgba(0,0,0,0.1);
            opacity: 0;
            transform: scale(0.8) translateZ(50px);
            transition: all 0.3s cubic-bezier(0.18, 0.89, 0.32, 1.28);
            pointer-events: none;
            z-index: 20;
        }
        /* 말풍선 꼬리 */
        .speech-bubble::after {
            content: '';
            position: absolute;
            bottom: -6px; left: 20px;
            border-width: 6px 6px 0;
            border-style: solid;
            border-color: #ffffff transparent transparent transparent;
            z-index: 2;
        }
        .speech-bubble::before {
            content: '';
            position: absolute;
            bottom: -8px; left: 19px;
            border-width: 7px 7px 0;
            border-style: solid;
            border-color: #037dd6 transparent transparent transparent;
            z-index: 1;
        }
        
        body.working .speech-bubble, body.idle .speech-bubble {
            opacity: 1;
            transform: scale(1) translateZ(50px);
        }

        .working-motion {
            transform-style: preserve-3d;
            display: flex; flex-direction: column; align-items: center;
        }

        /* 머리 */
        .dog-head {
            width: 90px; height: 90px;
            background: #f6c373; /* Golden yellow */
            border-radius: 45px 45px 30px 30px;
            position: relative; transform-style: preserve-3d;
            box-shadow: inset 0 -10px 0 rgba(0,0,0,0.05);
            z-index: 3;
        }

        .ears { position: absolute; width: 100%; top: -10px; transform: translateZ(-15px); }
        .ear { width: 30px; height: 60px; background: #e2a64c; border-radius: 50%; position: absolute; }
        .ear.left { left: -10px; transform: rotate(-25deg); }
        .ear.right { right: -10px; transform: rotate(25deg); }

        .eyes { position: absolute; top: 35px; width: 100%; display: flex; justify-content: center; gap: 20px; transform: translateZ(15px); }
        .eye { width: 14px; height: 14px; background: #fff; border-radius: 50%; position: relative; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.2); }
        .pupil { width: 8px; height: 8px; background: #24272a; border-radius: 50%; position: absolute; top: 3px; left: 3px; }

        .muzzle {
            width: 44px; height: 32px; background: #fff; border-radius: 22px;
            position: absolute; bottom: 10px; left: 23px;
            transform: translateZ(25px);
            box-shadow: 0 4px 8px rgba(0,0,0,0.1);
        }
        .nose { width: 14px; height: 10px; background: #24272a; border-radius: 7px 7px 4px 4px; position: absolute; top: -2px; left: 15px; }

        /* 상체 */
        .dog-body {
            width: 110px; height: 60px;
            background: #fff;
            border-radius: 40px 40px 0 0;
            position: relative; margin-top: -15px;
            transform: translateZ(5px);
            box-shadow: inset 0 -5px 15px rgba(0,0,0,0.1), 0 5px 10px rgba(0,0,0,0.1);
            transform-style: preserve-3d; z-index: 1; overflow: hidden;
        }
        .tie {
            width: 14px; height: 45px; background: #d63031;
            position: absolute; top: 5px; left: 48px;
            border-radius: 2px 2px 8px 8px; transform: translateZ(10px);
            box-shadow: 2px 2px 5px rgba(0,0,0,0.1);
        }
        .collar {
            width: 60px; height: 20px; background: #f1f2f6;
            position: absolute; top: 0px; left: 25px;
            clip-path: polygon(0 0, 100% 0, 50% 100%);
        }

        /* 팔, 손 (Paws) - 작업할 때 타닥타닥 움직임 */
        .paws {
            position: absolute; bottom: 0; left: 50%; transform: translateX(-50%) translateZ(35px);
            display: flex; gap: 40px; z-index: 6;
        }
        .paw {
            width: 24px; height: 14px;
            background: #f6c373; border-radius: 12px 12px 6px 6px;
            box-shadow: 0 3px 5px rgba(0,0,0,0.1);
        }

        /* 데스크 */
        .desk {
            position: absolute; bottom: 0; width: 180px; height: 18px;
            background: #e1e5e8; border-radius: 8px 8px 0 0;
            transform: translateZ(-10px);
            box-shadow: inset 0 2px 5px rgba(255,255,255,0.5), 0 -2px 10px rgba(0,0,0,0.05);
            display: flex; justify-content: center; align-items: flex-end;
            padding-bottom: 25px; z-index: 5;
        }
        .nameplate {
            background: #24272a; color: #fff; padding: 4px 14px; font-size: 11px; font-weight: 700;
            border-radius: 4px; box-shadow: 0 4px 6px rgba(0,0,0,0.2);
            letter-spacing: 1px; transform: translateZ(40px) rotateX(10deg);
            margin-bottom: -15px; border: 1px solid #454a50;
        }

        /* 🎬 워킹 애니메이션 (작업지시 후 몸통 + 손 흔들기) */
        body.working .working-motion { animation: typeBody 0.15s infinite alternate ease-in-out; }
        @keyframes typeBody { 0% { transform: translateY(0) rotate(-1deg); } 100% { transform: translateY(4px) rotate(1deg); } }
        
        body.working .paw.left { animation: pawTap 0.15s infinite alternate; }
        body.working .paw.right { animation: pawTap 0.15s infinite alternate-reverse; }
        @keyframes pawTap { 0% { transform: translateY(0) rotate(-10deg); } 100% { transform: translateY(-8px) rotate(10deg); } }

    </style>
</head>
<body>
    <!-- 상단 인풋 영역 -->
    <div class="top-section">
        <h1 class="title">TradingCrews:트루</h1>
        <p class="subtitle">수석 에디터 '카드라슈'가 대기 중입니다.</p>
        <div class="input-group" id="search-box">
            <input type="text" id="topicInput" placeholder="주제 (예: 2026년 반도체 시장)" onkeypress="if(event.keyCode==13) generate()">
            <button id="submitBtn" onclick="generate()">기획 시작</button>
        </div>
        <div id="loading-prompt" class="loading-state">
            <div class="spinner"></div>
        </div>
    </div>

    <!-- 결과물 영역 (캐릭터 위쪽 공간으로 렌더링) -->
    <div id="results-container">
        <div class="gallery-title">🎉 기획물 완성</div>
        <div class="gallery" id="gallery"></div>
    </div>

    <!-- 하단 캐릭터 영역 -->
    <div class="bottom-section">
        <div class="logo-container">
            <div id="character-group">
                <!-- 말풍선 추가 -->
                <div class="speech-bubble" id="speech-text">주제를 기다리고 있어요!</div>
                
                <div class="working-motion">
                    <div class="dog-head">
                        <div class="ears"><div class="ear left"></div><div class="ear right"></div></div>
                        <div class="eyes">
                            <div class="eye"><div class="pupil" id="pupil-l"></div></div>
                            <div class="eye"><div class="pupil" id="pupil-r"></div></div>
                        </div>
                        <div class="muzzle"><div class="nose"></div></div>
                    </div>
                    <div class="dog-body">
                        <div class="collar"></div>
                        <div class="tie"></div>
                    </div>
                </div>
                <!-- 책상 위 손 (강아지 발) -->
                <div class="paws">
                    <div class="paw left"></div>
                    <div class="paw right"></div>
                </div>
            </div>
            <!-- 데스크와 명패 -->
            <div class="desk"><div class="nameplate">카드라슈</div></div>
        </div>
    </div>

    <script>
        const head = document.getElementById('character-group');
        const pupils = [document.getElementById('pupil-l'), document.getElementById('pupil-r')];
        const searchBox = document.getElementById('search-box');
        const loadingPrompt = document.getElementById('loading-prompt');
        const resultsContainer = document.getElementById('results-container');
        const speechText = document.getElementById('speech-text');

        let idleTimer;
        const IDLE_TIME = 20000; // 20초

        function startIdleTimer() {
            clearTimeout(idleTimer);
            idleTimer = setTimeout(() => {
                if (!document.body.classList.contains('working')) {
                    enterIdleState();
                }
            }, IDLE_TIME);
        }

        function enterIdleState() {
            document.body.classList.add('idle');
            speechText.innerText = "조금 지루하네요... 멍! 👀";
        }

        function exitIdleState() {
            if (document.body.classList.contains('idle')) {
                document.body.classList.remove('idle');
                speechText.innerText = "앗, 오셨군요! 멍! 🐾";
                setTimeout(() => { 
                    if (!document.body.classList.contains('working') && !document.body.classList.contains('clicked'))
                        speechText.innerText = "주제를 기다리고 있어요!"; 
                }, 3000);
            }
            startIdleTimer();
        }

        const randomDialogues = [
            "오늘 주식 시장이 아주 뜨겁네요! 멍! 🔥",
            "어떤 멋진 카드뉴스를 만들어 드릴까요? 🐾",
            "열심히 기획하면 나중에 간식 주시는 거죠? 🦴",
            "저는 TradingCrews:트루의 수석 에디터 카드라슈라고 합니다! ✨",
            "궁금한 경제 키워드를 저에게 건네주세요! 📈",
            "지금 바로 기획 시작할 준비가 됐어요! 멍멍!",
            "제 넥타이 어때요? 오늘따라 멋져 보이죠? 😎"
        ];

        let clickTimeout;
        head.addEventListener('click', () => {
            if (document.body.classList.contains('working')) return;
            
            exitIdleState();
            document.body.classList.add('idle'); // 말풍선 표시를 위해 idle 클래스 재활용
            document.body.classList.add('clicked');
            
            const randomMsg = randomDialogues[Math.floor(Math.random() * randomDialogues.length)];
            speechText.innerText = randomMsg;
            
            clearTimeout(clickTimeout);
            clickTimeout = setTimeout(() => {
                document.body.classList.remove('clicked');
                if (!document.body.classList.contains('working')) {
                    document.body.classList.remove('idle');
                    speechText.innerText = "주제를 기다리고 있어요!";
                }
            }, 4000);
        });

        // Mouse tracking logic
        document.addEventListener('mousemove', (e) => {
            exitIdleState();
            if (document.body.classList.contains('working')) return;

            const rect = head.getBoundingClientRect();
            const centerX = rect.left + rect.width / 2;
            const centerY = rect.top + rect.height / 2;
            
            const deltaX = e.clientX - centerX;
            const deltaY = e.clientY - centerY;
            
            // Convert to rotation angles (adjusted sensitivty for scaled figure)
            let rotateY = (deltaX / window.innerWidth) * 80;
            let rotateX = -(deltaY / window.innerHeight) * 50;
            
            // Clamping
            rotateY = Math.max(-40, Math.min(40, rotateY));
            rotateX = Math.max(-20, Math.min(20, rotateX));
            
            head.style.transform = `rotateY(${rotateY}deg) rotateX(${rotateX}deg)`;
            
            // Pupils tracking
            const pupilX = Math.max(-2, Math.min(2, (deltaX / window.innerWidth) * 10));
            const pupilY = Math.max(-2, Math.min(2, (deltaY / window.innerHeight) * 10));
            
            pupils.forEach(pupil => {
                pupil.style.transform = `translate(${pupilX}px, ${pupilY}px)`;
            });
        });

        const typingTexts = [
            "관련 자료를 리서치하고 있습니다... 멍!",
            "문구 초안을 작성 중입니다... 🐾",
            "배경 그림을 기획 중입니다... ✨",
            "마무리 검토를 하고 있습니다... 👀"
        ];
        let typingInterval;

        async function generate() {
            const topic = document.getElementById('topicInput').value.trim();
            if (!topic) {
                alert("주제를 알려주셔야 제가 일을 하죠!");
                return;
            }

            // UI State -> Working
            document.body.classList.add('working');
            
            searchBox.style.display = 'none';
            loadingPrompt.style.display = 'flex';
            resultsContainer.style.display = 'none';
            document.getElementById('gallery').innerHTML = '';
            
            let i = 0;
            speechText.innerText = typingTexts[i];
            typingInterval = setInterval(() => {
                i = (i + 1) % typingTexts.length;
                speechText.innerText = typingTexts[i];
            }, 3000);

            // Center head looking slightly down when working
            head.style.transform = `rotateY(0deg) rotateX(10deg)`;

            try {
                const response = await fetch('/api/generate', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ topic: topic })
                });
                
                const data = await response.json();
                
                if (data.status === 'success') {
                    data.images.forEach((imgUrl, idx) => {
                        const img = document.createElement('img');
                        img.src = imgUrl + '?t=' + new Date().getTime(); 
                        document.getElementById('gallery').appendChild(img);
                    });
                    
                    resetState();
                    resultsContainer.style.display = 'flex';
                    // Scroll to results slightly above the bottom to balance view
                    setTimeout(() => {
                        window.scrollTo({ top: resultsContainer.offsetTop - 50, behavior: 'smooth' });
                    }, 100);
                } else {
                    alert("생성 실패: " + data.message);
                    resetState();
                }
            } catch (error) {
                alert("서버 연결에 실패했습니다.");
                resetState();
            }
        }

        function resetState() {
            clearInterval(typingInterval);
            document.body.classList.remove('working');
            loadingPrompt.style.display = 'none';
            searchBox.style.display = 'flex';
        }

        // Initialize idle timer
        startIdleTimer();
    </script>
</body>
</html>
"""

# ==========================================
# ⚙️ 백엔드 로직 (AI + Playwright)
# ==========================================
def create_html_template(page_num, title, content):
    return f"""
    <!DOCTYPE html>
    <html lang="ko">
    <head>
        <meta charset="UTF-8">
        <style>
            @import url('https://cdn.jsdelivr.net/gh/orioncactus/pretendard/dist/web/static/pretendard.css');
            body {{ margin: 0; padding: 80px; width: 1080px; height: 1440px; background-color: #121212; font-family: 'Pretendard', sans-serif; color: #ffffff; box-sizing: border-box; display: flex; flex-direction: column; justify-content: space-between; }}
            .top-bar {{ display: flex; justify-content: space-between; align-items: flex-start; width: 100%; height: 60px; }}
            .header {{ font-size: 40px; font-weight: 800; color: #00ffcc; letter-spacing: 2px; }}
            .page-number {{ font-size: 40px; font-weight: 700; color: #555555; }}
            .main-content {{ flex: 1; display: flex; flex-direction: column; justify-content: center; align-items: center; width: 100%; padding: 40px 0; box-sizing: border-box; overflow: hidden; }}
            .title {{ font-size: 75px; font-weight: 800; line-height: 1.35; text-align: center; word-break: keep-all; margin-bottom: 50px; text-shadow: 2px 2px 10px rgba(0,0,0,0.5); max-width: 920px; }}
            .content {{ font-size: 45px; font-weight: 500; line-height: 1.5; text-align: center; word-break: keep-all; color: #dddddd; max-width: 920px; }}
            .bottom-bar {{ width: 100%; text-align: center; height: 50px; font-size: 35px; font-weight: 600; color: #888888; letter-spacing: 5px; }}
        </style>
    </head>
    <body>
        <div class="top-bar"><div class="header">TradingCrews:트루</div></div>
        <div class="main-content"><div class="title">{title}</div><div class="content">{content}</div></div>
        <div class="bottom-bar">SWIPE TO READ MORE 👉</div>
    </body>
    </html>
    """

@app.route('/')
def home():
    return render_template_string(HTML_TEMPLATE)

@app.route('/api/generate', methods=['POST'])
def generate_api():
    data = request.json
    topic = data.get('topic', '주식 시장 동향')
    date_str = datetime.now().strftime("%Y-%m-%d")
    
    safe_topic = re.sub(r'[\\/*?:"<>|]', "", topic).strip().replace(" ", "_")
    folder_name = f"output_{date_str}_{safe_topic}"
    output_dir = os.path.join(STATIC_DIR, folder_name)
    
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    prompt = f"""
    You are the main content editor and AI art director for 'TradingCrews:트루', a specialized media platform for stocks and finance.
    Based on the provided topic ({topic}) and date ({date_str}), plan an Instagram-optimized card news manuscript and the perfect English AI image generation prompt for each page's background.

    [Planning Guidelines]
    1. Length: Create between 5 to 8 pages depending on the complexity and volume of the topic.
    2. Target & Tone: Target retail investors in their 20s and 30s. The 'title' and 'content' MUST be written in highly natural, trendy, and intuitive **Korean**. Omit unnecessary explanations and focus strictly on core information (Keep the 'content' under 3 sentences per page). Focus on overall market trends and insights rather than specific individual stocks.
    3. Structure:
       - Page 1: A powerful, hooking title and visual curiosity stimulator.
       - Middle Pages: Step-by-step arrangement of data, background explanations, and core insights.
       - Final Page: Summary and call-to-action (comments, saves, likes).
    4. [★CRITICAL★] Image Prompt ('image_prompt') Rules:
       - Express the 'content' of each page using symbolic and metaphorical imagery (e.g., Uptrend = Neon Bull, Downtrend = Shattering Ice, Analysis = Hologram Chart).
       - The 'image_prompt' MUST be written entirely in **English**.
       - For visual consistency, you MUST append the following [Fixed Style Keywords] to the very end of EVERY 'image_prompt' separated by a comma.
         [Fixed Style Keywords]: ", in dark glassmorphism style, dark cyberpunk background, subtle glowing neon blue and purple lights, sleek futuristic 3D render, cinematic lighting, highly detailed --ar 16:9"
    5. [★CRITICAL★] JSON Formatting Rules: Never use double quotes (") inside the actual text of 'content' or 'title' to emphasize words. Use only single quotes (') if needed.

    [Output Format]
    - You MUST respond ONLY with the JSON list format below.
    - Do NOT include any greetings, explanations, or markdown formatting (like ```json) outside the JSON.
    - 'title' and 'content' MUST be in Korean. 'image_prompt' MUST be in English.

    [
      {{
        "page": 1, 
        "title": "여기에 후킹 제목 작성 (Korean)", 
        "content": "여기에 핵심 요약 작성 (Korean)",
        "image_prompt": "A majestic glowing green neon bull charging through shattered glass charts, in dark glassmorphism style, dark cyberpunk background, subtle glowing neon blue and purple lights, sleek futuristic 3D render, cinematic lighting, highly detailed --ar 16:9"
      }},
      ... (Generate 5~8 objects based on the content)
    ]
    """
    
    try:
        try:
            # 1차 시도: 2.5-flash (가장 최신이지만 종종 503 발생)
            primary_model = "gemini-2.5-flash"
            response = client.models.generate_content(model=primary_model, contents=prompt)
        except Exception as e1:
            print(f"⚠️ [Fallback 1] {primary_model} 실패. gemini-flash-latest 버전으로 시도합니다. (사유: {str(e1)})", flush=True)
            try:
                # 2차 시도: gemini-flash-latest (가용한 최신 릴리즈)
                secondary_model = "gemini-flash-latest"
                response = client.models.generate_content(model=secondary_model, contents=prompt)
            except Exception as e2:
                # 3차 시도: gemini-3.1-flash-lite-preview (혹시 모를 대비)
                final_model = "gemini-3.1-flash-lite-preview"
                print(f"⚠️ [Fallback 2] {secondary_model} 실패. {final_model}로 최종 시도합니다. (사유: {str(e2)})", flush=True)
                response = client.models.generate_content(model=final_model, contents=prompt)

        raw_text = response.text
        clean_json = raw_text.strip().replace('```json', '').replace('```', '').strip()
        
        # 💡 AI가 또 JSON 문법을 틀렸을 때를 대비한 방어 코드
        try:
            cards_data = json.loads(clean_json)
        except json.JSONDecodeError:
            print(f"❌ [DEBUG] AI 오타 발생! 원본 데이터:\n{raw_text}", flush=True)
            return jsonify({"status": "error", "message": "AI가 데이터 작성 중 오타를 냈습니다. '콘텐츠 생성' 버튼을 한 번 더 눌러주세요!"})

        upload_to_notion(topic, cards_data, date_str)
        
        json_path = os.path.join(output_dir, "planning_data.json")
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(cards_data, f, ensure_ascii=False, indent=2)
            
    except Exception as e:
        return jsonify({"status": "error", "message": f"AI 생성 오류: {str(e)}"})

    image_urls = []
    try:
        # 1. 드라이브 서비스 준비 및 전용 폴더 생성
        service = get_drive_service()
        target_folder_name = f"[{date_str}] {topic}" 
        target_folder_id = get_or_create_folder(service, target_folder_name)

        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page(viewport={"width": 1080, "height": 1440})

            for card in cards_data:
                page_num, title, content = card["page"], card["title"], card["content"]
                html = create_html_template(page_num, title, content)
                page.set_content(html)
                
                filename = f"card_{page_num}.png"
                file_path = os.path.join(output_dir, filename)
                page.screenshot(path=file_path)

                # 2. ✨ 지정된 폴더 안으로 업로드
                media = MediaFileUpload(file_path, mimetype='image/png')
                service.files().create(
                    body={'name': filename, 'parents': [target_folder_id]},
                    media_body=media,
                    fields='id'
                ).execute()
                print(f"✅ [Drive] {target_folder_name} 폴더에 {filename} 업로드 완료!", flush=True)

                image_urls.append(f"/static/{folder_name}/{filename}")
                
            browser.close()
            
        # ✨ Spring DB에 카드뉴스 데이터 저장 (이미지 생성 성공 후)
        save_to_spring_db(topic, date_str, folder_name, cards_data)
            
        return jsonify({"status": "success", "images": image_urls})
    
    except Exception as e:
        return jsonify({"status": "error", "message": f"이미지 생성 오류: {str(e)}"})


def save_to_spring_db(topic, date_str, folder_name, cards_data):
    """로컬 서버에 먼저 시도하고, 실패 시 배포 서버에 저장 시도"""
    
    # 1. 주소 후보지 리스트 (로컬 우선)
    LOCAL_URL = "http://localhost:8090/infocenter/api/ingest"
    REMOTE_URL = os.getenv("REMOTE_SPRING_API_URL") # .env에 배포 서버 주소를 넣어주세요!

    target_urls = [LOCAL_URL, REMOTE_URL]
    
    payload = {
        "topic": topic,
        "date": date_str,
        "folderId": folder_name,
        "cards": cards_data
    }

    for url in target_urls:
        if not url: 
            continue # 주소가 설정되지 않은 경우 패스
            
        try:
            print(f"📡 [Spring DB] 연결 시도 중... ({url})", flush=True)
            # timeout을 짧게(예: 3초) 설정해야 로컬이 꺼져있을 때 빨리 다음으로 넘어갑니다.
            res = requests.post(url, json=payload, timeout=3)
            
            if res.status_code == 200:
                result = res.json()
                print(f"✅ [Spring DB] 저장 성공! (접속 서버: {url})", flush=True)
                return # 성공했으니 함수 종료!
            else:
                print(f"⚠️ [Spring DB] 서버 응답 오류 ({res.status_code}): {url}", flush=True)
                
        except (requests.exceptions.ConnectionError, requests.exceptions.Timeout):
            print(f"❌ [Spring DB] {url} 연결 불가. 다음 후보로 넘어갑니다...", flush=True)
        except Exception as e:
            print(f"❓ [Spring DB] 예상치 못한 오류 발생: {e}", flush=True)

    print("🚫 [Spring DB] 모든 서버 연결에 실패했습니다. 데이터를 저장하지 못했습니다.", flush=True)

if __name__ == "__main__":
    print("🚀 [TradingCrews:트루 Web Server] 노션 연동 모드로 실행되었습니다!")
    print("👉 웹 브라우저를 열고 [http://127.0.0.1:5000](http://127.0.0.1:5000) 으로 접속하세요.")
    app.run(debug=True, port=5000)