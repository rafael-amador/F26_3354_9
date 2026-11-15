"""Generate CometFit icon assets from the team logo (TD-7).

Source: spec/07-logo/cometfit-logo-transparent.png (TD-7 §1). Output: app/client/assets/.
TD-7 §3: square, logo centered and padded on the app's off-white background (#F6F6F1, ui.tsx colors.bg),
never stretched (aspect ratio kept), original colors kept.
Usage (from the repo root):  python app/tools/make_icons.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "spec" / "07-logo" / "cometfit-logo-transparent.png"
OUT = ROOT / "app" / "client" / "assets"
OFF_WHITE = (0xF6, 0xF6, 0xF1)  # ui.tsx colors.bg "warm off-white background"


def fit(logo: Image.Image, box: int) -> Image.Image:
    """Scale the logo so its longer side is `box` px, keeping the aspect ratio (no stretch)."""
    scale = box / max(logo.size)
    size = (max(1, round(logo.width * scale)), max(1, round(logo.height * scale)))
    return logo.resize(size, Image.LANCZOS)


def square(logo: Image.Image, side: int, fraction: float, bg) -> Image.Image:
    """Square canvas of `side` px; logo's longer side = fraction * side, centered. bg=None -> transparent."""
    canvas = Image.new("RGBA", (side, side), (*bg, 255) if bg else (0, 0, 0, 0))
    art = fit(logo, round(side * fraction))
    canvas.alpha_composite(art, ((side - art.width) // 2, (side - art.height) // 2))
    return canvas


def main() -> None:
    logo = Image.open(SRC).convert("RGBA")
    OUT.mkdir(parents=True, exist_ok=True)

    # App icon (iOS needs an opaque icon -> RGB). A-58: logo width 80 % of the square.
    square(logo, 1024, 0.80, OFF_WHITE).convert("RGB").save(OUT / "icon.png", optimize=True)
    # Splash icon (shown centered on the off-white splash background).
    square(logo, 1024, 0.80, OFF_WHITE).save(OUT / "splash-icon.png", optimize=True)
    # Web favicon: small, so less padding (A-58).
    square(logo, 48, 0.90, OFF_WHITE).save(OUT / "favicon.png", optimize=True)
    # Android adaptive icon: launchers crop to a circle/shape inside the middle 66 % (safe zone),
    # so the logo's longer side is 60 % of the canvas (A-58).
    square(logo, 512, 0.60, None).save(OUT / "android-icon-foreground.png", optimize=True)
    Image.new("RGBA", (512, 512), (*OFF_WHITE, 255)).save(OUT / "android-icon-background.png", optimize=True)
    # Android themed (monochrome) icon: Android uses only the alpha channel, so the logo's shape in one
    # color (A-59). Same 432 px size as the template asset.
    mono = square(logo, 432, 0.60, None)
    alpha = mono.getchannel("A")
    solid = Image.new("RGBA", mono.size, (255, 255, 255, 255))
    solid.putalpha(alpha)
    solid.save(OUT / "android-icon-monochrome.png", optimize=True)
    # Logo for the sign-in flow screens (TD-7 §2), transparent, 2x of the 64 px display width.
    fit(logo, 256).save(OUT / "logo.png", optimize=True)

    for f in sorted(OUT.glob("*.png")):
        im = Image.open(f)
        print(f"{f.name}: {im.size[0]}x{im.size[1]} {im.mode}")


if __name__ == "__main__":
    main()
