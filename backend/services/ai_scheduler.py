"""
Persona — AI Scheduling Service using Google Gemini
"""
import os
import json
import requests
from datetime import datetime, timedelta

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")

def _generate_content(prompt: str) -> str:
    if not GEMINI_API_KEY:
        raise RuntimeError("GEMINI_API_KEY not set in .env file")
        
    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key={GEMINI_API_KEY}"
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {"temperature": 0.7}
    }
    
    resp = requests.post(url, json=payload, headers={"Content-Type": "application/json"}, timeout=15)
    if not resp.ok:
        raise Exception(f"Gemini API Error {resp.status_code}: {resp.text}")
        
    data = resp.json()
    try:
        return data["candidates"][0]["content"]["parts"][0]["text"]
    except (KeyError, IndexError):
        raise Exception(f"Unexpected response format from Gemini: {data}")


def schedule_tasks(tasks: list, existing_schedule: list, timetable: list) -> dict:
    """
    Use Gemini to intelligently schedule a list of pending tasks.
    
    Returns:
      {
        "scheduled": [{"task_id":..., "title":..., "start_time":..., "end_time":..., "reason":...}],
        "explanation": "Human-readable summary of the plan",
        "tips": ["tip1", "tip2"]
      }
    """
    if not tasks:
        return {"scheduled": [], "explanation": "No pending tasks to schedule!", "tips": []}

    now = datetime.now()
    today_str = now.strftime("%A, %d %B %Y, %H:%M")

    # Build context strings
    tasks_text = "\n".join([
        f"- ID:{t.get('id','?')} | {t.get('title','?')} | priority:{t.get('priority','medium')} | "
        f"due:{t.get('due_date','no deadline')} | est:{t.get('estimated_minutes', 60)}min"
        for t in tasks
    ])

    blocked_text = "\n".join([
        f"- {b.get('day','?')} {b.get('start_time','?')}-{b.get('end_time','?')}: {b.get('subject','class')}"
        for b in timetable
    ]) or "No timetable set"

    existing_text = "\n".join([
        f"- {e.get('start_time','?')[:16]}: {e.get('title','?')}"
        for e in existing_schedule
    ]) or "No existing tasks"

    prompt = f"""You are an intelligent student productivity AI assistant named Persona.

Today is: {today_str}

PENDING TASKS TO SCHEDULE:
{tasks_text}

EXISTING SCHEDULED TASKS (avoid conflicts):
{existing_text}

BLOCKED TIME (classes / timetable):
{blocked_text}

SCHEDULING RULES:
- Students work best 7am-10pm
- High/urgent tasks first
- Add 10-min breaks between 90+ min tasks
- Avoid scheduling during blocked class times
- Tasks due sooner should be scheduled earlier
- Group similar subjects together when possible
- Prefer morning for hard cognitive tasks, evening for lighter review

Please schedule all pending tasks optimally for the next 3 days.

Respond ONLY with valid JSON in this exact format:
{{
  "scheduled": [
    {{
      "task_id": "id_here",
      "title": "task title",
      "start_time": "2026-03-13T09:00",
      "end_time": "2026-03-13T10:00",
      "reason": "short reason why this time slot"
    }}
  ],
  "explanation": "A warm, friendly 2-3 sentence summary of the plan",
  "tips": ["productivity tip 1", "tip 2"]
}}"""

    try:
        raw_response = _generate_content(prompt)
        raw = raw_response.strip()
    except Exception as e:
        print(f"AI Generation Failed: {e}")
        return {"scheduled": [], "explanation": f"AI service error: {e}", "tips": []}

    # Extract JSON from possible markdown code block
    if "```json" in raw:
        raw = raw.split("```json")[1].split("```")[0].strip()
    elif "```" in raw:
        raw = raw.split("```")[1].split("```")[0].strip()

    try:
        return json.loads(raw)
    except Exception as e:
        print(f"Failed to parse AI JSON: {raw}")
        return {"scheduled": [], "explanation": "AI returned invalid schedule format.", "tips": []}


def chat_with_ai(message: str, context: dict) -> dict:
    """
    Free-form chat with the AI about scheduling.
    context may include tasks, habits, assignments.
    """
    now = datetime.now().strftime("%A, %d %B %Y, %H:%M")

    tasks_text = "\n".join([
        f"- {t.get('title','?')} (priority:{t.get('priority')}, due:{t.get('due_date','?')})"
        for t in context.get("tasks", [])[:10]
    ]) or "No tasks"

    prompt = f"""You are Persona, an AI productivity assistant for a student app.
Today: {now}

Student's pending tasks:
{tasks_text}

Student says: "{message}"

Reply helpfully in 2-3 sentences. If they ask to reschedule, suggest specific times.
If they ask about habits or studies, give motivating advice. Be warm and encouraging, like a smart friend."""

    try:
        raw_response = _generate_content(prompt)
        return {"reply": raw_response.strip()}
    except Exception as e:
        print(f"AI Chat Failed: {e}")
        return {"reply": f"Sorry, I had trouble connecting to my brain! ({e})"}
