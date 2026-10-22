package com.columnaspine.weasis.spinopelvic;

import java.awt.geom.Point2D;
import java.util.List;

/**
 * Sagittal spinopelvic parameters, same definitions and sign conventions as the Horos plugin
 * (SPMath.c) and columna-spine.com/medidor. Input points are image coordinates (x right, y down).
 *
 * <p>Endplates arrive as unordered lines: the S1 posterior corner is the higher end (sacral slope
 * is positive on a standing film), which fixes the facing direction; every other endplate is then
 * oriented posterior to anterior from it.
 */
public final class Geometry {

  /** Landmarks; any of them may be null. */
  public static final class Input {
    public List<Point2D> hips = List.of(); // femoral head centers (1 or 2)
    public Point2D[] s1, l1, t12, tk; // endplates, two points each
    public Point2D t1, c7; // vertebral body centers
    public double mmPerPixel; // <= 0: uncalibrated
  }

  public static final class Result {
    public Double pi, pt, ss, ll, pill, tk, t1pa, sva;
    public boolean svaInMm;
  }

  private Geometry() {}

  private static final double DEG = 180 / Math.PI;

  private record V(double x, double y) {}

  public static Result compute(Input in) {
    Result r = new Result();
    if (!valid(in.s1)) {
      // Thoracic kyphosis does not need the pelvis.
      orientFrom(in.l1, in.t12, in.tk, in, r);
      return r;
    }
    Point2D post = in.s1[0].getY() <= in.s1[1].getY() ? in.s1[0] : in.s1[1];
    Point2D ant = post == in.s1[0] ? in.s1[1] : in.s1[0];
    double anterior = ant.getX() >= post.getX() ? 1 : -1;
    double s = in.mmPerPixel > 0 ? in.mmPerPixel : 1;

    V p = toPatient(post, anterior, s), a = toPatient(ant, anterior, s);
    V mid = new V((p.x + a.x) / 2, (p.y + a.y) / 2);
    double s1Slope = slope(p, a);
    r.ss = -s1Slope;

    V hip = null;
    if (in.hips != null && !in.hips.isEmpty()) {
      double hx = 0, hy = 0;
      for (Point2D h : in.hips) {
        V v = toPatient(h, anterior, s);
        hx += v.x;
        hy += v.y;
      }
      hip = new V(hx / in.hips.size(), hy / in.hips.size());
      r.pt = fromVertical(hip.x - mid.x, mid.y - hip.y);
      r.pi = r.pt + r.ss;
    }

    if (valid(in.l1)) {
      V[] l1 = oriented(in.l1, anterior, s);
      r.ll = wrap(slope(l1[0], l1[1]) - s1Slope);
    }
    if (r.pi != null && r.ll != null) {
      r.pill = r.pi - r.ll;
    }
    if (valid(in.t12) && valid(in.tk)) {
      V[] t12 = oriented(in.t12, anterior, s), tk = oriented(in.tk, anterior, s);
      r.tk = wrap(slope(t12[0], t12[1]) - slope(tk[0], tk[1]));
    }
    if (in.c7 != null) {
      r.sva = toPatient(in.c7, anterior, s).x - p.x;
      r.svaInMm = in.mmPerPixel > 0;
    }
    if (hip != null && in.t1 != null) {
      V t1 = toPatient(in.t1, anterior, s);
      r.t1pa =
          wrap(fromVertical(t1.x - hip.x, t1.y - hip.y) - fromVertical(mid.x - hip.x, mid.y - hip.y));
    }
    return r;
  }

  /** Without S1, take the facing direction from the femoral heads if possible (TK only). */
  private static void orientFrom(Point2D[] l1, Point2D[] t12, Point2D[] tk, Input in, Result r) {
    if (!valid(t12) || !valid(tk) || in.hips == null || in.hips.isEmpty()) {
      return;
    }
    double cx = (t12[0].getX() + t12[1].getX()) / 2;
    double anterior = in.hips.get(0).getX() >= cx ? 1 : -1;
    V[] a = oriented(t12, anterior, 1), b = oriented(tk, anterior, 1);
    r.tk = wrap(slope(a[0], a[1]) - slope(b[0], b[1]));
  }

  public static String schwabPill(Result r) {
    return r.pill == null ? null : grade(r.pill, 10, 20);
  }

  public static String schwabPt(Result r) {
    return r.pt == null ? null : grade(r.pt, 20, 30);
  }

  public static String schwabSva(Result r) {
    return r.sva == null || !r.svaInMm ? null : grade(r.sva, 40, 95);
  }

  /** Circle through three points, or null when they are collinear. */
  public static Point2D circleCenter(Point2D a, Point2D b, Point2D c) {
    double d =
        2 * (a.getX() * (b.getY() - c.getY()) + b.getX() * (c.getY() - a.getY()) + c.getX() * (a.getY() - b.getY()));
    if (Math.abs(d) < 1e-9) {
      return null;
    }
    double a2 = a.getX() * a.getX() + a.getY() * a.getY();
    double b2 = b.getX() * b.getX() + b.getY() * b.getY();
    double c2 = c.getX() * c.getX() + c.getY() * c.getY();
    double x = (a2 * (b.getY() - c.getY()) + b2 * (c.getY() - a.getY()) + c2 * (a.getY() - b.getY())) / d;
    double y = (a2 * (c.getX() - b.getX()) + b2 * (a.getX() - c.getX()) + c2 * (b.getX() - a.getX())) / d;
    return new Point2D.Double(x, y);
  }

  private static String grade(double v, double lo, double hi) {
    return v < lo ? "0" : v <= hi ? "+" : "++";
  }

  private static boolean valid(Point2D[] line) {
    return line != null && line.length == 2 && line[0] != null && line[1] != null && !line[0].equals(line[1]);
  }

  // Patient frame: X positive anterior, Y positive up.
  private static V toPatient(Point2D q, double anterior, double s) {
    return new V(anterior * q.getX() * s, -q.getY() * s);
  }

  private static V[] oriented(Point2D[] line, double anterior, double s) {
    V a = toPatient(line[0], anterior, s), b = toPatient(line[1], anterior, s);
    return a.x <= b.x ? new V[] {a, b} : new V[] {b, a};
  }

  // Inclination of an endplate from posterior to anterior; positive when the anterior corner is higher.
  private static double slope(V post, V ant) {
    return Math.atan2(ant.y - post.y, ant.x - post.x) * DEG;
  }

  // Angle from the upward vertical, positive towards anterior.
  private static double fromVertical(double dx, double dy) {
    return Math.atan2(dx, dy) * DEG;
  }

  private static double wrap(double a) {
    while (a > 180) a -= 360;
    while (a <= -180) a += 360;
    return a;
  }
}
