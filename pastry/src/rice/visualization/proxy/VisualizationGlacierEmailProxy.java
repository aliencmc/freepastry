package rice.visualization.proxy;

import rice.visualization.*;
import rice.visualization.server.*;
import rice.email.proxy.*;

import java.io.*;
import java.net.*;

import rice.p2p.multiring.*;

import rice.pastry.dist.*;

public class VisualizationGlacierEmailProxy extends EmailProxy {
    
  protected VisualizationServer server;
  
  protected InetSocketAddress serverAddress;
  
  protected int visualizationPort;
  
  protected static boolean visualization = false;

  protected transient String[] args;
  
  public VisualizationGlacierEmailProxy(String[] args) {
    super(args);
    this.args = args;
  }
  
  public void start() { 
    try {
      super.start();
    } catch (Exception e) {
      System.err.println("Exception: "+e);
      e.printStackTrace();
      System.exit(1);
    }
    
    DistPastryNode pastry = (DistPastryNode) ((MultiringNode) node).getNode();
    
    visualizationPort = ((DistNodeHandle) pastry.getLocalHandle()).getAddress().getPort() + Visualization.PORT_OFFSET;
     
    sectionStart("Starting Visualization services");
    stepStart("Creating Visualization Server");
    try {
      this.serverAddress = new InetSocketAddress(InetAddress.getLocalHost(), 
                                                 visualizationPort);
    } catch (IOException e) {
      stepDone(FAILURE, e + "");
    }
    
    server = new VisualizationServer(serverAddress, pastry, new Object[] {pastry, past, storage});
    server.setRestartCommand(
      "java -cp classes/:lib/activation.jar:lib/bouncycastle.jar:lib/javamail.jar:lib/junit.jar:lib/antlr.jar:lib/xmlpull_1_1_3_4a.jar:lib/xpp3-1.1.3.4d_b2.jar rice.visualization.proxy.VisualizationEmailProxy",
      args);
    server.addPanelCreator(new OverviewPanelCreator());
    NetworkActivityPanelCreator network = new NetworkActivityPanelCreator();
    server.addPanelCreator(network);
    MessageDistributionPanelCreator message = new MessageDistributionPanelCreator();
    server.addPanelCreator(message);
    RecentMessagesPanelCreator recent = new RecentMessagesPanelCreator();
    server.addPanelCreator(recent);
    server.addPanelCreator(new PastryPanelCreator());
    server.addPanelCreator(new PASTPanelCreator());
    server.addPanelCreator(new GlacierPanelCreator());
    
    pastry.addNetworkListener(network);
    pastry.addNetworkListener(recent);
    pastry.addNetworkListener(message);
    stepDone(SUCCESS);
    
    stepStart("Starting Visualization Server on port " + visualizationPort);
    Thread t = new Thread(server, "Visualization Server Thread");
    t.start();
    stepDone(SUCCESS);

    if (visualization) {
      stepStart("Launching Visualization Client");
      Visualization visualization = new Visualization((DistNodeHandle) pastry.getLocalHandle());
      stepDone(SUCCESS);
    }
  }
  
  public void parseArgs(String[] args) {
    super.parseArgs(args);
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-visualization")) {
        visualization = true;
        break;
      }
    }
  }
  
  public static void main(String[] args) {
    VisualizationGlacierEmailProxy proxy = new VisualizationGlacierEmailProxy(args);
    try {
        proxy.start();
    } catch (Exception e) {
        System.err.println("Exception: "+e);
        e.printStackTrace();
    }
  }
  
}