package rice.splitstream;
import rice.scribe.*;
import rice.scribe.messaging.*;
import rice.pastry.*;
import rice.pastry.standard.*;
import rice.pastry.security.*;
import java.io.*; 
import java.util.Observable;
/**
 * This class encapsulates all data about an individual stripe.
 */
public class Stripe extends Observable implements IScribeApp{
   /**
    * The stripe state, wheter it is dropped, connected, etc.
    */
   private int stripeState = 0;
   /**
    * The stripeId for this stripe
    */
   private StripeId stripeId = null;
   /**
    * The channel this stripe is a part of.
    */
   private Channel channel =  null;
   /**
    * The input stream used to get data from this stripe.
    */
   private InputStream inputStream = null;
   /**
    * The output stream used to give date to this stripe.
    */
   private OutputStream outputStream = null;
   /**
    * The credentials for this node. Not currently used.
    * Here as a place holder.
    */
   private Credentials credentials = null;
   /** 
    * The scribe instance used by this stripe.
    */
   private IScribe scribe = null;
   /**
    * A flag wheter or not this stripe is the primary stripe for this node
    */
   private boolean isPrimary = false;  
 
   /**
    * The constructor used when creating a stripe from scratch.
    */
   public Stripe(Channel channel, IScribe scribe, Credentials credentials){
      this.scribe = scribe;
      this.credentials = credentials;
      this.channel = channel;

      NodeId topicId = (new RandomNodeIdFactory()).generateNodeId();
      if(scribe.create(topicId, credentials)){
	stripeState = STRIPE_SUBSCRIBED;
        this.stripeId = (StripeId) topicId;
      }
    }
    /**
     * The constructor used to join a stripe when you know the 
     * StripeId
     */
    public Stripe(StripeId stripeId, Channel channel, IScribe scribe, 
             	  Credentials credentials){

    }
    /**
    * gets the StripeID for this stripe
    * @return theStripeID 
    */
    public StripeId getStripeId(){
     return stripeId;
    }
    /**
    * gets the Channel that this stripe is a part of 
    * @return Channel Object 
    */
    public Channel getChannel(){
      return channel;
    }
    /**
     * Gets the OutputStream for this stripe.
     * @return StripeOutputStream for this stripe
     */
    public OutputStream getOutputStream(){
      return outputStream;
    }
    /**
     * Gets the InputStream for this Stripe.
     * @return StripeInputStream for this Stripe
     */
     public InputStream getInputStream(){
       return inputStream;
     }
     /**
      * Leaves this stripe
      */
     public void leaveStripe(){
       stripeState = STRIPE_UNSUBSCRIBED;
     }
    /**
    * get the state of the Stripe 
    * @return int s
    */
    public int getState(){ 
       return stripeState;
    }
    /**
     * Scribe Implementation Methods
     */

     public void faultHandler(ScribeMessage msg, NodeHandle faultyParent){}
     public void forwardHandler(ScribeMessage msg){}
     public void receiveMessage(ScribeMessage msg){
       setChanged();
       notifyObservers(); 
       /* Check the type of message */
       /* then make call accordingly */
     }
     public void scribeIsReady(){}
     public void subscribeHandler(ScribeMessage msg, NodeId topicId, 
                                  NodeHandle child, boolean wasAdded){
          /* We should check if we can take this child on */
     }

    public static final int STRIPE_SUBSCRIBED = 0;
    public static final int STRIPE_UNSUBSCRIBED = 1;
    public static final int STRIPE_DROPPED = 2;
}
