import asyncio
from playwright.async_api import async_playwright
import re
import sys

async def main(user_code):
    async with async_playwright() as p:
        print("Connecting to Chrome...")
        browser = await p.chromium.connect_over_cdp("ws://127.0.0.1:9222/devtools/browser")
        context = browser.contexts[0]
        
        page = await context.new_page()
        await page.goto("https://microsoft.com/link")
        print("Opened microsoft.com/link")
        
        await page.wait_for_timeout(3000)
        
        # Enter code
        try:
            await page.wait_for_selector('input[name="otc"]', timeout=5000)
            await page.fill('input[name="otc"]', user_code)
            await page.keyboard.press("Enter")
            await page.wait_for_timeout(3000)
            print("Entered code")
        except:
            print("Could not find otc input")
            
        # Wait for "Choose an account" or "Pick an account"
        for _ in range(5):
            body_text = await page.inner_text('body')
            if "walleeve20@yahoo.com" in body_text:
                print("Clicking walleeve20@yahoo.com...")
                try:
                    await page.click('div:has-text("walleeve20@yahoo.com")')
                    await page.wait_for_timeout(3000)
                except:
                    pass
            
            # Click Continue or Accept
            try:
                await page.click('input[id="idSIButton9"], button[id="idBtn_Accept"], input[value="Continue"]')
                await page.wait_for_timeout(2000)
            except:
                pass
                
            if "You have signed in to the" in body_text or "You're signed in" in body_text:
                print("Successfully signed in!")
                break
                
            await asyncio.sleep(2)

if len(sys.argv) > 1:
    asyncio.run(main(sys.argv[1]))
