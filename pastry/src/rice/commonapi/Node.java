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

- Neither the name of Rice University (RICE) nor the names of its
contributors may be used to endorse or promote products derived from
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

package rice.commonapi;

/**
 * Interface which represents a node in a peer-to-peer system, regardless of
 * the underlying protocol.  This represents the *local* node, upon which applications
 * can call methods.  
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public interface Node {

  /**
   * Routes the message is routed directly to the given node, and delivers the message
   * there.  This has the same effect as route(hint.getId(), message), but may be more
   * efficient.
   *
   * @param message The message to deliver
   * @param hint The destination node.
   */
  void route(Message message, NodeHandle hint);
  
  /**
   * This method makes an attempt to route the message to the root of the given id.
   *
   * @param id The destination Id of the message.
   * @param message The message to deliver
   */
  void route(Id id, Message message);  
  
  /**
   * This method makes an attempt to route the message to the root of the given id.
   * The hint handle will be the first hop in the route.
   *
   * @param id The destination Id of the message.
   * @param message The message to deliver
   */
  void route(Id id, Message message, NodeHandle hint);

  /**
   * This call produces a list of nodes that can be used as next hops on a route towards
   * the given id, such that the resulting route satisfies the overlay protocol's bounds
   * on the number of hops taken.  If the safe flag is specified, then the fraction of
   * faulty nodes returned is no higher than the fraction of faulty nodes in the overlay.
   *
   * @param id The destination id.
   * @param num The number of nodes to return.
   * @param safe Whether or not to return safe nodes.
   */
  NodeHandle[] localLookup(Id id, int num, boolean safe);

  /**
   * This methods returns an ordered set of nodehandles on which replicas of an object with
   * a given id can be stored.  The call returns nodes up to and including a node with maxRank.
   *
   * @param id The object's id.
   * @param maxRank The number of desired replicas.
   */
  NodeHandle[] replicaSet(Id id, int maxRank);

  /**
   * This operation provides information aboutranges of keys for which the node is currently
   * a rank-root. The operations returns null if the range could not be determined, the range
   * otherwise. It is an error to query the range of a node not present in the neighbor set as
   * returned bythe update upcall or the neighborSet call. Certain implementations may return
   * an error if rank is greater than zero. Some protocols may have multiple, disjoint ranges
   * of keys for which a given node is responsible. The parameter lkey allows the caller to
   * specify which region should be returned. If the node referenced by is responsible for key
   * lkey, then the resulting range includes lkey. Otherwise, the result is the nearest range
   * clockwise from lkey for which is responsible.
   *
   * @param handle The handle whose range to check.
   * @param rank The root rank.
   * @param lkey An "index" in case of multiple ranges.
   */
  IdRange range(NodeHandle handle, int rank, Id lkey);
}




