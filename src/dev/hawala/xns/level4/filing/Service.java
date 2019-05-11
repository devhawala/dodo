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

import java.util.ArrayList;
import java.util.List;

import dev.hawala.xns.Log;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level4.common.AuthChsCommon;
import dev.hawala.xns.level4.common.AuthChsCommon.Credentials;
import dev.hawala.xns.level4.common.AuthChsCommon.CredentialsType;
import dev.hawala.xns.level4.common.AuthChsCommon.Problem;
import dev.hawala.xns.level4.common.AuthChsCommon.StrongVerifier;
import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;
import dev.hawala.xns.level4.common.AuthChsCommon.Verifier;
import dev.hawala.xns.level4.common.ChsDatabase;
import dev.hawala.xns.level4.filing.FilingCommon.AuthenticationErrorRecord;
import dev.hawala.xns.level4.filing.fs.Volume;

/**
 * Representation of a XNS File Service managing sessions opened
 * for that service.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2019)
 */
public class Service implements Runnable {
	
	private final ChsDatabase chsDatabase;
	private final long machineId;
	private final ThreePartName serviceName;
	private final Volume volume;
	
	private final List<Session> sessions = new ArrayList<>();
	
	private final Thread sessionWatcher;
	private boolean shutdown = false;
	
	public Service(ChsDatabase chsDatabase, long machineId, ThreePartName serviceName, Volume volume) {
		this.chsDatabase = chsDatabase;
		this.machineId = machineId;
		this.serviceName = serviceName;
		this.volume = volume;
		
		this.sessionWatcher = new Thread(this);
		this.sessionWatcher.start();
	}
	
	public void shutdown() {
		synchronized(this) {
			this.shutdown = true;
			this.sessionWatcher.interrupt();
		}
		for (Session s: this.sessions) {
			s.close();
		}
		try {
			this.sessionWatcher.join();
		} catch (InterruptedException e) {
			// ignored
		}
		
	}

	@Override
	public void run() {
		try {
			while(true) {
				Thread.sleep(500); // 500 ms
				for (Session s: this.getCurrentSessions()) {
					if (s.isOverdue()) {
						s.close();
					}
				}
			}
		} catch (InterruptedException e) {
			// ignored, watcher thread should terminate
		}
	}
	
	private synchronized List<Session> getCurrentSessions() {
		return new ArrayList<>(sessions);
	}
	
	public synchronized Session createSession(
				Credentials credentials,
				Verifier verifier,
				Long remoteHostId,
				StrongVerifier decodedVerifier) {
		if (this.shutdown) {
			throw new IllegalStateException("Service shut down");
		}
		int[] conversationKey = new int[4];
		ThreePartName username = this.checkCredentials(credentials, verifier, conversationKey, decodedVerifier);
		Session s = new Session(this, username, remoteHostId, conversationKey);
		this.sessions.add(s);
		return s;
	}
	
	public synchronized void dropSession(Session s) {
		if (this.sessions.contains(s)) {
			this.sessions.remove(s);
		}
	}

	public ThreePartName getServiceName() {
		return serviceName;
	}

	public Volume getVolume() {
		return volume;
	}

	private ThreePartName checkCredentials(
				Credentials credentials,
				Verifier verifier,
				int[] decodedConversationKey,
				StrongVerifier decodedVerifier) {
		ThreePartName username = null;
		try {
			if (credentials.type.get() == CredentialsType.simple) {
				if (credentials.value.size() == 0) {
					// anonymous access resp. secondary credentials currently not supported
					new AuthenticationErrorRecord(Problem.credentialsInvalid).raise();
				}
				username = AuthChsCommon.simpleCheckPasswordForSimpleCredentials(
									chsDatabase,
									credentials,
									verifier);
			} else {
				username = AuthChsCommon.checkStrongCredentials(
									chsDatabase,
									credentials,
									verifier,
									this.serviceName, // chsDatabase.getChsQueryName(),
									machineId,
									decodedConversationKey,
									decodedVerifier);
			}
		} catch (IllegalArgumentException iac) {
			AuthenticationErrorRecord err = new AuthenticationErrorRecord(Problem.credentialsInvalid);
			Log.C.printf("FS", "checkCredentials() IllegalArgumentException (name not existing) -> rejecting with AuthenticationError[credentialsInvalid]\n");
			err.raise();
		} catch (EndOfMessageException e) {
			AuthenticationErrorRecord err = new AuthenticationErrorRecord(Problem.inappropriateCredentials);
			Log.C.printf("FS", "checkCredentials() EndOfMessageException when deserializing credsObject -> rejecting with AuthenticationError[inappropriateCredentials]\n");
			err.raise();
		} catch (Exception e) {
			AuthenticationErrorRecord err = new AuthenticationErrorRecord(Problem.otherProblem);
			Log.C.printf("FS", "checkCredentials() Exception when checking credentials -> rejecting with AuthenticationError[otherProblem]: %s\n", e.getMessage());
			err.raise();
		}
		if (username == null) {
			AuthenticationErrorRecord err = new AuthenticationErrorRecord(Problem.credentialsInvalid);
			Log.C.printf("FS", "checkCredentials() -> rejecting with AuthenticationError[credentialsInvalid]\n");
			err.raise();
		}
		return username;
	}
	
}
