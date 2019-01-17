/*----------------------------------------------------------------------------*/
/* Copyright (c) 2017-2018 FIRST. All Rights Reserved.                        */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

package frc.robot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import edu.wpi.cscore.AxisCamera;
import edu.wpi.cscore.CvSink;
import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

/**
 * This is a demo program showing the use of OpenCV to do vision processing. The
 * image is acquired from the Axis camera, then a rectangle is put on the image
 * and sent to the dashboard. OpenCV has many methods for different types of
 * processing.
 */
public class Robot extends TimedRobot {
  Thread m_visionThread;

  GripPipeline gripPipeline = new GripPipeline();

  @Override
  public void robotInit() {
    m_visionThread = new Thread(() -> {
      // Get the Axis camera from CameraServer
      AxisCamera camera = CameraServer.getInstance().addAxisCamera("10.31.97.30");
      // Set the resolution
      camera.setResolution(640, 480);

      // Get a CvSink. This will capture Mats from the camera
      CvSink cvSink = CameraServer.getInstance().getVideo();
      // Setup a CvSource. This will send images back to the Dashboard
      /*
       * CvSource outputStream = CameraServer.getInstance().putVideo("Rectangle", 640,
       * 480);
       */

      // Mats are very memory expensive. Lets reuse this Mat.
      Mat mat = new Mat();

      // This cannot be 'true'. The program will never exit if it is. This
      // lets the robot stop this thread when restarting robot code or
      // deploying.
      while (!Thread.interrupted()) {
        // Tell the CvSink to grab a frame from the camera and put it
        // in the source mat. If there is an error notify the output.
        if (cvSink.grabFrame(mat) == 0) {
          System.out.println("Error with cvSink!");
          // Send the output the error.
          // outputStream.notifyError(cvSink.getError());
          // skip the rest of the current iteration
          continue;
        }
        // Put a rectangle on the image
        // Imgproc.rectangle(mat, new Point(100, 100), new Point(400, 400), new
        // Scalar(255, 255, 255), 5);
        // Give the output stream a new image to display
        // outputStream.putFrame(mat);
        List<MatOfPoint> contours;
        {
          ArrayList<MatOfPoint> filterContoursOutput = gripPipeline.process(mat);
          if (filterContoursOutput.size() < 2) {
            System.out.println("Not enough contours found");
            continue;
          }
          Collections.sort(filterContoursOutput, new java.util.Comparator<MatOfPoint>() {
            public int compare(MatOfPoint matA, MatOfPoint matB) {
              return Imgproc.contourArea(matA) > Imgproc.contourArea(matB) ? 1 : -1;
            }
          });
          contours = filterContoursOutput.subList(0, 2);
        }

        for (int i = 0; i < 2; i++) {
          MatOfPoint contour = contours.get(i);
          double area = Imgproc.contourArea(contour);
          Rect bb = Imgproc.boundingRect(contour);
          double ratio = bb.width / (double) bb.height;
          double x = bb.x + bb.width / 2.0;
          SmartDashboard.putNumber("area" + i, area);
          SmartDashboard.putNumber("ratio" + i, ratio);
          SmartDashboard.putNumber("x" + i, x);
        }
      }
    });
    m_visionThread.setDaemon(true);
    m_visionThread.start();
  }
}