#!/usr/bin/env python3
"""
Render the two cutaway graphics the demo video needs, at the video's own
resolution (1080x1920 portrait) so they drop straight onto the timeline with no
scaling.

  bench/results/overlay_kleidiai_log.png    the library's own log, verbatim
  bench/results/overlay_kleidiai_ab.png     the same-binary A/B table

The log text is copied from a real run on the Poco M2 Pro:

  adb shell "cd /data/local/tmp/ot && ./llama-bench \
      -m Llama-3.2-1B-Instruct-Q4_0.gguf -p 8 -n 4 -r 1 -v 2>&1" | grep -i kleidiai

llama-bench installs a null log callback unless -v is passed, which is why the
lines are invisible in a normal run. Numbers in the A/B table come from
bench/results/sweep_POCO_M2_Pro.csv.
"""

from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

W, H = 1080, 1920
OUT = Path(__file__).resolve().parent / "results"

BG      = "#0d1117"
FG      = "#c9d1d9"
DIM     = "#6e7681"
GREEN   = "#7ee787"
AMBER   = "#f0a020"
RED     = "#ff7b72"
BLUE    = "#79c0ff"
PANEL   = "#161b22"
RULE    = "#30363d"


def font(size, mono=True, bold=False):
    """Consolas / Segoe UI where present, DejaVu as the portable fallback."""
    names = (
        ["consolab.ttf", "consola.ttf"] if mono and bold else
        ["consola.ttf"] if mono else
        ["segoeuib.ttf", "seguisb.ttf"] if bold else
        ["segoeui.ttf"]
    )
    names += ["DejaVuSansMono.ttf"] if mono else ["DejaVuSans.ttf"]
    for n in names:
        for base in (r"C:\Windows\Fonts", "/usr/share/fonts/truetype/dejavu"):
            p = Path(base) / n
            if p.exists():
                try:
                    return ImageFont.truetype(str(p), size)
                except OSError:
                    pass
    return ImageFont.load_default()


def log_overlay():
    """The library reporting, in its own words, that it is doing nothing."""
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    f_title = font(46, mono=False, bold=True)
    f_sub   = font(27, mono=False)
    f_code  = font(26)
    f_cmd   = font(24)
    f_note  = font(25, mono=False)

    # Content block is ~1090 px tall; start it so the frame is balanced rather
    # than top-heavy, since this fills the screen as a cutaway.
    y = 416
    d.text((60, y), "KleidiAI, on this phone", font=f_title, fill=FG)
    y += 68
    d.text((60, y), "Poco M2 Pro  ·  Snapdragon 720G  ·  Armv8.2-A",
           font=f_sub, fill=DIM)
    y += 74

    # The command, so the log is traceable rather than asserted.
    d.rounded_rectangle([50, y, W - 50, y + 104], 12, fill=PANEL, outline=RULE)
    d.text((72, y + 22), "$ llama-bench -m Llama-3.2-1B-Instruct-Q4_0.gguf",
           font=f_cmd, fill=GREEN)
    d.text((72, y + 58), "               -p 8 -n 4 -v", font=f_cmd, fill=GREEN)
    y += 150

    # (text, colour, indented?) — indent marks a wrapped continuation line.
    lines = [
        ("kleidiai: no compatible q4 kernels found",      AMBER, False),
        ("          for CPU features mask 1",             AMBER, False),
        ("kleidiai: no compatible q8 kernels found",      AMBER, False),
        ("          for CPU features mask 1",             AMBER, False),
        ("kleidiai: SME disabled",                        AMBER, False),
        ("", FG, False),
        ("done_getting_tensors: tensor 'token_embd.weight'", FG, False),
        ("  (q6_K) (and 162 others) cannot be used with",   FG, True),
        ("  preferred buffer type CPU_KLEIDIAI,",           FG, True),
        ("  using CPU instead",                             RED, True),
        ("", FG, False),
        ("kleidiai: no kernel for tensor type q6_K,",     AMBER, False),
        ("          not accelerated by KleidiAI",         RED,   False),
        ("          (kernels available for Q4_0 and Q8_0)", DIM, False),
    ]

    box_top = y - 16
    box_h = len(lines) * 42 + 44
    d.rounded_rectangle([50, box_top, W - 50, box_top + box_h], 12,
                        fill=PANEL, outline=RULE)
    y += 8
    for text, colour, _ in lines:
        if text:
            d.text((72, y), text, font=f_code, fill=colour)
        y += 42

    y = box_top + box_h + 66
    d.text((60, y), "Its int4 kernels require i8mm or SME.", font=f_note, fill=FG)
    y += 42
    d.text((60, y), "This CPU has dotprod and neither, so GGML", font=f_note, fill=FG)
    y += 42
    d.text((60, y), "falls back — silently, for 163 tensors.", font=f_note, fill=BLUE)

    p = OUT / "overlay_kleidiai_log.png"
    img.save(p)
    return p


def ab_overlay():
    """Same source, same flags, one define apart."""
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    f_title = font(46, mono=False, bold=True)
    f_sub   = font(27, mono=False)
    f_head  = font(28, mono=False, bold=True)
    f_cell  = font(44)
    f_lab   = font(31, mono=False, bold=True)
    f_note  = font(27, mono=False)

    # ~870 px of content, centred for the same reason as the log overlay.
    y = 525
    d.text((60, y), "We built it twice", font=f_title, fill=FG)
    y += 68
    d.text((60, y), "Same source, same flags, one define apart",
           font=f_sub, fill=DIM)
    y += 110

    x0, x1 = 50, W - 50
    col2, col3 = 560, 830

    d.text((x0 + 30, y), "Q4_0 weights", font=f_head, fill=DIM)
    d.text((col2, y), "prefill t/s", font=f_head, fill=DIM)
    d.text((col3, y), "decode t/s", font=f_head, fill=DIM)
    y += 52
    d.line([x0, y, x1, y], fill=RULE, width=2)
    y += 10

    for label, pre, dec, hot in (
        ("KleidiAI ON",  "18.75", "8.82", False),
        ("KleidiAI OFF", "17.48", "9.17", True),
    ):
        d.rounded_rectangle([x0, y, x1, y + 108], 12, fill=PANEL, outline=RULE)
        d.text((x0 + 30, y + 34), label, font=f_lab, fill=FG)
        d.text((col2, y + 26), pre, font=f_cell, fill=FG)
        # The point of the whole table: decode is faster with it switched off.
        d.text((col3, y + 26), dec, font=f_cell, fill=GREEN if hot else FG)
        y += 128

    y += 40
    d.text((60, y), "Seven percent apart, with no", font=f_note, fill=FG)
    y += 40
    d.text((60, y), "consistent sign — and decode is", font=f_note, fill=FG)
    y += 40
    d.text((60, y), "faster with it switched off.", font=f_note, fill=GREEN)
    y += 66
    d.text((60, y), "That is run-to-run noise,", font=f_note, fill=FG)
    y += 40
    d.text((60, y), "not acceleration.", font=f_note, fill=AMBER)

    y += 90
    d.line([x0, y, x1, y], fill=RULE, width=2)
    y += 34
    d.text((60, y), "bench/results/sweep_POCO_M2_Pro.csv", font=font(24), fill=DIM)

    p = OUT / "overlay_kleidiai_ab.png"
    img.save(p)
    return p


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for p in (log_overlay(), ab_overlay()):
        print(f"wrote {p}")
