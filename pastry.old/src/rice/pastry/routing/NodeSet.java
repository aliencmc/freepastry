//////////////////////////////////////////////////////////////////////////////
// Rice Open Source Pastry Implementation                  //               //
//                                                         //  R I C E      //
// Copyright (c)                                           //               //
// Romer Gil                   rgil@cs.rice.edu            //   UNIVERSITY  //
// Andrew Ladd                 aladd@cs.rice.edu           //               //
// Tsuen Wan Ngan              twngan@cs.rice.edu          ///////////////////
//                                                                          //
// This program is free software; you can redistribute it and/or            //
// modify it under the terms of the GNU General Public License              //
// as published by the Free Software Foundation; either version 2           //
// of the License, or (at your option) any later version.                   //
//                                                                          //
// This program is distributed in the hope that it will be useful,          //
// but WITHOUT ANY WARRANTY; without even the implied warranty of           //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            //
// GNU General Public License for more details.                             //
//                                                                          //
// You should have received a copy of the GNU General Public License        //
// along with this program; if not, write to the Free Software              //
// Foundation, Inc., 59 Temple Place - Suite 330,                           //
// Boston, MA  02111-1307, USA.                                             //
//                                                                          //
// This license has been added in concordance with the developer rights     //
// for non-commercial and research distribution granted by Rice University  //
// software and patent policy 333-99.  This notice may not be removed.      //
//////////////////////////////////////////////////////////////////////////////

package rice.pastry.routing;

import rice.pastry.*;

import java.util.*;

public interface NodeSet {
    /**
     * Checks if the set is read-only.
     *
     * @return true if the set is read-only, false otherwise.
     */

    public boolean isReadOnly();

    /**
     * Puts an entry into a node set.
     * 
     * @param handle the handle to put into the table.
     */

    public void putNode(NodeHandle handle);

    /**
     * Checks if a given node id is in the node set.
     *
     * @param nid a node id.
     *
     * @return true if the node id is in the set, false otherwise.
     */

    public NodeHandle findNode(NodeId nid);

    /**
     * Removes an entry from a node set.
     *
     * @param nid the node id to remove from the set.
     *
     * @return the handle that was removed, null if nothing was removed.
     */

    public NodeHandle removeNode(NodeId nid);

    /**
     * Finds the closest node in the set (in the sense of proximity).
     *
     * @return the closest node.
     */

    public NodeHandle findClosestNode();

    /**
     * Finds the node with the most similar node id.
     *
     * @param nid a node.
     *
     * @return the most similar node.
     */

    public NodeHandle findMostSimilarNode(NodeId nid);
}








