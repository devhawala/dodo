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

import org.junit.Test;

import dev.hawala.xns.level0.NetPacket;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level2.Error;
import dev.hawala.xns.level2.PEX;
import dev.hawala.xns.level2.SPP;
import dev.hawala.xns.level2.Error.ErrorCode;

public class TestPayloads {
	
	private final static byte[] TESTCONTENT = {
			0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
			0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,

			0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
			0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F,
			
			0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
			0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F,

			0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,
			0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F
	};
	
	private NetPacket mkNetPacket() {
		NetPacket p = new NetPacket(TESTCONTENT);
		return p;
	}
	
	private void checkByteArrayContents(byte[] ref, byte[] actual) {
		assertEquals("ref.length == actual.length", ref.length, actual.length);
		for (int i = 0; i < ref.length; i++) {
			if (ref[i] != actual[i]) {
				assertEquals("ref[i] != actual[i] at i = " + i, ref[i], actual[i]);
			}
		}
	}
	
	@Test
	public void testPacketReadContent() {
		NetPacket packet = this.mkNetPacket();
		
		assertEquals("packet.maxPayloadLength", NetPacket.MAX_PACKET_SIZE, packet.getMaxPayloadLength());
		assertEquals("packet.payloadLength", TESTCONTENT.length, packet.getPayloadLength());
		
		assertEquals("payload.rdByte(-1)", 0, packet.rdByte(-1));
		assertEquals("payload.rdByte(64)", 0, packet.rdByte(TESTCONTENT.length));
		assertEquals("payload.rdByte(63)", 0x3F, packet.rdByte(TESTCONTENT.length -1));
		assertEquals("payload.rdByte(5)", 0x05, packet.rdByte(5));
		
		assertEquals("payload.rdCardinal(-1)", 0, packet.rdCardinal(-1));
		assertEquals("payload.rdCardinal(0)", 1, packet.rdCardinal(0));
		assertEquals("payload.rdCardinal(16)", 0x1011, packet.rdCardinal(16));
		assertEquals("payload.rdCardinal(17)", 0x1112, packet.rdCardinal(17));
		assertEquals("payload.rdCardinal(62)", 0x3E3F, packet.rdCardinal(62));
		assertEquals("payload.rdCardinal(63)", 0x3F00, packet.rdCardinal(63));
		
		assertEquals("payload.rdLongCardinal(57)", 0x393A3B3CL, packet.rdLongCardinal(57));
		
		assertEquals("payload.rdAddress(50)", 0x323334353637L, packet.rdAddress(50));
	}
	
	@Test
	public void testPacketWriteContent() {
		NetPacket packet = new NetPacket();
		
		assertEquals("packet.maxPayloadLength", NetPacket.MAX_PACKET_SIZE, packet.getMaxPayloadLength());
		assertEquals("packet.payloadLength", NetPacket.MAX_PACKET_SIZE, packet.getPayloadLength());
		
		packet.wrByte(3, (byte)0x11);
		packet.wrCardinal(4, 0x2233);
		packet.wrLongCardinal(6, 0x44556677L);
		packet.wrAddress(12, 0x112233445566L);
		packet.setPayloadLength(20);

		assertEquals("packet.payloadLength", 20, packet.getPayloadLength());
		
		final byte[] refData = {
			0x00, 0x00, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
			0x00, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x00, 0x00
		};
		
		byte[] actualData = new byte[20];
		int copiedLength = packet.rdBytes(0, NetPacket.MAX_PACKET_SIZE, actualData, 0, NetPacket.MAX_PACKET_SIZE);
		assertEquals("copiedLength", actualData.length, copiedLength);
		this.checkByteArrayContents(refData, actualData);
	}
	
	@Test
	public void testIDPReadContent() {
		NetPacket packet = this.mkNetPacket();
		IDP idp = new IDP(packet);
		
		assertEquals("idp.checksum", 0x0001, idp.getChecksum());
		assertEquals("idp.length", 0x0203, idp.getLength());
		assertEquals("idp.transportControl", 0x04, idp.getTransportControl());
		assertEquals("idp.packetTypeCode", 0x05, idp.getPacketTypeCode());
		assertEquals("idp.packetType", IDP.PacketType.SPP, idp.getPacketType());
		assertEquals("idp.dstNetwork", 0x06070809, idp.getDstNetwork());
		assertEquals("idp.dstHost", 0x0A0B0C0D0E0FL, idp.getDstHost());
		assertEquals("idp.dstSocket", 0x1011, idp.getDstSocket());
		assertEquals("idp.srcNetwork", 0x12131415, idp.getSrcNetwork());
		assertEquals("idp.srcHost", 0x161718191A1BL, idp.getSrcHost());
		assertEquals("idp.srcSocket", 0x1C1D, idp.getSrcSocket());
		
		assertEquals("idp.payloadLength", TESTCONTENT.length - 30, idp.getPayloadLength());
		
		byte[] refData = {
				0x1E, 0x1F,
				0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
				0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F,
				0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,
				0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F
		};
		
		
		byte[] actualData = new byte[TESTCONTENT.length - 30];
		int copiedLength = idp.rdBytes(0, NetPacket.MAX_PACKET_SIZE, actualData, 0, NetPacket.MAX_PACKET_SIZE);
		assertEquals("copiedLength", actualData.length, copiedLength);
		this.checkByteArrayContents(refData, actualData);
	}
	
	@Test
	public void testIDPWriteContent() {
		IDP idp = new IDP();
		
		assertEquals("(new)idp.maxPayloadLength", NetPacket.MAX_PACKET_SIZE - 30, idp.getMaxPayloadLength());
		assertEquals("(new)idp.payloadLength", 0, idp.getPayloadLength());
		assertEquals("(new)idp.packet.payloadLength", 30, idp.packet.getPayloadLength());
		
		idp.resetChecksum();
		idp.setTransportControl((byte)0xEE);
		idp.setPacketType(IDP.PacketType.RIP); // 0x01
		idp.setDstNetwork(0x22334455);
		idp.setDstHost(0x112233445566L);
		idp.setDstSocket(0x6677);
		idp.setSrcNetwork(0x55443322);
		idp.setSrcHost(0x665544332211L);
		idp.setSrcSocket(0x7766);
		
		byte[] payloadContent = { 0x7F, 0x7E, 0x7D, 0x7C, 0x7B, 0x7A, 0x79, 0x78, 0x77, 0x76, 0x75 }; // length == 11
		assertEquals("payloadContent.length", 11, payloadContent.length);
		for (int i = 0; i < payloadContent.length; i++) {
			idp.wrByte(i, payloadContent[i]);
		}
		idp.setPayloadLength(payloadContent.length);
		
		assertEquals("idp.maxPayloadLength", NetPacket.MAX_PACKET_SIZE - 30, idp.getMaxPayloadLength());
		assertEquals("idp.payloadLength", payloadContent.length, idp.getPayloadLength()); // odd(11)
		assertEquals("idp.packet.payloadLength", payloadContent.length + 31, idp.packet.getPayloadLength()); // even(42)
		
		byte[] refData = {
				(byte) 0xFF, (byte)0xFF,
				0x00, (byte)(payloadContent.length + 30), // this is the "real" packet length
				(byte)0xEE,
				0x01,
				0x22, 0x33, 0x44, 0x55,
				0x11, 0x22, 0x33, 0x44, 0x55, 0x66,
				0x66, 0x77,
				0x55, 0x44, 0x33, 0x22,
				0x66, 0x55, 0x44, 0x33, 0x22, 0x11,
				0x77, 0x66,
				0x7F, 0x7E, 0x7D, 0x7C, 0x7B, 0x7A, 0x79, 0x78, 0x77, 0x76, 0x75,
				0x00 // filler byte
		};
		assertEquals("refData.length", 42, refData.length);
		
		byte[] actualData = new byte[42];
		int copiedLength = idp.packet.rdBytes(0, NetPacket.MAX_PACKET_SIZE, actualData, 0, NetPacket.MAX_PACKET_SIZE);
		assertEquals("copiedLength", actualData.length, copiedLength);
		this.checkByteArrayContents(refData, actualData);
	}
	
	@Test
	public void testPEXReadContent() {
		NetPacket packet = this.mkNetPacket();
		IDP idp = new IDP(packet);
		PEX pex = new PEX(idp);
		
		assertEquals("pex.identification", 0x1E1F2021, pex.getIdentification());
		assertEquals("pex.clientType", 0x2223, pex.getClientType());
		assertEquals("pex.payloadLen", TESTCONTENT.length - 36, pex.getPayloadLength());
		
		byte[] refData = {
				0x24, 0x25, 0x26, 0x27,
				0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F,
				0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,
				0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F	
		};
		
		byte[] actualData = new byte[TESTCONTENT.length - 36];
		int copiedLength = pex.rdBytes(0, NetPacket.MAX_PACKET_SIZE, actualData, 0, NetPacket.MAX_PACKET_SIZE);
		assertEquals("copiedLength", actualData.length, copiedLength);
		this.checkByteArrayContents(refData, actualData);
	}
	
	@Test
	public void testPEXWriteContent() {
		PEX pex = new PEX();
		
		pex.setIdentification(0x55667788);
		pex.setClientType(0x3344);
		
		byte[] payloadContent = { 0x7F, 0x7E, 0x7D, 0x7C, 0x7B, 0x7A, 0x79, 0x78, 0x77, 0x76, 0x75 }; // length == 11
		assertEquals("payloadContent.length", 11, payloadContent.length);
		for (int i = 0; i < payloadContent.length; i++) {
			pex.wrByte(i, payloadContent[i]);
		}
		pex.setPayloadLength(payloadContent.length);

		assertEquals("payloadContent.length", 11, payloadContent.length);
		assertEquals("pex.payloadLength", payloadContent.length, pex.getPayloadLength());
		assertEquals("idp.payloadLength", payloadContent.length + 6, pex.idp.getPayloadLength()); // odd
		assertEquals("idp.length", payloadContent.length + 6 + 30, pex.idp.getLength()); // odd
		assertEquals("packet.payloadLength", payloadContent.length + 6 + 31, pex.idp.packet.getPayloadLength()); // even
		
		byte[] refData = {
			(byte)0xFF, (byte)0xFF, // IDP automatically resets the checksum => 0xFFFF
			0x00, (byte)(payloadContent.length + 6 + 30),
			0x00,
			IDP.PacketType.PEX.getPacketTypeCode(),
			0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00,
			0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00,
			0x55, 0x66, 0x77, (byte)0x88,
			0x33, 0x44,
			0x7F, 0x7E, 0x7D, 0x7C, 0x7B, 0x7A, 0x79, 0x78, 0x77, 0x76, 0x75,
			0x00 // filler byte
		};
		assertEquals("refData.length", payloadContent.length + 6 + 31, refData.length); // even
		
		byte[] actualData = new byte[refData.length];
		int copiedLength = pex.idp.packet.rdBytes(0, NetPacket.MAX_PACKET_SIZE, actualData, 0, NetPacket.MAX_PACKET_SIZE);
		assertEquals("copiedLength", actualData.length, copiedLength);
		this.checkByteArrayContents(refData, actualData);
	}
	
	@Test
	public void testSPPReadData() {
		NetPacket packet = this.mkNetPacket();
		IDP idp = new IDP(packet);
		SPP spp = new SPP(idp);
		
		assertEquals("spp.connectionControl", 0x1E, spp.getConnectionControl());
		assertEquals("spp.datastreamType", 0x1F, spp.getDatastreamType());
		assertEquals("spp.scrConnectionId", 0x2021, spp.getSrcConnectionId());
		assertEquals("spp.dstConnectionId", 0x2223, spp.getDstConnectionId());
		assertEquals("spp.sequenceNumber", 0x2425, spp.getSequenceNumber());
		assertEquals("spp.acknowloedgeNumber", 0x2627, spp.getAcknowledgeNumber());
		assertEquals("spp.allocationNumber", 0x2829, spp.getAllocationNumber());
		
		assertEquals("spp.payloadLen", TESTCONTENT.length - 42, spp.getPayloadLength());
		
		byte[] refData = {
				0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F,

				0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,
				0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F
		};
		assertEquals("refData.length", TESTCONTENT.length - 42, refData.length);
		
		byte[] actualData = new byte[TESTCONTENT.length - 42];
		int copiedLength = spp.rdBytes(0, NetPacket.MAX_PACKET_SIZE, actualData, 0, NetPacket.MAX_PACKET_SIZE);
		assertEquals("copiedLength", actualData.length, copiedLength);
		this.checkByteArrayContents(refData, actualData);
	}
	
	@Test
	public void testSPPWriteData() {
		SPP spp = new SPP();
		
		spp.setConnectionControl((byte)0xEE);
		spp.setDatastreamType((byte)0xDD);
		spp.setSrcConnectionId(0x5544);
		spp.setDstConnectionId(0x3322);
		spp.setSequenceNumber(0x7733);
		spp.setAcknowledgeNumber(0x1155);
		spp.setAllocationNumber(0x4466);
		
		byte[] payloadContent = { 0x7F, 0x7E, 0x7D, 0x7C, 0x7B, 0x7A, 0x79, 0x78, 0x77, 0x76, 0x75 }; // length == 11
		for (int i = 0; i < payloadContent.length; i++) {
			spp.wrByte(i, payloadContent[i]);
		}
		spp.setPayloadLength(payloadContent.length);
		
		assertEquals("payloadContent.length", 11, payloadContent.length);
		assertEquals("spp.payloadLength", payloadContent.length, spp.getPayloadLength());
		assertEquals("idp.payloadLength", payloadContent.length + 12, spp.idp.getPayloadLength()); // odd
		assertEquals("idp.length", payloadContent.length + 12 + 30, spp.idp.getLength()); // odd
		assertEquals("packet.payloadLength", payloadContent.length + 12 + 31, spp.idp.packet.getPayloadLength()); // even

		byte[] refData = {
			(byte)0xFF, (byte)0xFF, // IDP automatically resets the checksum => 0xFFFF
			0x00, (byte)(payloadContent.length + 12 + 30),
			0x00,
			IDP.PacketType.SPP.getPacketTypeCode(),
			0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00,
			0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00,
			(byte)0xEE,
			(byte)0xDD,
			0x55, 0x44,
			0x33, 0x22,
			0x77, 0x33,
			0x11, 0x55,
			0x44, 0x66,
			0x7F, 0x7E, 0x7D, 0x7C, 0x7B, 0x7A, 0x79, 0x78, 0x77, 0x76, 0x75,
			0x00 // filler byte
		};
		assertEquals("refData.length", payloadContent.length + 12 + 31, refData.length); // even
		
		byte[] actualData = new byte[refData.length];
		int copiedLength = spp.idp.packet.rdBytes(0, NetPacket.MAX_PACKET_SIZE, actualData, 0, NetPacket.MAX_PACKET_SIZE);
		assertEquals("copiedLength", actualData.length, copiedLength);
		this.checkByteArrayContents(refData, actualData);
	}
	
	@Test
	public void testErrorReadData() {
		NetPacket packet = this.mkNetPacket();
		IDP idp = new IDP(packet);
		Error err = new Error(idp);
		
		assertEquals("err.errorCode", ErrorCode.UNSPECIFIED, err.getErrorCode());
		assertEquals("err.errorParam", 0x2021, err.getErrorParam());
		assertEquals("err.payloadLen", TESTCONTENT.length - 34, err.getPayloadLength());
		
		IDP offending = err.getOffendingIdpPaket();
		assertEquals("offending.payloadLength", 0, offending.getPayloadLength());
		assertEquals("offending.checksum", 0x2223, offending.getChecksum());
		assertEquals("offending.length", 0x2425, offending.getLength());
		assertEquals("offending.transportControl", 0x26, offending.getTransportControl());
		assertEquals("offending.packetTypeCode", 0x27, offending.getPacketTypeCode());
		assertEquals("offending.dstNetwork", 0x28292A2B, offending.getDstNetwork());
		assertEquals("offending.dstHost", 0x2C2D2E2F3031L, offending.getDstHost());
		assertEquals("offending.dstSocket", 0x3233, offending.getDstSocket());
		assertEquals("offending.srcNetwork", 0x34353637, offending.getSrcNetwork());
		assertEquals("offending.srcHost", 0x38393A3B3C3DL, offending.getSrcHost());
		assertEquals("offending.srcSocket", 0x3E3F, offending.getSrcSocket());
	}
	
	@Test
	public void testErrorWriteData() {
		NetPacket offendingPacket = this.mkNetPacket();
		IDP offendingIdp = new IDP(offendingPacket);
		
		Error err = new Error(ErrorCode.PROTOCOL_VIOLATION, 0x1234, offendingIdp);
		
		assertEquals("err.payloadLength", 64, err.getPayloadLength());
		assertEquals("idp.payloadLength", 68, err.idp.getPayloadLength());
		assertEquals("idp.length", 98, err.idp.getLength());
		
		byte[] refData = {
				(byte)0xFF, (byte)0xFF, // IDP automatically resets the checksum => 0xFFFF
				0x00, (byte)98,
				0x00,
				IDP.PacketType.ERROR.getPacketTypeCode(),
				0x12, 0x13, 0x14, 0x15,             // dest network (== src network of offending packet)
				0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, // dest host (==src host of offending packet)
				0x00, 0x03,                         // dest socket == error 
				0x06, 0x07, 0x08, 0x09,             // src network (== dest network of offending packet)
				0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, // src host (== dest host of offending packet)
				0x10, 0x11,                         // src socket (== dest socket of offending packet)
				0x00, 0x06, // ErrorCode.PROTOCOL_VIOLATION
				0x12, 0x34, // error param
				
				// offending packet (first <n> bytes)
				0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
				0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
				0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
				0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F,
				0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
				0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F,
				0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,
				0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F
		};
		assertEquals("refData.length", 98, refData.length);
		
		byte[] actualData = new byte[refData.length];
		int copiedLength = err.idp.packet.rdBytes(0, NetPacket.MAX_PACKET_SIZE, actualData, 0, NetPacket.MAX_PACKET_SIZE);
		assertEquals("copiedLength", actualData.length, copiedLength);
		this.checkByteArrayContents(refData, actualData);
	}
	
	@Test
	public void testNominalSizes() {
		final int maxNetPayloadSize = NetPacket.MAX_PACKET_SIZE;
		final int maxIdpPayloadSize = NetPacket.MAX_PACKET_SIZE - IDP.IDP_DATA_START;
		final int maxSppPayloadSize = NetPacket.MAX_PACKET_SIZE - IDP.IDP_DATA_START - SPP.SPP_DATA_START;
		final int maxPexPayloadSize = NetPacket.MAX_PACKET_SIZE - IDP.IDP_DATA_START - PEX.PEX_DATA_START;
		final int maxErrPayloadSize = NetPacket.MAX_PACKET_SIZE - IDP.IDP_DATA_START - Error.ERROR_DATA_START;
		
		NetPacket netPacket = new NetPacket();
		assertEquals("netPacket.getMaxPayloadLength()", maxNetPayloadSize, netPacket.getMaxPayloadLength());
		
		IDP idp1 = new IDP();
		assertEquals("idp1.getMaxPayloadLength()", maxIdpPayloadSize, idp1.getMaxPayloadLength());
//		IDP idp2 = new IDP(new NetPacket());
//		assertEquals("idp2.getMaxPayloadLength()", maxIdpPayloadSize, idp2.getMaxPayloadLength());

		SPP spp1 = new SPP();
		assertEquals("spp1.getMaxPayloadLength()", maxSppPayloadSize, spp1.getMaxPayloadLength());
//		SPP spp2 = new SPP(new IDP());
//		assertEquals("spp2.getMaxPayloadLength()", maxSppPayloadSize, spp2.getMaxPayloadLength());
//		SPP spp3 = new SPP(new IDP(new NetPacket()));
//		assertEquals("spp3.getMaxPayloadLength()", maxSppPayloadSize, spp3.getMaxPayloadLength());

		PEX pex1 = new PEX();
		assertEquals("pex1.getMaxPayloadLength()", maxPexPayloadSize, pex1.getMaxPayloadLength());
//		PEX pex2 = new PEX(new IDP());
//		assertEquals("pex2.getMaxPayloadLength()", maxPexPayloadSize, pex2.getMaxPayloadLength());
//		PEX pex3 = new PEX(new IDP(new NetPacket()));
//		assertEquals("pex3.getMaxPayloadLength()", maxPexPayloadSize, pex3.getMaxPayloadLength());

//		Error err1 = new Error();
//		assertEquals("err1.getMaxPayloadLength()", maxErrPayloadSize, err1.getMaxPayloadLength());
//		Error err2 = new Error(new IDP());
//		assertEquals("err2.getMaxPayloadLength()", maxErrPayloadSize, err2.getMaxPayloadLength());
//		Error err3 = new Error(new IDP(new NetPacket()));
//		assertEquals("err3.getMaxPayloadLength()", maxErrPayloadSize, err3.getMaxPayloadLength());
	}

}
