import asyncio
from playwright.async_api import async_playwright
import time
import re

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.connect_over_cdp("ws://127.0.0.1:9222/devtools/browser")
        context = browser.contexts[0]
        
        yahoo_page = None
        for page in context.pages:
            if "yahoo.com" in page.url:
                yahoo_page = page
                break
                
        if not yahoo_page:
            yahoo_page = await context.new_page()
            await yahoo_page.goto("https://mail.yahoo.com")
            
        print("Yahoo page ready.")
        
        ms_page = await context.new_page()
        await ms_page.goto("https://www.microsoft.com/link")
        
        await ms_page.wait_for_selector('input[name="otc"]')
        await ms_page.fill('input[name="otc"]', "XUKZPYGU")
        await ms_page.keyboard.press("Enter")
        
        await ms_page.wait_for_selector('input[name="loginfmt"]', timeout=10000)
        await ms_page.fill('input[name="loginfmt"]', "walleeve20@yahoo.com")
        await ms_page.keyboard.press("Enter")
        
        try:
            await ms_page.wait_for_selector('input[name="passwd"], div#idDiv_SAOTCS_Title, div[data-bind*="Email"]', timeout=8000)
            if await ms_page.locator('div#idDiv_SAOTCS_Title').is_visible() or await ms_page.locator('div[data-bind*="Email"]').is_visible():
                email_option = ms_page.locator('div[data-bind*="Email"]')
                if await email_option.is_visible():
                    await email_option.click()
                
                print("Checking yahoo mail...")
                await yahoo_page.bring_to_front()
                await yahoo_page.reload()
                
                await yahoo_page.wait_for_selector('div[data-test-id="virtual-list"] div[role="row"]', timeout=20000)
                await yahoo_page.locator('div[data-test-id="virtual-list"] div[role="row"]').first.click()
                
                await yahoo_page.wait_for_selector('div.msg-body')
                body_text = await yahoo_page.locator('div.msg-body').inner_text()
                
                match = re.search(r'\b(\d{7})\b', body_text)
                if match:
                    code = match.group(1)
                    print(f"Found code: {code}")
                    await ms_page.bring_to_front()
                    await ms_page.fill('input[name="iProofCode"], input[name="otc"]', code)
                    await ms_page.keyboard.press("Enter")
                else:
                    print("Code not found in email!")
        except Exception as e:
            print("Exception during auth flow:", e)
            
        try:
            await ms_page.wait_for_selector('input[value="Continue"], input[id="idSIButton9"]', timeout=5000)
            await ms_page.click('input[value="Continue"], input[id="idSIButton9"]')
            await ms_page.wait_for_selector('input[id="idSIButton9"]', timeout=5000)
            await ms_page.click('input[id="idSIButton9"]')
        except:
            pass
            
        await asyncio.sleep(5)
        print("Done Playwright script")
        
asyncio.run(main())
