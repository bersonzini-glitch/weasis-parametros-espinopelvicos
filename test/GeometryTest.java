import com.columnaspine.weasis.spinopelvic.Geometry;
import java.awt.geom.Point2D;
import java.util.List;

/** Same synthetic scene as the Horos and web tests, facing both ways, lines drawn in either order. */
public class GeometryTest {
  static int failures;

  static void check(String name, Double got, double want) {
    boolean ok = got != null && Math.abs(got - want) < 0.01;
    if (!ok) failures++;
    System.out.printf("  %-6s %8s  (esperado %8.2f) %s%n", name, got == null ? "—" : String.format("%.2f", got), want, ok ? "ok" : "FALLA");
  }

  public static void main(String[] args) {
    double pt = Math.toDegrees(Math.atan2(20, 100));
    for (int facing : new int[] {1, -1}) {
      for (boolean reversed : new boolean[] {false, true}) {
        double sx = 0.5;
        java.util.function.BiFunction<Double, Double, Point2D> img =
            (X, Y) -> new Point2D.Double(500 + X / (facing * sx), 2000 - Y / sx);
        java.util.function.Function<double[], Point2D[]> plate = d -> {
          double t = Math.toRadians(d[2]), h = 20;
          Point2D p = img.apply(d[0] - h * Math.cos(t), d[1] - h * Math.sin(t));
          Point2D a = img.apply(d[0] + h * Math.cos(t), d[1] + h * Math.sin(t));
          return reversed ? new Point2D[] {a, p} : new Point2D[] {p, a};
        };
        Geometry.Input in = new Geometry.Input();
        in.mmPerPixel = sx;
        in.hips = List.of(img.apply(2.0, 1.0), img.apply(-2.0, -1.0));
        in.s1 = plate.apply(new double[] {-20, 100, -40});
        in.l1 = plate.apply(new double[] {-30, 300, 15});
        in.t12 = plate.apply(new double[] {-20, 380, 10});
        in.tk = plate.apply(new double[] {0, 520, -30});
        in.t1 = img.apply(30.0, 600.0);
        in.c7 = img.apply(0.0, 620.0);
        Geometry.Result r = Geometry.compute(in);
        System.out.printf("Mirando a la %s, líneas %s:%n", facing > 0 ? "derecha" : "izquierda", reversed ? "al revés" : "post→ant");
        check("PI", r.pi, pt + 40);
        check("PT", r.pt, pt);
        check("SS", r.ss, 40);
        check("LL", r.ll, 55);
        check("PI-LL", r.pill, pt + 40 - 55);
        check("TK", r.tk, 40);
        check("SVA", r.svaInMm ? r.sva : null, 20 + 20 * Math.cos(Math.toRadians(40)));
        check("T1PA", r.t1pa, Math.toDegrees(Math.atan2(30, 600)) + pt);
      }
    }
    Point2D c = Geometry.circleCenter(new Point2D.Double(10, 0), new Point2D.Double(0, 10), new Point2D.Double(-10, 0));
    check("círculo", c.getX() + c.getY(), 0);
    System.out.println(failures == 0 ? "\nTodo ok" : "\n" + failures + " FALLAS");
    System.exit(failures == 0 ? 0 : 1);
  }
}
