import subprocess
import re
import asyncio
from playwright.async_api import async_playwright

async def playwright_login(url):
    async with async_playwright() as p:
        browser = await p.chromium.launch(executable_path=r"C:\Program Files\Google\Chrome\Application\chrome.exe", headless=False)
        context = await browser.new_context()
        
        page = await context.new_page()
        asyncio.create_task(page.goto(url))
        print("Opened Interactive URL")
        
        await asyncio.sleep(3)
        
        for _ in range(15):
            print("Current URL:", page.url)
            if "?code=" in page.url or "&code=" in page.url:
                print("Redirected to desktop.srf!", page.url)
                if "code=" in page.url:
                    import urllib.parse
                    code = page.url.split("code=")[1].split("&")[0]
                    code = urllib.parse.unquote(code)
                    with open("auth_result.txt", "w") as f:
                        f.write(code)
                break

            try:
                body_text = await page.inner_text('body', timeout=1000)
                if "walleeve20@yahoo.com" in body_text:
                    print("Clicking walleeve20@yahoo.com...")
                    await page.click('div:has-text("walleeve20@yahoo.com")', timeout=1000)
                    await asyncio.sleep(2)
            except:
                pass
            
            try:
                await page.click('input[id="idSIButton9"], button[id="idBtn_Accept"], input[value="Continue"]', timeout=1000)
                await asyncio.sleep(2)
            except:
                pass
            
            await page.screenshot(path="screenshot.png")
            await asyncio.sleep(2)

def main():
    print("Starting Launcher...")
    process = subprocess.Popen(["dotnet", "run"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    
    for line in iter(process.stdout.readline, ''):
        print("LAUNCHER: " + line.strip())
        if "!!! OAUTH URL (INTERCEPT) !!!" in line:
            url = line.split("!!! OAUTH URL (INTERCEPT) !!!")[1].strip()
            print(f"FOUND OAUTH URL: {url}")
            asyncio.run(playwright_login(url))
        if "Checking for mod updates" in line or "Welcome," in line:
            print("LOGIN SUCCESSFUL!")
            
    process.stdout.close()
    process.wait()

if __name__ == "__main__":
    main()
