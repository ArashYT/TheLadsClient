import asyncio
from playwright.async_api import async_playwright
async def main():
    async with async_playwright() as p:
        browser=await p.chromium.launch(headless=True)
        page=await browser.new_page()
        await page.goto('https://login.live.com/oauth20_authorize.srf?response_type=code&redirect_uri=https%3a%2f%2flogin.live.com%2foauth20_desktop.srf&response_mode=query&prompt=select_account&client_id=00000000402b5328&scope=service%3a%3auser.auth.xboxlive.com%3a%3aMBI_SSL')
        await page.wait_for_load_state('networkidle')
        with open('first_page.html', 'w', encoding='utf-8') as f: f.write(await page.content())
        await browser.close()
asyncio.run(main())
