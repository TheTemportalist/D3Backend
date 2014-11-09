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

package net.doubledoordev.backend.web.socket;

import com.google.gson.JsonObject;
import net.doubledoordev.backend.Main;
import net.doubledoordev.backend.permissions.User;
import net.doubledoordev.backend.server.Server;
import net.doubledoordev.backend.util.Settings;
import net.doubledoordev.backend.util.TypeHellhole;
import net.doubledoordev.backend.util.WebSocketHelper;
import org.glassfish.grizzly.http.server.DefaultSessionManager;
import org.glassfish.grizzly.http.server.Session;
import org.glassfish.grizzly.websockets.DefaultWebSocket;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketApplication;
import org.glassfish.grizzly.websockets.WebSocketEngine;

import java.lang.reflect.InvocationTargetException;
import java.util.TimerTask;

import static net.doubledoordev.backend.util.Constants.*;

/**
 * TODO: change to /server/id
 *
 * @author Dries007
 */
public class ServerControlSocketApplication extends WebSocketApplication
{
    private static final String URL_PATTERN = "/server/*";

    public static final ServerControlSocketApplication SERVER_CONTROL_SOCKET_APPLICATION = new ServerControlSocketApplication();

    private ServerControlSocketApplication()
    {
    }

    private static void invokeWithRefectionMagic(JsonObject jsonObject, Object instance, String[] split, int start) throws IllegalAccessException
    {
        start++;
        for (java.lang.reflect.Method method : instance.getClass().getDeclaredMethods())
        {
            // Check to see if ID is same and if the amount of parameters fits.
            if (method.getName().equals(split[start - 1]) && method.getParameterTypes().length == split.length - start)
            {
                try
                {
                    Object parms[] = new Object[split.length - start];
                    for (int i = 0; i < method.getParameterTypes().length; i++) parms[i] = TypeHellhole.convert(method.getParameterTypes()[i], split[i + start]);
                    method.invoke(instance, parms);
                    return;
                }
                catch (ClassCastException ignored)
                {
                    // Ignored because we don't care.
                }
                catch (InvocationTargetException e)
                {
                    Main.LOGGER.warn("ERROR invoking method via reflection: " + method.toString());
                    e.printStackTrace();
                    jsonObject.addProperty("status", "error");
                    jsonObject.addProperty("message", e.getCause().toString());
                    return;
                }
            }
        }
        jsonObject.addProperty("status", "error");
        jsonObject.addProperty("message", "method not found");
    }

    @Override
    public void onConnect(WebSocket socket)
    {
        Session session = DefaultSessionManager.instance().getSession(null, ((DefaultWebSocket) socket).getUpgradeRequest().getRequestedSessionId());
        if (session == null)
        {
            socket.send("No valid session.");
            socket.close();
            return;
        }
        ((DefaultWebSocket) socket).getUpgradeRequest().setAttribute("user", session.getAttribute("user"));
        super.onConnect(socket);
    }

    @Override
    public void onMessage(WebSocket socket, String text)
    {
        String[] args = text.split("\\|");
        Server server = Settings.getServerByName(args[0]);
        if (server == null)
        {
            socket.send("No valid server.");
            socket.close();
            return;
        }
        else if (!server.canUserControl((User) ((DefaultWebSocket) socket).getUpgradeRequest().getAttribute("user")))
        {
            socket.send("You have no rights to this server.");
            socket.close();
            return;
        }
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("status", "ok");
        try
        {
            invokeWithRefectionMagic(jsonObject, server, args, 1);
        }
        catch (IllegalAccessException e)
        {
            jsonObject.addProperty("status", "error");
            jsonObject.addProperty("message", "");
        }
        socket.send(jsonObject.toString());
        socket.close();
    }

    public void register()
    {
        WebSocketEngine.getEngine().register(SOCKET_CONTEXT, URL_PATTERN, this);
        TIMER.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                for (WebSocket socket : getWebSockets()) socket.sendPing("ping".getBytes());
            }
        }, SOCKET_PING_TIME, SOCKET_PING_TIME);
    }

    public void sendStatusUpdateToAll(Server server)
    {
        JsonObject data = new JsonObject();
        data.addProperty("online", server.getOnline());
        for (WebSocket socket : getWebSockets()) WebSocketHelper.sendData(socket, data);
    }
}
