# Voxy Seam Fix (NeoForge 1.21.1)

Pins [voxy](https://github.com/MCRcortex/voxy)'s LOD fluid surfaces to one real plane, so the sea
stops stepping up by a whole voxel at every LOD ring.

This branch is the **NeoForge 1.21.1** port (the previous builds targeted Minecraft 26.1.2 on
Fabric).

## What it actually fixes

Voxy builds each LOD quad in LOD-voxel space and scales it by `lodScale = 2^lodLevel`. Two effects
stack up there:

1. **The indentation was scaled.** A fluid's top face sits 2/16 of a block low, and that offset was
   multiplied by `lodScale`, so the sea drooped a little more on every coarser ring.
2. **Mipping rounds fluids up.** Mipping never lets "air" win, so a fluid surface snaps to the top
   of whichever LOD voxel straddles it — the water plane lifts by one voxel per LOD level.

The mod fixes both in the shader that voxy compiles (`lod/quad_util.glsl::setupQuad`), and marks
which LOD models are fluids so nothing else on that Y gets moved along with the water.

## Requirements

| Thing | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Java | 21 |
| Voxy | any 1.21.1 build: a NeoForge port, or the Fabric jar loaded through Roxy |

Voxy itself is **not** bundled or redistributed by this mod — install it separately.

## Install

Drop `voxy-seam-fix-<version>.jar` into `mods/` next to voxy. There is nothing to configure to get
the fix; the config file exists only for the edge cases below.

## Compatibility

Everything the mod hooks **fails soft**:

* Every mixin injector is `require = 0` and the mixin config is `required: false`, so a voxy build
  with different signatures/classes produces a log warning instead of a crash.
* The shader patch is line-anchor based and all-or-nothing: if a voxy build reformats or renames the
  quad setup, the mod leaves the shader untouched rather than emitting GLSL that would not compile.
* The fluid surface height is baked into the generated GLSL from `baseSectionPos` instead of being
  pushed through voxy's scene uniform buffer, because the uniform layout differs between ports (some
  append `uEarthRadius`, `uColorFix`, … right after `cameraSubPos`). Touching a hard-coded offset
  would corrupt another mod's uniform.
* The `voxy` dependency in `neoforge.mods.toml` is optional and open-ended, because ports report
  wildly different versions under the same mod id.

Two `processTextureBakeResult` shapes are covered out of the box: 5 parameters, and 6 with the
`RenderType` layer that 1.21.1 builds have. (Mixin's `@Coerce` only works for primitive widening, so
an object argument cannot be coerced into a type-agnostic handler — a build whose layer type changed
simply does not match, and is skipped instead of crashing.)

If the log says the fluid hook did not apply and the sea still steps, set
`"pinTranslucentModels": true` in `config/voxy-seam-fix.json`: water is always a translucent model,
so the pin then falls back to translucent top faces.

## Config

`config/voxy-seam-fix.json`, created on first launch:

```json
{
  "enabled": true,
  "fluidSurfaceY": -1,
  "pinTranslucentModels": false
}
```

| Key | Meaning |
| --- | --- |
| `enabled` | Master switch; `false` leaves voxy's shaders completely untouched. |
| `fluidSurfaceY` | World Y to pin to. `-1` = the level's sea level (63 in the overworld). |
| `pinTranslucentModels` | Fallback for voxy builds this build cannot flag (see above). |

## Log lines worth knowing

| Line | Meaning |
| --- | --- |
| `marked <block> as a fluid LOD model, the voxy hook is live` | The model hook matched this voxy build. |
| `pinned fluid surfaces to y=<n> in <shader>` | The shader patch was applied (this is what fixes the seams). |
| `voxy's setupQuad no longer matches the anchors …` | This build cannot patch that voxy's shader; nothing was changed. |
| `voxy's bake result does not expose its model buffer …` | The model hook did not match; consider `pinTranslucentModels`. |

## Building

```bash
./gradlew build      # jar in build/libs
./gradlew test       # shader patcher unit tests, no game required
```

The jar lands in `build/libs/`. It compiles against ModDevGradle on the NeoForge 21.1 / Java 21
toolchain, and against compile-only stubs of the handful of voxy members it references
(`src/voxyStubs`), since voxy is all-rights-reserved and is not published to any maven. Those stubs
are excluded from the jar (CI fails the build if `me/cortex/**` ever shows up inside it).

## Licence

MIT, matching the original project.
