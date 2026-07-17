# PPP_Stack_Organizer — Project Design Document

**Provenzano Lab | University of Minnesota**
Version 0.2.0 — July 2026 | *DRAFT — Details subject to change*

**Local project path:** `/Users/cdz/PPP Lab Files/Coding Projects/ImageJ_Plugins/PPP_Stack_Organizer`

---

## 1. Project Overview

PPP_Stack_Organizer is a Fiji/ImageJ plugin for importing and organizing Bruker multiphoton microscopy acquisition data. It opens a Bruker acquisition's PrairieView `.xml` file, splits channels by biological role, and saves each XY position × role combination as a calibrated single-channel TIFF. It is a standalone tool usable independently of any downstream analysis pipeline.

Developed in the Provenzano Lab at the University of Minnesota to support intravital multiphoton imaging of the tumor microenvironment in pancreatic ductal adenocarcinoma (PDAC) models.

> ⚠ **Breaking change from v0.1.0 (this release, v0.2.0):** `.companion.ome` import support has been removed entirely — the plugin now always imports via the acquisition's PrairieView `.xml` (see §3). Output format also changed from OME-TIFF (`.ome.tif`) to standard ImageJ TIFF (`.tif`) — see §5.6. Existing v0.1.0 output files already on disk are untouched and require no migration; only new runs of the plugin produce the new format.

---

## 2. Plugin Status Overview

| Plugin | Class | Menu Path | Status |
|---|---|---|---|
| PPP Stack Organizer | `StackOrganizer` | `Plugins > PPP Lab > Stack Organizer` | **Complete ✅** |

---

## 3. Input Data Format

### 3.0 Why XML-Only

Earlier versions supported two parallel import paths (`.companion.ome`, and PrairieView `.xml` for older acquisitions with no `.companion.ome`). A lab-wide drive sample (~5000 files across 10 project folders) found:

- `.companion.ome` folders had a co-located `.xml` **99%+** of the time, with **no observed cases** of the raw per-plane TIFFs being missing/archived while only `.companion.ome` remained.
- `.xml`-only acquisitions (no `.companion.ome`) are common and not confined to one project — found across 5 different lab members' folders, all predating a PrairieView software version transition (5.6.x → 5.8.x) around September/October 2024.
- No breaking PrairieView XML schema differences were found across the versions actually present in lab data (5.3.x–5.8.x) that would complicate a single XML parser.

Given this, `.companion.ome` import support was dropped and the plugin now **always** imports via the acquisition's PrairieView `.xml` — one code path instead of two, covering both older and newer acquisitions identically.

### 3.1 Microscope and Acquisition

- **Source:** Bruker multiphoton microscope
- **File format:** PrairieView `.xml` (one per acquisition folder, e.g. `TSeries-NNN/*.xml`), opened via Bio-Formats' `loci.formats.in.PrairieReader` — already bundled in `formats-gpl`, no separate dependency, auto-detected via `setId()`
- **Import settings:** Hyperstack view, XYCZT stack order, *Concatenate series* **disabled**, *all series* selected. This was the source of a real bug caught during testing: *Concatenate series* was initially left **enabled** (carried over from earlier `.companion.ome`-era code, where it was required to merge fragmented per-cycle series). Direct testing at the lower-level `ImportProcess.getSeriesCount()` API showed 2 separate series as expected — but the full `BF.openImagePlus()` pixel-reading pathway silently re-merges those same 2 series into a single interleaved `SizeT=120` stack when concatenation is enabled, which the lower-level check does not reveal. With `setConcatenate(false)`, `BF.openImagePlus()` correctly returns one `ImagePlus` per XY-position series (see §3.2). Verified against both real 2-position sample datasets before shipping.
- XY pixel size comes from `IMetadata` normally. Voxel depth (Z) and frame interval come back `null` from `PrairieReader` (confirmed via direct testing) and are instead parsed directly from the XML by `PrairieXmlUtils` (§3.3). Prairie's own XML is unambiguous about units (always microns), so no unit-forcing workaround is needed.

### 3.2 Series-per-Position, No De-interleaving

`BF.openImagePlus()` on the `.xml` returns one `ImagePlus` **per XY position already** (confirmed via direct testing against real multi-position sample data: a 2-position, 120-cycle acquisition opens as `imps.length == 2`, each with `SizeT=60`, not one `SizeT=120` interleaved stack). This means:

- Series/`ImagePlus` index maps directly and ordinally to XY01, XY02, … — no coordinate math needed, since `PrairieReader` has already grouped cycles by position internally.
- There is no de-interleave step anywhere in the pipeline — each position's `ImagePlus` is passed straight through to channel-splitting. The nXY dialog field is prefilled with the real series count and remains user-editable, but it is informational only: the actual position count always comes from Bio-Formats' series count for that specific file, with a log note if the user's entered value differs.
- Channel names (`Ch2`/`Ch3`/`Ch4`) are populated by `PrairieReader` into `IMetadata` — confirmed via direct testing against real 2-channel and 3-channel files — so the role-mapping table (§3.4) works unchanged off `PrairieReader`'s metadata.

### 3.3 Prairie XML Fields Not in `IMetadata` (`PrairieXmlUtils`)

`PrairieReader` leaves `getPixelsPhysicalSizeZ()` and `getPixelsTimeIncrement()` `null` (confirmed via direct testing). `provenzano_lab.utils.PrairieXmlUtils` parses these two fields directly from the PVScan XML instead:

- `readVoxelDepthUm(xmlPath)` — `PVStateValue key="micronsPerPixel"` / `IndexedValue index="ZAxis"`, from the acquisition-wide `PVStateShard` (always microns; no unit ambiguity).
- `readFrameIntervalSec(xmlPath)` — wall-clock delta between the first two `<Sequence>` (cycle) elements' `@time` attributes. Uses the **first** delta rather than an average across all cycles, deliberately: acquisitions can pause between cycles (stage settling, user-triggered resume), which would skew an average; the first interval is the most reliable read of the acquisition's configured cycle period. (Documented as a judgment call in the code — revisit if real data shows the first delta is atypical.)

Both values feed into the same `CalibrationUtils.applyCalibration()` call that populates the `ImagePlus`'s `Calibration` object, which is what the standard-TIFF writer (§5.6) serializes on save.

### 3.4 Channel Hardware Mapping

The Bruker microscope has fixed detector assignments. `PrairieReader` reads the hardware channel names from the `.xml` and stores them in the OME `Channel:Name` field of the `IMetadata` it populates. These names drive channel routing.

| Hardware Channel | Channel Name | Default Role | Output Suffix |
|---|---|---|---|
| Ch1 | `Ch1` | Ignore (drop) | — |
| Ch2 | `Ch2` | Tumor | `_tumor` |
| Ch3 | `Ch3` | T cells | `_tcells` |
| Ch4 | `Ch4` | Collagen | `_collagen` |
| Any other name | — | Ignore (drop) | — |

Bio-Formats re-indexes channels contiguously starting at C=0, so a 2-channel run (Ch3/Ch4 only) produces C0=`Ch3` and C1=`Ch4` — but the `Ch3`/`Ch4` names are preserved in the metadata and the role lookup remains position-independent. Verified against real 2-channel and 3-channel Prairie-XML files.

**The role-to-name mapping is a controlled vocabulary** — only `Tumor`, `T cells`, and `Collagen` are recognized downstream roles. `Ignore` means no output file is written for that channel. Role labels are user-editable in the parameter dialog as a safety valve.

### 3.5 Typical Acquisition Parameters

All fields are user-editable at runtime, including those pre-filled from metadata:

| Parameter | Default | Source | Notes |
|---|---|---|---|
| XY positions (nXY) | Auto-detected | Bio-Formats series count | User-editable, but informational only — actual processing always uses the real per-file series count (§3.2) |
| Z planes (nZ) | From metadata | Metadata (editable) | Pre-filled; user can override |
| Timepoints per position (nT) | From metadata | Metadata (read-only) | Already per-position — one series per XY position, no total-across-positions concept on this path |
| Channels (nC) | From metadata | Metadata (editable) | Supports 2–4 channels |
| Time interval | Real value from XML | `PrairieXmlUtils.readFrameIntervalSec()`, still editable | See §3.3 |
| Pixel size (XY) | From metadata | Metadata (editable) | Microns |
| Voxel depth (Z) | From XML | `PrairieXmlUtils.readVoxelDepthUm()`, already unambiguously microns | Metadata (editable) |
| Ch2 role label | `tumor` | Pre-filled; **user-editable** | Drives filename suffix |
| Ch3 role label | `tcells` | Pre-filled; **user-editable** | Drives filename suffix |
| Ch4 role label | `collagen` | Pre-filled; **user-editable** | Drives filename suffix |

---

## 4. Package Architecture

### 4.1 Language and Framework Decision: Java (IJ1-style) with Maven build

After evaluating available options, **Java with an IJ1-style plugin structure built via Maven** was selected. Key alternatives considered:

| Option | Pros | Cons | Decision |
|---|---|---|---|
| **Java / IJ1 plugin** | Best Bio-Formats integration; compiles to distributable .jar; macro-recordable via GenericDialog; battle-tested | More verbose; requires compilation | **SELECTED** |
| Java / ImageJ2 SciJava command | Modern framework; annotation-driven; cleaner API | Steeper setup; more complex for lab distribution | Future consideration |
| Groovy script | Less verbose; no compilation; JVM-based; good Fiji support | Less clean for .jar distribution; less IDE support | Possible for future scripting steps |
| Jython (Python 2.7) | Familiar Python syntax; no compilation | Python 2.7 only; no numpy/scipy; poor distribution story | Not suitable |
| PyImageJ (Python 3) | Real Python 3; numpy/scipy access | Requires conda env; cannot be distributed as Fiji plugin | Not suitable for lab distribution |

> ℹ **Java version note:** Fiji ships with Java 8 (OpenJDK). The recommended JDK for building IJ1-style plugins is **Azul Zulu JDK 8**. Building with JDK 17 or 21 is not currently recommended for IJ1-style plugins, as ImageJ bytecode targets Java 1.6 minimum. Confirm JDK setup before beginning development.

> ℹ **pom-scijava version:** The current pom-scijava parent POM version was **42.0.0** as of June 2026. Verify the latest version at [https://maven.scijava.org](https://maven.scijava.org) when setting up the project.

### 4.2 Build System

Maven builds the project and packages it as a `.jar`. The `scijava-maven-plugin` deploys directly to a local Fiji installation:

```
mvn -Dscijava.app.directory=/Applications/Fiji
```

This copies the compiled `.jar` and all dependencies to the correct `plugins/` or `jars/` subdirectory automatically.

### 4.3 Package Name

All plugins reside in the Java package: `provenzano_lab`

### 4.4 Repository and Local Structure

- **GitHub:** `github.com/cdzahm/PPP_Stack_Organizer/`
- **Local:** `/Users/cdz/PPP Lab Files/Coding Projects/ImageJ_Plugins/PPP_Stack_Organizer/`

```
PPP_Stack_Organizer/
├── pom.xml                                       ← Maven build file
├── README.md
├── DESIGN.md
└── src/
    └── main/
        ├── java/
        │   └── provenzano_lab/
        │       ├── utils/
        │       │   ├── BioFormatsUtils.java      ← Bio-Formats import + acquisition-file resolution + standard-TIFF save
        │       │   ├── CalibrationUtils.java     ← spatial/temporal calibration helpers
        │       │   ├── LogUtils.java             ← shared logging utilities
        │       │   └── PrairieXmlUtils.java      ← Prairie-XML-only fields IMetadata omits (voxel depth Z, frame interval)
        │       └── StackOrganizer.java           ← PPP Stack Organizer
        └── resources/
            └── plugins.config                    ← Fiji menu registration
```

### 4.5 Distribution

Lab members install by downloading the latest `.jar` from the [GitHub Releases](../../releases) page and dropping it into their Fiji `plugins/` folder, then restarting Fiji (or **Help > Refresh Menus**). The plugin appears under **Plugins > PPP Lab > Stack Organizer**.

---

## 5. PPP Stack Organizer (`StackOrganizer`)

**Menu path:** `Plugins > PPP Lab > Stack Organizer`

### 5.1 Purpose

Opens a Bruker acquisition's PrairieView `.xml` file, splits channels by biological role, and saves each XY position × role combination as a calibrated single-channel TIFF. No de-interleaving step exists in this pipeline — Bio-Formats/`PrairieReader` already returns one series per XY position (§3.2).

### 5.2 Metadata Handling

`IMetadata` (read via `BioFormatsUtils.readSourceMetadata()`) is used only to read channel names for role-mapping (§3.4) and channel counts for validation — the plugin does **not** construct or write OME metadata for outputs (see §5.6: output is standard ImageJ TIFF, not OME-TIFF). Calibration (pixel size, voxel depth, unit, frame interval) is carried entirely through the `ImagePlus`'s `Calibration` object via `CalibrationUtils.applyCalibration()`, independent of any OME metadata.

### 5.3 Channel Role Assignment

Channel names from the source metadata are looked up in a fixed role table:

| Channel Name | Default Role | Output filename suffix |
|---|---|---|
| `Ch2` | `tumor` | `_tumor` |
| `Ch3` | `tcells` | `_tcells` |
| `Ch4` | `collagen` | `_collagen` |
| `Ch1` or anything else | Ignore | no file written |

The role labels (`tumor`, `tcells`, `collagen`) are pre-filled in the parameter dialog and **user-editable** — if the mapping changes for a particular experiment, the user can correct it before processing. No two routed channels may share the same role label (dialog re-shows with an error if this occurs). Ignored channels produce no output file.

Role labels are written to the output filename, so the identity of each file is self-describing at the filesystem level.

### 5.4 Modes of Operation

**Single File Mode**
- User selects the acquisition file or its containing folder (`JFileChooser`, "Select acquisition file or folder"); a folder selection resolves to the sole `.xml` inside it
- Parameter dialog pre-populated from metadata (all fields remain user-editable)
- Output files saved to `processed/` folder adjacent to the source file
- Output files left open in Fiji for inspection

**Batch Mode**
- User selects a root folder
- Plugin recursively finds the sole `.xml` file in each acquisition folder; a folder with multiple `.xml` files is ambiguous and is skipped (logged)
- Parameters and channel role assignments confirmed once from the first file; applied to all. nXY is not applied from this confirmed-once value per file — each file uses its own real Bio-Formats series count, with an advisory log note if it differs from the entered value.
- Files whose channel count or names don't match the confirmed mapping are skipped and flagged
- Processed silently — no image windows opened
- Progress log updated after each file; prominent failure summary at end

### 5.5 User Dialog Flow

**Dialog 1: Mode and File Selection**
- Radio button: Single file / Batch folder
- File/folder browser button (single file mode: `JFileChooser` allowing either a file or a folder; batch mode: `DirectoryChooser` for the recursive search root)

**Dialog 2: Parameters**
- Number of XY positions (nXY) — auto-detected default (Bio-Formats series count); user-editable, informational only (§3.2, §3.5)
- Z planes (nZ) — pre-filled from metadata; user-editable
- Timepoints per position (nT) — pre-filled from metadata; read-only for reference
- Channels (nC) — pre-filled from metadata; user-editable
- Time interval (seconds) — defaults to the real value computed from the XML (§3.3), still editable
- Pixel size XY — pre-filled from metadata; user-editable
- Voxel depth Z — pre-filled from `PrairieXmlUtils`; user-editable
- **Channel role assignments** — one row per detected channel, showing the hardware name (`Ch2`, `Ch3`, `Ch4`) and a pre-filled editable role label (`tumor`, `tcells`, `collagen`); channels that map to Ignore are shown but labeled `ignore`

> ⚠ If two channels are assigned the same non-ignore role, the dialog re-shows with an error.

### 5.6 Output

- **Format:** Standard ImageJ TIFF (`.tif`) — **not** OME-TIFF. Written via `ij.io.FileSaver` (`BioFormatsUtils.saveAsTiff()`), the same writer behind Fiji's native *File > Save As > Tiff*.
  - **Why:** this pipeline's outputs are single-channel or single-composite, single-position files — none of OME-TIFF's cross-vendor/multi-series value applies here. OME-TIFF also forces every downstream read through Bio-Formats/JVM overhead for no benefit. Standard ImageJ TIFF already carries full calibration (pixel size, voxel depth, units, frame interval) via the same `Calibration` object `CalibrationUtils` already populates, and is directly readable by `tifffile` in Python without Bio-Formats.
  - Import still requires Bio-Formats/`PrairieReader` regardless (§3) — this only changes the **write** side; Bio-Formats remains a project dependency.
- **Naming:** `{original_filename}_XY01_tcells.tif`, `_XY01_tumor.tif`, `_XY01_collagen.tif`, etc. (previously `.ome.tif` under v0.1.0 — **breaking change**, see top of document)
- **Location:** `processed/` folder created adjacent to the source `.xml`
  - Example: source at `/data/Exp01/Mouse1/TSeries-001/TSeries-001.xml`
  - Outputs: `/data/Exp01/Mouse1/TSeries-001/processed/TSeries-001_XY01_tumor.tif`, `TSeries-001_XY01_tcells.tif`, etc.
- **Dimensions:** Single channel (SizeC=1) per role file, full Z and T extent for that position; a multi-channel LUT-colored composite is also saved per position (all active/routed channels together, `_XY01.tif`)
- **Calibration:** XY and Z from `PrairieXmlUtils`/metadata (both unambiguously microns), time interval from `PrairieXmlUtils`/user input — carried via the `ImagePlus` `Calibration` object, round-trips through standard TIFF natively (confirmed via direct testing: reopening a saved file in Fiji shows correct pixel size, voxel depth, unit, and frame interval)
- **Channel LUTs:** composite output preserves per-channel LUT coloring (tumor=Red, tcells=Green, collagen=Grays) — an ImageJ-native concept (`CompositeImage`), confirmed to save/reopen correctly via the standard TIFF path
- **Ignored channels:** No file written — dropped silently

### 5.7 Logging

- Single file mode: file opened → parameters detected → each output file saved with filename
- Batch mode: `[ 3/12 ] Saved: /path/.../TSeries-001_XY01_tcells.tif` → final summary
- Batch failure summary: visually separated block, full paths, error reasons

---

## 6. Coding Conventions

### 6.1 General

- Java, targeting **Java 8** (Azul Zulu JDK 8 recommended), consistent with Fiji's bundled JRE
- IJ1-style plugins implementing `ij.plugin.PlugIn` (`run(String arg)` entry point)
- Built with Maven using `pom-scijava` as parent POM (verify current version at time of setup)
- Shared utilities in `provenzano_lab.utils` — Bio-Formats I/O, calibration, and logging never duplicated across steps
- `GenericDialog` used for all user-facing dialogs — ensures macro-recordability

### 6.2 Bio-Formats API Usage

**Import:**

```java
ImporterOptions options = new ImporterOptions();
options.setId(path);              // path to the acquisition's PrairieView .xml
options.setOpenAllSeries(true);
options.setConcatenate(false);    // NOT true — see §3.1, this was a real bug caught in testing
options.setStackFormat(ImporterOptions.VIEW_HYPERSTACK);
options.setStackOrder(ImporterOptions.ORDER_XYCZT);
ImagePlus[] imps = BF.openImagePlus(options);
// imps.length == real nXY; imps[xyIdx] is already that position's own stack — no de-interleave.
```

Confirmed via direct testing that these settings do not cause `PrairieReader` to concatenate separate XY-position series into one.

**Acquisition-file resolution:** `BioFormatsUtils.resolveAcquisitionPath(File selected)` — given a user-selected file or folder, returns the `.xml` path Bio-Formats should open: the file directly if it ends in `.xml`; if a folder, the sole `.xml` inside it (throws on ambiguity — folder has none, or multiple `.xml` files).

**Metadata read:** `BioFormatsUtils.readSourceMetadata()` reads channel names/counts via `ImageReader`/`OMEXMLService` for role-mapping and validation only — no OME metadata is constructed or written for outputs.

**Export:** `ij.io.FileSaver.saveAsTiff()` (`BioFormatsUtils.saveAsTiff()`) — standard ImageJ TIFF, not OME-TIFF. Same writer as Fiji's native *File > Save As > Tiff*; calibration and channel LUTs travel with the `ImagePlus` automatically.

### 6.3 Error Handling

- All Bio-Formats operations wrapped in try/catch
- Batch mode: per-file exceptions caught, logged, skipped — processing continues
- Single file mode: `IJ.error` dialog with clear message on failure

### 6.4 Macro Recordability

`GenericDialog` ensures all plugins are macro-recordable, enabling scripting and batch automation beyond the built-in batch mode for advanced users.

---

## 7. Open Questions / Items TBD

- **Build environment:** Confirm Azul Zulu JDK 8 setup on development machine before coding begins

---

*PPP_Stack_Organizer — Design Document v0.2.0 — Provenzano Lab, University of Minnesota*
