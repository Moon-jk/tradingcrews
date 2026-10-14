import os
from dotenv import load_dotenv

# 1. 경로 확인
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
ENV_PATH = os.path.join(CURRENT_DIR, "secu.env")

print("=========================================")
print(f"📂 1. 파이썬이 찾는 파일 경로:\n👉 {ENV_PATH}")
print(f"🔍 2. 실제 파일 존재 여부: {os.path.exists(ENV_PATH)}")
print("=========================================")

# 2. 파일 내부 텍스트 구조 확인 (내용 유출 방지를 위해 앞부분만 출력)
if os.path.exists(ENV_PATH):
    print("📜 3. 파일 내부 텍스트 인식 테스트:")
    with open(ENV_PATH, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith('#'):
                print(f"  ✔️ {line[:25]}...")
else:
    print("❌ 파일이 해당 경로에 없습니다! 파일명(secu.env.txt 인지)이나 위치를 확인하세요.")

# 3. 실제 로드 테스트
load_dotenv(ENV_PATH, override=True)

print("\n=========================================")
print("✅ 4. 변수 로드 최종 결과:")
print(f" - GEMINI_API_KEY: {'⭕ (정상 로드됨)' if os.getenv('GEMINI_API_KEY') else '❌ (실패)'}")
print(f" - NOTION_API_KEY: {'⭕ (정상 로드됨)' if os.getenv('NOTION_API_KEY') else '❌ (실패)'}")
print(f" - NOTION_DATABASE_ID: {'⭕ (정상 로드됨)' if os.getenv('NOTION_DATABASE_ID') else '❌ (실패)'}")
print("=========================================")