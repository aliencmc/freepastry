package rice.visualization;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import javax.swing.*;
import java.math.*;

import rice.visualization.data.*;
import rice.visualization.render.*;
import rice.pastry.dist.*;

public class PastryNodePanel extends JPanel {
  
  public static int PASTRY_NODE_PANEL_WIDTH = PastryRingPanel.PASTRY_RING_PANEL_WIDTH + InformationPanel.INFORMATION_PANEL_WIDTH;
  public static int PASTRY_NODE_PANEL_HEIGHT = 250;
  
  public static int PASTRY_NODE_TAB_WIDTH = PASTRY_NODE_PANEL_WIDTH - 40;
  public static int PASTRY_NODE_TAB_HEIGHT = PASTRY_NODE_PANEL_HEIGHT - 40;
  
  protected Visualization visualization;
  
  protected ViewRendererFactory factory;
  
  protected int selected = 0;
  
  protected JTabbedPane pane = null;
        
  public PastryNodePanel(Visualization visualization, ViewRendererFactory factory) {    
    this.visualization = visualization;
    this.factory = factory;
  }
  
  public void nodeSelected(DistNodeHandle node) {
    removeAll();
    
    if (visualization.getSelected() != null) {
      if (pane != null)
        selected = pane.getSelectedIndex();
  
      processData(visualization.getData(visualization.getSelected()));
    } else {
      pane = null;
      selected = 0;
    }
    
    repaint();  
  }
  
  protected void processData(Data data) {
    pane = new JTabbedPane();
    add(pane);

    DataPanel[] panels = data.getDataPanels();
    
    for (int i=0; i<panels.length; i++) {
      JPanel panel = processDataPanel(panels[i]);
      pane.addTab(panels[i].getName(), panel);
    }
    
    pane.setSelectedIndex(selected);
    
    doLayout(); 
  }
  
  protected JPanel processDataPanel(DataPanel panel) {
    GridBagLayout layout = new GridBagLayout();
    JPanel jpanel = new JPanel(layout);
    jpanel.setPreferredSize(new Dimension(PASTRY_NODE_TAB_WIDTH, PASTRY_NODE_TAB_HEIGHT));
    
    for (int i=0; i<panel.getDataViewCount(); i++) {
      JPanel thisPanel = factory.render(panel.getDataView(i));
      layout.setConstraints(thisPanel, panel.getDataView(i).getConstraints());      
      jpanel.add(thisPanel);
    }
        
    return jpanel; //factory.render(panel.getDataView(0)); //jpanel;
  }
  
  public Dimension getPreferredSize() {
    return new Dimension(PASTRY_NODE_PANEL_WIDTH, PASTRY_NODE_PANEL_HEIGHT);
  }
}