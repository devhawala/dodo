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

package dev.hawala.xns;

import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level3.courier.CourierServer;
import dev.hawala.xns.level4.auth.Authentication2Impl;
import dev.hawala.xns.level4.auth.BfsAuthenticationResponder;
import dev.hawala.xns.level4.chs.BfsClearinghouseResponder;
import dev.hawala.xns.level4.chs.Clearinghouse3Impl;
import dev.hawala.xns.level4.echo.EchoResponder;
import dev.hawala.xns.level4.rip.RipResponder;
import dev.hawala.xns.level4.time.TimeServiceResponder;

/**
 * Simple XNS server providing some services from the Xerox world.
 * <p>
 * As for the name "Dodo" for the machine name. It had to be a name
 * beginning with the letter "D" (following the Xerox tradition for
 * XNS machines) but it had to be something no longer existing (as
 * more or less general for Xerox servers), so the idea for the
 * extinct Dodo bird came up).   
 *  </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class DodoServer {
	
	private static iNetMachine localSite;

	public static void main(String[] args) throws XnsException {
		
//		LocalSite.configureHub(null, 0);
		LocalSite.configureLocal(0x0401, LocalSite.getMachineId(), "DodoServer", true);
		localSite = LocalSite.getInstance();
		
		// echo service
		localSite.clientBindToSocket(
				IDP.KnownSocket.ECHO.getSocket(), 
				new EchoResponder());
		
		// time service
		localSite.pexListen(
				IDP.KnownSocket.TIME.getSocket(), 
				new TimeServiceResponder());
		
		// wakeup requestor
		iWakeupRequestor wakeupper = null;
//		wakeupper = new WakeupRequestor();
//		localSite.clientBindToFreeSocket(wakeupper);
		// wakeupper = new WsInfoRequestor(localSite);
		
		// broadcast for clearinghouse service
		// one common implementation for versions 2 and 3, as both versions have the same RetrieveAddresses method
		Clearinghouse3Impl.init(localSite.getNetworkId(), localSite.getMachineId());
		localSite.pexListen(
				IDP.KnownSocket.CLEARINGHOUSE.getSocket(), 
				new BfsClearinghouseResponder(),
				wakeupper);
		
		// broadcast for authentication service
		// one common implementation for versions 1, 2 and 3, as all versions are assumed to have
		// the same (undocumented) RetrieveAddresses method
		Authentication2Impl.init(localSite.getNetworkId(), localSite.getMachineId());
		localSite.pexListen(
				IDP.KnownSocket.AUTH.getSocket(), 
				new BfsAuthenticationResponder());
		
		// routing protocol responder
		localSite.clientBindToSocket(
				IDP.KnownSocket.ROUTING.getSocket(),
				new RipResponder());
		
		// register courier programs in registry
		Clearinghouse3Impl.register();
		Authentication2Impl.register();
		
		// Courier server with dispatcher
		CourierServer courierServer = new CourierServer(localSite);
		
		// silence logging a bit
		Log.L0.doLog(false);
		Log.L1.doLog(false);
		Log.L2.doLog(false);
		Log.L3.doLog(false);
		Log.L4.doLog(false);
		
		/*
		 * let the server machine run...
		 */
	}

}
