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

package rice.scribe;

import rice.pastry.NodeHandle;
import rice.scribe.messaging.ScribeMessage;

/**
 * @(#) IScribeApp.java
 *
 * This Interface must be implemented by all applications using Scribe. The 
 * methods in this interface are called by Scribe whenever a particular event
 * happens in the scribe application layer.
 *
 * @version $Id$
 *
 * @author Romer Gil
 * @author Eric Engineer
 * @author Atul Singh
 * @author Animesh Nandi
 */

public interface IScribeApp
{

    /**
     * Invoked when the underlying Scribe substrate is ready. 
     * The Scribe substrate becomes ready as soon as the local Pastry node
     * on which it lies is ready. In order to get this upcall it is 
     * necessary that the IScribeApp registers itself to the Scribe 
     * substrate using the registerApp() method in IScribe interface.
     */
    public void scribeIsReady();
	
    /** 
     * Called by Scribe when a multicast message arrives.
     * 
     * @param msg 
     * The message sent in the PUBLISH message.
     */
    public void receiveMessage( ScribeMessage msg );
    

    /**
     * Called by Scribe before the node forwards a message to
     * its children in the multicast tree.
     *
     * @param msg 
     * The message about to be forwarded.
     */
    public void forwardHandler( ScribeMessage msg );
    

    /**
     * Invoked by Scribe after a new child is added to one of
     * the node's children tables.
     *
     * @param msg 
     * The SUBSCRIBE message from the new child.
     */
    public void subscribeHandler( ScribeMessage msg );
    

    /**
     * Invoked by Scribe just before the "repair" SUBSCRIBE message is sent
     * when a node suspects its parent is faulty.
     *
     * @param msg 
     * The SUBSCRIBE message that is sent to repair the multicast tree.
     *
     * @param faultyParent
     * The suspected faulty parent.
     */
    public void faultHandler( ScribeMessage msg, NodeHandle faultyParent );
    
}




