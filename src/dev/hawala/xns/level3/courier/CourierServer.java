/*
Copyright (c) 2018, Dr. Hans-Walter Latz
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

package dev.hawala.xns.level3.courier;

import java.util.ArrayList;
import java.util.List;

import dev.hawala.xns.Log;
import dev.hawala.xns.XnsException;
import dev.hawala.xns.iNetMachine;
import dev.hawala.xns.iSppInputStream;
import dev.hawala.xns.iSppOutputStream;
import dev.hawala.xns.iSppServerSocket;
import dev.hawala.xns.iSppSocket;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level3.courier.CrProgram.iRawCourierConnectionClient;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;

/**
 * SPP server on the Courier port, accepting ingoing Courier connections
 * and starting a separate thread for handling procedure invocations
 * over that SPP connection.
 * <br/>
 * To support the Gateway Access Protocol (GAP) and more specifically the usage of
 * the SPP connection of a Courier session for the direct end-to-end data transmission
 * between the client and the remote system (where this Courier server is only the
 * mediator instance), the SPP connection can temporarily be borrowed from Courier
 * for the lifetime of the end-to-end connection. The usage pattern seems to be that
 * the end of the end-to-end connection is signaled by closing the SPP connection
 * (i.e. destroying it!) and possibly re-awake this same connection in the next 5 seconds
 * by continuing to use it by sending the next Courier call ... this is how ViewPoint does it.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018, 2023)
 */
public class CourierServer implements Runnable {
	
	private final iNetMachine site;
	private final int socket;
	
	private final iSppServerSocket srvSocket;
	private final Thread crListenerThread;
	
	private final List<CourierServerConnection> connections = new ArrayList<>();
	
	public CourierServer(iNetMachine site) throws XnsException {
		this(site, IDP.KnownSocket.COURIER.getSocket());
	}
	
	public CourierServer(iNetMachine site, int socket) throws XnsException {
		this.site = site;
		this.socket = socket;
		
		this.srvSocket = this.site.sppListen(this.socket);
		
		this.crListenerThread = new Thread(this);
		this.crListenerThread.setName("CourierListener");
		this.crListenerThread.setDaemon(true);
		this.crListenerThread.start();
	}
	
	private void addConnection(CourierServerConnection conn) {
		synchronized(this) {
			// first remove closed connections
			List<CourierServerConnection> closedConns = new ArrayList<>();
			for (CourierServerConnection c : this.connections) {
				if (c.isClosed()) { closedConns.add(c); }
			}
			for (CourierServerConnection c : closedConns) {
				this.connections.remove(c);
			}
			
			// add the new connection
			this.connections.add(conn);
		}
	}
	
	public void shutdown() {
		this.crListenerThread.interrupt();
		synchronized(this) {
			for (CourierServerConnection c : this.connections) {
				c.close();
			}
			this.connections.clear();
		}
	}

	@Override
	public void run() {
		int clientConnectionNo = 0;
		while(true) {
			iSppSocket clientSocket = this.srvSocket.listen();
			if (clientSocket != null) {
				Log.C.printf(null, "CourierServer - new connection opened, starting new CourierServerConnection\n");
				CourierServerConnection conn = new CourierServerConnection(clientSocket, clientConnectionNo++);
				this.addConnection(conn);
				conn.start();
			} else {
				Log.C.printf(null, "CourierServer - got null clientSocket => stopping CourierServer\n");
			}
		}
	}
	
	private static class CourierServerConnection implements Runnable {
		
		private final iSppSocket clientSocket;
		
		private final String connId;
		private final CourierConnection crConn;
		
		private final Thread thr;
		
		private boolean closed = false;
		
		private CourierServerConnection(iSppSocket clientSocket, int connNo) {
			this.clientSocket = clientSocket;
			this.connId = String.format("CR%04d", connNo);
			
			iSppInputStream nis = clientSocket.getInputStream();
			iSppOutputStream nos = clientSocket.getOutputStream();
			WireSPPStream wireStream = new WireSPPStream(nis, nos);
			this.crConn = new CourierConnection(wireStream, this.connId);
			
			this.thr = new Thread(this);
			this.thr.setName("CourierServerConnection-" + this.connId);
			this.thr.setDaemon(true);
		}
		
		private void start() {
			this.thr.start();
		}

		@Override
		public void run() {
			try {
				while(!this.isClosed()) {
					iRawCourierConnectionClient connectionClient = this.crConn.processSingleCall();
					this.clientSocket.handleAwakeAfterCloseByRemote(false);
					if (connectionClient != null) {
						this.clientSocket.handleAwakeAfterCloseByRemote(true);
						connectionClient.useConnection(this.clientSocket);
					}
				}
			} catch (EndOfMessageException | NoMoreWriteSpaceException e) {
				synchronized(this) {
					Log.C.printf(this.connId, "CourierServerConnection: processSingleCall() failed, closing CourierConnection (%s)\n", e.getMessage());
					this.closed = true;
				}
			}
			this.clientSocket.close();
		}
		
		private void close() {
			synchronized(this) {
				if (!this.closed) {
					this.thr.interrupt();
				}
				this.closed = true;
			}
		}
		
		private boolean isClosed() {
			synchronized(this) {
				return this.closed;
			}
		}
	}

}
