package com.columnaspine.weasis.spinopelvic;

import bibliothek.gui.dock.common.CLocation;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.area.ThreePointsCircleGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.imp.DefaultLayer;
import org.weasis.dicom.viewer2d.EventManager;

/**
 * Side panel of the DICOM 2D viewer. Each landmark is drawn with a native Weasis measurement
 * (3-point circle, line), so it is saved with the image and edited like any other
 * measurement. The panel picks the right tool for each step, assigns the next finished graphic to
 * it and recomputes the parameters whenever a graphic moves.
 */
public class SpinopelvicTool extends PluginTool {

  public static final String NAME = "SagitalApp";

  enum Kind {
    CIRCLE("Círculo por 3 puntos"),
    LINE("Línea");

    final String tool;

    Kind(String tool) {
      this.tool = tool;
    }

    boolean accepts(Graphic g) {
      return switch (this) {
        case CIRCLE -> g instanceof ThreePointsCircleGraphic;
        case LINE -> g.getClass() == LineGraphic.class;
      };
    }

    boolean isTool(Object item) {
      return switch (this) {
        case CIRCLE -> item instanceof ThreePointsCircleGraphic;
        case LINE -> item != null && item.getClass() == LineGraphic.class;
      };
    }
  }

  record Step(String label, String hint, Kind kind, boolean optional) {}

  private static final int HIP1 = 0, HIP2 = 1, S1 = 2, L1 = 3, T12 = 4, TK = 5, T1 = 6, C7 = 7;

  private static final Color PLATE = new Color(0x4F, 0xD1, 0xC5);
  private static final Color HIP = new Color(0x7F, 0xB6, 0xFF);
  private static final Color PLUMB = new Color(0xF3, 0xB7, 0x4F);

  /** Everything known about one image (Weasis keeps one graphic model per image). */
  private static final class State {
    final Graphic[] assigned = new Graphic[8];
    final boolean[] skipped = new boolean[8];
    final Set<Graphic> known = Collections.newSetFromMap(new IdentityHashMap<>());
    final List<Graphic> helpers = new ArrayList<>();
    final GraphicLayer helperLayer;
    int active = 0;
    boolean engaged; // the panel has been opened on this image
    String signature = "";

    State(GraphicModel model) {
      known.addAll(model.getModels()); // measurements already there are not landmarks
      // Below the measurement layer, so a click on a landmark handle reaches the landmark and not a
      // helper line ending at the same point. Locked and never saved with the image.
      DefaultLayer layer = new DefaultLayer(LayerType.DRAW);
      layer.setName(NAME);
      layer.setLocked(true);
      layer.setSerializable(false);
      helperLayer = layer;
    }
  }

  private final Map<GraphicModel, State> states = new WeakHashMap<>();
  private State state;
  private ViewCanvas<?> view;
  private String tkLevel = "T4";
  private String report = "";

  private final JScrollPane rootPane = new JScrollPane();
  private final JLabel instruction = new JLabel();
  private final JList<Integer> stepList = new JList<>(new Integer[] {0, 1, 2, 3, 4, 5, 6, 7});
  private final JTextArea results = new JTextArea();
  private final JLabel scale = new JLabel();
  private final JButton skip = new JButton("Saltar");
  private final JButton unassign = new JButton("Borrar trazo");
  private final JButton update = new JButton();
  private Updater.Release release;
  private boolean updatingList;
  private boolean wasShowing;
  private long rearmUntil;

  public SpinopelvicTool(Type type) {
    super(NAME, type, 130);
    var icon = SpinopelvicTool.class.getResource("/icon/pelvis-16.png");
    if (icon != null) {
      dockable.setTitleIcon(new ImageIcon(icon));
    }
    setDockableWidth(300);
    rootPane.setBorder(BorderFactory.createEmptyBorder());
    // PluginTool is itself the panel that Weasis puts in the scroll pane (getToolComponentFromJScrollPane).
    setLayout(new BorderLayout());
    add(buildPanel(), BorderLayout.NORTH);
    new Timer(300, e -> tick()).start();
    Updater.latest(bundleVersion())
        .thenAccept(rel -> { if (rel != null) javax.swing.SwingUtilities.invokeLater(() -> showUpdate(rel)); });
  }

  private Step step(int i) {
    return switch (i) {
      case HIP1 -> new Step("Cabeza femoral 1", "Marcá 3 puntos sobre el borde de una cabeza femoral.", Kind.CIRCLE, false);
      case HIP2 -> new Step("Cabeza femoral 2", "3 puntos sobre la otra cabeza femoral. Saltalo si se superponen.", Kind.CIRCLE, true);
      case S1 -> new Step("Platillo de S1", "Línea de esquina a esquina del platillo superior de S1.", Kind.LINE, false);
      case L1 -> new Step("Platillo superior de L1", "Línea de esquina a esquina.", Kind.LINE, false);
      case T12 -> new Step("Platillo inferior de T12", "Línea de esquina a esquina.", Kind.LINE, true);
      case TK -> new Step("Platillo superior de " + tkLevel, "Línea de esquina a esquina.", Kind.LINE, true);
      case T1 -> new Step("Cuerpo de T1", "Línea diagonal de esquina a esquina del cuerpo de T1 (su punto medio es el centro).", Kind.LINE, true);
      default -> new Step("Cuerpo de C7", "Línea diagonal de esquina a esquina del cuerpo de C7 (su punto medio es el centro).", Kind.LINE, true);
    };
  }

  // ---------------------------------------------------------------- UI

  private JComponent buildPanel() {
    JPanel p = new JPanel();
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    // Hidden until Updater finds a newer release.
    update.setVisible(false);
    update.setFont(update.getFont().deriveFont(Font.BOLD));
    update.addActionListener(e -> installUpdate());
    p.add(left(row(update)));

    instruction.setFont(instruction.getFont().deriveFont(Font.BOLD));
    instruction.setForeground(PLUMB);
    p.add(left(instruction));
    p.add(Box.createVerticalStrut(6));

    stepList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    stepList.setVisibleRowCount(8);
    stepList.setCellRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean sel, boolean focus) {
            int i = (Integer) value;
            Step s = step(i);
            String mark =
                state == null ? "○" : i == state.active ? "▶" : state.assigned[i] != null ? "✓" : state.skipped[i] ? "–" : "○";
            String text = mark + "  " + s.label() + (s.optional() ? " (opcional)" : "");
            return super.getListCellRendererComponent(list, text, index, sel, focus);
          }
        });
    stepList.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting() && !updatingList && state != null && stepList.getSelectedIndex() >= 0) {
            setActive(stepList.getSelectedIndex());
          }
        });
    stepList.addMouseListener(
        new java.awt.event.MouseAdapter() {
          @Override
          public void mouseClicked(java.awt.event.MouseEvent e) {
            int i = stepList.locationToIndex(e.getPoint());
            if (state != null && i >= 0) setActive(i);
          }
        });
    JScrollPane listScroll = new JScrollPane(stepList);
    listScroll.setPreferredSize(new Dimension(260, 8 * 20 + 6));
    p.add(left(listScroll));
    p.add(Box.createVerticalStrut(6));

    JButton reset = new JButton("Reiniciar");
    skip.addActionListener(e -> skipStep());
    unassign.addActionListener(e -> unassignStep());
    reset.addActionListener(e -> resetAll());
    p.add(left(row(skip, unassign, reset)));

    JComboBox<String> tk = new JComboBox<>(new String[] {"Cifosis T4–T12", "Cifosis T5–T12"});
    tk.addActionListener(
        e -> {
          tkLevel = tk.getSelectedIndex() == 1 ? "T5" : "T4";
          if (state != null) {
            state.signature = "";
          }
          refreshUi();
        });
    p.add(left(row(tk)));
    p.add(Box.createVerticalStrut(6));

    results.setEditable(false);
    results.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    results.setRows(14);
    results.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
    JScrollPane resultScroll = new JScrollPane(results);
    resultScroll.setPreferredSize(new Dimension(260, 250));
    p.add(left(resultScroll));
    p.add(Box.createVerticalStrut(4));

    scale.setFont(scale.getFont().deriveFont(11f));
    p.add(left(scale));
    JButton copy = new JButton("Copiar resultados");
    copy.addActionListener(
        e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(report), null));
    JButton about = new JButton("Acerca de…");
    about.addActionListener(e -> showAbout());
    p.add(left(row(copy, about)));
    p.add(Box.createVerticalGlue());
    return p;
  }

  private static final String AUTHOR_URL = "https://www.columna-spine.com/";

  private static String bundleVersion() {
    var bundle = org.osgi.framework.FrameworkUtil.getBundle(SpinopelvicTool.class);
    return bundle == null ? "0" : bundle.getVersion().toString();
  }

  private void showUpdate(Updater.Release rel) {
    release = rel;
    update.setText("Actualizar a la versión " + rel.version() + "…");
    update.setEnabled(true);
    update.setVisible(true);
    revalidate();
  }

  private void installUpdate() {
    Updater.Release rel = release;
    String notes = rel.notes().replaceAll("[*`#]", "").strip();
    if (notes.length() > 600) notes = notes.substring(0, 600) + "…";
    // An HTML label with a fixed width wraps and sizes itself; a wrapping JTextArea gets clipped.
    String body = (notes.isEmpty() ? "" : notes + "\n\n") + "Se descarga el plugin nuevo. Después cerrá y volvé a abrir Weasis.";
    JLabel text = new JLabel(
        "<html><body style='width:340px'><b>Hay una versión nueva: " + rel.version() + "</b> (tenés la " + bundleVersion() + ").<br><br>"
            + body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</body></html>");
    Object[] options = {"Descargar e instalar", "Ahora no"};
    if (JOptionPane.showOptionDialog(this, text, "Actualización disponible", JOptionPane.DEFAULT_OPTION,
            JOptionPane.INFORMATION_MESSAGE, null, options, options[0]) != 0) {
      return;
    }
    update.setEnabled(false);
    update.setText("Descargando la actualización…");
    var bundle = org.osgi.framework.FrameworkUtil.getBundle(SpinopelvicTool.class);
    String location = bundle == null ? "" : bundle.getLocation();
    new Thread(() -> {
      Exception error = null;
      try {
        Updater.install(rel, location);
      } catch (Exception ex) {
        error = ex;
      }
      Exception failed = error;
      javax.swing.SwingUtilities.invokeLater(() -> {
        if (failed == null) {
          update.setText("Versión " + rel.version() + " instalada: reiniciá Weasis");
          JOptionPane.showMessageDialog(this,
              "Listo. Cerrá Weasis y volvé a abrirlo para usar la versión " + rel.version() + ".",
              "Actualización instalada", JOptionPane.INFORMATION_MESSAGE);
          return;
        }
        showUpdate(rel);
        Object[] opts = {"Abrir la página", "Cerrar"};
        if (JOptionPane.showOptionDialog(this,
                "No se pudo instalar la actualización (" + failed.getMessage() + ").\n"
                    + "Podés descargarla a mano desde la página del plugin.",
                "Actualización", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, opts, opts[0]) == 0) {
          browse(Updater.RELEASES_PAGE);
        }
      });
    }, "espinopelvico-update").start();
  }

  private void browse(String url) {
    try {
      java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(this, "No pude abrir el navegador. La dirección es " + url);
    }
  }

  private void showAbout() {
    String version = " " + bundleVersion();
    String text =
        "<html><b>SagitalApp" + version + "</b><br><br>"
            + "Parámetros espinopélvicos en Weasis: mide PI, PT, SS, LL, PI−LL, cifosis torácica,<br>"
            + "T1PA y SVA, con los modificadores SRS-Schwab.<br><br>"
            + "Autor: Dr. Bernardo Sonzini Astudillo<br>"
            + "columna-spine.com<br><br>"
            + "<span style='color:gray'>Herramienta de apoyo; no es un dispositivo médico.<br>"
            + "Código abierto (licencia MIT).</span></html>";
    Object[] options = {"Abrir columna-spine.com", "Cerrar"};
    int choice =
        JOptionPane.showOptionDialog(
            this, text, "Acerca de", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
            null, options, options[0]);
    if (choice == 0) browse(AUTHOR_URL);
  }

  private static JComponent row(JComponent... items) {
    JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    for (JComponent c : items) r.add(c);
    return r;
  }

  private static JComponent left(JComponent c) {
    c.setAlignmentX(Component.LEFT_ALIGNMENT);
    return c;
  }

  @Override
  public Component getToolComponent() {
    return getToolComponentFromJScrollPane(rootPane);
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // Nothing to adapt.
  }

  // ---------------------------------------------------------------- Steps

  private void setActive(int i) {
    state.active = i;
    if (i >= 0) {
      selectTool(step(i).kind());
    }
    refreshUi();
  }

  private void advance(int from) {
    for (int k = 0; k < 8; k++) {
      int i = (from + k) % 8;
      if (state.assigned[i] == null && !state.skipped[i]) {
        setActive(i);
        return;
      }
    }
    setActive(-1);
  }

  private void skipStep() {
    if (state == null || state.active < 0 || !step(state.active).optional()) return;
    state.skipped[state.active] = true;
    advance(state.active + 1);
  }

  /** Deletes the trace of the selected step from the image and waits for a new one. */
  private void unassignStep() {
    if (state == null) return;
    int i = stepList.getSelectedIndex() >= 0 ? stepList.getSelectedIndex() : state.active;
    if (i < 0 || state.assigned[i] == null) return;
    view.getGraphicManager().removeGraphic(state.assigned[i]);
    state.assigned[i] = null;
    state.signature = "";
    view.getJComponent().repaint();
    setActive(i);
  }

  /** Deletes every trace of the measurement and the helper lines, and starts again from step 1. */
  private void resetAll() {
    if (state == null) return;
    int answer =
        JOptionPane.showConfirmDialog(
            this,
            "¿Borrar de la imagen todos los trazos de la medición espinopélvica?",
            "Reiniciar",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (answer != JOptionPane.OK_OPTION) return;
    GraphicModel model = view.getGraphicManager();
    for (int i = 0; i < state.assigned.length; i++) {
      if (state.assigned[i] != null) model.removeGraphic(state.assigned[i]);
      state.assigned[i] = null;
      state.skipped[i] = false;
    }
    for (Graphic g : state.helpers) model.removeGraphic(g);
    state.helpers.clear();
    state.signature = "";
    view.getJComponent().repaint();
    advance(0);
  }

  /** Puts the matching Weasis measurement tool on the left mouse button. */
  private void selectTool(Kind kind) {
    EventManager em = EventManager.getInstance();
    em.getAction(ActionW.DRAW_MEASURE)
        .ifPresent(
            combo -> {
              for (Object item : combo.getAllItem()) {
                if (kind.isTool(item)) {
                  combo.setSelectedItem(item);
                  break;
                }
              }
            });
    em.changeLeftMouseAction(ActionW.MEASURE.cmd());
  }

  private boolean activeToolMatches() {
    EventManager em = EventManager.getInstance();
    if (state == null || state.active < 0) return true;
    Kind kind = step(state.active).kind();
    return ActionW.MEASURE.cmd().equals(em.getMouseActions().getLeft())
        && em.getAction(ActionW.DRAW_MEASURE).map(c -> kind.isTool(c.getSelectedItem())).orElse(false);
  }

  // ---------------------------------------------------------------- Polling

  private void tick() {
    ViewCanvas<?> v = EventManager.getInstance().getSelectedViewPane();
    if (v == null || v.getGraphicManager() == null || v.getImage() == null) {
      if (state != null || view != null) {
        state = null;
        view = null;
        refreshUi();
      }
      return;
    }
    GraphicModel model = v.getGraphicManager();
    State s = states.computeIfAbsent(model, State::new);
    if (s != state || v != view) {
      state = s;
      view = v;
      s.signature = "";
      wasShowing = false; // a new image counts as opening the panel on it
      refreshUi();
    }
    // The panel puts the step's tool on the left button only when the panel is opened, when a step
    // is clicked (setActive) and right after a graphic is assigned, because Weasis falls back to the
    // selection pointer after each drawing ("draw only once"). Any tool the user picks afterwards
    // (pan, zoom, the selection pointer to edit a trace) is left alone.
    boolean showing = isShowing();
    boolean opened = showing && !wasShowing;
    wasShowing = showing;
    if (showing) s.engaged = true;
    boolean justAssigned = System.currentTimeMillis() < rearmUntil && !activeToolMatches();
    if (s.engaged && s.active >= 0 && (opened || justAssigned)) {
      selectTool(step(s.active).kind());
    }

    List<Graphic> graphics = new ArrayList<>(model.getModels());
    boolean changed = false;
    for (int i = 0; i < 8; i++) {
      if (s.assigned[i] != null && !graphics.contains(s.assigned[i])) {
        s.assigned[i] = null; // deleted in Weasis
        changed = true;
      }
    }
    for (Graphic g : graphics) {
      if (s.known.contains(g) || g.getLayer() == s.helperLayer) continue;
      if (!Boolean.TRUE.equals(g.isGraphicComplete())) continue; // still being drawn
      s.known.add(g);
      if (s.active >= 0 && step(s.active).kind().accepts(g)) {
        s.assigned[s.active] = g;
        s.skipped[s.active] = false;
        g.setPaint(step(s.active).kind() == Kind.CIRCLE ? HIP : PLATE);
        rearmUntil = System.currentTimeMillis() + 1500; // Weasis resets the tool right after a drawing
        advance(s.active + 1);
        changed = true;
      }
    }

    String sig = signature(s);
    if (changed || !sig.equals(s.signature)) {
      s.signature = sig;
      recompute();
    }
  }

  private String signature(State s) {
    StringBuilder b = new StringBuilder(tkLevel);
    for (Graphic g : s.assigned) {
      b.append('|');
      if (g != null) {
        for (Point2D p : g.getPts()) b.append(Math.round(p.getX() * 10)).append(',').append(Math.round(p.getY() * 10)).append(';');
      }
    }
    ImageElement img = view.getImage();
    b.append(img.getPixelSize()).append(img.getPixelSpacingUnit());
    return b.toString();
  }

  // ---------------------------------------------------------------- Computation

  private Point2D[] line(Graphic g) {
    if (g == null || g.getPts().size() < 2) return null;
    return new Point2D[] {g.getPts().get(0), g.getPts().get(1)};
  }

  private static Point2D center(Graphic g) {
    if (g == null || g.getPts().isEmpty()) return null;
    List<Point2D> pts = g.getPts();
    if (g instanceof ThreePointsCircleGraphic && pts.size() >= 3) {
      return Geometry.circleCenter(pts.get(0), pts.get(1), pts.get(2));
    }
    double x = 0, y = 0;
    for (Point2D p : pts) {
      x += p.getX();
      y += p.getY();
    }
    return new Point2D.Double(x / pts.size(), y / pts.size());
  }

  /** mm per image unit, or 0 when the image has no spatial calibration. */
  private double mmPerPixel() {
    ImageElement img = view.getImage();
    Unit unit = img.getPixelSpacingUnit();
    if (unit == null || unit == Unit.PIXEL) return 0;
    return img.getPixelSize() * unit.getFactorToMeters() * 1000;
  }

  private void recompute() {
    State s = state;
    Geometry.Input in = new Geometry.Input();
    List<Point2D> hips = new ArrayList<>();
    for (int i : new int[] {HIP1, HIP2}) {
      Point2D c = center(s.assigned[i]);
      if (c != null) hips.add(c);
    }
    in.hips = hips;
    in.s1 = line(s.assigned[S1]);
    in.l1 = line(s.assigned[L1]);
    in.t12 = line(s.assigned[T12]);
    in.tk = line(s.assigned[TK]);
    in.t1 = center(s.assigned[T1]);
    in.c7 = center(s.assigned[C7]);
    in.mmPerPixel = mmPerPixel();
    Geometry.Result r = Geometry.compute(in);
    showResults(r, in);
    drawHelpers(in);
    refreshUi();
  }

  private static String deg(Double v) {
    return v == null ? "     —" : String.format("%6.1f°", v);
  }

  private void showResults(Geometry.Result r, Geometry.Input in) {
    StringBuilder b = new StringBuilder();
    b.append("PI    Incidencia pélvica ").append(deg(r.pi)).append('\n');
    b.append("PT    Tilt pélvico       ").append(deg(r.pt)).append('\n');
    b.append("SS    Slope sacro        ").append(deg(r.ss)).append('\n');
    b.append("LL    Lordosis L1–S1     ").append(deg(r.ll)).append('\n');
    b.append("PI−LL Mismatch           ").append(deg(r.pill)).append('\n');
    b.append("TK    Cifosis ").append(tkLevel).append("–T12    ").append(deg(r.tk)).append('\n');
    b.append("T1PA  T1 pelvic angle    ").append(deg(r.t1pa)).append('\n');
    b.append("SVA   C7 – S1 posterior  ")
        .append(r.sva == null ? "     —" : String.format(r.svaInMm ? "%+6.1f mm" : "%+6.0f px", r.sva))
        .append('\n');
    String mPill = Geometry.schwabPill(r), mPt = Geometry.schwabPt(r), mSva = Geometry.schwabSva(r);
    if (mPill != null || mPt != null || mSva != null) {
      b.append("\nModificadores SRS-Schwab\n");
      if (mPill != null) b.append(String.format("  PI−LL %-3s (0 <10° · + 10–20° · ++ >20°)%n", mPill));
      if (mPt != null) b.append(String.format("  PT    %-3s (0 <20° · + 20–30° · ++ >30°)%n", mPt));
      if (mSva != null) b.append(String.format("  SVA   %-3s (0 <4 cm · + 4–9,5 cm · ++ >9,5 cm)%n", mSva));
    }
    b.append("\nLL y TK positivos = lordosis y cifosis.\nSVA y PT positivos = hacia anterior.");
    report = b.toString();
    results.setText(report);
    results.setCaretPosition(0);
    scale.setText(
        in.mmPerPixel > 0
            ? String.format("Escala: %.3f mm/píxel. Calibrable con la herramienta de Weasis.", in.mmPerPixel)
            : "Sin escala: el SVA sale en píxeles. Calibrá con una línea de Weasis.");
  }

  // ---------------------------------------------------------------- Helper lines

  private void drawHelpers(Geometry.Input in) {
    GraphicModel model = view.getGraphicManager();
    for (Graphic g : state.helpers) model.removeGraphic(g);
    state.helpers.clear();

    Point2D hip = null;
    if (!in.hips.isEmpty()) {
      double x = 0, y = 0;
      for (Point2D h : in.hips) {
        x += h.getX();
        y += h.getY();
      }
      hip = new Point2D.Double(x / in.hips.size(), y / in.hips.size());
    }
    if (in.hips.size() == 2) addHelper(in.hips.get(0), in.hips.get(1), HIP);
    if (in.s1 != null) {
      Point2D mid = mid(in.s1[0], in.s1[1]);
      if (hip != null) {
        addHelper(hip, mid, HIP);
        // Perpendicular to S1 at its midpoint, towards the hips, as long as the hip line (PI).
        double ex = in.s1[1].getX() - in.s1[0].getX(), ey = in.s1[1].getY() - in.s1[0].getY();
        double len = Math.hypot(ex, ey), reach = hip.distance(mid);
        double nx = -ey / len, ny = ex / len;
        if (nx * (hip.getX() - mid.getX()) + ny * (hip.getY() - mid.getY()) < 0) {
          nx = -nx;
          ny = -ny;
        }
        addHelper(mid, new Point2D.Double(mid.getX() + nx * reach, mid.getY() + ny * reach), PLATE);
        if (in.t1 != null) addHelper(hip, in.t1, HIP);
      }
      if (in.c7 != null) {
        Point2D post = in.s1[0].getY() <= in.s1[1].getY() ? in.s1[0] : in.s1[1];
        Point2D foot = new Point2D.Double(in.c7.getX(), post.getY());
        addHelper(in.c7, foot, PLUMB); // C7 plumb line
        addHelper(foot, post, PLUMB); // SVA
      }
    }
    view.getJComponent().repaint();
  }

  private static Point2D mid(Point2D a, Point2D b) {
    return new Point2D.Double((a.getX() + b.getX()) / 2, (a.getY() + b.getY()) / 2);
  }

  private void addHelper(Point2D a, Point2D b, Color color) {
    if (a == null || b == null || a.distance(b) < 1) return;
    try {
      LineGraphic g = new LineGraphic();
      g.setPaint(color);
      g.setLineThickness(1.5f);
      g.setLabelVisible(false);
      g.buildGraphic(List.of(new Point2D.Double(a.getX(), a.getY()), new Point2D.Double(b.getX(), b.getY())));
      AbstractGraphicModel.addGraphicToModel(view, state.helperLayer, g);
      state.known.add(g);
      state.helpers.add(g);
    } catch (Exception e) {
      // A helper line is cosmetic; skip it rather than break the measurement.
    }
  }

  // ---------------------------------------------------------------- Refresh

  private void refreshUi() {
    if (state == null) {
      instruction.setText("<html>Abrí una telerradiografía de perfil en el visor.</html>");
      results.setText("");
      scale.setText("");
      skip.setEnabled(false);
      unassign.setEnabled(false);
      stepList.repaint();
      return;
    }
    if (state.active >= 0) {
      Step s = step(state.active);
      instruction.setText(
          "<html>" + s.label() + "<br><span style='font-weight:normal'>" + s.hint()
              + "<br>Herramienta: " + s.kind().tool + "</span></html>");
    } else {
      instruction.setText(
          "<html>Listo.<br><span style='font-weight:normal'>Ajustá cualquier trazo arrastrándolo; los valores se recalculan.</span></html>");
    }
    skip.setEnabled(state.active >= 0 && step(state.active).optional());
    unassign.setEnabled(true);
    updatingList = true;
    if (state.active >= 0) stepList.setSelectedIndex(state.active);
    else stepList.clearSelection();
    updatingList = false;
    stepList.repaint();
  }
}
