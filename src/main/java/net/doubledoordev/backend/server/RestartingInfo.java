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

package net.doubledoordev.backend.server;

import com.google.gson.annotations.Expose;
import net.doubledoordev.backend.Main;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * @author Dries007
 */
@SuppressWarnings("UnusedDeclaration")
public class RestartingInfo
{
    @Expose
    public boolean autoStart              = false;
    @Expose
    public int     globalTimeout          = 24;
    @Expose
    public int     whenEmptyTimeout       = 30;
    @Expose
    public boolean enableRestartSchedule  = false;
    @Expose
    public int     restartScheduleHours   = 0;
    @Expose
    public int     restartScheduleMinutes = 0;
    @Expose
    public String  restartScheduleMessage = "Server reboot in %time minutes!";

    private boolean      restartNextRun  = false;
    private ScheduleStep runningSchedule = ScheduleStep.NONE;
    private Date lastRestart, emptyDate;

    public void run(Server server)
    {
        // To restart the server after it has been stopped by us.
        try
        {
            if (!server.getOnline() && !server.isDownloading() && restartNextRun)
            {
                server.startServer();
                restartNextRun = false;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        if (!server.getOnline()) return;
        if (lastRestart != null && System.currentTimeMillis() - lastRestart.getTime() < globalTimeout * 3500000) return; // No 3600000! Because of correction factor

        // Empty check
        if (server.getPlayerList().size() == 0)
        {
            if (emptyDate == null) emptyDate = new Date();
            else if (System.currentTimeMillis() - emptyDate.getTime() > whenEmptyTimeout * 60000)
            {
                initReboot(server, "Server restart because empty.");
            }
        }
        else emptyDate = null;

        // Restart Schedule
        if (enableRestartSchedule)
        {
            Calendar calendar = Calendar.getInstance();
            switch (runningSchedule)
            {
                case NONE:
                    calendar.add(Calendar.MINUTE, 15);
                    if (calendar.get(Calendar.HOUR_OF_DAY) == restartScheduleHours && calendar.get(Calendar.MINUTE) >= restartScheduleMinutes)
                    {
                        runningSchedule = ScheduleStep.M15;
                        server.sendCmd("say " + restartScheduleMessage.replace("%time", Integer.toString(runningSchedule.timeLeft)));
                    }
                    break;
                default:
                    calendar.add(Calendar.MINUTE, runningSchedule.timeLeft);
                    if (calendar.get(Calendar.HOUR_OF_DAY) == restartScheduleHours && calendar.get(Calendar.MINUTE) >= restartScheduleMinutes)
                    {
                        server.sendCmd("say " + restartScheduleMessage.replace("%time", Integer.toString(runningSchedule.timeLeft)));
                        runningSchedule = runningSchedule.nextStep;
                    }
                    break;
                case NOW:
                    runningSchedule = ScheduleStep.NONE;
                    initReboot(server, "Restarting on schedule.");
            }
        }
    }

    private void initReboot(Server server, String s)
    {
        lastRestart = new Date();
        server.stopServer(s);
        restartNextRun = true;
    }

    public String getLastRestart(String format)
    {
        return lastRestart == null ? "" : new SimpleDateFormat(format).format(lastRestart);
    }

    public enum ScheduleStep
    {
        NONE(-1, null), NOW(0, NONE), M1(1, NOW), M2(2, M1), M3(3, M2), M4(4, M3), M5(5, M4), M10(10, M5), M15(15, M10);

        public final int timeLeft;
        public final ScheduleStep nextStep;

        ScheduleStep(int timeLeft, ScheduleStep nextStep)
        {
            this.timeLeft = timeLeft;
            this.nextStep = nextStep != null ? nextStep : this;
        }
    }
}
