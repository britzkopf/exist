/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.dom.memtree;

import org.apache.xml.utils.XMLChar;
import org.apache.xml.utils.XML11Char;
import org.exist.collections.Collection;
import org.exist.dom.INode;
import org.exist.dom.NodeListImpl;
import org.exist.dom.QName;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.EmptyNodeSet;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeSet;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.Receiver;
import org.exist.xquery.Constants;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.Cardinality;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.MemoryNodeSet;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.UntypedAtomicValue;
import org.exist.xquery.value.ValueSequence;
import org.xml.sax.ContentHandler;
import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.UserDataHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import javax.xml.XMLConstants;
import java.util.Iterator;
import java.util.Properties;


public abstract class NodeImpl<T extends NodeImpl> implements INode<DocumentImpl, T>, NodeValue {

    public static final short REFERENCE_NODE = 100;
    public static final short NAMESPACE_NODE = 101;

    protected int nodeNumber;
    protected DocumentImpl document;

    public NodeImpl(final DocumentImpl doc, final int nodeNumber) {
        this.document = doc;
        this.nodeNumber = nodeNumber;
    }

    public int getNodeNumber() {
        return nodeNumber;
    }

    @Override
    public int getImplementationType() {
        return NodeValue.IN_MEMORY_NODE;
    }

    @Override
    public DocumentSet getDocumentSet() {
        return DocumentSet.EMPTY_DOCUMENT_SET;
    }

    @Override
    public Iterator<Collection> getCollectionIterator() {
        return EmptyNodeSet.EMPTY_COLLECTION_ITERATOR;
    }

    @Override
    public Node getNode() {
        return this;
    }

    @Override
    public final QName getQName() {
        switch(getNodeType()) {
            case Node.ATTRIBUTE_NODE:
                return document.attrName[nodeNumber];

            case Node.ELEMENT_NODE:
            case Node.PROCESSING_INSTRUCTION_NODE:
                return document.nodeName[nodeNumber];

            case NodeImpl.NAMESPACE_NODE:
                return document.namespaceCode[nodeNumber];

            case Node.DOCUMENT_NODE:
                return QName.EMPTY_QNAME;

            case Node.COMMENT_NODE:
                return QName.EMPTY_QNAME;

            case Node.TEXT_NODE:
                return QName.EMPTY_QNAME;

            case Node.CDATA_SECTION_NODE:
                return QName.EMPTY_QNAME;

            default:
                return null;
        }
    }

    @Override
    public final void setQName(final QName qname) {
        switch(getNodeType()) {
            case Node.ATTRIBUTE_NODE:
                document.attrName[nodeNumber] = qname;
                break;

            case Node.ELEMENT_NODE:
            case Node.PROCESSING_INSTRUCTION_NODE:
                document.nodeName[nodeNumber] = qname;
                break;

            case NodeImpl.NAMESPACE_NODE:
                document.namespaceCode[nodeNumber] = qname;
                break;
        }
    }

    @Override
    public final String getNodeName() {
        switch(getNodeType()) {
            case Node.DOCUMENT_NODE:
                return "#document";

            case Node.DOCUMENT_FRAGMENT_NODE:
                return "#document-fragment";

            case Node.ELEMENT_NODE:
            case Node.ATTRIBUTE_NODE:
            case NAMESPACE_NODE:
                return getQName().getStringValue();

            case Node.PROCESSING_INSTRUCTION_NODE:
                return ((ProcessingInstructionImpl)this).getTarget();

            case Node.TEXT_NODE:
                return "#text";

            case Node.COMMENT_NODE:
                return "#comment";

            case Node.CDATA_SECTION_NODE:
                return "#cdata-section";

            default:
                return "#unknown";
        }
    }

    @Override
    public String getLocalName() {
        switch(getNodeType()) {
            case Node.ELEMENT_NODE:
            case Node.ATTRIBUTE_NODE:
            case NAMESPACE_NODE:
                return getQName().getLocalPart();

            default:
                return null;
        }
    }

    @Override
    public String getNamespaceURI() {
        switch(getNodeType()) {
            case Node.ELEMENT_NODE:
            case Node.ATTRIBUTE_NODE:
                final String nsUri = getQName().getNamespaceURI();
                if(nsUri.equals(XMLConstants.NULL_NS_URI)) {
                    return null;
                } else {
                    return nsUri;
                }

            case NodeImpl.NAMESPACE_NODE:
                return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;

            default:
                return null;
        }
    }

    @Override
    public final String getPrefix() {
        switch(getNodeType()) {
            case Node.ELEMENT_NODE:
            case Node.ATTRIBUTE_NODE:
            case NodeImpl.NAMESPACE_NODE:
                return getQName().getPrefix();

            default:
                return null;
        }
    }

    @Override
    public void setPrefix(final String prefix) throws DOMException {
        if(prefix == null || getNodeType() == Node.DOCUMENT_NODE) {
            return;
        } else if(getOwnerDocument().getXmlVersion().equals("1.0") && !XMLChar.isValidNCName(prefix)) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "Prefix '" + prefix + "' in XML 1.0 contains invalid characters");
        } else if(getOwnerDocument().getXmlVersion().equals("1.1") && !XML11Char.isXML11ValidNCName(prefix)) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "Prefix '" + prefix + "' in XML 1.1 contains invalid characters");
        } else if(getNamespaceURI() == null) {
            throw new DOMException(DOMException.NAMESPACE_ERR, "Cannot set prefix when namespace is null");
        } else if(prefix.equals(XMLConstants.XML_NS_PREFIX) && !getNamespaceURI().equals(XMLConstants.XML_NS_URI)) {
            throw new DOMException(DOMException.NAMESPACE_ERR, "Prefix '" + XMLConstants.XML_NS_PREFIX + "' is invalid for namespace '" + getNamespaceURI() + "'");
        } else if(getNodeType() == Node.ATTRIBUTE_NODE && prefix.equals(XMLConstants.XMLNS_ATTRIBUTE) && !getNamespaceURI().equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
            throw new DOMException(DOMException.NAMESPACE_ERR, "Prefix '" + XMLConstants.XMLNS_ATTRIBUTE + "' is invalid for namespace '" + getNamespaceURI() + "'");
        } else if(getNodeType() == Node.ELEMENT_NODE || getNodeType() == Node.ATTRIBUTE_NODE) {
            final QName qname = getQName();
            setQName(new QName(qname.getLocalPart(), qname.getNamespaceURI(), prefix, qname.getNameType()));
        }
    }

    @Override
    public NodeId getNodeId() {
        expand();
        return document.nodeId[nodeNumber];
    }

    public void expand() throws DOMException {
        document.expand();
    }

    public void deepCopy() throws DOMException {
        final DocumentImpl newDoc = document.expandRefs(this);
        if(newDoc != document) {
            // we received a new document
            this.nodeNumber = 1;
            this.document = newDoc;
        }
    }

    @Override
    public String getNodeValue() throws DOMException {
        return null;
    }

    @Override
    public void setNodeValue(final String nodeValue) throws DOMException {
        throw unsupported();
    }

    @Override
    public short getNodeType() {
        //Workaround for fn:string-length(fn:node-name(document {""}))
        if(this.document == null) {
            return Node.DOCUMENT_NODE;
        }
        return document.nodeKind[nodeNumber];
    }

    @Override
    public Node getParentNode() {
        int next = document.next[nodeNumber];
        while(next > nodeNumber) {
            next = document.next[next];
        }
        if(next < 0) {
            return null;
        }
        return document.getNode(next);
    }

    public Node selectParentNode() {
        // as getParentNode() but doesn't return the document itself
        if(nodeNumber == 0) {
            return null;
        }
        int next = document.next[nodeNumber];
        while(next > nodeNumber) {
            next = document.next[next];
        }
        if(next < 0) { //Is this even possible ?
            return null;
        }
        if(next == 0) {
            return this.document.explicitlyCreated ? this.document : null;
        }
        return document.getNode(next);
    }

    @Override
    public void addContextNode(final int contextId, final NodeValue node) {
        throw unsupported();
    }

    @Override
    public boolean equals(final Object other) {
        if(!(other instanceof NodeImpl)) {
            return false;
        }
        final NodeImpl o = (NodeImpl) other;
        return document == o.document && nodeNumber == o.nodeNumber &&
            getNodeType() == o.getNodeType();
    }

    @Override
    public boolean equals(final NodeValue other) throws XPathException {
        if(other.getImplementationType() != NodeValue.IN_MEMORY_NODE) {
            return false;
        }
        final NodeImpl o = (NodeImpl) other;
        return document == o.document && nodeNumber == o.nodeNumber &&
            getNodeType() == o.getNodeType();
    }

    @Override
    public boolean after(final NodeValue other, final boolean isFollowing) throws XPathException {
        if(other.getImplementationType() != NodeValue.IN_MEMORY_NODE) {
            throw new XPathException("cannot compare persistent node with in-memory node");
        }
        return nodeNumber > ((NodeImpl) other).nodeNumber;
    }

    @Override
    public boolean before(final NodeValue other, final boolean isPreceding) throws XPathException {
        if(other.getImplementationType() != NodeValue.IN_MEMORY_NODE) {
            throw new XPathException("cannot compare persistent node with in-memory node");
        }
        return nodeNumber < ((NodeImpl)other).nodeNumber;
    }

    @Override
    public int compareTo(final NodeImpl other) {
        if(other.document == document) {
            if(nodeNumber == other.nodeNumber && getNodeType() == other.getNodeType()) {
                return Constants.EQUAL;
            } else if(nodeNumber < other.nodeNumber) {
                return Constants.INFERIOR;
            } else {
                return Constants.SUPERIOR;
            }
        } else if(document.docId < other.document.docId) {
            return Constants.INFERIOR;
        } else {
            return Constants.SUPERIOR;
        }
    }

    @Override
    public Sequence tail() throws XPathException {
        return Sequence.EMPTY_SEQUENCE;
    }

    @Override
    public NodeList getChildNodes() {
        return new NodeListImpl();
    }

    @Override
    public Node getFirstChild() {
        return null;
    }

    @Override
    public Node getLastChild() {
        return null;
    }

    @Override
    public Node getPreviousSibling() {
        if(nodeNumber == 0) {
            return null;
        }
        final int parent = document.getParentNodeFor(nodeNumber);
        int nextNode = document.getFirstChildFor(parent);
        while((nextNode >= parent) && (nextNode < nodeNumber)) {
            final int following = document.next[nextNode];
            if(following == nodeNumber) {
                return document.getNode(nextNode);
            }
            nextNode = following;
        }
        return null;
    }

    @Override
    public Node getNextSibling() {
        final int nextNr = document.next[nodeNumber];
        return nextNr < nodeNumber ? null : document.getNode(nextNr);
    }

    @Override
    public NamedNodeMap getAttributes() {
        return null;
    }

    @Override
    public DocumentImpl getOwnerDocument() {
        return document;
    }

    @Override
    public Node insertBefore(final Node newChild, final Node refChild) throws DOMException {
        throw unsupported();
    }

    @Override
    public Node replaceChild(final Node newChild, final Node oldChild) throws DOMException {
        throw unsupported();
    }

    @Override
    public Node removeChild(final Node oldChild) throws DOMException {
        throw unsupported();
    }

    @Override
    public Node appendChild(final Node newChild) throws DOMException {
        if((newChild.getNodeType() == Node.DOCUMENT_NODE && newChild != document) || newChild.getOwnerDocument() != document) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "Owning document IDs do not match");
        }

        throw unsupported();
    }

    @Override
    public boolean hasChildNodes() {
        return false;
    }

    @Override
    public Node cloneNode(final boolean deep) {
        throw unsupported();
    }

    @Override
    public void normalize() {
        // TODO(AR) do we need to implement something here? or is the tree already normalized?
        throw unsupported();
    }

    @Override
    public boolean isSupported(final String feature, final String version) {
        throw unsupported();
    }

    @Override
    public boolean hasAttributes() {
        return false;
    }

    @Override
    public int getType() {
        //Workaround for fn:string-length(fn:node-name(document {""}))
        if(this.document == null) {
            return Type.DOCUMENT;
        }
        switch(getNodeType()) {
            case Node.DOCUMENT_NODE:
                return Type.DOCUMENT;

            case Node.COMMENT_NODE:
                return Type.COMMENT;

            case Node.PROCESSING_INSTRUCTION_NODE:
                return Type.PROCESSING_INSTRUCTION;

            case Node.ELEMENT_NODE:
                return Type.ELEMENT;

            case Node.ATTRIBUTE_NODE:
                return Type.ATTRIBUTE;

            case Node.TEXT_NODE:
                return Type.TEXT;

            case Node.CDATA_SECTION_NODE:
                return Type.CDATA_SECTION;

            default:
                return Type.NODE;
        }
    }

    @Override
    public String getStringValue() {
        final int level = document.treeLevel[nodeNumber];
        int next = nodeNumber + 1;
        int startOffset = 0;
        int len = -1;

        while(next < document.size && document.treeLevel[next] > level) {
            if(
                (document.nodeKind[next] == Node.TEXT_NODE)
                    || (document.nodeKind[next] == Node.CDATA_SECTION_NODE)
                    || (document.nodeKind[next] == Node.PROCESSING_INSTRUCTION_NODE)
                ) {
                if(len < 0) {
                    startOffset = document.alpha[next];
                    len = document.alphaLen[next];
                } else {
                    len += document.alphaLen[next];
                }
            } else {
                return getStringValueSlow();
            }
            ++next;
        }
        return len < 0 ? "" : new String(document.characters, startOffset, len);
    }

    private String getStringValueSlow() {
        final int level = document.treeLevel[nodeNumber];
        StringBuilder buf = null;
        int next = nodeNumber + 1;

        while(next < document.size && document.treeLevel[next] > level) {
            switch(document.nodeKind[next]) {
                case Node.TEXT_NODE:
                case Node.CDATA_SECTION_NODE:
                case Node.PROCESSING_INSTRUCTION_NODE: {
                    if(buf == null) {
                        buf = new StringBuilder();
                    }
                    buf.append(document.characters, document.alpha[next], document.alphaLen[next]);
                    break;
                }
                case REFERENCE_NODE: {
                    if(buf == null) {
                        buf = new StringBuilder();
                    }
                    buf.append(document.references[document.alpha[next]].getStringValue());
                    break;
                }
            }
            ++next;
        }
        return ((buf == null) ? "" : buf.toString());
    }

    @Override
    public Sequence toSequence() {
        return this;
    }

    @Override
    public AtomicValue convertTo(final int requiredType) throws XPathException {
        return UntypedAtomicValue.convertTo(null, getStringValue(), requiredType);
    }

    @Override
    public AtomicValue atomize() throws XPathException {
        return new UntypedAtomicValue(getStringValue());
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean hasOne() {
        return true;
    }

    @Override
    public boolean hasMany() {
        return false;
    }

    @Override
    public void add(final Item item) throws XPathException {
        throw unsupported();
    }

    @Override
    public void addAll(final Sequence other) throws XPathException {
        throw unsupported();
    }

    @Override
    public int getItemType() {
        return Type.NODE;
    }

    @Override
    public SequenceIterator iterate() throws XPathException {
        return new SingleNodeIterator(this);
    }

    @Override
    public SequenceIterator unorderedIterator() {
        return new SingleNodeIterator(this);
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    @Override
    public int getCardinality() {
        return Cardinality.EXACTLY_ONE;
    }

    @Override
    public Item itemAt(final int pos) {
        return pos == 0 ? this : null;
    }

    @Override
    public boolean effectiveBooleanValue() throws XPathException {
        //A node evaluates to true()
        return true;
    }

    @Override
    public NodeSet toNodeSet() throws XPathException {
        final ValueSequence seq = new ValueSequence();
        seq.add(this);
        return seq.toNodeSet();
    }

    @Override
    public MemoryNodeSet toMemNodeSet() throws XPathException {
        return new ValueSequence(this).toMemNodeSet();
    }

    @Override
    public void toSAX(final DBBroker broker, final ContentHandler handler, final Properties properties)
        throws SAXException {
        final Serializer serializer = broker.getSerializer();
        serializer.reset();
        serializer.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");

        if(properties != null) {
            serializer.setProperties(properties);
        }

        if(handler instanceof LexicalHandler) {
            serializer.setSAXHandlers(handler, (LexicalHandler) handler);
        } else {
            serializer.setSAXHandlers(handler, null);
        }

        serializer.toSAX(this);
    }

    @Override
    public void copyTo(final DBBroker broker, final DocumentBuilderReceiver receiver) throws SAXException {
        //Null test for document nodes
        if(document != null) {
            document.copyTo(this, receiver);
        }
    }

    public void streamTo(final Serializer serializer, final Receiver receiver) throws SAXException {
        //Null test for document nodes
        if(document != null) {
            document.streamTo(serializer, this, receiver);
        }
    }

    @Override
    public int conversionPreference(final Class<?> javaClass) {
        final int preference;
        if(javaClass.isAssignableFrom(NodeImpl.class)) {
            preference = 0;
        } else if(javaClass.isAssignableFrom(Node.class)) {
            preference = 1;
        } else if((javaClass == String.class) || (javaClass == CharSequence.class)) {
            preference = 2;
        } else if((javaClass == Character.class) || (javaClass == char.class)) {
            preference = 2;
        } else if((javaClass == Double.class) || (javaClass == double.class)) {
            preference = 10;
        } else if((javaClass == Float.class) || (javaClass == float.class)) {
            preference = 11;
        } else if((javaClass == Long.class) || (javaClass == long.class)) {
            preference = 12;
        } else if((javaClass == Integer.class) || (javaClass == int.class)) {
            preference = 13;
        } else if((javaClass == Short.class) || (javaClass == short.class)) {
            preference = 14;
        } else if((javaClass == Byte.class) || (javaClass == byte.class)) {
            preference = 15;
        } else if((javaClass == Boolean.class) || (javaClass == boolean.class)) {
            preference = 16;
        } else if(javaClass == Object.class) {
            preference = 20;
        } else {
            preference = Integer.MAX_VALUE;
        }
        return preference;
    }

    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        if(target.isAssignableFrom(NodeImpl.class) || target.isAssignableFrom(Node.class) || target == Object.class) {
            return (T) this;
        } else {
            final StringValue v = new StringValue(getStringValue());
            return v.toJavaObject(target);
        }
    }

    @Override
    public void setSelfAsContext(final int contextId) {
        throw unsupported();
    }

    @Override
    public boolean isCached() {
        // always return false
        return false;
    }

    @Override
    public void setIsCached(final boolean cached) {
        throw unsupported();
    }

    @Override
    public void removeDuplicates() {
    }

    @Override
    public String getBaseURI() {
        return null;
    }

    @Override
    public void destroy(final XQueryContext context, final Sequence contextSequence) {
    }


    public abstract void selectAttributes(final NodeTest test, final Sequence result) throws XPathException;

    public abstract void selectDescendantAttributes(final NodeTest test, final Sequence result) throws XPathException;

    public abstract void selectChildren(final NodeTest test, final Sequence result) throws XPathException;

    public void selectDescendants(final boolean includeSelf, final NodeTest test, final Sequence result)
        throws XPathException {
        if(includeSelf && test.matches(this)) {
            result.add(this);
        }
    }

    public void selectAncestors(final boolean includeSelf, final NodeTest test, final Sequence result)
        throws XPathException {
        if(nodeNumber < 1) {
            return;
        }
        if(includeSelf) {
            final NodeImpl n = document.getNode(nodeNumber);
            if(test.matches(n)) {
                result.add(n);
            }
        }
        int nextNode = document.getParentNodeFor(nodeNumber);
        while(nextNode > 0) {
            final NodeImpl n = document.getNode(nextNode);
            if(test.matches(n)) {
                result.add(n);
            }
            nextNode = document.getParentNodeFor(nextNode);
        }
    }

    public void selectPrecedingSiblings(final NodeTest test, final Sequence result)
        throws XPathException {
        final int parent = document.getParentNodeFor(nodeNumber);
        int nextNode = document.getFirstChildFor(parent);
        while((nextNode >= parent) && (nextNode < nodeNumber)) {
            final NodeImpl n = document.getNode(nextNode);
            if(test.matches(n)) {
                result.add(n);
            }
            nextNode = document.next[nextNode];
        }
    }

    public void selectPreceding(final NodeTest test, final Sequence result, final int position)
        throws XPathException {
        final NodeId myNodeId = getNodeId();
        int count = 0;

        for(int i = nodeNumber - 1; i > 0; i--) {
            final NodeImpl n = document.getNode(i);
            if(!myNodeId.isDescendantOf(n.getNodeId()) && test.matches(n)) {
                if((position < 0) || (++count == position)) {
                    result.add(n);
                }
                if(count == position) {
                    break;
                }
            }
        }
    }

    public void selectFollowingSiblings(final NodeTest test, final Sequence result)
        throws XPathException {
        final int parent = document.getParentNodeFor(nodeNumber);
        if(parent == 0) {
            // parent is the document node
            if(getNodeType() == Node.ELEMENT_NODE) {
                return;
            }
            NodeImpl next = (NodeImpl) getNextSibling();
            while(next != null) {
                if(test.matches(next)) {
                    result.add(next);
                }
                if(next.getNodeType() == Node.ELEMENT_NODE) {
                    break;
                }
                next = (NodeImpl) next.getNextSibling();
            }
        } else {
            int nextNode = document.getFirstChildFor(parent);
            while(nextNode > parent) {
                final NodeImpl n = document.getNode(nextNode);
                if((nextNode > nodeNumber) && test.matches(n)) {
                    result.add(n);
                }
                nextNode = document.next[nextNode];
            }
        }
    }

    public void selectFollowing(final NodeTest test, final Sequence result, final int position)
        throws XPathException {
        final int parent = document.getParentNodeFor(nodeNumber);
        if(parent == 0) {
            // parent is the document node
            if(getNodeType() == Node.ELEMENT_NODE) {
                return;
            }
            NodeImpl next = (NodeImpl) getNextSibling();
            while(next != null) {
                if(test.matches(next)) {
                    next.selectDescendants(true, test, result);
                }
                if(next.getNodeType() == Node.ELEMENT_NODE) {
                    break;
                }
                next = (NodeImpl) next.getNextSibling();
            }
        } else {
            final NodeId myNodeId = getNodeId();
            int count = 0;
            int nextNode = nodeNumber + 1;
            while(nextNode < document.size) {
                final NodeImpl n = document.getNode(nextNode);
                if(!n.getNodeId().isDescendantOf(myNodeId) && test.matches(n)) {
                    if((position < 0) || (++count == position)) {
                        result.add(n);
                    }
                    if(count == position) {
                        break;
                    }
                }
                nextNode++;
            }
        }
    }

    public boolean matchAttributes(final NodeTest test) {
        // do nothing
        return false;
    }

    public boolean matchDescendantAttributes(final NodeTest test) throws XPathException {
        // do nothing
        return false;
    }

    public boolean matchChildren(final NodeTest test) throws XPathException {
        return false;
    }

    public boolean matchDescendants(final boolean includeSelf, final NodeTest test) throws XPathException {
        return includeSelf && test.matches(this);
    }

    @Override
    public short compareDocumentPosition(final Node other) throws DOMException {
        throw unsupported();
    }

    @Override
    public String getTextContent() throws DOMException {
        throw unsupported();
    }

    @Override
    public void setTextContent(final String textContent) throws DOMException {
        throw unsupported();
    }

    @Override
    public boolean isSameNode(final Node other) {
        throw unsupported();
    }

    @Override
    public String lookupPrefix(final String namespaceURI) {
        throw unsupported();
    }

    @Override
    public boolean isDefaultNamespace(final String namespaceURI) {
        throw unsupported();
    }

    @Override
    public String lookupNamespaceURI(final String prefix) {
        throw unsupported();
    }

    @Override
    public boolean isEqualNode(final Node arg) {
        throw unsupported();
    }

    @Override
    public Object getFeature(final String feature, final String version) {
        throw unsupported();
    }

    @Override
    public Object setUserData(final String key, final Object data, final UserDataHandler handler) {
        throw unsupported();
    }

    @Override
    public Object getUserData(final String key) {
        throw unsupported();
    }

    @Override
    public boolean isPersistentSet() {
        return false;
    }

    @Override
    public void nodeMoved(final NodeId oldNodeId, final NodeHandle newNode) {
    }

    @Override
    public void clearContext(final int contextId) {
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

    @Override
    public boolean hasChanged(final int previousState) {
        return false;
    }


    protected DOMException unsupported() {
        return new DOMException(DOMException.NOT_SUPPORTED_ERR, "not implemented on class: " + getClass().getName());
    }

    private final static class SingleNodeIterator implements SequenceIterator {
        NodeImpl node;

        public SingleNodeIterator(final NodeImpl node) {
            this.node = node;
        }

        @Override
        public boolean hasNext() {
            return node != null;
        }

        @Override
        public Item nextItem() {
            final NodeImpl next = node;
            node = null;
            return next;
        }

    }
}
