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


package rice.rm;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.security.*;

/**
 * @(#) RMClient.java
 *
 * This interface should be implemented by all applications that interact
 * with the Replica Manager.
 *
 * @version $Id$
 *
 * @author Animesh Nandi
 */
public interface RMClient {

    /**
     * This upcall is invoked to notify the application that is should
     * fetch the cooresponding keys in this set, since the node is now
     * responsible for these keys also.
     * @param keySet set containing the keys that needs to be fetched
     */
    public void fetch(IdSet keySet);



    /**
     * This upcall is simply to denote that the underlying replica manager
     * (rm) is ready. The 'rm' should henceforth be used by this RMClient
     * to issue the downcalls on the RM interface.
     * @param rm the instance of the Replica Manager
     */
    public void rmIsReady(RM rm);



    /**
     * This upcall is to notify the application of the range of keys for 
     * which it is responsible. The application might choose to react to 
     * call by calling a scan(complement of this range) to the persistance
     * manager and get the keys for which it is not responsible and
     * call delete on the persistance manager for those objects.
     * @param range the range of keys for which the local node is currently 
     *              responsible  
     */
    public void isResponsible(IdRange range);



    /**
     * This upcall should return the set of keys that the application
     * currently stores in this range. Should return a empty IdSet (not null),
     * in the case that no keys belong to this range.
     * @param range the requested range
     */
    public IdSet scan(IdRange range);

}















































































