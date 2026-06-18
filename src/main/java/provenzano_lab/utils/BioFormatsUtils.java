package provenzano_lab.utils;

import ij.ImagePlus;
import loci.plugins.BF;
import loci.plugins.LociExporter;
import loci.plugins.in.ImporterOptions;
import loci.plugins.out.Exporter;

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

    public static void saveAsOMETIFF(ImagePlus imp, String outputPath) throws Exception {
        LociExporter lociExporter = new LociExporter();
        lociExporter.arg = "outfile=" + outputPath;
        Exporter exporter = new Exporter(lociExporter, imp);
        exporter.run();
    }
}
