/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved. 

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/


package rice.scribe.messaging;

import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.scribe.*;
import rice.scribe.security.*;
import rice.scribe.maintenance.*;

import java.io.*;
import java.util.*;
/**
 * @(#) MessageAckOnSubscribe.java
 *
 * MessageAckOnSubscribe is used to modify the ackOnSubscribeSwitch for
 * a given topic on the destination node, so that whenever a new 
 * subscriber joins the multicast tree for that topic,
 * an immediate ACK is sent to it in the form of
 * MessageAckOnSubscribe type message. 
 *
 * There are two functions of this message :
 * 1) Sets the parent pointer for given topic on destination node.
 * 2) Sets the ackOnSubscribeSwitch for given topic on destination node
 * 
 * @version $Id$ 
 * 
 * @author Animesh Nandi
 * @author Atul Singh
 */


public class MessageAckOnSubscribe extends ScribeMessage implements Serializable
{
    /**
     * Constructor
     *
     * @param addr the address of the scribe receiver.
     * @param source the node generating the message.
     * @param tid the topic to which this message refers to.
     * @param c the credentials associated with the mesasge.
     */
    public 
	MessageAckOnSubscribe( Address addr, NodeHandle source, 
			  NodeId tid, Credentials c ) {
	super( addr, source, tid, c );
    }
    
    /**
     * This method is called whenever the scribe node receives a message for 
     * itself and wants to process it. The processing is delegated by scribe 
     * to the message.
     * 
     * @param scribe the scribe application.
     * @param topic the topic within the scribe application.
     */
    public void 
	handleDeliverMessage( Scribe scribe, Topic topic ) {
	// This message was send for us.
	NodeId topicId = m_topicId;
	Credentials cred = scribe.getCredentials();
	SendOptions opt = scribe.getSendOptions();
	NodeHandle prev_parent = topic.getParent();

	if( topic == null ){
	    ScribeMessage msg = scribe.makeUnsubscribeMessage( m_topicId, cred );
	    scribe.routeMsgDirect( m_source, msg, cred, opt );
	    return;
	}

	if( prev_parent != null && prev_parent != m_source){
	    // If we had non-null previous parent and it is different
	    // from new parent, then we send an unsubscribe message
	    // to prev parent for this topic.
	    ScribeMessage msg = scribe.makeUnsubscribeMessage( m_topicId, cred );
	    scribe.routeMsgDirect( prev_parent, msg, cred, opt );
	    
	}

	topic.setParent(m_source);
	topic.postponeParentHandler();

	// if waiting to find parent, now send unsubscription msg
	if ( topic.isWaitingUnsubscribe() ) {
	    scribe.unsubscribe( topic.getTopicId(), null, cred );
	    topic.waitUnsubscribe( false );
	    return;
	}
    }
    
    /**
     * This method is called whenever the scribe node forwards a message in 
     * the scribe network. The processing is delegated by scribe to the 
     * message.
     * 
     * @param scribe the scribe application.
     * @param topic the topic within the scribe application.
     *
     * @return true if the message should be routed further, false otherwise.
     */
    public boolean 
	handleForwardMessage(Scribe scribe, Topic topic ) {
	return true;
    }

    public String toString() {
	return new String( "ACK_ON_SUBSCRIBE MSG:" + m_source );
    }
}

