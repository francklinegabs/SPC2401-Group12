"""Renders real program output from evidence/ as terminal-style PNG captures.

Usage (repository root):  python docs/make_captures.py
Needs Google Chrome or Microsoft Edge (headless) for the PNG step.
Each capture shows the exact text of the file it names; nothing is edited.
"""
import html
import os
import re
import subprocess

OUT = os.path.join("evidence", "screenshots")
EXP = os.path.join("evidence", "experiments")
INT = os.path.join("evidence", "logs", "integration")

CAPTURES = [
    ("01-integration-server", "Server process - IT01 integration test", [os.path.join(INT, "server.out")]),
    ("02-integration-students", "Two student processes - notification arrives",
     [os.path.join(INT, "student-KYU001.out"), os.path.join(INT, "student-KYU002.out")]),
    ("03-integration-lecturer", "Lecturer process - submit, invalid mark, publish, list",
     [os.path.join(INT, "lecturer.out")]),
    ("04-junit-summary", "JUnit run - 31 tests", [os.path.join("evidence", "test-output.txt")]),
    ("05-A2-crash-after-apply", "Experiment A2 - server killed after storing; retry ignored as duplicate",
     [os.path.join(EXP, "A2-server-killed-after-apply", "lecturer.out"),
      os.path.join(EXP, "A2-server-killed-after-apply", "server-2.out")]),
    ("06-B-offline-student", "Experiment B - offline student skipped and removed",
     [os.path.join(EXP, "B-subscriber-offline", "server.out")]),
    ("07-C-network-delay", "Experiment C - 1500 ms delay: timeouts, one apply, duplicates ignored",
     [os.path.join(EXP, "C-network-delay", "lecturer-1500ms.out"),
      os.path.join(EXP, "C-network-delay", "server.out")]),
]

BROWSERS = [r"C:\Program Files\Google\Chrome\Application\chrome.exe",
            r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
            "google-chrome", "chromium"]


def colour(line):
    s = html.escape(line)
    s = re.sub(r"\[(FAILURE)\]", r'<span class="f">[\1]</span>', s)
    s = re.sub(r"\[(WARN)\]", r'<span class="w">[\1]</span>', s)
    s = re.sub(r"^(\d{4}-\d\d-\d\dT[\d:.]+[-+]\d\d:\d\d)", r'<span class="ts">\1</span>', s)
    s = s.replace("✔", '<span class="ok">✔</span>')
    return s


def text_of(path, title):
    with open(path, encoding="utf-8", errors="replace") as f:
        lines = f.read().splitlines()
    if path.endswith("test-output.txt"):
        lines = [l for l in lines if l.strip()][-48:]
    # shorten the long RMI thread names so lines fit
    lines = [re.sub(r"\[RMI TCP Connection\((\d+)\)-[\d.]+\]", r"[RMI-\1]", l) for l in lines]
    return lines


def build(name, title, files):
    body = []
    for p in files:
        body.append(f'<div class="cmd">$ type {html.escape(p)}</div>')
        body.extend(f"<div>{colour(l)}</div>" for l in text_of(p, title))
    page = f"""<!doctype html><meta charset="utf-8"><style>
body{{margin:0;background:#1e1e1e;font:12.5px/1.45 Consolas,'Courier New',monospace;color:#d4d4d4}}
.bar{{background:#323233;color:#ccc;padding:6px 12px;font:12px Segoe UI,Arial}}
.bar b{{color:#fff}} .dots span{{display:inline-block;width:11px;height:11px;border-radius:50%;margin-right:6px}}
.wrap{{padding:10px 14px;white-space:pre-wrap;word-break:break-all}}
.cmd{{color:#4ec9b0;margin-top:6px}} .ts{{color:#808080}} .f{{color:#f48771;font-weight:700}}
.w{{color:#dcdcaa}} .ok{{color:#6a9955}}
</style><div class="bar"><span class="dots"><span style="background:#ff5f57"></span><span style="background:#febc2e"></span><span style="background:#28c840"></span></span><b>{html.escape(title)}</b></div>
<div class="wrap">{''.join(body)}</div>"""
    os.makedirs(OUT, exist_ok=True)
    h = os.path.abspath(os.path.join(OUT, name + ".html"))
    with open(h, "w", encoding="utf-8") as f:
        f.write(page)
    rows = 0
    for p in files:
        rows += 1 + sum(max(1, -(-len(l) // 160)) for l in text_of(p, title))
    height = 40 + rows * 18 + 30
    png = os.path.abspath(os.path.join(OUT, name + ".png"))
    for b in BROWSERS:
        try:
            subprocess.run([b, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                            f"--window-size=1500,{height}", f"--screenshot={png}",
                            "file:///" + h.replace("\\", "/")], check=True,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=60)
            os.remove(h)
            print("wrote", png)
            return
        except (OSError, subprocess.SubprocessError):
            continue
    print("no headless browser found; kept", h)


if __name__ == "__main__":
    for c in CAPTURES:
        build(*c)
