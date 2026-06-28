import os

OUTPUT_FILE = "output.txt"
EXTENSIONS = (".java",)

with open(OUTPUT_FILE, "w", encoding="utf-8") as out:
    for root, _, files in os.walk("."):
        for name in sorted(files):
            if name.endswith(EXTENSIONS):
                path = os.path.join(root, name)

                out.write(f"# {path}\n")
                with open(path, "r", encoding="utf-8") as f:
                    out.write(f.read())

                out.write("\n\n")

print(f"Saved to {OUTPUT_FILE}")
