package provenzano_lab;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import provenzano_lab.utils.BioFormatsUtils;
import provenzano_lab.utils.CalibrationUtils;
import provenzano_lab.utils.LogUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Step1_SeparateXYSeries implements PlugIn {

    @Override
    public void run(String arg) {
        // --- Dialog 1: mode and file selection ---
        GenericDialog d1 = new GenericDialog("Step 1 — Separate XY Series");
        d1.addMessage("Select processing mode:");
        String[] modes = {"Single file", "Batch folder"};
        d1.addRadioButtonGroup("Mode:", modes, 1, 2, "Single file");
        d1.showDialog();
        if (d1.wasCanceled()) return;

        String mode = d1.getNextRadioButton();
        boolean isBatch = mode.equals("Batch folder");

        // Collect target paths
        List<String> filePaths = new ArrayList<>();
        String baseDir;

        if (!isBatch) {
            OpenDialog od = new OpenDialog("Select .companion.ome file", null);
            if (od.getPath() == null) return;
            String path = od.getPath();
            if (!path.endsWith(".companion.ome")) {
                IJ.error("Step 1", "Please select a .companion.ome file.");
                return;
            }
            filePaths.add(path);
            baseDir = od.getDirectory();
        } else {
            DirectoryChooser dc = new DirectoryChooser("Select folder containing .companion.ome files");
            baseDir = dc.getDirectory();
            if (baseDir == null) return;
            collectCompanionOmeFiles(new File(baseDir), filePaths);
            if (filePaths.isEmpty()) {
                IJ.error("Step 1", "No .companion.ome files found in the selected folder.");
                return;
            }
        }

        // Pre-read metadata from first file for dialog defaults
        int metaNC = 1, metaNZ = 1, metaNT = 1;
        double metaPixW = 1.0, metaPixH = 1.0, metaVoxD = 1.0;
        try {
            ImagePlus[] probe = BioFormatsUtils.openWithBioFormats(filePaths.get(0));
            if (probe != null && probe.length > 0 && probe[0] != null) {
                ImagePlus p = probe[0];
                metaNC = p.getNChannels();
                metaNZ = p.getNSlices();
                metaNT = p.getNFrames();
                double[] cal = CalibrationUtils.readCalibration(p);
                metaPixW = cal[0];
                metaPixH = cal[1];
                metaVoxD = cal[2];
                p.close();
                for (int i = 1; i < probe.length; i++) if (probe[i] != null) probe[i].close();
            }
        } catch (Exception e) {
            IJ.log("Warning: could not pre-read metadata from first file: " + e.getMessage());
        }

        // --- Dialog 2: parameters ---
        GenericDialog d2 = new GenericDialog("Step 1 — Parameters");
        d2.addNumericField("Number of XY positions (nXY):", 2, 0);
        d2.addNumericField("Channels (nC) [from metadata, editable]:", metaNC, 0);
        d2.addNumericField("Z planes (nZ) [from metadata, editable]:", metaNZ, 0);
        d2.addMessage("Total timepoints (nT): " + metaNT + "  [read-only]");
        int nXY_preview = 2;
        int nT_per_preview = (nXY_preview > 0) ? metaNT / nXY_preview : metaNT;
        d2.addMessage("Timepoints per position: " + nT_per_preview + "  (= nT / nXY, read-only)");
        d2.addNumericField("Time interval (seconds):", 60.0, 1);
        d2.addNumericField("Pixel width (microns):", metaPixW, 6);
        d2.addNumericField("Pixel height (microns):", metaPixH, 6);
        d2.addNumericField("Voxel depth (microns):", metaVoxD, 6);
        d2.showDialog();
        if (d2.wasCanceled()) return;

        int nXY = (int) d2.getNextNumber();
        int nC = (int) d2.getNextNumber();
        int nZ = (int) d2.getNextNumber();
        double frameInterval = d2.getNextNumber();
        double pixelWidth = d2.getNextNumber();
        double pixelHeight = d2.getNextNumber();
        double voxelDepth = d2.getNextNumber();

        if (nXY < 1) { IJ.error("Step 1", "nXY must be at least 1."); return; }

        // Warn if nT not evenly divisible by nXY
        if (nXY > 1 && metaNT % nXY != 0) {
            GenericDialog warn = new GenericDialog("Warning");
            warn.addMessage("nT_total (" + metaNT + ") is not evenly divisible by nXY (" + nXY + ").\n" +
                            "The last position may have fewer timepoints.\nContinue anyway?");
            warn.showDialog();
            if (warn.wasCanceled()) return;
        }

        // --- Process files ---
        List<String> failures = new ArrayList<>();
        int total = filePaths.size();

        for (int fileIdx = 0; fileIdx < total; fileIdx++) {
            String filePath = filePaths.get(fileIdx);
            try {
                processFile(filePath, nXY, nC, nZ, frameInterval,
                            pixelWidth, pixelHeight, voxelDepth, isBatch);
                if (isBatch) {
                    String outDir = getOutputDir(filePath);
                    LogUtils.batchProgress(fileIdx + 1, total, outDir);
                }
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                failures.add(filePath + "  →  " + reason);
                LogUtils.log("FAILED: " + filePath + " — " + reason);
            }
        }

        if (isBatch && !failures.isEmpty()) {
            LogUtils.failureSummary(failures);
        }

        if (!isBatch) {
            LogUtils.log("Step 1 complete.");
        } else {
            LogUtils.log("Batch complete. " + (total - failures.size()) + "/" + total + " files succeeded.");
        }
    }

    private void processFile(String filePath, int nXY, int nC, int nZ,
                              double frameInterval, double pixelWidth, double pixelHeight,
                              double voxelDepth, boolean isBatch) throws Exception {
        LogUtils.log("Opening: " + filePath);
        ImagePlus[] imps = BioFormatsUtils.openWithBioFormats(filePath);
        if (imps == null || imps.length == 0 || imps[0] == null) {
            throw new Exception("Bio-Formats returned no images.");
        }
        ImagePlus source = imps[0];
        for (int i = 1; i < imps.length; i++) if (imps[i] != null) imps[i].close();

        int nT_total = source.getNFrames();
        String outputDir = getOutputDir(filePath);
        new File(outputDir).mkdirs();

        String basename = getBasename(filePath);

        if (nXY == 1) {
            // Passthrough: just apply calibration and save
            CalibrationUtils.applyCalibration(source, pixelWidth, pixelHeight, voxelDepth, frameInterval);
            String outPath = outputDir + File.separator + basename + "_XY01.ome.tif";
            LogUtils.log("Saving: " + outPath);
            BioFormatsUtils.saveAsOMETIFF(source, outPath);
            if (!isBatch) {
                source.show();
            } else {
                source.close();
            }
            return;
        }

        // De-interleave: pattern is XY1-T1, XY2-T1, ..., XYn-T1, XY1-T2, ...
        int nT_per = nT_total / nXY;
        ImageStack srcStack = source.getStack();

        for (int xyIdx = 0; xyIdx < nXY; xyIdx++) {
            ImageStack outStack = new ImageStack(source.getWidth(), source.getHeight());

            for (int t = 0; t < nT_per; t++) {
                // Source frame index (1-indexed in ImageJ)
                // Interleave pattern: frame for xyIdx at timepoint t is at position (t*nXY + xyIdx)
                int srcFrameIndex = t * nXY + xyIdx; // 0-indexed timepoint in source

                for (int z = 0; z < nZ; z++) {
                    for (int c = 0; c < nC; c++) {
                        // getStackIndex is 1-indexed: (channel, slice, frame)
                        int stackIdx = source.getStackIndex(c + 1, z + 1, srcFrameIndex + 1);
                        outStack.addSlice(srcStack.getSliceLabel(stackIdx),
                                          srcStack.getProcessor(stackIdx));
                    }
                }
            }

            String label = String.format("%s_XY%02d.ome.tif", basename, xyIdx + 1);
            ImagePlus outImp = new ImagePlus(label, outStack);
            outImp.setDimensions(nC, nZ, nT_per);
            outImp.setOpenAsHyperStack(true);

            CalibrationUtils.applyCalibration(outImp, pixelWidth, pixelHeight, voxelDepth, frameInterval);

            String outPath = outputDir + File.separator + label;
            LogUtils.log("Saving: " + outPath);
            BioFormatsUtils.saveAsOMETIFF(outImp, outPath);

            if (!isBatch) {
                outImp.show();
            } else {
                outImp.close();
            }
        }

        source.close();
    }

    private String getOutputDir(String filePath) {
        File f = new File(filePath);
        return f.getParent() + File.separator + "processed";
    }

    private String getBasename(String filePath) {
        String name = new File(filePath).getName();
        // Strip .companion.ome
        if (name.endsWith(".companion.ome")) {
            name = name.substring(0, name.length() - ".companion.ome".length());
        }
        return name;
    }

    private void collectCompanionOmeFiles(File dir, List<String> results) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectCompanionOmeFiles(entry, results);
            } else if (entry.getName().endsWith(".companion.ome")) {
                results.add(entry.getAbsolutePath());
            }
        }
    }
}
