/********************************************************************************
 * CruiseControl, a Continuous Integration Toolkit
 * Copyright (c) 2001, ThoughtWorks, Inc.
 * 200 E. Randolph, 25th Floor
 * Chicago, IL 60601 USA
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     + Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *
 *     + Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *
 *     + Neither the name of ThoughtWorks, Inc., CruiseControl, nor the
 *       names of its contributors may be used to endorse or promote
 *       products derived from this software without specific prior
 *       written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ********************************************************************************/

package net.sourceforge.cruisecontrol.util;

import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * This filter prunes branches that start with an element with
 * the given tagname.
 * It is used by the MergeLogger to get rid of
 * &lt;properties&gt;-tags in JUnit test results / reports.
 *
 * @author <a href="mailto:joriskuipers@xs4all.nl">Joris Kuipers</a>
 */
public class PruneElementFilter extends XMLFilterImpl {

    private static final Logger LOG = Logger.getLogger(PruneElementFilter.class);

    private final String tagName;

    private int depth = 0;

    /**
     * Constructor for PruneElementFilter.
     */
    public PruneElementFilter(String tagName) {
        super();
        this.tagName = tagName;
    }

    /**
     * Constructor for PruneElementFilter.
     * @param arg0
     */
    public PruneElementFilter(String tagName, XMLReader arg0) {
        super(arg0);
        this.tagName = tagName;
    }

    /**
     * @see org.xml.sax.ContentHandler#characters(char[], int, int)
     */
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (!isPruning()) {
            super.characters(ch, start, length);
        }
    }

    /**
     * @see org.xml.sax.ContentHandler#ignorableWhitespace(char[], int, int)
     */
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        if (!isPruning()) {
            super.ignorableWhitespace(ch, start, length);
        }
    }

    /**
     * @see org.xml.sax.ContentHandler#endElement(String, String, String)
     */
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (isPruning()) {
            depth--;
        } else {
            super.endElement(uri, localName, qName);
        }
    }

    /**
     * @see org.xml.sax.ContentHandler#startElement(String, String, String, Attributes)
     */
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        if (!isPruning() && localName.equals(tagName)) {
            // prune the tree that starts with this element
            depth = 1;
            LOG.debug("pruning branch starting with element named " + tagName);
        } else if (isPruning()) {
            depth++;
        } else {
            super.startElement(uri, localName, qName, atts);
        }
    }

    private boolean isPruning() {
        return depth > 0;
    }

}
