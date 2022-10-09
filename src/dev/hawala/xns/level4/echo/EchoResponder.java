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

package dev.hawala.xns.level4.echo;

import dev.hawala.xns.EndpointAddress;
import dev.hawala.xns.Log;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level2.Error;
import dev.hawala.xns.network.iIDPReceiver;
import dev.hawala.xns.network.iIDPSender;

/**
 * Responder for the XNS Echo protocol.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class EchoResponder implements iIDPReceiver {
	
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
		
		int contentLen = idp.getPayloadLength();
		if (contentLen < 2 || idp.rdCardinal(0) != 1) {
			return; // invalid packet or not  request
		}
		IDP response = new IDP().asReplyTo(idp);
		response.setPacketType(IDP.PacketType.ECHO);
		response.wrCardinal(0, 2); // Header.Operation = response
		for (int i = 2; i < contentLen; i++) {
			response.wrByte(i, idp.rdByte(i));
		}
		response.setPayloadLength(Math.max(2, contentLen));
		this.sender.send(response);
		
	}

	@Override
	public void acceptError(Error err) {
		Log.E.printf("ECHO", "Received error for sent response packet: %s\n", err);
	}

	@Override
	public void stopped() {
		this.sender = null;
	}

}
