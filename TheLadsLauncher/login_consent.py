import asyncio
from playwright.async_api import async_playwright
import time

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.connect_over_cdp("ws://127.0.0.1:9222/devtools/browser")
        context = browser.contexts[0]
        ms_page = context.pages[-1]
        
        body = await ms_page.inner_text('body')
        print(f"Current page text:\n{body}")
        
        try:
            # Click Accept or Continue
            await ms_page.wait_for_selector('button[id="idBtn_Accept"], input[value="Accept"], button[id="idSIButton9"]', timeout=5000)
            await ms_page.click('button[id="idBtn_Accept"], input[value="Accept"], button[id="idSIButton9"]')
            print("Clicked Accept")
        except Exception as e:
            print("Failed to click accept:", e)
            
        await asyncio.sleep(5)
        print("Done")
        
asyncio.run(main())
