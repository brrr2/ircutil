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

import java.util.StringTokenizer;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.ConnectEvent;
import org.pircbotx.hooks.events.DisconnectEvent;

/**
 * Allows the creation of dummy IRC users.
 * @author Yizhe Shen
 */
public class CloneBot extends PircBotX {
    public String cloneChannels;
    
     /* Listener for CloneBot initialization */
    public static class InitClone extends ListenerAdapter<CloneBot> {
        @Override
        public void onConnect(ConnectEvent<CloneBot> event){
            CloneBot bot = event.getBot();
            StringTokenizer st = new StringTokenizer(bot.cloneChannels, ",");
            while(st.hasMoreTokens()) {
                bot.joinChannel(st.nextToken());
            }            
        }
    }
    
    /**
     * Creates a dummy IRC user.
     * @param nick the clone's nick
     * @param channels the channel for the clone to join
     * @throws java.lang.Exception
     */
    public CloneBot(String nick, String channels) throws Exception {
        super();
        version = "CloneBot";
        cloneChannels = channels;
        getListenerManager().addListener(new InitClone());
        getListenerManager().addListener(new Utilities(this, '@'));
        setAutoNickChange(true);
        setName(nick);
        setLogin(nick);
    }
    
    /**
     * Patch to eliminate exceptions during shutdown of the bot by removing 
     * channel caching any reconnecting.
     * @param noReconnect Not used
     */
    @Override
    public void shutdown(boolean noReconnect) {
        try {
            if (outputThread != null) outputThread.interrupt();
            if (inputThread != null) inputThread.interrupt();
        } catch (Exception e) {
            logException(e);
        }
        
        //Close the socket from here and let the threads die
        if (socket != null && !socket.isClosed())
            try {
                socket.shutdownInput();
                socket.close();
            } catch (Exception e) {
                logException(e);
            }
        
        //Close the DCC Manager
        try {
            dccManager.close();
        } catch (Exception ex) {
            //Not much we can do with it here. And throwing it would not let other things shutdown
            logException(ex);
        }
        
        //Clear relevant variables of information
        userChanInfo.clear();
        userNickMap.clear();
        channelListBuilder.finish();
        
        //Dispatch event
        getListenerManager().dispatchEvent(new DisconnectEvent(this));
        log("*** Disconnected.");
    }
}