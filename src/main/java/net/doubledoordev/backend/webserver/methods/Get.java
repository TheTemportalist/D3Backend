/*
 * Unless otherwise specified through the '@author' tag or comments at
 * the top of the file or on a specific portion of the code the following license applies:
 *
 * Copyright (c) 2014, DoubleDoorDevelopment
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  The header specified or the above copyright notice, this list of conditions
 *   and the following disclaimer below must be displayed at the top of the source code
 *   of any web page received while using any part of the service this software provides.
 *
 *   The header to be displayed:
 *       This page was generated by DoubleDoorDevelopment's D3Backend or a derivative thereof.
 *
 *  Neither the name of the project nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package net.doubledoordev.backend.webserver.methods;

import freemarker.template.*;
import net.doubledoordev.backend.Main;
import net.doubledoordev.backend.permissions.User;
import net.doubledoordev.backend.server.FileManager;
import net.doubledoordev.backend.server.Server;
import net.doubledoordev.backend.util.Constants;
import net.doubledoordev.backend.util.CustomLogAppender;
import net.doubledoordev.backend.util.Settings;
import net.doubledoordev.backend.webserver.NanoHTTPD;
import net.doubledoordev.backend.webserver.Webserver;

import java.io.FileNotFoundException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.doubledoordev.backend.util.Settings.SETTINGS;
import static net.doubledoordev.backend.util.Settings.getServerByName;
import static net.doubledoordev.backend.webserver.NanoHTTPD.*;
import static net.doubledoordev.backend.webserver.NanoHTTPD.Response.Status.*;

/**
 * Processes GET requests
 *
 * @author Dries007
 */
public class Get
{
    public static final  List<String>  ADMINPAGES     = Arrays.asList("console", "backendConsoleText");
    /**
     * Freemaker template engine config.
     * Used to resolve templates later on
     */
    private static final Configuration FREEMARKER_CFG = new Configuration();

    static
    {
        try
        {
            FREEMARKER_CFG.setClassForTemplateLoading(Main.class, "/templates/");
            FREEMARKER_CFG.setObjectWrapper(new DefaultObjectWrapper());
            FREEMARKER_CFG.setDefaultEncoding("UTF-8");
            FREEMARKER_CFG.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
            FREEMARKER_CFG.setIncompatibleImprovements(new Version(2, 3, 20));  // FreeMarker 2.3.20
            /**
             * Default objects passed on to the template engine
             */
            Map<String, Object> dataObject = new HashMap<>();
            dataObject.put("Settings", Settings.SETTINGS);
            dataObject.put("Helper", Constants.HELPER_TEMPLATE_MODEL);
            FREEMARKER_CFG.setAllSharedVariables(new SimpleHash(dataObject));
        }
        catch (TemplateModelException e)
        {
            throw new RuntimeException(e);
        }
    }

    private Get()
    {
    }

    /**
     * Entry point
     */
    public static Response handleGet(HashMap<String, Object> dataObject, NanoHTTPD.HTTPSession session)
    {
        String uri = session.getUri();
        try
        {
            if (uri.equals("/")) uri += "index";
            uri = uri.substring(1);
            if (uri.endsWith("/")) uri = uri.substring(0, uri.length() - 1);
            String[] args = uri.split("/");

            if (!dataObject.containsKey("user") && !SETTINGS.anonPages.contains(args[0].toLowerCase()))
            {
                args[0] = String.valueOf(FORBIDDEN.getRequestStatus());
                if (args[0].equals("serverConsoleText") || args[0].equals("backendConsoleText")) return new Response(FORBIDDEN, MIME_PLAINTEXT, "Not authorized.");
                return new Response(FORBIDDEN, MIME_HTML, resolveTemplate(dataObject, args, session));
            }

            if (ADMINPAGES.contains(args[0].toLowerCase()) && !((User) dataObject.get("user")).isAdmin())
            {
                args[0] = String.valueOf(FORBIDDEN.getRequestStatus());
                if (args[0].equals("backendConsoleText")) return new Response(FORBIDDEN, MIME_PLAINTEXT, "Not authorized.");
                return new Response(FORBIDDEN, MIME_HTML, resolveTemplate(dataObject, args, session));
            }

            if (args.length > 0)
            {
                switch (args[0])
                {
                    case "serverConsoleText":
                        return new Response(OK, MIME_PLAINTEXT, getServerByName(args[1]).getLogLinesAfter(Integer.parseInt(args[2])));
                    case "backendConsoleText":
                        return new Response(OK, MIME_PLAINTEXT, CustomLogAppender.getLogLinesAfter(Integer.parseInt(args[1])));
                    case "worldmanager":
                        if (args.length > 1)
                        {
                            Server server = getServerByName(args[1]);
                            dataObject.put("wm", server.getWorldManager());
                            dataObject.put("server", server);
                        }
                        else return new Response(BAD_REQUEST, MIME_PLAINTEXT, "No server provided.");
                        break;
                    case "filemanager":
                        if (args.length > 1)
                        {
                            Server server = getServerByName(args[1]);
                            if (!server.isCoOwner((User) dataObject.get("user")))
                            {
                                args[0] = String.valueOf(FORBIDDEN.getRequestStatus());
                                return new Response(FORBIDDEN, MIME_HTML, resolveTemplate(dataObject, args, session));
                            }
                            FileManager fileManager = new FileManager(server, session.getParms().get("file"));
                            if (session.getParms().containsKey("raw")) return Webserver.WEBSERVER.serveFile(fileManager.getFile());
                            dataObject.put("fm", fileManager);
                        }
                        else return new Response(BAD_REQUEST, MIME_PLAINTEXT, "No server provided.");
                        break;
                    case "serverconsole":
                    case "servers":
                        if (args.length > 1) dataObject.put("server", getServerByName(args[1]));
                        break;
                }
            }
            return new Response(resolveTemplate(dataObject, args, session));
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return new Response(INTERNAL_ERROR, MIME_PLAINTEXT, e.toString());
        }
    }

    /**
     * Get a template and process it with the dataObject
     */
    public static String resolveTemplate(HashMap<String, Object> dataObject, String[] args, NanoHTTPD.HTTPSession session)
    {
        StringWriter stringWriter = new StringWriter();
        try
        {
            FREEMARKER_CFG.getTemplate(getTemplateFor(args, session)).process(dataObject, stringWriter);
        }
        catch (FileNotFoundException e)
        {
            try
            {
                stringWriter = new StringWriter();
                FREEMARKER_CFG.getTemplate(NOT_FOUND.getDescription() + ".ftl").process(dataObject, stringWriter);
            }
            catch (Exception e1)
            {
                e.printStackTrace();
                return String.format("<h1>Error!</h1><p>Message:<br><pre>%s</pre></p>", e.toString());
            }
        }
        catch (Exception e)
        {
            try
            {
                stringWriter = new StringWriter();
                dataObject.put("message", e.toString());
                FREEMARKER_CFG.getTemplate(INTERNAL_ERROR.getDescription() + ".ftl").process(dataObject, stringWriter);
            }
            catch (Exception e1)
            {
                e.printStackTrace();
                return String.format("<h1>Error!</h1><p>Message:<br><pre>%s</pre></p>", e.toString());
            }
        }
        return stringWriter.toString();
    }

    /**
     * Find the right template file for a given URL
     */
    private static String getTemplateFor(String[] args, NanoHTTPD.HTTPSession session)
    {
        switch (args[0])
        {
            default:
                return args[0] + ".ftl";
            case "servers":
                return args.length > 1 ? "server.ftl" : "serverlist.ftl";
            case "users":
                return args.length > 1 ? "user.ftl" : "userlist.ftl";
        }
    }
}
