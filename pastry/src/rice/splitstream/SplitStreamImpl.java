package rice.splitstream;
import rice.scribe.*;
import rice.splitstream.messaging.*;
import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
/*
 * @(#) SplitStream.java
 *
 */

/**
 * This is the implementing class of the SplitStream service 
 */
public class SplitStreamImpl extends PastryAppl implements ISplitStream{
    private IScribe scribe = null;
    private Credentials credentials = new PermissiveCredentials();
    public SplitStreamImpl(PastryNode node, IScribe scribe){
        super(node);
 	scribe = this.scribe;   
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
   public Channel createChannel(int numStripes){
	return (new Channel(numStripes, scribe, getCredentials()));
   }
   /**
    * This method is used by peers who wish to listen to content distributed 
    * by some other peer using SplitStream. It attaches the local node to the 
    * Channel which is being used by the source peer to distribute the content.
    * Essentially, this method finds out the different parameters of Channel
    * object which is created by the source, (the peer distributing the content)
    *, and then creates a local Channel object with these parameters and
    * returns it.  
    * This is a non-blocking call so the returned Channel object may not be 
    * initialized with all the parameters, so applications should wait for 
    * channelIsReady() notification made by channels when they are ready. 
    * @param channelId - Identifier of channel to which local node wishes
    *  to attach to. 
    * @return  An instance of Channel object.
    */
   public Channel attachChannel(ChannelId channelId){return null;}
   /**
    * Handles the upcall generated by Scribe when a new topicID is 
    * created. SplitStream can generate a channel as appropriate.
    * @param The channelId for the created channel. 
    * This was designed per suggestion by Atuhl, he is going to 
    * add the appropriate hooks into Scribe.
    */
   public void handleSubscribe(ChannelId channelId){}
   public void messageForAppl(Message msg){}
   public Credentials getCredentials(){return credentials;}
   public Address getAddress(){return null;}
   public SplitStreamImpl(){super(null);}
   public BandwidthManager getBandwidthManager(){return null;}
   public void setBandwidthManager(){}
}
