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
import dev.hawala.xns.iPexResponder;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level1.IDP.PacketType;
import dev.hawala.xns.level2.Error;
import dev.hawala.xns.level2.Error.ErrorCode;
import dev.hawala.xns.level2.PEX;

/**
 * (obsolete)
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class PexServerListener implements iIDPReceiver {
	
	private final iPexResponder responder;
	
	private EndpointAddress localEndpoint = null;
	private iIDPSender sender = null;
	
	public PexServerListener(iPexResponder responder) {
		this.responder = responder;
	}

	@Override
	public void start(EndpointAddress localEndpoint, iIDPSender sender) {
		this.localEndpoint = localEndpoint;
		this.sender = sender;
	}

	@Override
	public void accept(IDP idp) {
		if (idp.getPacketType() != PacketType.PEX) {
			Error err = new Error(ErrorCode.INVALID_PACKET_TYPE, 0, idp);
			this.sender.send(err.idp);
			return;
		}
		
		PEX request = new PEX(idp);
		long clientIdentification = request.getIdentification();
		int clientType = request.getClientType();
		byte[] buffer = new byte[request.getPayloadLength()];
		request.rdBytes(0, buffer.length, buffer, 0, buffer.length);
		
		responder.handlePacket(
			idp.getSrcHost(),
			clientType,
			buffer,
			(b,o,l) -> {
				PEX response = new PEX();
				response.idp.asReplyTo(request.idp);
				response.idp.setSrcHost(localEndpoint.host);
				response.idp.setSrcNetwork(localEndpoint.network);
				response.setIdentification(clientIdentification);
				response.setClientType(clientType);
				response.wrBytes(0, l, b, o, l);
				response.idp.resetChecksum();
				sender.send(response.idp);
			},
			(c,p) -> {
				Error err = new Error(c, p, request.idp);
				sender.send(err.idp);
			});
	}

	@Override
	public void acceptError(Error err) {
		// TODO: somehow handle error response
		// this can only be an error to a PEX service response sent by the responder
		// => simply log and ignore ??
	}

	@Override
	public void stopped() {
		// nothing to do
	}

}
