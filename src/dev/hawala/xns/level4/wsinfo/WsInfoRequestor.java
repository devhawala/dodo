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

package dev.hawala.xns.level4.wsinfo;

import java.util.ArrayList;
import java.util.List;

import dev.hawala.xns.Log;
import dev.hawala.xns.iNetMachine;
import dev.hawala.xns.iWakeupRequestor;
import dev.hawala.xns.level0.Payload;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level2.PEX;

/**
 * (Attempt of a) WsInfo protocol requestor, in the futile intention
 * to wake up a workstation and let it start using the SPP protocol.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class WsInfoRequestor implements iWakeupRequestor {

	private final iNetMachine netMachine;
	
	private final List<Long> alreadyWaked = new ArrayList<>();
	
	public WsInfoRequestor(iNetMachine machine) {
		this.netMachine = machine; 
	}

	@Override
	public synchronized void wakeUp(Long host) {
		if (this.alreadyWaked.contains(host)) {
			return;
		}
		this.alreadyWaked.add(host);
		
		Log.I.printf("WAKE", "WsInfoRequestor waking up %06X\n", host);
		
		Thread thr = new Thread(() -> {
			try {
				Thread.sleep(5); // wait 5 milliseconds 
				
				byte[] request = { 0, 1, 0, 1 }; // unknown required content (undocumented), assuming it is"version=1,request=1" (in analogy to time)
				Payload result = this.netMachine.pexRequest(
						host,
						IDP.KnownSocket.WS_INFO.getSocket(),
						PEX.ClientType.UNSPECIFIED.getTypeValue(),
						request, 0, request.length);
				if (result != null) {
					Log.I.printf("WAKE", "WsInfoRequestor result: %s\n", result.payloadToString());
				} else {
					Log.I.printf("WAKE", "WsInfoRequestor no response (timeout)\n");
				}
			} catch (Exception e) {
				Log.E.printf("WAKE", "WsInfoRequestor: failed with error: %s\n", e.getMessage());
			}
		});
		thr.setDaemon(true);
		thr.start();
	}
	
}
