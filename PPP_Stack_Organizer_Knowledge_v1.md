# PPP_Stack_Organizer — Project Knowledge Base
Provenzano Lab, University of Minnesota
GitHub: cdzahm/PPP_Stack_Organizer (repo root = project root)
Local: /Users/cdz/PPP Lab Files/Coding Projects/ImageJ_Plugins/PPP_Stack_Organizer/

---

## Stack
- Language: Java (IJ1-style), Java 8 (Azul Zulu JDK 8)
- Build: Maven 3.9.16, pom-scijava parent (v42.0.0, verify at setup)
- Deploy: `mvn -Dscijava.app.directory=/Applications/Fiji`
- Distribution: GitHub Releases (.jar); lab installs by dropping into Fiji plugins/ folder
- Package: `provenzano_lab`
- Artifact ID: `PPP_Stack_Organizer`
- Requires: Fiji (not vanilla ImageJ); Bio-Formats (bundled with Fiji) — still required for IMPORT even though output is no longer OME-TIFF
- All dialogs use GenericDialog (macro-recordable)
- All plugins implement `ij.plugin.PlugIn` (`run(String arg)`)

---

## Plugin
- **Name:** PPP Stack Organizer
- **Class:** `provenzano_lab.StackOrganizer`
- **Menu path:** `Plugins > PPP Lab > Stack Organizer` (registered via `plugins.config` as `Plugins>PPP Lab, "Stack Organizer", provenzano_lab.StackOrganizer`)
- **Status:** Complete ✅

---

## v0.2.0 Breaking Changes (from v0.1.0)
1. **Import is XML-only now.** `.companion.ome` support removed entirely (dual-path design from an earlier iteration of v0.2.0 development was simplified back to one path). Always imports via the acquisition's PrairieView `.xml`. Rationale: lab-wide drive sample (~5000 files, 10 project folders) found `.companion.ome` co-occurs with `.xml` 99%+ of the time with no raw-files-missing cases, so `.xml` alone covers virtually everything `.companion.ome` did, plus the ~1000+ pre-Sept-2024 acquisitions that only ever had `.xml`.
2. **Output is standard ImageJ TIFF (`.tif`), not OME-TIFF (`.ome.tif`).** Written via `ij.io.FileSaver`, not `OMETiffWriter`. Rationale: outputs are single-channel/single-position — none of OME-TIFF's cross-vendor/multi-series value applies; avoids forcing downstream reads through Bio-Formats/JVM; standard TIFF already carries full calibration via the `Calibration` object and is directly readable by Python's `tifffile` without Bio-Formats.
3. Existing v0.1.0 `.ome.tif` output files on disk are untouched — no migration needed, this only affects new runs.

---

## Bio-Formats API Notes (verified during compile and runtime)
- Import: `options.setStackFormat(ImporterOptions.VIEW_HYPERSTACK)` and `options.setStackOrder(ImporterOptions.ORDER_XYCZT)` — NOT `setViewHyperstack()`
- **`openWithBioFormats()` uses `setConcatenate(false)`, not `true`.** Real bug caught during testing: with `setConcatenate(true)` (carried over from `.companion.ome`-era code), the lower-level `ImportProcess.getSeriesCount()` API correctly reports 2 separate XY-position series, but the full `BF.openImagePlus()` pixel-reading pathway silently re-merges them into one interleaved `SizeT=120` stack anyway — the lower-level check does not reveal this. With `setConcatenate(false)`, `BF.openImagePlus()` returns `imps.length == 2` as expected, each already per-position `SizeT`. Verified against both real 2-position sample datasets.
- Export now uses `ij.io.FileSaver.saveAsTiff(String)` — standard ImageJ TIFF writer, same as *File > Save As > Tiff*. (The old `OMETiffWriter`-based export notes below are retained for history/context only — no longer used.)
- ~~Export: use `OMETiffWriter` directly~~ — REMOVED in v0.2.0. Old notes: NOT `LociExporter` (triggers interactive dialog) and NOT default `ImageWriter` (defaults to LZW which is extremely slow on large hyperstacks); compression/sequential flags had to be set before `setId()`.
- Dependencies in pom.xml: `ome:bio-formats_plugins` (for loci.plugins.*), `ome:formats-api`, `ome:formats-gpl` — all still required (import-side only now)
- scijava-maven-plugin uses `populate-app` goal (v3.x rename of `copy-jars`)
- Enforcer skipped via `<enforcer.skip>true</enforcer.skip>` (appropriate for internal lab plugin)

---

## Fiji Installation Notes
- Fiji lives at `/Applications/Fiji/Fiji.app` — the deploy target is `/Applications/Fiji` (the parent)
- Maven deploys to `/Applications/Fiji/plugins/` — this is what Fiji actually reads
- `/Applications/Fiji/Fiji.app/plugins/` also exists but is NOT the correct deploy target
- `plugins.config` label must differ from the last menu folder name to avoid Fiji creating a submenu; use `Plugins>PPP Lab, "Stack Organizer", ...` not `Plugins>PPP Lab>Stack Organizer, "Stack Organizer", ...`

---

## Input Data — PrairieView `.xml` (only supported format as of v0.2.0)
- One PVScan `.xml` per acquisition folder (e.g. `TSeries-NNN/*.xml`), alongside per-plane `*_Cycle#####_Ch#_*.ome.tif` files
- Opened via `loci.formats.in.PrairieReader` — already bundled in `formats-gpl`, no new dependency, auto-detected via `setId()`
- **Confirmed via direct testing:** `BF.openImagePlus()` with `setConcatenate(false)` returns **one `ImagePlus` per XY position already** (a 2-position, 120-cycle sample opened as `imps.length == 2`, each `SizeT=60`) — so **no de-interleave math exists anywhere in this plugin**. `setConcatenate(true)` must NOT be used here — see the Bio-Formats API Notes section above for the bug this caused.
- Channel names (`Ch2`/`Ch3`/`Ch4`) confirmed via direct testing against real 2-channel and 3-channel files — existing role table applies unchanged
- `IMetadata.getPixelsPhysicalSizeZ()` / `getPixelsTimeIncrement()` return `null` (confirmed) — read from the XML directly via `PrairieXmlUtils` instead; Prairie's XML is unambiguously in microns, so no unit-forcing workaround needed
- `BioFormatsUtils.resolveAcquisitionPath(File)` resolves a user-selected file or folder to the `.xml` path to open: the file itself if it's `.xml`, else the sole `.xml` in the selected folder (throws on ambiguity)

---

## Plugin Status
| Plugin | Class | Status |
|--------|-------|--------|
| Stack Organizer | `StackOrganizer` | Complete ✅ |

---

## Repo Structure
```
PPP_Stack_Organizer/          ← repo root
├── pom.xml
├── README.md
├── DESIGN.md
├── .gitignore
└── src/main/
    ├── java/provenzano_lab/
    │   ├── utils/
    │   │   ├── BioFormatsUtils.java
    │   │   ├── CalibrationUtils.java
    │   │   ├── LogUtils.java
    │   │   └── PrairieXmlUtils.java
    │   └── StackOrganizer.java
    └── resources/plugins.config
```

(`PositionUtils.java` — from an earlier dual-path iteration's `.companion.ome` stage-position clustering — was removed entirely in v0.2.0; it had no purpose once `.companion.ome` import was dropped.)

---

## StackOrganizer — What It Does

### The Problem (and why there's no de-interleaving)
Bio-Formats/`PrairieReader` already returns one series per XY position when opening the
PrairieView `.xml` (confirmed via direct testing) — so the plugin only needs to
channel-split each position's `ImagePlus`, never de-interleave. (An earlier dual-path
iteration of this plugin also supported `.companion.ome`, whose Bio-Formats series WAS one
interleaved stack requiring `srcFrame = t*nXY+xyIdx` de-interleave math — that path and its
math were removed entirely when `.companion.ome` import was dropped.)

### Single-Path Flow
`StackOrganizer.processFile()` opens the `.xml` via `BioFormatsUtils.openWithBioFormats()`,
which returns `ImagePlus[]` with one entry per XY position (`imps.length` = real nXY). For
each position, `writeXYPosition()` builds the multi-channel LUT-colored composite and each
active-role single-channel stack directly from that position's own `ImagePlus` (no frame
remapping needed — `t` maps straight to that position's own frame `t`).

### Channel Hardware Mapping
Fixed Bruker detector assignments; names preserved through `PrairieReader` into `IMetadata`:
| Channel Name | Default Role | Output Suffix |
|---|---|---|
| `Ch1` | Ignore | — |
| `Ch2` | tumor | `_tumor` |
| `Ch3` | tcells | `_tcells` |
| `Ch4` | collagen | `_collagen` |

Role labels are user-editable in the parameter dialog. 2-channel acquisitions (Ch3/Ch4 only) handled correctly via name-based routing — confirmed against real 2-channel and 3-channel files.

### Acquisition Defaults (all user-editable)
| Param | Default | Source |
|-------|---------|--------|
| nXY | Bio-Formats series count | Auto-detected; editable but informational only — real per-file series count always used |
| nZ | From metadata | Metadata (editable) |
| nT (per position) | From metadata | Metadata (read-only) — already per-position, no total-across-positions concept |
| nC | From metadata | Metadata (editable); supports 2–4 |
| Time interval | Real value from XML | `PrairieXmlUtils.readFrameIntervalSec()` (editable) |
| Pixel size XY | metadata | Metadata (editable) |
| Voxel depth Z | XML | `PrairieXmlUtils.readVoxelDepthUm()` (editable) |

### Dialog Flow
1. Mode dialog: Single file / Batch folder + file/folder browser
2. Parameter dialog: pre-filled from metadata where possible, all editable

### Bio-Formats Import
```java
ImporterOptions options = new ImporterOptions();
options.setId(path);   // path to the PrairieView .xml
options.setOpenAllSeries(true);
options.setConcatenate(false);  // NOT true — see Bio-Formats API Notes above
options.setStackFormat(ImporterOptions.VIEW_HYPERSTACK);
options.setStackOrder(ImporterOptions.ORDER_XYCZT);
return BF.openImagePlus(options);
// imps.length == real nXY; imps[xyIdx] already that position's own stack.
```

### Standard TIFF Export (BioFormatsUtils.saveAsTiff) — v0.2.0
```java
FileSaver saver = new FileSaver(imp);
saver.saveAsTiff(outputPath);
```
Calibration (pixel size, voxel depth, unit, frame interval) and channel LUTs/composite mode
travel with the `ImagePlus` automatically — no separate metadata construction needed.
Confirmed via direct round-trip testing: reopening a saved file shows correct dimensions,
calibration, and LUT coloring.

### Output
- Format: standard ImageJ TIFF (`.tif`), **not** OME-TIFF (v0.1.0 used `.ome.tif` — breaking change)
- Naming: `{filename}_XY01_tcells.tif`, `{filename}_XY01_tumor.tif`, etc.
- Location: `{same dir as .xml}/processed/`
- Calibration: XY+Z from `PrairieXmlUtils`/metadata (both unambiguously microns), time interval from `PrairieXmlUtils`/user input
- Hyperstack: XYCZT order, single channel (or LUT-colored composite), full Z and T extent per position

### Modes
**Single file:** save outputs + leave hyperstacks open in Fiji
**Batch:** recursive subfolder search for the sole `.xml` per acquisition folder, same params for all, silent (no windows), progress log, skip+log failures, prominent failure summary at end

### Logging
- Single: file opened → params detected → each output saved with filename only (not full path)
- Batch: `[ 3/12 ] Saved: /path/...` → final summary → failure block if any (failures show full path)

---

## Shared Utilities (provenzano_lab.utils)
Never duplicate these.

### BioFormatsUtils
- `openWithBioFormats(String path)` — opens a PrairieView .xml, returns ImagePlus[] (one per XY position)
- `resolveAcquisitionPath(File selected)` — resolves a user-selected file or folder to the `.xml` path to open (throws on ambiguity)
- `readSourceMetadata(String xmlPath)` — reads IMetadata (channel names/counts) via ImageReader/OMEXMLService — used only for role-mapping/validation, not for writing output metadata
- `saveAsTiff(ImagePlus imp, String outputPath)` — saves via `ij.io.FileSaver.saveAsTiff()`, standard ImageJ TIFF (v0.2.0; replaces the removed `saveAsOMETIFF`/`saveChannelAsOMETIFF`)

### PrairieXmlUtils
- `readVoxelDepthUm(String xmlPath)` — Z voxel depth from `PVStateValue key="micronsPerPixel"` / `ZAxis`
- `readFrameIntervalSec(String xmlPath)` — frame interval from the first two `<Sequence>` `@time` deltas
- For the two fields `IMetadata` returns `null` for

### CalibrationUtils
- `applyCalibration(ImagePlus imp, double pixelWidth, double pixelHeight, double voxelDepth, double frameIntervalSec)` — sets calibration, forces microns
- `readCalibration(ImagePlus imp)` — returns double[] {pixelWidth, pixelHeight, voxelDepth}
- Unchanged in v0.2.0 — same `Calibration` object now consumed by `FileSaver` instead of `OMETiffWriter`

### LogUtils
- `log(String msg)` — timestamped IJ.log entry
- `batchProgress(int current, int total, String savedPath)` — formats as `[ 3/12 ] Saved: ...`
- `failureSummary(List<String> failures)` — visually distinct block with full paths and reasons

---

## Error Handling
- Wrap all Bio-Formats ops in try/catch
- Batch: catch per-file, log, continue
- Single: IJ.error dialog
