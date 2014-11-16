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
 *
 */

package net.doubledoordev.backend.web.http;

import com.google.common.collect.ImmutableList;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.*;
import net.doubledoordev.backend.permissions.User;
import net.doubledoordev.backend.server.FileManager;
import net.doubledoordev.backend.server.Server;
import net.doubledoordev.backend.util.Constants;
import net.doubledoordev.backend.util.Helper;
import net.doubledoordev.backend.util.Settings;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.ErrorPageGenerator;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.StaticHttpHandlerBase;
import org.glassfish.grizzly.http.util.HttpStatus;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static net.doubledoordev.backend.util.Constants.*;
import static net.doubledoordev.backend.web.http.PostHandler.POST_HANDLER;

/**
 * Freemarker!
 *
 * @author Dries007
 */
public class FreemarkerHandler extends StaticHttpHandlerBase implements ErrorPageGenerator
{
    private static final ImmutableList<String> ADMINPAGES = ImmutableList.of("console", "backendConsoleText");
    public static long lastRequest = 0L;
    private final Configuration freemarker = new Configuration();

    public FreemarkerHandler(Class clazz, String path) throws TemplateModelException
    {
        freemarker.setClassForTemplateLoading(clazz, path);
        freemarker.setObjectWrapper(new DefaultObjectWrapper());
        freemarker.setDefaultEncoding("UTF-8");
        freemarker.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
        freemarker.setIncompatibleImprovements(new Version(2, 3, 20));  // FreeMarker 2.3.20
        Map<String, Object> dataObject = new HashMap<>();
        dataObject.put("Settings", Settings.SETTINGS);
        dataObject.put("Helper", BeansWrapper.getDefaultInstance().getStaticModels().get(Helper.class.getName()));
        freemarker.setAllSharedVariables(new SimpleHash(dataObject));
    }

    @Override
    protected boolean handle(String uri, Request request, Response response) throws Exception
    {
        lastRequest = System.currentTimeMillis();
        if (request.getSession(false) != null) request.getSession();

        HashMap<String, Object> data = new HashMap<>(request.getSession().attributes().size() + 10);
        // Put all session data in map, take 1
        data.putAll(request.getSession().attributes());

        /**
         * Data processing
         */
        if (request.getMethod() == Method.GET)
        {
            Server server = Settings.getServerByName(request.getParameter(SERVER));
            if (server != null && server.canUserControl((User) data.get(USER)))
            {
                data.put(SERVER, server);
                if (uri.equals("/filemanager")) data.put("fm", new FileManager((Server) data.get(SERVER), request.getParameter(FILE)));
            }
        }
        else if (request.getMethod() == Method.POST)
        {
            uri = POST_HANDLER.handle(data, uri, request, response);
        }
        else
        {
            response.sendError(HttpStatus.METHOD_NOT_ALLOWED_405.getStatusCode());
        }

        /**
         * fix up the url to match template
         */
        if (uri.endsWith(SLASH_STR)) uri += INDEX;
        if (uri.startsWith(SLASH_STR)) uri = uri.substring(1);

        if (request.getSession().getAttribute(USER) == null && !Settings.SETTINGS.anonPages.contains(uri))
        {
            response.sendError(HttpStatus.UNAUTHORIZED_401.getStatusCode());
            return true;
        }
        else if (ADMINPAGES.contains(uri) && !((User) request.getSession().getAttribute(USER)).isAdmin())
        {
            response.sendError(HttpStatus.UNAUTHORIZED_401.getStatusCode());
            return true;
        }
        if (!uri.endsWith(Constants.TEMPLATE_EXTENSION)) uri += Constants.TEMPLATE_EXTENSION;

        // Put all session data in map, take 2
        data.putAll(request.getSession().attributes());

        try
        {
            freemarker.getTemplate(uri).process(data, response.getWriter());
            return true;
        }
        catch (FileNotFoundException ignored)
        {
            return false;
        }
    }

    @Override
    public String generate(Request request, int status, String reasonPhrase, String description, Throwable exception)
    {
        HashMap<String, Object> data = new HashMap<>(request.getSession().attributes().size() + 10);
        data.putAll(request.getSession().attributes());
        data.put(STATUS, status);
        data.put("reasonPhrase", reasonPhrase);
        data.put("description", description);
        data.put("exception", exception);
        if (exception != null) data.put("stackTrace", ExceptionUtils.getStackTrace(exception));

        StringWriter stringWriter = new StringWriter();
        try
        {
            freemarker.getTemplate(ERROR_TEMPLATE).process(data, stringWriter);
        }
        catch (Exception e)
        {
            e.printStackTrace(new PrintWriter(stringWriter));
        }
        return stringWriter.toString();
    }
}