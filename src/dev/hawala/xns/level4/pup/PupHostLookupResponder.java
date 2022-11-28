/*
Copyright (c) 2022, Dr. Hans-Walter Latz
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

package dev.hawala.xns.level4.pup;

import dev.hawala.xns.EndpointAddress;
import dev.hawala.xns.MachineIds;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level2.Error;
import dev.hawala.xns.network.iIDPReceiver;
import dev.hawala.xns.network.iIDPSender;

/**
 * Responder for Interlisp-D (Medley) "NS-to-PUP host lookup" broadcast packets.
 * <p>
 * Mapping of XNS (48-bit) addresses to PUP (16-bit) addresses is defined in the
 * file {@code machines.cfg} through {@code + pup.hostAddress =} configuration
 * parameters on 48-bit machine-ids.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2022)
 */
public class PupHostLookupResponder implements iIDPReceiver {
	
	private EndpointAddress localEndpoint = null;
	private iIDPSender sender = null;
	
	private final boolean logPackets;
	
	public PupHostLookupResponder(boolean logPackets) {
		this.logPackets = logPackets;
	}

	@Override
	public void start(EndpointAddress localEndpoint, iIDPSender sender) {
		this.localEndpoint = localEndpoint;
		this.sender = sender;
	}

	@Override
	public void accept(IDP idp) {
		if (this.logPackets) {
			System.out.printf("\n++\n+++ PupHostLookup: packet ::\n%s\n++\n++ payload:", idp.toString());
			for (int i = 0; i < idp.getPayloadLength(); i++) {
				System.out.printf(" %02X", idp.rdByte(i) & 0xFF);
			}
			System.out.printf("\n++\n");
		}
		
		// assumed "NS-to-PUP host lookup" packet structure:
		//   identification (2 words)
		//   packet-type    (1 word: request (1), response (2), or negative (3))
		//   data           (request => 3 words for 48-bit host address , response => 1 word for PUP::net+host , negative => empty)
		if (idp.getPayloadLength() >= 12 && idp.rdCardinal(4) == 1) {
			long w0 = idp.rdCardinal(6);
			long w1 = idp.rdCardinal(8);
			long w2 = idp.rdCardinal(10);
			long hostAddress = (w0 << 32) | (w1 << 16) | w2;
			long pupAddress = MachineIds.getCfgLong(hostAddress, MachineIds.CFG_PUP_HOST_ADDRESS, -1);
			
			IDP response = new IDP().asReplyTo(idp).withSource(this.localEndpoint);
			response.setPacketType(IDP.PacketType.PUP_HOST_LOOKUP);
			if (pupAddress >= 0) {
				response.setPayloadLength(8);
				response.wrLongCardinal(0, idp.rdLongCardinal(0));
				response.wrCardinal(4, 2);     // response
				response.wrCardinal(6, (int)(pupAddress & 0xFFFFL));
			} else {
				response.setPayloadLength(6);
				response.wrLongCardinal(0, idp.rdLongCardinal(0));
				response.wrCardinal(4, 3);     // negative
			}
			
			if (this.logPackets) {
				System.out.printf("\n++\n+++ PupHostLookup: response ::\n%s\n++\n++ payload:", response.toString());
				for (int i = 0; i < response.getPayloadLength(); i++) {
					System.out.printf(" %02X", response.rdByte(i) & 0xFF);
				}
				System.out.printf("\n++\n");
			}
			
			this.sender.send(response.updateChecksum());
		}
	}

	@Override
	public void acceptError(Error err) {
		// this is a broadcast responder, so ignore errors (none really expected)
	}

	@Override
	public void stopped() {
		// nothing to stop
	}

}
