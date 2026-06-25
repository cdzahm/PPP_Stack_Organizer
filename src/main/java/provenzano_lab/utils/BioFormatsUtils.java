package provenzano_lab.utils;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import loci.common.DataTools;
import loci.common.services.ServiceFactory;
import loci.formats.ImageReader;
import loci.formats.meta.IMetadata;
import loci.formats.out.OMETiffWriter;
import loci.formats.out.TiffWriter;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

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

    // Reads full OME metadata from a .companion.ome without loading pixel data.
    // Returns an IMetadata with one Image element per series in the file.
    public static IMetadata readSourceMetadata(String companionOmePath) throws Exception {
        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        IMetadata meta = service.createOMEXMLMetadata();
        ImageReader reader = new ImageReader();
        reader.setMetadataStore(meta);
        reader.setId(companionOmePath);
        reader.close();
        return meta;
    }

    // Saves a single-channel hyperstack as OME-TIFF, copying all source metadata wholesale
    // and overwriting only the fields specific to this output file (dimensions, IDs,
    // calibration, the single relevant channel, and the TiffData block).
    //
    // imp:              single-channel hyperstack (nC=1) to write
    // sourceMeta:       OME metadata from readSourceMetadata() — series 0 used as template
    // imageId:          unique Image ID for this output (e.g. "Image:XY01_tcells")
    // pixelsId:         unique Pixels ID for this output (e.g. "Pixels:XY01_tcells")
    // nZ, nT:           Z planes and timepoints in the output (per-position values)
    // physicalSizeX/Y:  pixel dimensions in microns
    // physicalSizeZ:    voxel depth in microns (forced to microns regardless of source unit)
    // timeIncrementSec: frame interval in seconds (replaces source value, which is always 0)
    // srcChannelIdx:    which channel index in sourceMeta image 0 to copy Channel block from
    // channelRole:      role label written to Channel:Name and the output filename
    // outputPath:       full path to the .ome.tif output file
    public static void saveChannelAsOMETIFF(
            ImagePlus imp,
            IMetadata sourceMeta,
            String imageId,
            String pixelsId,
            int nZ,
            int nT,
            double physicalSizeX,
            double physicalSizeY,
            double physicalSizeZ,
            double timeIncrementSec,
            int srcChannelIdx,
            String channelRole,
            String outputPath) throws Exception {

        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);

        IMetadata meta = cloneAndAdaptMetadata(
                sourceMeta, service,
                imageId, pixelsId,
                imp.getWidth(), imp.getHeight(), nZ, nT,
                physicalSizeX, physicalSizeY, physicalSizeZ,
                timeIncrementSec,
                srcChannelIdx, channelRole);

        OMETiffWriter writer = new OMETiffWriter();
        writer.setMetadataRetrieve(meta);
        writer.setCompression(TiffWriter.COMPRESSION_UNCOMPRESSED);
        writer.setWriteSequentially(true);
        writer.setId(outputPath);

        ImageStack stack = imp.getStack();
        int ijType = imp.getType();
        int plane = 0;
        // DimensionOrder XYCZT: planes ordered C-fastest, then Z, then T.
        // With SizeC=1 this collapses to Z-inner, T-outer.
        for (int t = 0; t < nT; t++) {
            for (int z = 0; z < nZ; z++) {
                ImageProcessor ip = stack.getProcessor(imp.getStackIndex(1, z + 1, t + 1));
                writer.saveBytes(plane++, processorToBytes(ip, ijType));
            }
        }

        writer.close();
    }

    // Writes headlessly via OMETiffWriter with no compression — avoids the interactive
    // dialog and the LZW overhead that makes the default exporter take 20+ min.
    public static void saveAsOMETIFF(ImagePlus imp, String outputPath) throws Exception {
        int nC     = imp.getNChannels();
        int nZ     = imp.getNSlices();
        int nT     = imp.getNFrames();
        int ijType = imp.getType();

        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service  = factory.getInstance(OMEXMLService.class);
        IMetadata meta         = service.createOMEXMLMetadata();

        meta.createRoot();
        meta.setImageID("Image:0", 0);
        meta.setPixelsID("Pixels:0", 0);
        meta.setPixelsBigEndian(Boolean.FALSE, 0);
        meta.setPixelsDimensionOrder(DimensionOrder.XYCZT, 0);
        meta.setPixelsType(ijTypeToOME(ijType), 0);
        meta.setPixelsSizeX(new PositiveInteger(imp.getWidth()), 0);
        meta.setPixelsSizeY(new PositiveInteger(imp.getHeight()), 0);
        meta.setPixelsSizeZ(new PositiveInteger(nZ), 0);
        meta.setPixelsSizeC(new PositiveInteger(nC), 0);
        meta.setPixelsSizeT(new PositiveInteger(nT), 0);

        Calibration cal = imp.getCalibration();
        if (cal.pixelWidth   > 0) meta.setPixelsPhysicalSizeX(new Length(cal.pixelWidth,   UNITS.MICROMETER), 0);
        if (cal.pixelHeight  > 0) meta.setPixelsPhysicalSizeY(new Length(cal.pixelHeight,  UNITS.MICROMETER), 0);
        if (cal.pixelDepth   > 0) meta.setPixelsPhysicalSizeZ(new Length(cal.pixelDepth,   UNITS.MICROMETER), 0);
        if (cal.frameInterval > 0) meta.setPixelsTimeIncrement(new Time(cal.frameInterval, UNITS.SECOND), 0);

        for (int c = 0; c < nC; c++) {
            meta.setChannelID("Channel:0:" + c, 0, c);
            meta.setChannelSamplesPerPixel(new PositiveInteger(1), 0, c);
        }

        OMETiffWriter writer = new OMETiffWriter();
        writer.setMetadataRetrieve(meta);
        writer.setCompression(TiffWriter.COMPRESSION_UNCOMPRESSED);
        writer.setWriteSequentially(true);
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

    // Clones sourceMeta image 0 as the template, strips structural elements that are
    // output-file-specific (extra Images, TiffData, Plane entries, and extra Channels),
    // then applies the per-output values via the IMetadata setter API.
    private static IMetadata cloneAndAdaptMetadata(
            IMetadata sourceMeta,
            OMEXMLService service,
            String imageId,
            String pixelsId,
            int width,
            int height,
            int nZ,
            int nT,
            double physicalSizeX,
            double physicalSizeY,
            double physicalSizeZ,
            double timeIncrementSec,
            int srcChannelIdx,
            String channelRole) throws Exception {

        // Clone the full metadata via its XML representation
        String xml = service.getOMEXML(sourceMeta);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        Element root = doc.getDocumentElement();

        // Keep only the first Image element (series 0); strip the rest.
        // All series share the same channel/instrument metadata — series 0 is the template.
        NodeList imageNodes = root.getElementsByTagNameNS("*", "Image");
        List<Node> toRemove = new ArrayList<>();
        for (int i = 1; i < imageNodes.getLength(); i++) toRemove.add(imageNodes.item(i));
        for (Node n : toRemove) n.getParentNode().removeChild(n);

        // Within the remaining Pixels element, strip per-file structural blocks.
        // TiffData references the source companion file set — must be removed so the
        // OMETiffWriter can generate correct TiffData for the single output file.
        NodeList pixelsList = root.getElementsByTagNameNS("*", "Pixels");
        if (pixelsList.getLength() > 0) {
            Element pixelsEl = (Element) pixelsList.item(0);
            stripChildrenByLocalName(pixelsEl, "TiffData");
            stripChildrenByLocalName(pixelsEl, "Plane");

            // Keep only srcChannelIdx Channel; strip all others.
            NodeList channelNodes = pixelsEl.getElementsByTagNameNS("*", "Channel");
            List<Node> channelsToRemove = new ArrayList<>();
            for (int i = 0; i < channelNodes.getLength(); i++) {
                if (i != srcChannelIdx) channelsToRemove.add(channelNodes.item(i));
            }
            for (Node n : channelsToRemove) n.getParentNode().removeChild(n);
        }

        // Serialize the modified XML and parse into a fresh IMetadata
        Transformer tf = TransformerFactory.newInstance().newTransformer();
        StringWriter sw = new StringWriter();
        tf.transform(new DOMSource(doc), new StreamResult(sw));
        IMetadata meta = service.createOMEXMLMetadata(sw.toString());

        // Overwrite the fields that must change for this specific output file
        meta.setImageID(imageId, 0);
        meta.setPixelsID(pixelsId, 0);
        meta.setPixelsBigEndian(Boolean.FALSE, 0);
        meta.setPixelsDimensionOrder(DimensionOrder.XYCZT, 0);
        meta.setPixelsSizeX(new PositiveInteger(width), 0);
        meta.setPixelsSizeY(new PositiveInteger(height), 0);
        meta.setPixelsSizeZ(new PositiveInteger(nZ), 0);
        meta.setPixelsSizeT(new PositiveInteger(nT), 0);
        meta.setPixelsSizeC(new PositiveInteger(1), 0);
        meta.setPixelsPhysicalSizeX(new Length(physicalSizeX, UNITS.MICROMETER), 0);
        meta.setPixelsPhysicalSizeY(new Length(physicalSizeY, UNITS.MICROMETER), 0);
        // Force Z unit to microns — Bruker .companion.ome omits the unit for voxel depth
        meta.setPixelsPhysicalSizeZ(new Length(physicalSizeZ, UNITS.MICROMETER), 0);
        // Source TimeIncrement is always 0 sec in Bruker files; replace with user value
        meta.setPixelsTimeIncrement(new Time(timeIncrementSec, UNITS.SECOND), 0);

        // Update the single remaining Channel block
        meta.setChannelID("Channel:0:0", 0, 0);
        meta.setChannelName(channelRole, 0, 0);
        meta.setChannelSamplesPerPixel(new PositiveInteger(1), 0, 0);

        return meta;
    }

    private static void stripChildrenByLocalName(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        List<Node> toRemove = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) toRemove.add(nodes.item(i));
        for (Node n : toRemove) n.getParentNode().removeChild(n);
    }

    private static PixelType ijTypeToOME(int ijType) {
        switch (ijType) {
            case ImagePlus.GRAY16: return PixelType.UINT16;
            case ImagePlus.GRAY32: return PixelType.FLOAT;
            default:               return PixelType.UINT8;
        }
    }

    // DataTools.shortsToBytes/floatsToBytes pack as little-endian, matching setPixelsBigEndian(FALSE).
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
