/*
    Copyright (C) 2013 Yizhe Shen <brrr@live.ca>

    This file is part of ircutil.

    ircutil is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    ircutil is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with ircutil.  If not, see <http://www.gnu.org/licenses/>.
*/

package ircutil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import org.pircbotx.*;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.NoticeEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;

/**
 * A set of useful functions for an IRC bot.
 * @author Yizhe Shen
 */
public class Utilities extends ListenerAdapter<PircBotX>{
    private PircBotX bot;
    private char commandChar;
    private long startTime;
    private ArrayList<String> awayList;
    private ArrayList<String> adminList;
    private ArrayList<String> simpleList;
    private ArrayList<CloneBot> cloneList;
    Random randGen;
    
    public Utilities(PircBotX parent, char commChar){
        bot = parent;
        commandChar = commChar;
        startTime = System.currentTimeMillis();
        randGen = new Random();
        awayList = loadHostList("away.txt");
        adminList = loadHostList("admins.txt");
        simpleList = loadHostList("simple.txt");
        cloneList = new ArrayList<CloneBot>();
    }

    @Override
    public void onNotice (NoticeEvent<PircBotX> event){
        String msg = event.getMessage();
        
        // Check if it's a CTCP reply
        if (msg.startsWith("\u0001") && msg.endsWith("\u0001")){
            processCTCPReply(bot, event.getUser(), msg);
        }
    }
    
    @Override
    public void onMessage(MessageEvent<PircBotX> event){
        String msg = event.getMessage();
        
        // Parse the message if it is a command
        if (msg.length() > 1 && msg.charAt(0) == commandChar && msg.charAt(1) != ' ') {
            msg = msg.substring(1);
            StringTokenizer st = new StringTokenizer(msg);
            String command = st.nextToken().toLowerCase();
            String[] params = new String[st.countTokens()];
            for (int ctr = 0; ctr < params.length; ctr++){
                params[ctr] = st.nextToken();
            }
            processCommand(event.getChannel(), event.getUser(), command, params);
        }
    }
    
    @Override
    public void onPrivateMessage (PrivateMessageEvent<PircBotX> event){
        String msg = event.getMessage();
        
        // Parse the private message
        StringTokenizer st = new StringTokenizer(msg);
        String command = st.nextToken().toLowerCase();
        String[] params = new String[st.countTokens()];
        for (int ctr = 0; ctr < params.length; ctr++){
            params[ctr] = st.nextToken();
        }
        processPM(event.getUser(), command, params, msg);
    }
    
    /**
     * Processes a CTCP reply from a user.
     * 
     * @param bot the bot the caught the reply
     * @param user the User that sent the reply
     * @param msg the CTCP message
     */
    public void processCTCPReply(PircBotX bot, User user, String msg){
        StringTokenizer st = new StringTokenizer(msg, "\u0020\u0001");
        String[] params = new String[st.countTokens()];
        for (int ctr = 0; ctr < params.length; ctr++){
            params[ctr] = st.nextToken();
        }
        
        if (params[0].equals("PING")){
            bot.sendNotice(user, "Lag: " + formatPing((double) (System.currentTimeMillis() - Long.parseLong(params[1])) / 1000) + " seconds");
        }
    }
    
    /**
     * Processes messages given to the the bot through PM. These commands
     * should be accessible only by admins.
     * 
     * @param user the User that sent the command
     * @param command the command
     * @param params the command parameters
     * @param origMsg the entire original message
     */
    public void processPM(User user, String command, String[] params, String origMsg){
        // Check if the user is an admin
        if (!isAdmin(user)){
            bot.sendNotice(user, "You are not authorized to make this command.");
        
        // Join a specified channel
        } else if (command.equals("join")){
            // Check if we have enough parameters
            if (params.length < 1) {
                bot.sendNotice(user, "Missing channel parameter.");
            } else {
                if (!params[0].startsWith("#")){
                    bot.joinChannel("#" + params[0]);
                } else {
                    bot.joinChannel(params[0]);
                }
            }
            
        // Leave a specified channel
        } else if (command.equals("part") || command.equals("leave")){
            // Check if we have enough parameters
            if (params.length < 1) {
                bot.sendNotice(user, "Missing channel parameter.");
            } else {
                if (bot.channelExists(params[0])){
                    bot.partChannel(bot.getChannel(params[0]));
                } else {
                    bot.sendNotice(user, bot.getNick() + " is not in " + params[0] + ".");
                }
            }
        
        // Op/Deop/Voice/Devoice the specified user in the specified channel
        } else if (command.equals("op") || command.equals("deop") ||
                    command.equals("voice") || command.equals("devoice")){
            // Check if we have enough parameters
            if (params.length < 1){
                bot.sendNotice(user, "Missing channel parameter.");
            } else {
                // Check if the bot is in the specified channel
                if (!bot.channelExists(params[0])){
                    bot.sendNotice(user, bot.getNick() + " is not in " + params[0] + ".");
                } else {
                    Channel channel = bot.getChannel(params[0]);
                    // Check if the bot has Ops in that channel
                    if (!channel.isOp(bot.getUserBot())){
                        bot.sendNotice(user, bot.getNick()+" is not authorized to do this in " + params[0] + ".");
                    } else {
                        // Check if we have a specified user
                        if (params.length > 1){
                            if (!isUserInChannel(channel, params[1])){
                                bot.sendNotice(user, params[1] + " is not in " + params[0] + ".");
                            } else {
                                User newuser = bot.getUser(params[1]);
                                if (command.equals("op")){
                                    bot.op(channel, newuser);
                                } else if (command.equals("deop")){
                                    bot.deOp(channel, newuser);
                                } else if (command.equals("voice")){
                                    bot.voice(channel, newuser);
                                } else if (command.equals("devoice")){
                                    bot.deVoice(channel, newuser);
                                }
                            }
                        } else {
                            if (!channel.getUsers().contains(user)){
                                bot.sendNotice(user, "You are not in " + params[0] + ".");
                            } else {
                                if (command.equals("op")){
                                    bot.op(channel, user);
                                } else if (command.equals("deop")){
                                    bot.deOp(channel, user);
                                } else if (command.equals("voice")){
                                    bot.voice(channel, user);
                                } else if (command.equals("devoice")){
                                    bot.deVoice(channel, user);
                                }
                            }
                        }
                    }
                }
            }
        
        // Add/Remove bot admins
        } else if (command.equals("admin") || command.equals("deadmin")){
            // Check if we have enough parameters
            if (params.length < 1){
                bot.sendNotice(user, "Missing channel parameter.");
            } else {
                // Find if the user is in any of the channels the bot is in
                User tUser;
                boolean found = false;
                Iterator<Channel> it = bot.getChannels().iterator();
                Iterator<User> it2;
                while(it.hasNext()){
                    it2 = it.next().getUsers().iterator();
                    while(it2.hasNext()){
                        tUser = it2.next();
                        // If we find the user, then we can add/remove them from the admin list
                        if (tUser.getNick().equalsIgnoreCase(params[0])){
                            found = true;
                            if (command.equals("admin")){
                                addAdmin(tUser);
                            } else if (command.equalsIgnoreCase("deadmin")){
                                removeAdmin(tUser);
                            }
                            break;
                        }
                    }
                    if (found){
                        break;
                    }
                }
                
                // If user is not in any channel to which the bot is joined
                if (!found){
                    bot.sendNotice(user, params[0] + " was not found in any of the channels to which I am joined.");
                }
            }
        
        // Gets the bot to say a given message to a specified recipient
        } else if (command.equals("say") || command.equals("echo")){
            // params[0] == recipient
            int msgLoc = origMsg.toLowerCase().indexOf(params[0]) + params[0].length() + 1;
            bot.sendMessage(params[0], origMsg.substring(msgLoc));
        
        // Gets the bot to say send a raw line
        } else if (command.equals("raw")){
            int msgLoc = origMsg.toLowerCase().indexOf(command) + command.length() + 1;
            bot.sendRawLine(origMsg.substring(msgLoc));
            
        // Erases all hostmasks from away.txt
        } else if (command.equals("resetaway")){
            awayList.clear();
            saveHostmaskList("away.txt", awayList);
            bot.sendNotice(user, "The away list has been emptied.");
        
        } else if (command.equals("resetsimple")) {
            simpleList.clear();
            saveHostmaskList("simple.txt", simpleList);
            bot.sendNotice(user, "The simple list has been emptied.");
        
        // Adds a clone to the specified channel
        } else if (command.equals("addclone")) {
            if (params.length > 1) {
                CloneBot newClone = new CloneBot(params[0], params[1], "chat.freenode.net");
                cloneList.add(newClone);
            } else {
                bot.sendNotice(user, "Missing parameter(s).");
            }
            
        // Removes the specified clone
        } else if (command.equals("removeclone")) {
            if (params.length > 0) {
                CloneBot cBot;
                for (int ctr = 0; ctr < cloneList.size(); ctr++) {
                    cBot = cloneList.get(ctr);
                    if (cBot.getNick().equalsIgnoreCase(params[0])) {
                        removeClone(cBot);
                        break;
                    }
                }
            } else {
                bot.sendNotice(user, "Missing parameter(s).");
            }
        }
    }
    
    /**
     * Process a command.
     * 
     * @param channel the originating channel of the command
     * @param user the user who made the command
     * @param command the command
     * @param params the parameters after the command
     */
    public void processCommand(Channel channel, User user, String command, String[] params){    	 
        // Display current host time
        if (command.equals("time")){
            bot.sendMessage(channel, "Time: " + new Date().toString());
            
        // Display bot uptime
        } else if (command.equals("uptime")){
            long d = (System.currentTimeMillis() - startTime)/1000;
            long seconds = d % 60;
            long minutes = (d / 60) % 60;
            long hours = (d / 3600) % 24;
            long days = d / 86400;
            bot.sendMessage(channel, "Uptime: "+String.format("%02d:%02d:%02d:%02d", days, hours, minutes, seconds));
            
        // Display channels to which the bot is connected
        } else if (command.equals("channels")){
            String outStr = "Channels: ";
            Iterator<Channel> it = bot.getChannels().iterator();
            while(it.hasNext()){
                outStr += it.next().getName()+", ";
            }
            bot.sendMessage(channel, outStr.substring(0, outStr.length()-2));
            
        // Remove the user from the away list
        } else if (command.equals("back")){
            if (isUserAway(user)){
                toggleUserAway(user);
                bot.sendNotice(user, "You are no longer marked as away.");
            } else {
                bot.sendNotice(user, "You are not marked as away!");
            }
            
        // Add the user to the away list
        } else if (command.equals("away")){
            if (isUserAway(user)){
                bot.sendNotice(user, "You are already marked as away!");
            } else {
                toggleUserAway(user);
                bot.sendNotice(user, "You are now marked as away.");
            }
        
        // Toggles the user's simple status
        } else if (command.equals("simple")) {
            /*if (isUserSimple(user)){
                bot.sendNotice(user, "Private messages will now be sent via msg.");
            } else {
                bot.sendNotice(user, "Private messages will now be sent via notice.");
            }
            toggleUserSimple(user);*/
            
        // Display a list of users in a channel excluding ChanServ, the bot
        } else if (command.equals("ping")){
            // Grab the users in the channel
            User tUser;
            String tNick;
            String outStr = "Ping: ";
            Iterator<User> it = channel.getUsers().iterator();
            while(it.hasNext()){
                tUser = it.next();
                tNick = tUser.getNick();
                if (!tNick.equalsIgnoreCase("ChanServ") && 
                        !tNick.equalsIgnoreCase(bot.getNick()) &&
                        !awayList.contains(tUser.getHostmask())){
                    outStr += tNick+", ";
                }
            }
            bot.sendMessage(channel, outStr.substring(0, outStr.length()-2));
            
        // Display lag between the bot and user
        } else if (command.equals("lag")){
            bot.sendCTCPCommand(user, "PING " + System.currentTimeMillis());
            
        // Display the results of a coin flip
        } else if (command.equals("coin")){
            int n = randGen.nextInt(2);
            String outStr = formatBold(user.getNick()) + " flips a coin... and it lands on ";
            if (n == 0){
                outStr += formatBold("tails") + ".";
            } else {
                outStr += formatBold("heads") + ".";
            }
            bot.sendMessage(channel, outStr);
            
        // Displays greetings to user in channel
        } else if (command.equals("hi")){
            bot.sendMessage(channel, "Hi "+user.getNick()+"!");
            
        // Gives the user a cup of hot chocolate
        } else if (command.equals("cocoa")){
            if (params.length < 1) {
                bot.sendAction(channel, "hands " + user.getNick() + " a cup of hot chocolate. Cheers!");
            } else {
                if (isUserInChannel(channel, params[0])){
                    bot.sendAction(channel, "hands " + params[0] + " a cup of hot chocolate. Cheers!");
                } else {
                    bot.sendNotice(user, params[0] + " is not in " + channel.getName() + ". :(");
                }
            }
            
        // Stokes the fireplace
        } else if (command.equals("stoke")){
            bot.sendAction(channel, "stokes the glowing embers of the fire.");
            
        // Displays a list of commands
        } else if (command.equals("commands")){
            bot.sendMessage(channel, "Commands: channels, time, uptime, lag, cocoa, stoke, away, back, simple, ping, coin, hi, help");
            if (isAdmin(user)){
                bot.sendNotice(user, "Admin Commands: say, raw, join, part, op, deop, voice, devoice, admin, deadmin, clearaway, clearsimple, addclone, removeclone");
            }
        // Displays a help message
        } else if (command.equals("help")){
            bot.sendMessage(channel, user.getNick()+": Read the topic. For a list of non-game commands, type .commands.");
        }
    } 
    
    // Checks if the user is in the channel
    private boolean isUserInChannel(Channel channel, String nick){
        Iterator<User> it = channel.getUsers().iterator();
        while(it.hasNext()){
            if (it.next().getNick().equalsIgnoreCase(nick)){
                return true;
            }
        }
        return false;
    }
    
    // Determines if the user is on the away list
    private boolean isUserAway(User user) {
        return awayList.contains(user.getHostmask());
    }
    
    private void toggleUserAway(User user) {
        if (isUserAway(user)) {
            awayList.remove(user.getHostmask());
        } else {
            awayList.add(user.getHostmask());
        }
        saveHostmaskList("away.txt", awayList);
    }
    
    private boolean isUserSimple(User user) {
        return simpleList.contains(user.getHostmask());
    }
    
    private void toggleUserSimple(User user) {
        if (isUserSimple(user)) {
            simpleList.remove(user.getHostmask());
        } else {
            simpleList.add(user.getHostmask());
        }
        saveHostmaskList("simple.txt", simpleList);
    }
    
    // Adds a user to the admin list
    private void addAdmin(User user){
        adminList.add(user.getHostmask());
        saveHostmaskList("admins.txt", adminList);
    }
    
    // Removes a user from the admin list
    private void removeAdmin(User user){
        adminList.remove(user.getHostmask());
        saveHostmaskList("admins.txt", adminList);
    }
    
    // Determines if a user is an admin
    private boolean isAdmin (User user){
        return adminList.contains(user.getHostmask());
    }
    
    // Saves a list of hostmasks to the specified file
    private void saveHostmaskList(String file, ArrayList<String> hostList){
        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file)));
            for (int ctr = 0; ctr < hostList.size(); ctr++){
                out.println(hostList.get(ctr));
            }
            out.close();
        } catch (IOException e){
            bot.log("Error writing to " + file + "!");
        }      
    }
    
    // Loads a list of hostmasks from the specified file
    private ArrayList<String> loadHostList(String file){
        ArrayList<String> hostList = new ArrayList<String>();
        try {
            BufferedReader in = new BufferedReader(new FileReader(file));
            while (in.ready()) {
                hostList.add(in.readLine());
            }
            in.close();
            return hostList;
        } catch (IOException e){
            bot.log("Creating " + file + "...");
            saveHostmaskList(file, hostList);
            return hostList; // return empty list if unable to read file
        }      
    }
    
    // Disconnects the specified CloneBot
    public void removeClone(CloneBot cBot) {
        try {
            cBot.quitServer("Bad clone.");
            cloneList.remove(cBot);
        } catch (Exception e) {
            System.out.println("Error: " + e);
        }
    }
    
    // Disconnects all clones
    public void removeAllClones() {
        for (int ctr = 0; ctr < cloneList.size(); ctr++){
            removeClone(cloneList.get(ctr));
            ctr--;
        }
    }
    
    // Returns a decimal number formatted to 3 decimal places
    private String formatPing(double n) {
        return String.format("%.3f", n);
    }
    
    // Returns the original string with IRC bold tags
    private String formatBold(String str) {
        return Colors.BOLD + str + Colors.BOLD;
    }
}
