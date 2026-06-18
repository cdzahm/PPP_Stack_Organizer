package provenzano_lab.utils;

import ij.IJ;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class LogUtils {

    private static final SimpleDateFormat FMT = new SimpleDateFormat("HH:mm:ss");

    public static void log(String msg) {
        IJ.log("[" + FMT.format(new Date()) + "] " + msg);
    }

    public static void batchProgress(int current, int total, String savedPath) {
        String index = String.format("%2d/%-2d", current, total);
        IJ.log("[ " + index + " ] Saved: " + savedPath);
    }

    public static void failureSummary(List<String> failures) {
        if (failures.isEmpty()) return;
        IJ.log("╔══════════════════════════════════════════════════════════╗");
        IJ.log("║              BATCH FAILURES (" + failures.size() + " file" +
               (failures.size() == 1 ? "" : "s") + ")                     ║");
        IJ.log("╠══════════════════════════════════════════════════════════╣");
        for (String entry : failures) {
            IJ.log("║  " + entry);
        }
        IJ.log("╚══════════════════════════════════════════════════════════╝");
    }
}
