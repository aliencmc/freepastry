/*
 * Created on Jun 24, 2005
 */
package rice.tutorial.lesson7;

import java.net.*;
import java.util.Vector;

import rice.Continuation;
import rice.environment.Environment;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.past.*;
import rice.pastry.*;
import rice.pastry.commonapi.PastryIdFactory;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;
import rice.persistence.*;

/**
 * This tutorial shows how to use Past.
 * 
 * @author Jeff Hoye, Jim Stewart, Ansley Post
 */
public class PastTutorial {
  /**
   * this will keep track of our Past applications
   */
  Vector apps = new Vector();

  /**
   * Based on the rice.tutorial.lesson6.ScribeTutorial
   * 
   * This constructor launches numNodes PastryNodes. They will bootstrap to an
   * existing ring if one exists at the specified location, otherwise it will
   * start a new ring.
   * 
   * @param bindport the local port to bind to
   * @param bootaddress the IP:port of the node to boot from
   * @param numNodes the number of nodes to create in this JVM
   * @param env the Environment
   */
  public PastTutorial(int bindport, InetSocketAddress bootaddress,
      int numNodes, Environment env) throws Exception {
    
    // Generate the NodeIds Randomly
    NodeIdFactory nidFactory = new RandomNodeIdFactory(env);


    // construct the PastryNodeFactory, this is how we use rice.pastry.socket
    PastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory,
        bindport, env);

    // loop to construct the nodes/apps
    for (int curNode = 0; curNode < numNodes; curNode++) {
      // This will return null if we there is no node at that location
      NodeHandle bootHandle = ((SocketPastryNodeFactory) factory)
          .getNodeHandle(bootaddress);

      // construct a node, passing the null boothandle on the first loop will
      // cause the node to start its own ring
      PastryNode node = factory.newNode((rice.pastry.NodeHandle) bootHandle);

      // the node may require sending several messages to fully boot into the
      // ring
      while (!node.isReady()) {
        // delay so we don't busy-wait
        Thread.sleep(100);
      }

      System.out.println("Finished creating new node " + node);
      
      
      // used for generating PastContent object Ids.
      // this implements the "hash function" for our DHT
      PastryIdFactory idf = new rice.pastry.commonapi.PastryIdFactory(env);
      
      // create a different storage root for each node
      String storageDirectory = "./storage"+node.getId().hashCode();

      // create the persistent part
      Storage stor = new PersistentStorage(idf, storageDirectory, 4 * 1024 * 1024, node
          .getEnvironment());
      Past app = new PastImpl(node, new StorageManagerImpl(idf, stor, new LRUCache(
          new MemoryStorage(idf), 512 * 1024, node.getEnvironment())), 3, "");
      
      apps.add(app);      
    }
    
    // wait 5 seconds
    Thread.sleep(5000);

    // We could cache the idf from whichever app we use, but it doesn't matter
    PastryIdFactory localFactory = new rice.pastry.commonapi.PastryIdFactory(env);

    // Store 5 keys
    // let's do the "put" operation
    System.out.println("Storing 5 keys");
    Id[] storedKey = new Id[5];
    for(int ctr = 0; ctr < storedKey.length; ctr++) {
      // these variables are final so that the continuation can access them
      final String s = "test" + env.getRandomSource().nextInt();
      
      // build the past content
      final PastContent myContent = new MyPastContent(localFactory.buildId(s), s);
    
      // store the key for a lookup at a later point
      storedKey[ctr] = myContent.getId();
      
      // pick a random past appl on a random node
      Past p = (Past)apps.get(env.getRandomSource().nextInt(numNodes));
      System.out.println("Inserting " + myContent + " at node "+p.getLocalNodeHandle());
      
      // insert the data
      p.insert(myContent, new Continuation() {
        // the result is an Array of Booleans for each insert
        public void receiveResult(Object result) {          
          Boolean[] results = ((Boolean[]) result);
          int numSuccessfulStores = 0;
          for (int ctr = 0; ctr < results.length; ctr++) {
            if (results[ctr].booleanValue()) 
              numSuccessfulStores++;
          }
          System.out.println(myContent + " successfully stored at " + 
              numSuccessfulStores + " locations.");
        }
  
        public void receiveException(Exception result) {
          System.out.println("Error storing "+myContent);
          result.printStackTrace();
        }
      });
    }
    
    // wait 5 seconds
    Thread.sleep(5000);
    
    // let's do the "get" operation
    System.out.println("Looking up the 5 keys");
    
    // for each stored key
    for (int ctr = 0; ctr < storedKey.length; ctr++) {
      final Id lookupKey = storedKey[ctr];
      
      // pick a random past appl on a random node
      Past p = (Past)apps.get(env.getRandomSource().nextInt(numNodes));

      System.out.println("Looking up " + lookupKey + " at node "+p.getLocalNodeHandle());
      p.lookup(lookupKey, new Continuation() {
        public void receiveResult(Object result) {
          System.out.println("Successfully looked up " + result + " for key "+lookupKey+".");
        }
  
        public void receiveException(Exception result) {
          System.out.println("Error looking up "+lookupKey);
          result.printStackTrace();
        }
      });
    }
    
    // wait 5 seconds
    Thread.sleep(5000);
    
    // now lets see what happens when we do a "get" when there is nothing at the key
    System.out.println("Looking up a bogus key");
    final Id bogusKey = localFactory.buildId("bogus");
    
    // pick a random past appl on a random node
    Past p = (Past)apps.get(env.getRandomSource().nextInt(numNodes));

    System.out.println("Looking up bogus key " + bogusKey + " at node "+p.getLocalNodeHandle());
    p.lookup(bogusKey, new Continuation() {
      public void receiveResult(Object result) {
        System.out.println("Successfully looked up " + result + " for key "+bogusKey+".  Notice that the result is null.");
      }

      public void receiveException(Exception result) {
        System.out.println("Error looking up "+bogusKey);
        result.printStackTrace();
      }
    });
  }

  /**
   * Usage: java [-cp FreePastry- <version>.jar]
   * rice.tutorial.lesson4.DistTutorial localbindport bootIP bootPort numNodes
   * example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001 10
   */
  public static void main(String[] args) throws Exception {
    // Loads pastry configurations
    Environment env = new Environment();

    // disable the UPnP setting (in case you are testing this on a NATted LAN)
    env.getParameters().setString("nat_search_policy","never");
    
    try {
      // the port to use locally
      int bindport = Integer.parseInt(args[0]);

      // build the bootaddress from the command line args
      InetAddress bootaddr = InetAddress.getByName(args[1]);
      int bootport = Integer.parseInt(args[2]);
      InetSocketAddress bootaddress = new InetSocketAddress(bootaddr, bootport);

      // the port to use locally
      int numNodes = Integer.parseInt(args[3]);

      // launch our node!
      PastTutorial dt = new PastTutorial(bindport, bootaddress, numNodes, env);
    } catch (Exception e) {
      // remind user how to use
      System.out.println("Usage:");
      System.out
          .println("java [-cp FreePastry-<version>.jar] rice.tutorial.lesson4.DistTutorial localbindport bootIP bootPort numNodes");
      System.out
          .println("example java rice.tutorial.DistTutorial 9001 pokey.cs.almamater.edu 9001 10");
      throw e;
    }
  }
}

