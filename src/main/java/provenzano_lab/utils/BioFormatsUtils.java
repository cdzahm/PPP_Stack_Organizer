package provenzano_lab.utils;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import loci.common.DataTools;
import loci.common.services.ServiceFactory;
import loci.formats.ImageWriter;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;

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

    // Writes headlessly via loci.formats.ImageWriter — never opens an interactive dialog.
    public static void saveAsOMETIFF(ImagePlus imp, String outputPath) throws Exception {
        int nC  = imp.getNChannels();
        int nZ  = imp.getNSlices();
        int nT  = imp.getNFrames();
        int ijType = imp.getType();

        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service  = factory.getInstance(OMEXMLService.class);
        IMetadata meta         = service.createOMEXMLMetadata();

        meta.createRoot();
        meta.setImageID("Image:0", 0);
        meta.setPixelsID("Pixels:0", 0);
        meta.setPixelsBigEndian(Boolean.FALSE, 0);          // little-endian TIFF
        meta.setPixelsDimensionOrder(DimensionOrder.XYCZT, 0);
        meta.setPixelsType(ijTypeToOME(ijType), 0);
        meta.setPixelsSizeX(new PositiveInteger(imp.getWidth()), 0);
        meta.setPixelsSizeY(new PositiveInteger(imp.getHeight()), 0);
        meta.setPixelsSizeZ(new PositiveInteger(nZ), 0);
        meta.setPixelsSizeC(new PositiveInteger(nC), 0);
        meta.setPixelsSizeT(new PositiveInteger(nT), 0);

        Calibration cal = imp.getCalibration();
        if (cal.pixelWidth  > 0) meta.setPixelsPhysicalSizeX(new Length(cal.pixelWidth,  UNITS.MICROMETER), 0);
        if (cal.pixelHeight > 0) meta.setPixelsPhysicalSizeY(new Length(cal.pixelHeight, UNITS.MICROMETER), 0);
        if (cal.pixelDepth  > 0) meta.setPixelsPhysicalSizeZ(new Length(cal.pixelDepth,  UNITS.MICROMETER), 0);
        if (cal.frameInterval > 0) meta.setPixelsTimeIncrement(new Time(cal.frameInterval, UNITS.SECOND), 0);

        for (int c = 0; c < nC; c++) {
            meta.setChannelID("Channel:0:" + c, 0, c);
            meta.setChannelSamplesPerPixel(new PositiveInteger(1), 0, c);
        }

        ImageWriter writer = new ImageWriter();
        writer.setMetadataRetrieve(meta);
        writer.setId(outputPath);

        ImageStack stack = imp.getStack();
        int plane = 0;
        for (int t = 0; t < nT; t++) {
            for (int z = 0; z < nZ; z++) {
                for (int c = 0; c < nC; c++) {
                    ImageProcessor ip = stack.getProcessor(imp.getStackIndex(c + 1, z + 1, t + 1));
                    writer.saveBytes(plane++, processorToBytes(ip, ijType));
                }
            }
        }

        writer.close();
    }

    private static PixelType ijTypeToOME(int ijType) {
        switch (ijType) {
            case ImagePlus.GRAY16: return PixelType.UINT16;
            case ImagePlus.GRAY32: return PixelType.FLOAT;
            default:               return PixelType.UINT8;
        }
    }

    // DataTools handles little-endian packing; GRAY8 pixels are already raw bytes.
    private static byte[] processorToBytes(ImageProcessor ip, int ijType) {
        switch (ijType) {
            case ImagePlus.GRAY16:
                return DataTools.shortsToBytes((short[]) ip.getPixels(), true);
            case ImagePlus.GRAY32:
                return DataTools.floatsToBytes((float[]) ip.getPixels(), true);
            default:
                return (byte[]) ip.getPixels();
        }
    }
}
