# Your own shapes

Put a **square PNG** in this folder and it becomes a new shape. Nothing else to do.

- Name it with lowercase letters, digits and `_`, starting with a letter: `glassy.png`, `blob_2.png`.
- It is used **as it is**, so draw the whole background yourself (colour, gradient, shadow, transparent corners).
  The icon glyph is drawn on top, centred.
- Any size works (512x512 is plenty).

Then run `./build.sh --shape glassy` (or pick it in the app's Shape row).

The PNGs already here (`glassy`, `gradient_tile`, `ring`, `neon`, `paper`, `gem`, `sticker`) are **placeholders** to try the
feature. They are simple generated images: replace or delete any of them.
