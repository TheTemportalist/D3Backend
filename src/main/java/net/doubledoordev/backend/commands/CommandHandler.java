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

package net.doubledoordev.backend.commands;

import com.sk89q.intake.Command;
import com.sk89q.intake.CommandException;
import com.sk89q.intake.CommandMapping;
import com.sk89q.intake.context.CommandLocals;
import com.sk89q.intake.dispatcher.Dispatcher;
import com.sk89q.intake.fluent.CommandGraph;
import com.sk89q.intake.parametric.ParametricBuilder;
import com.sk89q.intake.parametric.annotation.Optional;
import com.sk89q.intake.util.auth.AuthorizationException;
import net.doubledoordev.backend.Main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static net.doubledoordev.backend.Main.LOGGER;
import static net.doubledoordev.backend.util.Constants.JOINER_COMMA_SPACE;

/**
 * Using sk89q's Intake lib
 *
 * @author Dries007
 */
public class CommandHandler implements Runnable
{
    public final Dispatcher dispatcher;
    private CommandHandler()
    {
        ParametricBuilder parametricBuilder = new ParametricBuilder();
        parametricBuilder.addBinding(new Bindings());

        dispatcher = new CommandGraph()
                .builder(parametricBuilder)
                .commands()
                .registerMethods(new Commands(this))
                .registerMethods(this)
                .graph()
                .getDispatcher();
    }

    public static void init()
    {
        new Thread(new CommandHandler(), "CommandHandler").start();
    }

    @Override
    public void run()
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (Main.running)
        {
            try
            {
                dispatcher.call(reader.readLine(), new CommandLocals(), new String[0]);
            }
            catch (CommandException | IOException | AuthorizationException e)
            {
                LOGGER.warn(e);
            }
        }
    }
}