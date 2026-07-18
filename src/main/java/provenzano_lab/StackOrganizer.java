package provenzano_lab;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import ij.plugin.PlugIn;
import loci.formats.meta.IMetadata;
import provenzano_lab.utils.BioFormatsUtils;
import provenzano_lab.utils.CalibrationUtils;
import provenzano_lab.utils.LogUtils;
import provenzano_lab.utils.PrairieXmlUtils;

import javax.swing.JFileChooser;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Label;
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
            IJ.showMessage("Stack Organizer — Select Acquisition",
                    "In the next dialog, select the acquisition file or its containing folder.\n\n" +
                    "Select the PrairieView .xml file for the acquisition, or the folder\n" +
                    "containing it.");
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Select acquisition file or folder");
            fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            int result = fc.showOpenDialog(null);
            if (result != JFileChooser.APPROVE_OPTION) return;

            String resolved;
            try {
                resolved = BioFormatsUtils.resolveAcquisitionPath(fc.getSelectedFile());
            } catch (Exception e) {
                IJ.error("Stack Organizer", e.getMessage());
                return;
            }
            filePaths.add(resolved);
        } else {
            IJ.showMessage("Stack Organizer — Select Folder",
                    "In the next dialog, select the root folder to search recursively.\n\n" +
                    "Finds one PrairieView .xml file per acquisition folder.\n\n" +
                    "Note: incomplete imaging runs, single images, or ambiguous folders (multiple\n" +
                    ".xml files in one folder) will appear as skipped/errors in the log but will\n" +
                    "not stop processing of complete datasets.");
            DirectoryChooser dc = new DirectoryChooser("Select root folder");
            if (dc.getDirectory() == null) return;
            collectAcquisitionFiles(new File(dc.getDirectory()), filePaths);
            if (filePaths.isEmpty()) {
                IJ.error("Stack Organizer", "No .xml acquisition files found in the selected folder.");
                return;
            }
        }

        // ── Pre-read metadata from the first file ─────────────────────────────
        String firstPath = filePaths.get(0);

        int metaNC = 3, metaNZ = 20, metaNT = 60, metaNXY = 2;
        double metaPixXY = 1.0, metaVoxZ = 1.0, metaInterval = 60.0;
        String[] channelNames = null;
        IMetadata firstFileMeta = null;

        LogUtils.log("Assessing metadata — this may take a few min...");
        IJ.showStatus("Assessing acquisition files...");
        try {
            firstFileMeta = BioFormatsUtils.readSourceMetadata(firstPath);

            // One Bio-Formats series per XY position, already fully split by PrairieReader —
            // series count IS the real nXY, and each series' nFrames is already per-position.
            ImagePlus[] probe = BioFormatsUtils.openWithBioFormats(firstPath);
            if (probe != null && probe.length > 0 && probe[0] != null) {
                metaNXY = probe.length;
                ImagePlus p0 = probe[0];
                metaNC = p0.getNChannels();
                metaNZ = p0.getNSlices();
                metaNT = p0.getNFrames();
                double[] cal = CalibrationUtils.readCalibration(p0);
                metaPixXY = cal[0];
                for (ImagePlus pp : probe) if (pp != null) pp.close();
            }
            IJ.showStatus("");
            // IMetadata from PrairieReader leaves PhysicalSizeZ / TimeIncrement null
            // (confirmed via direct testing) — read them from the XML directly instead.
            // Prairie's XML is unambiguously in microns, so no unit-forcing workaround is needed.
            try {
                metaVoxZ = PrairieXmlUtils.readVoxelDepthUm(firstPath);
            } catch (Exception e) {
                LogUtils.log("Warning: could not read voxel depth from Prairie XML: " + e.getMessage());
            }
            try {
                metaInterval = PrairieXmlUtils.readFrameIntervalSec(firstPath, metaNXY);
            } catch (Exception e) {
                LogUtils.log("Warning: could not read frame interval from Prairie XML: " + e.getMessage());
            }

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
        int     nXY      = metaNXY;
        int     nC       = metaNC;
        int     nZ       = metaNZ;
        double  interval = metaInterval;
        double  pixXY    = metaPixXY;
        double  voxZ     = metaVoxZ;
        String[] roles   = new String[nCFixed];
        for (int c = 0; c < nCFixed; c++) roles[c] = defaultRole(chNames[c]);

        final int finalMetaNT = metaNT;
        String errorMsg = null;
        while (true) {
            GenericDialog d2 = new GenericDialog("Stack Organizer — Parameters");

            if (errorMsg != null) {
                d2.addMessage("⚠  " + errorMsg + "\nPlease correct and click OK.");
            }

            d2.addMessage("Auto-detected XY positions: " + metaNXY + "  (Bio-Formats series count — positions already separated)");
            d2.addNumericField("Number of XY positions (nXY):", nXY, 0);
            d2.addNumericField("Channels (nC) [from metadata, editable]:", nC, 0);
            d2.addNumericField("Z planes (nZ) [from metadata, editable]:", nZ, 0);
            d2.addMessage("Timepoints per position (nT): " + finalMetaNT +
                    "  [read-only, from metadata — already separated per position]");
            final Component tPerPosComponent = d2.getMessage();

            d2.addNumericField("Time interval (seconds):", interval, 1);
            d2.addNumericField("Pixel size XY (microns):", pixXY, 6);
            d2.addNumericField("Voxel depth Z (microns):", voxZ, 6);
            d2.addMessage("─── Channel role assignments ───\n" +
                          "Use 'ignore' to skip a channel. Roles drive the output filename.\n" +
                          "No two active channels may share a role.");
            for (int c = 0; c < nCFixed; c++) {
                d2.addStringField(chNames[c] + " role:", roles[c], 12);
            }

            d2.addDialogListener(new DialogListener() {
                @Override
                public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
                    gd.getNextNumber(); // skip nXY (no effect on nT display on this path)
                    gd.getNextNumber(); // skip nC
                    gd.getNextNumber(); // skip nZ
                    gd.getNextNumber(); // skip interval
                    gd.getNextNumber(); // skip pixXY
                    gd.getNextNumber(); // skip voxZ
                    for (int c = 0; c < nCFixed; c++) {
                        gd.getNextString(); // skip roles
                    }
                    return !gd.invalidNumber();
                }
            });

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
        final int    finalNZ       = nZ;
        final double finalInterval = interval;
        final double finalPixXY    = pixXY;
        final double finalVoxZ     = voxZ;

        // ── Process files ──────────────────────────────────────────────────────
        List<String> failures = new ArrayList<>();
        int total = filePaths.size();

        for (int fileIdx = 0; fileIdx < total; fileIdx++) {
            String filePath = filePaths.get(fileIdx);
            try {
                if (isBatch && fileIdx > 0) {
                    // Validate channel mapping matches the confirmed-once dialog values
                    IMetadata fileMeta = BioFormatsUtils.readSourceMetadata(filePath);
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
                }

                processFile(filePath, finalNXY, finalNZ,
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

    // ── PrairieReader already returns one Bio-Formats series per XY position — no
    //    de-interleave math, only channel-splitting per position ──────────────────────
    private void processFile(
            String filePath,
            int userNXY,
            int nZ,
            double frameInterval,
            double pixelSizeXY,
            double voxelDepth,
            List<Integer> activeCIdx,
            List<String> activeRoles,
            boolean isBatch) throws Exception {

        LogUtils.log("Opening: " + new File(filePath).getName());
        IJ.showStatus("Opening: " + new File(filePath).getName() + "...");
        ImagePlus[] imps = BioFormatsUtils.openWithBioFormats(filePath);
        IJ.showStatus("");
        if (imps == null || imps.length == 0 || imps[0] == null) {
            throw new Exception("Bio-Formats returned no images.");
        }

        // One Bio-Formats series per XY position (confirmed: setConcatenate(true) does NOT
        // merge Prairie's already-distinct per-position series). Series/Image index order is
        // used directly as position ordinal — no coordinate math needed.
        int nXY = imps.length;
        if (nXY != userNXY) {
            LogUtils.log("NOTE: " + new File(filePath).getName() + " — Bio-Formats reports " + nXY +
                    " XY position series; using " + nXY + " (entered nXY=" + userNXY +
                    " is informational only — positions are already separated, no de-interleave math applies).");
        }

        String outputDir = getOutputDir(filePath);
        new File(outputDir).mkdirs();
        String basename = getBasename(filePath);

        for (int xyIdx = 0; xyIdx < nXY; xyIdx++) {
            ImagePlus source = imps[xyIdx];
            if (source == null) continue;
            int nT_per = source.getNFrames(); // already the per-position extent
            ImageStack srcStack = source.getStack();

            writeXYPosition(source, srcStack, xyIdx, nT_per, nZ,
                    pixelSizeXY, voxelDepth, frameInterval,
                    activeCIdx, activeRoles,
                    outputDir, basename, isBatch);

            source.close();
        }
    }

    // ── Write the composite + per-role single-channel outputs for one XY position ──
    private void writeXYPosition(
            ImagePlus source,
            ImageStack srcStack,
            int xyIdx,
            int nT_per,
            int nZ,
            double pixelSizeXY,
            double voxelDepth,
            double frameInterval,
            List<Integer> activeCIdx,
            List<String> activeRoles,
            String outputDir,
            String basename,
            boolean isBatch) throws Exception {

        String xyLabel = String.format("XY%02d", xyIdx + 1);

        // ── Multi-channel composite for this XY position ──────────────────
        {
            int nActive = activeCIdx.size();
            // IJ hyperstack slice order: C varies fastest, then Z, then T.
            ImageStack compositeStack = new ImageStack(source.getWidth(), source.getHeight());
            for (int t = 0; t < nT_per; t++) {
                for (int z = 0; z < nZ; z++) {
                    for (int ai = 0; ai < nActive; ai++) {
                        int cIdx = activeCIdx.get(ai);
                        int srcSliceIdx = source.getStackIndex(cIdx + 1, z + 1, t + 1);
                        compositeStack.addSlice(srcStack.getSliceLabel(srcSliceIdx),
                                                srcStack.getProcessor(srcSliceIdx));
                    }
                }
            }

            String compositeFilename = basename + "_" + xyLabel + ".tif";
            String compositePath = outputDir + File.separator + compositeFilename;

            ImagePlus baseImp = new ImagePlus(compositeFilename, compositeStack);
            baseImp.setDimensions(nActive, nZ, nT_per);
            baseImp.setOpenAsHyperStack(true);
            CalibrationUtils.applyCalibration(baseImp, pixelSizeXY, pixelSizeXY, voxelDepth, frameInterval);

            // Construct CompositeImage directly — avoids the stale-reference problem
            // that occurs when using IJ.run("Make Composite") which creates a new object.
            CompositeImage compositeImp = new CompositeImage(baseImp, CompositeImage.COMPOSITE);
            for (int ai = 0; ai < nActive; ai++) {
                compositeImp.setC(ai + 1);
                IJ.run(compositeImp, roleLUT(activeRoles.get(ai)), "");
            }

            LogUtils.log("Saving composite: " + compositeFilename);
            BioFormatsUtils.saveAsTiff(compositeImp, compositePath);

            if (!isBatch) {
                compositeImp.show();
            } else {
                compositeImp.close();
            }
        }

        for (int ai = 0; ai < activeCIdx.size(); ai++) {
            int cIdx   = activeCIdx.get(ai);
            String role = activeRoles.get(ai);

            ImageStack outStack = new ImageStack(source.getWidth(), source.getHeight());
            for (int t = 0; t < nT_per; t++) {
                for (int z = 0; z < nZ; z++) {
                    int srcIdx = source.getStackIndex(cIdx + 1, z + 1, t + 1);
                    outStack.addSlice(srcStack.getSliceLabel(srcIdx),
                                      srcStack.getProcessor(srcIdx));
                }
            }

            String outFilename = basename + "_" + xyLabel + "_" + role + ".tif";
            ImagePlus outImp = new ImagePlus(outFilename, outStack);
            outImp.setDimensions(1, nZ, nT_per);
            outImp.setOpenAsHyperStack(true);
            CalibrationUtils.applyCalibration(outImp, pixelSizeXY, pixelSizeXY, voxelDepth, frameInterval);

            String outPath = outputDir + File.separator + outFilename;

            LogUtils.log("Saving: " + outFilename);
            BioFormatsUtils.saveAsTiff(outImp, outPath);

            outImp.close();
        }
    }

    private String getOutputDir(String filePath) {
        return new File(filePath).getParent() + File.separator + "processed";
    }

    private String getBasename(String filePath) {
        String name = new File(filePath).getName();
        if (name.toLowerCase().endsWith(".xml")) {
            name = name.substring(0, name.length() - ".xml".length());
        }
        return name;
    }

    // Recursively finds acquisition files: the sole .xml (PrairieView PVScan) file in each
    // folder, if exactly one exists there. A folder with multiple .xml files is ambiguous
    // and is skipped (logged).
    private void collectAcquisitionFiles(File dir, List<String> results) {
        File[] entries = dir.listFiles();
        if (entries == null) return;

        List<File> xmls = new ArrayList<>();
        List<File> subdirs = new ArrayList<>();
        for (File e : entries) {
            if (e.isDirectory()) {
                subdirs.add(e);
            } else if (e.getName().toLowerCase().endsWith(".xml")) {
                xmls.add(e);
            }
        }

        if (xmls.size() == 1) {
            results.add(xmls.get(0).getAbsolutePath());
        } else if (xmls.size() > 1) {
            LogUtils.log("Skipping folder (ambiguous: " + xmls.size() +
                    " .xml files): " + dir.getAbsolutePath());
        }

        for (File sd : subdirs) collectAcquisitionFiles(sd, results);
    }
}
