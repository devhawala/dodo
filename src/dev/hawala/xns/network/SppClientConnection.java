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

import dev.hawala.xns.EndpointAddress;
import dev.hawala.xns.XnsException;
import dev.hawala.xns.level2.SppConnection;

/**
 * SPP connection initiated by a client, connecting to a socket on
 * a remote server (or even the local machine).
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018,2023)
 */
public class SppClientConnection extends SppAbstractConnection {
	
	private final NetMachine netMachine;
	private final int localSocket;
	
	private iIDPSender sender = null;
	
	public SppClientConnection(NetMachine netMachine) throws XnsException {
		this.netMachine = netMachine;
		this.localSocket = this.netMachine.clientBindToFreeSocket(this);
		if (this.localSocket < 0) {
			throw new XnsException(
						XnsException.ExceptionType.NoLocalSocketAvailable,
						"No more local client sockets available");
		}
	}
	
	public void connectTo(EndpointAddress toEndpoint) throws InterruptedException {
		SppConnection establishingConnection = null;
		synchronized(this) {
			if (this.connection != null) {
				throw new IllegalStateException("Already connected to remote endpoint " + this.remoteEndpoint);
			}
			if (this.remoteEndpoint != null) {
				throw new IllegalStateException("Already bound to remote endpoint " + this.remoteEndpoint);
			}
			
			this.remoteEndpoint = toEndpoint;
			if (this.sender != null) {
				this.connection = new SppConnection(this.localEndpoint, this.remoteEndpoint, this.sender);
				establishingConnection = this.connection;
			}
		}
		if (establishingConnection != null) {
			establishingConnection
				.setSocketUnbinder(() -> this.netMachine.stopListening(this.localSocket))
				.waitEstablished();
		}
	}

	@Override
	public void start(EndpointAddress localEndpoint, iIDPSender sender) {
		SppConnection establishingConnection = null;
		synchronized(this) {
			if (this.sender != null) {
				throw new IllegalStateException("Already started");
			}
			this.localEndpoint = localEndpoint;
			this.sender = sender;
			
			if (this.remoteEndpoint != null) {
				this.connection = new SppConnection(this.localEndpoint, this.remoteEndpoint, this.sender);
				establishingConnection = this.connection;
			}
		}
		if (establishingConnection != null) {
			establishingConnection.setSocketUnbinder(() -> this.netMachine.stopListening(this.localSocket));
		}
	}
	
	@Override
	public void close() {
		if (this.connection != null) {
			this.connection.closeConnection(100); // 100ms max. wait time
		}
		// this.netMachine.stopListening(this.localSocket);
		// TODO: prevent sending packets
	}

	@Override
	public void handleAwakeAfterCloseByRemote(boolean allowReAwaking) {
		this.connection.handleAwakeAfterCloseByRemote(allowReAwaking);
	}

}
