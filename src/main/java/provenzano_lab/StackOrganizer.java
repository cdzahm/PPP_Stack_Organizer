package provenzano_lab;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import ij.io.OpenDialog;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import loci.formats.meta.IMetadata;
import provenzano_lab.utils.BioFormatsUtils;
import provenzano_lab.utils.CalibrationUtils;
import provenzano_lab.utils.LogUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StackOrganizer implements PlugIn {

    // Hardware channel name → default role label. Anything not in this table → "ignore".
    private static String defaultRole(String channelName) {
        if ("Ch2".equals(channelName)) return "tumor";
        if ("Ch3".equals(channelName)) return "tcells";
        if ("Ch4".equals(channelName)) return "collagen";
        return "ignore";
    }

    // Role label → IJ LUT name for composite display.
    private static String roleLUT(String role) {
        if ("tumor".equals(role))    return "Red";
        if ("tcells".equals(role))   return "Green";
        if ("collagen".equals(role)) return "Grays";
        return "Grays";
    }

    @Override
    public void run(String arg) {

        // ── Dialog 1: mode and file/folder selection ──────────────────────────
        GenericDialog d1 = new GenericDialog("Stack Organizer");
        d1.addMessage("Select processing mode:");
        d1.addRadioButtonGroup("Mode:", new String[]{"Single file", "Batch folder"}, 1, 2, "Single file");
        d1.showDialog();
        if (d1.wasCanceled()) return;

        boolean isBatch = d1.getNextRadioButton().equals("Batch folder");

        List<String> filePaths = new ArrayList<>();

        if (!isBatch) {
            IJ.showMessage("Stack Organizer — Select File",
                    "In the next dialog, navigate to your acquisition folder\n" +
                    "and select the .companion.ome file.");
            OpenDialog od = new OpenDialog("Select .companion.ome file", null);
            if (od.getPath() == null) return;
            if (!od.getPath().endsWith(".companion.ome")) {
                IJ.error("Stack Organizer", "Please select a .companion.ome file.");
                return;
            }
            filePaths.add(od.getPath());
        } else {
            IJ.showMessage("Stack Organizer — Select Folder",
                    "In the next dialog, select the folder containing your .companion.ome files.\n\n" +
                    "Note: incomplete imaging runs or single images in the same folder will appear\n" +
                    "as errors in the log but will not stop processing of complete datasets.");
            DirectoryChooser dc = new DirectoryChooser("Select folder containing .companion.ome files");
            if (dc.getDirectory() == null) return;
            collectCompanionOmeFiles(new File(dc.getDirectory()), filePaths);
            if (filePaths.isEmpty()) {
                IJ.error("Stack Organizer", "No .companion.ome files found in the selected folder.");
                return;
            }
        }

        // ── Pre-read metadata from the first file ─────────────────────────────
        int metaNC = 3, metaNZ = 20, metaNT = 120;
        double metaPixXY = 1.0, metaVoxZ = 1.0;
        String[] channelNames = null;
        IMetadata firstFileMeta = null;

        try {
            // Open via Bio-Formats to get dimension and calibration values
            ImagePlus[] probe = BioFormatsUtils.openWithBioFormats(filePaths.get(0));
            if (probe != null && probe.length > 0 && probe[0] != null) {
                ImagePlus p = probe[0];
                metaNC = p.getNChannels();
                metaNZ = p.getNSlices();
                metaNT = p.getNFrames();
                double[] cal = CalibrationUtils.readCalibration(p);
                metaPixXY = cal[0];
                metaVoxZ  = cal[2];
                p.close();
                for (int i = 1; i < probe.length; i++) if (probe[i] != null) probe[i].close();
            }
            // Read OME metadata separately to get hardware channel names
            firstFileMeta = BioFormatsUtils.readSourceMetadata(filePaths.get(0));
            int nCMeta = firstFileMeta.getChannelCount(0);
            if (nCMeta > 0) metaNC = nCMeta;
            channelNames = new String[metaNC];
            for (int c = 0; c < metaNC; c++) {
                String name = firstFileMeta.getChannelName(0, c);
                channelNames[c] = (name != null && !name.isEmpty()) ? name : ("Ch" + (c + 1));
            }
        } catch (Exception e) {
            IJ.log("Warning: could not pre-read metadata from first file: " + e.getMessage());
        }

        // Fallback channel names if metadata read failed
        if (channelNames == null) {
            channelNames = new String[metaNC];
            for (int c = 0; c < metaNC; c++) channelNames[c] = "Ch" + (c + 1);
        }

        final int nCFixed = metaNC;
        final String[] chNames = channelNames;

        // ── Dialog 2: parameters + channel roles (loops until validation passes) ──
        int     nXY      = 2;
        int     nC       = metaNC;
        int     nZ       = metaNZ;
        double  interval = 60.0;
        double  pixXY    = metaPixXY;
        double  voxZ     = metaVoxZ;
        String[] roles   = new String[nCFixed];
        for (int c = 0; c < nCFixed; c++) roles[c] = defaultRole(chNames[c]);

        String errorMsg = null;
        while (true) {
            GenericDialog d2 = new GenericDialog("Stack Organizer — Parameters");

            if (errorMsg != null) {
                d2.addMessage("⚠  " + errorMsg + "\nPlease correct and click OK.");
            }

            d2.addNumericField("Number of XY positions (nXY):", nXY, 0);
            d2.addNumericField("Channels (nC) [from metadata, editable]:", nC, 0);
            d2.addNumericField("Z planes (nZ) [from metadata, editable]:", nZ, 0);
            d2.addMessage("Total timepoints (nT total): " + metaNT + "  [read-only, from metadata]");
            int tPerPos = (nXY > 0) ? metaNT / nXY : 0;
            d2.addMessage("Timepoints per position: " + tPerPos + "  (= nT / nXY, read-only)");
            d2.addNumericField("Time interval (seconds):", interval, 1);
            d2.addNumericField("Pixel size XY (microns):", pixXY, 6);
            d2.addNumericField("Voxel depth Z (microns):", voxZ, 6);
            d2.addMessage("─── Channel role assignments ───\n" +
                          "Use 'ignore' to skip a channel. Roles drive the output filename\n" +
                          "and OME Channel:Name. No two active channels may share a role.");
            for (int c = 0; c < nCFixed; c++) {
                d2.addStringField(chNames[c] + " role:", roles[c], 12);
            }

            d2.showDialog();
            if (d2.wasCanceled()) return;

            nXY      = (int) d2.getNextNumber();
            nC       = (int) d2.getNextNumber();
            nZ       = (int) d2.getNextNumber();
            interval = d2.getNextNumber();
            pixXY    = d2.getNextNumber();
            voxZ     = d2.getNextNumber();
            for (int c = 0; c < nCFixed; c++) roles[c] = d2.getNextString().trim().toLowerCase();

            // Validate: nXY
            if (nXY < 1) {
                errorMsg = "nXY must be at least 1.";
                continue;
            }

            // Validate: no duplicate non-ignore roles
            Set<String> seen = new HashSet<>();
            String dup = null;
            for (String r : roles) {
                if (!"ignore".equals(r) && !seen.add(r)) {
                    dup = r;
                    break;
                }
            }
            if (dup != null) {
                errorMsg = "Duplicate role '" + dup + "' — each active channel must have a unique role.";
                continue;
            }

            // All channels ignored?
            boolean anyActive = false;
            for (String r : roles) if (!"ignore".equals(r)) { anyActive = true; break; }
            if (!anyActive) {
                errorMsg = "All channels are set to 'ignore'. At least one must have a role.";
                continue;
            }

            errorMsg = null;
            break;
        }

        // Warn if nT not evenly divisible by nXY
        if (nXY > 1 && metaNT % nXY != 0) {
            GenericDialog warn = new GenericDialog("Warning");
            warn.addMessage("nT total (" + metaNT + ") is not evenly divisible by nXY (" + nXY + ").\n" +
                            "The last XY position will have fewer timepoints.\nContinue anyway?");
            warn.showDialog();
            if (warn.wasCanceled()) return;
        }

        // Build the active-channel list: (sourceChannelIndex, role)
        final List<Integer> activeCIdx  = new ArrayList<>();
        final List<String>  activeRoles = new ArrayList<>();
        for (int c = 0; c < nCFixed; c++) {
            if (!"ignore".equals(roles[c])) {
                activeCIdx.add(c);
                activeRoles.add(roles[c]);
            }
        }

        // Freeze dialog values for use inside the processing loop
        final int    finalNXY      = nXY;
        final int    finalNC       = nC;
        final int    finalNZ       = nZ;
        final double finalInterval = interval;
        final double finalPixXY    = pixXY;
        final double finalVoxZ     = voxZ;
        final IMetadata finalFirstMeta = firstFileMeta;

        // ── Process files ──────────────────────────────────────────────────────
        List<String> failures = new ArrayList<>();
        int total = filePaths.size();

        for (int fileIdx = 0; fileIdx < total; fileIdx++) {
            String filePath = filePaths.get(fileIdx);
            try {
                IMetadata fileMeta;
                if (isBatch && fileIdx > 0) {
                    // Re-read OME metadata for each batch file; validate channel mapping matches
                    fileMeta = BioFormatsUtils.readSourceMetadata(filePath);
                    int fileNC = fileMeta.getChannelCount(0);
                    if (fileNC != nCFixed) {
                        throw new Exception("Channel count mismatch: expected " + nCFixed +
                                            ", got " + fileNC);
                    }
                    for (int c = 0; c < nCFixed; c++) {
                        String name = fileMeta.getChannelName(0, c);
                        if (name == null) name = "";
                        if (!name.equals(chNames[c])) {
                            throw new Exception("Channel name mismatch at index " + c +
                                                ": expected '" + chNames[c] + "', got '" + name + "'");
                        }
                    }
                } else {
                    fileMeta = finalFirstMeta;
                }

                processFile(filePath, fileMeta, finalNXY, finalNC, finalNZ,
                            finalInterval, finalPixXY, finalVoxZ,
                            activeCIdx, activeRoles, isBatch);

                if (isBatch) {
                    LogUtils.batchProgress(fileIdx + 1, total, getOutputDir(filePath));
                }
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                failures.add(filePath + "  →  " + reason);
                LogUtils.log("FAILED: " + new File(filePath).getName() + " — " + reason);
            }
        }

        if (isBatch && !failures.isEmpty()) LogUtils.failureSummary(failures);

        if (!isBatch) {
            LogUtils.log("Stack Organizer complete.");
        } else {
            LogUtils.log("Batch complete. " + (total - failures.size()) + "/" + total + " files succeeded.");
        }
    }

    // ── Core processing: open file, de-interleave XY, split channels, save ───
    private void processFile(
            String filePath,
            IMetadata sourceMeta,
            int nXY,
            int nC,
            int nZ,
            double frameInterval,
            double pixelSizeXY,
            double voxelDepth,
            List<Integer> activeCIdx,
            List<String> activeRoles,
            boolean isBatch) throws Exception {

        LogUtils.log("Opening: " + new File(filePath).getName());
        ImagePlus[] imps = BioFormatsUtils.openWithBioFormats(filePath);
        if (imps == null || imps.length == 0 || imps[0] == null) {
            throw new Exception("Bio-Formats returned no images.");
        }
        ImagePlus source = imps[0];
        for (int i = 1; i < imps.length; i++) if (imps[i] != null) imps[i].close();

        int nT_total = source.getNFrames();
        // When nXY=1 there is no interleaving; nT_per equals nT_total.
        int nT_per = (nXY > 1) ? nT_total / nXY : nT_total;

        String outputDir = getOutputDir(filePath);
        new File(outputDir).mkdirs();
        String basename = getBasename(filePath);

        ImageStack srcStack = source.getStack();

        for (int xyIdx = 0; xyIdx < nXY; xyIdx++) {
            String xyLabel = String.format("XY%02d", xyIdx + 1);

            // ── Multi-channel composite for this XY position ──────────────────
            {
                int nActive = activeCIdx.size();
                // IJ hyperstack slice order: C varies fastest, then Z, then T.
                ImageStack compositeStack = new ImageStack(source.getWidth(), source.getHeight());
                for (int t = 0; t < nT_per; t++) {
                    int srcFrame = t * nXY + xyIdx;
                    for (int z = 0; z < nZ; z++) {
                        for (int ai = 0; ai < nActive; ai++) {
                            int cIdx = activeCIdx.get(ai);
                            int srcSliceIdx = source.getStackIndex(cIdx + 1, z + 1, srcFrame + 1);
                            compositeStack.addSlice(srcStack.getSliceLabel(srcSliceIdx),
                                                    srcStack.getProcessor(srcSliceIdx));
                        }
                    }
                }

                String compositeFilename = basename + "_" + xyLabel + ".ome.tif";
                String compositePath = outputDir + File.separator + compositeFilename;

                ImagePlus baseImp = new ImagePlus(compositeFilename, compositeStack);
                baseImp.setDimensions(nActive, nZ, nT_per);
                baseImp.setOpenAsHyperStack(true);

                Calibration cal = baseImp.getCalibration();
                cal.pixelWidth    = pixelSizeXY;
                cal.pixelHeight   = pixelSizeXY;
                cal.pixelDepth    = voxelDepth;
                cal.frameInterval = frameInterval;
                cal.setUnit("micron");
                cal.setTimeUnit("sec");

                // Construct CompositeImage directly — avoids the stale-reference problem
                // that occurs when using IJ.run("Make Composite") which creates a new object.
                CompositeImage compositeImp = new CompositeImage(baseImp, CompositeImage.COMPOSITE);
                for (int ai = 0; ai < nActive; ai++) {
                    compositeImp.setC(ai + 1);
                    IJ.run(compositeImp, roleLUT(activeRoles.get(ai)), "");
                }

                LogUtils.log("Saving composite: " + compositeFilename);
                BioFormatsUtils.saveAsOMETIFF(compositeImp, compositePath);

                if (!isBatch) {
                    compositeImp.show();
                } else {
                    compositeImp.close();
                }
            }

            for (int ai = 0; ai < activeCIdx.size(); ai++) {
                int cIdx   = activeCIdx.get(ai);
                String role = activeRoles.get(ai);

                // Build a single-channel stack for this XY position and channel.
                // Interleave pattern: timepoint t at position xyIdx lives at source frame
                // index (t * nXY + xyIdx). When nXY=1 this simplifies to frame t.
                ImageStack outStack = new ImageStack(source.getWidth(), source.getHeight());
                for (int t = 0; t < nT_per; t++) {
                    int srcFrame = t * nXY + xyIdx; // 0-indexed in source
                    for (int z = 0; z < nZ; z++) {
                        int srcIdx = source.getStackIndex(cIdx + 1, z + 1, srcFrame + 1);
                        outStack.addSlice(srcStack.getSliceLabel(srcIdx),
                                          srcStack.getProcessor(srcIdx));
                    }
                }

                String outFilename = basename + "_" + xyLabel + "_" + role + ".ome.tif";
                ImagePlus outImp = new ImagePlus(outFilename, outStack);
                outImp.setDimensions(1, nZ, nT_per);
                outImp.setOpenAsHyperStack(true);

                String outPath  = outputDir + File.separator + outFilename;
                String imageId  = "Image:"  + xyLabel + "_" + role;
                String pixelsId = "Pixels:" + xyLabel + "_" + role;

                LogUtils.log("Saving: " + outFilename);
                BioFormatsUtils.saveChannelAsOMETIFF(
                        outImp, sourceMeta,
                        imageId, pixelsId,
                        nZ, nT_per,
                        pixelSizeXY, pixelSizeXY, voxelDepth,
                        frameInterval,
                        cIdx, role,
                        outPath);

                outImp.close();
            }
        }

        source.close();
    }

    private String getOutputDir(String filePath) {
        return new File(filePath).getParent() + File.separator + "processed";
    }

    private String getBasename(String filePath) {
        String name = new File(filePath).getName();
        if (name.endsWith(".companion.ome")) {
            name = name.substring(0, name.length() - ".companion.ome".length());
        }
        return name;
    }

    private void collectCompanionOmeFiles(File dir, List<String> results) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory()) collectCompanionOmeFiles(entry, results);
            else if (entry.getName().endsWith(".companion.ome")) results.add(entry.getAbsolutePath());
        }
    }
}
