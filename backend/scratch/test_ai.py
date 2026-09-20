import asyncio
import os
import sys

# add backend to sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))

from app.config import get_settings
from app.services.meal_ai_client import AzureMaaSMealClient


async def main():
    """테스트용 메인 함수."""
    settings = get_settings()
    print("Endpoint:", settings.deepseek_endpoint)
    
    client = AzureMaaSMealClient()
    
    profile_summary = "- 키: 175cm\n- 몸무게: 70kg\n- 체지방률: 15%\n- 골격근량: 33kg"
    target_macros = {
        "total_calories": 2500,
        "protein_g": 150,
        "carbs_g": 300,
        "fat_g": 70,
    }
    workout_dates = ["2026-09-21", "2026-09-23", "2026-09-25"]
    
    print("Sending request to DeepSeek...")
    try:
        result = await client.generate_meal_plan(profile_summary, target_macros, workout_dates)
        print("SUCCESS!")
        print(result.model_dump_json(indent=2))
    except Exception:
        print("FAILED!")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    asyncio.run(main())
