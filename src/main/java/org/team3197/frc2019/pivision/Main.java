package org.team3197.frc2019.pivision;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import edu.wpi.cscore.UsbCamera;
import edu.wpi.cscore.VideoSource;
import edu.wpi.cscore.VideoSource.ConnectionStrategy;
import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.vision.VisionThread;

/*
   JSON format:
   {
       "team": <team number>,
       "ntmode": <"client" or "server", "client" if unspecified>
       "cameras": [
           {
               "name": <camera name>
               "path": <path, e.g. "/dev/video0">
               "pixel format": <"MJPEG", "YUYV", etc>   // optional
               "width": <video mode width>              // optional
               "height": <video mode height>            // optional
               "fps": <video mode fps>                  // optional
               "brightness": <percentage brightness>    // optional
               "white balance": <"auto", "hold", value> // optional
               "exposure": <"auto", "hold", value>      // optional
               "properties": [                          // optional
                   {
                       "name": <property name>
                       "value": <property value>
                   }
               ],
               "stream": {                              // optional
                   "properties": [
                       {
                           "name": <stream property name>
                           "value": <stream property value>
                       }
                   ]
               }
           }
       ]
   }
 */

public final class Main {
  private static String configFile = "./frc.json";

  @SuppressWarnings("MemberName")
  public static class CameraConfig {
    public String name;
    public String host;
    public JsonObject config;
    public JsonElement streamConfig;
  }

  public static int team;
  public static boolean server;
  public static List<CameraConfig> cameraConfigs = new ArrayList<>();

  private Main() {
  }

  public static void parseError(String str) {
    System.err.println("config error in '" + configFile + "': " + str);
  }

  public static boolean readCameraConfig(JsonObject config) {
    CameraConfig cam = new CameraConfig();

    JsonElement nameElement = config.get("name");
    if (nameElement == null) {
      parseError("could not read camera name");
      return false;
    }
    cam.name = nameElement.getAsString();

    JsonElement hostElement = config.get("host");
    if (hostElement == null) {
      parseError("camera '" + cam.name + "': could not read host");
      return false;
    }
    cam.host = hostElement.getAsString();

    cam.streamConfig = config.get("stream");

    cam.config = config;

    cameraConfigs.add(cam);
    return true;
  }

  @SuppressWarnings("PMD.CyclomaticComplexity")
  public static boolean readConfig() {
    JsonElement top;
    try {
      top = new JsonParser().parse(Files.newBufferedReader(Paths.get(configFile)));
    } catch (IOException ex) {
      System.err.println("could not open '" + configFile + "': " + ex);
      return false;
    }

    if (!top.isJsonObject()) {
      parseError("must be JSON object");
      return false;
    }
    JsonObject obj = top.getAsJsonObject();

    JsonElement teamElement = obj.get("team");
    if (teamElement == null) {
      parseError("could not read team number");
      return false;
    }
    team = teamElement.getAsInt();

    if (obj.has("ntmode")) {
      String str = obj.get("ntmode").getAsString();
      if ("client".equalsIgnoreCase(str)) {
        server = false;
      } else if ("server".equalsIgnoreCase(str)) {
        server = true;
      } else {
        parseError("could not understand ntmode value '" + str + "'");
      }
    }

    JsonElement camerasElement = obj.get("cameras");
    if (camerasElement == null) {
      parseError("could not read cameras");
      return false;
    }
    JsonArray cameras = camerasElement.getAsJsonArray();
    for (JsonElement camera : cameras) {
      if (!readCameraConfig(camera.getAsJsonObject())) {
        return false;
      }
    }

    return true;
  }

  public static VideoSource startCamera(CameraConfig config) {
    System.out.println("Starting camera '" + config.name + "' on " + config.host);
    return CameraServer.getInstance().addAxisCamera(config.name, config.host);
  }

  static final int kCONTOURS_SIZE = 2;

  public static void main(String... args) {
    if (args.length > 0) {
      configFile = args[0];
    }

    if (!readConfig()) {
      return;
    }

    NetworkTableInstance ntinst = NetworkTableInstance.getDefault();
    if (server) {
      System.out.println("Setting up NetworkTables server");
      ntinst.startServer();
      System.out.println("NT: sever mode");
    } else {
      System.out.println("Setting up NetworkTables client for team " + team);
      ntinst.startClientTeam(team);
      System.out.println("NT: client mode");
    }

    UsbCamera usb0 = CameraServer.getInstance().startAutomaticCapture(0);
    // UsbCamera usb1 = CameraServer.getInstance().startAutomaticCapture(1);
    // VideoSink server = CameraServer.getInstance().getServer();
    usb0.setConnectionStrategy(ConnectionStrategy.kKeepOpen);
    // usb1.setConnectionStrategy(ConnectionStrategy.kKeepOpen);

    NetworkTable vision = ntinst.getTable("Vision");
    // vision.getEntry("test").clearPersistent();
    // vision.getEntry("test").setString("test");

    List<VideoSource> cameras = new ArrayList<>();
    for (CameraConfig cameraConfig : cameraConfigs) {
      cameras.add(startCamera(cameraConfig));
    }

    NetworkTableEntry contourXs = vision.getEntry("contour_xs");
    NetworkTableEntry contourAreas = vision.getEntry("contour_areas");

    if (cameras.size() >= 1) {
      VisionThread visionThread = new VisionThread(cameras.get(0), new GripPipeline(), pipeline -> {
        // ArrayList<MatOfPoint> contours = pipeline.filterContoursOutput();
        // if (!contours.isEmpty()) {
        // // System.out.println("Not empty (" + contours.size() + " contours)!");
        // Collections.sort(contours, Comparable<>);
        // for (int i = 0; i < contours.size(); i++) {
        // double area = Imgproc.contourArea(contours.get(i));
        // Rect bb = Imgproc.boundingRect(contours.get(i));
        // double x = bb.x + 0.5 * bb.width;
        // NetworkTable subtable = vision.getSubTable("contour." + i);

        // subtable.getEntry("area").setNumber(area);
        // subtable.getEntry("x").setNumber(x);

        // SmartDashboard.putNumber("contour." + i + ".area", area);
        // SmartDashboard.putNumber("contour." + i + ".x", x);
        // // System.out.println(i + " " + area + " " + x);
        // }
        // } else {
        // // System.out.println("Empty (no contours)!");
        // }
        ArrayList<Contour> contours = pipeline.getContours();
        if (contours.size() >= kCONTOURS_SIZE) {
          System.out.println("Not empty (" + contours.size() + " contour(s))!");
          Collections.sort(contours);
          Double[] areas = new Double[kCONTOURS_SIZE];
          Double[] xs = new Double[kCONTOURS_SIZE];
          for (int i = 0; i < kCONTOURS_SIZE; i++) {
            areas[i] = contours.get(i).getArea();
            xs[i] = contours.get(i).getX();
          }
          contourAreas.setNumberArray(areas);
          contourXs.setNumberArray(xs);
        } else {
          contourAreas.delete();
          contourXs.delete();
          System.out.println("Empty (no contours)!");
        }
      });
      visionThread.start();
    }
    for (;;) {
      try {
        Thread.sleep(10000);
      } catch (InterruptedException ex) {
        return;
      }
    }
  }
}
