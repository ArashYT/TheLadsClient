import asyncio
from playwright.async_api import async_playwright

async def main():
    async with async_playwright() as p:
        print("Connecting to Chrome...")
        browser = await p.chromium.connect_over_cdp("ws://127.0.0.1:9222/devtools/browser")
        context = browser.contexts[0]
        
        page = await context.new_page()
        await page.goto("https://www.xbox.com/en-CA/auth/msa?action=logIn")
        
        await page.wait_for_timeout(5000)
        print("URL:", page.url)
        
        # Take a screenshot to verify what it sees!
        await page.screenshot(path="xbox_screenshot.png")
        
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
            print("No 'I Accept' found. HTML snippet:")
            print(body_text[:1000])

asyncio.run(main())
