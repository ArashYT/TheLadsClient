import asyncio
from playwright.async_api import async_playwright

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.connect_over_cdp("ws://127.0.0.1:9222/devtools/browser")
        context = browser.contexts[0]
        page = await context.new_page()
        
        await page.goto("https://www.xbox.com/en-CA/auth/msa?action=logIn")
        await page.wait_for_timeout(5000)
        
        print("URL:", page.url)
        body = await page.inner_text('body')
        if "I Accept" in body or "Create Profile" in body or "Xbox profile" in body:
            print("Profile creation required!")
            
            try:
                await page.wait_for_selector('button:has-text("I Accept"), button:has-text("Create"), button#create-account-btn', timeout=5000)
                await page.click('button:has-text("I Accept"), button:has-text("Create"), button#create-account-btn')
                print("Clicked create/accept")
                await page.wait_for_timeout(5000)
                print("URL after:", page.url)
            except Exception as e:
                print("Error clicking:", e)
        else:
            print("No profile creation found. HTML snippet:")
            print(body[:500])
            
        
asyncio.run(main())
