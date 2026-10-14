import json

# 현재 사용 중인 키 파일 경로
key_path = r"C:\stock_pj\stock-console\scripts\google_drive_key.json"

with open(key_path, 'r') as f:
    data = json.load(f)
    print(f"🆔 JSON 파일의 프로젝트 ID: {data.get('project_id')}")
    print(f"📧 서비스 계정 이메일: {data.get('client_email')}")
    # 이 이메일이 드라이브 폴더 공유에 추가되어야 합니다!