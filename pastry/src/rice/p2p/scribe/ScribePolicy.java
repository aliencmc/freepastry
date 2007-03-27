/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 

package rice.p2p.scribe;

import java.util.*;

import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.scribe.messaging.*;

/**
 * @(#) ScribePolicy.java This interface represents a policy for Scribe, which is asked whenever a
 * child is about to be added or removed, or when the the local node is about to be implicitly
 * subscribed to a new topic.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public interface ScribePolicy {

  /**
   * This method is called when the newChild is about to become our child, and the policy should
   * return whether or not the child should be allowed to become our child. If the length of
   * children and clients is both 0, allowing the child to join will have the effect of implicitly
   * subscribing this node the the given topic.
   *
   * For each Topic that you are willing to accept, call message.accept(Topic);
   * 
   * Here is some example code:
   * 
   * <pre>
   * for (Topic topic : new ArrayList<Topic>(message.getTopics())) {
   *   message.accept(topic);
   * }
   * </pre>
   *
   * Some calls that are likely useful are: 
   * <ul> 
   *   <li>scribe.getChildren(topic)</li>
   *   <li>scribe.getClients(topic)</li>
   * </ul>
   *
   * @param message The subscribe message in question
   * @param children The list of children who are currently subscribed to this topic
   * @param clients The list of clients are are currently subscribed to this topic
   */
  public List<Topic> allowSubscribe(Scribe scribe, NodeHandle source, List<Topic> topics, ScribeContent content);

  /**
   * This method is called when an anycast is received which is not satisfied at the local node.
   * This method should add both the parent and child nodes to the anycast's to-search list, but
   * this method allows different policies concerning the order of the adding as well as selectively
   * adding nodes.
   *
   * @param message The anycast message in question
   * @param parent Our current parent for this message's topic
   * @param children Our current children for this message's topic
   */
  public void directAnycast(AnycastMessage message, NodeHandle parent, Collection<NodeHandle> children);
  
  /**
   * Informs this policy that a child was added to a topic - the topic is free to ignore this
   * upcall if it doesn't care.
   *
   * @param topic The topic to unsubscribe from
   * @param child The child that was added
   */
  public void childAdded(Topic topic, NodeHandle child);
  
  /**
   * Informs this policy that a child was removed from a topic - the topic is free to ignore this
   * upcall if it doesn't care.
   *
   * @param topic The topic to unsubscribe from
   * @param child The child that was removed
   */
  public void childRemoved(Topic topic, NodeHandle child);
  
  /**
   * This notifies us when we receive a failure for a anycast
   */
  public void recvAnycastFail(Topic topic, NodeHandle failedAtNode, ScribeContent content);

  /**
   * This is invoked whenever this message arrives on any overlay node, this gives the ScribeClient's power
   * to tap into some datastructures they might wanna edit
   */
  public void intermediateNode(ScribeMessage message);

  /**
   * The default policy for Scribe, which always allows new children to join and adds children in
   * the order in which they are provided, implicitly providing a depth-first search.
   *
   * @version $Id$
   * @author amislove
   */
  public static class DefaultScribePolicy implements ScribePolicy {
    protected Environment environment;
    public DefaultScribePolicy(Environment env) {
      environment = env;
    }
    /**
     * If you don't override the deprecated allowSubscribe(), This method always return true;
     *
     * @param message The subscribe message in question
     * @param children The list of children who are currently subscribed
     * @param clients The list of clients are are currently subscribed
     * @return the topics to accept
     */
    public List<Topic> allowSubscribe(Scribe scribe, NodeHandle source, List<Topic> topics, ScribeContent content) {
      Iterator<Topic> i = topics.iterator();
      while(i.hasNext()) {
        Topic topic = i.next();        
        if (!allowSubscribe(
            new BogusSubscribeMessage(source, topic, 0, content),
            scribe.getClients(topic).toArray(new ScribeClient[0]), 
            scribe.getChildrenOfTopic(topic).toArray(new NodeHandle[0]))) {
          i.remove();
        }
      }
      return topics;
    }

    class BogusSubscribeMessage extends SubscribeMessage {
      ScribeContent theContent;
      
      public BogusSubscribeMessage(NodeHandle source, Topic topic, int i, ScribeContent content) {
        super(source, topic, i, null);
        theContent = content;
      }
      
      
      public ScribeContent getContent() {
        return theContent;
      }
    }
    
    /**
     * This method should be deprecated, but is here for reverse compatibility.  We changed the 
     * allowSubscribe() method, but classes that extend DefaultScribePolicy object could make a mistake
     * and not override the new one.
     * 
     * @param message
     * @param clients
     * @param children
     * @return
     */
    public boolean allowSubscribe(SubscribeMessage message, ScribeClient[] clients, NodeHandle[] children) {
      return true;
    }
    
    /**
     * Simply adds the parent and children in order, which implements a depth-first-search.
     *
     * randomly picks a child
     *
     * @param message The anycast message in question
     * @param parent Our current parent for this message's topic
     * @param children Our current children for this message's topic
     */
    public void directAnycast(AnycastMessage message, NodeHandle parent, Collection<NodeHandle> theChildren) {
      if (parent != null) {
        message.addLast(parent);
      }
      
      ArrayList<NodeHandle> children = new ArrayList<NodeHandle>(theChildren);
      
      // now randomize the children list
      while (!children.isEmpty()) {
        message.addFirst(
            children.remove(
                environment.getRandomSource().nextInt(
                    children.size())));
      }
    }
    
    
    /**
     * Informs this policy that a child was added to a topic - the topic is free to ignore this
     * upcall if it doesn't care.
     *
     * @param topic The topic to unsubscribe from
     * @param child The child that was added
     */
    public void childAdded(Topic topic, NodeHandle child) {
    }
    
    /**
     * Informs this policy that a child was removed from a topic - the topic is free to ignore this
     * upcall if it doesn't care.
     *
     * @param topic The topic to unsubscribe from
     * @param child The child that was removed
     */
    public void childRemoved(Topic topic, NodeHandle child) {
    }
    public void intermediateNode(ScribeMessage message) {
    }
    public void recvAnycastFail(Topic topic, NodeHandle failedAtNode, ScribeContent content) {
    }
  }

  /**
   * An optional policy for Scribe, which allows up to a specified number of children per topic.
   *
   * @version $Id$
   * @author amislove
   */
  public static class LimitedScribePolicy extends DefaultScribePolicy {

    /**
     * The number of children to allow per topic
     */
    protected int maxChildren;

    /**
     * Construtor which takes a maximum number
     *
     * @param max The maximum number of children
     */
    public LimitedScribePolicy(int max, Environment env) {
      super(env);
      this.maxChildren = max;
    }

    /**
     * This method returns (children.length < maxChildren-1);
     *
     * @param message The subscribe message in question
     * @param children The list of children who are currently subscribed
     * @param clients The list of clients are are currently subscribed
     * @return True.
     */
    public boolean allowSubscribe(SubscribeMessage message, ScribeClient[] clients, NodeHandle[] children) {
      return (children.length < (maxChildren - 1));
    }
  }
}

