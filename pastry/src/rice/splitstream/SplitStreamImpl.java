package rice.splitstream;

import java.io.*;
import java.util.*;

import rice.scribe.*;
import rice.scribe.messaging.*;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

import rice.splitstream.messaging.*;

/**
 * This is the implementing class of the ISplitStream interface. It 
 * provides the functionality of creating and attaching to channels.
 * It also provides alot of implementation details. It handles the
 * creation of Channel objects in the path of the Channel tree. It 
 * also monitors the creation of stripes interior to the tree and
 * keeps track of the bandwidth used until the user subscribes to 
 * the channel. It implements the IScribeApp interface for this reason
 *
 * @(#) SplitStreamImpl.java
 * @version $Id$
 * @author Ansley Post
 */
public class SplitStreamImpl implements ISplitStream, IScribeApp,IScribeObserver{
    /**
     * The scribe instance for this SplitStream Object
     */
    private IScribe scribe = null;

    /**
     * The bandwidthManger which controls bandwidth usage
     */
    private BandwidthManager bandwidthManager = new BandwidthManager();

    /**
     * Credentials for this application
     */
    private Credentials credentials = new PermissiveCredentials();

    /**
     * The pastry node that this application is running on
     */
    private PastryNode node; 

    /**
     * Hashtable of all the channels currently created on this node implicitly
     * or explicitly.
     */
    private Hashtable channels;

    /**
     * Set of ISplitStreamApps waiting for the SplitStream object to be ready,
     * which is ready when Scribe is ready
     */
    private Set m_apps = new HashSet();
    
    /**
     * Flag for whether this obj is ready
     */
    private boolean m_ready = false;

    /**
     * The constructor for building the splitStream object
     */
     public SplitStreamImpl(PastryNode node, IScribe scribe){
 	this.scribe = scribe;  
 	this.scribe.registerScribeObserver(this);
	this.scribe.registerApp(this);
        this.node = node;
	this.channels = new Hashtable();
   }

   /**
    * This method is used by a peer who wishes to distribute the content
    * using SplitStream. It creates a Channel Object consisting of numStripes
    * number of Stripes, one for each strips content. A Channel object is
    * responsible for implementing SplitStream functionality, like maintaing
    * multiple multicast trees, bandwidth management and discovering parents
    * having spare capacity. One Channel object should be created for each 
    * content distribution which wishes to use SplitStream. 
    * @param numStripes - number of parts into which the content will
    *        be striped, each part is a Stripe.
    * @return an instance of a Channel class. 
    */
   public Channel createChannel(int numStripes, String name){

	Channel channel = new Channel(numStripes, name, scribe, credentials, bandwidthManager, node);
	channels.put(channel.getChannelId(), channel);
	return (channel);

   }

   /**
    * This method is used by peers who wish to listen to content distributed 
    * by some other peer using SplitStream. It attaches the local node to the 
    * Channel which is being used by the source peer to distribute the content.
    * Essentially, this method finds out the different parameters of Channel
    * object which is created by the source, (the peer distributing the content)    *, and then creates a local Channel object with these parameters and
    * returns it.  
    * This is a non-blocking call so the returned Channel object may not be 
    * initialized with all the parameters, so applications should wait for 
    * channelIsReady() notification made by channels when they are ready. 
    * @param channelId - Identifier of channel to which local node wishes
    *  to attach to. 
    * @return  An instance of Channel object.
    */
   public Channel attachChannel(ChannelId channelId){
     Channel channel = (Channel) channels.get(channelId);
     //System.out.println("Attempting to attach to Channel " + channelId);
     if(channel == null){
	//System.out.println("Creating New Channel Object");
     	channel = new Channel(channelId, scribe, credentials, bandwidthManager, node);
	channels.put(channelId, channel);
     }
	return channel;
   }

   /**
    * Gets the bandwidth manager associated with this splitstream object
    * 
    * @return BandwidthManager that is associated with this splitstream
    */ 
   public BandwidthManager getBandwidthManager(){
      return bandwidthManager;
   }

   /**
    * Sets the bandwidthManager for this splitstream
    *
    * @param bandwidthManager the new bandwidthManager
    */
   public void setBandwidthManager(BandwidthManager bandwidthManager){
       this.bandwidthManager = bandwidthManager;
   }

   /** - IScribeObserver Implementation -- */
   /**
    * The update method called when a new topic is created at this
    * node.  When a new topic is created we join it so we can receive
    * any more messages that come for it.
    *
    * @param topicId the new topic being created
    */
    public void update(Object topicId){
	if(scribe.join((NodeId) topicId, this, credentials)){}
   } 

   /** - IScribeApp Implementation -- */
   /**
    * The method called when a fault occurs. Currently not implemented.
    *
    * @param msg The message to be sent on the failure
    * @param faultyParent the node that caused the fault 
    */
    public void faultHandler(ScribeMessage msg, NodeHandle faultyParent){}
   public void forwardHandler(ScribeMessage msg){}
   public void receiveMessage(ScribeMessage msg){
     /* Check the type of message */
     /* then make call accordingly */
     //System.out.println("Recieved message");
   }
  
   /**
    * Upcall generated when the underlying scribe is ready
    */ 
   public void scribeIsReady(){
       //System.out.println("Scribe is Ready");
       m_ready = true;
       notifyApps();
   }

   /**
    * The upcall generated when a subscribe message is recieved
    * This currently handles the  implicit creation of channel objects
    * when they don't exist at this node
    *
    * @param topicId the topic being subscribed/dropped
    * @param child the child to be added/dropped
    * @param was added wether the was a subscribe or an unsubscribe
    * @param data the date that was in the subscribe/unsubscribe message
    * 
    */
   public void subscribeHandler(NodeId topicId, 
                               NodeHandle child, 
                               boolean wasAdded,  
                               Serializable data){
     //System.out.println("Subscribe Handler at " + ((Scribe) scribe).getNodeId() + " for " + topicId + " from " + child.getNodeId());
     NodeId[] nodeData = (NodeId[]) data;
     
     if(nodeData!=null){
   			
	/* Clean This up */
	StripeId[] stripeId = new StripeId[nodeData.length - 2];
	ChannelId channelId = new ChannelId(nodeData[0]);
	for(int i = 1; i < nodeData.length -1; i++){
		stripeId[i-1] = new StripeId(nodeData[i]);
	}


        SpareCapacityId spareCapacityId = new SpareCapacityId(nodeData[nodeData.length -1]);
	/* Clean This up */
	if(channels.get(channelId) == null)
	channels.put(channelId, 
	new Channel(channelId, stripeId, spareCapacityId, scribe, bandwidthManager, node));
        }
     }
    
    /**
     * Returns the underlying scribe object.
     */
    public IScribe getScribe(){
	return scribe;
    }
    
    /**
     * registers an app to be notified when a fault occurs
     * that is so severe that the application can not handle it
     * by automatically repairing it. For example if there is
     * no spare capacity left in the system and node is looking
     * for a new parent
     * 
     * @param app the app to be registered
    public void registerApp(ISplitStreamApp app){
	m_apps.add(app);
    }
   
    /**
     * called when the apps registered with this splitstream
     * object must be notified of something
     *
     */
    public void notifyApps(){
	Iterator it = m_apps.iterator();
	
	while(it.hasNext()){
	    ISplitStreamApp app = (ISplitStreamApp)it.next();
	    app.splitstreamIsReady();
	}
    }

} 

