# Changes to the MicroEmulator sources

Everything in this directory is the upstream source except the changes in `PATCHES.diff` (unified diff against the
archive supplied by the project owner).

**Why:** some Teamobi games override `Canvas.getWidth()` / `getHeight()` to return -1 (they use their own
`getWidthz()`). MicroEmulator itself called those overridable methods to decide the repaint rectangle and whether the
canvas is full screen, so it asked to repaint a (0, 0, -1, -1) area and nothing but explicit partial repaints
ever reached the screen (black background, menus missing).

**What:** `Displayable.getEmuWidth()` / `getEmuHeight()` are final and return the same value as the original
`getWidth()` / `getHeight()`. MicroEmulator's own call sites (`Display`, `Displayable.repaint()`, `GameCanvas`, `J2SEDeviceDisplay`) use them.
The public `getWidth()` / `getHeight()` that the MIDlet sees are unchanged.
