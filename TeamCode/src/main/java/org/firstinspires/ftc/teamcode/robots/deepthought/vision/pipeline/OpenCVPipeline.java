package org.firstinspires.ftc.teamcode.robots.deepthought.vision.pipeline;

import android.graphics.Bitmap;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.dashboard.config.Config;
import com.acmerobotics.dashboard.telemetry.TelemetryPacket;

import org.firstinspires.ftc.teamcode.robots.deepthought.vision.Position;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.openftc.easyopencv.OpenCvPipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mahesh Natamai
 */

@Config(value = "NEW OPENCV")
public class OpenCVPipeline extends OpenCvPipeline {
    static FtcDashboard dashboard = FtcDashboard.getInstance();

    double cX = 0;
    double cY = 0;
    double width = 0;

    private static final int CAMERA_WIDTH = 640;
    private static final int CAMERA_HEIGHT = 360;
    private static final double FOV = 6; //CHANGE??

    public static final double objectWidthirl = 3.5;
    public static final double focalLength = 362.0*8.02/objectWidthirl;

    private Mat cropOutput = new Mat();
    private Mat normalizeInput = new Mat();
    private Mat normalizeOutput = new Mat();
    private Mat blurInput = new Mat();
    private Mat blurOutput = new Mat();
    private Mat hsvThresholdInput = new Mat();
    private Mat hsvThresholdOutput = new Mat();
    private List<MatOfPoint> findContoursOutput = new ArrayList<>();
    private Mat findContoursInput = new Mat();
    private Mat findContoursOutputMat = new Mat();
    private Mat finalContourOutputMat = new Mat();
    private Mat dashboardMat = new Mat();
    private Mat hierarchy = new Mat();
    private volatile Bitmap dashboardBitmap;

    private volatile int largestX, largestY;
    private double largestArea;
    private volatile Position lastPosition;
    
    // Constants
    public static int VIEW_OPEN_CV_PIPELINE_STAGE = 6;
    public static int TOP_LEFT_X = 0, TOP_LEFT_Y = 80;
    public static int BOTTOM_RIGHT_X = 640, BOTTOM_RIGHT_Y = 480;
    public static double NORMALIZE_ALPHA = 51.0, NORMALIZE_BETA = 261.0;
    public static double BLUR_RADIUS = 7;
    public static double HUE_MIN = 30, HUE_MAX = 90;
    public static double SATURATION_MIN = 20, SATURATION_MAX = 255;
    public static double VALUE_MIN = 120, VALUE_MAX = 255;
    public static double MIN_CONTOUR_AREA = 700;
    public static String BLUR = "Box Blur";

    public static int LEFT_THRESHOLD = 142;
    public static int RIGHT_THRESHOLD = 211;

    public OpenCVPipeline() {
        largestX = -1;
        largestY = -1;
        largestArea = -1;
        lastPosition = Position.HOLD;
    }

    @Override
    public Mat processFrame(Mat input) {
            // Step crop (stage 1):
            cropOutput = input.submat(new Rect(new Point(TOP_LEFT_X, TOP_LEFT_Y), new Point(BOTTOM_RIGHT_X, BOTTOM_RIGHT_Y)));

            // Step Normalize0 (stage 2):
            normalizeInput = cropOutput;
            int normalizeType = Core.NORM_MINMAX;
            double normalizeAlpha = NORMALIZE_ALPHA;
            double normalizeBeta = NORMALIZE_BETA;
            normalize(normalizeInput, normalizeType, normalizeAlpha, normalizeBeta, normalizeOutput);

            // Step Blur0 (stage 3):
            blurInput = normalizeOutput;
            BlurType blurType = BlurType.get(BLUR);
            double blurRadius = BLUR_RADIUS;
            blur(blurInput, blurType, blurRadius, blurOutput);

            // Step HSV_Threshold0  (stage 4):
            hsvThresholdInput = blurOutput;
            double[] hsvThresholdHue = {HUE_MIN, HUE_MAX};
            double[] hsvThresholdSaturation = {SATURATION_MIN, SATURATION_MAX};
            double[] hsvThresholdValue = {VALUE_MIN, VALUE_MAX};
            hsvThreshold(hsvThresholdInput, hsvThresholdHue, hsvThresholdSaturation, hsvThresholdValue, hsvThresholdOutput);

            // Step Find_Contours0 (stage 5):
            findContoursInput = hsvThresholdOutput;
            findContours(findContoursInput, findContoursOutput);
            findContoursOutputMat = cropOutput;
            for (int i = 0; i < findContoursOutput.size(); i++) {
                Imgproc.drawContours(findContoursOutputMat, findContoursOutput, i, new Scalar(255, 255, 255), 2);
            }

            // Finding largest contour (stage 6):
            finalContourOutputMat = cropOutput;
            largestArea = -1;
            largestX = -1;
            largestY = -1;
            int largestContourIndex = -1;
            for (int i = 0; i < findContoursOutput.size(); i++) {
                MatOfPoint contour = findContoursOutput.get(i);
                double contourArea = Imgproc.contourArea(contour);
                if (contourArea > MIN_CONTOUR_AREA && contourArea > largestArea) {
                    Moments p = Imgproc.moments(contour, false);
                    int x = (int) (p.get_m10() / p.get_m00());
                    int y = (int) (p.get_m01() / p.get_m00());

                    largestContourIndex = i;
                    largestX = x;
                    largestY = y;
                    largestArea = contourArea;
                }
            }
            //LOOK HERE
            if (largestContourIndex != -1) {
                Imgproc.drawContours(finalContourOutputMat, findContoursOutput, largestContourIndex, new Scalar(255, 255, 255), 2);
                Imgproc.drawMarker(finalContourOutputMat, new Point(largestX, largestY), new Scalar(0, 255, 0));
                width = calculateWidth(findContoursOutput.get(largestContourIndex));
                String widthLable = "Width: " + (int)width + " pixles";
                Imgproc.putText(input, widthLable, new Point(cX + 10, cY + 20), Imgproc.FONT_HERSHEY_SIMPLEX, .5, new Scalar(0, 255, 0), 2);
                String distanceLable = "Distance: " + String.format("%.2f",getDistance(width)) + " inches";
                Imgproc.putText(input, distanceLable, new Point(cX + 10, cY + 60), Imgproc.FONT_HERSHEY_SIMPLEX, .5, new Scalar(0, 255, 0), 2);
                Moments moments = Imgproc.moments(findContoursOutput.get(largestContourIndex));
                cX = moments.get_m10() / moments.get_m00();
                cY = moments.get_m01() / moments.get_m00();

                String lable = "(" + (int)cX + ", "+ (int)cY + ")";
                Imgproc.putText(input, lable, new Point(cX + 10, cY), Imgproc.FONT_HERSHEY_SIMPLEX, .5, new Scalar(0, 255, 0), 2);
                Imgproc.circle(input, new Point(cX, cY), 5,  new Scalar(0, 255, 0), -1);
                TelemetryPacket p=new TelemetryPacket();
                p.addLine("FOC??? "+(int)cX+" "+(int)cY);
                dashboard.sendTelemetryPacket(p);

            }


            Imgproc.line(finalContourOutputMat, new Point(RIGHT_THRESHOLD, 0), new Point(RIGHT_THRESHOLD, finalContourOutputMat.height()), new Scalar(255, 255, 255), 2);
            Imgproc.line(finalContourOutputMat, new Point(LEFT_THRESHOLD, 0), new Point(LEFT_THRESHOLD, finalContourOutputMat.height()), new Scalar(255, 255, 255), 2);

            if (largestX > 0 && largestX < LEFT_THRESHOLD) {
                lastPosition = Position.LEFT;
            } else if (largestX > LEFT_THRESHOLD && largestX < RIGHT_THRESHOLD) {
                lastPosition = Position.MIDDLE;
            } else if (largestX > RIGHT_THRESHOLD && largestX < cropOutput.width()) {
                lastPosition = Position.RIGHT;
            } else
                lastPosition = Position.NONE_FOUND;

            switch (VIEW_OPEN_CV_PIPELINE_STAGE) {
                case 0:
                    dashboardMat = cropOutput;
                    break;
                case 1:
                    dashboardMat = normalizeOutput;
                    break;
                case 2:
                    dashboardMat = blurInput;
                    break;
                case 3:
                    dashboardMat = blurOutput;
                    break;
                case 4:
                    dashboardMat = hsvThresholdOutput;
                    break;
                case 5:
                    dashboardMat = findContoursOutputMat;
                    break;
                case 6:
                    dashboardMat = finalContourOutputMat;
                    break;
                default:
                    dashboardMat = input;
                    break;
            }
            if (dashboardMat != null && !dashboardMat.empty()) {
                dashboardBitmap = Bitmap.createBitmap(dashboardMat.width(), dashboardMat.height(), Bitmap.Config.RGB_565);
                Utils.matToBitmap(dashboardMat, dashboardBitmap);
            }


            return input;
    }

    private double calculateWidth(MatOfPoint contour){
        Rect boundingRect = Imgproc.boundingRect(contour);
        return boundingRect.width;
    }



    public int[] getPosition() {
        return new int[] {largestX, largestY};
    }

    public Bitmap getDashboardImage() {
        return dashboardBitmap;
    }

    /**
     * Normalizes or remaps the values of pixels in an image.
     * @param input The image on which to perform the Normalize.
     * @param type The type of normalization.
     * @param a The minimum value.
     * @param b The maximum value.
     * @param output The image in which to store the output.
     */
    private void normalize(Mat input, int type, double a, double b, Mat output) {
        Core.normalize(input, output, a, b, type);
    }

    enum BlurType{
        BOX("Box Blur"), GAUSSIAN("Gaussian Blur"), MEDIAN("Median Filter"),
        BILATERAL("Bilateral Filter");

        private final String label;

        BlurType(String label) {
            this.label = label;
        }

        public static BlurType get(String type) {
            if (BILATERAL.label.equals(type)) {
                return BILATERAL;
            }
            else if (GAUSSIAN.label.equals(type)) {
                return GAUSSIAN;
            }
            else if (MEDIAN.label.equals(type)) {
                return MEDIAN;
            }
            else {
                return BOX;
            }
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    private void blur(Mat input, BlurType type, double doubleRadius,
                      Mat output) {
        int radius = (int)(doubleRadius + 0.5);
        int kernelSize;
        switch(type){
            case BOX:
                kernelSize = 2 * radius + 1;
                Imgproc.blur(input, output, new Size(kernelSize, kernelSize));
                break;
            case GAUSSIAN:
                kernelSize = 6 * radius + 1;
                Imgproc.GaussianBlur(input,output, new Size(kernelSize, kernelSize), radius);
                break;
            case MEDIAN:
                kernelSize = 2 * radius + 1;
                Imgproc.medianBlur(input, output, kernelSize);
                break;
            case BILATERAL:
                Imgproc.bilateralFilter(input, output, -1, radius, radius);
                break;
        }
    }

    //GO TO 7:13 ON YOUTUBE VIDEO IDK WHAT TO DO NEXT
    public static double getDistance(double width){
        double distance = (objectWidthirl * focalLength) / width;
        return distance;
    }

    private static double getAngleTarget(double objMidpoint){
        double midpoint = -((objMidpoint - (CAMERA_WIDTH/2))*FOV)/CAMERA_WIDTH;
        return midpoint;
    }

    private void hsvThreshold(Mat input, double[] hue, double[] sat, double[] val,
                              Mat out) {
        Imgproc.cvtColor(input, out, Imgproc.COLOR_BGR2HSV);
        Core.inRange(out, new Scalar(hue[0], sat[0], val[0]),
                new Scalar(hue[1], sat[1], val[1]), out);
    }


    private void findContours(Mat input, List<MatOfPoint> contours) {
        contours.clear();
        int mode = Imgproc.RETR_LIST;
        int method = Imgproc.CHAIN_APPROX_SIMPLE;
        Imgproc.findContours(input, contours, hierarchy, mode, method);
    }

    public Position getLastPosition() {
        return lastPosition;
    }

    public double[] getLargestCoordinate() { return new double[] {largestX, largestY}; }
}
