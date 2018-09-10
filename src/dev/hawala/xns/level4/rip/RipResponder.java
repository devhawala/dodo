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

package dev.hawala.xns.level4.rip;

import dev.hawala.xns.EndpointAddress;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level2.Error;
import dev.hawala.xns.network.iIDPReceiver;
import dev.hawala.xns.network.iIDPSender;

/**
 * (Attempt for a) XNS Routing protocol responder.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class RipResponder implements iIDPReceiver {
	
	private EndpointAddress localEndpoint = null;
	private iIDPSender sender = null;

	@Override
	public void start(EndpointAddress localEndpoint, iIDPSender sender) {
		this.localEndpoint = localEndpoint;
		this.sender = sender;
	}

	@Override
	public void accept(IDP idp) {
		if (this.localEndpoint == null || this.sender == null) {
			return; // no way to respond!
		}
		
		// check if it is a request
		int contentLen = idp.getPayloadLength();
		if (contentLen < 1) { return; } // no operation
		int operation = idp.rdCardinal(0);
		if (operation != 1) { return; } // not 'request'
		
		// for now: do a hard response with our local network zero hops away...
//		byte[] responseContent = {
//			// operation = response
//			0x00, 0x02,
//			// (local) network 
//			(byte)((localEndpoint.network >> 24) & 0xFF),
//			(byte)((localEndpoint.network >> 16) & 0xFF),
//			(byte)((localEndpoint.network >> 8) & 0xFF),
//			(byte)(localEndpoint.network & 0xFF),
//			// hop count
//			0x00, 0x00
//		};
//		IDP response = new IDP(responseContent, 0, responseContent.length).asReplyTo(idp);
//		response.wrBytes(0, responseContent.length, responseContent, 0, responseContent.length);
		IDP response = new IDP().asReplyTo(idp);
		response.wrCardinal(0, 2);
		response.wrCardinal(2, (int)((localEndpoint.network >> 16) & 0xFFFF));
		response.wrCardinal(4, (int)(localEndpoint.network & 0xFFFF));
		response.wrCardinal(6, 0);
		response.wrCardinal(8, 0xFFFF);
		response.wrCardinal(10, 0xFFFF);
		response.wrCardinal(12, 16);
		response.setPayloadLength(14);
		response.setPacketType(IDP.PacketType.RIP);

		response.setDstHost(IDP.BROADCAST_ADDR);
		response.setDstNetwork(IDP.LOCAL_NETWORK);
		this.sender.send(response);
	}

	@Override
	public void acceptError(Error err) {
		// this is a broadcast responder, so ignore errors (none really expected)
	}

	@Override
	public void stopped() {
		this.sender = null;
	}

}
