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
- Requires: Fiji (not vanilla ImageJ); Bio-Formats (bundled with Fiji)
- All dialogs use GenericDialog (macro-recordable)
- All plugins implement `ij.plugin.PlugIn` (`run(String arg)`)

---

## Plugin
- **Name:** PPP Stack Organizer
- **Class:** `provenzano_lab.StackOrganizer`
- **Menu path:** `Plugins > PPP Lab > Stack Organizer` (registered via `plugins.config` as `Plugins>PPP Lab, "Stack Organizer", provenzano_lab.StackOrganizer`)
- **Status:** Complete ✅

---

## Bio-Formats API Notes (verified during compile and runtime)
- Import: `options.setStackFormat(ImporterOptions.VIEW_HYPERSTACK)` and `options.setStackOrder(ImporterOptions.ORDER_XYCZT)` — NOT `setViewHyperstack()`
- Export: use `OMETiffWriter` directly — NOT `LociExporter` (triggers interactive dialog) and NOT default `ImageWriter` (defaults to LZW which is extremely slow on large hyperstacks)
- Export compression: `setCompression(TiffWriter.COMPRESSION_UNCOMPRESSED)` and `setWriteSequentially(true)` must both be called BEFORE `setId()` — this is when the writer allocates its internal state
- Export speed: uncompressed + sequential = disk-speed limited (~30-60 sec on SSD for ~2GB file); LZW default = CPU-speed limited (20+ min for same file)
- Dependencies in pom.xml: `ome:bio-formats_plugins` (for loci.plugins.*), `ome:formats-api`, `ome:formats-gpl`
- scijava-maven-plugin uses `populate-app` goal (v3.x rename of `copy-jars`)
- Enforcer skipped via `<enforcer.skip>true</enforcer.skip>` (appropriate for internal lab plugin)

---

## Fiji Installation Notes
- Fiji lives at `/Applications/Fiji/Fiji.app` — the deploy target is `/Applications/Fiji` (the parent)
- Maven deploys to `/Applications/Fiji/plugins/` — this is what Fiji actually reads
- `/Applications/Fiji/Fiji.app/plugins/` also exists but is NOT the correct deploy target
- `plugins.config` label must differ from the last menu folder name to avoid Fiji creating a submenu; use `Plugins>PPP Lab, "Stack Organizer", ...` not `Plugins>PPP Lab>Stack Organizer, "Stack Organizer", ...`

---

## Input Data
- Source: Bruker multiphoton microscope
- Format: .companion.ome via Bio-Formats, Hyperstack, XYCZT, Concatenate series enabled, all series selected
- Spatial calibration (XY pixels, Z voxel, microns): correct in metadata
- Time interval: NOT in metadata, always 0 sec — must be set manually (default 60 sec)
- Z unit bug: voxel depth unit missing — force microns on output

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
    │   │   └── LogUtils.java
    │   └── StackOrganizer.java
    └── resources/plugins.config
```

---

## StackOrganizer — What It Does

### The Problem
Bio-Formats concatenates multi-position acquisitions with interleaved timepoints:
`Frame 1→XY1/T1, Frame 2→XY2/T1, Frame 3→XY1/T2, Frame 4→XY2/T2, ...`
Plugin de-interleaves into separate per-position, per-role single-channel hyperstacks.
When nXY=1: skip de-interleaving, act as calibration corrector and channel splitter only.

### Channel Hardware Mapping
Fixed Bruker detector assignments; names preserved through Bio-Formats into OME metadata:
| OME Channel Name | Default Role | Output Suffix |
|---|---|---|
| `Ch1` | Ignore | — |
| `Ch2` | tumor | `_tumor` |
| `Ch3` | tcells | `_tcells` |
| `Ch4` | collagen | `_collagen` |

Role labels are user-editable in the parameter dialog. 2-channel acquisitions (Ch3/Ch4 only) handled correctly via name-based routing.

### Acquisition Defaults (all user-editable)
| Param | Default | Source |
|-------|---------|--------|
| nXY | 2 | User entered |
| nZ | 20 | Metadata (editable) |
| nT total | 120 | Metadata (read-only) |
| nT per position | nT/nXY | Calculated (read-only) |
| nC | 3 | Metadata (editable); supports 2–4 |
| Time interval | 60 sec | User entered (always) |
| Pixel size XY | metadata | Metadata (editable) |
| Voxel depth Z | metadata | Metadata (editable); force microns |

Warn user if nT_total % nXY != 0.

### Dialog Flow
1. Mode dialog: Single file / Batch folder + file/folder browser
2. Parameter dialog: pre-filled from metadata where possible, all editable

### Bio-Formats Import
```java
ImporterOptions options = new ImporterOptions();
options.setId(path);
options.setOpenAllSeries(true);
options.setConcatenate(true);
options.setStackFormat(ImporterOptions.VIEW_HYPERSTACK);
options.setStackOrder(ImporterOptions.ORDER_XYCZT);
return BF.openImagePlus(options);
```

### Bio-Formats Export (BioFormatsUtils.saveAsOMETIFF)
```java
OMETiffWriter writer = new OMETiffWriter();
writer.setMetadataRetrieve(meta);
writer.setCompression(TiffWriter.COMPRESSION_UNCOMPRESSED); // before setId()
writer.setWriteSequentially(true);                          // before setId()
writer.setId(outputPath);
// then write planes in XYCZT order
writer.close();
```

### Output
- Format: OME-TIFF (.ome.tif), uncompressed
- Naming: `{filename}_XY01_tcells.ome.tif`, `{filename}_XY01_tumor.ome.tif`, etc.
- Location: `{same dir as .companion.ome}/processed/`
- Calibration: XY+Z from metadata, Z unit forced to microns, time interval from user input
- Hyperstack: XYCZT order, single channel, full Z and T extent per position

### Modes
**Single file:** save outputs + leave hyperstacks open in Fiji
**Batch:** recursive subfolder search for .companion.ome files, same params for all, silent (no windows), progress log, skip+log failures, prominent failure summary at end

### Logging
- Single: file opened → params detected → each output saved with filename only (not full path)
- Batch: `[ 3/12 ] Saved: /path/...` → final summary → failure block if any (failures show full path)

---

## Shared Utilities (provenzano_lab.utils)
Never duplicate these.

### BioFormatsUtils
- `openWithBioFormats(String path)` — opens .companion.ome, returns ImagePlus[]
- `saveAsOMETIFF(ImagePlus imp, String outputPath)` — saves via OMETiffWriter, uncompressed, sequential

### CalibrationUtils
- `applyCalibration(ImagePlus imp, double pixelWidth, double pixelHeight, double voxelDepth, double frameIntervalSec)` — sets calibration, forces microns
- `readCalibration(ImagePlus imp)` — returns double[] {pixelWidth, pixelHeight, voxelDepth}

### LogUtils
- `log(String msg)` — timestamped IJ.log entry
- `batchProgress(int current, int total, String savedPath)` — formats as `[ 3/12 ] Saved: ...`
- `failureSummary(List<String> failures)` — visually distinct block with full paths and reasons

---

## Error Handling
- Wrap all Bio-Formats ops in try/catch
- Batch: catch per-file, log, continue
- Single: IJ.error dialog
