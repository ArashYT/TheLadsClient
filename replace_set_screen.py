import os

root_dir = r"C:\Users\Arash\Desktop\Lads Client\TheLadsCore\src\main\java"

for dirpath, _, filenames in os.walk(root_dir):
    for filename in filenames:
        if filename.endswith(".java"):
            filepath = os.path.join(dirpath, filename)
            try:
                with open(filepath, "r", encoding="utf-8") as f:
                    content = f.read()
                
                if ".setScreen(" in content:
                    new_content = content.replace(".setScreen(", ".setScreenAndShow(")
                    with open(filepath, "w", encoding="utf-8") as f:
                        f.write(new_content)
                    print(f"Updated {filename}")
            except Exception as e:
                print(f"Error processing {filename}: {e}")
