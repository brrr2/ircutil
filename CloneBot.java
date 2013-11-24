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

import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.ConnectEvent;

/**
 * Allows the creation of dummy IRC users.
 * @author Yizhe Shen
 */
public class CloneBot extends PircBotX {
    public String cloneChannel;
    
     /* Listener for CloneBot initialization */
    private class InitClone extends ListenerAdapter<PircBotX> {
        @Override
        public void onConnect(ConnectEvent<PircBotX> event){
            CloneBot bot = (CloneBot) event.getBot();
            bot.joinChannel(bot.cloneChannel);
        }
    }
    
    /**
     * Creates a dummy IRC user.
     * @param nick the clone's nick
     * @param channel the channel for the clone to join
     * @throws java.lang.Exception
     */
    public CloneBot(String nick, String channel) throws Exception {
        super();
        version = "CloneBot";
        cloneChannel = channel;
        InitClone init = new InitClone();
        getListenerManager().addListener(init);
        setAutoNickChange(true);
        setName(nick);
        setLogin(nick);
    }
}