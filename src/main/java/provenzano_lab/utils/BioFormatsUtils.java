package provenzano_lab.utils;

import ij.ImagePlus;
import ij.io.FileSaver;
import loci.common.DebugTools;
import loci.common.services.ServiceFactory;
import loci.formats.ImageReader;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BioFormatsUtils {

    // Bio-Formats' readers log per-plane/per-IFD status internally at INFO level (SLF4J), which
    // floods the console with repeating "Reading IFDs / Populating metadata / Checking comment
    // style / Populating OME metadata" for every file it touches — not intentional plugin
    // logging. Silenced once here (first class load) rather than per call, so it stays quiet for
    // both the pre-read metadata probe and the actual pixel-reading pass. The plugin shows its
    // own single-line status via IJ.showStatus at each call site instead.
    static {
        DebugTools.setRootLevel("ERROR");
    }

    // setConcatenate(false) is required here — confirmed via direct testing that
    // setConcatenate(true) causes BF.openImagePlus() to re-merge PrairieReader's already
    // position-separated series back into a single interleaved SizeT=(nT_per_position * nXY)
    // stack (this only shows up at the full ImagePlus-reading stage; a lower-level
    // ImportProcess.getSeriesCount() check does not reveal it, which is what led to the
    // original "no de-interleave needed" assumption). With setConcatenate(false),
    // BF.openImagePlus() returns one ImagePlus per XY-position series as expected.
    public static ImagePlus[] openWithBioFormats(String path) throws Exception {
        ImporterOptions options = new ImporterOptions();
        options.setId(path);
        options.setOpenAllSeries(true);
        options.setConcatenate(false);
        options.setStackFormat(ImporterOptions.VIEW_HYPERSTACK);
        options.setStackOrder(ImporterOptions.ORDER_XYCZT);
        return BF.openImagePlus(options);
    }

    // Resolves a user-selected file or folder to the single PrairieView .xml Bio-Formats
    // should open. Throws with a clear message if the selection is ambiguous (folder has
    // none, or has multiple .xml files).
    public static String resolveAcquisitionPath(File selected) throws Exception {
        if (selected.isDirectory()) {
            File[] entries = selected.listFiles();
            if (entries == null) throw new Exception("Cannot read folder: " + selected);
            List<File> xmls = new ArrayList<>();
            for (File e : entries) {
                if (e.isFile() && e.getName().toLowerCase().endsWith(".xml")) xmls.add(e);
            }
            if (xmls.size() == 1) return xmls.get(0).getAbsolutePath();
            if (xmls.isEmpty()) {
                throw new Exception("No .xml acquisition file found in: " + selected);
            }
            throw new Exception("Multiple .xml files found in " + selected +
                    " — cannot determine the acquisition file.");
        } else {
            String name = selected.getName();
            if (name.toLowerCase().endsWith(".xml")) {
                return selected.getAbsolutePath();
            }
            throw new Exception("Select a PrairieView .xml acquisition file (or its containing folder).");
        }
    }

    // Reads OME metadata (channel names, etc.) from a PrairieView .xml without loading pixel
    // data. Returns an IMetadata with one Image element per XY-position series.
    public static IMetadata readSourceMetadata(String xmlPath) throws Exception {
        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        IMetadata meta = service.createOMEXMLMetadata();
        ImageReader reader = new ImageReader();
        reader.setMetadataStore(meta);
        reader.setId(xmlPath);
        reader.close();
        return meta;
    }

    // Saves a hyperstack as a standard ImageJ TIFF (not OME-TIFF). Calibration (pixel size,
    // voxel depth, unit, frame interval) and channel LUTs/composite mode already carried on
    // the ImagePlus round-trip natively through ImageJ's own TIFF metadata block — no
    // separate OME metadata construction needed. Single-channel or multi-channel composite,
    // any Z/T extent — FileSaver handles the full hyperstack via saveAsTiffStack.
    public static void saveAsTiff(ImagePlus imp, String outputPath) throws Exception {
        FileSaver saver = new FileSaver(imp);
        boolean ok = saver.saveAsTiff(outputPath);
        if (!ok) {
            throw new Exception("Failed to save TIFF: " + outputPath);
        }
    }
}
