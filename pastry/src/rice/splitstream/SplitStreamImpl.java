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
/*
 * @(#) SplitStream.java
 *
 */

/**
 * This is the implementing class of the SplitStream service 
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

   public BandwidthManager getBandwidthManager(){
      return bandwidthManager;
   }

   public void setBandwidthManager(BandwidthManager bandwidthManager){
       this.bandwidthManager = bandwidthManager;
   }

   /** - IScribeObserver Implementation -- */
   public void update(Object topicId){

	if(scribe.join((NodeId) topicId, this, credentials)){}
   } 

   /** - IScribeApp Implementation -- */
   public void faultHandler(ScribeMessage msg, NodeHandle faultyParent){}
   public void forwardHandler(ScribeMessage msg){}
   public void receiveMessage(ScribeMessage msg){
     /* Check the type of message */
     /* then make call accordingly */
     //System.out.println("Recieved message");
   }
   
   public void scribeIsReady(){
       //System.out.println("Scribe is Ready");
       m_ready = true;
       notifyApps();
   }
   
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

    public void registerApp(ISplitStreamApp app){
	m_apps.add(app);
    }

    public void notifyApps(){
	Iterator it = m_apps.iterator();
	
	while(it.hasNext()){
	    ISplitStreamApp app = (ISplitStreamApp)it.next();
	    app.splitstreamIsReady();
	}
    }

} 

