"""Draws the Section 7.4 graphs from evidence/perf/*.csv.

Usage (from the repository root):  python docs/plot_perf.py
Needs matplotlib.  Writes PNG files next to the CSV files and into docs/.
"""
import csv
import os
import shutil

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

PERF = os.path.join("evidence", "perf")
DOCS = "docs"
BLUE, ORANGE, GREY = "#1f4e79", "#d9822b", "#8a8a8a"


def rows(name):
    with open(os.path.join(PERF, name), newline="") as f:
        return list(csv.DictReader(f))


def save(fig, name):
    path = os.path.join(PERF, name)
    fig.tight_layout()
    fig.savefig(path, dpi=150)
    shutil.copy(path, os.path.join(DOCS, name))
    plt.close(fig)
    print("wrote", path)


def local_vs_remote():
    r = {x["call"]: x for x in rows("local_vs_remote.csv")}
    lat = [float(r["local"]["mean_latency_us"]), float(r["remote"]["mean_latency_us"])]
    thr = [float(r["local"]["throughput_per_s"]), float(r["remote"]["throughput_per_s"])]
    fig, (a, b) = plt.subplots(1, 2, figsize=(8, 3.2))
    a.bar(["local", "remote (RMI)"], lat, color=[GREY, BLUE])
    a.set_yscale("log")
    a.set_ylabel("mean latency (µs, log scale)")
    a.set_title("getMark latency")
    for i, v in enumerate(lat):
        a.text(i, v, f"{v:.2f} µs", ha="center", va="bottom")
    b.bar(["local", "remote (RMI)"], thr, color=[GREY, BLUE])
    b.set_yscale("log")
    b.set_ylabel("calls per second (log scale)")
    b.set_title("getMark throughput, 1 thread")
    for i, v in enumerate(thr):
        b.text(i, v, f"{v:,.0f}/s", ha="center", va="bottom")
    save(fig, "local_vs_remote.png")


def threads():
    data = rows("threads.csv")
    fig, (a, b) = plt.subplots(1, 2, figsize=(9, 3.6))
    for mode, colour, label in (("one-core", BLUE, "1 simulated core"), ("all-cores", ORANGE, "4 real cores")):
        d = [x for x in data if x["mode"] == mode]
        w = [int(x["workers"]) for x in d]
        a.plot(w, [float(x["throughput_rps"]) for x in d], "o-", color=colour, label=f"measured, {label}")
        a.plot(w, [float(x["expected_rps"]) for x in d], "--", color=colour, alpha=0.6,
               label=f"calculated (4.3), {label}")
        b.plot(w, [float(x["mean_latency_ms"]) for x in d], "o-", color=colour, label=label)
    a.set_xscale("log", base=2)
    a.set_xticks([1, 2, 4, 8, 16], ["1", "2", "4", "8", "16"])
    a.set_xlabel("worker threads")
    a.set_ylabel("requests / second")
    a.set_title("Throughput (4 ms CPU + 6 ms disk)")
    a.legend(fontsize=7)
    b.set_xscale("log", base=2)
    b.set_xticks([1, 2, 4, 8, 16], ["1", "2", "4", "8", "16"])
    b.set_xlabel("worker threads")
    b.set_ylabel("mean latency (ms), 32 clients")
    b.set_title("Latency under load")
    b.legend(fontsize=7)
    save(fig, "threads.png")


def dashboard():
    d = rows("dashboard.csv")
    fig, ax = plt.subplots(figsize=(5.5, 3.2))
    labels, vals, cols = [], [], []
    for x in d:
        labels.append(f"{x['mode']}\n{x['net_delay_ms']} ms delay")
        vals.append(float(x["mean_ms"]))
        cols.append(BLUE if x["mode"] == "sequential" else ORANGE)
    ax.bar(labels, vals, color=cols)
    for i, v in enumerate(vals):
        ax.text(i, v, f"{v:.2f} ms", ha="center", va="bottom")
    ax.set_ylabel("time for 3 getMark calls (ms)")
    ax.set_title("Student dashboard: 3 calls (4.4)")
    save(fig, "dashboard.png")


if __name__ == "__main__":
    local_vs_remote()
    threads()
    dashboard()
