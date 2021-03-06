/*******************************************************************************
 * Copyright © Squid Solutions, 2016
 *
 * This file is part of Open Bouquet software.
 *  
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * There is a special FOSS exception to the terms and conditions of the 
 * licenses as they are applied to this program. See LICENSE.txt in
 * the directory of this program distribution.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Squid Solutions also offers commercial licenses with additional warranties,
 * professional functionalities or services. If you purchase a commercial
 * license, then it supersedes and replaces any other agreement between
 * you and Squid Solutions (above licenses and LICENSE.txt included).
 * See http://www.squidsolutions.com/EnterpriseBouquet/
 *******************************************************************************/
package com.squid.kraken.v4.api.core.websocket;

import java.util.Set;

import javax.websocket.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squid.kraken.v4.api.core.AccessRightsUtils;
import com.squid.kraken.v4.api.core.websocket.NotificationWebsocket.SessionMessage;
import com.squid.kraken.v4.model.AccessRight.Role;
import com.squid.kraken.v4.model.AccessToken;
import com.squid.kraken.v4.model.AccessTokenPK;
import com.squid.kraken.v4.model.Persistent;
import com.squid.kraken.v4.model.ProjectAnalysisJob;
import com.squid.kraken.v4.model.ProjectAnalysisJobPK;
import com.squid.kraken.v4.model.ProjectFacetJob;
import com.squid.kraken.v4.model.ProjectFacetJobPK;
import com.squid.kraken.v4.model.State;
import com.squid.kraken.v4.model.StatePK;
import com.squid.kraken.v4.persistence.AppContext;
import com.squid.kraken.v4.persistence.DataStoreEvent;
import com.squid.kraken.v4.persistence.DataStoreEvent.Emitter;
import com.squid.kraken.v4.persistence.DataStoreEvent.Type;
import com.squid.kraken.v4.persistence.DataStoreEventObserver;

/**
 * Observes the Meta-Model.
 */
public class NotificationWebsocketMetaModelObserver implements
		DataStoreEventObserver {
	
	private static final Logger logger = LoggerFactory
			.getLogger(NotificationWebsocketMetaModelObserver.class);

	private static NotificationWebsocketMetaModelObserver instance;

	public static synchronized NotificationWebsocketMetaModelObserver getInstance() {
		if (instance == null) {
			instance = new NotificationWebsocketMetaModelObserver();
		}
		return instance;
	}

	private NotificationWebsocketMetaModelObserver() {
	}

	@Override
	public void notifyEvent(DataStoreEvent event) {
		try {
			if (acceptEvent(event)) {
				Object sourceEvent = event.getSource();
				if ((event.getType() == Type.DELETE)
						&& ((sourceEvent instanceof AccessToken) || (sourceEvent instanceof AccessTokenPK))) {
					// handle logout events
					String tokenId;
					if (sourceEvent instanceof AccessToken) {
						tokenId = ((AccessToken) sourceEvent).getOid();
					} else {
						tokenId = ((AccessTokenPK) sourceEvent).getTokenId();
					}
					Set<Session> sessions = NotificationWebsocket
							.getSessionsByToken(tokenId);
					// send logout message to all corresponding sessions
					for (Session s : sessions) {
						if (s.isOpen()) {
							Emitter emitter = event.getEmitter();
							SessionMessage message = new SessionMessage(
									emitter.getSessionId(), true, false);
							send(null, emitter, s, message);
						}
					}
				} else {
					if (sourceEvent instanceof Persistent<?>) {
						Persistent<?> source = (Persistent<?>) sourceEvent;
						DataStoreEvent eventOut = null;
						Set<Session> sessions = NotificationWebsocket.getSessions();
						for (Session s : sessions) {
							if (s.isOpen()) {
								// check access rights
								AppContext ctx = (AppContext) s.getUserProperties()
										.get("ctx");
								if (AccessRightsUtils.getInstance().hasRole(ctx,
										source, Role.READ)) {
									if (eventOut == null) {
										// only send out the source event id (not to
										// expose
										// private properties)
										eventOut = new DataStoreEvent(
												event.getEmitter(), null,
												source.getId(), event.getType(),
												event.isExternal());
									}
									// send the event
									send(ctx, event.getEmitter(), s, eventOut);
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			// global exception catching to avoid failure in operations
			logger.warn("notifyEvent failed for event : "+event, e);
		}
	}

	private void send(AppContext ctx, Emitter emitter, Session s, Object object) {
		if (ctx == null) {
			ctx = (AppContext) s.getUserProperties().get("ctx");
		}
		// do not send back events to emitter
		if (emitter == null
				|| (!ctx.getSessionId().equals(emitter.getSessionId()))) {
			// send
			try {
				s.getAsyncRemote().sendObject(object);
			} catch (IllegalStateException e) {
				// T1562 getAsyncRemote appears not to be thread-safe but I prefer to ignore than locking
				logger.info("Message sending failed : "+e.getMessage());
			}
		}
	}

	private boolean acceptEvent(DataStoreEvent event) {
		Object origin = event.getOrigin();
		Object sourceEvent = event.getSource();
		if (origin == sourceEvent || origin == null) {
			// shortcut to avoid loosing too much time with stuff we don't mind
			if (sourceEvent instanceof ProjectFacetJob
					|| sourceEvent instanceof ProjectFacetJobPK
					|| sourceEvent instanceof ProjectAnalysisJob
					|| sourceEvent instanceof ProjectAnalysisJobPK
					|| sourceEvent instanceof State
					|| sourceEvent instanceof StatePK) {
				// just in case...
				return false;
			} else {
				return true;
			}
		}
		// else
		return false;
	}

}
