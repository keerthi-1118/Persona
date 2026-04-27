"""
Persona — Timetable Image OCR using Google Gemini (REST)
"""
import os
import json
import base64
import requests

def extract_timetable_from_image(image_file) -> list:
    """
    Given a raw image file from Flask request, use Gemini 1.5 Flash via REST to extract the schedule.
    """
    GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
    if not GEMINI_API_KEY:
        raise RuntimeError("GEMINI_API_KEY not set in environment")
        
    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={GEMINI_API_KEY}"
    
    # Read and encode the image
    image_data = image_file.read()
    b64_image = base64.b64encode(image_data).decode('utf-8')
    mime_type = image_file.mimetype or 'image/jpeg'
    if not mime_type.startswith('image/'):
        mime_type = 'image/jpeg'

    prompt = """
You are a timetable extraction assistant. I will provide an image of a class schedule.
Please extract all the classes/subjects with their day of the week, start time, and end time.

Rules:
1. day_of_week MUST be accurately interpreted and stored strictly as a 3-letter lowercase abbreviation: "mon", "tue", "wed", "thu", "fri", "sat", or "sun".
2. start_time and end_time MUST be in HH:MM 24-hour format (e.g. "09:00", "14:30"). 
3. subject MUST be the name of the class or block.

Output ONLY a JSON array in the exact format below, with nothing else:
[
  {
    "day_of_week": "mon",
    "start_time": "09:00",
    "end_time": "10:30",
    "subject": "Physics 101"
  }
]
"""

    payload = {
        "contents": [
            {
                "parts": [
                    {"text": prompt},
                    {
                        "inline_data": {
                            "mime_type": mime_type,
                            "data": b64_image
                        }
                    }
                ]
            }
        ],
        "generationConfig": {"temperature": 0.2}
    }
    
    resp = requests.post(url, json=payload, headers={"Content-Type": "application/json"}, timeout=30)
    if not resp.ok:
        raise Exception(f"API Error {resp.status_code}: {resp.text}")
        
    resp_data = resp.json()
    try:
        raw = resp_data["candidates"][0]["content"]["parts"][0]["text"].strip()
    except (KeyError, IndexError):
        raise ValueError(f"Unexpected response format: {resp_data}")
    
    if "```json" in raw:
        raw = raw.split("```json")[1].split("```")[0].strip()
    elif "```" in raw:
        raw = raw.split("```")[1].split("```")[0].strip()
        
    try:
        data = json.loads(raw)
        if not isinstance(data, list):
            return []
        return data
    except Exception as e:
        print(f"OCR Parsing Logic failed. Output was: {raw}")
        raise ValueError("Failed to parse extracted JSON from AI")
