package com.columnaspine.weasis.spinopelvic;

import java.util.Hashtable;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.dicom.viewer2d.EventManager;

/**
 * Registers the panel in the tool bar on the right of the DICOM 2D viewer. Declared as an OSGi
 * component in OSGI-INF/spinopelvic-tool.xml (service InsertableFactory, property
 * org.weasis.dicom.viewer2d.View2dContainer=true), like the Weasis plug-in archetype does.
 * {@link Image} does the same for the viewer Weasis uses for JPG, PNG and other plain images.
 */
public class SpinopelvicToolFactory implements InsertableFactory {

  private SpinopelvicTool tool;

  /** Each viewer gets its own panel, driven by that viewer's event manager. */
  protected SpinopelvicTool newTool() {
    return new SpinopelvicTool(getType(), EventManager::getInstance);
  }

  /** Image viewer (org.weasis.base.viewer2d), declared in OSGI-INF/spinopelvic-image-tool.xml. */
  public static class Image extends SpinopelvicToolFactory {
    @Override
    protected SpinopelvicTool newTool() {
      return new SpinopelvicTool(getType(), org.weasis.base.viewer2d.EventManager::getInstance);
    }
  }

  @Override
  public Type getType() {
    return Type.TOOL;
  }

  @Override
  public Insertable createInstance(Hashtable<String, Object> properties) {
    if (tool == null) {
      tool = newTool();
    }
    return tool;
  }

  @Override
  public void dispose(Insertable component) {
    tool = null;
  }

  @Override
  public boolean isComponentCreatedByThisFactory(Insertable component) {
    return component == tool;
  }
}
