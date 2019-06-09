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

package dev.hawala.xns.level2;

import java.util.ArrayList;
import java.util.List;

import dev.hawala.xns.EndpointAddress;
import dev.hawala.xns.Log;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level2.Error.ErrorCode;
import dev.hawala.xns.network.iIDPSender;
import dev.hawala.xns.network.iSocketUnbinder;

/**
 * Low-Level SPP connection implementation, handling the flow of SPP packets
 * from and to the other end of the connection.
 * <p>
 * This class handles the "sequenced" part of the SPP protocol, organizing
 * the packet windows for both directions, out-of-sequence packets (interrupts)
 * as well as the connecting resp. disconnecting handshakes.
 * <br/>
 * However it is not self connected to a socket, instead the "client" wrapping
 * an instance of this class provides an {@code iIDPSender} for transmitting
 * packets to the other end resp. must feed in packets into {@code handleIngonePacket()}
 * received from there.  
 * </p>
 * <p>
 * This class implements both connections initiated here (by sending the first packet) and
 * connections initiated remotely (by handling the first packet sent by the other end).
 * After initiating the connection, there is no difference regarding the work done
 * in this class.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016-2019
 *
 */
public class SppConnection {
	
	/*
	 * transmission throttling parameter
	 */
	private static long SENDING_TIMEGAP = 5;
	
	/**
	 * Milliseconds to wait for transmitting next packet after having sent
	 * a packet.
	 * <br/>Default is 5 msecs.
	 * 
	 * @param gapMs milliseconds between 2 packets sent
	 */
	public static void setSendingTimeGap(long gapMs) {
		SENDING_TIMEGAP = gapMs;
	}
	
	/*
	 * parameters for automatic re-sending unacknowledged packets mechanism
	 * (externally customizable for all SPP connections)
	 */
	
	private static int HANDSHAKE_CHECK_INTERVAL = 10;
	private static int HANDSHAKE_SENDACK_COUNTDOWN = 4;
	private static long RESEND_DELAY = 20;
	private static int HANDSHAKE_RESEND_COUNTDOWN = 50;
	private static int HANDSHAKE_MAX_RESENDS = 5;
	private static int RESEND_PACKET_COUNT = 2; 
	
	/**
	 * milliseconds, wake up interval of the common thread for checks
	 * for acknowledgments (requested by other end) and packet resends
	 * (missing acknowledgments from the other rend). Default is 10 msecs,
	 * changing this value may make it necessary to adjust other configurable values.
	 *  
	 * @param intervalMs check interval in msecs 
	 */
	public static void setHandshakeCheckInterval(int intervalMs) {
		HANDSHAKE_CHECK_INTERVAL = intervalMs;
	}

	/**
	 * Number of check intervals after receiving the others send-ack before sending our ack packet.
	 * <br/>Default is 4, giving the local service 30..40 ms processing time to react on the ingone
	 * data and sending result data packets before the ack-packet from our side is sent.
	 * 
	 * @param count check interval count from ack-request to ack-send
	 */
	public static void setHandshakeSendackCountdown(int count) {
		HANDSHAKE_SENDACK_COUNTDOWN = count;
	}
	
	/**
	 * Milliseconds after a packet send before sending the acknowledge-request on missing acknowledge,
	 * initiating our resend standard procedure.
	 * <br/>Default is 20 msecs.
	 * 
	 * @param delayMs milliseconds after a packet sent before requesting the acknowledge if none arrived.
	 */
	public static void setResendDelay(long delayMs) {
		RESEND_DELAY = delayMs;
	}
	
	/**
	 * Number of check intervals after sending our request for acknowledgment before
	 * starting resending our packets if no acknowledge arrives in the meantime.
	 * <br/> Default is 50, giving the other side about 500 ms before we resend some
	 * of the unacknowledged packets.
	 * 
	 * @param count check interval counts after our ack-request before starting the resend
	 */
	public static void setHandshakeResendCountdown(int count) {
		HANDSHAKE_RESEND_COUNTDOWN = count;
	}
	
	/**
	 * Max. number of resend cycles without acknowledge from the other side before the
	 * connection is considered dead and hard-closed from this side.
	 * <br/>Default is 5.
	 * 
	 * @param count number of vain resend cycles before considering a connection dead.
	 */
	public static void setHandshakeMaxResends(int count) {
		HANDSHAKE_MAX_RESENDS = count;
	}
	
	/**
	 * Max. number of packets (starting with the oldest i.e. lowest sequence number) in
	 * one resend cycle.
	 * <br/>Default is 2.
	 * 
	 * @param count
	 */
	public static void setResendPacketCount(int count) {
		RESEND_PACKET_COUNT = count;
	}
	
	/*
	 * items for automatic re-sending unacknowledged packets
	 * (thread supervising all SPP connections)
	 */
	
	private static List<SppConnection> activeConnections = null;
	
	private static synchronized List<SppConnection> getActiveConnections() {
		return activeConnections;
	}
	
	private static synchronized void addActiveConnection(SppConnection c) {
		if (activeConnections.contains(c)) { return; }
		List<SppConnection> conns = new ArrayList<>(activeConnections);
		conns.add(c);
		activeConnections = conns;
	}
	
	private static synchronized void removeActiveConnection(SppConnection c) {
		if (!activeConnections.contains(c)) { return; }
		List<SppConnection> conns = new ArrayList<>(activeConnections);
		conns.remove(c);
		activeConnections = conns;
	}
	
	private static void packetResender() {
		try {
			while(true) {
				Thread.sleep(HANDSHAKE_CHECK_INTERVAL);
				List<SppConnection> currConns = getActiveConnections();
				for (SppConnection conn : currConns) {
					conn.handleHandshakes();
				}
			}
		} catch (InterruptedException e) {
			// end auto resending
		}
	}
	
	private static synchronized void runPacketResender() {
		if (activeConnections != null) { return; }
		activeConnections = new ArrayList<>();
		
		Thread thr = new Thread(SppConnection::packetResender);
		thr.setDaemon(true);
		thr.setName("SPP packet resend watcher");
		thr.start();
	}
	
	/*
	 * SPP connection id management
	 */
	
	public static final int DEFAULT_WINDOWLENGTH = 8;

	private static long lastConnectionId = Long.valueOf(System.currentTimeMillis()).hashCode() & 0xFFFFFFFF;
		
	public static synchronized int getConnectionId() {
		int connectionId = (int)((lastConnectionId + 17L) & 0xFFFF);
		lastConnectionId = connectionId;
		return connectionId;
	}
	
	/*
	 * SPP constants
	 */
	
	private final static int FIRST_SEQNO = 0;
	
	private enum State {
		
		/** initiating packet was exchanged */
		CONNECTING,
		
		/** connection is active (initiating packet was answered) */
		CONNECTED,
		
		/** 
		 * close-initiating packet (sst=254) was sent or was received,
		 * waiting for close-confirming packet (sst=255)
		 */
		CLOSING,
		
		/**
		 * close-confirming packet (sst=255) was received
		 */
		CLOSED };
		
	public static final int SST_CLOSE_REQUEST = 254;
	public static final int SST_CLOSE_CONFIRM = 255;
	
	/*
	 * connection specific SPP items
	 */
	
	private final EndpointAddress myEndpoint;
	private EndpointAddress othersEndpoint;
	
	private final iIDPSender idpSender;

	private final int windowLength;
	
	private iSocketUnbinder socketUnbinder = () -> {};
	
	private final int myConnectionId;
	private int othersConnectionId = -1;
	
	private State state;
	private boolean otherRequestedClose = false;
	
	
	private final String intro;
	
	private final List<Byte> pendingAttentions = new ArrayList<>();
	
	// resend unacknowledged packets management for *this* connection
	private long noResendBefore = System.currentTimeMillis(); // gets currentTimeMillis() + RESEND_INTERVAL after each packet sent 
	private int ackCountdown = 0; // when > 0: sent ack packet when counted down to 0
	private int resendCountdown = 0x7FFFFFFF;
	private int resendRetries = 0;
	
	// transmission throttling for *this* connection
	private long nextSendTS = System.currentTimeMillis(); // timestamp when next spp packet "may" be transmitted
	
	// connection initiated here
	public SppConnection(
			EndpointAddress myEndpoint,
			EndpointAddress othersEndpoint,
			iIDPSender idpSender,
			int windowLength) {
		this.myEndpoint = myEndpoint;
		this.othersEndpoint = othersEndpoint;
		this.idpSender = idpSender;
		this.windowLength = windowLength;
		this.ingonePackets = new SPP[windowLength];
		this.outgoingPackets = new SPP[windowLength];
		this.myConnectionId = getConnectionId();
		this.lastOthersActivity = 0;
		
		this.intro = "clnt";

		/*this.state = State.NEW;*/
		
		this.inMaxAllowedSeqNo = windowLength + FIRST_SEQNO - 1;
		
		runPacketResender();
		addActiveConnection(this);
		
		// send connection start packet
		synchronized(this) {
			SPP startPacket = this.fillSppConnectionData(new SPP()).asSystemPacket();
			this.state = State.CONNECTING;
			this.transmitPacket(startPacket.idp);
		}
	}
	
	// connection initiated here
	public SppConnection(
			EndpointAddress myEndpoint,
			EndpointAddress othersEndpoint,
			iIDPSender idpSender) {
		this(myEndpoint, othersEndpoint, /*datastreamType,*/ idpSender, DEFAULT_WINDOWLENGTH);
	}
	
	// connection initiated by the other end, i.e. with a packet accepted on server-listener
	public SppConnection(
			EndpointAddress myEndpoint,
			iIDPSender idpSender,
			SPP connectingPacket,
			int windowLength) {
		this.myEndpoint = myEndpoint;
		this.othersEndpoint = connectingPacket.idp.getSrcEndpoint();
		this.idpSender = idpSender;
		this.windowLength = windowLength;
		this.ingonePackets = new SPP[windowLength];
		this.outgoingPackets = new SPP[windowLength];
		this.myConnectionId = getConnectionId();
		this.othersConnectionId = connectingPacket.getSrcConnectionId();
		this.lastOthersActivity = System.currentTimeMillis();
		
		this.intro = "srvr";

		this.state = State.CONNECTED;
		
		this.inMaxAllowedSeqNo = windowLength + FIRST_SEQNO - 1;
		
		this.outNextExpectedSeqNo = connectingPacket.getAcknowledgeNumber();
		this.outMaxAllowedSeqNo = connectingPacket.getAllocationNumber();
		
		if (this.outNextExpectedSeqNo > this.outMaxAllowedSeqNo) {
			// no room in the send window for a first packet => error on client side
			this.transmitPacket(new Error(ErrorCode.PROTOCOL_VIOLATION, 0, connectingPacket.idp).idp);
			throw new IllegalArgumentException("No room in client receive window at startup: acknowledgeNo > allocationNo");
		}
		
		runPacketResender();
		addActiveConnection(this);
		
		// send connection accepted packet
		synchronized(this) {
			SPP startPacket = this.fillSppConnectionData(new SPP()).asSystemPacket().asSendAcknowledge();
			this.transmitPacket(startPacket.idp);
		}
	}
	
	// connection initiated by the other end, i.e. with a packet accepted on server-listener
	public SppConnection(
			EndpointAddress myEndpoint,
			iIDPSender idpSender,
			SPP connectingPacket) {
		this(myEndpoint, idpSender, connectingPacket, DEFAULT_WINDOWLENGTH);
	}
	
	public synchronized SppConnection setSocketUnbinder(iSocketUnbinder u) {
		this.socketUnbinder = u;
		return this;
	}
	
	public synchronized void waitEstablished() throws InterruptedException {
		while(this.state == State.CONNECTING) {
			this.wait();
		}
	}
	
	/*
	 * ingoing data stream
	 */
	
	private final SPP[] ingonePackets;
	private int inNextExpectedSeqNo = FIRST_SEQNO; // the first seqNo not yet seen
	private int inFirstSeqNo = FIRST_SEQNO; // the seqNo of ingonePackets[0]
	private int inMaxAllowedSeqNo; // the last sequenceNumber we can currently accept, i.e. the seqNo for ingonePackets[ingonePackets.length - 1]
	
	private List<SPP> spillOver = new ArrayList<>();

	private long lastOthersActivity;
	
	public void handleIngonePacket(IDP idp) {
		if (idp.getDstHost() == IDP.BROADCAST_ADDR) {
			return; // ignore errating broadcast packets hitting our socket
		}
		synchronized(this) {
			SPP spp = new SPP(idp);
			boolean doNotify = false;
			
			if (this.state == State.CLOSED) {
				return; // well that's clear!
			}
			
			long now = System.currentTimeMillis();
			this.lastOthersActivity = now;
			this.resendRetries = 0;
			
			try {
				int sst = spp.getDatastreamType() & 0xFF;
				
				// check for correct connection
				if (this.state != State.CONNECTING) {
					if (this.myConnectionId != spp.getDstConnectionId() || this.othersConnectionId != spp.getSrcConnectionId()) {
						// invalid packet for this local socket...?
						this.transmitPacket(new Error(ErrorCode.PROTOCOL_VIOLATION, 0, spp.idp).idp);
						return;
					}
				}
				
				// ignore all when closing except close confirmation
				if (this.state == State.CLOSING) {
					if (sst == SST_CLOSE_CONFIRM) {
						Log.L3.printf(this.intro, "** handleIngonePacket(): received close-ack, connection now closed\n");
						this.state = State.CLOSED;
						removeActiveConnection(this);
						if (!this.otherRequestedClose) {
							SPP closeAck = this.fillSppConnectionData(new SPP());
							closeAck.setDatastreamType((byte)SST_CLOSE_CONFIRM);
							this.sendSequencedOOB(closeAck);
							Log.L3.printf(this.intro, "** handleIngonePacket(): sent final close-ack (as close-initiator)\n");
						}
						// unbind listener socket for this connection and inform others
						this.socketUnbinder.unbind();
						this.notifyAll();
					}
					if (sst == SST_CLOSE_REQUEST) {
						// overlapping close requests
						// we send our confirmation and wait for others confirmation 
						SPP closeAck = this.fillSppConnectionData(new SPP());
						closeAck.setDatastreamType((byte)SST_CLOSE_CONFIRM);
						this.sendSequencedOOB(closeAck);
						this.otherRequestedClose = true;
						Log.L3.printf(this.intro, "** handleIngonePacket(): received close-request while waiting for close-ack, sent close-ack\n");
					}
					if (spp.isSendAcknowledge()) {
						// BSD-4.3 uses SendAcks when closing/confirming close,
						// but the following packet will probably not be received
						// due to NO_SOCKET (!) => we will get an error packet(!!)
						SPP ack = this.fillSppConnectionData(new SPP())
								.asSystemPacket()
								.setAcknowledgeNumber(spp.getSequenceNumber() + 1); // we allow us to lie to the other end, as the connection is going down 
						this.transmitPacket(ack.idp);
					}
					return;
				}
				
				// only system packets are allowed until really connected
				if (this.state == State.CONNECTING && !spp.isSystemPacket()) {
					if (sst == SST_CLOSE_REQUEST || sst == SST_CLOSE_CONFIRM) {
						this.state = State.CLOSED;
						removeActiveConnection(this);
					}
					this.transmitPacket(new Error(ErrorCode.PROTOCOL_VIOLATION, 0, spp.idp).idp);
					return;
				}
				
				// update send window range allowed by the other end
				if (this.outNextExpectedSeqNo != spp.getAcknowledgeNumber()
					|| this.outMaxAllowedSeqNo != spp.getAllocationNumber()) {
					this.outNextExpectedSeqNo = spp.getAcknowledgeNumber();
					this.outMaxAllowedSeqNo = spp.getAllocationNumber();
					this.cleanupOutgoingQueue();
					doNotify = true;
				}
				
				// handle a response to our connection initiation
				if (this.state == State.CONNECTING) {
					if (!this.myEndpoint.equals(idp.getDstEndpoint())
						|| this.myConnectionId != spp.getDstConnectionId()) {
						Log.L3.printf(idp, "!!! SppConnection.handleIngonePacket(...): protocol violation for packet: %s\n", idp.toString());
						this.transmitPacket(new Error(ErrorCode.PROTOCOL_VIOLATION, 0, spp.idp).idp);
						return;
					}
					this.othersEndpoint = spp.idp.getSrcEndpoint();
					this.othersConnectionId = spp.getSrcConnectionId();
					this.outNextExpectedSeqNo = spp.getAcknowledgeNumber();
					this.outMaxAllowedSeqNo = spp.getAllocationNumber();
					this.state = State.CONNECTED;
					doNotify = true;
					this.notifyAll();
					return;
				}
				
				if (sst == SST_CLOSE_REQUEST) {
					Log.L3.printf(this.intro, "** handleIngonePacket(): seen close-request\n");
				}
								
				// interpret connection control flags
				if (spp.isSendAcknowledge() && this.ackCountdown == 0) {
					// enlist sending acknowledgment if not already pending
					this.ackCountdown = HANDSHAKE_SENDACK_COUNTDOWN;
					doNotify = true;
				}
				if (spp.isAttention()) {
					if (spp.getPayloadLength() > 0) {
						this.pendingAttentions.add(spp.rdByte(0));
					}
				}
				if (spp.isSystemPacket()) {
					// check if the other side may have lost some packet, and if so resend starting with next seqNo expected by the other side...
					this.checkForResends("other's systemPacket", 999);
					
					return; // system packets are not part of the sequence!
				}
				
				// here we have a client (data) packet...
				int seqNo = spp.getSequenceNumber();
				
				// has this seqNo already processed, i.e. is it an superfluous resent?
				if (seqNo < this.inFirstSeqNo) {
					return; // ignored, as already dequeued
				}
				
				// is this seqNo beyond our window?
				if (seqNo > this.inMaxAllowedSeqNo) {
					if (spp.isAttention()) {
						this.spillOver.add(spp);
						return;
					} else {
						Log.L3.printf(idp, " SppConnection.handleIngonePacket(): seqNo > this.inMaxAllowedSeqNo for packet: %s\n", idp.toString());
						Log.X.printf(idp, "%% ERROR: SppConnection.handleIngonePacket(): seqNo(%d) > this.inMaxAllowedSeqNo(%d) for packet: %s\n", seqNo, this.inMaxAllowedSeqNo, spp.toString());
						this.transmitPacket(new Error(ErrorCode.PROTOCOL_VIOLATION, 0, spp.idp).idp);
						return;
					}
				}
				
				// place the packet into its position in the window
				this.ingonePackets[seqNo - this.inFirstSeqNo] = spp;
				
				// update the acknowledgment data (from us to the other end)
				boolean checkSpillOver = true;
				for (int i = 0; i < this.windowLength; i++) {
					SPP ingone = this.ingonePackets[i];
					if (ingone == null) {
						checkSpillOver = false;
						break;
					}
					this.inNextExpectedSeqNo = ingone.getSequenceNumber() + 1;
				}
				if (checkSpillOver) {
					for(SPP ingone : this.spillOver) {
						if (ingone.getSequenceNumber() == this.inNextExpectedSeqNo) {
							this.inNextExpectedSeqNo++;
						} else {
							break;
						}
					}
				}
				
				// wake up potential readers
				doNotify = true;
			} finally {
				if (doNotify) {
					// let waiting reader(s) get the packet(s) and process them
					this.notifyAll();
					// and give them a chance to do it
					try { this.wait(1); } catch (InterruptedException ie) { }
				}
			}
		}
	}
	
	public SPP dequeueIngonePacket() throws InterruptedException {
		SPP dequeued = null;
		while (dequeued == null || dequeued.isAttention()) { // ignore OOB packets in the sequence, as attentions are handled separately
			synchronized(this) {
				// make sure (possibly wait for) that there is a packet to dequeue
				while(this.ingonePackets[0] == null) {
					if (this.state == State.CLOSED || this.state == State.CLOSING) {
						return null;
					}
					this.wait();
				}
				
				// if the in-window is full, de-queueing allows the other to send one more packet...
				// but send the notification also if we have something to send to avoid deadlocks
				boolean sendWindowUpdate 
						= (this.ingonePackets[this.windowLength - 1] != null)
						|| (this.inNextExpectedSeqNo >= this.inMaxAllowedSeqNo)
						|| this.outgoingPackets[0] != null; 
				
				// extract the first packet and shift the in-window up by one
				dequeued = this.ingonePackets[0];
				for(int i = 1; i < this.windowLength; i++) {
					this.ingonePackets[i - 1] = this.ingonePackets[i];
					this.ingonePackets[i] = null;
				}
				this.inMaxAllowedSeqNo++;
				this.inFirstSeqNo++;
				
				// check if there is a fitting spill-over packet
				if (!this.spillOver.isEmpty()) {
					SPP spp = this.spillOver.get(0);
					int seqNo = spp.getSequenceNumber();
					if (seqNo == this.inMaxAllowedSeqNo) {
						// put the spilled over packet in the in-queue
						this.ingonePackets[seqNo - this.inFirstSeqNo] = spp;
						this.spillOver.remove(0);
						// update the acknowledgment data (from us to the other end)
						for (int i = 0; i < this.windowLength; i++) {
							SPP ingone = this.ingonePackets[i];
							if (ingone == null) { break; }
							this.inNextExpectedSeqNo = ingone.getSequenceNumber() + 1;
						}
						// force an information of the other
						sendWindowUpdate = true;
					}
				}
				
				// inform the other about new space if necessary
				if (sendWindowUpdate) {
					SPP ack = this.fillSppConnectionData(new SPP()).asSystemPacket();
					this.transmitPacket(ack.idp);
				}
				
				// handle closing the connection
				int sst = dequeued.getDatastreamType() & 0xFF;
				if (sst == SST_CLOSE_REQUEST) {
					this.state = State.CLOSING;
					this.otherRequestedClose = true;
					SPP closeAck = this.fillSppConnectionData(new SPP());
					closeAck.setDatastreamType((byte)SST_CLOSE_CONFIRM);
					this.sendSequencedOOB(closeAck);
					Log.L3.printf(this.intro, "** dequeueIngonePacket(): received close-request, sent close-ack\n");
					return null; // as we are closing, anything ingoing is ignored from now
				}
			}
		}
		
		// done
		return dequeued;
	}
	
	public boolean getAttentionState() {
		synchronized(this) {
			return (this.pendingAttentions.size() > 0);
		}
	}
	
	public byte consumePendingAttention() {
		synchronized(this) {
			if (this.pendingAttentions.size() > 0) {
				byte attnByte = this.pendingAttentions.remove(0);
				return attnByte;
			}
		}
		throw new IllegalStateException("No pending attention");
	}
	
	/*
	 * outgoing data stream
	 */
	
	private final SPP[] outgoingPackets; // sent packets not confirmed so far
	private int outCount = 0; // # packets in outgoingPackets
	private int myNextSeqNo = FIRST_SEQNO; // the seqNo for the next packet we will send
	private int outNextExpectedSeqNo = FIRST_SEQNO; // the +1 of the seqNo the other last acknowledged 
	private int outMaxAllowedSeqNo = -1; // the max seqNo we may send
	private int outFirstSeqNo = FIRST_SEQNO; // seqNo of outgoingPackets[0]
	private final List<SPP> sentAttentions = new ArrayList<SPP>(); // attention packets sent not yet sorted into 'outgoingPackets' 
	
	private void outErrCheck(String when) {
		if (this.outCount > this.windowLength) {
			Log.E.printf(this.intro, "** cleanupOutgoigQueue(%s) :: this.outCount = %d (> windowLength) !!\n", when, this.outCount);
		} else if (this.outCount < 0) {
			Log.E.printf(this.intro, "** cleanupOutgoigQueue(%s) :: this.outCount = %d (< 0) !!\n", when, this.outCount);
		} else if (this.outCount < this.windowLength && this.outgoingPackets[this.outCount] != null) {
			Log.E.printf(this.intro, "** cleanupOutgoigQueue(%s) :: this.outgoingPackets[this.outCount = %d] not null !!\n", when, this.outCount);
		}
		if (this.outFirstSeqNo > this.myNextSeqNo) {
			Log.E.printf(this.intro, "** cleanupOutgoigQueue(%s) :: this.outFirstSeqNo(%d) > this.myNextSeqNo(%d) !!\n", when, this.outFirstSeqNo, this.myNextSeqNo);
		}
	}
	
	/** 
	 * Reorganize outgoing packet queue: shift out confirmed packets and append
	 * sent OOB (attention) packets 
	 */
	private void cleanupOutgoingQueue() {
		this.outErrCheck("start");
				
		int trgIdx = 0;
		for (int srcIdx = 0; srcIdx < this.windowLength; srcIdx++) {
			SPP spp = this.outgoingPackets[srcIdx];
			this.outgoingPackets[srcIdx] = null;
			if (spp != null && spp.getSequenceNumber() < this.outNextExpectedSeqNo) {
				this.outCount--;
				this.outFirstSeqNo++;
			} else {
				this.outgoingPackets[trgIdx++] = spp;
			}
		}
		while(this.outCount < this.windowLength && !this.sentAttentions.isEmpty()) {
			this.outgoingPackets[this.outCount++] = this.sentAttentions.remove(0);
		}
		
		this.outErrCheck("end");
	}
	
	private void sendSequencedOOB(SPP oob) {
		oob.setSequenceNumber(this.myNextSeqNo++);
		this.sentAttentions.add(oob);
		this.cleanupOutgoingQueue();
		this.transmitPacket(oob.idp);
	}
	
	public void sendAttention(byte attnByte) {
		byte[] attnPayload = { attnByte };
		synchronized(this) {
			if (this.state == State.CLOSED || this.state == State.CLOSING) {
				return;
			}
			SPP attn = this.fillSppConnectionData(new SPP(attnPayload)).asAttention();
			this.sendSequencedOOB(attn);
		}
	}
	
	public int enqueueOutgoingPacket(
					byte[] data,
					int offset,
					int length,
					byte datastreamType,
					boolean isEndOfMessage) throws InterruptedException {
		synchronized(this) {
			if (this.state == State.CLOSED || this.state == State.CLOSING) {
				return -1;
			}
			
			// remove acknowledged packets from our outgoing queue
			this.cleanupOutgoingQueue();
			
			// check that there is space in the out queue,
			// possibly waiting and requesting window updates from the other
			// and resending possibly lost packets
			int maxSendWindowLength = Math.min(this.windowLength, 1 + this.outMaxAllowedSeqNo - this.outFirstSeqNo);
//			Log.L3.printf("enqueueOutgoingPacket(): this.windowLength       = %d\n", this.windowLength);
//			Log.L3.printf("enqueueOutgoingPacket(): this.outMaxAllowedSeqNo = %d\n", this.outMaxAllowedSeqNo);
//			Log.L3.printf("enqueueOutgoingPacket(): this.outFirstSeqNo      = %d\n", this.outFirstSeqNo);
//			Log.L3.printf("enqueueOutgoingPacket(): ==> maxSendWindowLength = %d\n", maxSendWindowLength);
//			Log.L3.printf("enqueueOutgoingPacket(): this.outCount = %d\n", this.outCount);
			if (this.outCount >= maxSendWindowLength) {
				this.wait(50); // wait for notification but max. 50ms
				if (this.state == State.CLOSED || this.state == State.CLOSING) {
					return -1;
				}
				this.cleanupOutgoingQueue();
				maxSendWindowLength = Math.min(this.windowLength, 1 + this.outMaxAllowedSeqNo - this.outFirstSeqNo);
			}
			while(this.outCount >= maxSendWindowLength) {
				this.wait(50); // wait for notification but max. more 50ms
				if (this.state == State.CLOSED || this.state == State.CLOSING) {
					return -1;
				}
				this.cleanupOutgoingQueue();
				maxSendWindowLength = Math.min(this.windowLength, 1 + this.outMaxAllowedSeqNo - this.outFirstSeqNo);				
			}
			
			// prepare outgoing packet, put the new packet in the out queue and send it
			int seqNo = this.myNextSeqNo++;
			if (seqNo > this.outMaxAllowedSeqNo) {
				Log.L3.printf(null, "!! ERROR enqueueOutgoingPacket() :: sending seqNo(%d) > this.outMaxAllowedSeqNo(%d)\n",seqNo, this.outMaxAllowedSeqNo); 
			}
			SPP spp = new SPP(data, offset, length);
			this.fillSppConnectionData(spp)
					.asSendAcknowledge()
					.setDatastreamType(datastreamType)
					.setSequenceNumber(seqNo);
			if (isEndOfMessage) { spp.asEndOfMessage(); }
			this.outgoingPackets[this.outCount++] = spp;
			this.transmitPacket(spp.idp);
			this.noResendBefore = System.currentTimeMillis() + RESEND_DELAY;
			Log.L3.printf(this.intro, "enqueueOutgoingPacket(): ---------------- sent data packet - seqNo = %d\n", spp.getSequenceNumber());
			String s = "enqueueOutgoingPacket(): outQueue = [ ";
			for (int i = 0; i < this.outgoingPackets.length; i++) {
				SPP p = this.outgoingPackets[i];
				if (p == null) {
					s += "- ";
				} else {
					s +=  p.getSequenceNumber() + " ";
				}
			}
			Log.L3.printf(this.intro, s + "]\n");
			
			this.notifyAll();
			
			return spp.getPayloadLength();
		}
	}
	
	public void sync() {
		synchronized(this) {
			while(this.outgoingPackets.length > 0) {
				if (this.state == State.CLOSED || this.state == State.CLOSING) {
					return;
				}
				try {
					this.wait(500);
				} catch (InterruptedException e) {
					return;
				}
				this.requestAcknowledge();
			}
		}
	}
	
	/*
	 * close connection
	 */
	
	public void closeConnection(int maxWaitMs) {
		synchronized(this) {
			if (this.state == State.CLOSED || this.state == State.CLOSING) {
				Log.L3.printf(this.intro, "** closeConnection(): already closed\n");
				return;
			}
			if (this.state != State.CONNECTED) {
				Log.L3.printf(this.intro, "** closeConnection(): not yet connected\n");
				this.state = State.CLOSED;
				// unbind listener socket for this connection
				this.socketUnbinder.unbind();
				return;
			}
			
			// phase 1: initiate and request close
			this.state = State.CLOSING;
			SPP closeReq = this.fillSppConnectionData(new SPP());
			closeReq.setDatastreamType((byte)SST_CLOSE_REQUEST);
			this.sendSequencedOOB(closeReq);
			Log.L3.printf(this.intro, "** closeConnection(): sent close initiating packet\n");
			
			// phase 2: wait for confirmation
			int waitsLeft = Math.max(1,  (maxWaitMs + 9) / 10);
			while(this.state != State.CLOSED && waitsLeft > 0) {
				try {
					this.wait(10);
				} catch (InterruptedException e) {
					break;
				}
				waitsLeft--;
			}
			if (this.state == State.CLOSED) {
				// unbinding the socket was already done when state
				// switched to CLOSED in handleIngonePacket() at end
				// of close protocol handshake
				return;
			}
			
			// fallback: no close confirmation from other end after maxWaitMs:
			// set state to closed and unbind listener socket for this connection
			this.state = State.CLOSED;
			removeActiveConnection(this);
			this.socketUnbinder.unbind();
			Log.L3.printf(this.intro, "** closeConnection(): closed after time-out\n");
		}
	}
	
	public boolean isClosed() {
		synchronized(this) {
			return this.state == State.CLOSED || this.state == State.CLOSING;
		}
	}
	
	
	/*
	 * error handling
	 */
	
	public void handleErrorPacket(Error err) {
		System.err.printf(
				"SppConnection -> got error packet, errorCode = %s , offending packet:\n%s\n",
				err.getErrorCode(),
				err.getOffendingIdpPaket().toString());
		
		// ?? any more differenciated & meaningful error handling regarding this SPP connection ??
		// let's abandon this connection
		synchronized(this) {
			this.state = State.CLOSED;
			
			removeActiveConnection(this);
			this.socketUnbinder.unbind();
			
			this.outCount = 0;
			for (int i = 0; i < this.outgoingPackets.length; i++) { this.outgoingPackets[i] = null; }
			this.sentAttentions.clear();
			
			for (int i = 0; i < this.ingonePackets.length; i++) { this.ingonePackets[i] = null; }
			this.spillOver.clear();
		}
	}
	
	/*
	 * misc. info methods
	 */
	
	public Long getRemoteNetwork() {
		if (this.othersEndpoint != null) {
			return this.othersEndpoint.network;
		} else {
			return null;
		}
	}
	
	public Long getRemoteHost() {
		if (this.othersEndpoint != null) {
			return this.othersEndpoint.host;
		} else {
			return null;
		}
	}
	
	public Integer getRemoteSocket() {
		if (this.othersEndpoint != null) {
			return this.othersEndpoint.socket;
		} else {
			return null;
		}
	}
	
	/*
	 * utilities
	 */
	
	private SPP fillSppConnectionData(SPP sppPacket) {
		sppPacket
			.setDstConnectionId(this.othersConnectionId)
			.setSrcConnectionId(this.myConnectionId)
			.setAcknowledgeNumber(this.inNextExpectedSeqNo)
			.setAllocationNumber(this.inMaxAllowedSeqNo)
			.setSequenceNumber(Math.max(0, this.myNextSeqNo - 1));
		sppPacket.idp
			.withDestination(this.othersEndpoint)
			.withSource(this.myEndpoint);
		return sppPacket;
	}
	
	private SPP updateOthersWindowNumbers(SPP sppPacket) {
		return sppPacket
				.setAcknowledgeNumber(this.inNextExpectedSeqNo)
				.setAllocationNumber(this.inMaxAllowedSeqNo);
	}
	
	private void transmitPacket(IDP idp) {
		long now = System.currentTimeMillis();
		if (now < this.nextSendTS) {
			try { Thread.sleep(this.nextSendTS - now); } catch (InterruptedException e) { }
			now = System.currentTimeMillis();
		}
		this.idpSender.send(idp);
		this.nextSendTS = now + SENDING_TIMEGAP;
	}
	
	public void requestAcknowledge() {
		SPP ackReq = this.fillSppConnectionData(new SPP()).asSystemPacket().asSendAcknowledge();
		this.transmitPacket(ackReq.idp);
	}
	
	private void checkForResendUnacknowledgedPackets(String actor, int maxResentPackets) {
		if (this.resendCountdown <= 0) {
			// no send pending resp. no ack requested
			return;
		}
		
		this.resendCountdown--;
		if (this.resendCountdown > 0) {
			// still not time for resends
			return;
		}
		
		// so do the resends
		long now = System.currentTimeMillis();
		int othersAckNo = this.outNextExpectedSeqNo;
		System.out.printf("!!!!!!!!!!! begin resending un-acknowledged packets starting with seqNo %d (actor: %s)\n", othersAckNo, actor);
		int packetsResent = 0;
		for (int i = 0; i < this.outgoingPackets.length; i++) {
			SPP resendSpp = this.outgoingPackets[i];
			if (resendSpp == null || resendSpp.getSequenceNumber() < othersAckNo) { continue; }
			this.updateOthersWindowNumbers(resendSpp);
			resendSpp.asSendAcknowledge();
			this.transmitPacket(resendSpp.idp);
			System.out.printf("!!!!!! resent packet with seqNo %d\n", resendSpp.getSequenceNumber());
			packetsResent++;
			if (packetsResent >= maxResentPackets) {
				break; // max. number of packets in a resend chunk reached
			}
		}
		System.out.printf("!!!!!!!!!!! done resending un-acknowledged packets starting with seqNo %d\n", othersAckNo);
		
		this.noResendBefore = now + RESEND_DELAY;
		this.resendRetries++;
	}
	
	private void checkForRequestAcknowledgment() {
		long now = System.currentTimeMillis();
		int othersAckNo = this.outNextExpectedSeqNo;
		
		if (othersAckNo >= this.myNextSeqNo) {
			// all is acknowledged, so cancel any resend activity
			this.resendCountdown = 0;
			this.noResendBefore = now + RESEND_DELAY;
			return;
		}
		
		if (this.resendCountdown > 0) {
			// countdown already running
			return;
		}
		
		if (this.resendRetries >= HANDSHAKE_MAX_RESENDS) {
			this.state = State.CLOSED;
			removeActiveConnection(this);
			this.socketUnbinder.unbind();
			Log.L3.printf(this.intro, "** checkForRequestAcknowledgment(): closed after max. resend retries reached\n");
		}
		
		if (othersAckNo < this.myNextSeqNo && this.noResendBefore <= now) {
			// time is come to initiate a resend if the other side does not soon acknowledge our sequenced packets
			this.requestAcknowledge();
			this.resendCountdown = HANDSHAKE_RESEND_COUNTDOWN;
		}
	}
	
	// must be call synchronized
	private void checkForResends(String actor, int maxResentPackets) {
		this.checkForRequestAcknowledgment();
		this.checkForResendUnacknowledgedPackets(actor, maxResentPackets);
	}
	
	private synchronized void handleHandshakes() {
		// no handshakes for closed connections
		if (this.state == State.CLOSED || this.state == State.CLOSING) {
			return;
		}
		
		// acknowledgments (here -> other)
		if (this.ackCountdown > 0) {
			this.ackCountdown--;
			if (this.ackCountdown == 0) {
				SPP ack = this.fillSppConnectionData(new SPP()).asSystemPacket();
				this.transmitPacket(ack.idp);
			}
		}
		
		// request acks (other -> here) & packet resends
		this.checkForResends("handshake thread", RESEND_PACKET_COUNT);
	}
	
}
