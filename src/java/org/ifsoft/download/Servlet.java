package org.ifsoft.download;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletOutputStream;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import java.nio.file.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.jivesoftware.util.*;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.http.HttpBindManager;


public class Servlet extends HttpServlet
{
    public static final long serialVersionUID = 24362462L;
    private static final Logger Log = LoggerFactory.getLogger( Servlet.class );

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        try {
            Log.debug("download - servlet - doGet start");

            String url = request.getParameter("url");

            if (url != null) {
                writeHeader(url, response);
                writeGet(url, response.getOutputStream());
            }

        }
        catch(Exception e) {
            Log.debug("download - servlet doGet Error: " + e.toString());
        }
    }


    private void writeHeader(String urlString, HttpServletResponse response)
    {

        try {
            response.setHeader("Expires", "Sat, 6 May 1995 12:00:00 GMT");
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.addHeader("Cache-Control", "post-check=0, pre-check=0");
            response.setHeader("Pragma", "no-cache");

            if (urlString.endsWith(".rss") || urlString.endsWith(".atom"))
                response.setHeader("Content-Type", "txt/xml");
            else
                response.setHeader("Content-Type", "text/html");

            response.setHeader("Connection", "close");
			
			HttpBindManager boshManager = HttpBindManager.getInstance();
			response.setHeader("Access-Control-Allow-Methods", String.join(",", HttpBindManager.HTTP_BIND_CORS_ALLOW_METHODS.getValue()));
			response.setHeader("Access-Control-Allow-Headers", String.join(",", HttpBindManager.HTTP_BIND_CORS_ALLOW_HEADERS.getValue() + ", Authorization"));
			response.setHeader("Access-Control-Max-Age", String.valueOf(HttpBindManager.HTTP_BIND_CORS_MAX_AGE.getValue().toSeconds()));
			response.setHeader("Access-Control-Allow-Origin", String.valueOf(HttpBindManager.HTTP_BIND_ALLOWED_ORIGINS.getDefaultValue()));
			response.setHeader("Access-Control-Allow-Credentials", "true");			
        }
        catch(Exception e)
        {
            Log.debug("download - servlet writeHeader Error: " + e.toString());
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
            Log.debug("download - servlet writeGet MalformedURLException", e);

        } catch(IOException  e1){
            Log.debug("download - servlet writeGet IOException", e1);

        } catch (Exception e2) {
            Log.debug("download - servlet - writeGet Exception", e2);
        }
    }
}
