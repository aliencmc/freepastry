/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

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

package rice.pastry.direct;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;
import rice.pastry.standard.*;
import rice.pastry.routing.*;
import rice.pastry.leafset.*;

import java.util.*;

/**
 * Pastry node factory for direct connections between nodes (local instances).
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 * @author Sitaram Iyer
 */

public class DirectPastryNodeFactory implements PastryNodeFactory
{
    private RandomNodeIdFactory nidFactory;
    private NetworkSimulator simulator;

    private static final int rtMax = 8;
    private static final int lSetSize = 24;
  
    /**
     * Large value means infrequent, 0 means never
     */
    private static final int leafSetMaintFreq = 1000;
    private static final int routeSetMaintFreq = 3000;

    public DirectPastryNodeFactory() {
	nidFactory = new RandomNodeIdFactory();
//	simulator = new EuclideanNetwork();
	simulator = new SphereNetwork();
    }

    public NetworkSimulator getNetworkSimulator() { return simulator; }
    
    /**
     * Manufacture a new Pastry node.
     *
     * @return a new PastryNode
     */
    public PastryNode newNode(NodeHandle bootstrap) {

	NodeId nodeId = nidFactory.generateNodeId();
	DirectPastryNode pn = new DirectPastryNode(nodeId);
	
	NodeHandle localhandle = new DirectNodeHandle(pn, pn, simulator);

	DirectSecurityManager secureMan = new DirectSecurityManager(simulator);
	MessageDispatch msgDisp = new MessageDispatch();

	RoutingTable routeTable = new RoutingTable(localhandle, rtMax);
	LeafSet leafSet = new LeafSet(localhandle, lSetSize);
		
	StandardRouter router =
	    new StandardRouter(localhandle, routeTable, leafSet);
	StandardLeafSetProtocol lsProtocol =
	    new StandardLeafSetProtocol(localhandle, secureMan, leafSet, routeTable);
	StandardRouteSetProtocol rsProtocol =
	    new StandardRouteSetProtocol(localhandle, secureMan, routeTable);
	StandardJoinProtocol jProtocol =
	    new StandardJoinProtocol(localhandle, secureMan, routeTable, leafSet);

	simulator.registerNodeId(nodeId);

	msgDisp.registerReceiver(router.getAddress(), router);
	msgDisp.registerReceiver(lsProtocol.getAddress(), lsProtocol);
	msgDisp.registerReceiver(rsProtocol.getAddress(), rsProtocol);
	msgDisp.registerReceiver(jProtocol.getAddress(), jProtocol);

	pn.setElements(localhandle, secureMan, msgDisp, leafSet, routeTable,
		       leafSetMaintFreq, routeSetMaintFreq);
	pn.setDirectElements(/* simulator */);
	secureMan.setLocalPastryNode(pn);

	pn.doneNode(bootstrap);

	return pn;
    }
}
