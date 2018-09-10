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

import java.util.ArrayList;
import java.util.List;

import dev.hawala.xns.EndpointAddress;
import dev.hawala.xns.Log;
import dev.hawala.xns.iWakeupRequestor;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level2.Error;
import dev.hawala.xns.network.iIDPReceiver;
import dev.hawala.xns.network.iIDPSender;

/**
 * Wake-up requestor, sending an XNS Echo request and awaiting the response
 * to this request.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class WakeupRequestor implements iIDPReceiver, iWakeupRequestor {
	
	private EndpointAddress localEndpoint = null;
	private iIDPSender sender = null;
	
	private final List<Long> alreadyWaked = new ArrayList<>();
	private int wakeCount = 0;
	
	@Override
	public void wakeUp(Long host) {
		if (this.localEndpoint == null || this.sender == null) {
			return;
		}
		if (this.alreadyWaked.contains(host)) {
			return;
		}
		
		IDP req = new IDP().withSource(this.localEndpoint);
		req.setDstHost(host);
		req.setDstNetwork(this.localEndpoint.network);
		req.setDstSocket(IDP.KnownSocket.ECHO.getSocket());
		req.setPacketType(IDP.PacketType.ECHO);
		req.wrCardinal(0, 1); // Header.Operation = request
		req.wrByte(2, (byte)'W');
		req.wrByte(3, (byte)'a');
		req.wrByte(4, (byte)'k');
		req.wrByte(5, (byte)'e');
		req.wrCardinal(6, ++wakeCount);
		req.setPayloadLength(8);
		
		this.alreadyWaked.add(host);
		
		this.sender.send(req);
	}

	@Override
	public void start(EndpointAddress localEndpoint, iIDPSender sender) {
		this.localEndpoint = localEndpoint;
		this.sender = sender;
	}

	@Override
	public void accept(IDP idp) {
		Log.I.printf("WAKE", "received response to wake up echo request from: %s\n", idp.getSrcEndpoint());
	}

	@Override
	public void acceptError(Error err) {
		Log.E.printf("WAKE", "received ERROR response to wake up echo request from: %s\n", err.idp.getSrcEndpoint());
	}

	@Override
	public void stopped() {
		this.sender = null;
	}

}
