/********************************************************************************
 * CruiseControl, a Continuous Integration Toolkit
 * Copyright (c) 2001, ThoughtWorks, Inc.
 * 651 W Washington Ave. Suite 500
 * Chicago, IL 60661 USA
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
package net.sourceforge.cruisecontrol.taglib;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Comparator;
import java.util.StringTokenizer;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.BodyContent;
import javax.servlet.jsp.tagext.BodyTag;
import javax.servlet.jsp.tagext.Tag;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 *  JSP custom tag to handle xsl transforms.  This tag also caches the output of the transform to disk, reducing the
 *  number of transforms necessary.
 *
 *  @author alden almagro, ThoughtWorks, Inc. 2002
 */
public class XSLTag implements Tag, BodyTag {

    private BodyContent bodyOut;
    private Tag parent;
    private PageContext pageContext;
    private String xslFileName;

    /**
     *  Perform an xsl transform.  This body of this method is based upon the xalan sample code.
     *
     *  @param xmlFile the xml file to be transformed
     *  @param in stream containing the xsl stylesheet
     *  @param out writer to output the results of the transform
     */
    protected void transform(File xmlFile, InputStream in, Writer out) {
        try {
            TransformerFactory tFactory = TransformerFactory.newInstance();
            if (xslFileName != null) {
                String xslFilePath = pageContext.getServletContext().getRealPath(xslFileName);
                final String xslDir = new File(xslFilePath).getAbsoluteFile().getParent();
                javax.xml.transform.URIResolver resolver = new javax.xml.transform.URIResolver() {
                    public javax.xml.transform.Source resolve(String href, String base) {
                        File possibleFile = new File(xslDir, href);
                        if (possibleFile.exists()) {
                            System.out.println("Using nested stylesheet for " + href);
                            return new javax.xml.transform.stream.StreamSource(possibleFile);
                        } else {
                            System.out.println("Nested stylesheet not found for " + href);
                            return null;
                        }
                    }
                };
                tFactory.setURIResolver(resolver);
            } else {
                System.err.println("No XSL File Name!");
            }
            Transformer transformer = tFactory.newTransformer(new StreamSource(in));
            transformer.transform(new StreamSource(xmlFile), new StreamResult(out));
        } catch (TransformerFactoryConfigurationError error) {
            error.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
        }
    }

    /**
     *  Determine whether the cache file is current or not.  The file will be current if it is newer than both the
     *  xml log file and the xsl file used to create it.
     *
     *  @return true if the cache file is current.
     */
    protected boolean isCacheFileCurrent(File xmlFile, File cacheFile) {
        if (!cacheFile.exists()) {
            return false;
        }

        long xmlLastModified = xmlFile.lastModified();
        long xslLastModified = xmlLastModified;
        long cacheLastModified = cacheFile.lastModified();

        try {
            URL url = pageContext.getServletContext().getResource(xslFileName);
            URLConnection con = url.openConnection();
            xslLastModified = con.getLastModified();
        } catch (Exception e) {
            System.err.println("Failed to retrieve lastModified of xsl file " + xslFileName);
        }

        return (cacheLastModified > xmlLastModified) && (cacheLastModified > xslLastModified);
    }

    /**
     *  Serves the cached copy rather than re-performing the xsl transform for every request.
     *
     *  @param cacheFile The filename of the cached copy of the transform.
     *  @param out The writer to write to
     */
    protected void serveCachedCopy(File cacheFile, Writer out) {
        InputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(cacheFile));
            int c = 0;
            while ((c = is.read()) != -1) {
                out.write(c);
            }
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            is = null;
        }
    }

    /**
     *  Create a filename for the cached copy of this transform.  This filename will be the concatenation of the
     *  log file and the xsl file used to create it.
     *
     *  @param xmlFile The log file used as input to the transform
     *  @return The filename for the cached file
     */
    protected String getCachedCopyFileName(File xmlFile) {
        String xmlFileName = xmlFile.getName().substring(0, xmlFile.getName().lastIndexOf("."));
        String styleSheetName = xslFileName.substring(xslFileName.lastIndexOf("/") + 1, xslFileName.lastIndexOf("."));
        return xmlFileName + "-" + styleSheetName + ".html";
    }

    /**
     *  Gets the correct log file, based on the query string and the log directory.
     *
     *  @param queryString The query string from the request, which should contain a name value pair log=LOG_FILE_NAME
     *  @param logDir The directory where the log files reside.
     *  @return The specifed log file or the latest log, if nothing is specified
     */
    protected File getXMLFile(String queryString, File logDir) {
        File xmlFile = null;
        if (queryString == null || queryString.trim().equals("")) {
            xmlFile = getLatestLogFile(logDir);
            System.out.println("Using latest log file: " + xmlFile.getAbsolutePath());
        } else {
            String logFile = null;
            StringTokenizer tokenizer = new StringTokenizer(queryString, "&");
            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken();
                if (token.startsWith("log")) {
                    logFile = token.substring(token.lastIndexOf('=') + 1);
                }
            }
            xmlFile = new File(logDir, logFile + ".xml");
            System.out.println("Using specified log file: " + xmlFile.getAbsolutePath());
        }
        return xmlFile;
    }

    /**
     *  Gets the latest log file in a given directory.  Since all of our logs contain a date/time string, this method
     *  is actually getting the log file that comes last alphabetically.
     *
     *  @return The latest log file.
     */
    protected File getLatestLogFile(File logDir) {
        File[] logs = logDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return (name.startsWith("log") && name.endsWith(".xml") && name.length() > 7);
            }
        });
        if (logs != null && logs.length > 0) {
            Arrays.sort(logs, new Comparator() {
                public int compare(Object o1, Object o2) {
                    return ((File) o2).getName().compareTo(((File) o1).getName());
                }
            });
            return logs[0];
        } else {
            return null;
        }
    }

    /**
     *  Sets the xsl file to use.  It is expected that this can be found by the <code>ServletContext</code> for this
     *  web application.
     *
     *  @param xslFile The path to the xslFile.
     */
    public void setXslFile(String xslFile) {
        xslFileName = xslFile;
    }

    /**
     *  Write the transformed log content to page writer given.
     */
    protected void writeContent(Writer out) throws JspException {
        String logDirName = pageContext.getServletConfig().getInitParameter("logDir");
        if (logDirName == null) {
            logDirName = pageContext.getServletContext().getInitParameter("logDir");
        }
        File logDir = new File(logDirName);
        System.out.println("Scanning directory: " + logDir.getAbsolutePath() + " for log files.");


        File cacheDir = new File(logDir, "_cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdir();
        }

        String queryString = ((HttpServletRequest) pageContext.getRequest()).getQueryString();
        File xmlFile = getXMLFile(queryString, logDir);
        File cacheFile = new File(cacheDir, getCachedCopyFileName(xmlFile));
        if (!isCacheFileCurrent(xmlFile, cacheFile)) {
            try {
                final InputStream styleSheetStream = pageContext.getServletContext().getResourceAsStream(xslFileName);
                transform(xmlFile, styleSheetStream, new FileWriter(cacheFile));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Using cached copy: " + cacheFile.getAbsolutePath());
        }

        serveCachedCopy(cacheFile, out);
    }

    public int doAfterBody() throws JspException {
        //writeContent(bodyOut.getEnclosingWriter());
        return SKIP_BODY;
    }

    public int doEndTag() throws JspException {
        writeContent(pageContext.getOut());
        return EVAL_PAGE;
    }

    public int doStartTag() throws JspException {
        return EVAL_BODY_TAG;
    }

    public Tag getParent() {
        return parent;
    }

    public void release() {
    }

    public void setPageContext(PageContext pageContext) {
        this.pageContext = pageContext;
    }

    public void setParent(Tag parent) {
        this.parent = parent;
    }

    public void doInitBody() throws JspException {
    }

    public void setBodyContent(BodyContent bodyOut) {
        this.bodyOut = bodyOut;
    }
}