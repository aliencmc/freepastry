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

package rice.util;

import java.util.*;

/**
 * A collection of elements which contains the largest elements placed into it.
 *
 * @author Andrew Ladd
 */

public class BoundedHeap extends Heap
{
    private int maxElements;

    /**
     * Constructor.
     *
     * @param maxElts the maximum number of elements allowed in the heap.
     */

    public BoundedHeap(int maxElts) 
    {
	super();

	maxElements = maxElts;	
    }

    /**
     * Constructor.
     *
     * @param maxElts the maxmimum number of elements allowed in the heap.
     * @param cmp a Comparator which orders the elements which will go into the heap.
     */
    
    public BoundedHeap(int maxElts, Comparator cmp)
    {
	super(cmp);
	
	maxElements = maxElts;
    }

    /**
     * Changes the maximum element count.
     *
     * @param newMax the new maximum count.
     */
    
    public void modifyMaximumElements(int newMax) 
    {
	while (size() > newMax) extract();
    }

    /**
     * Inserts an element into the heap.
     *
     * @param elt the element to insert into the heap.
     *
     * @return the interface to the element.
     */
    
    public HeapElementInterface put(Object elt)
    {
	HeapElementInterface hei = super.put(elt);
	
	if (size() > maxElements) extract();

	return hei;
    }    
}







