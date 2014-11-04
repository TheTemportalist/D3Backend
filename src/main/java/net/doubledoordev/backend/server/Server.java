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

package net.doubledoordev.backend.server;

import com.google.gson.*;
import net.doubledoordev.backend.permissions.User;
import net.doubledoordev.backend.server.query.MCQuery;
import net.doubledoordev.backend.server.query.QueryResponse;
import net.doubledoordev.backend.server.rcon.RCon;
import net.doubledoordev.backend.util.*;
import net.doubledoordev.backend.util.exceptions.ServerOfflineException;
import net.doubledoordev.backend.util.exceptions.ServerOnlineException;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.progress.ProgressMonitor;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

import static net.doubledoordev.backend.util.Constants.*;
import static net.doubledoordev.backend.util.CustomLogAppender.LOG_LINES_KEPT;

/**
 * Class that holds methods related to Server instances.
 * The json based data is in a separate class for easy GSON integration.
 *
 * @author Dries007
 */
@SuppressWarnings("UnusedDeclaration")
public class Server
{
    private static final String SERVER_PROPERTIES = "server.properties";
    private static final String SERVER_PORT       = "server-port";
    private static final String QUERY_PORT        = "query.port";
    private static final String QUERY_ENABLE      = "enable-query";
    private static final String RCON_ENABLE       = "enable-rcon";
    private static final String RCON_PASSWORD     = "rcon.password";
    private static final String RCON_PORT         = "rcon.port";
    private static final String SERVER_IP         = "server-ip";

    /**
     * Java bean holding all the config data
     */
    private final ServerData data;
    private final Map<Integer, Dimension> dimensionMap = new HashMap<>();

    private final ArrayList<String> log  = new ArrayList<>(LOG_LINES_KEPT + 10);
    /**
     * Diskspace var + timer to avoid long page load times.
     */
    public        int[]             size = new int[3];
    public QueryResponse cachedResponse;
    /**
     * Used to reroute server output to our console.
     * NOT LOGGED TO FILE!
     */
    Logger logger;
    private File folder;
    private File propertiesFile;
    private long       propertiesFileLastModified = 0L;
    private Properties properties                 = new Properties();
    /**
     * RCon instance + timer to avoid long page load times.
     */
    private RCon    rCon;
    /**
     * MCQuery and QueryResponse instances + timer to avoid long page load times.
     */
    private MCQuery query;
    /**
     * The process the server will be running in
     */
    private Process process;
    private boolean starting = false;
    private File backupFolder;
    private WorldManager worldManager = new WorldManager(this);
    private User ownerObject;
    private boolean downloading = false;

    public Server(ServerData data, boolean isNewServer)
    {
        this.data = data;
        this.logger = LogManager.getLogger(data.ID);
        this.folder = new File(SERVERS, data.ID);
        this.backupFolder = new File(BACKUPS, data.ID);
        this.propertiesFile = new File(folder, SERVER_PROPERTIES);

        if (!backupFolder.exists()) backupFolder.mkdirs();
        if (!folder.exists()) folder.mkdir();
        normalizeProperties();

        // Check to see if the server is running outside the backend, if so reboot please!
        if (getRCon() != null)
        {
            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        renewQuery();
                        RCon rCon = getRCon();
                        for (String user : getPlayerList())
                            rCon.send("kick", user, NAME + " is taking over! Server Reboot!");
                        rCon.stop();
                        startServer();
                    }
                    catch (Exception ignored)
                    {
                    }
                }
            }).start();
        }

        if (isNewServer)
        {
            try
            {
                SizeCounter sizeCounter = new SizeCounter();
                Files.walkFileTree(getFolder().toPath(), sizeCounter);
                size[0] = sizeCounter.getSizeInMB();
                sizeCounter = new SizeCounter();
                if (getBackupFolder().exists()) Files.walkFileTree(getBackupFolder().toPath(), sizeCounter);
                size[1] = sizeCounter.getSizeInMB();
                size[2] = size[0] + size[1];
            }
            catch (IOException ignored)
            {
            }
        }
        else
        {
            getProperties();
        }
    }

    /**
     * Proper way of obtaining a RCon instance
     *
     * @return null if offine!
     */
    public RCon getRCon()
    {
        return rCon;
    }

    public void makeRcon()
    {
        try
        {
            rCon = new RCon(LOCALHOST, data.rconPort, data.rconPswd.toCharArray());
        }
        catch (Exception ignored) // Server offline.
        {
        }
    }

    /**
     * Proper way of obtaining a MCQuery instance
     */
    public MCQuery getQuery()
    {
        if (query == null) query = new MCQuery(LOCALHOST, data.serverPort);
        return query;
    }

    public void renewQuery()
    {
        cachedResponse = getQuery().fullStat();
    }

    /**
     * The properties from the server.properties file
     * Reloads form file!
     */
    public Properties getProperties()
    {
        if (propertiesFile.exists() && propertiesFile.lastModified() > propertiesFileLastModified)
        {
            try
            {
                properties.load(new StringReader(FileUtils.readFileToString(propertiesFile, "latin1")));
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        normalizeProperties();
        return properties;
    }

    /**
     * Saves the server.properties
     */
    public void saveProperties()
    {
        getProperties();
        try
        {
            if (!propertiesFile.exists()) //noinspection ResultOfMethodCallIgnored
                propertiesFile.createNewFile();
            FileUtils.writeStringToFile(propertiesFile, getPropertiesAsText().trim(), "latin1");
            propertiesFileLastModified = propertiesFile.lastModified();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void normalizeProperties()
    {
        if (Settings.SETTINGS.fixedPorts)
        {
            properties.setProperty(SERVER_PORT, String.valueOf(data.serverPort));
            properties.setProperty(QUERY_PORT, String.valueOf(data.serverPort));
        }
        else
        {
            data.serverPort = Integer.parseInt(properties.getProperty(SERVER_PORT, String.valueOf(data.serverPort)));
            properties.setProperty(QUERY_PORT, String.valueOf(data.serverPort));
        }

        if (Settings.SETTINGS.fixedIP) properties.setProperty(SERVER_IP, data.ip);
        else data.ip = properties.getProperty(SERVER_IP, data.ip);

        if (Settings.SETTINGS.fixedPorts) properties.setProperty(RCON_PORT, String.valueOf(data.rconPort));
        else data.rconPort = Integer.parseInt(properties.getProperty(RCON_PORT, String.valueOf(data.rconPort)));

        properties.put(RCON_ENABLE, "true");
        properties.put(QUERY_ENABLE, "true");
        properties.put(RCON_PASSWORD, data.rconPswd);
    }

    /**
     * Set a server.properties property and save the file.
     *
     * @param key   the key
     * @param value the value
     * @throws ServerOnlineException when the server is online
     */
    public void setProperty(String key, String value) throws ServerOnlineException
    {
        if (getOnline()) throw new ServerOnlineException();
        properties.put(key, value);
        normalizeProperties();
    }

    /**
     * Get a server.properties
     *
     * @param key the key
     * @return the value
     */
    public String getProperty(String key)
    {
        return properties.getProperty(key);
    }

    /**
     * Get all server.properties keys
     *
     * @return the value
     */
    public Enumeration<Object> getPropertyKeys()
    {
        return properties.keys();
    }

    public String getPropertiesAsText()
    {
        getProperties();
        StringBuilder sb = new StringBuilder();
        for (Object s : properties.keySet()) sb.append(s.toString()).append('=').append(properties.get(s)).append('\n');
        return sb.toString();
    }

    public void setPropertiesAsText(String urlEncodedText)
    {
        try
        {
            FileUtils.writeStringToFile(propertiesFile, urlEncodedText.trim(), "latin1");
            propertiesFileLastModified = 0L;
            getProperties();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Check server online status.
     * Ony detects when the process is started by us.
     * Bypass this limitation with RCon
     */
    public boolean getOnline()
    {
        try
        {
            if (process == null) return false;
            process.exitValue();
            return false;
        }
        catch (IllegalThreadStateException e)
        {
            return true;
        }
    }

    /**
     * Invokes getter method with reflection.
     * Used in templates as 'get($key)' where $key can be assigned by a list.
     *
     * @param name of property
     * @return null or the result of the method
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    public Object get(String name) throws InvocationTargetException, IllegalAccessException
    {
        for (Method method : this.getClass().getDeclaredMethods())
            if (method.getName().equalsIgnoreCase("get" + name)) return method.invoke(this);
        return null;
    }

    /**
     * @return Human readable server address
     */

    public String getDisplayAddress()
    {
        StringBuilder builder = new StringBuilder(25);
        if (data.ip != null && data.ip.trim().length() != 0) builder.append(data.ip);
        else builder.append(Settings.SETTINGS.hostname);
        builder.append(':').append(data.serverPort);
        return builder.toString();
    }

    public int getServerPort()
    {
        return Integer.parseInt(properties.containsKey(SERVER_PORT) ? getProperty(SERVER_PORT) : "-1");
    }

    public int getRconPort()
    {
        return Integer.parseInt(properties.containsKey(RCON_PORT) ? getProperty(RCON_PORT) : "-1");
    }

    public int getOnlinePlayers()
    {
        return cachedResponse == null ? 0 : cachedResponse.getOnlinePlayers();
    }

    public int getSlots()
    {
        return cachedResponse == null ? -1 : cachedResponse.getMaxPlayers();
    }

    public String getMotd()
    {
        return cachedResponse == null ? "?" : cachedResponse.getMotd();
    }

    public String getGameMode()
    {
        return cachedResponse == null ? "?" : cachedResponse.getGameMode();
    }

    public String getMapName()
    {
        return cachedResponse == null ? "?" : cachedResponse.getMapName();
    }

    public ArrayList<String> getPlayerList()
    {
        return cachedResponse == null ? new ArrayList<String>() : cachedResponse.getPlayerList();
    }

    public String getPlugins()
    {
        return cachedResponse == null ? "?" : cachedResponse.getPlugins();
    }

    public String getVersion()
    {
        return cachedResponse == null ? "?" : cachedResponse.getVersion();
    }

    /**
     * Remove the old and download the new server jar file
     */
    public void setVersion(final String version) throws ServerOnlineException
    {
        if (getOnline()) throw new ServerOnlineException();
        if (downloading) throw new IllegalStateException("Already downloading something.");
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                downloading = true;
                try
                {
                    // delete old files
                    for (File file : folder.listFiles(ACCEPT_MINECRAFT_SERVER_FILTER)) file.delete();
                    for (File file : folder.listFiles(ACCEPT_FORGE_FILTER)) file.delete();

                    File jarfile = new File(folder, getJarName());
                    if (jarfile.exists()) jarfile.delete();
                    File tempFile = new File(folder, getJarName() + ".tmp");

                    // Downloading new file

                    Download download = new Download(new URL(Constants.MC_SERVER_JAR_URL.replace("%ID%", version)), tempFile);

                    long lastTime = System.currentTimeMillis();
                    int lastInfo = 0;

                    while (download.getStatus() == Download.Status.Downloading)
                    {
                        if (download.getSize() != -1)
                        {
                            printLine(String.format("Download is %dMB", (download.getSize() / (1024 * 1024))));
                            break;
                        }
                        Thread.sleep(10);
                    }

                    while (download.getStatus() == Download.Status.Downloading)
                    {
                        if ((download.getProgress() - lastInfo >= 5) || (System.currentTimeMillis() - lastTime > 1000 * 10))
                        {
                            lastInfo = (int) download.getProgress();
                            lastTime = System.currentTimeMillis();

                            printLine(String.format("Downloaded %2.0f%% (%dMB / %dMB)", download.getProgress(), (download.getDownloaded() / (1024 * 1024)), (download.getSize() / (1024 * 1024))));
                        }

                        Thread.sleep(10);
                    }

                    if (download.getStatus() == Download.Status.Error)
                    {
                        throw new Exception(download.getMessage());
                    }

                    tempFile.renameTo(jarfile);
                }
                catch (Exception e)
                {
                    printLine("##################################################################");
                    printLine("Error downloading a new minecraft jar (version " + version + ")");
                    printLine("##################################################################");
                    logger.error(e);
                }
                downloading = false;
            }
        }, getID() + "-jar-downloader").start();
    }

    /**
     * Downloads and uses specific forge installer
     */
    public void installForge(String name) throws ServerOnlineException
    {
        if (getOnline()) throw new ServerOnlineException();
        final String version = Helper.getForgeVersionForName(name);
        if (version == null) throw new IllegalArgumentException("Forge with ID " + name + " not found.");
        if (downloading) throw new IllegalStateException("Already downloading something.");
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                downloading = true;
                try
                {
                    // delete old files
                    for (File file : folder.listFiles(ACCEPT_MINECRAFT_SERVER_FILTER)) file.delete();
                    for (File file : folder.listFiles(ACCEPT_FORGE_FILTER)) file.delete();

                    // download new files
                    String url = Constants.FORGE_INSTALLER_URL.replace("%ID%", version);
                    String forgeName = url.substring(url.lastIndexOf('/'));
                    File forge = new File(folder, forgeName);
                    FileUtils.copyURLToFile(new URL(url), forge);

                    // run installer
                    List<String> arguments = new ArrayList<>();

                    arguments.add(Constants.JAVAPATH);
                    arguments.add("-Xmx1G");

                    arguments.add("-jar");
                    arguments.add(forge.getName());

                    arguments.add("--installServer");

                    ProcessBuilder builder = new ProcessBuilder(arguments);
                    builder.directory(folder);
                    builder.redirectErrorStream(true);
                    final Process process = builder.start();
                    printLine(arguments.toString());
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) printLine(line);

                    try
                    {
                        process.waitFor();
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }

                    for (String name : folder.list(ACCEPT_MINECRAFT_SERVER_FILTER)) data.jarName = name;

                    forge.delete();
                    Settings.save();

                    printLine("Forge installer done.");
                }
                catch (IOException e)
                {
                    printLine("##################################################################");
                    printLine("Error installing a new forge version (version " + version + ")");
                    printLine(e.toString());
                    printLine("##################################################################");
                    e.printStackTrace();
                }
                downloading = false;
            }
        }, getID() + "-forge-installer").start();
    }

    public String getGameID()
    {
        return cachedResponse == null ? "?" : cachedResponse.getGameID();
    }

    public String getID()
    {
        return data.ID;
    }

    public int getRamMin()
    {
        return data.ramMin;
    }

    public void setRamMin(int ramMin) throws ServerOnlineException
    {
        if (getOnline()) throw new ServerOnlineException();
        data.ramMin = ramMin;
        Settings.save();
    }

    public int getRamMax()
    {
        return data.ramMax;
    }

    public void setRamMax(int ramMax) throws ServerOnlineException
    {
        if (getOnline()) throw new ServerOnlineException();
        data.ramMax = ramMax;
        Settings.save();
    }

    public int getPermGen()
    {
        return data.permGen;
    }

    public void setPermGen(int permGen) throws ServerOnlineException
    {
        if (getOnline()) throw new ServerOnlineException();
        data.permGen = permGen;
        Settings.save();
    }

    public List<String> getExtraJavaParameters()
    {
        return data.extraJavaParameters;
    }

    public void setExtraJavaParameters(List<String> list) throws Exception
    {
        if (getOnline()) throw new ServerOnlineException();
        for (String s : list)
            for (Pattern pattern : Constants.ILLEGAL_OPTIONS)
                if (pattern.matcher(s).matches()) throw new Exception(s + " NOT ALLOWED.");
        data.extraJavaParameters = list;
        Settings.save();
    }

    public void setExtraJavaParameters(String list) throws Exception
    {
        setExtraJavaParameters(Arrays.asList(list.split(",")));
    }

    public List<String> getExtraMCParameters()
    {
        return data.extraMCParameters;
    }

    public void setExtraMCParameters(List<String> list) throws Exception
    {
        if (getOnline()) throw new ServerOnlineException();
        for (String s : list)
            for (Pattern pattern : Constants.ILLEGAL_OPTIONS)
                if (pattern.matcher(s).matches()) throw new Exception(s + " NOT ALLOWED.");
        data.extraMCParameters = list;
        Settings.save();
    }

    public void setExtraMCParameters(String list) throws Exception
    {
        setExtraMCParameters(Arrays.asList(list.split(",")));
    }

    public String getJarName()
    {
        return data.jarName;
    }

    public void setJarName(String jarName) throws ServerOnlineException
    {
        if (getOnline()) throw new ServerOnlineException();
        data.jarName = jarName;
        Settings.save();
    }

    public boolean getAutoStart()
    {
        return data.autoStart;
    }

    public void setAutoStart(boolean autoStart)
    {
        printLine("setAutoStart " + autoStart);
        data.autoStart = autoStart;
        Settings.save();
    }

    public String getOwner()
    {
        return data.owner;
    }

    public void setOwner(String username)
    {
        ownerObject = null;
        data.owner = username;
    }

    public User getOwnerObject()
    {
        if (ownerObject == null) ownerObject = Settings.getUserByName(getOwner());
        if (ownerObject == null)
        {
            for (User user : Settings.SETTINGS.users.values())
            {
                if (user.isAdmin())
                {
                    ownerObject = user;
                    break;
                }
            }
        }
        return ownerObject;
    }

    public List<String> getAdmins()
    {
        return data.admins;
    }

    public void setAdmins(List<String> strings)
    {
        data.admins = strings;
        Settings.save();
    }

    public void setAdmins(String list) throws Exception
    {
        setAdmins(Arrays.asList(list.split(",")));
    }

    public void setAdmins()
    {
        setAdmins(Arrays.asList(new String[0]));
    }

    public List<String> getCoOwners()
    {
        return data.coOwners;
    }

    public void setCoOwners(List<String> strings)
    {
        data.coOwners = strings;
        Settings.save();
    }

    public void setCoOwners(String list) throws Exception
    {
        setCoOwners(Arrays.asList(list.split(",")));
    }

    public void setCoOwners()
    {
        setCoOwners(Arrays.asList(new String[0]));
    }

    /**
     * Clear the extraJavaParameters array.
     *
     * @throws ServerOnlineException
     */

    public void setExtraJavaParameters() throws Exception
    {
        setExtraJavaParameters(Arrays.asList(new String[0]));
    }

    /**
     * Clear the extraMCParameters array.
     *
     * @throws ServerOnlineException
     */

    public void setExtraMCParameters() throws Exception
    {
        setExtraMCParameters(Arrays.asList(new String[0]));
    }

    /**
     * Start the server in a process controlled by us.
     * Threaded to avoid haning.
     *
     * @throws ServerOnlineException
     */

    public void startServer() throws Exception
    {
        if (getOnline() || starting) throw new ServerOnlineException();
        if (downloading) throw new Exception("Still downloading something. You can see the progress in the server console.");
        if (new File(folder, data.jarName + ".tmp").exists()) throw new Exception("Minecraft server jar still downloading...");
        if (!new File(folder, data.jarName).exists()) throw new FileNotFoundException(data.jarName + " not found.");
        User user = Settings.getUserByName(getOwner());
        if (user == null) throw new Exception("No owner set??");
        if (user.getMaxRamLeft() != -1 && getRamMax() > user.getMaxRamLeft()) throw new Exception("Out of usable RAM. Lower your max RAM.");
        saveProperties();
        starting = true;

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                printLine("Starting server ................");
                try
                {
                    /**
                     * Build arguments list.
                     */
                    List<String> arguments = new ArrayList<>();
                    arguments.add(Constants.JAVAPATH);
                    arguments.add("-server");
                    {
                        int amount = getRamMin();
                        if (amount > 0) arguments.add(String.format("-Xms%dM", amount));
                        amount = getRamMax();
                        if (amount > 0) arguments.add(String.format("-Xmx%dM", amount));
                        amount = getPermGen();
                        if (amount > 0) arguments.add(String.format("-XX:MaxPermSize=%dm", amount));
                    }
                    for (String s : data.extraJavaParameters) if (s.trim().length() != 0) arguments.add(s.trim());
                    arguments.add("-jar");
                    arguments.add(data.jarName);
                    arguments.add("nogui");
                    for (String s : data.extraMCParameters) if (s.trim().length() != 0) arguments.add(s.trim());

                    // Debug printout
                    printLine("Arguments: " + arguments.toString());

                    /**
                     * Make ProcessBuilder, set rundir, and make sure the io gets redirected
                     */
                    ProcessBuilder pb = new ProcessBuilder(arguments);
                    pb.directory(folder);
                    pb.redirectErrorStream(true);
                    if (!new File(folder, data.jarName).exists()) return; // for reasons of WTF?
                    process = pb.start();
                    new Thread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            try
                            {
                                printLine("----=====##### STARTING SERVER #####=====-----");
                                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                                String line;
                                while ((line = reader.readLine()) != null)
                                {
                                    printLine(line);
                                }
                            }
                            catch (IOException e)
                            {
                                logger.error(e);
                            }
                        }
                    }, data.ID.concat("-streamEater")).start();

                    /**
                     * Renews cashed vars so they are up to date when the page is refreshed.
                     */
                    makeRcon();
                }
                catch (IOException e)
                {
                    logger.error(e);
                }
                starting = false;
            }
        }, "ServerStarter-" + getID()).start(); // <-- Very important call.
    }

    public void printLine(String line)
    {
        logger.info(line);
        log.add(line);
        while (log.size() > LOG_LINES_KEPT) log.remove(0);
    }

    /**
     * Stop the server gracefully
     *
     * @return true if successful via RCon
     */
    public boolean stopServer(String message)
    {
        if (!getOnline()) return false;
        try
        {
            renewQuery();
            makeRcon();
            printLine("----=====##### STOPPING SERVER WITH RCON #####=====-----");
            for (String user : getPlayerList())
                getRCon().send("kick", user, message);
            getRCon().stop();
            return true;
        }
        catch (Exception e)
        {
            printLine("----=====##### STOPPING SERVER VIA STREAM #####=====-----");
            PrintWriter printWriter = new PrintWriter(process.getOutputStream());
            printWriter.println("stop");
            printWriter.flush();
            return false;
        }
    }

    public boolean forceStopServer() throws Exception
    {
        if (!getOnline()) throw new ServerOfflineException();
        printLine("----=====##### KILLING SERVER #####=====-----");
        process.destroy();
        try
        {
            process.getOutputStream().close();
        }
        catch (IOException ignored)
        {
        }
        try
        {
            process.getErrorStream().close();
        }
        catch (IOException ignored)
        {
        }
        try
        {
            process.getInputStream().close();
        }
        catch (IOException ignored)
        {
        }
        return true;
    }

    public Process getProcess()
    {
        return process;
    }

    public boolean canUserControl(User user)
    {
        if (user == null) return false;
        if (user.isAdmin() || user.getUsername().equalsIgnoreCase(getOwner())) return true;
        for (String admin : getAdmins()) if (admin.equalsIgnoreCase(user.getUsername())) return true;
        for (String admin : getCoOwners()) if (admin.equalsIgnoreCase(user.getUsername())) return true;
        return false;
    }

    public boolean isCoOwner(User user)
    {
        if (user == null) return false;
        if (user.isAdmin() || user.getUsername().equalsIgnoreCase(getOwner())) return true;
        for (String admin : getCoOwners()) if (admin.equalsIgnoreCase(user.getUsername())) return true;
        return false;
    }

    public void delete() throws ServerOnlineException, IOException
    {
        if (getOnline()) throw new ServerOnlineException();
        Settings.SETTINGS.servers.remove(getID()); // Needs to happen first because
        FileUtils.deleteDirectory(folder);
        FileUtils.deleteDirectory(backupFolder);
    }

    public void downloadModpack(final String zipURL, final boolean purge) throws IOException, ZipException
    {
        if (downloading) throw new IllegalStateException("Already downloading something.");
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    downloading = true;
                    if (purge) for (File file : folder.listFiles(Constants.ACCEPT_ALL_FILTER)) if (file.isFile()) file.delete(); else FileUtils.deleteDirectory(file);
                    if (!folder.exists()) folder.mkdirs();

                    final File zip = new File(folder, "modpack.zip");

                    if (zip.exists()) zip.delete();
                    zip.createNewFile();

                    printLine("Downloading zip...");

                    Download download = new Download(new URL(URLDecoder.decode(zipURL, "UTF-8")), zip);

                    long lastTime = System.currentTimeMillis();
                    int lastInfo = 0;

                    while (download.getStatus() == Download.Status.Downloading)
                    {
                        if (download.getSize() != -1)
                        {
                            printLine(String.format("Download is %dMB", (download.getSize() / (1024 * 1024))));
                            break;
                        }
                        Thread.sleep(10);
                    }

                    while (download.getStatus() == Download.Status.Downloading)
                    {
                        if ((download.getProgress() - lastInfo >= 5) || (System.currentTimeMillis() - lastTime > 1000 * 10))
                        {
                            lastInfo = (int) download.getProgress();
                            lastTime = System.currentTimeMillis();

                            printLine(String.format("Downloaded %2.0f%% (%dMB / %dMB)", download.getProgress(), (download.getDownloaded() / (1024 * 1024)), (download.getSize() / (1024 * 1024))));
                        }

                        Thread.sleep(10);
                    }

                    if (download.getStatus() == Download.Status.Error)
                    {
                        throw new Exception(download.getMessage());
                    }

                    printLine("Downloading zip done, extracting...");

                    ZipFile zipFile = new ZipFile(zip);
                    zipFile.setRunInThread(true);
                    zipFile.extractAll(folder.getCanonicalPath());
                    lastTime = System.currentTimeMillis();
                    lastInfo = 0;
                    while (zipFile.getProgressMonitor().getState() == ProgressMonitor.STATE_BUSY)
                    {
                        if (zipFile.getProgressMonitor().getPercentDone() - lastInfo >= 10 || System.currentTimeMillis() - lastTime > 1000 * 10)
                        {
                            lastInfo = zipFile.getProgressMonitor().getPercentDone();
                            lastTime = System.currentTimeMillis();

                            printLine(String.format("Extracting %d%%", zipFile.getProgressMonitor().getPercentDone()));
                        }

                        Thread.sleep(10);
                    }

                    zip.delete();

                    printLine("Done extracting zip.");
                }
                catch (Exception e)
                {
                    printLine("##################################################################");
                    printLine("Error installing the modpack");
                    printLine(e.toString());
                    printLine("##################################################################");
                    e.printStackTrace();
                }
                downloading = false;
            }
        }, getID() + "-modpack-installer").start();
    }

    public File getFolder()
    {
        return folder;
    }

    public String getLogLinesAfter(int index)
    {
        JsonObject responce = new JsonObject();
        StringBuilder stringBuilder = new StringBuilder();
        synchronized (log)
        {
            responce.addProperty("size", log.size());
            if (index < log.size()) for (String line : log.subList(index, log.size())) stringBuilder.append(line).append('\n');
        }
        responce.addProperty("text", stringBuilder.toString());
        return responce.toString();
    }

    public void send(String s)
    {
        PrintWriter printWriter = new PrintWriter(process.getOutputStream());
        printWriter.println(s);
        printWriter.flush();
    }

    public File getBackupFolder()
    {
        return backupFolder;
    }

    public int[] getDiskspaceUse()
    {
        return size;
    }

    public WorldManager getWorldManager()
    {
        return worldManager;
    }

    public JsonElement toJson()
    {
        return GSON.toJsonTree(data);
    }

    public Map<Integer, Dimension> getDimensionMap()
    {
        return dimensionMap;
    }

    @Override
    public String toString()
    {
        return getID();
    }

    public static class Deserializer implements JsonDeserializer<Server>
    {
        @Override
        public Server deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
        {
            Server server = new Server((ServerData) context.deserialize(json, ServerData.class), false);
            if (json.getAsJsonObject().has("dimensions"))
            {
                for (Dimension dimension : (Dimension[]) context.deserialize(json.getAsJsonObject().get("dimensions"), Dimension[].class))
                {
                    server.dimensionMap.put(dimension.dimid, dimension);
                }
            }
            return server;
        }
    }

    public static class Serializer implements JsonSerializer<Server>
    {
        @Override
        public JsonElement serialize(Server src, Type typeOfSrc, JsonSerializationContext context)
        {
            JsonObject data = context.serialize(src.data).getAsJsonObject();
            data.add("dimensions", context.serialize(src.dimensionMap.values()));
            return data;
        }
    }
}
