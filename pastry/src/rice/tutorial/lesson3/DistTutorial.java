package rice.tutorial.lesson3;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.NodeHandleSet;
import rice.pastry.NodeHandle;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.PastryNodeFactory;
import rice.pastry.leafset.LeafSet;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;

/**
 * This tutorial shows how to setup a FreePastry node using the Socket Protocol.
 * 
 * @author Jeff Hoye
 */
public class DistTutorial {

  /**
   * This constructor sets up a PastryNode.  It will bootstrap to an 
   * existing ring if it can find one at the specified location, otherwise
   * it will start a new ring.
   * 
   * @param bindport the local port to bind to 
   * @param bootaddress the IP:port of the node to boot from
   */
  public DistTutorial(int bindport, InetSocketAddress bootaddress) throws Exception {
    
    // Generate the NodeIds Randomly
    NodeIdFactory nidFactory = new RandomNodeIdFactory();
    
    // construct the PastryNodeFactory, this is how we use rice.pastry.socket
    PastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, bindport);

    // This will return null if we there is no node at that location
    NodeHandle bootHandle = ((SocketPastryNodeFactory)factory).getNodeHandle(bootaddress);

    // construct a node, passing the null boothandle on the first loop will cause the node to start its own ring
    PastryNode node = factory.newNode(bootHandle);
      
    // the node may require sending several messages to fully boot into the ring
    while(!node.isReady()) {
      // delay so we don't busy-wait
      Thread.sleep(100);
    }
    
    System.out.println("Finished creating new node "+node);
    
    // construct a new MyApp
    MyApp app = new MyApp(node);
    
    // wait 10 seconds
    Thread.sleep(10000);
    
    // as long as we're not the first node
    if (bootHandle != null) {
      
      // route 10 messages
      for (int i = 0; i < 10; i++) {
        // pick a key at random
        Id randId = nidFactory.generateNodeId();
        
        // send to that key
        app.routeMyMsg(randId);
        
        // wait a sec
        Thread.sleep(1000);
      }

      // wait 10 seconds
      Thread.sleep(10000);
      
      // send directly to my leafset
      LeafSet leafSet = node.getLeafSet();
      
      // this is a typical loop to cover your leafset.  Note that if the leafset
      // overlaps, then duplicate nodes will be sent to twice
      for (int i=-leafSet.ccwSize(); i<=leafSet.cwSize(); i++) {
        if (i != 0) { // don't send to self
          // select the item
          NodeHandle nh = leafSet.get(i);
          
          // send the message directly to the node
          app.routeMyMsgDirect(nh);   
          
          // wait a sec
          Thread.sleep(1000);
        }
      }
    }
  }

  /**
   * Usage: 
   * java [-cp FreePastry-<version>.jar] rice.tutorial.DistTutorial localbindport bootIP bootPort
   * example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001
   */
  public static void main(String[] args) throws Exception {
    // the port to use locally
    int bindport = Integer.parseInt(args[0]);
    
    // build the bootaddress from the command line args
    InetAddress bootaddr = InetAddress.getByName(args[1]);
    int bootport = Integer.parseInt(args[2]);
    InetSocketAddress bootaddress = new InetSocketAddress(bootaddr,bootport);

    // launch our node!
    DistTutorial dt = new DistTutorial(bindport, bootaddress);
  }
}