import os
import sys
import io
import json
import re
from datetime import datetime
from flask import Flask, render_template_string, request, jsonify
from google import genai
from playwright.sync_api import sync_playwright
from dotenv import load_dotenv # 🔐 환경변수 로드 라이브러리 추가

# 1. 한글 깨짐 방지
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

app = Flask(__name__)

# ==========================================
# 🔐 보안 세팅: secu.env 파일에서 API 키 불러오기
# ==========================================
# 코드에 키를 직접 노출하지 않고 금고 파일에서 꺼내옵니다.
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
# 현재 파일이 scripts 폴더 안에 있다면, 상위 폴더의 secu.env를 찾습니다.
ENV_PATH = os.path.join(os.path.dirname(CURRENT_DIR), "secu.env")

# 만약 루트 폴더에서 바로 실행할 경우를 대비한 처리
if not os.path.exists(ENV_PATH):
    ENV_PATH = os.path.join(CURRENT_DIR, "secu.env")

load_dotenv(ENV_PATH)

API_KEY = os.getenv("GEMINI_API_KEY")

if not API_KEY:
    print(f"❌ 에러: {ENV_PATH} 파일에서 GEMINI_API_KEY를 찾을 수 없습니다!")
    print("secu.env 파일이 존재하는지, 키가 제대로 입력되었는지 확인하세요.")
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
# 🎨 프론트엔드 (웹 화면 HTML & CSS & JS)
# ==========================================
HTML_TEMPLATE = """
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <title>TradingCrews:트루 - AI 콘텐츠 생성기</title>
    <style>
        @import url('https://cdn.jsdelivr.net/gh/orioncactus/pretendard/dist/web/static/pretendard.css');
        body { background-color: #121212; color: #ffffff; font-family: 'Pretendard', sans-serif; margin: 0; padding: 50px; text-align: center; }
        h1 { color: #00ffcc; font-size: 40px; margin-bottom: 10px; }
        p { color: #888; font-size: 18px; margin-bottom: 40px; }
        .search-box { display: flex; justify-content: center; gap: 10px; margin-bottom: 40px; }
        input[type="text"] { width: 500px; padding: 15px 20px; font-size: 20px; border-radius: 30px; border: 2px solid #333; background-color: #222; color: #fff; outline: none; }
        input[type="text"]:focus { border-color: #00ffcc; }
        button { padding: 15px 30px; font-size: 20px; font-weight: bold; background-color: #00ffcc; color: #121212; border: none; border-radius: 30px; cursor: pointer; transition: 0.3s; }
        button:hover { background-color: #00ccaa; }
        
        .loader { display: none; border: 5px solid #333; border-top: 5px solid #00ffcc; border-radius: 50%; width: 50px; height: 50px; animation: spin 1s linear infinite; margin: 0 auto 20px auto; }
        @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
        #loading-text { display: none; color: #00ffcc; font-size: 18px; font-weight: bold; }

        .gallery { display: flex; flex-wrap: wrap; justify-content: center; gap: 20px; margin-top: 40px; }
        .gallery img { width: 300px; height: 300px; border-radius: 10px; box-shadow: 0 4px 15px rgba(0,255,204,0.2); transition: transform 0.3s; }
        .gallery img:hover { transform: scale(1.05); }
    </style>
</head>
<body>
    <h1>TradingCrews:트루 AI</h1>
    <p>주제를 입력하면 인스타그램 카드뉴스 5장이 자동으로 구워집니다.</p>

    <div class="search-box">
        <input type="text" id="topicInput" placeholder="예: 2026년 테슬라 사이버트럭 전망" onkeypress="if(event.keyCode==13) generate()">
        <button onclick="generate()">콘텐츠 생성</button>
    </div>

    <div class="loader" id="loader"></div>
    <div id="loading-text">AI가 기획하고 이미지를 렌더링 중입니다... (약 10~20초 소요)</div>

    <div class="gallery" id="gallery"></div>

    <script>
        async function generate() {
            const topic = document.getElementById('topicInput').value;
            if (!topic) return alert("주제를 입력해주세요!");

            document.getElementById('loader').style.display = 'block';
            document.getElementById('loading-text').style.display = 'block';
            document.getElementById('gallery').innerHTML = '';

            try {
                const response = await fetch('/api/generate', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ topic: topic })
                });
                
                const data = await response.json();
                
                if (data.status === 'success') {
                    data.images.forEach(imgUrl => {
                        const img = document.createElement('img');
                        img.src = imgUrl + '?t=' + new Date().getTime(); 
                        document.getElementById('gallery').appendChild(img);
                    });
                } else {
                    alert("생성 실패: " + data.message);
                }
            } catch (error) {
                alert("서버 통신 에러가 발생했습니다.");
            } finally {
                document.getElementById('loader').style.display = 'none';
                document.getElementById('loading-text').style.display = 'none';
            }
        }
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
            body {{ margin: 0; padding: 80px; width: 1080px; height: 1080px; background-color: #121212; font-family: 'Pretendard', sans-serif; color: #ffffff; box-sizing: border-box; display: flex; flex-direction: column; justify-content: space-between; }}
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
        <div class="top-bar"><div class="header">TradingCrews:트루</div><div class="page-number">{page_num} / 5</div></div>
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

    # prompt = f"""
    # 당신은 대한민국 최고의 주식 전문 미디어 'Stock Lab'의 핵심 에디터입니다.
    # 주제: {topic} / 기준일: {date_str}
    
    # 위 주제로 인스타그램 카드뉴스 5장 분량의 '주식 종합 정보' 원고를 기획하세요.
    # 인스타그램 모바일 화면에서 한눈에 읽혀야 하므로, 부연 설명은 과감히 생략하고 직관적이고 뼈대 있는 핵심 정보만 아주 간결하게 작성하세요. (각 페이지의 content는 최대 2문장을 절대 넘지 않게 짧게 끊어 쓰세요.)
    
    # 반드시 아래 JSON 리스트 형식으로만 답변해야 하며, JSON 외의 인사말이나 마크다운 기호(```json 등)는 절대 포함하지 마세요.

    # [
    #   {{"page": 1, "title": "시선을 끄는 짧은 제목", "content": "단번에 이목을 끄는 직관적인 핵심 요약 1줄"}},
    #   {{"page": 2, "title": "이슈 팩트 체크", "content": "이 이슈가 왜 중요한지 핵심 팩트만 2줄 이내로 간결히 요약"}},
    #   {{"page": 3, "title": "시장 파급력", "content": "산업/종목에 미치는 구체적 영향을 수치나 주요 키워드 중심으로 짧게 정리"}},
    #   {{"page": 4, "title": "실전 투자 전략", "content": "관련주 동향 및 투자자가 취해야 할 명확한 액션 플랜 2줄 이내"}},
    #   {{"page": 5, "title": "결론 및 랩업", "content": "최종 인사이트 1줄 요약 및 '좋아요/저장' 행동 유도"}}
    # ]
    # """

    prompt = f"""
    당신은 주식 및 금융 전문 미디어 'TradingCrews:트루'의 메인 콘텐츠 에디터입니다.
    전달된 주제({topic})와 날짜({date_str})를 바탕으로 인스타그램에 최적화된 카드뉴스 원고를 기획하세요.

    위 주제로 인스타그램 카드뉴스 5장 분량의 '주식 종합 정보' 원고를 기획하세요.
    인스타그램 모바일 화면에서 한눈에 읽혀야 하므로, 부연 설명은 과감히 생략하고 직관적이고 뼈대 있는 핵심 정보만 아주 간결하게 작성하세요. (각 페이지의 content는 최대 2문장을 절대 넘지 않게 짧게 끊어 쓰세요.)

    [기획 가이드라인]
    1. 주제의 복잡도와 정보량에 따라 최소 5장에서 최대 8장 사이로 구성하세요.
    2. 타겟: 주식 투자에 관심이 있는 2030 개인 투자자 (쉽고 명확한 어조).
    3. 구성 원칙:
    - 1페이지: 강력한 후킹 문구와 시각적 호기심 자극.
    - 중간 페이지: 데이터, 배경 설명, 핵심 인사이트를 단계별로 배치.
    - 마지막 페이지: 요약 및 독자 참여(댓글, 저장, 좋아요) 유도.
    4. 특정 종목에 국한되지 않고, 시장의 흐름이나 투자 원칙 등 포괄적인 관점에서 서술하세요.

    [출력 형식]
    - 반드시 아래 JSON 리스트 형식으로만 답변하세요. 
    - JSON 외의 인사말, 설명, 마크다운 기호(```json 등)는 절대 포함하지 마세요.

    [
    {{"page": 1, "title": "후킹 제목", "content": "부제목 혹은 핵심 요약"}},
    ... (내용에 따라 5~8개 객체 생성)
    ]
    """
    
    try:
        response = client.models.generate_content(model="gemini-2.5-flash", contents=prompt)
        clean_json = response.text.strip().replace('```json', '').replace('```', '').strip()
        cards_data = json.loads(clean_json)
        
        json_path = os.path.join(output_dir, "planning_data.json")
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(cards_data, f, ensure_ascii=False, indent=2)
            
    except Exception as e:
        return jsonify({"status": "error", "message": f"AI 생성 오류: {str(e)}"})

    image_urls = []
    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page(viewport={"width": 1080, "height": 1080})

            for card in cards_data:
                page_num, title, content = card["page"], card["title"], card["content"]
                html = create_html_template(page_num, title, content)
                page.set_content(html)
                
                filename = f"card_{page_num}.png"
                file_path = os.path.join(output_dir, filename)
                page.screenshot(path=file_path)
                
                image_urls.append(f"/static/{folder_name}/{filename}")
                
            browser.close()
            
        return jsonify({"status": "success", "images": image_urls})
    
    except Exception as e:
        return jsonify({"status": "error", "message": f"이미지 생성 오류: {str(e)}"})

if __name__ == "__main__":
    print("🚀 [TradingCrews:트루 Web Server] 보안 모드로 실행되었습니다!")
    print("👉 웹 브라우저를 열고 [http://127.0.0.1:5000](http://127.0.0.1:5000) 으로 접속하세요.")
    app.run(debug=True, port=5000)