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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.hawala.xns.level3.courier.UNSPECIFIED2;
import dev.hawala.xns.level4.filing.FilingCommon.HandleErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.HandleProblem;
import dev.hawala.xns.level4.filing.fs.FileEntry;

/**
 * Representation of file handles and management of handles
 * at file service and session level.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2019)
 */
public class Handle {
	
	private static long lastHandleId = System.currentTimeMillis() & 0xFFFF_FFFFL;
	
	private static synchronized int createHandleId() {
		lastHandleId = (lastHandleId + 1) & 0xFFFF_FFFF;
		return (int)lastHandleId;
	}
	
	private static Map<Integer,Handle> handles = new HashMap<>(); 
	
	private final int handleId;
	private final Service service;
	private final Session session;
	private final boolean isNullHandle;
	private final boolean isVolumeRootDir;
	private final FileEntry fe;
	
	private boolean closed = false;
	
	public Handle(Session session, FileEntry fe) {
		if (session == null) {
			throw new IllegalArgumentException("session must be given");
		}
		this.handleId = createHandleId();
		this.service = session.getService();
		this.session = session;
		this.fe = fe;
		this.isNullHandle = (fe == null);
		this.isVolumeRootDir = (fe == this.service.getVolume().rootDirectory);
		synchronized(handles) {
			handles.put(this.handleId, this);
		}
	}
	
	public Handle(Session session) {
		this(session, null);
	}
	
	public void close() {
		if (closed) { return; }
		synchronized(handles) {
			handles.remove(this.handleId);
		}
		this.closed = true;
	}
	
	public static Handle get(UNSPECIFIED2 handle) {
		if (FilingCommon.isNullHandle(handle)) { return null; }
		synchronized(handles) {
			Handle h = handles.get(handle.get());
//			if (h == null || h.isClosed()) {
//				new HandleErrorRecord(HandleProblem.invalid).raise();
//			}
			return h;
		}
	}
	
	public static List<Handle> getAllInSession(Session s) {
		synchronized(handles) {
			return handles.values().stream().filter( h -> h.getSession() == s).collect(Collectors.toList());
		}
	}
	
	public static List<Handle> getAllInService(Service s) {
		synchronized(handles) {
			return handles.values().stream().filter( h -> h.getService() == s).collect(Collectors.toList());
		}
	}
	
	public boolean isClosed() {
		return this.closed;
	}
	
	public boolean isVolumeRoot() {
		return this.isVolumeRootDir;
	}
	
	public boolean isNullHandle() {
		return this.isNullHandle;
	}
	
	public void setIdTo(UNSPECIFIED2 handle) {
		if (this.isNullHandle) {
			FilingCommon.asNullHandle(handle);
		} else {
			handle.set(this.handleId);
		}
	}

	public Service getService() {
		return service;
	}

	public Session getSession() {
		return session;
	}

	public FileEntry getFe() {
		return fe;
	}

}