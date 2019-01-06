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

import java.util.HashMap;
import java.util.Map;

import dev.hawala.xns.EndpointAddress;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level2.Error;
import dev.hawala.xns.network.iIDPReceiver;
import dev.hawala.xns.network.iIDPSender;

/**
 * XNS Routing protocol service.
 * <p>
 * This service broadcasts the minimal routing information
 * for the local network at regular intervals (once a minute).
 * Broadcasting can also be triggered by a server internal event,
 * for example a workstation having sent a BfS request.
 * <br/>This service also implements the responder for answering
 * explicit routing information requests by a machine.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018,2019)
 */
public class RipResponder implements iIDPReceiver, RoutingInformer, Runnable {
	
	private static final long BROADCAST_INTERVAL = 60000L; // ms, interval between regular broadcasts
	private static final long CHECK_INTERVAL = 2; // ms, interval for checking if a broadcast should happen
	private static final long INHIBIT_INTERVAL = 3000L; // ms, interval before an implicit broadcast request is honored for the same machine
	
	private EndpointAddress localEndpoint = null;
	private iIDPSender sender = null;

	private Thread broadcaster = null;
	private long nextBroadcastTS = System.currentTimeMillis() + BROADCAST_INTERVAL;
	private Map<Long,Long> machineInhibits = new HashMap<>();
	
	private final IDP broadcastIdp = new IDP();

	@Override
	public synchronized void start(EndpointAddress localEndpoint, iIDPSender sender) {
		this.localEndpoint = localEndpoint;
		this.sender = sender;
		
		// setup broadcast packet (one single packet is used, as the routing information does not change)
		// 1. data part: routing response, local network is 1 hop away
		this.broadcastIdp.wrCardinal(0, 2); // response
		this.broadcastIdp.wrCardinal(2, (int)((localEndpoint.network >> 16) & 0xFFFF));
		this.broadcastIdp.wrCardinal(4, (int)(localEndpoint.network & 0xFFFF));
		this.broadcastIdp.wrCardinal(6, 1); // 1 hop
		this.broadcastIdp.setPayloadLength(8);
		this.broadcastIdp.setPacketType(IDP.PacketType.RIP);
		// 2. destination: broadcast in this network to RIP port
		this.broadcastIdp.setDstHost(IDP.BROADCAST_ADDR);
		this.broadcastIdp.setDstNetwork(localEndpoint.network);
		this.broadcastIdp.setDstSocket(IDP.KnownSocket.ROUTING.getSocket());
		// 3. source: this machine in this network from RIP port
		this.broadcastIdp.setSrcHost(localEndpoint.host);
		this.broadcastIdp.setSrcNetwork(localEndpoint.network);
		this.broadcastIdp.setSrcSocket(IDP.KnownSocket.ROUTING.getSocket());
		
		// setup sending broadcasts
		this.broadcaster = new Thread(this);
		this.broadcaster.setName("Routing broadcaster");
		this.broadcaster.start();
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
		
		// send routing information
		this.sendBroadcast(true);
	}

	@Override
	public void acceptError(Error err) {
		// this is a broadcast sender/responder, so ignore errors (none really expected)
	}

	@Override
	public synchronized void stopped() {
		this.sender = null;
		if (this.broadcaster != null) {
			this.broadcaster.interrupt();
			try { this.broadcaster.join(); } catch (InterruptedException e) { }
			this.broadcaster = null;
		}
	}

	@Override
	public void run() {
		try {
			while(true) {
				Thread.sleep(CHECK_INTERVAL);
				this.sendBroadcast(false);
			}
		} catch (InterruptedException e) {
			// simply terminate broadcasting
		}
	}

	public synchronized void requestBroadcast(long requestingMachineId) {
		long now = System.currentTimeMillis();
		if (this.machineInhibits.containsKey(requestingMachineId)) {
			long notBeforeTs = this.machineInhibits.get(requestingMachineId);
			if (now < notBeforeTs) {
				return;
			}
		}
		this.machineInhibits.put(requestingMachineId, now + INHIBIT_INTERVAL);
		this.nextBroadcastTS = System.currentTimeMillis() + 1;
	}
	
	private synchronized void sendBroadcast(boolean forceSend) {
		long now = (forceSend) ? this.nextBroadcastTS : System.currentTimeMillis();
		if (this.sender != null && now >= this.nextBroadcastTS) {
			this.sender.send(this.broadcastIdp);
			this.nextBroadcastTS = now + BROADCAST_INTERVAL;
		}
	}
	
}
