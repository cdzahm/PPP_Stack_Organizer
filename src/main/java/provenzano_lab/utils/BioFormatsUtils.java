package provenzano_lab.utils;

import ij.ImagePlus;
import ij.WindowManager;
import loci.plugins.BF;
import loci.plugins.LociExporter;
import loci.plugins.in.ImporterOptions;

public class BioFormatsUtils {

    public static ImagePlus[] openWithBioFormats(String path) throws Exception {
        ImporterOptions options = new ImporterOptions();
        options.setId(path);
        options.setOpenAllSeries(true);
        options.setConcatenate(true);
        options.setStackFormat(ImporterOptions.VIEW_HYPERSTACK);
        options.setStackOrder(ImporterOptions.ORDER_XYCZT);
        return BF.openImagePlus(options);
    }

    /**
     * Saves an ImagePlus as OME-TIFF using LociExporter in headless/macro mode.
     * This matches the speed of a manual Bio-Formats Exporter save (~15 sec)
     * and avoids the interactive dialog by passing the output path as a macro argument.
     */
    public static void saveAsOMETIFF(ImagePlus imp, String outputPath) throws Exception {
        WindowManager.setTempCurrentImage(imp);
        LociExporter exporter = new LociExporter();
        exporter.arg = "outfile=[" + outputPath + "]";
        exporter.run(exporter.arg);
        WindowManager.setTempCurrentImage(null);
    }
}
