/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.numbering;

import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;

import java.io.IOException;

/**
 * Represents a node id in the form of a dynamic level number (DLN). DLN's are
 * hierarchical ids, which borrow from Dewey's decimal classification. Examples for
 * node ids: 1, 1.1, 1.2, 1.2.1, 1.2.2, 1.3. In this case, 1 represents the root node, 1.1 is
 * the first node on the second level, 1.2 the second, and so on.
 * 
 * To support efficient insertion of new nodes between existing nodes, we use the
 * concept of sublevel ids. Between two nodes 1.1 and 1.2, a new node can be inserted
 * as 1.1/1, where the / is the sublevel separator. The / does not start a new level. 1.1 and 
 * 1.1/1 are thus on the same level of the tree.
 * 
 * In the binary encoding, the '.' is represented by a 0-bit while '/' is written as a 1-bit.
 */
public class DLN extends DLNBase implements NodeId {

	/**
	 * Constructs a new DLN with a single id with value 1.
	 *
	 */
    public DLN() {
        this(1);
    }

    /**
     * Constructs a new DLN by parsing the string argument.
     * In the string, levels are separated by a '.', sublevels by
     * a '/'. For example, '1.2/1' or '1.2/1.2' are valid ids.
     * 
     * @param s
     */
    public DLN(String s) {
        bits = new byte[1];
        StringBuffer buf = new StringBuffer(16);
        boolean subValue = false;
        for (int p = 0; p < s.length(); p++) {
            char ch = s.charAt(p);
            if (ch == '.' || ch == '/') {
                addLevelId(Integer.parseInt(buf.toString()), subValue);
                subValue = ch == '/';
                buf.setLength(0);
            } else
                buf.append(ch);
        }
        if (buf.length() > 0) {
            addLevelId(Integer.parseInt(buf.toString()), subValue);
        }
    }
    
    /**
     * Constructs a new DLN, using the passed id as its
     * single level value.
     * 
     * @param id
     */
    public DLN(int id) {
        bits = new byte[1];
        addLevelId(id, false);
    }

    /**
     * Constructs a new DLN by copying the data of the
     * passed DLN.
     * 
     * @param other
     */
    public DLN(DLN other) {
        super(other);
    }

    /**
     * Reads a DLN from the given byte[].
     * 
     * @param units number of bits to read
     * @param data the byte[] to read from
     * @param startOffset the start offset to start reading at
     */
    public DLN(int units, byte[] data, int startOffset) {
        super(units, data, startOffset);
    }

    /**
     * Reads a DLN from the given {@link VariableByteInput} stream.
     * 
     * @see #write(VariableByteOutputStream)
     * @param is
     * @throws IOException
     */
    public DLN(VariableByteInput is) throws IOException {
        super(is);
    }

    /**
     * Create a new DLN by copying nbits bits from the given 
     * byte[].
     * 
     * @param data
     * @param nbits
     */
    protected DLN(byte[] data, int nbits) {
        super(data, nbits);
    }

    /**
     * Returns a new DLN representing the first child
     * node of this node.
     *
     * @return new child node id
     */
    public NodeId newChild() {
        DLN child = new DLN(this);
        child.addLevelId(1, false);
        return child;
    }

    /**
     * Returns a new DLN representing the next following
     * sibling of this node.
     *
     * @return new sibling node id.
     */
    public NodeId nextSibling() {
        DLN sibling = new DLN(this);
        sibling.incrementLevelId();
        return sibling;
    }
    
    public NodeId insertNode(NodeId right) {
        DLN rightNode = (DLN) right;
        if (right == null)
            return nextSibling();
        int lastLeft = lastLevelOffset();
        int lastRight = rightNode.lastLevelOffset();
        int lenLeft = getSubLevelCount(lastLeft);
        int lenRight = rightNode.getSubLevelCount(lastRight);
        DLN newNode;
        if (lenLeft > lenRight) {
            newNode = new DLN(this);
            newNode.incrementLevelId();
        } else if (lenLeft < lenRight) {
            newNode = (DLN) rightNode.insertBefore(); 
        } else {
            newNode = new DLN(this);
            newNode.addLevelId(1, true);
        }
        return newNode;
    }
    
    public NodeId insertBefore() {
        int lastPos = lastFieldPosition();
        int lastId = getLevelId(lastPos);
        DLN newNode = new DLN(this);
//        System.out.println("insertBefore: " + newNode.toString() + " = " + newNode.bitIndex);
        if (lastId == 1) {
            newNode.setLevelId(lastPos, 0);
            newNode.addLevelId(35, true);
        } else {
            newNode.setLevelId(lastPos, lastId - 1);
            newNode.compact();
//            System.out.println("newNode: " + newNode.toString() + " = " + newNode.bitIndex + "; last = " + lastPos);
        }
        return newNode;
    }
    
    /**
     * Returns a new DLN representing the parent of the
     * current node. If the current node is the root element
     * of the document, the method returns 
     * {@link NodeId#DOCUMENT_NODE}. If the current node
     * is the document node, null is returned.
     * 
     * @see NodeId#getParentId()
     */
    public NodeId getParentId() {
        if (this == DOCUMENT_NODE)
            return null;
        int last = lastLevelOffset();
        if (last == 0)
            return DOCUMENT_NODE;
        return new DLN(bits, last - 1);
    }

    public boolean isDescendantOf(NodeId ancestor) {
    	DLN other = (DLN) ancestor;
    	return startsWith(other) && bitIndex > other.bitIndex;
    }

    public boolean isDescendantOrSelfOf(NodeId ancestor) {
        return startsWith((DLN) ancestor);
    }

    public boolean isChildOf(NodeId parent) {
    	DLN other = (DLN) parent;
    	if(!startsWith(other))
    		return false;
    	int levels = getLevelCount(other.bitIndex + 2);
    	return levels == 1;
    }

    public int isDescendantOrChildOf(NodeId ancestor) {
        DLN other = (DLN) ancestor;
        if (startsWith(other) && bitIndex > other.bitIndex) {
            if (getLevelCount(other.bitIndex + 2) == 1)
                return IS_CHILD;
            return IS_DESCENDANT;
        }
        return -1;
    }
    
    public int isSiblingOf(NodeId sibling) {
        DLN other = (DLN) sibling;
        int last = lastLevelOffset();
        if (last == other.lastLevelOffset())
            return compareBits(other, last);
        else
            return compareTo(other);
    }
    
    /**
     * Returns the level within the document tree at which
     * this node occurs.
     *
     * @return
     */
    public int getTreeLevel() {
        return getLevelCount(0);
    }

    public boolean equals(NodeId other) {
        return super.equals((DLNBase) other);
    }

    public int compareTo(Object other) {
        return compareTo((DLN) other);
    }
    
    public int compareTo(NodeId otherId) {
        if (otherId == null)
            return 1;
        
        final DLN other = (DLN) otherId;
        final int a1len = bits.length;
        final int a2len = other.bits.length;

        int limit = a1len <= a2len ? a1len : a2len;
        byte[] obits = other.bits;
        for (int i = 0; i < limit; i++) {
            byte b1 = bits[i];
            byte b2 = obits[i];
            if (b1 != b2)
                return (b1 & 0xFF) - (b2 & 0xFF);
        }
        return (a1len - a2len);
    }

    public boolean after(NodeId other, boolean isFollowing) {
        if (compareTo(other) > 0) {
            if (isFollowing)
                return !isDescendantOf(other);
            else
                return true;
        }
        return false;
    }
    
    public boolean before(NodeId other, boolean isPreceding) {
        if (compareTo(other) < 0) {
            if (isPreceding)
                return !other.isDescendantOf(this);
            else
                return true;
        }
        return false;
    }
    
    /**
     * Write the node id to a {@link VariableByteOutputStream}.
     *
     * @param os
     * @throws IOException
     */
    public void write(VariableByteOutputStream os) throws IOException {
        os.writeShort((short) units());
        os.write(bits, 0, bits.length);
    }

    public static void main(String[] args) {
        DLN id0 = new DLN("1.1/0/0/7");
        System.out.println(id0.size());
        
        DLN id1 = new DLN("1.1");
        DLN id = (DLN) id1.insertNode(id0);
        System.out.println(id.debug());
        System.out.println(id.size());
        System.out.println(id.units());
        
        byte[] data = new byte[id.size() + 1];
        data[0] = (byte) id.units();
        id.serialize(data, 1);
        
        
        DLN dln = new DLN(data[0], data, 1);
        System.out.println(dln.debug());
        System.out.println(dln.size());
        System.out.println(dln.units());
    }
}
