package com.columnaspine.weasis.spinopelvic;

import java.util.Hashtable;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;

/**
 * Registers the panel in the tool bar on the right of the DICOM 2D viewer. Declared as an OSGi
 * component in OSGI-INF/spinopelvic-tool.xml (service InsertableFactory, property
 * org.weasis.dicom.viewer2d.View2dContainer=true), like the Weasis plug-in archetype does.
 */
public class SpinopelvicToolFactory implements InsertableFactory {

  private SpinopelvicTool tool;

  @Override
  public Type getType() {
    return Type.TOOL;
  }

  @Override
  public Insertable createInstance(Hashtable<String, Object> properties) {
    if (tool == null) {
      tool = new SpinopelvicTool(getType());
    }
    return tool;
  }

  @Override
  public void dispose(Insertable component) {
    tool = null;
  }

  @Override
  public boolean isComponentCreatedByThisFactory(Insertable component) {
    return component instanceof SpinopelvicTool;
  }
}
