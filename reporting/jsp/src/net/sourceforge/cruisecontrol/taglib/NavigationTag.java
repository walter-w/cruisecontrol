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

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.BodyContent;
import javax.servlet.jsp.tagext.BodyTag;
import javax.servlet.jsp.tagext.Tag;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Arrays;
import java.util.Comparator;

/**
 *
 */
public class NavigationTag implements Tag, BodyTag {
    public static final String LABEL_SEPARATOR = "L";
    public static final SimpleDateFormat US_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    public static final String LINK_TEXT_ATTR = "linktext";
    public static final String URL_ATTR = "url";

    private Tag parent;
    private BodyContent bodyOut;
    private PageContext pageContext;
    private File logDir;
    private String[] fileNames;
    private int count;
    private DateFormat dateFormat = US_DATE_FORMAT;

    private int startingBuildNumber = 0;
    private int finalBuildNumber = Integer.MAX_VALUE;
    private int endPoint;

    protected String getUrl(String fileName, String servletPath) {
        String queryString = fileName.substring(0, fileName.lastIndexOf(".xml"));
        return servletPath + "?log=" + queryString;
    }

    protected String getLinkText(String fileName) {
        String dateString = "";
        String label = "";
        if (fileName.lastIndexOf(LABEL_SEPARATOR) > -1) {
            dateString = fileName.substring(3, fileName.indexOf(LABEL_SEPARATOR));
            label = " (" + fileName.substring(fileName.indexOf(LABEL_SEPARATOR) + 1, fileName.lastIndexOf(".xml")) + ")";
        } else {
            dateString = fileName.substring(3, fileName.lastIndexOf(".xml"));
        }
        DateFormat inputDate = null;
        if (dateString.length() == 14) {
            inputDate = new SimpleDateFormat("yyyyMMddHHmmss");
        } else {
            inputDate = new SimpleDateFormat("yyyyMMddHHmm");
        }

        Date date = null;
        try {
            date = inputDate.parse(dateString);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return dateFormat.format(date) + label;
    }

    protected String getServletPath() {
        String servletPath = ((HttpServletRequest) pageContext.getRequest()).getServletPath();
        String contextPath = ((HttpServletRequest) pageContext.getRequest()).getContextPath();
        return contextPath + servletPath;
    }


    public int doStartTag() throws JspException {
        String logDirName = pageContext.getServletConfig().getInitParameter("logDir");
        if (logDirName == null) {
            logDirName = pageContext.getServletContext().getInitParameter("logDir");
        }
        logDir = new File(logDirName);

        String logDirPath = logDir.getAbsolutePath();

        System.out.println("Scanning directory: " + logDirPath + " for log files.");

        String [] logFileNames = logDir.list(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("log") && name.endsWith(".xml") && !(new File(dir, name).isDirectory());
            }
        });

        if (logFileNames == null) {
            throw new JspException(
                    "Configuration problem? No logs found in logDir: "
                    + logDirPath);
        }

        //sort links...
        Arrays.sort(logFileNames, new Comparator() {
            public int compare(Object o1, Object o2) {
                return ((String) o2).compareTo((String) o1);
            }
        });

        setFileNames(logFileNames);
        return EVAL_BODY_TAG;
    }

    void setFileNames(String [] logFileNames) {
        fileNames = logFileNames;
    }

    public void doInitBody() throws JspException {
        count = Math.max(0, startingBuildNumber);
        endPoint = Math.min(finalBuildNumber + 1, fileNames.length);
        if (count < endPoint) {
            setupLinkVariables();
        }
    }

    private void setupLinkVariables() {
        final String fileName = fileNames[count];
        pageContext.setAttribute(URL_ATTR, getUrl(fileName, getServletPath()));
        pageContext.setAttribute(LINK_TEXT_ATTR, getLinkText(fileName));
        count++;
    }

    public int doAfterBody() throws JspException {
        if (count < endPoint) {
            setupLinkVariables();
            return EVAL_BODY_TAG;
        } else {
            try {
                bodyOut.writeOut(bodyOut.getEnclosingWriter());
            } catch (IOException e) {
                e.printStackTrace();
            }
            return SKIP_BODY;
        }
    }

    public void release() {
    }

    public void setPageContext(PageContext pageContext) {
        this.pageContext = pageContext;
    }

    public int doEndTag() throws JspException {
        return EVAL_PAGE;
    }

    public void setParent(Tag parent) {
        this.parent = parent;
    }

    public Tag getParent() {
        return parent;
    }

    public void setBodyContent(BodyContent bodyOut) {
        this.bodyOut = bodyOut;
    }

    public int getStartingBuildNumber() {
        return startingBuildNumber;
    }

    public void setStartingBuildNumber(int startingBuildNumber) {
        this.startingBuildNumber = startingBuildNumber;
    }

    public int getFinalBuildNumber() {
        return finalBuildNumber;
    }

    public void setFinalBuildNumber(int finalBuildNumber) {
        this.finalBuildNumber = finalBuildNumber;
    }

    /**
     * Set the DateFormat to use. The default is for US-Style (MM/dd/yyyy HH:mm:ss).
     * @param dateFormatString  the date format to use. Any format appropriate for the java.text.SimpleDataFormat is
     *                          okay to use.
     */
    public void setDateFormat(String dateFormatString) {
        dateFormat = new SimpleDateFormat(dateFormatString);
    }

    /**
     * Return the count through the list of files.
     * @return  the current count.
     */
    public int getCount() {
        return count;
    }
}
