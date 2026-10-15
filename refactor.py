import os

base_dir = os.path.dirname(os.path.abspath(__file__))
roots = [os.path.join(base_dir, "TheLadsCore", "src"), os.path.join(base_dir, "TheLadsClientMod", "src")]

for root in roots:
    for dirpath, _, filenames in os.walk(root):
        for f in filenames:
            if not f.endswith(".java"): continue
            p = os.path.join(dirpath, f)
            with open(p, "r", encoding="utf-8") as file:
                content = file.read()
            if "CycleOption" in content:
                # First rename the class itself if it hasn't been deleted
                if f == "CycleOption.java":
                    continue
                new_content = content.replace("import com.thelads.core.config.CycleOption;", "import com.thelads.core.config.DropdownOption;\nimport com.thelads.core.config.SliderOption;")
                new_content = new_content.replace("CycleOption", "DropdownOption")
                with open(p, "w", encoding="utf-8") as file:
                    file.write(new_content)
