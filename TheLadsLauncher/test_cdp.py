import asyncio
from playwright.async_api import async_playwright

async def run():
    async with async_playwright() as p:
        print('connecting')
        try:
            browser = await p.chromium.connect_over_cdp('ws://127.0.0.1:9222/devtools/browser')
            print('connected')
            context = browser.contexts[0]
            print('got context')
            page = await context.new_page()
            print('new page created')
            await page.close()
            await browser.close()
        except Exception as e:
            print("Error:", e)

asyncio.run(run())
