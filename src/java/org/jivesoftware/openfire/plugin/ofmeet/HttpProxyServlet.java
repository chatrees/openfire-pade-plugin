/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jivesoftware.openfire.plugin.ofmeet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;



public class HttpProxyServlet extends HttpServlet
{
    private static final Logger Log = LoggerFactory.getLogger(HttpProxyServlet.class);
    public static final long serialVersionUID = 24362462L;

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        try {
            Log.info("HttpProxy - doGet start");

            String url = request.getParameter("url");

            if (url != null) {
                writeHeader(url, response);
                writeGet(url, response.getOutputStream());
            }

        }
        catch(Exception e) {
            Log.info("HttpProxy doGet Error: " + e.toString());
        }
    }


    private void writeHeader(String urlString, HttpServletResponse response)
    {

        try {
            response.setHeader("Expires", "Sat, 6 May 1995 12:00:00 GMT");
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.addHeader("Cache-Control", "post-check=0, pre-check=0");
            response.setHeader("Pragma", "no-cache");

            if (urlString.endsWith(".pdf"))
                response.setHeader("Content-Type", "application/pdf");
            else
                response.setHeader("Content-Type", "text/html");

            response.setHeader("Connection", "close");
        }
        catch(Exception e)
        {
            Log.info("HttpProxy writeHeader Error: " + e.toString());
        }
    }


    private void writeGet(String urlString, ServletOutputStream out)
    {
        byte[] buffer = new byte[4096];

        try{
            URL url = new URL(urlString);
            URLConnection urlConn = url.openConnection();
            InputStream inStream = urlConn.getInputStream();
            int n = -1;

            while ( (n = inStream.read(buffer)) != -1)
            {
                out.write(buffer, 0, n);
            }

            out.close();

        } catch(MalformedURLException e){
            Log.info("HttpProxy writeGet MalformedURLException", e);

        } catch(IOException  e1){
            Log.info("HttpProxy writeGet IOException", e1);

        } catch (Exception e2) {
            Log.info("HttpProxy - writeGet Exception", e2);
        }
    }
}
