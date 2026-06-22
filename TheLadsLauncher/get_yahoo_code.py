import time, os, re, pyautogui, pyperclip, pygetwindow as gw
def get_code():
    for _ in range(5):
        windows = gw.getWindowsWithTitle('Yahoo Mail')
        if not windows: return print('Yahoo Mail window not found!')
        win = windows[0]
        try: win.activate()
        except: pass
        time.sleep(1)
        pyautogui.click(win.left + 500, win.top + 300)
        time.sleep(0.5)
        pyautogui.hotkey('ctrl', 'a')
        time.sleep(0.5)
        pyautogui.hotkey('ctrl', 'c')
        time.sleep(0.5)
        text = pyperclip.paste()
        match = re.search(r'code is:?\s*(\d{7})', text, re.IGNORECASE)
        if not match: match = re.search(r'\b(\d{7})\b', text)
        if match:
            code = match.group(1)
            print('FOUND YAHOO CODE:', code)
            with open('code.txt', 'w') as f: f.write(code)
            return code
        print('Code not found, waiting 3s...')
        time.sleep(3)
if __name__ == '__main__':
    while True:
        if os.path.exists('waiting_for_code.txt'):
            print('Launcher is waiting for code...')
            get_code()
            os.remove('waiting_for_code.txt')
        time.sleep(1)
