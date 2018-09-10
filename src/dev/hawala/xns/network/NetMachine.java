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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.hawala.xns.EndpointAddress;
import dev.hawala.xns.Log;
import dev.hawala.xns.XnsException;
import dev.hawala.xns.XnsException.ExceptionType;
import dev.hawala.xns.iNetMachine;
import dev.hawala.xns.iPexResponder;
import dev.hawala.xns.iPexSocket;
import dev.hawala.xns.iSppServerSocket;
import dev.hawala.xns.iSppSocket;
import dev.hawala.xns.iWakeupRequestor;
import dev.hawala.xns.level0.Payload;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level1.IDP.PacketType;
import dev.hawala.xns.level2.Error;
import dev.hawala.xns.level2.Error.ErrorCode;
import dev.hawala.xns.level2.PEX;
import dev.hawala.xns.level2.SPP;

/**
 * Implementation of the dispatchers for ingoing IDP packets to handlers
 * for higher level XNS protocols registered to the ports of the local machine. 
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class NetMachine implements iNetMachine {
	
	private static final int CLIENTSOCKET_RANGE_START = 16384; // [0..3000) are well known socket according to Pilot Programmers Manual of Feb/89
	private static final int CLIENTSOCKET_RANGE_END = 65535;
	
	private static final int CLIENTSOCKET_PEXREQUEST_WAITER = CLIENTSOCKET_RANGE_START - 1; 
	
	// construction data
	private final long networkId;
	private final long machineId;
	private final String machineName;
	private final iIDPSender sender;
	private final boolean enforceChecksums;
	
	// all local sockets currently bound to a handler for ingoing packets
	private final Map<Integer, iIDPReceiver> sockets = new HashMap<>();
	
	// the one(!) handler for ingoing responses to PEX requests sent by this machine
	private final PexRequestHandler pexRequestHandler = new PexRequestHandler();
	
	/*
	 * construction
	 */
	
	public NetMachine(long networkId, long machineId, String machineName, iIDPSender sender, boolean enforceChecksums) {
		this.networkId = networkId;
		this.machineId = machineId;
		this.machineName = machineName;
		this.sender = sender;
		this.enforceChecksums = enforceChecksums;
		
		this.clientBindToSocket(CLIENTSOCKET_PEXREQUEST_WAITER, this.pexRequestHandler);
	}
	
	/*
	 * simple info routines
	 */
	
	@Override
	public long getNetworkId() {
		return networkId;
	}

	@Override
	public long getMachineId() {
		return machineId;
	}

	@Override
	public String getMachineName() {
		return machineName;
	}
	
	/*
	 * socket listener management
	 */

	@Override
	public boolean clientBindToSocket(int sockNo, iIDPReceiver listener) {
		synchronized(this.sockets) {
			if (this.sockets.containsKey(sockNo)) {
				Log.L1.printf(null, "ERROR: clientBindToSocket( sockNo = %d, listener = %s ) => socket already bound!\n", sockNo, listener);
				return false;
			}
			this.sockets.put(sockNo, listener);
			Log.L1.printf(null, "clientBindToSocket( sockNo = %d, listener = %s ): socket bound\n", sockNo, listener);
			listener.start(new EndpointAddress(this.networkId, this.machineId, sockNo), this.sender);
			Log.L1.printf(null, "clientBindToSocket( sockNo = %d, listener = %s ): listener started\n", sockNo, listener);
		}
		return true;
	}
	
	@Override
	public int clientBindToFreeSocket(iIDPReceiver listener) {
		synchronized(this.sockets) {
			for (int sockNo = CLIENTSOCKET_RANGE_START; sockNo <= CLIENTSOCKET_RANGE_END; sockNo++) {
				if (!this.sockets.containsKey(sockNo)) {
					this.sockets.put(sockNo, listener);
					Log.L1.printf(null, "clientBindToSocket( listener = %s ): bound to socket %d\n", listener, sockNo);
					listener.start(new EndpointAddress(this.networkId, this.machineId, sockNo), this.sender);
					Log.L1.printf(null, "clientBindToSocket( listener = %s ): listener started\n", listener);
					return sockNo;
				}
			}
		}
		Log.L1.printf(null, "ERROR: clientBindToSocket( listener = %s ): no free socket to bind to\n", listener);
		return -1;
	}
	
	@Override
	public boolean stopListening(int sockNo) {
		synchronized(this.sockets) {
			if (!this.sockets.containsKey(sockNo)) {
				Log.L1.printf(null, "ERROR: stopListening( sockNo = %d ): no listener bound to socket\n", sockNo);
				return false;
			}
			iIDPReceiver receiver = this.sockets.remove(sockNo);
			Log.L1.printf(null, "stopListening( sockNo = %d ): unbound socket\n", sockNo);
			if (receiver != null) {
				receiver.stopped();
				Log.L1.printf(null, "stopListening( sockNo = %d ): listener stopped\n", sockNo);
			}
			return true;
		}
	}
	
	public void shutdown() {
		synchronized(this.sockets) {
			List<Integer> sockets = new ArrayList<>(this.sockets.keySet());
			for (Integer sockNo : sockets) {
				this.stopListening(sockNo.intValue());
			}
		}
	}
	
	/*
	 * incoming packet handling
	 */
	
	private iIDPReceiver getListener(int sockNo) {
		synchronized(this.sockets) {
			return this.sockets.get(sockNo);
		}
	}

	public void handlePacket(IDP idp) {
		if (idp.getPacketType() == PacketType.SPP) {
			SPP spp = new SPP(idp);
			Log.L1.printf(idp, "handlePacket( %s )\n", spp.toString());
		} else {
			Log.L1.printf(idp, "handlePacket( %s )\n", idp.toString());
		}
		
		// temp: verify the checksum to test the algorithm
		int packetChecksum = idp.getChecksum();
		if (packetChecksum != IDP.NO_CHECKSUM) {
			int tmpCkSum = idp.computeChecksum();
			if (tmpCkSum != packetChecksum) {
				Log.E.printf(idp, "** checksums: orig = 0x%04X - recomputed = 0x%04X\n%s\n",
						packetChecksum, tmpCkSum, idp.packet.payloadToString());
//			} else {
//				Log.I.printf(idp, "++ checksum OK (orig == recomputed = 0x%04X)\n", packetChecksum);
			}
		}
		// end temp
		
		int targetSocket = idp.getDstSocket();
		
		Error err = (idp.getPacketType() == PacketType.ERROR) ? new Error(idp) : null;
		if (err != null) {
			IDP errPacket = err.getOffendingIdpPaket();
			String errData = (errPacket.getPacketType() == PacketType.SPP)
					? (new SPP(errPacket)).toString()
					: errPacket.toString();
			Log.E.printf(idp,
					"handlePacket(): packet type is ERROR(%s), offending packet: %s\n",
					err.getErrorCode(),
					errData);
			targetSocket = err.getOffendingIdpPaket().getSrcSocket();
		}
		
		iIDPReceiver listener = this.getListener(targetSocket);
		if (listener == null) {
			Log.L1.printf(idp, "** handlePacket(): no listener registered for target socket\n");
			// avoid error ping-pong or replying errors to broadcasts
			if (err == null && idp.getDstHost() != IDP.BROADCAST_ADDR) {
				Log.L1.printf(idp, "** handlePacket(): sending back error packet\n");
				this.sender.send(new Error(ErrorCode.NO_SOCKET, idp.getDstSocket(), idp).idp);
			}
			return;
		}
		
		int ckSum = idp.getChecksum();
		if (this.enforceChecksums && ckSum != IDP.NO_CHECKSUM) {
			int localCkSum = idp.computeChecksum();
			if (localCkSum != ckSum) {
				Log.L1.printf(idp, "** handlePacket(): checkum error (packet-checksum: 0x%04X, locally-recomputed: 0x%04X\n",
						ckSum, localCkSum);
				this.sender.send(new Error(ErrorCode.BAD_CHECKSUM, idp.getDstSocket(), idp).idp);
			}
		}
		
		if (err != null) {
			Log.L1.printf(idp, "handlePacket(): invoking listener.acceptError()\n");
			listener.acceptError(err);
		} else {
			Log.L1.printf(idp, "handlePacket(): invoking listener.accept()\n");
			listener.accept(idp);
		}
	}
	
	/*
	 * SPP connections
	 */

	@Override
	public iSppServerSocket sppListen(int localPort) throws XnsException {
		return new SppServerListener(this, localPort);
	}

	@Override
	public iSppSocket sppConnect(long remoteHostAddress, int remotePort) throws XnsException {
		EndpointAddress remoteEndpoint = new EndpointAddress(this.networkId, remoteHostAddress, remotePort);
		return this.sppConnect(remoteEndpoint);
	}

	@Override
	public iSppSocket sppConnect(EndpointAddress remoteEndpoint) throws XnsException {
		SppClientConnection connection = new SppClientConnection(this);
		try {
			connection.connectTo(remoteEndpoint);
		} catch (InterruptedException e) {
			throw new XnsException(ExceptionType.NoRemoteListener, "Interrupted while connecting");
		}
		return connection;
	}
	
	/*
	 * PEX request servicing
	 */
	
	private static class PexServer implements iIDPReceiver {
		private final iPexResponder responder;
		private final iWakeupRequestor wakeupRequestor;
		private iIDPSender realSender;
		private EndpointAddress localEndpoint;
		
		public PexServer(iPexResponder responder, iWakeupRequestor wakeupRequestor) {
			this.responder = responder;
			this.wakeupRequestor = wakeupRequestor;
		}
		
		@Override
		public synchronized void start(EndpointAddress localEndpoint, iIDPSender sender) {
			Log.L1.printf(null, "PexServer.start( localEndpoint = %s , sender = ... )\n", localEndpoint.toString());
			this.localEndpoint = localEndpoint;
			this.realSender = sender;
		}
		
		private synchronized iIDPSender getSender() {
			return this.realSender;
		}
		
		@Override
		public void accept(IDP idp) {
			// sanity checks
			iIDPSender sender = this.getSender();
			if (sender == null) {
				 // stopped
				Log.L1.printf(idp, "PexServer.accept[localEndpoint = %s](): stopped\n", localEndpoint.toString());
				return;
			}
			if (idp.getPacketType() != PacketType.PEX) {
				Error err = new Error(ErrorCode.INVALID_PACKET_TYPE, 0, idp);
				Log.L1.printf(idp, "PexServer.accept[localEndpoint = %s](): not PEX, sending back error\n", localEndpoint.toString());
				sender.send(err.idp);
				return;
			}
			
			// let the packet be processed
			PEX pex = new PEX(idp);
			byte[] payload = new byte[pex.getPayloadLength()];
			pex.rdBytes(0, payload.length, payload, 0, payload.length);
			Log.L1.printf(idp, "PexServer.accept[localEndpoint = %s](): invoking responder.handlePacket()\n", localEndpoint.toString());
			this.responder.handlePacket(
					pex.getClientType(),
					payload,
					(buffer,offset,length) -> { // ResponseSender
						PEX resp = new PEX(buffer, offset, length);
						resp.idp
							.withDestination(pex.idp.getSrcEndpoint())
							.withSource(this.localEndpoint);
						resp.setClientType(pex.getClientType());
						resp.setIdentification(pex.getIdentification());
						sender.send(resp.idp);
					},
					(code,param) -> { // ErrorSender
						Error err = new Error(code, param, pex.idp);
						sender.send(err.idp);
					});
			
			// if we have a wakeup service => enlist the wakeup
			if (this.wakeupRequestor != null) {
				this.wakeupRequestor.wakeUp(idp.getSrcHost());
			}
		}
		
		@Override
		public void acceptError(Error err) {
			// ignore errors sent back due to responses we sent (avoid error ping-pong...)
			Log.L1.printf(err, "PexServer.acceptError[localEndpoint = %s](): ignoring error packet\n", localEndpoint.toString());
		}
		
		@Override
		public synchronized void stopped() {
			Log.L1.printf(null, "PexServer.stopped[localEndpoint = %s]()\n", localEndpoint.toString());
			this.realSender = null;
		}
	}

	@Override
	public iPexSocket pexListen(int localPort, iPexResponder responder, iWakeupRequestor wakeUpRequestor) throws XnsException {
		PexServer server = new PexServer(responder, wakeUpRequestor);
		if (!this.clientBindToSocket(localPort, server)) {
			throw new XnsException(XnsException.ExceptionType.SocketAlreadyInUse);
		}
		return new iPexSocket() {
			@Override
			public void stop() {
				stopListening(localPort);
			}
			
		};
	}

	@Override
	public iPexSocket pexListen(int localPort, iPexResponder responder) throws XnsException {
		return this.pexListen(localPort, responder, null);
	}
	
	/*
	 * PEX request sending and response handling
	 */
	
	private long lastClientIdentification = System.currentTimeMillis();
	
	private synchronized long getClientIdentification() {
		this.lastClientIdentification++;
		return this.lastClientIdentification & 0xFFFFFFFFL;
	}
	
	private static class PexResponseWaiter {
		public final IDP requestPacket;
		public final long identification;
		public final long retransmitIntervalMs;
		private final int retryCount;
		private final boolean raiseExceptionOnTimeout;
		
		private Payload result;
		
		private long nextTimeout;
		private int retries = 0;
		
		private PexResponseWaiter(
					IDP requestPacket,
					long identification,
					long retransmitIntervalMs,
					int retryCount,
					boolean raiseExceptionOnTimeout) {
			this.requestPacket = requestPacket;
			this.identification = identification;
			this.retransmitIntervalMs = retransmitIntervalMs;
			this.retryCount = retryCount;
			this.raiseExceptionOnTimeout = raiseExceptionOnTimeout;
			
			this.nextTimeout = System.currentTimeMillis() + this.retransmitIntervalMs;
		}
		
		public long getTimeout() {
			return this.nextTimeout;
		}
		
		public boolean canRetry() {
			if (this.retries++ < this.retryCount) {
				this.nextTimeout = System.currentTimeMillis() + this.retransmitIntervalMs;
				return true;
			}
			return false;
		}
		
		public Payload getResult() {
			return result;
		}

		public void setResult(Payload result) {
			this.result = result;
		}

		@Override
		public int hashCode() {
			return (int)(this.identification & 0xFFFFFFFFL);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) { return true; }
			if (obj == null) { return false; }
			if (this.getClass() != obj.getClass()) { return false; }
			PexResponseWaiter other = (PexResponseWaiter) obj;
			return (this.identification == other.identification);
		}
	}
	
	private static class PexRequestHandler implements iIDPReceiver, Runnable {
		
		private final List<PexResponseWaiter> pendingRequests = new ArrayList<>();
		
		private final List<PexResponseWaiter> doneRequests = new ArrayList<>();
		
		private final Thread timeouterThread = new Thread(this);
		
		private EndpointAddress localEndpoint = null;
		private iIDPSender sender = null;
		
		private long retransmitIntervalMs = 1000;
		private int retryCount = 2;
		private boolean raiseExceptionOnTimeout = false;;
		
		// retry and timeout handling thread
		@Override
		public void run() {
			synchronized(this) {
				List<PexResponseWaiter> timeouts = new ArrayList<>();
				while(true) {
					try {
						this.wait(100); // ~10 wake-ups per second 
					} catch (InterruptedException e) {
						return;
					}
					
					// check if we are still online
					if (this.sender == null) {
						return; // no, we have been stopped
					}
					
					// check for timeouts and possible retries
					long now = System.currentTimeMillis();
					timeouts.clear();
					for(PexResponseWaiter prw : this.pendingRequests) {
						if (now >= prw.getTimeout()) {
							if (prw.canRetry()) {
								this.sender.send(prw.requestPacket);
							} else {
								timeouts.add(prw);
							}
						}
					}
					
					// move real timeouts to done
					boolean notify = false;
					for (PexResponseWaiter prw : timeouts) {
						this.pendingRequests.remove(prw);
						this.doneRequests.add(prw);
						notify = true;
					}
					if (notify) {
						this.notifyAll();
					}
				}
			}
		}

		@Override
		public void start(EndpointAddress localEndpoint, iIDPSender sender) {
			synchronized(this) {
				this.localEndpoint = localEndpoint;
				this.sender = sender;
				this.notifyAll();
			}
			this.timeouterThread.start();
		}

		@Override
		public void accept(IDP idp) {
			Log.L1.printf(idp, "PexRequestHandler.accept()\n");
			synchronized(this) {
				if (idp.getPacketType() == PacketType.PEX) {
					PEX pex = new PEX(idp);
					PexResponseWaiter prw = find(this.pendingRequests, pex.getIdentification());
					if (prw != null) {
						Log.L1.printf(idp, "PexRequestHandler.accept(): matching PexResponseWaiter found, moving request to done\n");
						this.pendingRequests.remove(prw);
						prw.setResult(pex);
						this.doneRequests.add(prw);
						this.notifyAll();
					} else {
						Log.L1.printf(idp, "PexRequestHandler.accept(): no PexResponseWaiter found for ingone packet, ignored\n");
					}
				} else {
					Log.L1.printf(idp, "PexRequestHandler.accept(): ingone packet not PEX, ignored\n");
				}
			}
		}

		@Override
		public void acceptError(Error err) {
			Log.L1.printf(err, "PexRequestHandler.acceptError()\n");
			synchronized(this) {
				IDP idp = err.getOffendingIdpPaket();
				if (idp.getPacketType() == PacketType.PEX) {
					PEX pex = new PEX(idp);
					PexResponseWaiter prw = find(this.pendingRequests, pex.getIdentification());
					if (prw != null) {
						Log.L1.printf(err, "PexRequestHandler.acceptError(): matching PexResponseWaiter found, moving request to done\n");
						this.pendingRequests.remove(prw);
						prw.setResult(err);
						this.doneRequests.add(prw);
						this.notifyAll();
					} else {
						Log.L1.printf(err, "PexRequestHandler.acceptError(): no PexResponseWaiter found for offending packet, ignored\n");
					}
				} else {
					Log.L1.printf(err, "PexRequestHandler.acceptError(): offending packet not PEX, ignored\n");
				}
			}
		}

		@Override
		public void stopped() {
			synchronized(this) {
				this.sender = null;
				this.doneRequests.addAll(this.pendingRequests);
			}
		}
		
		public void config(long retransmitIntervalMs, int retryCount, boolean raiseExceptionOnTimeout) {
			synchronized(this) {
				this.retransmitIntervalMs = retransmitIntervalMs;
				this.retryCount = retryCount;
				this.raiseExceptionOnTimeout = raiseExceptionOnTimeout;
			}
		}
		
		public Payload sendAndwaitForResponse(PEX pex) throws XnsException {
			synchronized(this) {
				// make sure we can send the pex request
				if (this.localEndpoint != null && this.sender == null) {
					// we were online but stopped listening => can't do the request
					throw new XnsException(XnsException.ExceptionType.Stopped);
				}
				while(this.sender == null) {
					try {
						this.wait();
					} catch (InterruptedException e) {
						if (this.raiseExceptionOnTimeout) {
							throw new XnsException(XnsException.ExceptionType.TransmissionTimeout);
						}
						return null;
					}
				}
				if (this.localEndpoint == null) {
					// how could this happen?
					throw new XnsException(XnsException.ExceptionType.Stopped);
				}
				
				// register the request as pending and send the request packet
				PexResponseWaiter prw = new PexResponseWaiter(
						pex.idp.withSource(this.localEndpoint),
						pex.getIdentification(),
						this.retransmitIntervalMs,
						this.retryCount,
						this.raiseExceptionOnTimeout);
				this.pendingRequests.add(prw);
				Log.L1.printf(pex, "PexRequestHandler.sendAndwaitForResponse(): created PexResponseWaiter for client-identification 0x%08X\n", pex.getIdentification());
				this.sender.send(prw.requestPacket);
				
				// wait for the request having some outcome and signal the result 
				while(!this.doneRequests.contains(prw)) {
					try {
						this.wait();
					} catch (InterruptedException e) {
						break;
					}
				}
				Payload result = prw.getResult();
				Log.L1.printf(pex, "PexRequestHandler.sendAndwaitForResponse(): proceeding with result:: %s\n", result);
				if (result == null && prw.raiseExceptionOnTimeout) {
					throw new XnsException(XnsException.ExceptionType.TransmissionTimeout);
				}
				return result;
			}
		}
		
		// must be called with synchronized access to the list
		private static PexResponseWaiter find(List<PexResponseWaiter> list, long id) {
			for (PexResponseWaiter prw : list) {
				if (prw.identification == id) {
					return prw;
				}
			}
			return null;
		}
	}

	@Override
	public Payload pexRequest(
			long remoteHostAddress,
			int remotePort,
			int clientType,
			byte[] pBuffer,
			int pOffset,
			int pLength) throws XnsException {
		return this.pexRequest(
				new EndpointAddress(this.networkId, remoteHostAddress, remotePort),
				clientType,
				pBuffer,
				pOffset,
				pLength);
	}

	@Override
	public Payload pexRequest(
			EndpointAddress remoteEndpoint,
			int clientType,
			byte[] pBuffer,
			int pOffset,
			int pLength) throws XnsException {
		PEX pex = new PEX(pBuffer, pOffset, pLength);
		pex.setClientType(clientType);
		pex.setIdentification(this.getClientIdentification());
		pex.idp.withDestination(remoteEndpoint);
		return this.pexRequestHandler.sendAndwaitForResponse(pex);
	}

	@Override
	public void configPexRequestors(long retransmitIntervalMs, int retryCount, boolean raiseExceptionOnTimeout) {
		this.pexRequestHandler.config(retransmitIntervalMs, retryCount, raiseExceptionOnTimeout);
	}
}
