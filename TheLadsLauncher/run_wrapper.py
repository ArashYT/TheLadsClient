import subprocess
import re
import asyncio
from playwright.async_api import async_playwright

async def playwright_login(url):
    try:
        async with async_playwright() as p:
            browser = await p.chromium.launch(executable_path=r"C:\Program Files\Google\Chrome\Application\chrome.exe", headless=False)
            context = await browser.new_context()
            page = await context.new_page()
            asyncio.create_task(page.goto(url))
            print("Opened Interactive URL")
            
            await asyncio.sleep(3)
            
            for _ in range(120):
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
                    # Check what's visible
                    html = await page.content()
                    with open("page.html", "w", encoding="utf-8") as f:
                        f.write(html)
                    
                    
                    if await page.locator('input[name="otc"]').first.is_visible() or await page.locator('input[id="idTxtBx_OTC_Password"], input[type="tel"]').first.is_visible():
                        print("Waiting for code...")
                        with open("waiting_for_code.txt", "w") as f:
                            f.write("ready")
                        import os
                        if os.path.exists("code.txt"):
                            with open("code.txt", "r") as f:
                                my_code = f.read().strip()
                            if my_code:
                                print("Found code.txt! Typing code...")
                                await page.locator('input[name="otc"], input[id="idTxtBx_OTC_Password"], input[type="tel"]').first.fill(my_code)
                                await page.locator('button[data-testid="primaryButton"], input[type="submit"], input[id="idSIButton9"]').first.click()
                                os.remove("code.txt")
                    elif await page.locator('input[id="usernameEntry"], input[name="loginfmt"]').first.is_visible():
                        print("Typing email...")
                        await page.locator('input[id="usernameEntry"], input[name="loginfmt"]').first.fill('walleeve20@yahoo.com')
                        await page.locator('button[data-testid="primaryButton"], input[type="submit"], input[id="idSIButton9"]').first.click()
                    elif await page.locator('div:has-text("Send a code")').first.is_visible():
                        print("Clicking Send a code...")
                        await page.locator('div:has-text("Send a code")').first.click()
                    elif await page.locator('div[id="otherTile"]').first.is_visible():
                        print("Clicking Use another account...")
                        await page.locator('div[id="otherTile"]').first.click()
                    elif await page.locator('input[name="otc"]').first.is_visible() or await page.locator('input[id="idTxtBx_OTC_Password"], input[type="tel"]').first.is_visible():
                        print("Waiting for code...")
                        with open("waiting_for_code.txt", "w") as f:
                            f.write("ready")
                        import os
                        if os.path.exists("code.txt"):
                            with open("code.txt", "r") as f:
                                my_code = f.read().strip()
                            if my_code:
                                print("Found code.txt! Typing code...")
                                await page.locator('input[name="otc"], input[id="idTxtBx_OTC_Password"], input[type="tel"]').first.fill(my_code)
                                await page.locator('button[data-testid="primaryButton"], input[type="submit"], input[id="idSIButton9"]').first.click()
                                os.remove("code.txt")
                    elif await page.locator('div:has-text("Sign in another way")').first.is_visible():
                        print("Clicking Sign in another way...")
                        await page.locator('div:has-text("Sign in another way")').first.click()
                    elif await page.locator('button[data-testid="primaryButton"], input[id="idSIButton9"]').first.is_visible():
                        print("Clicking submit button... Also pressing ESC just in case of Windows Security prompt")
                        
                        # Reload the page to cancel the FIDO prompt!
                        print("Reloading page to cancel FIDO prompt...")
                        await page.reload()
                        await page.wait_for_timeout(3000)
                        
                except Exception as inner_e:
                    pass
                
                try:
                    await page.screenshot(path="screenshot.png")
                except Exception as e:
                    print("Screenshot failed:", e)
                await asyncio.sleep(2)
    except Exception as e:
        print("CRITICAL PLAYWRIGHT ERROR:", e)

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
