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

import java.io.File;
import java.io.FileInputStream;

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
	
	private static final String DEFAULT_CONFIG_FILE = "dodo.properties";
	
	// the machine running the Dodo server
	private static iNetMachine localSite;
	
	// configuration data
	private static String configFilename;
	private static long networkNo = 0x0401;
	private static long machineId = LocalSite.getMachineId();
	private static boolean doChecksums = true;
	private static String netHubHost = "localhost";
	private static int netHubPort = 3333;
	private static int localTimeOffsetMinutes = 0;
	private static int daysBackInTime = 0;
	

	private static boolean initializeConfiguration(String filename) {
		if (!filename.endsWith(".properties")) { filename += ".properties"; }
		File cfgFile = new File(filename);
		if (!cfgFile.canRead()) {
			System.err.printf("Error: unable to read configuration properties file: %s\n", filename);
			return false;
		}
		configFilename = filename;
		
		PropertiesExt props = new PropertiesExt();
		try {
			FileInputStream fis = new FileInputStream(cfgFile);
			props.load(fis);
			fis.close();
		} catch (Exception e) {
			System.err.printf("Error: unable to load configuration from properties file: %s\n", filename);
			System.err.printf("=> %s\n", e.getMessage());
			return false;
		}
		
		networkNo = props.getLong("networkNo", networkNo);
		String mId = props.getString("machineId", null);
		doChecksums = props.getBoolean("useChecksums", doChecksums);
		netHubHost = props.getString("netHubHost", netHubHost);
		netHubPort = props.getInt("netHubPort", netHubPort);
		localTimeOffsetMinutes = props.getInt("localTimeOffsetMinutes", localTimeOffsetMinutes);
		daysBackInTime = props.getInt("daysBackInTime", daysBackInTime);
		
		if (mId != null) {
			String[] submacs = mId.split("-");
			if (submacs.length != 6) {
				System.err.printf("Error: invalid processor id format (not XX-XX-XX-XX-XX-XX): %s\n", mId);
				return false;
			}
			
			long macId = 0;
			for (int i = 0; i < submacs.length; i++) {
				macId = macId << 8;
				try {
					macId |= Integer.parseInt(submacs[i], 16) & 0xFF;
				} catch (Exception e) {
					System.err.printf("Error: invalid processor id format (not XX-XX-XX-XX-XX-XX): %s\n", mId); 
					return false;
				}
			}
			machineId = macId;
		}
		
		return true;
	}

	public static void main(String[] args) throws XnsException {
		String cfgFile = null;

		for (String arg : args) {
			if (cfgFile == null) {
				cfgFile = arg;
			} else {
				System.out.printf("Warning: ignoring unknown argument: %s\n", arg);
			}
		}
		if (cfgFile == null && (new File(DEFAULT_CONFIG_FILE)).canRead()) {
			cfgFile = DEFAULT_CONFIG_FILE;
		}
		if (cfgFile != null && !initializeConfiguration(cfgFile)) {
			return;
		}
		
		LocalSite.configureHub(netHubHost, netHubPort);
		LocalSite.configureLocal(networkNo, machineId, "DodoServer", doChecksums);
		localSite = LocalSite.getInstance();
		
		// echo service
		localSite.clientBindToSocket(
				IDP.KnownSocket.ECHO.getSocket(), 
				new EchoResponder());
		
		// time service
		localSite.pexListen(
				IDP.KnownSocket.TIME.getSocket(), 
				new TimeServiceResponder(localTimeOffsetMinutes, daysBackInTime));
		
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
