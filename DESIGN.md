# PPP_Stack_Organizer — Project Design Document

**Provenzano Lab | University of Minnesota**
Version 0.4.1 — June 2026 | *DRAFT — Details subject to change*

**Local project path:** `/Users/cdz/PPP Lab Files/Coding Projects/ImageJ_Plugins/PPP_Stack_Organizer`

---

## 1. Project Overview

PPP_Stack_Organizer is a Fiji/ImageJ plugin for importing and organizing Bruker multiphoton microscopy acquisition data. It opens `.companion.ome` files, de-interleaves multi-position acquisitions, splits channels by biological role, and saves each position × role combination as a calibrated single-channel OME-TIFF. It is a standalone tool usable independently of any downstream analysis pipeline.

Developed in the Provenzano Lab at the University of Minnesota to support intravital multiphoton imaging of the tumor microenvironment in pancreatic ductal adenocarcinoma (PDAC) models.

---

## 2. Plugin Status Overview

| Plugin | Class | Menu Path | Status |
|---|---|---|---|
| PPP Stack Organizer | `StackOrganizer` | `Plugins > PPP Lab > Stack Organizer` | **Complete ✅** |

---

## 3. Input Data Format

### 3.1 Microscope and Acquisition

- **Source:** Bruker multiphoton microscope
- **File format:** `.companion.ome` opened via Bio-Formats importer in Fiji
- **Import settings:** Hyperstack view, XYCZT stack order, *Concatenate series when compatible* enabled, *all series* selected
- Spatial calibration (pixel width, height, voxel depth in microns) is correctly encoded in the `.companion.ome` metadata
- Time interval is **not** correctly encoded — defaults to 0 sec; must be set manually (default: 60 sec / 1 min)

### 3.2 Channel Hardware Mapping

The Bruker microscope has fixed detector assignments. Bio-Formats reads the hardware channel names from the `.companion.ome` and stores them in the OME `Channel:Name` field. These names survive into the plugin's output files and drive channel routing.

| Hardware Channel | OME Channel Name | Default Role | Output Suffix |
|---|---|---|---|
| Ch1 | `Ch1` | Ignore (drop) | — |
| Ch2 | `Ch2` | Tumor | `_tumor` |
| Ch3 | `Ch3` | T cells | `_tcells` |
| Ch4 | `Ch4` | Collagen | `_collagen` |
| Any other name | — | Ignore (drop) | — |

Bio-Formats re-indexes channels contiguously starting at C=0, so a 2-channel run (Ch3/Ch4 only) produces C0=`Ch3` and C1=`Ch4` — but the `Ch3`/`Ch4` names are preserved in the metadata and the role lookup remains position-independent.

**The role-to-name mapping is a controlled vocabulary** — only `Tumor`, `T cells`, and `Collagen` are recognized downstream roles. `Ignore` means no output file is written for that channel. Role labels are user-editable in the parameter dialog as a safety valve.

### 3.3 Typical Acquisition Parameters

All fields are user-editable at runtime, including those pre-filled from metadata:

| Parameter | Default | Source | Notes |
|---|---|---|---|
| XY positions (nXY) | 2 | User entered | Supports nXY=1 (passthrough/calibration-only mode) |
| Z planes (nZ) | 20 | Metadata (editable) | Pre-filled; user can override |
| Total timepoints (nT) | 120 | Metadata (read-only) | Shown for reference only |
| Timepoints per position | 60 | Calculated: nT / nXY | Read-only; warns if not integer |
| Channels (nC) | 3 | Metadata (editable) | Supports 2–4 channels |
| Time interval | 60 sec | User entered | Not in metadata; always manual |
| Pixel size (XY) | From metadata | Metadata (editable) | Microns |
| Voxel depth (Z) | From metadata | Metadata (editable) | Forced to microns on output |
| Ch2 role label | `tumor` | Pre-filled; **user-editable** | Drives filename suffix and OME Channel:Name |
| Ch3 role label | `tcells` | Pre-filled; **user-editable** | Drives filename suffix and OME Channel:Name |
| Ch4 role label | `collagen` | Pre-filled; **user-editable** | Drives filename suffix and OME Channel:Name |

### 3.4 XY Interleaving Pattern

When Bio-Formats concatenates multiple XY position series with *Concatenate series when compatible* enabled, timepoints are interleaved as:

```
Frame 1 → XY1, T=1  |  Frame 2 → XY2, T=1  |  Frame 3 → XY1, T=2  |  Frame 4 → XY2, T=2  |  ...
```

This pattern extends to any nXY. **When nXY=1, no de-interleaving is performed** — the plugin acts as a standardized importer, calibration corrector, and channel splitter.

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
        │       │   ├── BioFormatsUtils.java      ← shared Bio-Formats I/O helpers
        │       │   ├── CalibrationUtils.java     ← spatial/temporal calibration helpers
        │       │   └── LogUtils.java             ← shared logging utilities
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

Opens a Bruker `.companion.ome` file, de-interleaves XY positions, splits channels by biological role, and saves each position × role combination as a calibrated single-channel OME-TIFF.

**When nXY=1**, no de-interleaving is performed — the plugin acts as a standardized importer, calibration corrector, and channel splitter.

### 5.2 Metadata Handling

Rather than rebuilding OME metadata from scratch, Stack Organizer copies the full source metadata from the `.companion.ome` and makes only the necessary modifications:

- **Copied wholesale:** channel names, emission/excitation wavelengths, detector settings, acquisition date, instrument block — anything Bruker wrote
- **Modified for the output file:** pixel dimensions (SizeX, SizeY, SizeZ, SizeT, SizeC=1), DimensionOrder (forced XYCZT), PhysicalSizeZ unit (forced to microns), TimeIncrement (set from user input), ImageID/PixelsID (unique per output), TiffData block (regenerated for single-channel layout), Channel block (only the one channel relevant to this output file, with Name set to the role label)

This approach ensures hardware identity (`Ch2`, `Ch3`, `Ch4`) and any other acquisition metadata survive into all downstream files without selective preservation.

### 5.3 Channel Role Assignment

Channel names from the source metadata are looked up in a fixed role table:

| OME Channel Name | Default Role | Output filename suffix |
|---|---|---|
| `Ch2` | `tumor` | `_tumor` |
| `Ch3` | `tcells` | `_tcells` |
| `Ch4` | `collagen` | `_collagen` |
| `Ch1` or anything else | Ignore | no file written |

The role labels (`tumor`, `tcells`, `collagen`) are pre-filled in the parameter dialog and **user-editable** — if the mapping changes for a particular experiment, the user can correct it before processing. No two routed channels may share the same role label (dialog re-shows with an error if this occurs). Ignored channels produce no output file.

Role labels are written to both the output filename and the OME `Channel:Name` field, so the identity of each file is self-describing at both the filesystem and metadata level.

### 5.4 Modes of Operation

**Single File Mode**
- User selects a single `.companion.ome` file via file browser
- Parameter dialog pre-populated from metadata (all fields remain user-editable)
- Output files saved to `processed/` folder adjacent to the source file
- Output files left open in Fiji for inspection

**Batch Mode**
- User selects a root folder
- Plugin recursively finds all `.companion.ome` files in all subfolders
- Parameters and channel role assignments confirmed once from the first file; applied to all
- Files whose channel count or names don't match the confirmed mapping are skipped and flagged
- Processed silently — no image windows opened
- Progress log updated after each file; prominent failure summary at end

### 5.5 User Dialog Flow

**Dialog 1: Mode and File Selection**
- Radio button: Single file / Batch folder
- File/folder browser button

**Dialog 2: Parameters**
- Number of XY positions (nXY) — user enters; default 2
- Z planes (nZ) — pre-filled from metadata; user-editable
- Total timepoints (nT total) — pre-filled from metadata; read-only for reference
- Timepoints per position — calculated `nT_total / nXY`; read-only
- Channels (nC) — pre-filled from metadata; user-editable
- Time interval (seconds) — user enters; default 60
- Pixel size XY — pre-filled from metadata; user-editable
- Voxel depth Z — pre-filled from metadata; user-editable
- **Channel role assignments** — one row per detected channel, showing the hardware name (`Ch2`, `Ch3`, `Ch4`) and a pre-filled editable role label (`tumor`, `tcells`, `collagen`); channels that map to Ignore are shown but labeled `ignore`

> ⚠ If `nT_total` is not evenly divisible by `nXY`, the plugin warns the user before proceeding.
> ⚠ If two channels are assigned the same non-ignore role, the dialog re-shows with an error.

### 5.6 Output

- **Format:** OME-TIFF (`.ome.tif`), uncompressed, sequential write
- **Naming:** `{original_filename}_XY01_tcells.ome.tif`, `_XY01_tumor.ome.tif`, `_XY01_collagen.ome.tif`, etc.
- **Location:** `processed/` folder created adjacent to the source `.companion.ome`
  - Example: source at `/data/Exp01/Mouse1/AB_D28.companion.ome`
  - Outputs: `/data/Exp01/Mouse1/processed/AB_D28_XY01_tumor.ome.tif`, `AB_D28_XY01_tcells.ome.tif`, etc.
- **Dimensions:** Single channel (SizeC=1), full Z and T extent for that position
- **Calibration:** XY and Z from metadata (Z unit forced to microns), time interval from user input
- **Metadata:** Full source OME metadata copied and trimmed to the single relevant channel (see Section 5.2)
- **Ignored channels:** No file written — dropped silently

### 5.7 Logging

- Single file mode: file opened → parameters detected → each output file saved with filename
- Batch mode: `[ 3/12 ] Saved: /path/.../AB_D28_XY01_tcells.ome.tif` → final summary
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
options.setId(path);
options.setOpenAllSeries(true);
options.setConcatenate(true);
options.setStackFormat(ImporterOptions.VIEW_HYPERSTACK);
options.setStackOrder(ImporterOptions.ORDER_XYCZT);
ImagePlus[] imps = BF.openImagePlus(options);
```

**Metadata copy approach:** Source OME metadata is read directly from the companion file via `ImageReader` / `OMEXMLService`, copied into an `IMetadata` object, and then modified only for the fields that must change per output file (dimensions, IDs, TiffData, TimeIncrement, Z unit). This preserves all Bruker-written hardware metadata in every output file.

**Export:** `OMETiffWriter` with `COMPRESSION_UNCOMPRESSED` and `setWriteSequentially(true)` called before `setId()`. The default `ImageWriter` (LZW) is not used — it is CPU-limited and extremely slow on large hyperstacks.

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

*PPP_Stack_Organizer — Design Document v0.4.1 — Provenzano Lab, University of Minnesota*
