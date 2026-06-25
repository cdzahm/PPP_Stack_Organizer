PPP_Motility_Analysis — Project Knowledge Base
Provenzano Lab, University of Minnesota
GitHub: cdzahm/PPP_Motility_Analysis (repo root = project root)
Local: /Users/cdz/PPP Lab Files/Coding Projects/ImageJ_Plugins/PPP_Motility_Analysis/

Stack

Language: Java (IJ1-style), Java 8 (Azul Zulu JDK 8)
Build: Maven 3.9.16, pom-scijava parent (v42.0.0, verify at setup)
Deploy: mvn -Dscijava.app.directory=/Applications/Fiji.app
Distribution: .jar dropped in Fiji plugins/ folder
Package: provenzano_lab
Requires: Fiji (not vanilla ImageJ); Bio-Formats (bundled with Fiji)
All dialogs use GenericDialog (macro-recordable)
All plugins implement ij.plugin.PlugIn (run(String arg))


Bio-Formats API Notes (verified during compile and runtime)

Import: options.setStackFormat(ImporterOptions.VIEW_HYPERSTACK) and options.setStackOrder(ImporterOptions.ORDER_XYCZT) — NOT setViewHyperstack()
Export: use OMETiffWriter directly — NOT LociExporter (triggers interactive dialog) and NOT default ImageWriter (defaults to LZW which is extremely slow on large hyperstacks)
Export compression: setCompression(TiffWriter.COMPRESSION_UNCOMPRESSED) and setWriteSequentially(true) must both be called BEFORE setId() — this is when the writer allocates its internal state
Export speed: uncompressed + sequential = disk-speed limited (~30-60 sec on SSD for ~2GB file); LZW default = CPU-speed limited (20+ min for same file)
Dependencies in pom.xml: ome:bio-formats_plugins (for loci.plugins.*), ome:formats-api, ome:formats-gpl
scijava-maven-plugin uses populate-app goal (v3.x rename of copy-jars)
Enforcer skipped via <enforcer.skip>true</enforcer.skip> (appropriate for internal lab plugin)


Input Data

Source: Bruker multiphoton microscope
Format: .companion.ome via Bio-Formats, Hyperstack, XYCZT, Concatenate series enabled, all series selected
Spatial calibration (XY pixels, Z voxel, microns): correct in metadata
Time interval: NOT in metadata, always 0 sec — must be set manually (default 60 sec)
Z unit bug: voxel depth unit missing — force microns on output


Pipeline Status
StepClassStatus1: XY Series SeparationStep1_SeparateXYSeriesComplete ✅2: Channel SplittingStep2_SplitChannelsIn Development3: Channel ProcessingStep3_ProcessChannelsPlanned — manual intervention required4: Motility AnalysisStep4_MotilityAnalysisPlanned — details TBD5: Z Projection + AVIStep5_ProjectAndExportPlanned — details TBDMaster RunnerPPP_Pipeline_RunnerPlanned
Ask before assuming details about Steps 3–5.

Repo Structure
PPP_Motility_Analysis/          ← repo root
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
    │   ├── Step1_SeparateXYSeries.java
    │   └── ... (planned steps)
    └── resources/plugins.config

Step 1 — SeparateXYSeries (COMPLETE ✅)
The Problem
Bio-Formats concatenates multi-position acquisitions with interleaved timepoints:
Frame 1→XY1/T1, Frame 2→XY2/T1, Frame 3→XY1/T2, Frame 4→XY2/T2, ...
Plugin de-interleaves into separate per-position hyperstacks.
When nXY=1: skip de-interleaving, act as calibration corrector only.
Acquisition Defaults (all user-editable)
ParamDefaultSourcenXY2User enterednZ20Metadata (editable)nT total120Metadata (read-only)nT per positionnT/nXYCalculated (read-only)nC3Metadata (editable); supports 2–4Time interval60 secUser entered (always)Pixel size XYmetadataMetadata (editable)Voxel depth ZmetadataMetadata (editable); force microns
Warn user if nT_total % nXY != 0.
Dialog Flow

Mode dialog: Single file / Batch folder + file/folder browser
Parameter dialog: pre-filled from metadata where possible, all editable

Bio-Formats Import
javaImporterOptions options = new ImporterOptions();
options.setId(path);
options.setOpenAllSeries(true);
options.setConcatenate(true);
options.setStackFormat(ImporterOptions.VIEW_HYPERSTACK);
options.setStackOrder(ImporterOptions.ORDER_XYCZT);
return BF.openImagePlus(options);
Bio-Formats Export (BioFormatsUtils.saveAsOMETIFF)
javaOMETiffWriter writer = new OMETiffWriter();
writer.setMetadataRetrieve(meta);
writer.setCompression(TiffWriter.COMPRESSION_UNCOMPRESSED); // before setId()
writer.setWriteSequentially(true);                          // before setId()
writer.setId(outputPath);
// then write planes in XYCZT order
writer.close();
Output

Format: OME-TIFF (.ome.tif), uncompressed
Naming: {filename}_XY01.ome.tif, {filename}_XY02.ome.tif, ...
Location: {same dir as .companion.ome}/processed/

e.g. source: /data/Exp01/Mouse1/AB_D28.companion.ome
output: /data/Exp01/Mouse1/processed/AB_D28_XY01.ome.tif


Calibration: XY+Z from metadata, Z unit forced to microns, time interval from user input
Hyperstack: XYCZT order, 2–4 channels, all raw intensity data

Modes
Single file: save outputs + leave hyperstacks open in Fiji
Batch: recursive subfolder search for .companion.ome files, same params for all, silent (no windows), progress log, skip+log failures, prominent failure summary at end
Logging

Single: file opened → params detected → each XY saved with filename only (not full path)
Batch: [ 3/12 ] Saved: /path/... → final summary → failure block if any (failures show full path)


Step 2 — SplitChannels (IN DEVELOPMENT)
Input

OME-TIFF hyperstack output from Step 1
2–4 channels, all raw intensity data
XYCZT, calibrated in microns

Goal
Split into individual single-channel stacks. User assigns a role to each channel
(e.g. T cells, collagen, vasculature) via dialog. Role assignments drive downstream
routing in Steps 4 and 5.
Details
Still being designed — discuss before writing any code.

Shared Utilities (provenzano_lab.utils)
Never duplicate these across steps.
BioFormatsUtils

openWithBioFormats(String path) — opens .companion.ome, returns ImagePlus[]
saveAsOMETIFF(ImagePlus imp, String outputPath) — saves via OMETiffWriter, uncompressed, sequential

CalibrationUtils

applyCalibration(ImagePlus imp, double pixelWidth, double pixelHeight, double voxelDepth, double frameIntervalSec) — sets calibration, forces microns
readCalibration(ImagePlus imp) — returns double[] {pixelWidth, pixelHeight, voxelDepth}

LogUtils

log(String msg) — timestamped IJ.log entry
batchProgress(int current, int total, String savedPath) — formats as [ 3/12 ] Saved: ...
failureSummary(List<String> failures) — visually distinct block with full paths and reasons


Error Handling

Wrap all Bio-Formats ops in try/catch
Batch: catch per-file, log, continue
Single: IJ.error dialog


Open Questions (Steps 3–5)

Step 3: preprocessing ops, threshold methods, per-image override UI
Step 4: tracking algorithm (TrackMate? manual ROI? custom?), full param list
Step 5: AVI codec/compression, LUT/color assignment UI
Master runner: Step 3 manual intervention handling