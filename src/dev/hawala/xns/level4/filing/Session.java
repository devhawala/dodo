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
	
	public static final int CONTINUANCE_DURANCE_DEFAULT = 60_000; // 1 minute in ms
	public static final int CONTINUANCE_DURANCE_FILING4 = 3600_000; // 1 hour in ms
	
	private static int lastSessionId = (int)(System.currentTimeMillis() & 0xFFFF_FFFF);
	
	private static synchronized int createSessionId() {
		lastSessionId = (lastSessionId + 17) & 0xFFFF_FFFF;
		return lastSessionId;
	}
	
	private final Service service;
	private final ThreePartName userChsName;
	private final int sessionId;
	
	private final int[] conversationKey;
	
	private final String username;
	
	private final int filingVersion;
	private final int continuanceDuranceMillisecs;
	
	private long nextContinuanceDueMillisecs = System.currentTimeMillis() + CONTINUANCE_DURANCE_DEFAULT;
	private boolean closed = false;
	private boolean disableClosing = false;
	
	public Session(Service service, ThreePartName username, int[] conversationKey, int filingVersion) {
		this.service = service;
		this.userChsName = username;
		this.sessionId = createSessionId();
		this.conversationKey = conversationKey;
		this.filingVersion = filingVersion;
		this.continuanceDuranceMillisecs = (filingVersion < 5) ? CONTINUANCE_DURANCE_FILING4 : CONTINUANCE_DURANCE_DEFAULT;
		
		this.nextContinuanceDueMillisecs = System.currentTimeMillis() + this.continuanceDuranceMillisecs;
		
		this.username = this.userChsName.toString();
		
		this.logStatus("created");
	}
	
	private void logStatus(String msg) {
		// System.out.printf("+++++++++++++++++++++++++++++++++++++++++++++++++ Session 0x%08X :: %s\n", this.sessionId, msg);
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
	
	public int[] getConversationKey() {
		return this.conversationKey;
	}
	
	public int getFilingVersion() {
		return this.filingVersion;
	}

	public synchronized int /* seconds */ continueUse() {
		this.logStatus("continueUse");
		this.disableClosing = false;
		this.nextContinuanceDueMillisecs = System.currentTimeMillis() + this.continuanceDuranceMillisecs;
		return this.continuanceDuranceMillisecs / 1000;
	}
	
	public synchronized boolean isOverdue() {
		return this.nextContinuanceDueMillisecs < System.currentTimeMillis();
	}
	
	public synchronized void close() {
		this.logStatus("closed");
		if (this.disableClosing) {
			return;
		}
		this.service.dropSession(this);
		this.closed = true;
	}
	
	public synchronized boolean isClosed() {
		return this.closed;
	}
	
	public synchronized void holdClosing() {
		this.disableClosing = true;
	}

}