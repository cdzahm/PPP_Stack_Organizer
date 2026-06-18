package provenzano_lab.utils;

import ij.ImagePlus;
import ij.measure.Calibration;

public class CalibrationUtils {

    public static void applyCalibration(ImagePlus imp, double pixelWidth, double pixelHeight,
                                        double voxelDepth, double frameIntervalSec) {
        Calibration cal = imp.getCalibration();
        cal.pixelWidth = pixelWidth;
        cal.pixelHeight = pixelHeight;
        cal.pixelDepth = voxelDepth;
        cal.setUnit("micron");
        cal.frameInterval = frameIntervalSec;
        cal.setTimeUnit("sec");
        imp.setCalibration(cal);
    }

    public static double[] readCalibration(ImagePlus imp) {
        Calibration cal = imp.getCalibration();
        return new double[]{cal.pixelWidth, cal.pixelHeight, cal.pixelDepth};
    }
}
