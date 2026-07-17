package provenzano_lab.utils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

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

    // Frame interval in seconds — the true per-position revisit interval, i.e. the wall-clock
    // Sequence/@time delta between consecutive visits to the SAME XY position, not between
    // consecutive <Sequence> (cycle) elements overall. For a multi-position acquisition,
    // PrairieView's <Sequence> elements cycle through positions round-robin (A, B, A, B, ...),
    // so a naive consecutive-cycle delta measures the position-switch time (~1/nXY of the real
    // interval), not the revisit interval — confirmed against real data (nXY=2 datasets showed
    // ~half the true 60s interval before this fix). Cycles are grouped by (index % nXY) and the
    // FIRST delta within each group is used (not an average across all deltas in the group):
    // acquisitions can pause between cycles (stage settling, user-triggered resume, etc.), which
    // would skew an average; the first interval within a position's group reflects the
    // acquisition's configured cycle period most reliably. Revisit if real data shows otherwise.
    public static double readFrameIntervalSec(String xmlPath, int nXY) throws Exception {
        if (nXY < 1) nXY = 1;
        Document doc = parse(xmlPath);
        NodeList sequences = doc.getElementsByTagName("Sequence");
        if (sequences.getLength() < 1 + nXY) {
            throw new Exception("Fewer than " + (1 + nXY) + " <Sequence> elements in " + xmlPath +
                    " — cannot compute per-position frame interval for nXY=" + nXY + ".");
        }

        double[] times = new double[sequences.getLength()];
        for (int i = 0; i < sequences.getLength(); i++) {
            times[i] = parseTimeToSeconds(((Element) sequences.item(i)).getAttribute("time"));
        }

        // First delta within each (index % nXY) group — i.e. cycle i vs. cycle i+nXY.
        List<Double> groupFirstDeltas = new ArrayList<>();
        boolean inconsistent = false;
        for (int group = 0; group < nXY; group++) {
            if (group + nXY >= times.length) continue;
            double delta = times[group + nXY] - times[group];
            if (delta < 0) delta += 24 * 3600; // guard against a midnight rollover
            groupFirstDeltas.add(delta);
        }
        if (groupFirstDeltas.isEmpty()) {
            throw new Exception("Could not compute a same-position Sequence/@time delta in " + xmlPath);
        }
        double result = groupFirstDeltas.get(0);
        for (double d : groupFirstDeltas) {
            if (Math.abs(d - result) > 0.5) inconsistent = true;
        }
        if (inconsistent) {
            LogUtils.log("WARNING: Per-position frame interval groups disagree in " + xmlPath +
                    " (values: " + groupFirstDeltas + ") — using first group's value: " + result + "s. " +
                    "This may indicate irregular acquisition timing.");
        }
        return result;
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
