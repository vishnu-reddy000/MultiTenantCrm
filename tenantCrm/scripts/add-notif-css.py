import os
import re

templates = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "templates")
for fn in os.listdir(templates):
    if not fn.endswith(".html"):
        continue
    path = os.path.join(templates, fn)
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    if "auth.js" not in content or "notifications.css" in content:
        continue
    updated = re.sub(
        r'(<link rel="stylesheet" th:href="@\{[^}]+\}">)',
        r'\1\n\t<link rel="stylesheet" th:href="@{/notifications.css}">',
        content,
        count=1,
    )
    if updated != content:
        with open(path, "w", encoding="utf-8") as f:
            f.write(updated)
        print(fn)
