package provenzano_lab.utils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

// Parses the two PrairieView PVScan XML fields that Bio-Formats' PrairieReader does not
// populate into IMetadata for Prairie-only acquisitions (no .companion.ome): Z voxel depth
// and frame interval (confirmed via direct testing — getPixelsPhysicalSizeZ/TimeIncrement
// both return null from PrairieReader's metadata store). XY pixel size and channel names
// come from IMetadata directly for this path and don't need this class.
public class PrairieXmlUtils {

    // PVStateValue key="micronsPerPixel" / IndexedValue index="ZAxis" from the acquisition-wide
    // PVStateShard (the first one in document order, before any <Sequence>). Prairie's XML is
    // unambiguously in microns, so no unit conversion or forcing is needed on this path.
    public static double readVoxelDepthUm(String xmlPath) throws Exception {
        Document doc = parse(xmlPath);
        NodeList stateValues = doc.getElementsByTagName("PVStateValue");
        for (int i = 0; i < stateValues.getLength(); i++) {
            Element el = (Element) stateValues.item(i);
            if (!"micronsPerPixel".equals(el.getAttribute("key"))) continue;
            NodeList indexed = el.getElementsByTagName("IndexedValue");
            for (int j = 0; j < indexed.getLength(); j++) {
                Element iv = (Element) indexed.item(j);
                if ("ZAxis".equals(iv.getAttribute("index"))) {
                    return Double.parseDouble(iv.getAttribute("value"));
                }
            }
        }
        throw new Exception("PVStateValue key=\"micronsPerPixel\" ZAxis not found in " + xmlPath);
    }

    // Frame interval in seconds, from the wall-clock Sequence/@time delta between the first
    // two <Sequence> (cycle) elements. Uses the FIRST delta rather than an average across all
    // cycles by design: acquisitions can pause between cycles (stage settling, user-triggered
    // resume, etc.), which would skew an average; the first interval reflects the acquisition's
    // configured cycle period most reliably. Revisit if real data shows first-delta is atypical.
    public static double readFrameIntervalSec(String xmlPath) throws Exception {
        Document doc = parse(xmlPath);
        NodeList sequences = doc.getElementsByTagName("Sequence");
        if (sequences.getLength() < 2) {
            throw new Exception("Fewer than 2 <Sequence> elements in " + xmlPath + " — cannot compute frame interval.");
        }
        double t0 = parseTimeToSeconds(((Element) sequences.item(0)).getAttribute("time"));
        double t1 = parseTimeToSeconds(((Element) sequences.item(1)).getAttribute("time"));
        double delta = t1 - t0;
        if (delta < 0) delta += 24 * 3600; // guard against a midnight rollover between cycles
        return delta;
    }

    // Format observed in PVScan XML: "16:29:15.3001025" (HH:mm:ss.ffffff, no date).
    private static double parseTimeToSeconds(String hhmmss) {
        String[] parts = hhmmss.split(":");
        double h = Double.parseDouble(parts[0]);
        double m = Double.parseDouble(parts[1]);
        double s = Double.parseDouble(parts[2]);
        return h * 3600 + m * 60 + s;
    }

    private static Document parse(String xmlPath) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(new File(xmlPath));
    }
}
