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

import java.util.HashMap;
import java.util.Map;

import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level1.IDP.PacketType;
import dev.hawala.xns.level2.Error;

/**
 * (obsolete)
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class NetSwitch implements iIDPSender {
	
	private final long networkId;
	private final iIDPSender externalSender;
	
	public NetSwitch(long networkId, iIDPSender sender) {
		this.networkId = networkId;
		this.externalSender = sender; 
	}
	
	private final Map<Long,NetMachine> localMachines = new HashMap<>();
	
	public void send(IDP idp) {
		if (idp.getPacketType() == PacketType.ERROR) {
			Error err = new Error(idp);
			if (err.getOffendingIdpPaket().getPacketType() == PacketType.ERROR) {
				return; // avoid endless error packed  ping-pong: swallow error packets for errors
			}
		}
		
		if (idp.getDstNetwork() == this.networkId) {
			Long target = idp.getDstHost();
			synchronized(this.localMachines) {
				if (this.localMachines.containsKey(target)) {
					this.localMachines.get(target).handlePacket(idp);
					return;
				}
			}
		}
		
		this.externalSender.send(idp);
	}

	public NetMachine addMachine(long macAddress, String name) {
		NetMachine machine = new NetMachine(this.networkId, macAddress, name, this, true);
		synchronized(this.localMachines) {
			this.localMachines.put(macAddress, machine);
		}
		return machine;
	}
	
	public NetMachine getMachine(long macAddress) {
		synchronized(this.localMachines) {
			return this.localMachines.get(macAddress);
		}
	}

	public void removeMachine(long macAddress) {
		synchronized(this.localMachines) {
			this.localMachines.remove(macAddress);
		}
	}
	
	public void handleNewPacket(IDP idp) {
		// check for our network
		if (idp.getDstNetwork() != this.networkId) {
			return;
		}
		
		// get the target machine, giving a warning if there seems to be a mac address collision 
		NetMachine target = null;
		synchronized(this.localMachines) {
			if (this.localMachines.containsKey(idp.getSrcHost())) {
				System.err.printf(
						"WARNING: possible duplicate MAC address %06X on wire (external source for machine on this switch)\n",
						idp.getSrcHost());
			}
			target = this.localMachines.get(idp.getDstHost());
		}
		if (target != null) {
			target.handlePacket(idp);
		}
	}
}
