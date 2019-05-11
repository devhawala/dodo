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

import dev.hawala.xns.level0.NetPacket;
import dev.hawala.xns.level0.Payload;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level2.PEX;

public abstract class Tests {
	
	private final static long HOST_ADDRESS   = 0x0000888801020304L;
	
	private final static long BROADCAST_HOST = 0x0000FFFFFFFFFFFFL;
	
	private final static long NETWORK_NUMBER = 0x1234;
	private final static byte[] NETWORK_NUMBER_BYTES = { 0x00, 0x00, 0x12, 0x34 };
	
	private static class WorkPayload extends Payload {

		public WorkPayload(int size) { super(size); }

		@Override
		public long getPacketId() { return 0; }
		
	}

	public static void main(String[] args) {
		
		int[] rawData = {
			0x17, 0x59, // checksum
			0x00, 0x28, // length = 40
			0x00,       // transportControl
			0x04,       // packetType
			
			0x00, 0x00, 0x00, 0x00,             // dst network number
			0xff, 0xff, 0xff, 0xff, 0xff, 0xff, // dst address
			0x00, 0x30,                         // dst socket
			
			0x00, 0x00, 0x00, 0x00,             // src network number
			0x08, 0x00, 0x27, 0xd2, 0x97, 0x6e, // src address
			0x00, 0x30,                         // src socket
			
			// IDP payload
			0x91, 0x35,	0x05, 0x99, 0x00, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
		};
		
		byte[] rawPayload = new byte[rawData.length];
		for (int i = 0; i < rawData.length; i++) { rawPayload[i] = (byte)(0xFF & rawData[i]); }

		NetPacket netPacket = new NetPacket(rawPayload);
		IDP idpPacket = new IDP(netPacket);
		
		switch(idpPacket.getPacketType()) {
		case PEX:
			PEX pexPacket = new PEX(idpPacket);
			System.out.printf(pexPacket.toString());
			System.out.printf("\npayload %s\n", pexPacket.payloadToString());
			
			System.out.printf("\nraw packet payload %s\n", pexPacket.idp.packet.payloadToString());
			
//			pexPacket.idp.computeChecksum_A();
//			System.out.printf("=> recomputed checksum_A: 0x%04X\n", pexPacket.idp.getChecksum());
//			pexPacket.idp.computeChecksum_B();
//			System.out.printf("=> recomputed checksum_B: 0x%04X\n", pexPacket.idp.getChecksum());
//			pexPacket.idp.computeChecksum_C();
//			System.out.printf("=> recomputed checksum_C: 0x%04X\n", pexPacket.idp.getChecksum());
//			pexPacket.idp.computeChecksum_D();
//			System.out.printf("=> recomputed checksum_D: 0x%04X\n", pexPacket.idp.getChecksum());

			pexPacket.idp.updateChecksum();
			System.out.printf("=> recomputed checksum: 0x%04X\n", pexPacket.idp.getChecksum());
			
			PEX response = new PEX(NETWORK_NUMBER_BYTES);
			
			response.idp.asReplyTo(pexPacket.idp);
			response.idp.setDstNetwork(NETWORK_NUMBER);
			response.idp.setSrcNetwork(NETWORK_NUMBER);
			response.idp.setSrcHost(HOST_ADDRESS);
			
			response.setIdentification(pexPacket.getIdentification());
			response.setClientType(pexPacket.getClientType());

			System.out.printf("\n=> response packet:\n");
			System.out.printf(response.toString());
			System.out.printf("\npayload %s\n", response.payloadToString());
			
			System.out.printf("\nraw packet payload %s\n", response.idp.packet.payloadToString());
			
			Payload checkPayload = new WorkPayload(response.idp.getLength());
			checkPayload.copy(response.idp.packet);
			System.out.printf("\n     check payload %s\n", checkPayload.payloadToString());
			break;
		default:
			System.out.printf(idpPacket.toString());
			System.out.printf("\npayload %s", idpPacket.payloadToString());
			break;
		}
		System.out.println();
	}
	
	private boolean isWhereAmI(PEX pex) {
		return this.isWhereAmI(pex.idp);
	}
	
	private boolean isWhereAmI(IDP idp) {
		return (idp.getSrcNetwork() == 0 && idp.getDstNetwork() == 0 && idp.getDstHost() == BROADCAST_HOST);
	}
	
	private PEX createWhereAmIReply(PEX pexPacket) {
		PEX response = new PEX(NETWORK_NUMBER_BYTES);
		
		response.idp.asReplyTo(pexPacket.idp);
		response.idp.setDstNetwork(NETWORK_NUMBER);
		response.idp.setSrcNetwork(NETWORK_NUMBER);
		response.idp.setSrcHost(HOST_ADDRESS);
		
		response.setIdentification(pexPacket.getIdentification());
		response.setClientType(pexPacket.getClientType());
		
		response.copy(pexPacket);
		
		return response;
	}
	
	

}
