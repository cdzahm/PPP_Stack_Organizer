# PPP_Motility_Analysis — Project Design Document

**Provenzano Lab | University of Minnesota**  
Version 0.1 — June 2026 | *DRAFT — Details subject to change*

---

## 1. Project Overview

PPP_Motility_Analysis is a suite of Fiji/ImageJ plugins providing a standardized, reproducible, and distributable pipeline for analyzing T cell motility from intravital multiphoton microscopy data acquired on the Bruker system. The plugins are modular — each pipeline step is an independent plugin — but are also callable from a master pipeline runner that executes all steps sequentially with a single setup dialog.

The primary scientific use case is quantifying T cell motility coefficients and related parameters within the tumor microenvironment of pancreatic ductal adenocarcinoma (PDAC) models, supporting ongoing CAR T cell therapy development.

---

## 2. Pipeline Status Overview

> ⚠ This document is a living design record. Steps 2–5 are planned but details are still being worked out. Expect this document to be updated as each step is developed.

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
- **Import settings:** Hyperstack view, XYCZT stack order, *Concatenate series when compatible* enabled
- Spatial calibration (pixel width, height, voxel depth in microns) is correctly encoded in the `.companion.ome` metadata
- Time interval is **not** correctly encoded — defaults to 0 sec; must be set manually (default: 60 sec / 1 min)

### 3.2 Typical Acquisition Parameters

Default values used throughout the pipeline (all adjustable at runtime):

| Parameter | Default | Notes |
|---|---|---|
| XY positions (nXY) | 2 | User-entered; plugin calculates nT per position |
| Z planes (nZ) | 20 | Pre-filled from metadata |
| Timepoints per position (nT) | 60 | Total T / nXY; calculated automatically |
| Channels (nC) | 3 | Pre-filled from metadata; supports 2–4 |
| Time interval | 60 sec | Not in metadata; user-adjustable |
| Pixel size (XY) | From metadata | Microns; read from `.companion.ome` |
| Voxel depth (Z) | From metadata | Forced to microns on output |

### 3.3 XY Interleaving Pattern

When Bio-Formats concatenates multiple XY position series, timepoints are interleaved as:

```
Frame 1  → XY position 1, T=1
Frame 2  → XY position 2, T=1
Frame 3  → XY position 1, T=2
Frame 4  → XY position 2, T=2
... (XY1-T1, XY2-T1, XY1-T2, XY2-T2, ...)
```

This pattern holds for any number of XY positions. Step 1 uses this pattern to de-interleave the concatenated stack.

---

## 4. Package Architecture

### 4.1 Package Name

All plugins reside in the Java package: `provenzano_lab`

### 4.2 Repository Structure

```
Fiji-ImageJ_Plugins/
├── PPP_Motility_Analysis/
│   ├── README.md
│   ├── DESIGN.md
│   ├── src/
│   │   └── provenzano_lab/
│   │       ├── utils/
│   │       │   ├── BioFormatsUtils.java    ← shared Bio-Formats I/O helpers
│   │       │   ├── CalibrationUtils.java   ← spatial/temporal calibration helpers
│   │       │   └── LogUtils.java           ← shared logging utilities
│   │       ├── Step1_SeparateXYSeries.java
│   │       ├── Step2_SplitChannels.java    (planned)
│   │       ├── Step3_ProcessChannels.java  (planned)
│   │       ├── Step4_MotilityAnalysis.java (planned)
│   │       ├── Step5_ProjectAndExport.java (planned)
│   │       └── PPP_Pipeline_Runner.java    (planned)
│   └── plugins.config                      ← Fiji menu registration
```

### 4.3 Dependencies

- Fiji (required — not vanilla ImageJ)
- Bio-Formats plugin (bundled with Fiji)
- OME-TIFF writer via Bio-Formats (bundled with Fiji)
- No external Maven/Gradle dependencies for distribution simplicity

### 4.4 Distribution

Each compiled `.class` file (or `.jar`) is placed in the Fiji `plugins/` folder. Lab members install by dropping into their local Fiji installation. Plugins appear in the Fiji menu under **Plugins > Provenzano Lab**.

---

## 5. Step 1 — XY Series Separation (`Step1_SeparateXYSeries`)

### 5.1 Purpose

Opens a Bruker `.companion.ome` file, separates the interleaved XY position timeseries into individual hyperstacks, corrects temporal calibration, and saves each position as a properly calibrated OME-TIFF.

### 5.2 Modes of Operation

**Single File Mode**
- User selects a single `.companion.ome` file via file browser
- Parameter dialog pre-populated from metadata
- Output OME-TIFFs saved to `processed/` subfolder adjacent to source
- Separated hyperstacks left open in Fiji for inspection

**Batch Mode**
- User selects a root folder
- Plugin recursively finds all `.companion.ome` files in all subfolders
- Same parameters applied to all files in the batch
- Processed silently (no image windows opened) for performance
- Progress log updated after each file completes
- Files that fail are skipped; prominent summary of failures shown at end

### 5.3 User Dialog Flow

**Step 1: Mode Selection Dialog**
- Radio button: Single file / Batch folder
- File/folder browser button

**Step 2: Parameter Dialog**
- Number of XY positions (nXY) — user enters; default 2
- Channels (nC) — pre-filled from metadata
- Z planes (nZ) — pre-filled from metadata
- Total timepoints (nT total) — pre-filled from metadata; shown read-only for reference
- Timepoints per position — calculated automatically as `nT_total / nXY`; shown read-only
- Time interval (seconds) — user-adjustable; default 60

> ⚠ If `nT_total` is not evenly divisible by `nXY`, the plugin will warn the user before proceeding.

### 5.4 Output

- **Format:** OME-TIFF (`.ome.tif`)
- **Naming:** `{original_filename}_XY01.ome.tif`, `{original_filename}_XY02.ome.tif`, etc.
- **Location:** `{source_folder}/processed/{original_filename}_XY01.ome.tif`
- **Calibration:** XY pixel size and Z voxel depth preserved from metadata, both forced to microns
- **Time interval:** set to user-specified value (default 60 sec)

### 5.5 Logging

- Single file mode: log panel shows file opened, parameters detected, each XY saved with path
- Batch mode: log shows progress per file (X of N complete), and a final summary
- Failure summary in batch mode is visually distinct (clearly labeled, lists each failed file path and reason)

---

## 6. Planned Pipeline Steps (Details TBD)

> ⚠ The following steps are described at a high level only. Designs will be refined during development.

### 6.1 Step 2 — Channel Splitting

Splits a separated XY hyperstack into individual single-channel image stacks. A channel assignment dialog lets the user designate the role of each channel (e.g., T cells, collagen, vasculature). Role assignments are used by downstream steps to route the correct channel to the correct analysis.

### 6.2 Step 3 — Channel Processing

Applies per-channel preprocessing: Gaussian blur, background subtraction, and thresholding. Parameters are set via dialog and applied uniformly across a batch by default, with an option to manually adjust settings for individual images when needed. **This step requires human review — it is not fully automated.**

- Batch default: same filter/threshold parameters for all files
- Per-image override: user can flag individual images for manual parameter adjustment
- Exact operations and parameters TBD during development

### 6.3 Step 4 — Motility Analysis

Operates on the processed T cell channel. Calculates motility coefficient and other quantitative parameters from cell tracking data. Specific metrics, tracking algorithm, and output format are TBD. This is the **core scientific output** of the pipeline.

### 6.4 Step 5 — Z Projection and AVI Export

Generates a composite Z-projection across all channels with user-defined LUTs and channel colors. Converts the time series into an `.avi` video file for presentation and publication. Compression settings and frame rate TBD.

### 6.5 Master Pipeline Runner

A single plugin that calls Steps 1–5 in sequence with a unified setup dialog. Each step remains independently callable for re-running individual steps on already-processed data. Step 3 manual intervention handling TBD.

---

## 7. Coding Conventions

### 7.1 General

- Java, targeting Fiji's bundled JRE
- All plugins implement `ij.plugin.PlugIn` (`run(String arg)` entry point)
- Shared utilities in `provenzano_lab.utils` — never duplicate I/O or calibration code across steps
- `GenericDialog` used for all user dialogs for macro-recordability

### 7.2 Error Handling

- All Bio-Formats operations wrapped in try/catch
- Batch mode: catch per-file exceptions, log failure, continue to next file
- Single file mode: show `IJ.error` dialog with clear message on failure

### 7.3 Macro Recordability

All dialogs use `GenericDialog`, ensuring all steps are macro-recordable. This enables batch scripting outside the built-in batch mode for advanced users.

---

## 8. Open Questions / Items TBD

- **Step 3:** Exact preprocessing operations per channel (Gaussian sigma, threshold method, background subtraction approach)
- **Step 3:** UI design for per-image manual override of parameters
- **Step 4:** Tracking algorithm selection (Manual ROI-based? TrackMate integration? Custom?)
- **Step 4:** Full list of motility parameters to calculate beyond motility coefficient
- **Step 5:** AVI codec and compression settings
- **Step 5:** Channel color/LUT assignment UI
- **Master runner:** Whether Step 3 manual intervention pauses the runner or is handled as a separate pre-run pass
- **Long-term:** Whether to package as a proper `.jar` with manifest for cleaner distribution

---

*PPP_Motility_Analysis — Design Document v0.1 — Provenzano Lab, University of Minnesota*
