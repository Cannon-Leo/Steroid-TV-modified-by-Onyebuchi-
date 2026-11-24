#!/usr/bin/env python3
"""Depth effect masks for the screensaver (HyperOS style: the photo's subject stands in front of the clock).

  python3 depth.py <folder>     writes <photo>.mask.png beside each photo that has a clear subject

Uses rembg (pip install "rembg[cpu]"; the model downloads once, about 180 MB). Photos without a clear
subject (landscapes, skies) get no mask and simply show the clock in front, as normal.
Masks are small greyscale PNGs; Little Office serves them to the TV with your photos.
"""
import os
import sys

from PIL import Image, ImageFilter, ImageOps


def main(folder):
    import json
    print("Checking photos for a clear subject (the first time downloads the cut out model, about 180 MB)...", flush=True)
    from rembg import new_session, remove
    session = new_session("isnet-general-use")
    made = skipped = 0
    memo_path = os.path.join(folder, ".depth-checked.json")  # photos already checked and left without depth
    try:
        memo = json.load(open(memo_path))
    except Exception:
        memo = {}
    for n in sorted(os.listdir(folder)):
        low = n.lower()
        if ".mask." in low or not low.endswith((".jpg", ".jpeg", ".png", ".webp")) or low.startswith("profile."):
            continue
        out = os.path.join(folder, n + ".mask.png")
        src = os.path.join(folder, n)
        if os.path.exists(out) and os.path.getmtime(out) >= os.path.getmtime(src):
            continue
        if memo.get(n) == int(os.path.getmtime(src)):
            continue  # checked before: no depth for this one
        im = ImageOps.exif_transpose(Image.open(src)).convert("RGB")  # upright, as the TV shows it
        if im.width > 1280:
            im = im.resize((1280, round(im.height * 1280 / im.width)), Image.LANCZOS)
        m = remove(im, session=session, only_mask=True, post_process_mask=True).convert("L")
        m = m.filter(ImageFilter.GaussianBlur(1.2))                   # soft, natural edge
        hist = m.histogram()
        cover = sum(hist[128:]) / (m.width * m.height)
        box = m.point(lambda v: 255 if v > 128 else 0).getbbox()
        # how the TV frames it (16:9, filling the screen) and where the big clock sits
        fw, fh = (m.width, round(m.width * 9 / 16)) if m.width / m.height < 16 / 9 else (round(m.height * 16 / 9), m.height)
        fx, fy = (m.width - fw) // 2, (m.height - fh) // 2
        frame = m.crop((fx, fy, fx + fw, fy + fh))
        clock = frame.crop((int(fw * 0.30), int(fh * 0.19), int(fw * 0.70), int(fh * 0.41)))
        over = sum(clock.point(lambda v: 1 if v > 128 else 0).getdata()) / (clock.width * clock.height)
        solid = cover * m.width * m.height / max(1, (box[2] - box[0]) * (box[3] - box[1])) if box else 0
        # depth only when it reads like HyperOS: a solid subject (not thin lines or scattered bits)
        # that overlaps part of the clock, never most of it (the time must stay readable)
        if not box or cover < 0.04 or cover > 0.65 or solid < 0.24 or over < 0.03 or over > 0.45:
            if os.path.exists(out):
                os.remove(out)
            skipped += 1
            memo[n] = int(os.path.getmtime(src))
            print(f"no depth: {n}", flush=True)
            continue
        m.save(out, optimize=True)
        memo.pop(n, None)
        made += 1
        print(f"depth mask: {n}", flush=True)
    with open(memo_path, "w") as f:
        json.dump(memo, f)
    print(f"{made} masks made, {skipped} photos without a clear subject", flush=True)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/.little-office/photos"))
