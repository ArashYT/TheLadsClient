import re

with open("C:/Users/Arash/curseforge/minecraft/Instances/TheLadsCore/build_output.txt", "r", encoding="utf-16le") as f:
    text = f.read()

# Replace newlines that happen right after .java: or mid-number
text = re.sub(r'Tier1FeatureTests\.java:(\d*)\n(\d+): error:', r'Tier1FeatureTests.java:\1\2: error:', text)
text = re.sub(r'Tier1FeatureTests\.java:\n(\d+): error:', r'Tier1FeatureTests.java:\1: error:', text)

lines = text.split('\n')
reconstructions = {}

for i, line in enumerate(lines):
    match = re.search(r'Tier1FeatureTests\.java:(\d+): error:', line)
    if match:
        line_num = int(match.group(1))
        # The next line is the code
        code_line = lines[i+1].strip('\r\n')
        reconstructions[line_num] = code_line

for k in sorted(reconstructions.keys()):
    print(f"{k}: {reconstructions[k]}")
