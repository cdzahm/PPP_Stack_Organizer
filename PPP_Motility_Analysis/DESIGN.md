# PPP_Motility_Analysis — Project Design Document

**Provenzano Lab | University of Minnesota**
Version 0.2 — June 2026 | *DRAFT — Details subject to change*

**Local project path:** `/Users/cdz/PPP Lab Files/Coding Projects/Cell Tracking/PPP_Motility_Analysis`

---

## 1. Project Overview

PPP_Motility_Analysis is a suite of Fiji/ImageJ plugins providing a standardized, reproducible, and distributable pipeline for analyzing T cell motility from intravital multiphoton microscopy data acquired on the Bruker system. The plugins are modular — each pipeline step is an independent plugin — but are also callable from a master pipeline runner that executes all steps sequentially with a single setup dialog.

The primary scientific use case is quantifying T cell motility coefficients and related parameters within the tumor microenvironment of pancreatic ductal adenocarcinoma (PDAC) models, supporting ongoing CAR T cell therapy development.

---

## 2. Pipeline Status Overview

> ⚠ This document is a living design record. Steps 2–5 are planned but details are still being worked out. This document will be updated as each step is developed.

| Pipeline Step | Status | Notes |
|---|---|---|
| Step 1: XY Series Separation | **In Development** | First plugin being built |
| Step 2: Channel Splitting | Planned | Details TBD |
| Step 3: Channel Processing | Planned | Manual intervention required |
| Step 4: Motility Analysis | Planned | Core scientific output |
| Step 5: Z Projection + AVI Export | Planned | Presentation output |
| Master Pipeline Runner | Planned | Calls all steps sequentially |

---

## 3. Input Data Format

### 3.1 Microscope and Acquisition

- **Source:** Bruker multiphoton microscope
- **File format:** `.companion.ome` opened via Bio-Formats importer in Fiji
- **Import settings:** Hyperstack view, XYCZT stack order, *Concatenate series when compatible* enabled, *all series* selected
- Spatial calibration (pixel width, height, voxel depth in microns) is correctly encoded in the `.companion.ome` metadata
- Time interval is **not** correctly encoded — defaults to 0 sec; must be set manually (default: 60 sec / 1 min)

### 3.2 Typical Acquisition Parameters

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

### 3.3 XY Interleaving Pattern

When Bio-Formats concatenates multiple XY position series with *Concatenate series when compatible* enabled, timepoints are interleaved as:

```
Frame 1 → XY1, T=1  |  Frame 2 → XY2, T=1  |  Frame 3 → XY1, T=2  |  Frame 4 → XY2, T=2  |  ...
```

This pattern extends to any nXY. **When nXY=1, no de-interleaving is performed** — the plugin acts as a standardized importer and calibration corrector, keeping the rest of the pipeline consistent regardless of acquisition setup.

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
mvn -Dscijava.app.directory=/path/to/Fiji.app/
```

This copies the compiled `.jar` and all dependencies to the correct `plugins/` or `jars/` subdirectory automatically.

### 4.3 Package Name

All plugins reside in the Java package: `provenzano_lab`

### 4.4 Repository and Local Structure

- **GitHub:** `github.com/cdzahm/Fiji-ImageJ_Plugins/PPP_Motility_Analysis/`
- **Local:** `/Users/cdz/PPP Lab Files/Coding Projects/Cell Tracking/PPP_Motility_Analysis/`

```
PPP_Motility_Analysis/
├── pom.xml                                    ← Maven build file
├── README.md
├── DESIGN.md
└── src/
    └── main/
        ├── java/
        │   └── provenzano_lab/
        │       ├── utils/
        │       │   ├── BioFormatsUtils.java   ← shared Bio-Formats I/O helpers
        │       │   ├── CalibrationUtils.java  ← spatial/temporal calibration helpers
        │       │   └── LogUtils.java          ← shared logging utilities
        │       ├── Step1_SeparateXYSeries.java
        │       ├── Step2_SplitChannels.java    (planned)
        │       ├── Step3_ProcessChannels.java  (planned)
        │       ├── Step4_MotilityAnalysis.java (planned)
        │       ├── Step5_ProjectAndExport.java (planned)
        │       └── PPP_Pipeline_Runner.java    (planned)
        └── resources/
            └── plugins.config                 ← Fiji menu registration
```

### 4.5 Distribution

Lab members install by dropping the compiled `.jar` into their Fiji `plugins/` folder and restarting Fiji (or Help > Refresh Menus). Plugins appear under **Plugins > Provenzano Lab**. The `.jar` filename must contain an underscore — handled automatically by Maven with the artifact ID.

---

## 5. Step 1 — XY Series Separation (`Step1_SeparateXYSeries`)

### 5.1 Purpose

Opens a Bruker `.companion.ome` file, separates the interleaved XY position timeseries into individual hyperstacks, corrects temporal calibration, and saves each position as a properly calibrated OME-TIFF.

**When nXY=1**, the plugin skips de-interleaving and functions as a standardized importer and time calibration corrector — keeping the rest of the pipeline consistent regardless of acquisition setup.

### 5.2 Modes of Operation

**Single File Mode**
- User selects a single `.companion.ome` file via file browser
- Parameter dialog pre-populated from metadata (all fields remain user-editable)
- Output OME-TIFFs saved to a `processed/` folder created adjacent to the source file
- Separated hyperstacks left open in Fiji for inspection

**Batch Mode**
- User selects a root folder
- Plugin recursively finds all `.companion.ome` files in all subfolders
- Same parameters applied to all files in the batch
- Processed silently — no image windows opened
- Progress log updated after each file completes (X of N done, file path, save location)
- Files that fail are skipped; prominent failure summary shown at end listing each failed path and reason
- Images are **not** left open in batch mode

### 5.3 User Dialog Flow

**Dialog 1: Mode and File Selection**
- Radio button: Single file / Batch folder
- File/folder browser button

**Dialog 2: Parameters**
- Number of XY positions (nXY) — user enters; default 2
- Channels (nC) — pre-filled from metadata; **user-editable**
- Z planes (nZ) — pre-filled from metadata; **user-editable**
- Total timepoints (nT total) — pre-filled from metadata; shown read-only for reference
- Timepoints per position — calculated as `nT_total / nXY`; shown read-only
- Time interval (seconds) — user enters; default 60; not available from metadata

> ⚠ If `nT_total` is not evenly divisible by `nXY`, the plugin warns the user before proceeding.

### 5.4 Output

- **Format:** OME-TIFF (`.ome.tif`) written via Bio-Formats exporter
- **Naming:** `{original_filename}_XY01.ome.tif`, `{original_filename}_XY02.ome.tif`, etc.
- **Location:** A `processed/` folder created in the **same directory as the source `.companion.ome` file**
  - Example: source at `/data/Exp01/Mouse1/AB_D28.companion.ome`
  - Output: `/data/Exp01/Mouse1/processed/AB_D28_XY01.ome.tif`
  - This keeps each experiment's processed outputs next to its raw data without mixing files directly in the acquisition folder
- **Calibration:** XY pixel size and Z voxel depth preserved from metadata, both forced to microns
- **Time interval:** set to user-specified value (default 60 sec)

### 5.5 Logging

- Single file mode: IJ.log panel shows file opened, parameters detected, each XY file saved with full path
- Batch mode: running count (e.g. `[ 3/12 ] Saved: /path/to/file_XY01.ome.tif`), then final summary
- Batch failure summary: visually separated block listing each failed file and the error reason

---

## 6. Planned Pipeline Steps (Details TBD)

> ⚠ The following steps are described at a high level only. All details are subject to change during development.

### 6.1 Step 2 — Channel Splitting

Splits a separated XY hyperstack (output of Step 1) into individual single-channel image stacks. A channel assignment dialog lets the user designate the role of each channel (e.g., T cells, collagen, vasculature). Role assignments drive downstream routing — Step 4 operates on the designated T cell channel, Step 5 uses all channels with user-defined colors. Supports 2–4 channels.

### 6.2 Step 3 — Channel Processing

Applies per-channel preprocessing: Gaussian blur, background subtraction, and thresholding. **This step requires human review and is not fully automated.**

- Batch default: same filter/threshold parameters applied to all files in a dataset
- Per-image override: user can flag individual images for manual adjustment when defaults don't work
- A single threshold and filter setting generally works across all images within one dataset, but may need adjustment between datasets or for outlier images
- Exact operations, filter types, and threshold methods TBD during development

### 6.3 Step 4 — Motility Analysis

Operates on the processed T cell channel. Calculates motility coefficient and other quantitative parameters from cell tracking data. This is the **core scientific output** of the pipeline. Tracking algorithm (manual ROI-based, TrackMate integration, or custom), specific metrics, and output format are all TBD.

### 6.4 Step 5 — Z Projection and AVI Export

Generates a maximum intensity Z-projection across all channels with user-defined LUTs and channel colors. Converts the projected time series into an `.avi` video for presentation and publication. Bio-Formats Exporter supports AVI and QuickTime natively. Codec, compression, and frame rate TBD.

### 6.5 Master Pipeline Runner

A single plugin that calls Steps 1–5 in sequence with a unified setup dialog. Each step also remains independently callable for re-running individual steps on already-processed data. Handling of Step 3 manual intervention within the runner is TBD.

---

## 7. Coding Conventions

### 7.1 General

- Java, targeting **Java 8** (Azul Zulu JDK 8 recommended), consistent with Fiji's bundled JRE
- IJ1-style plugins implementing `ij.plugin.PlugIn` (`run(String arg)` entry point)
- Built with Maven using `pom-scijava` as parent POM (verify current version at time of setup)
- Shared utilities in `provenzano_lab.utils` — Bio-Formats I/O, calibration, and logging never duplicated across steps
- `GenericDialog` used for all user-facing dialogs — ensures macro-recordability

### 7.2 Bio-Formats API Usage

Bio-Formats provides a high-level Java API for programmatic import:

```java
ImporterOptions options = new ImporterOptions();
options.setId(path);
options.setOpenAllSeries(true);
options.setConcatenate(true);
ImagePlus[] imps = BF.openImagePlus(options);
```

OME-TIFF output is written via the Bio-Formats Exporter. Both are bundled with Fiji — no additional dependencies required.

### 7.3 Error Handling

- All Bio-Formats operations wrapped in try/catch
- Batch mode: per-file exceptions caught, logged, skipped — processing continues
- Single file mode: `IJ.error` dialog with clear message on failure

### 7.4 Macro Recordability

`GenericDialog` ensures all steps are macro-recordable, enabling scripting and batch automation beyond the built-in batch mode for advanced users.

---

## 8. Open Questions / Items TBD

- **Step 3:** Exact preprocessing operations per channel (Gaussian sigma, threshold method, background subtraction approach)
- **Step 3:** UI design for per-image manual override — flag before batch run, or interrupt during?
- **Step 4:** Tracking algorithm — manual ROI-based, TrackMate integration, or custom implementation?
- **Step 4:** Full list of motility parameters beyond motility coefficient
- **Step 5:** AVI codec and compression settings (Bio-Formats supports AVI and QuickTime natively)
- **Step 5:** Channel color/LUT assignment UI
- **Master runner:** Step 3 manual intervention handling
- **Build environment:** Confirm Azul Zulu JDK 8 setup on development machine before coding begins

---

*PPP_Motility_Analysis — Design Document v0.2 — Provenzano Lab, University of Minnesota*
