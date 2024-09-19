/*
 * Copyright (C) 2019 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.igniterealtime.openfire.plugins.pushnotification;

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.OfflineMessageListener;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.OfflineMessage;

import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.bind.DatatypeConverter;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import nl.martijndwars.webpush.*;
import org.jivesoftware.util.*;
import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil;
import com.linkedin.urls.detection.*;
import com.linkedin.urls.*;
import org.broadbear.link.preview.*;
import net.sf.json.*;


public class WebPushInterceptor implements PacketInterceptor, OfflineMessageListener
{
    private static final Logger Log = LoggerFactory.getLogger( WebPushInterceptor.class );
    public static final ConcurrentHashMap<String, String> tokens = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, String> notifications = new ConcurrentHashMap<>();
    private static Cache url_source = CacheFactory.createLocalCache("URL Source Content");	

    /**
     * Invokes the interceptor on the specified packet. The interceptor can either modify
     * the packet, or throw a PacketRejectedException to block it from being sent or processed
     * (when read).<p>
     * <p>
     * An exception can only be thrown when <tt>processed</tt> is false which means that the read
     * packet has not been processed yet or the packet was not sent yet. If the exception is thrown
     * with a "read" packet then the sender of the packet will receive an answer with an error. But
     * if the exception is thrown with a "sent" packet then nothing will happen.<p>
     * <p>
     * Note that for each packet, every interceptor will be called twice: once before processing
     * is complete (<tt>processing==true</tt>) and once after processing is complete. Typically,
     * an interceptor will want to ignore one or the other case.
     *
     * @param packet    the packet to take action on.
     * @param session   the session that received or is sending the packet.
     * @param incoming  flag that indicates if the packet was read by the server or sent from
     *                  the server.
     * @param processed flag that indicates if the action (read/send) was performed. (PRE vs. POST).
     * @throws PacketRejectedException if the packet should be prevented from being processed.
     */
	 
	
    @Override
    public void interceptPacket( final Packet packet, final Session session, final boolean incoming, final boolean processed ) throws PacketRejectedException
    {
        if ( incoming ) {
            return;
        }

        if ( !processed ) {
            return;
        }

        if ( !(packet instanceof Message)) {
            return;
        }

        final String body = ((Message) packet).getBody();
        if ( body == null || body.isEmpty() )
        {
            return;
        }

        if (!(session instanceof ClientSession)) {
            return;
        }

        Element originId = ((Message) packet).getChildElement("origin-id", "urn:xmpp:sid:0");
		
		if (originId != null && originId.attribute("id") != null) {
			String id = originId.attribute("id").getStringValue();
			
			if (id != null) {
				UrlDetector parser = new UrlDetector(body, UrlDetectorOptions.Default);
				List<Url> found = parser.detect();

				for(Url url : found) {
					Log.debug("found URL " + url + " " + id);
					
					String sourceContent = getUrlSource(url.toString());

					if (sourceContent != null) {
						JSONObject source = new JSONObject(sourceContent);	
						
						String image = null;
						if (source.has("image")) image = source.getString("image");

						String descriptionShort = "";						
						if (source.has("descriptionShort")) descriptionShort = source.getString("descriptionShort");
						
						String title = "";						
						if (source.has("title"))  title = source.getString("title");
						
						Log.debug("found unfurl " + image + " " + descriptionShort + " " + title);
						
						if (!"".equals(descriptionShort) || !"".equals(title)) {							
							String msgId = "unfurl-" + System.currentTimeMillis();
							Message message = new Message();
							message.setFrom(packet.getFrom().toBareJID());
							message.setID(msgId);
							message.setTo(packet.getTo());
							message.setType(Message.Type.groupchat);
							message.addChildElement("x", "http://jabber.org/protocol/muc#user");	
							
							Element applyTo = message.addChildElement("apply-to", "urn:xmpp:fasten:0").addAttribute("id", id);
							applyTo.addElement("meta", "http://www.w3.org/1999/xhtml").addAttribute("property", "og:url").addAttribute("content", url.toString());
							applyTo.addElement("meta", "http://www.w3.org/1999/xhtml").addAttribute("property", "og:title").addAttribute("content", title);	
							applyTo.addElement("meta", "http://www.w3.org/1999/xhtml").addAttribute("property", "og:description").addAttribute("content", descriptionShort);
							
							if (image != null) {
								applyTo.addElement("meta", "http://www.w3.org/1999/xhtml").addAttribute("property", "og:image").addAttribute("content", image);								
							}
							
							message.addChildElement("origin-id", "urn:xmpp:sid:0").addAttribute("id", msgId);							
							XMPPServer.getInstance().getRoutingTable().routePacket(packet.getTo(), message);
						}							
					}					
				}
			}
		}
	
        final User user;
        try
        {
            user = XMPPServer.getInstance().getUserManager().getUser( packet.getTo().getNode() );
        }
        catch ( Exception e )
        {
            Log.debug( "Not a recognized user. " + packet.getTo());
            return;
        }

		if (user != null) {
			Log.debug( "If user '{}' has push services configured, pushes need to be sent for a message that just arrived.", user );
			tryPushNotification( user, body, packet.getFrom(), ((Message) packet).getType() );
		}
    }

    private void tryPushNotification( User user, String body, JID jid, Message.Type msgtype )
    {
        String username = user.getUsername();

        if (XMPPServer.getInstance().getPresenceManager().isAvailable( user ))
        {
            notifications.remove(username); // reset notification indicator
            return; // dont notify if user is online and available. let client handle that
        }

        if (!notifications.containsKey(username))   // notify once until user goes offline again
        {
            webPush(user, body, jid, msgtype, null);
        }
    }
	
	private String getUrlSource(String url)
	{
		String sourceText = (String) url_source.get(url);	
		
		if (sourceText == null) {
			SourceContent sourceContent = TextCrawler.scrape(url.toString(), 3);

			if (sourceContent != null) {
				JSONObject source = new JSONObject();				
				String image = null;
				try {
					source.put("image", image = sourceContent.getImages().get(0));
				} catch (Exception e) {}
				
				String title = sourceContent.getTitle();
				
				if (title != null && !"".equals(title)) {
					source.put("title", title);					
					source.put("descriptionShort", sourceContent.getDescription());
					sourceText = source.toString();
					url_source.put(url, sourceText);
				}
			}				
		}
		return sourceText;
	}
	
    /**
     * Notification message indicating that a message was not stored offline but bounced
     * back to the sender.
     *
     * @param message the message that was bounced.
     */
    @Override
    public void messageBounced( final Message message )
    {}

    /**
     * Notification message indicating that a message was stored offline since the target entity
     * was not online at the moment.
     *
     * @param message the message that was stored offline.
     */
    @Override
    public void messageStored( final OfflineMessage offlineMessage )
    {
		final Message message = (Message) offlineMessage;
		
        if ( message.getBody() == null || message.getBody().isEmpty() )
        {
            return;
        }

        Log.debug( "Message stored to offline storage. Try to send push notification." );
        final User user;
        try
        {
            user = XMPPServer.getInstance().getUserManager().getUser( message.getTo().getNode() );
            tryPushNotification( user, message.getBody(), message.getFrom(), message.getType() );
        }
        catch ( UserNotFoundException e )
        {
            Log.error( "Unable to find local user '{}'.", message.getTo().getNode(), e );
        }
    }
    /**
     * Push a payload to a subscribed web push user
     *
     *
     * @param user being pushed to.
     * @param publishOptions web push data stored.
     * @param body web push payload.
     */
    public void webPush( final User user, final String body, JID jid, Message.Type msgtype, String nickname )
    {
        try {
			String hostname = XMPPServer.getInstance().getServerInfo().getHostname();
			String public_port = JiveGlobals.getProperty( "httpbind.port.secure", "7443");	
			String url = "https://" + JiveGlobals.getProperty( "ofmeet.websockets.domain", hostname) + ":" + JiveGlobals.getProperty( "ofmeet.websockets.publicport", public_port);	
			
            String publicKey = user.getProperties().get("vapid.public.key");
            String privateKey = user.getProperties().get("vapid.private.key");

            if (publicKey == null) publicKey = JiveGlobals.getProperty("vapid.public.key", null);
            if (privateKey == null) privateKey = JiveGlobals.getProperty("vapid.private.key", null);

            if (publicKey != null && privateKey != null)
            {
                PushService pushService = new PushService()
                    .setPublicKey(publicKey)
                    .setPrivateKey(privateKey)
                    .setSubject("mailto:admin@" + XMPPServer.getInstance().getServerInfo().getXMPPDomain());

                String username = user.getUsername();
                String token = tokens.get(username);

                if (token == null)
                {
                    token = TimeBasedOneTimePasswordUtil.generateBase32Secret();
                    tokens.put(token, username);
                }

                String avatar = null;
                String fullname = jid.asBareJID().toString();
                try {
                    Element vCard = VCardManager.getProvider().loadVCard(jid.getNode());
                    Element ldapPhotoElem = vCard.element("PHOTO");
                    Element ldapFNElem = vCard.element("FN");

                    if (ldapFNElem != null) fullname = ldapFNElem.getTextTrim();

                    if (ldapPhotoElem != null) {
                        Element ldapBinvalElem  = ldapPhotoElem.element("BINVAL");
                        Element ldapTypeElem    = ldapPhotoElem.element("TYPE");

                        if (ldapTypeElem != null && ldapBinvalElem != null) {
                            avatar = "data:" + ldapTypeElem.getTextTrim() + ";base64," + ldapBinvalElem.getTextTrim();
                        }
                    }
                } catch (Exception e) {}

                boolean notified = false;

                for (String key : user.getProperties().keySet())
                {
                    if (key.startsWith("webpush.subscribe."))
                    {
                        Subscription subscription = new Gson().fromJson(user.getProperties().get(key), Subscription.class);
                        Stanza stanza = new Stanza(msgtype == Message.Type.chat ? "chat" : "groupchat", jid.asBareJID().toString(), body, nickname, token, avatar, fullname, url);
                        Notification notification = new Notification(subscription, (new Gson().toJson(stanza)).toString());
                        HttpResponse response = pushService.send(notification);
                        int statusCode = response.getStatusLine().getStatusCode();
                        notified = true;

                        Log.debug( "For user '{}', Web push notification response '{}'", user.toString(), response.getStatusLine().getStatusCode() );
                    }
                }

                if (notified)
                {
                    notifications.put(username, token);
                }
            }
        } catch (Exception e) {
            Log.warn( "An exception occurred while trying send a web push for user '{}'.", new Object[] { user, e } );
        }
    }
}
