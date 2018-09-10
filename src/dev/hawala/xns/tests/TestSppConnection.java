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

package dev.hawala.xns.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import dev.hawala.xns.EndpointAddress;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level1.IDP.PacketType;
import dev.hawala.xns.level2.Error;
import dev.hawala.xns.level2.Error.ErrorCode;
import dev.hawala.xns.level2.SPP;
import dev.hawala.xns.level2.SppConnection;
import dev.hawala.xns.network.iIDPSender;

public class TestSppConnection {
	

	private final EndpointAddress clientEnd = new EndpointAddress(1,1,1);
	private final EndpointAddress serverEnd = new EndpointAddress(2,2,2);
	
	private class IdpPacketSink implements iIDPSender {
		
		private final List<IDP> packets = new ArrayList<>();
		private final boolean failOnNullPacket;
		private final boolean client2server;
		
		public IdpPacketSink(boolean client2server, boolean failOnNull) {
			this.client2server = client2server;
			this.failOnNullPacket = failOnNull;
		}
		
		public IdpPacketSink(boolean client2server) {
			this(client2server, false);
		}

		@Override
		public synchronized void send(IDP idp) {
			if (idp == null){
				if (this.failOnNullPacket) {
					fail("Attempt to send null packet");
				}
				return;
			}
			this.packets.add(idp);
		}
		
		public synchronized int getLength() { return this.packets.size(); }
		
		public synchronized IDP get(int idx) {
			if (idx < 0 || idx >= this.packets.size()) { return null; }
			return this.packets.get(idx);
		}
		
		public synchronized IDP consumeNext() {
			if (this.packets.size() == 0) { fail("expected next packet not available"); }
			IDP idp = this.packets.get(0);
			this.packets.remove(0);
			return idp;
		}
		
		public SppPacketChecker next(String name) {
			return this.next(name, INITIAL_COUNTERS);
		}
		
		public SppPacketChecker next(String name, SppCounters oldCounters) {
			IDP idp = this.consumeNext();
			
			if (this.client2server) {
				assertEquals(name + ".getSrcEndpoint()", clientEnd, idp.getSrcEndpoint());
				assertEquals(name + ".getDstEndpoint()", serverEnd, idp.getDstEndpoint());
			} else {
				assertEquals(name + ".getSrcEndpoint()", serverEnd, idp.getSrcEndpoint());
				assertEquals(name + ".getDstEndpoint()", clientEnd, idp.getDstEndpoint());
			}
			assertEquals(name + ".getPacketType()", IDP.PacketType.SPP, idp.getPacketType());
			
			SPP spp = new SPP(idp);
			if (spp.getAcknowledgeNumber() < oldCounters.acknowledgmentNumber) {
				fail(name + ".getAcknowledgeNumber() was decremented");
			}
			if (spp.getAllocationNumber() < oldCounters.allocationNumber) {
				fail(name + ".getAllocationNumber() was decremented");
			}
			if (!spp.isSystemPacket() && spp.getSequenceNumber() < oldCounters.sequenceNumber) {
				fail(name + ".getSequenceNumber() was decremented");
			} else if (spp.isSystemPacket() && spp.getSequenceNumber() != 0) {
				fail(name + ".getSequenceNumber() != 0 on system packet");
			}
			if (oldCounters.srcConnectionId != 0) {
				assertEquals(name + ".getSrcConnectionId()", oldCounters.srcConnectionId, spp.getSrcConnectionId()); 
			}
			if (oldCounters.dstConnectionId != 0) {
				assertEquals(name + ".getDstConnectionId()", oldCounters.dstConnectionId, spp.getDstConnectionId()); 
			}
			
			return new SppPacketChecker(spp, name, oldCounters);
		}
		
		public IdpPacketSink reset() {
			this.packets.clear();
			return this;
		}
		
		public void reqEmpty(String name) {
			assertEquals(name + ": outgoing packet queue is empty", true, this.packets.isEmpty());
		}
	}
	
	@FunctionalInterface
	private interface ReqVerifier {
		public boolean isOk(String name, SPP spp);
	}
	
	private static class SppCounters {
		public final int acknowledgmentNumber;
		public final int allocationNumber;
		public final int sequenceNumber;
		public final int srcConnectionId;
		public final int dstConnectionId;
		
		public SppCounters(int ackNo, int allocNo, int seqNo, int srcId, int dstId) {
			this.acknowledgmentNumber = ackNo;
			this.allocationNumber = allocNo;
			this.sequenceNumber = seqNo;
			this.srcConnectionId = srcId;
			this.dstConnectionId = dstId;
		}
		
		public SppCounters withDstId(int dstId) {
			return new SppCounters(this.acknowledgmentNumber, this.allocationNumber, this.sequenceNumber, this.srcConnectionId, dstId);
		}
	}
	
	private static final SppCounters INITIAL_COUNTERS = new SppCounters(0,0,0,0,0); 
	
	private static class SppPacketChecker {
		
		private final SPP p;
		private final String name;
		private final SppCounters prevCounters;
		
		public SppPacketChecker(SPP p, String name, SppCounters prevCounters) {
			this.p = p;
			this.name = name;
			this.prevCounters = prevCounters;
		}
		
		public SppCounters counters() {
			return new SppCounters(
						this.p.getAcknowledgeNumber(),
						this.p.getAllocationNumber(),
						this.p.getSequenceNumber(),
						this.p.getSrcConnectionId(),
						this.p.getDstConnectionId());
		}
		
		public SppPacketChecker reqSystem(boolean isSys) {
			assertEquals(this.name + ".isSystemPacket()", isSys, this.p.isSystemPacket());
			return this;
		}
		
		public SppPacketChecker reqAttention(boolean isAttn) {
			assertEquals(this.name + ".isSystemPacket()", isAttn, this.p.isAttention());
			return this;
		}
		
		public SppPacketChecker reqEndOfMessage(boolean isEOM) {
			assertEquals(this.name + ".isEndOfMessage()", isEOM, this.p.isEndOfMessage());
			return this;
		}
		
		public SppPacketChecker reqSendAcknowledge(boolean isSendAck) {
			assertEquals(this.name + ".isSendAcknowledge()", isSendAck, this.p.isSendAcknowledge());
			return this;
		}
		
		public SppPacketChecker reqDatastreamType(byte datastreamType) {
			assertEquals(this.name + ".getDatastreamType()", datastreamType, this.p.getDatastreamType());
			return this;
		}
		
		public SppPacketChecker reqDatastreamType(int datastreamType) {
			assertEquals(this.name + ".getDatastreamType()", (byte)(datastreamType & 0xFF), this.p.getDatastreamType());
			return this;
		}
		
		public SppPacketChecker reqSeqNoGrown() {
			if (this.p.getSequenceNumber() <= this.prevCounters.sequenceNumber) {
				fail(this.name + ".getSequenceNumber() is incremented"
						+ " (prev: " + this.prevCounters.sequenceNumber
						+ " ; now: " + this.p.getSequenceNumber() + ")");
			}
			return this;
		}
		
		public SppPacketChecker reqSeqNoGrown(int by) {
			if (this.p.getSequenceNumber() != (this.prevCounters.sequenceNumber + by)) {
				assertEquals(this.name + ".getSequenceNumber() is incremented by " + by,
						this.prevCounters.sequenceNumber + by,
						this.p.getSequenceNumber());
			}
			return this;
		}
		
		public SppPacketChecker reqSeqNoSame() {
			if (this.p.getSequenceNumber() != this.prevCounters.sequenceNumber) {
				assertEquals(this.name + ".getSequenceNumber() is unchanged",
						this.prevCounters.sequenceNumber,
						this.p.getSequenceNumber());
			}
			return this;
		}
		
		public SppPacketChecker reqAllocNoGrown() {
			if (this.p.getAllocationNumber() <= this.prevCounters.allocationNumber) {
				fail(this.name + ".getAllocationNumber() is incremented"
						+ " (prev: " + this.prevCounters.allocationNumber
						+ " ; now: " + this.p.getAllocationNumber() + ")");
			}
			return this;
		}
		
		public SppPacketChecker reqAllocNoGrown(int by) {
			if (this.p.getAllocationNumber() != (this.prevCounters.allocationNumber + by)) {
				assertEquals(this.name + ".getAllocationNumber() is incremented by " + by,
						this.prevCounters.allocationNumber + by,
						this.p.getAllocationNumber());
			}
			return this;
		}
		
		public SppPacketChecker reqAllocNoSame() {
			if (this.p.getAllocationNumber() != this.prevCounters.allocationNumber) {
				assertEquals(this.name + ".getAllocationNumber() is unchanged",
						this.prevCounters.allocationNumber,
						this.p.getAllocationNumber());
			}
			return this;
		}
		
		public SppPacketChecker reqAckNoGrown() {
			if (this.p.getAcknowledgeNumber() <= this.prevCounters.acknowledgmentNumber) {
				fail(this.name + ".getAcknowledgeNumber() is incremented"
						+ " (prev: " + this.prevCounters.acknowledgmentNumber
						+ " ; now: " + this.p.getAcknowledgeNumber() + ")");
			}
			return this;
		}
		
		public SppPacketChecker reqAckNoGrown(int by) {
			if (this.p.getAcknowledgeNumber() != (this.prevCounters.acknowledgmentNumber + by)) {
				assertEquals(this.name + ".getAcknowledgeNumber() is incremented by " + by,
						this.prevCounters.acknowledgmentNumber + by,
						this.p.getAcknowledgeNumber());
			}
			return this;
		}
		
		public SppPacketChecker reqAckNoSame() {
			if (this.p.getAcknowledgeNumber() != this.prevCounters.acknowledgmentNumber) {
				assertEquals(this.name + ".getAcknowledgeNumber() is unchanged",
						this.prevCounters.acknowledgmentNumber,
						this.p.getAcknowledgeNumber());
			}
			return this;
		}
		
		public SppPacketChecker reqPayloadSize(int size) {
			assertEquals(this.name + ".getPayloadLength()", size,  this.p.getPayloadLength());
			return this;
		}
		
		public SppPacketChecker reqPayload(int at, byte b) {
			if ((b & 0xFF) != this.p.rdByte(at)) {
				assertEquals(this.name + ".payload[" + at + "]", b & 0xFF, this.p.rdByte(at));
			}
			return this;
		}
		
		public SppPacketChecker reqPayload(int at, byte[] bytes) {
			for (int i = 0; i < bytes.length; i++) {
				byte b = bytes[i];
				if ((b & 0xFF) != this.p.rdByte(at)) {
					assertEquals(this.name + ".payload[" + at + "]", b & 0xFF, this.p.rdByte(at));
				}
			}
			return this;
		}
		
		public SppPacketChecker req(String what, ReqVerifier verifier) {
			if (!verifier.isOk(this.name, this.p)) {
				fail(this.name + " - " + what);
			}
			return this;
		}
	}
	
	private static class SppData {
		public int acknowledgmentNumber = 0;
		public int allocationNumber = 0;
		public int sequenceNumber = 0;
		public int srcConnectionId = 0;
		public int dstConnectionId = 0;; 
	}
	
	private SPP mkResponseSPP(IDP to, SppData counters) {
		SPP response = new SPP();
		response.idp.asReplyTo(to);
		response.setAcknowledgeNumber(counters.acknowledgmentNumber);
		response.setAllocationNumber(counters.allocationNumber);
		response.setSequenceNumber(counters.sequenceNumber);
		response.setSrcConnectionId(counters.srcConnectionId);
		response.setDstConnectionId(counters.dstConnectionId);
		return response;
	}
	
	private static final byte[] DATA_15A = { 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A', 'A' };
	private static final byte[] DATA_10B = { 'B', 'B', 'B', 'B', 'B', 'B', 'B', 'B', 'B', 'B' };
	
	public void innerTestOpenClientConnection(final int clientWindowLength) throws InterruptedException {
		
		// window-length: min. 2 packets for tests here		
		assertTrue("Requirement :: clientWindowLength >= 2", clientWindowLength >= 2);
		
		// remarks on client window size in sendAck-response
		// as: -> ackNo   ::= first not received seqNo
		//     -> allocNo ::== last acceptable SeqNo
		// so: => (ackNo == allocNo) means: 1 free slot in receive window
		//     => (ackNo > allocNo) means: no free slot, receive window is full
		//     => ( (allocNo - ackNo) == (windowsize - 1) ) means: receive window is empty, all slots are free
		
		IdpPacketSink sink = new IdpPacketSink(true, true);
		
		// 1. create client connection
		SppConnection clientConn = new SppConnection(clientEnd, serverEnd, sink, clientWindowLength);
		Assert.assertEquals("Packet count at client connection start", 1, sink.getLength());
		IDP clientIdp1 = sink.get(0);
		SppCounters counts1 = sink.next("client-packet1")
			.reqSystem(true)
			.reqAttention(false)
			.reqDatastreamType(0)
			.reqPayloadSize(0)
			.counters();
		assertNotEquals("1. srcIdentification of client-connect first packet", 0, counts1.srcConnectionId);
		assertNotEquals("1. dstIdentification of client-connect first packet", 0, counts1.dstConnectionId);
		sink.reqEmpty("1. after client-connect first packet");
		
		// 2. let the server respond to the client-connect
		SppData srvCounters = new SppData();
		srvCounters.dstConnectionId = counts1.srcConnectionId;
		srvCounters.srcConnectionId = 4711;
		counts1 = counts1.withDstId(srvCounters.srcConnectionId);
		SPP srvSpp1 = this.mkResponseSPP(clientIdp1, srvCounters).asSystemPacket();
		clientConn.handleIngonePacket(srvSpp1.idp);
		sink.reqEmpty("2. after first server-packet after client-connect");
		
		// 3. send packet : server(data) => client
		SPP srvSpp2 = this.mkResponseSPP(clientIdp1, srvCounters);
		srvSpp2.wrBytes(0, 1024, DATA_15A, 0, DATA_15A.length);
		srvSpp2.setPayloadLength(DATA_15A.length);
		srvCounters.sequenceNumber++;
		clientConn.handleIngonePacket(srvSpp2.idp);
		sink.reqEmpty("3. after 1. data packet server => client");
		
		// 4. send packet : server(data + sendAck) => client
		SPP srvSpp3 = this.mkResponseSPP(clientIdp1, srvCounters).asSendAcknowledge();
		srvSpp3.wrBytes(0, 1024, DATA_10B, 0, DATA_10B.length);
		srvSpp3.setPayloadLength(DATA_10B.length);
		srvCounters.sequenceNumber++;
		clientConn.handleIngonePacket(srvSpp3.idp);
		assertEquals("4. Packet count after [1. + 2.] server(data+sendAck) => client", 1, sink.getLength());
		SppCounters counts2 = sink.next("4. client-packet2", counts1)
				.reqSystem(true)
				.reqAttention(false)
				.reqDatastreamType(0)
				.reqPayloadSize(0)
				.reqAckNoGrown(2)
				.reqAllocNoSame()
				.req("4. the client window size after sending 2 data packets",
						 (name, spp) -> (spp.getAllocationNumber() - spp.getAcknowledgeNumber()) == (clientWindowLength - 3))
				.reqSeqNoSame()
				.counters();
		sink.reqEmpty("after 2. data packet server => client");
		
		// 5.a dequeue the first packet and check payload
		SPP deq1 = clientConn.dequeueIngonePacket();
		assertEquals("deq1.getPayloadLength()", DATA_15A.length, deq1.getPayloadLength());
		for(int i = 0; i < DATA_15A.length; i++) {
			assertEquals("deq1.rdByte(" + i + ")", DATA_15A[i], deq1.rdByte(i));
		}
		if (sink.getLength() > 0) {
			sink.next("5.a intermediate window notification after dequeing first packet", counts2)
				.reqSystem(true)
				.reqAttention(false)
				.reqAckNoSame()
				.reqAllocNoGrown(1)
				; // ignore new counts (explicitly checked with next system+sendAck)
		}
		
		// 5.b send packet(system + sendAck) => client
		SPP srvSpp4 = this.mkResponseSPP(clientIdp1, srvCounters).asSystemPacket().asSendAcknowledge();
		clientConn.handleIngonePacket(srvSpp4.idp);
		assertEquals("5.b Packet count after server(system+sendAck) => client", 1, sink.getLength());
		SppCounters counts3 = sink.next("client-packet3", counts2)
				.reqSystem(true)
				.reqAttention(false)
				.reqDatastreamType(0)
				.reqPayloadSize(0)
				.reqAckNoSame()
				.reqAllocNoGrown(1)
				.reqSeqNoSame()
				.counters();
		sink.reqEmpty("5.b after data packet server => client");
		
		// 6.a dequeue the second packet and check payload
		SPP deq2 = clientConn.dequeueIngonePacket();
		assertEquals("deq2.getPayloadLength()", DATA_10B.length, deq2.getPayloadLength());
		for(int i = 0; i < DATA_10B.length; i++) {
			assertEquals("deq2.rdByte(" + i + ")", DATA_10B[i], deq2.rdByte(i));
		}
		if (sink.getLength() > 0) {
			sink.next("6.a intermediate window notification after dequeing second packet", counts3)
				.reqSystem(true)
				.reqAttention(false)
				.reqAckNoSame()
				.reqAllocNoGrown(1)
				; // ignore new counts (explicitly checked with next system+sendAck)
		}
		
		// 6.b send packet(system + sendAck) => client
		SPP srvSpp5 = this.mkResponseSPP(clientIdp1, srvCounters).asSystemPacket().asSendAcknowledge();
		clientConn.handleIngonePacket(srvSpp5.idp);
		assertEquals("6.b Packet count after server(system+sendAck) => client", 1, sink.getLength());
		SppCounters counts4 = sink.next("client-packet4", counts3)
				.reqSystem(true)
				.reqAttention(false)
				.reqDatastreamType(0)
				.reqPayloadSize(0)
				.reqAckNoSame()
				.reqAllocNoGrown(1)
				.req("6.b the client window has again the full size",
						 (name, spp) -> (spp.getAllocationNumber() - spp.getAcknowledgeNumber()) == (clientWindowLength - 1))
				.reqSeqNoSame()
				.counters();
		sink.reqEmpty("6.b after data packet server => client");
		
		// 7. send packet(system + sendAck) => client
		// (should return the counters as immediately before)
		SPP srvSpp6 = this.mkResponseSPP(clientIdp1, srvCounters).asSystemPacket().asSendAcknowledge();
		clientConn.handleIngonePacket(srvSpp6.idp);
		assertEquals("7. Packet count after server(system+sendAck) => client", 1, sink.getLength());
		SppCounters counts5 = sink.next("client-packet5", counts4)
				.reqSystem(true)
				.reqAttention(false)
				.reqDatastreamType(0)
				.reqPayloadSize(0)
				.reqAckNoSame()
				.reqAllocNoSame()
				.reqSeqNoSame()
				.counters();
		sink.reqEmpty("7. after data packet server => client");
		
		// 8. fill up the client's window and expect an error packet after overflowing packet
		assertEquals("8. Packet count before begin filling client window", 0, sink.getLength());
		for (int i = 0; i < clientWindowLength; i++) {
			SPP srvSppData = this.mkResponseSPP(clientIdp1, srvCounters);
			srvSppData.wrBytes(0, 1024, DATA_10B, 0, DATA_10B.length);
			srvSppData.setPayloadLength(DATA_10B.length);
			srvCounters.sequenceNumber++;
			clientConn.handleIngonePacket(srvSppData.idp);
			assertEquals("8. Packet count while filling client window before full, packet = " + i, 0, sink.getLength());
		}
		SPP srvSppData = this.mkResponseSPP(clientIdp1, srvCounters);
		srvSppData.wrBytes(0, 1024, DATA_10B, 0, DATA_10B.length);
		srvSppData.setPayloadLength(DATA_10B.length);
		srvCounters.sequenceNumber++;
		clientConn.handleIngonePacket(srvSppData.idp);
		assertEquals("8. Packet count after sending overflowing packet", 1, sink.getLength());
		IDP errIdp = sink.consumeNext();
		assertEquals("8. Packet after overflowing is error packet", PacketType.ERROR, errIdp.getPacketType());
		Error err = new Error(errIdp);
		assertEquals("8. Error type after sending overflow packet", ErrorCode.PROTOCOL_VIOLATION, err.getErrorCode());
		
		// 9.a consuming one packet of out the filled-up window must send a window update
		SPP deq3 = clientConn.dequeueIngonePacket();
		assertEquals("deq3.getPayloadLength()", DATA_10B.length, deq3.getPayloadLength());
		for(int i = 0; i < DATA_10B.length; i++) {
			assertEquals("deq3.rdByte(" + i + ")", DATA_10B[i], deq3.rdByte(i));
		}
		assertEquals("9.a Packet count after dequeuing one packet", 1, sink.getLength());
		SppCounters counts6 = sink.next("9.a after freeing one slot in receiving window", counts5)
				.reqSystem(true)
				.reqAttention(false)
				.reqDatastreamType(0)
				.reqPayloadSize(0)
				.reqAckNoGrown(clientWindowLength)
				.reqAllocNoGrown(1)
				.req("9.a the client window has space for one packet after dequeuing one packet",
						 (name, spp) -> spp.getAcknowledgeNumber() == spp.getAllocationNumber())
				.reqSeqNoSame()
				.counters();
		sink.reqEmpty("9.a after freeing one place in window");
		
		// 9.b having freed up one place in the window must allow to resend the last packet...
		clientConn.handleIngonePacket(srvSppData.idp);
		assertEquals("9.b Packet count after having dequeued one packet and re-sending the overflowing packet", 0, sink.getLength());
		
		// 9.c request the client's window data and check that there is no place left
		SPP srvSpp9c = this.mkResponseSPP(clientIdp1, srvCounters).asSystemPacket().asSendAcknowledge();
		clientConn.handleIngonePacket(srvSpp9c.idp);
		assertEquals("9.c Packet count after server(system+sendAck) => client", 1, sink.getLength());
		SppCounters counts9c = sink.next("9.c client-ack on full window", counts6)
				.reqSystem(true)
				.reqAttention(false)
				.reqDatastreamType(0)
				.reqPayloadSize(0)
				.reqAckNoGrown(1)
				.reqAllocNoSame()
				.req("9.c the client window is full after resending overflowing packet",
					 (name, spp) -> spp.getAcknowledgeNumber() > spp.getAllocationNumber())
				.reqSeqNoSame()
				.counters();
		sink.reqEmpty("9.c after system(ack) packet server => client");
	}
	
	@Test
	public void testOpenClientConnection_len8() throws InterruptedException {
		this.innerTestOpenClientConnection(8);
	}
	
	@Test
	public void testOpenClientConnection_len4() throws InterruptedException {
		this.innerTestOpenClientConnection(4);
	}
	
	@Test
	public void testOpenClientConnection_len3() throws InterruptedException {
		this.innerTestOpenClientConnection(3);
	}
	
	@Test
	public void testOpenClientConnection_len2() throws InterruptedException {
		this.innerTestOpenClientConnection(2);
	}
	
	private void innerTestOpenServerConnection(int serverWindowSize, int clientWindowSize) throws InterruptedException {
		
		assertTrue("serverWindowSize > 0", serverWindowSize > 0);
		assertTrue("clientWindowSize > 0", clientWindowSize > 0);
		
		System.err.flush();
		System.err.printf("\n## starting outgoing queue tests: serverWindowSize = %d -- clientWindowSize = %d\n", serverWindowSize, clientWindowSize);
		System.err.flush();
		
		IdpPacketSink sink = new IdpPacketSink(false, true);
		
		SppData cltCounters = new SppData();
		cltCounters.srcConnectionId = 4711;
		
		// 1.a build the initiating packet (coming from the client)
		// remark: allocNo == 0 => client window size == 1 !!
		SPP cltSpp1 = new SPP()
				.asSystemPacket()
				.setAllocationNumber(clientWindowSize - 1)
				.setSrcConnectionId(cltCounters.srcConnectionId);
		cltSpp1.idp
			.withSource(clientEnd)
			.withDestination(serverEnd);
		
		// 1.b let the server accept the connection attempt
		SppConnection srvConn = new SppConnection(serverEnd, sink, cltSpp1, serverWindowSize);
		assertEquals("Packet count from server after client connection start", 1, sink.getLength());
		SppCounters srvCounts1 = sink.next("srv-response-to-client-start-packet")
				.reqSystem(true)
				.reqDatastreamType(0)
				.reqPayloadSize(0)
				.counters();
		assertEquals("srv-response-to-client-start-packet.srcIdentification", cltCounters.srcConnectionId, srvCounts1.dstConnectionId);
		assertNotEquals("srv-response-to-client-start-packet.srcIdentification", 0, srvCounts1.srcConnectionId);
		cltCounters.dstConnectionId = srvCounts1.srcConnectionId;
		
		// 2. send as much data packets as possible until either the server or client capacity is reached
		
		// 2.a send first server data packet to client
		srvConn.enqueueOutgoingPacket(DATA_15A, 0, DATA_15A.length, (byte)15, false);
		assertEquals("Packet count from server after 1. enqueueOutgoingPacket()", 1, sink.getLength());
		SppCounters srvCounts2 = sink.next("after-first-enqueueOutgoingPacket", srvCounts1)
				.reqSystem(false) // .............. this is a data packet
				.reqAckNoSame()
				.reqAllocNoSame()
				.reqDatastreamType((byte)15)
				.reqEndOfMessage(false)
				.reqSeqNoSame() // ................ the very first data still has SeqNo == 0 
				.reqPayloadSize(DATA_15A.length) // the payload
				.reqPayload(0, DATA_15A) // ....... the payload
				.counters();
		
		// 2.b fill until capacity is reached
		int remainingCapacity = Math.min(serverWindowSize, clientWindowSize) - 1;
		while(remainingCapacity-- > 0) {
			srvConn.enqueueOutgoingPacket(DATA_15A, 0, DATA_15A.length, (byte)15, false);
			srvCounts2 = sink.next("after-first-enqueueOutgoingPacket", srvCounts2)
					.reqSystem(false) // .............. this is a data packet
					.reqAckNoSame()
					.reqAllocNoSame()
					.reqDatastreamType((byte)15)
					.reqEndOfMessage(false)
					.reqSeqNoGrown(1) // .............. SeqNo is incremented after the very first packet 
					.reqPayloadSize(DATA_15A.length) // the payload
					.reqPayload(0, DATA_15A) // ....... the payload
					.counters();
		}
		
		// 3. send the second server data packet to client
		// => the target queue at client is already full
		// => sender will request current window information and eventually resend all not confirmed data packets
		// => as the sending thread is blocked, sending and consuming must be different threads
		Thread thr = new Thread(
				() -> {
					try {
						srvConn.enqueueOutgoingPacket(DATA_10B, 0, DATA_10B.length, (byte)10, true);
					} catch (InterruptedException e) {
						// System.err.println("Async packet sender thread interrupted !!");
					}
				}
			);
		thr.start();
		try {
			// 3.a swallow system messages requesting current target window information
			SppCounters srvWaitCounts = srvCounts2;
			int systemPackets = 0;
			int waits = 0;
			while(true) {
				this.sleep();
//				System.out.printf("   receiver - %04d waited for packet from server\n", ++waits);
				if (waits >= 300) {
					fail("blocked SPP connection did not resend data after 300ms");
				}
				if (sink.getLength() > 0) {
					if (new SPP(sink.get(0)).isSystemPacket()) {
						assertEquals("# system packets in 1 ms while waiting", 1, sink.getLength());
						systemPackets++;
						srvWaitCounts = sink.next("wait system packet", srvWaitCounts)
								.reqSendAcknowledge(true)
								.counters();
					} else {
						break; // it's a data packet...
					}
				}
			}
			assertTrue("at least 1 systemPacket with sendAck was sent while waiting", systemPackets > 0);
			
			// 3.b check for expected resent data packet(s)
			SppCounters srvResentDataCounts = srvWaitCounts;
			int[] expectedSeqNo = { 0 };
			this.sleep();
			while(sink.getLength() > 0) {
				srvResentDataCounts = sink.next("resent data packet", srvResentDataCounts)
					.reqSystem(false)
					.req("sequenceNo", (name, spp) -> {
							assertEquals(name + " seqNo", expectedSeqNo[0]++, spp.getSequenceNumber());
							return true;
						})
					.counters();
			}
			
			// 3.c send new receiver window data with 1 "consumed" packet
			cltCounters.acknowledgmentNumber++;
			SPP cltSpp2 = new SPP()
					.asSystemPacket()
					.setSrcConnectionId(cltCounters.srcConnectionId)
					.setDstConnectionId(cltCounters.dstConnectionId)
					.setAcknowledgeNumber(cltCounters.acknowledgmentNumber)
					.setAllocationNumber(cltCounters.allocationNumber)
					.setSequenceNumber(0) // system packets have it always 0
					;
			cltSpp2.idp
				.withSource(clientEnd)
				.withDestination(serverEnd);
			srvConn.handleIngonePacket(cltSpp2.idp);
			
			// 3.d having signaled 1 free slot, this should release the blocked data packet
			waits = 0;
			while(true) {
				Thread.yield();
				this.sleep();
				waits++;
				if (sink.getLength() > 0) { break; }
				if (waits >= 200) {
					fail("unblocked packet did not arrive after 200ms");
					// break; // <- required to signal the compiler that things below are not "unreachable code"...
				}
			}
			SppCounters srvUnblockedData = sink.next("unblocked packet", srvResentDataCounts)
					.reqSystem(false)
					.reqAckNoSame()
					.reqAllocNoSame()
					.reqSeqNoGrown(1)
					.reqDatastreamType((byte)10)
					.reqEndOfMessage(true) 
					.reqPayloadSize(DATA_10B.length)
					.reqPayload(0, DATA_10B)
					.counters();
			
			// all went well => join the thread and forget it
			thr.join();
			thr = null;
		} finally {
			if (thr != null) {
				thr.interrupt();
				thr.join();
			}
		}
	}
	
	private void sleep() {
		try {
			Thread.sleep(1);  // 1 ms
		} catch (InterruptedException e) {
			// ignored..
		}
	}
	
	@Test
	public void testOpenServerConnection_srv8_clt1() throws InterruptedException {
		this.innerTestOpenServerConnection(8, 1);
	}
	
	@Test
	public void testOpenServerConnection_srv1_clt8() throws InterruptedException {
		this.innerTestOpenServerConnection(1, 8);
	}
	
	@Test
	public void testOpenServerConnection_srv8_clt2() throws InterruptedException {
		this.innerTestOpenServerConnection(8, 2);
	}
	
	@Test
	public void testOpenServerConnection_srv2_clt8() throws InterruptedException {
		this.innerTestOpenServerConnection(2, 8);
	}
	
	@Test
	public void testOpenServerConnection_srv8_clt5() throws InterruptedException {
		this.innerTestOpenServerConnection(8, 5);
	}
	
	@Test
	public void testOpenServerConnection_srv5_clt8() throws InterruptedException {
		this.innerTestOpenServerConnection(5, 8);
	}

}
