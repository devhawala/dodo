/*
Copyright (c) 2019, Dr. Hans-Walter Latz
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * The name of the author may not be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER "AS IS" AND ANY EXPRESS
OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package dev.hawala.xns.level4.filing;

import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;

/**
 * Representation of a session for an XNS File Service.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2019)
 */
public class Session {
	
	public static final int CONTINUANCE_DURANCE = 60_000; // ms
	
	private static int lastSessionId = (int)(System.currentTimeMillis() & 0xFFFF_FFFF);
	
	private static synchronized int createSessionId() {
		lastSessionId = (lastSessionId + 17) & 0xFFFF_FFFF;
		return lastSessionId;
	}
	
	private final Service service;
	private final ThreePartName userChsName;
	private final int sessionId;
	
	private final Long remoteHostId;
	private final int[] conversationKey;
	
	private final String username;
	
	private long nextContinuanceDue = System.currentTimeMillis() + CONTINUANCE_DURANCE;
	private boolean closed = false;
	
	public Session(Service service, ThreePartName username, Long remoteHostId, int[] conversationKey) {
		this.service = service;
		this.userChsName = username;
		this.sessionId = createSessionId();
		this.remoteHostId = remoteHostId;
		this.conversationKey = conversationKey;
		
		this.username = this.userChsName.toString();
	}
	
	public Service getService() {
		return this.service;
	}
	
	public int getSessionId() {
		return this.sessionId;
	}
	
	public ThreePartName getUserChsName() {
		return userChsName;
	}

	public String getUsername() {
		return username;
	}
	
	public Long getRemoteHostid() {
		return this.remoteHostId;
	}
	
	public int[] getConversationKey() {
		return this.conversationKey;
	}

	public int /* secs */ continueUse() {
		this.nextContinuanceDue = System.currentTimeMillis() + CONTINUANCE_DURANCE;
		return CONTINUANCE_DURANCE;
	}
	
	public boolean isOverdue() {
		return this.nextContinuanceDue < System.currentTimeMillis();
	}
	
	public void close() {
		this.service.dropSession(this);
		this.closed = true;
	}
	
	public boolean isClosed() {
		return this.closed;
	}

}
