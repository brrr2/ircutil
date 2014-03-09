/*
    Copyright (C) 2013-2014 Yizhe Shen <brrr@live.ca>

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
    
    private final int PING_LIMIT = 600;
    private long LAST_PING = 0;
    
    private PircBotX bot;
    private char commandChar;
    private long startTime;
    private ArrayList<String> awayList;
    private ArrayList<String> adminList;
    private ArrayList<String> notSimpleList;
    private ArrayList<CloneBot> cloneList;
    Random randGen;
    
    public Utilities(PircBotX parent, char commChar){
        bot = parent;
        commandChar = commChar;
        startTime = System.currentTimeMillis();
        randGen = new Random();
        awayList = loadHostList("away.txt");
        adminList = loadHostList("admins.txt");
        notSimpleList = loadHostList("simple.txt");
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
            String command = st.nextToken();
            String[] params = new String[st.countTokens()];
            for (int ctr = 0; ctr < params.length; ctr++){
                params[ctr] = st.nextToken();
            }
            processCommand(event.getChannel(), event.getUser(), command, params, msg);
        }
    }
    
    @Override
    public void onPrivateMessage (PrivateMessageEvent<PircBotX> event){
        String msg = event.getMessage();
        
        // Parse the private message
        StringTokenizer st = new StringTokenizer(msg);
        String command = st.nextToken();
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
            informUser(user, "Lag: " + formatPing((double) (System.currentTimeMillis() - Long.parseLong(params[1])) / 1000) + " seconds");
        }
    }
    
    /**
     * Processes messages given to the the bot through PM. These commands
     * should be accessible only by admins.
     * 
     * @param user the User that sent the command
     * @param command the command
     * @param params the command parameters
     * @param msg the entire original message
     */
    public void processPM(User user, String command, String[] params, String msg){
        // Check if the user is an admin
        if (!isAdmin(user)){
            // Do nothing to prevent potential spamming.
            //informUser(user, "You are not authorized to make this command.");
        } else if (command.equalsIgnoreCase("join")){
            join(user, params, msg);
        } else if (command.equalsIgnoreCase("part") || command.equalsIgnoreCase("leave")){
            part(user, params, msg);
        } else if (command.equalsIgnoreCase("op")){
            op(user, params, msg);
        } else if (command.equalsIgnoreCase("deop")) {
            deop(user, params, msg);
        } else if (command.equalsIgnoreCase("voice")) {
            voice(user, params, msg);
        } else if (command.equalsIgnoreCase("devoice")) {
            devoice(user, params, msg);
        } else if (command.equalsIgnoreCase("addadmin")){
            addAdmin(user, params, msg);
        } else if (command.equalsIgnoreCase("removeadmin")) {
            removeAdmin(user, params, msg);
        } else if (command.equalsIgnoreCase("listadmins")) {
            listAdmins(user, params, msg);
        } else if (command.equalsIgnoreCase("msg") || command.equalsIgnoreCase("say")){
            msg(user, params, msg);
        } else if (command.equalsIgnoreCase("notice")) {
            notice(user, params, msg);
        } else if (command.equalsIgnoreCase("action")) {
            action(user, params, msg);
        } else if (command.equalsIgnoreCase("raw")){
            raw(user, params, msg);
        } else if (command.equalsIgnoreCase("resetaway")){
            resetAway(user, params, msg);
        } else if (command.equalsIgnoreCase("resetsimple")) {
            resetSimple(user, params, msg);
        } else if (command.equalsIgnoreCase("addclone")) {
            addClone(user, params, msg);
        } else if (command.equalsIgnoreCase("removeclone")) {
            removeClone(user, params, msg);
        } else if (command.equalsIgnoreCase("removeallclones")) {
            removeAllClones(user, params, msg);
        } else if (command.equalsIgnoreCase("listclones")) {
            listClones(user, params, msg);
        }
    }
    
    /**
     * Process an in-channel command.
     * 
     * @param channel the originating channel of the command
     * @param user the user who made the command
     * @param command the command
     * @param params the parameters after the command
     */
    public void processCommand(Channel channel, User user, String command, String[] params, String msg){    	 
        if (command.equalsIgnoreCase("time")){
            time(channel, user, params, msg);
        } else if (command.equalsIgnoreCase("uptime")){
            uptime(channel, user, params, msg);
        } else if (command.equalsIgnoreCase("channels")){
            channels(channel, user, params, msg);
        } else if (command.equalsIgnoreCase("back")){
            back(channel, user, params, msg);
        } else if (command.equalsIgnoreCase("away")){
            away(channel, user, params, msg);
        } else if (command.equalsIgnoreCase("simple")) {
            simple(channel, user, params, msg);
        } else if (command.equalsIgnoreCase("ping")){
            ping(channel, user, params, msg);
        } else if (command.equalsIgnoreCase("lag")){
            lag(channel, user, params, msg);
        } else if (command.equalsIgnoreCase("coin")){
            coin(channel, user, params, msg);
        } else if (command.equalsIgnoreCase("hi")){
            hi(channel, user, params, msg);
        } else if (command.equalsIgnoreCase("cocoa")){
            cocoa(channel, user, params, msg);
        } else if (command.equalsIgnoreCase("stoke")){
            stoke(channel, user, params, msg);
        } else if (command.equalsIgnoreCase("commands")){
            commands(channel, user, params, msg);
        } else if (command.equalsIgnoreCase("help")){
            help(channel, user, params, msg);
        }
    } 
    
    // Private message command methods
    /**
     * Joins the specified channel.
     * @param user
     * @param params 
     */
    public void join(User user, String[] params, String msg) {
        if (params.length < 1) {
            informUser(user, "Missing parameter(s).");
        } else {
            String channel = params[0];
            if (!channel.startsWith("#")){
                bot.joinChannel("#" + channel);
            } else {
                bot.joinChannel(channel);
            }
        }
    }
    
    /**
     * Parts the specified channel.
     * @param user
     * @param params 
     */
    public void part(User user, String[] params, String msg) {
        if (params.length < 1) {
            informUser(user, "Missing parameter(s).");
        } else {
            String channel = params[0];
            if (bot.channelExists(channel)){
                bot.partChannel(bot.getChannel(channel));
            } else {
                informUser(user, bot.getNick() + " is not in " + channel + ".");
            }
        }
    }
    
    /**
     * Ops the specified user in the specified channel.
     * @param user
     * @param params 
     */
    public void op(User user, String[] params, String msg) {
        if (params.length < 2){
            informUser(user, "Missing parameter(s).");
        } else {
            String channel = params[0];
            String nick = params[1];
            // Check if the bot is in the specified channel
            if (!bot.channelExists(channel)){
                informUser(user, bot.getNick() + " is not in " + channel + ".");
            } else {
                Channel tChannel = bot.getChannel(channel);
                // Check if the bot has Ops in that channel
                if (!tChannel.isOp(bot.getUserBot())){
                    informUser(user, bot.getNick() + " is not authorized to do this in " + channel + ".");
                } else {
                    // Check if the user to Op is in the channel
                    if (!isUserInChannel(tChannel, nick)){
                        informUser(user, nick + " is not in " + channel + ".");
                    } else {
                        User tUser = bot.getUser(nick);
                        bot.op(tChannel, tUser);
                    }
                }
            }
        }
    }
    
    /**
     * DeOps the specified user in the specified channel.
     * @param user
     * @param params 
     */
    public void deop(User user, String[] params, String msg) {
        if (params.length < 2){
            informUser(user, "Missing parameter(s).");
        } else {
            String channel = params[0];
            String nick = params[1];
            // Check if the bot is in the specified channel
            if (!bot.channelExists(channel)){
                informUser(user, bot.getNick() + " is not in " + channel + ".");
            } else {
                Channel tChannel = bot.getChannel(channel);
                // Check if the bot has Ops in that channel
                if (!tChannel.isOp(bot.getUserBot())){
                    informUser(user, bot.getNick() + " is not authorized to do this in " + channel + ".");
                } else {
                    // Check if the user to Op is in the channel
                    if (!isUserInChannel(tChannel, nick)){
                        informUser(user, nick + " is not in " + channel + ".");
                    } else {
                        User tUser = bot.getUser(nick);
                        bot.deOp(tChannel, tUser);
                    }
                }
            }
        }
    }
    
    /**
     * Voices the specified user in the specified channel.
     * @param user
     * @param params 
     */
    public void voice(User user, String[] params, String msg) {
        if (params.length < 2){
            informUser(user, "Missing parameter(s).");
        } else {
            String channel = params[0];
            String nick = params[1];
            // Check if the bot is in the specified channel
            if (!bot.channelExists(channel)){
                informUser(user, bot.getNick() + " is not in " + channel + ".");
            } else {
                Channel tChannel = bot.getChannel(channel);
                // Check if the bot has Ops in that channel
                if (!tChannel.isOp(bot.getUserBot())){
                    informUser(user, bot.getNick() + " is not authorized to do this in " + channel + ".");
                } else {
                    // Check if the user to Op is in the channel
                    if (!isUserInChannel(tChannel, nick)){
                        informUser(user, nick + " is not in " + channel + ".");
                    } else {
                        User tUser = bot.getUser(nick);
                        bot.voice(tChannel, tUser);
                    }
                }
            }
        }
    }
    
    /**
     * Devoices the specified user in the specified channel
     * @param user
     * @param params 
     */
    public void devoice(User user, String[] params, String msg) {
        if (params.length < 2){
            informUser(user, "Missing parameter(s).");
        } else {
            String channel = params[0];
            String nick = params[1];
            // Check if the bot is in the specified channel
            if (!bot.channelExists(channel)){
                informUser(user, bot.getNick() + " is not in " + channel + ".");
            } else {
                Channel tChannel = bot.getChannel(channel);
                // Check if the bot has Ops in that channel
                if (!tChannel.isOp(bot.getUserBot())){
                    informUser(user, bot.getNick() + " is not authorized to do this in " + channel + ".");
                } else {
                    // Check if the user to Op is in the channel
                    if (!isUserInChannel(tChannel, nick)){
                        informUser(user, nick + " is not in " + channel + ".");
                    } else {
                        User tUser = bot.getUser(nick);
                        bot.deVoice(tChannel, tUser);
                    }
                }
            }
        }
    }
    
    /**
     * Adds a bot admin.
     * @param user
     * @param params 
     */
    public void addAdmin(User user, String[] params, String msg) {
        if (params.length < 1){
            informUser(user, "Missing parameter(s).");
        } else {
            String nick = params[0];
            // Find if user to add is in any of the channels the bot is in
            User tUser;
            Iterator<Channel> it = bot.getChannels().iterator();
            Iterator<User> it2;
            while(it.hasNext()){
                it2 = it.next().getUsers().iterator();
                while(it2.hasNext()){
                    tUser = it2.next();
                    // If we find the user, we can add them to the admin list
                    if (tUser.getNick().equalsIgnoreCase(nick)){
                        adminList.add(tUser.getHostmask());
                        saveHostList("admins.txt", adminList);
                        return;
                    }
                }
            }

            // If user is not in any channel to which the bot is joined
            informUser(user, nick + " was not found!");
        }
    }
    
    /**
     * Removes a bot admin.
     * @param user
     * @param params 
     */
    public void removeAdmin(User user, String[] params, String msg) {
        if (params.length < 1){
            informUser(user, "Missing parameter(s).");
        } else {
            String nick = params[0];
            // Find if user to remove is in any of the channels the bot is in
            User tUser;
            Iterator<Channel> it = bot.getChannels().iterator();
            Iterator<User> it2;
            while(it.hasNext()){
                it2 = it.next().getUsers().iterator();
                while(it2.hasNext()){
                    tUser = it2.next();
                    // If we find the user, we can remove them from the admin list
                    if (tUser.getNick().equalsIgnoreCase(nick)){
                        adminList.remove(tUser.getHostmask());
                        saveHostList("admins.txt", adminList);
                        return;
                    }
                }
            }

            // If user is not in any channel to which the bot is joined
            informUser(user, nick + " was not found!");
        }
    }
    
    /**
     * Lists the current bot admins.
     * @param user 
     */
    public void listAdmins(User user, String[] params, String msg) {
        if (adminList.isEmpty()) {
            informUser(user, "No admins to list.");
        } else {
            String outStr = String.format("Admins (%d): ", adminList.size());
            for (String admin : adminList) {
                outStr += admin + ", ";
            }
            informUser(user, outStr.substring(0, outStr.length()-2));
        }
    }
    
    /**
     * Sends a message to the specified recipient.
     * @param user
     * @param params
     * @param msg 
     */
    public void msg(User user, String[] params, String msg) {
        if (params.length < 2) {
            informUser(user, "Missing parameter(s).");
        } else {
            String recip = params[0];
            int msgLoc = msg.indexOf(recip) + recip.length() + 1;
            bot.sendMessage(recip, msg.substring(msgLoc));
        }
    }
    
    /**
     * Sends a notice to the specified recipient.
     * @param user
     * @param params
     * @param msg 
     */
    public void notice(User user, String[] params, String msg) {
        if (params.length < 2) {
            informUser(user, "Missing parameter(s).");
        } else {
            String recip = params[0];
            int msgLoc = msg.indexOf(recip) + recip.length() + 1;
            bot.sendNotice(recip, msg.substring(msgLoc)); 
        }
    }
    
    /**
     * Sends an action to the specified recipient.
     * @param user
     * @param params
     * @param msg 
     */
    public void action(User user, String[] params, String msg) {
        if (params.length < 2) {
            informUser(user, "Missing parameter(s).");
        } else {
            String recip = params[0];
            int msgLoc = msg.indexOf(recip) + recip.length() + 1;
            bot.sendAction(recip, msg.substring(msgLoc)); 
        }
    }
    
    /**
     * Sends a raw line to the server.
     * @param user
     * @param msg 
     */
    public void raw(User user, String[] params, String msg) {
        bot.sendRawLine(msg.substring(4));
    }
    
    /**
     * Erases all hosts from away.txt.
     * @param user 
     */
    public void resetAway(User user, String[] params, String msg) {
        awayList.clear();
        saveHostList("away.txt", awayList);
        informUser(user, "The away list has been emptied.");
    }
    
    /**
     * Erases all hosts from simple.txt.
     * @param user 
     */
    public void resetSimple(User user, String[] params, String msg) {
        notSimpleList.clear();
        saveHostList("simple.txt", notSimpleList);
        informUser(user, "The simple list has been emptied.");
    }
    
    /**
     * Adds a CloneBot to the specified channel.
     * @param user
     * @param params 
     */
    public void addClone(User user, String[] params, String msg) {
        if (params.length < 2) {
            informUser(user, "Missing parameter(s).");
        } else {
            String nick = params[0];
            String channel = params[1];
            try {
                CloneBot newClone = new CloneBot(nick, channel);
                newClone.connect(bot.getServer());
                cloneList.add(newClone);
            } catch (Exception e) {
                bot.log("Error: " + e);
                informUser(user, "Error: " + e);
            }
        }
    }
    
    /**
     * Removes the specified CloneBot.
     * @param user
     * @param params 
     */
    public void removeClone(User user, String[] params, String msg) {
        if (params.length < 1) {
            informUser(user, "Missing parameter(s).");
        } else {
            try {
                String nick = params[0];
                for (CloneBot cBot : cloneList) {
                    if (cBot.getNick().equalsIgnoreCase(nick)) {
                        cBot.quitServer("Bad clone.");
                        cloneList.remove(cBot);
                        break;
                    }
                }
            } catch (Exception e) {
                bot.log("Error: " + e);
                informUser(user, "Error: " + e);
            }
        }
    }
    
    /**
     * Disconnects all clones.
     * @param user
     */
    public void removeAllClones(User user, String[] params, String msg) {
        try {
            for (CloneBot cBot : cloneList) {
                cBot.quitServer("Bad clone.");
            }
            cloneList.clear();
        } catch (Exception e) {
            bot.log("Error: " + e);
            informUser(user, "Error: " + e);
        }
    }
    
    /**
     * Lists the CloneBots currently running.
     * @param user 
     */
    public void listClones(User user, String[] params, String msg) {
        if (cloneList.isEmpty()) {
            informUser(user, "No clones to list.");
        } else {
            String outStr = String.format("Clones (%d): ", cloneList.size());
            for (CloneBot cBot : cloneList) {
                outStr += cBot.getNick() + ", ";
            }
            informUser(user, outStr.substring(0, outStr.length()-2));
        }
    }
    
    // In-channel command methods
    /**
     * Displays current host time.
     * @param channel 
     */
    public void time(Channel channel, User user, String[] params, String msg) {
        bot.sendMessage(channel, "Time: " + new Date().toString());
    }
    
    /**
     * Displays the amount of time since activation in dd:hh:mm:ss form.
     * @param channel 
     */
    public void uptime(Channel channel, User user, String[] params, String msg) {
        long d = (System.currentTimeMillis() - startTime)/1000;
        long seconds = d % 60;
        long minutes = (d / 60) % 60;
        long hours = (d / 3600) % 24;
        long days = d / 86400;
        bot.sendMessage(channel, "Uptime: "+String.format("%02d:%02d:%02d:%02d", days, hours, minutes, seconds));
    }    
    
    /**
     * Displays channels to which the bot is connected.
     * @param channel 
     */
    public void channels(Channel channel, User user, String[] params, String msg) {
        String outStr = "Channels: ";
        Iterator<Channel> it = bot.getChannels().iterator();
        while(it.hasNext()){
            outStr += it.next().getName() + ", ";
        }
        bot.sendMessage(channel, outStr.substring(0, outStr.length()-2));
    }
    
    /**
     * Removes the user from the away list.
     * @param user 
     */
    public void back(Channel channel, User user, String[] params, String msg) {
        if (isUserAway(user)){
            awayList.remove(user.getHostmask());
            saveHostList("away.txt", awayList);
            informUser(user, "You are no longer marked as away.");
        } else {
            informUser(user, "You are not marked as away!");
        }
    }
            
    /**
     * Adds the user to the away list.
     * @param user 
     */
    public void away(Channel channel, User user, String[] params, String msg) {
        if (isUserAway(user)) {
            informUser(user, "You are already marked as away!");
        } else {
            awayList.add(user.getHostmask());
            saveHostList("away.txt", awayList);
            informUser(user, "You are now marked as away.");
        }
    }
        
    /**
     * Toggles the user's simple status.
     * @param user 
     */
    public void simple(Channel channel, User user, String[] params, String msg) {
        if (isUserNotSimple(user)) {
            notSimpleList.remove(user.getHostmask());
            //informUser(user, "Private messages will now be sent via notice.");
        } else {
            notSimpleList.add(user.getHostmask());
            //informUser(user, "Private messages will now be sent via msg.");
        }
        saveHostList("simple.txt", notSimpleList);
    }
    
    /**
     * Displays a list of users in a channel excluding ChanServ, the bot and
     * users who are in the awayList.
     * @param user 
     */
    public void ping(Channel channel, User user, String[] params, String msg) {
        // Check for rate-limit
        if (LAST_PING != 0 && System.currentTimeMillis() - LAST_PING < PING_LIMIT * 1000) {
            informUser(user, "This command is rate-limited. Please wait to use it again.");
            return;
        }
        
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
        
        LAST_PING = System.currentTimeMillis();
    }
    
    /**
     * Sends a CTCP PING to the user.
     * @param user 
     */
    public void lag(Channel channel, User user, String[] params, String msg) {
        bot.sendCTCPCommand(user, "PING " + System.currentTimeMillis());
    }
    
    /**
     * Displays the results of a coin flip.
     * @param user
     * @param channel 
     */
    public void coin(Channel channel, User user, String[] params, String msg) {
        int n = randGen.nextInt(2);
        String outStr = formatBold(user.getNick()) + " flips a coin... and it lands on ";
        if (n == 0){
            outStr += formatBold("tails") + ".";
        } else {
            outStr += formatBold("heads") + ".";
        }
        bot.sendMessage(channel, outStr);
    }
    
    /**
     * Displays greetings to user in channel.
     * @param user
     * @param channel 
     */
    public void hi(Channel channel, User user, String[] params, String msg) {
        bot.sendMessage(channel, "Hi " + user.getNick() + "!");
    }
    
    /**
     * Hands out cups of hot chocolate.
     * @param user
     * @param channel
     * @param params 
     */
    public void cocoa(Channel channel, User user, String[] params, String msg) {
        if (params.length < 1) {
            bot.sendAction(channel, "hands " + user.getNick() + " a cup of hot chocolate. Cheers!");
        } else {
            String recip = params[0];
            if (isUserInChannel(channel, recip)){
                bot.sendAction(channel, "hands " + recip + " a cup of hot chocolate. Cheers!");
            } else {
                informUser(user, recip + " is not in " + channel.getName() + ". :(");
            }
        }
    }
    
    /**
     * Stokes the fire.
     * @param channel 
     */
    public void stoke(Channel channel, User user, String[] params, String msg) {
        bot.sendAction(channel, "stokes the glowing embers of the fire.");
    }
    
    /**
     * Displays a list of commands available in this module.
     * @param channel 
     */
    public void commands(Channel channel, User user, String[] params, String msg) {
        bot.sendMessage(channel, "Commands: channels, time, uptime, lag, cocoa, stoke, away, back, simple, ping, coin, hi, help, commands");
        if (isAdmin(user)){
            informUser(user, "Admin Commands: msg, notice, action, raw, join, part, op, deop, voice, devoice, addadmin, removeadmin, listadmins, resetaway, resetsimple, addclone, removeclone, removeallclones, listclones");
        }
    }
            
    /**
     * Displays a help message.
     * @param channel
     * @param user 
     */
    public void help(Channel channel, User user, String[] params, String msg) {
        bot.sendMessage(channel, user.getNick() + ": Please read the topic.");
    }
    
    // Helper methods
    /**
     * Checks if a user is in a channel.
     * @param channel the channel to check
     * @param nick the user's nick
     * @return true if the user is found in the channel
     */
    private boolean isUserInChannel(Channel channel, String nick){
        Iterator<User> it = channel.getUsers().iterator();
        while(it.hasNext()){
            if (it.next().getNick().equalsIgnoreCase(nick)){
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if the user is on the away list.
     * @param user the user to check
     * @return true if on the away list
     */
    private boolean isUserAway(User user) {
        return awayList.contains(user.getHostmask());
    }
    
    /**
     * Checks if the user is on the simple list.
     * @param user the user to check
     * @return true if on the simple list
     */
    private boolean isUserNotSimple(User user) {
        return notSimpleList.contains(user.getHostmask());
    }
    
    /**
     * Determines if a user is an admin for the bot.
     * @param user the user to check
     * @return true if the user is on the admin list
     */
    private boolean isAdmin (User user){
        return adminList.contains(user.getHostmask());
    }
    
    /**
     * Saves a list of hosts to the specified file.
     * @param file the file path
     * @param hostList ArrayList of hosts
     */
    private void saveHostList(String file, ArrayList<String> hostList){
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
    
    /**
     * Loads a list of hosts from the specified file.
     * @param file the file path
     * @return ArrayList of hosts
     */
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
            saveHostList(file, hostList);
            return hostList; // return empty list if unable to read file
        }      
    }
    
    /**
     * Sends a private message to the target user.
     * @param user the target
     * @param msg the message
     */
    private void informUser(User user, String msg) {
        if (isUserNotSimple(user)) {
            bot.sendMessage(user, msg);
        } else {
            bot.sendNotice(user, msg);
        }
    }
    
    /**
     * Returns a decimal number formatted as a String to 3 decimal places.
     * @param n the number
     * @return the formatted number as a String
     */
    private String formatPing(double n) {
        return String.format("%.3f", n);
    }
    
    /**
     * Returns the original string with IRC bold tags.
     * @param str the original string
     * @return the original string sandwiched with bold tags
     */
    private String formatBold(String str) {
        return Colors.BOLD + str + Colors.BOLD;
    }
}
