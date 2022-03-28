/*
Copyright (c) 2020, Dr. Hans-Walter Latz
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

package dev.hawala.xns.level4.mailing;

import java.util.List;

import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;

/**
 * Representation for a Session for managing a mailbox by a user agent,
 * holding the session specific copies of the mailbox data.
 * <p>
 * There may be more than one session for the same mailbox at the same
 * time, it is the responsibility of the mail service to reflect changes
 * in one session (like deleting a mail) to the other sessions for the same
 * mailbox.
 *  </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2020)
 */
public class MailSession {
	
	/*
	 * static functionality for generating unique session IDs
	 */
	
	private static int lastSessionId = (int)(System.currentTimeMillis() & 0xFFFF_FFFF);
	
	private static synchronized int createSessionId() {
		lastSessionId = (lastSessionId + 11) & 0xFFFF_FFFF;
		return lastSessionId;
	}
	
	/*
	 * instance items
	 */
	
	private final MailService service;
	
	private final ThreePartName userChsName;
	private final String userLcFqn;
	private final int sessionId;
	
	private final Long remoteHostId;
	private final int[] conversationKey;
	
	private final List<MailboxEntry> inboxMails;
	
	private Object clientData = null;
	
	private static final long SESSION_TIME_TO_LIVE_MS = 1200_000L; // 1200 seconds = 20 minutes ; is that long enough?
	private long timeout = System.currentTimeMillis() + SESSION_TIME_TO_LIVE_MS;
	
	/**
	 * Constructor for a new mail session.
	 * 
	 * @param service the mail service creating the mail session.
	 * @param username the user name owning the mailbox, where the user name is
	 * 		identical with the mailbox name
	 * @param remoteHostId the machine-id of the client system opening the session
	 * @param conversationKey the conversation key from the credentials used to open
	 * 		the session
	 * @param inboxMails the list of mails which can be managed in this session, where
	 * 		the entries may share (content, envelope) data with mail lists in other sessions
	 */
	public MailSession(MailService service, ThreePartName username, Long remoteHostId, int[] conversationKey, List<MailboxEntry> inboxMails) {
		this.service = service;
		this.userChsName = username;
		this.userLcFqn = username.getLcFqn();
		this.remoteHostId = remoteHostId;
		this.conversationKey = conversationKey;
		this.inboxMails = inboxMails;
		
		this.sessionId = createSessionId();
	}
	
	/**
	 * Reset the timeout timer of this session by signaling the session as used.
	 * @return the mail session instance for fluent-API.
	 */
	public MailSession used() {
		this.timeout = System.currentTimeMillis() + SESSION_TIME_TO_LIVE_MS;
		return this;
	}
	
	/**
	 * Check if this session timed out, i.e. was not used for a longer time.
	 * 
	 * @return {@code true} if the session timed out.
	 */
	public boolean isTimedOut() {
		return (this.timeout > (System.currentTimeMillis() + SESSION_TIME_TO_LIVE_MS));
	}

	/**
	 * @return the mail service holding this session
	 */
	public MailService getService() {
		return this.service;
	}

	/**
	 * return the lowercased full-qualified name of the user owning the mailbox.
	 */
	public String getUserLcFqn() {
		return this.userLcFqn;
	}
	
	/**
	 * @return the name of the user owning the mailbox.
	 */
	public ThreePartName getUser() {
		return this.userChsName;
	}

	/**
	 * @return the 32 bit value identifying this mail session
	 */
	public int getSessionId() {
		return this.sessionId;
	}

	/**
	 * @return the machine-id of the client system which opened this session
	 */
	public Long getRemoteHostId() {
		return this.remoteHostId;
	}

	/**
	 * @return the conversation key from the credentials used to open this session
	 */
	public int[] getConversationKey() {
		return this.conversationKey;
	}
	
	/**
	 * @return the number of entries in the mail list of this session.
	 */
	public int getMailCount() {
		return this.inboxMails.size();
	}
	
	/**
	 * Access an entry in the mail list of this session.
	 * 
	 * @param index the 0-based index of the entry to fetch in the mail list
	 * @return the entry at {@code index} or {@code null} if the index is invalid
	 */
	public MailboxEntry getMailEntry(int index) { // 0-based index!
		if (index < 0 || index >= this.inboxMails.size()) {
			return null;
		}
		return this.inboxMails.get(index);
	}
	
	/**
	 * Drop the mail at the given index in this mail session, however data for
	 * the same mail mail still be accessible in other sessions.
	 * 
	 * @param index the 0-based index of the entry to remove from the mail list
	 */
	public void clearMailEntry(int index) { // 0-based index!
		if (index >= 0 || index < this.inboxMails.size()) {
			this.inboxMails.set(index, null);
		}
	}
	
	/**
	 * @return the current client specific data in the session
	 */
	@SuppressWarnings("unchecked")
	public <T> T getClientData() {
		return (T)this.clientData;
	}
	
	/**
	 * Set the client specific data in the session
	 * @param data
	 */
	public void setClientData(Object data) {
		this.clientData = data;
	}
	
}
