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

package dev.hawala.xns.network;

import java.util.LinkedList;
import java.util.Queue;

import dev.hawala.xns.EndpointAddress;
import dev.hawala.xns.XnsException;
import dev.hawala.xns.iSppServerSocket;
import dev.hawala.xns.iSppSocket;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level2.Error;
import dev.hawala.xns.level2.Error.ErrorCode;
import dev.hawala.xns.level2.SPP;
import dev.hawala.xns.level2.SppConnection;

/**
 * SPP server implementation (see {@code iSppServerSocket}), bound to
 * a local socket and accepting connection requests.
 * <p>
 * An instance of the class is returned when starting listening for SPP
 * connections on a local port.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018,2023)
 */
public class SppServerListener implements iIDPReceiver, iSppServerSocket {

	private final NetMachine netMachine;
	private final int listenSocket;
	
	private boolean listening = false;
	private iIDPSender sender;
	
	private static final int MAX_WAITING_CONNECTIONS = 8;
	private final Queue<SppServerConnection> waitingConnections = new LinkedList<>();
	
	public SppServerListener(NetMachine machine, int forSocket) throws XnsException {
		this.netMachine = machine;
		this.listenSocket = forSocket;
		if (!this.netMachine.clientBindToSocket(this.listenSocket, this)) {
			throw new XnsException(
					XnsException.ExceptionType.SocketAlreadyInUse,
					"Unable to start listening, socket " + forSocket + " already bound.");
		}
	}

	@Override
	public void start(EndpointAddress localEndpoint, iIDPSender sender) {
		synchronized(this.waitingConnections) {
			this.sender = sender;
			this.listening = true;
		}
	}

	@Override
	public void accept(IDP idp) {
		// ignore broadcasts to a SPP server socket
		if (idp.getDstHost() == IDP.BROADCAST_ADDR) {
			return;
		}
		
		// handle connection attempt
		synchronized(this.waitingConnections) {
			// check if waiting for connection attempts => produce error if not
			// check if idp is SPP packet => produce error if not
			// check for space for pending ingone connections => produce error if not
			if (!this.listening
					|| idp.getPacketType() != IDP.PacketType.SPP
					|| this.waitingConnections.size() >= MAX_WAITING_CONNECTIONS) {
				Error errPacket = new Error(ErrorCode.LISTEN_REJECT, this.listenSocket, idp);
				this.sender.send(errPacket.idp);
				return;
			}
			
			// create new SppServerConnection
			// and enqueue it into ingone queue for a client to use it
			SPP spp = new SPP(idp);
			this.waitingConnections.add(new SppServerConnection(this.netMachine, spp));
			this.waitingConnections.notifyAll();
		}
	}
	
	@Override
	public iSppSocket listen() {
		SppServerConnection conn;
		synchronized(this.waitingConnections) {
			while(this.waitingConnections.isEmpty()) {
				try {
					this.waitingConnections.wait();
					if (!this.listening) { return null; }
				} catch (InterruptedException e) {
					return null;
				}
			}
			conn = this.waitingConnections.remove();
		}
		this.netMachine.clientBindToFreeSocket(conn);
		return conn;
	}

	@Override
	public void acceptError(Error err) {
		// ?? how can a passive listener receive an error?
		// TODO: handle somehow...
	}
	
	
	private static class SppServerConnection extends SppAbstractConnection {
		
		private final NetMachine netMachine;
		private SPP initialPacket; // != null only until SppConnection is created
		
		private iIDPSender sender = null;
		
		public SppServerConnection(NetMachine netMachine, SPP initialPacket) {
			this.netMachine = netMachine;
			this.initialPacket = initialPacket;
			this.remoteEndpoint = initialPacket.idp.getSrcEndpoint();
		}
		
		@Override
		public void start(EndpointAddress localEndpoint, iIDPSender sender) {
			SPP packet;
			synchronized(this) {
				if (this.initialPacket == null) { return; } // already connected
				packet = this.initialPacket;
				this.initialPacket = null;
			}
			this.localEndpoint = localEndpoint;
			this.sender = sender;
			this.connection = new SppConnection(this.localEndpoint, this.sender, packet);
			this.connection.setSocketUnbinder(() -> this.netMachine.stopListening(this.localEndpoint.socket));
			
		}
		
		@Override
		public void close() {
			if (this.connection != null) {
				this.connection.closeConnection(100); // 100ms max. wait time
			}
		}

		@Override
		public void handleAwakeAfterCloseByRemote(boolean allowReAwaking) {
			this.connection.handleAwakeAfterCloseByRemote(allowReAwaking);
		}
		
	}
	
	@Override
	public void close() {
		synchronized(this.waitingConnections) {
			if (!this.listening) { return; }
			this.listening = false;
			this.waitingConnections.notifyAll();
		}
		this.netMachine.stopListening(this.listenSocket);
	}

	@Override
	public void stopped() {
		// drop waiting connections (initiated by remote client, but still not serviced here)
		synchronized(this.waitingConnections) {
			for (SppServerConnection c : this.waitingConnections) {
				if (c.initialPacket != null) {
					Error errPacket = new Error(ErrorCode.LISTEN_REJECT, this.listenSocket, c.initialPacket.idp);
					this.sender.send(errPacket.idp);
				}
			}
			this.waitingConnections.clear();
		}
	}
}
