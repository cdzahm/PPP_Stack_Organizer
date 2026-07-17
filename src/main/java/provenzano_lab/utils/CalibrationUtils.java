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
        // setUnit() only sets Calibration's shared 'unit' field, which getYUnit()/getZUnit()
        // fall back to when yunit/zunit are null (in-memory). But FileSaver only writes explicit
        // yunit=/zunit= tags into the TIFF description when they differ from setXUnit()'s value,
        // so a reader that doesn't replicate ImageJ's null-fallback convention (rather than
        // assuming the shared 'unit' applies to Y/Z too) can come back with no unit for pixel
        // height/voxel depth. Setting all three explicitly guarantees they're always written.
        cal.setXUnit("micron");
        cal.setYUnit("micron");
        cal.setZUnit("micron");
        cal.frameInterval = frameIntervalSec;
        cal.setTimeUnit("sec");
        imp.setCalibration(cal);
    }

    public static double[] readCalibration(ImagePlus imp) {
        Calibration cal = imp.getCalibration();
        return new double[]{cal.pixelWidth, cal.pixelHeight, cal.pixelDepth};
    }
}
