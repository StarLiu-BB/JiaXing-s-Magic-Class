import requests
import json

url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
headers = {
    "Authorization": "Bearer sk-d6ed1e6754b84d6e95e6d56414c64ed7",
    "Content-Type": "application/json"
}
data = {
    "model": "qwen-plus",
    "input": {
        "messages": [
            {
                "role": "user",
                "content": "What is Java?"
            }
        ]
    }
}

try:
    print("Sending request...")
    response = requests.post(url, headers=headers, json=data, timeout=10)
    print(f"Status Code: {response.status_code}")
    print(f"Response: {response.text}")
except Exception as e:
    print(f"Error: {e}")
