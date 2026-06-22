import asyncio
from playwright.async_api import async_playwright

async def main():
    async with async_playwright() as p:
        print("Connecting to Chrome...")
        browser = await p.chromium.connect_over_cdp("ws://127.0.0.1:9222/devtools/browser")
        context = browser.contexts[0]
        
        page = await context.new_page()
        await page.goto("https://www.xbox.com/en-CA/auth/msa?action=logIn")
        
        await page.wait_for_timeout(3000)
        
        body_text = await page.inner_text('body')
        if "walleeve20@yahoo.com" in body_text:
            print("Clicking account...")
            try:
                await page.click('div:has-text("walleeve20@yahoo.com")')
                await page.wait_for_timeout(5000)
            except Exception as e:
                print("Error clicking:", e)
        
        print("URL after:", page.url)
        body_text = await page.inner_text('body')
        
        if "I Accept" in body_text or "Xbox profile" in body_text:
            print("NEEDS PROFILE CREATION!")
            try:
                await page.click('button:has-text("I Accept")')
                await page.wait_for_timeout(5000)
                print("Accepted!")
            except:
                pass
        else:
            print("Finished check")

asyncio.run(main())
