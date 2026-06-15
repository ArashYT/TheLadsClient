import asyncio
from playwright.async_api import async_playwright

async def main():
    async with async_playwright() as p:
        print("Connecting to Chrome...")
        browser = await p.chromium.connect_over_cdp("ws://127.0.0.1:9222/devtools/browser")
        context = browser.contexts[0]
        
        while True:
            target_page = None
            for page in context.pages:
                if "login.live.com" in page.url:
                    target_page = page
                    break
            
            if target_page:
                print("Found login page:", target_page.url)
                await target_page.bring_to_front()
                body_text = await target_page.inner_text('body')
                if "walleeve20@yahoo.com" in body_text:
                    print("Clicking account...")
                    try:
                        await target_page.click('div:has-text("walleeve20@yahoo.com")')
                        await target_page.wait_for_timeout(3000)
                    except Exception as e:
                        print("Error clicking:", e)
                
                try:
                    await target_page.click('input[id="idSIButton9"], input[value="Continue"]')
                    await target_page.wait_for_timeout(2000)
                except:
                    pass
            else:
                print("Waiting for login page...")
            
            await asyncio.sleep(2)

asyncio.run(main())
