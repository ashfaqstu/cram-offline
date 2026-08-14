#!/usr/bin/env python3
"""
Turn bench/results/*.csv into the charts and tables the README uses.

    python bench/analyze.py

Reads  bench/results/sweep_<device>.csv
Writes bench/results/*.png              and  bench/results/TABLES.md
"""
import os
import sys
import glob
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "results")

# Dark palette so the charts sit naturally next to the app screenshots.
BG, FG, MUTED = "#0f1315", "#e6eaec", "#7d888f"
ACCENT, WARN, CRIT = "#34cfc0", "#d8964f", "#e0736a"

plt.rcParams.update({
    "figure.facecolor": BG, "axes.facecolor": BG,
    "savefig.facecolor": BG, "text.color": FG,
    "axes.labelcolor": FG, "xtick.color": MUTED, "ytick.color": MUTED,
    "axes.edgecolor": "#2c3438", "grid.color": "#1e2529",
    "font.size": 11, "axes.titlesize": 13, "axes.titleweight": "bold",
})


def save(fig, name):
    p = os.path.join(RES, name)
    fig.tight_layout()
    fig.savefig(p, dpi=160)
    plt.close(fig)
    print("wrote", p)


def sweep_path():
    hits = sorted(glob.glob(os.path.join(RES, "sweep_*.csv")))
    if not hits:
        sys.exit("no sweep_*.csv in bench/results — run otbench first")
    return hits[0]


def main():
    df = pd.read_csv(sweep_path())
    df["tps"] = pd.to_numeric(df["tps"], errors="coerce")
    tables = []

    # ── Chart 1: the thread cliff ────────────────────────────────────────────
    a = df[(df["sweep"] == "A") & (df["affinity"] == "none")]
    if not a.empty:
        pp = a[a["test"] == "pp128"].sort_values("threads")
        tg = a[a["test"] == "tg32"].sort_values("threads")
        fig, ax = plt.subplots(figsize=(7.2, 4.2))
        ax.plot(pp["threads"], pp["tps"], "o-", color=ACCENT, lw=2.5, ms=8, label="prefill (pp128)")
        ax.plot(tg["threads"], tg["tps"], "o-", color=CRIT, lw=2.5, ms=8, label="decode (tg32)")
        if not tg.empty:
            worst = tg.loc[tg["tps"].idxmin()]
            best = tg.loc[tg["tps"].idxmax()]
            ax.annotate(
                f"{(1 - worst.tps / best.tps) * 100:.0f}% collapse\nat {int(worst.threads)} threads",
                xy=(worst.threads, worst.tps), xytext=(worst.threads - 2.4, worst.tps + 3.2),
                color=CRIT, fontsize=10, fontweight="bold",
                arrowprops=dict(arrowstyle="->", color=CRIT, lw=1.5))
        ax.set_xlabel("threads"); ax.set_ylabel("tokens / sec")
        ax.set_title("More threads is not more speed\nLlama 3.2 1B Q4_0 · 2x Cortex-A76 + 6x Cortex-A55")
        ax.grid(alpha=.3); ax.legend(facecolor=BG, edgecolor="#2c3438", labelcolor=FG)
        save(fig, "chart_threads.png")
        tables.append(("Threads x throughput (unpinned)",
                       a.pivot_table(index="threads", columns="test", values="tps")))

    # ── Chart 2: cluster comparison ──────────────────────────────────────────
    b = df[(df["sweep"] == "A") & (df["test"] == "tg32")]
    if not b.empty:
        rows, labels, colors = [], [], []
        for aff, t, lab, c in [("c0", 2, "2 threads\nbig only (A76)", ACCENT),
                               ("3f", 6, "6 threads\nLITTLE only (A55)", WARN),
                               ("none", 6, "6 threads\nboth clusters", CRIT)]:
            m = b[(b["affinity"] == aff) & (b["threads"] == t)]
            if not m.empty:
                rows.append(m["tps"].iloc[0]); labels.append(lab); colors.append(c)
        if rows:
            fig, ax = plt.subplots(figsize=(7.2, 4.0))
            bars = ax.bar(labels, rows, color=colors, width=.55)
            for bar, v in zip(bars, rows):
                ax.text(bar.get_x() + bar.get_width() / 2, v + .15, f"{v:.2f}",
                        ha="center", color=FG, fontweight="bold")
            ax.set_ylabel("decode tokens / sec")
            ax.set_title("Two big cores beat all six LITTLE cores\ndecode, Llama 3.2 1B Q4_0")
            ax.grid(axis="y", alpha=.3)
            save(fig, "chart_clusters.png")

    # ── Chart 3: the KleidiAI cliff (a null result, shown honestly) ──────────
    k = df[df["sweep"] == "B"]
    if not k.empty:
        piv = k.pivot_table(index=["quant", "binary"], columns="test", values="tps").reset_index()
        piv["kleidi"] = piv["binary"].apply(lambda s: "ON" if "nokleidi" not in s else "OFF")
        fig, ax = plt.subplots(figsize=(7.6, 4.2))
        quants = sorted(piv["quant"].unique())
        x = range(len(quants))
        on = [piv[(piv["quant"] == q) & (piv["kleidi"] == "ON")]["pp128"].mean() for q in quants]
        off = [piv[(piv["quant"] == q) & (piv["kleidi"] == "OFF")]["pp128"].mean() for q in quants]
        ax.bar([i - .18 for i in x], on, width=.34, color=ACCENT, label="KleidiAI ON")
        ax.bar([i + .18 for i in x], off, width=.34, color=MUTED, label="KleidiAI OFF")
        for i, (a_, b_) in enumerate(zip(on, off)):
            ax.text(i - .18, a_ + .2, f"{a_:.1f}", ha="center", color=FG, fontsize=9)
            ax.text(i + .18, b_ + .2, f"{b_:.1f}", ha="center", color=FG, fontsize=9)
        ax.set_xticks(list(x)); ax.set_xticklabels(quants)
        ax.set_ylabel("prefill tokens / sec")
        ax.set_title("KleidiAI changes nothing on Armv8.2-A\nits int4/int8 kernels require i8mm or SME; this CPU has neither")
        ax.grid(axis="y", alpha=.3)
        ax.legend(facecolor=BG, edgecolor="#2c3438", labelcolor=FG)
        save(fig, "chart_kleidiai.png")
        tables.append(("Quantization x KleidiAI", piv))


    with open(os.path.join(RES, "TABLES.md"), "w", encoding="utf-8") as f:
        f.write("# Measured results\n\nGenerated by `bench/analyze.py`.\n")
        for title, t in tables:
            f.write(f"\n## {title}\n\n{t.to_markdown(index=True)}\n")
    print("wrote", os.path.join(RES, "TABLES.md"))


if __name__ == "__main__":
    main()
