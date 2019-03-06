package org.team3197.frc2019.pivision;

import org.opencv.core.Rect;

public class Contour implements Comparable<Contour> {
    private double area;
    private int x;

    public Contour(double area, Rect bb) {
        this.area = area;
        this.x = bb.x + bb.width / 2;
    }

    public double getArea() {
        return area;
    }

    public double getX() {
        return x;
    }

    public int compareTo(Contour other) {
        // return (other.area > area) ? -1 : 1;
        return Integer.compare(x, other.x);
    }

}