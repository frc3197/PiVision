import org.opencv.core.Rect;

public class Contour implements Comparable<Contour> {
    private double area;
    private double x;

    public Contour(double area, Rect bb) {
        this.area = area;
        this.x = bb.x + 0.5 * bb.width;
    }

    public double getArea() {
        return area;
    }

    public double getX() {
        return x;
    }

    public int compareTo(Contour other) {
        return (other.area > area) ? -1 : 1;
    }

}