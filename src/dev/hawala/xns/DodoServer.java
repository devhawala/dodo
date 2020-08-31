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
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level2.SppConnection;
import dev.hawala.xns.level3.courier.CourierServer;
import dev.hawala.xns.level4.auth.Authentication2Impl;
import dev.hawala.xns.level4.auth.BfsAuthenticationResponder;
import dev.hawala.xns.level4.boot.BootResponder;
import dev.hawala.xns.level4.chs.BfsClearinghouseResponder;
import dev.hawala.xns.level4.chs.Clearinghouse3Impl;
import dev.hawala.xns.level4.common.AuthChsCommon;
import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;
import dev.hawala.xns.level4.common.ChsDatabase;
import dev.hawala.xns.level4.common.Time2;
import dev.hawala.xns.level4.echo.EchoResponder;
import dev.hawala.xns.level4.filing.FilingImpl;
import dev.hawala.xns.level4.printing.Printing3Impl;
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
 * @author Dr. Hans-Walter Latz / Berlin (2018,2019)
 */
public class DodoServer {

	private static final String DEFAULT_BASECONFIG_FILE = "dodo_common_configurations.properties";
	private static final String DEFAULT_CONFIG_FILE = "dodo.properties";
	
	// the machine running the Dodo server
	private static iNetMachine localSite;
	
	// configuration data
	private static String configFilename;
	private static long networkNo = 0x0401;
	private static long machineId = LocalSite.getMachineId();
	private static boolean doChecksums = true;
	private static boolean doDarkstarWorkaround = false;
	private static String netHubHost = "localhost";
	private static int netHubPort = 3333;
	
	private static int localTimeOffsetMinutes = 0;
	private static int daysBackInTime = 0;
	private static int timeServiceSendingTimeGap = 0;
	
	private static boolean startEchoService = true;
	private static boolean startTimeService = true;
	private static boolean startChsAndAuth = true;
	private static boolean startRipService = true;
	private static boolean startBootService = false;
	
	private static String organizationName = "hawala";
	private static String domainName = "dev";
	private static boolean strongKeysAsSpecified = true;
	private static boolean authSkipTimestampChecks = false;
	private static String chsDatabaseRoot = null;
	
	// startPrintServer <=> printServiceName != null && printServiceOutputDirectory != null 
	private static String printServiceName = null;
	private static String printServiceOutputDirectory = null;
	private static String printServicePaperSizes = null;
	private static boolean printServiceDisassembleIp = false;
	private static String printServiceIp2PsProcFilename = null;
	private static String printServicePsPostprocessor = null;
	
	private static String bootServiceBasedir = "bootsvc";
	private static boolean bootServiceVerbose = false;
	private static int bootServiceSimpleDataSendInterval = -1;
	
	private static int sppHandshakeCheckInterval = -1;
	private static int sppHandshakeSendackCountdown = -1;
	private static int sppHandshakeResendCountdown = -1;
	private static int sppHandshakeMaxResends = -1;
	private static int sppResendDelay = -1;
	private static int sppSendingTimeGap = -1;
	private static int sppResendPacketCount = -1;
	
	// startFileServer <=> fileServiceSpecs.size() > 0
	private static Map<String,String> fileServiceSpecs = new HashMap<>();

	private static boolean initializeConfiguration(String filename) {
		// load the properties file
		if (!filename.endsWith(".properties")) { filename += ".properties"; }
		File cfgFile = new File(filename);
		if (!cfgFile.canRead()) {
			System.err.printf("Error: unable to read configuration properties file: %s\n", filename);
			return false;
		}
		configFilename = filename;
		
		PropertiesExt props = new PropertiesExt(cfgFile);
		
		// get the values
		networkNo = props.getLong("networkNo", networkNo);
		String mId = props.getString("machineId", null);
		doChecksums = props.getBoolean("useChecksums", doChecksums);
		doDarkstarWorkaround = props.getBoolean("ether.useDarkstarWorkaround", doDarkstarWorkaround);
		netHubHost = props.getString("netHubHost", netHubHost);
		netHubPort = props.getInt("netHubPort", netHubPort);
		
		localTimeOffsetMinutes = props.getInt("localTimeOffsetMinutes", localTimeOffsetMinutes);
		localTimeOffsetMinutes = props.getInt("timeService.localTimeOffsetMinutes", localTimeOffsetMinutes);
		daysBackInTime = props.getInt("daysBackInTime", daysBackInTime);
		daysBackInTime = props.getInt("timeService.daysBackInTime", daysBackInTime);
		timeServiceSendingTimeGap = props.getInt("timeService.sendingTimeGap", timeServiceSendingTimeGap);
		
		startEchoService = props.getBoolean("startEchoService", startEchoService);
		startTimeService = props.getBoolean("startTimeService", startTimeService);
		startChsAndAuth = props.getBoolean("startChsAndAuthServices", startChsAndAuth);
		startRipService = props.getBoolean("startRipService", startRipService);
		startBootService = props.getBoolean("startBootService", startBootService);
		
		strongKeysAsSpecified = props.getBoolean("strongKeysAsSpecified", strongKeysAsSpecified);
		authSkipTimestampChecks = props.getBoolean("authSkipTimestampChecks", authSkipTimestampChecks);
		organizationName = props.getString("organizationName", organizationName);
		domainName = props.getString("domainName", domainName);
		chsDatabaseRoot = props.getString("chsDatabaseRoot", chsDatabaseRoot);
		
		printServiceName = props.getString("printService.name", printServiceName);
		printServiceOutputDirectory = props.getString("printService.outputDirectory", printServiceOutputDirectory);
		printServicePaperSizes = props.getString("printService.paperSizes", printServicePaperSizes);
		printServiceDisassembleIp = props.getBoolean("printService.disassembleIp", printServiceDisassembleIp);
		printServiceIp2PsProcFilename = props.getString("printService.ip2PsProcFilename", printServiceIp2PsProcFilename);
		printServicePsPostprocessor = props.getString("printService.psPostprocessor", printServicePsPostprocessor);
		
		bootServiceBasedir = props.getString("bootService.basedir", bootServiceBasedir);
		bootServiceVerbose = props.getBoolean("bootService.verbose", bootServiceVerbose);
		bootServiceSimpleDataSendInterval = props.getInt("bootService.simpleDataSendInterval", bootServiceSimpleDataSendInterval);
		
		int fileSvcIdx = 0;
		String serviceName;
		String serviceVolumePath;
		while ((serviceName = props.getString("fileService." + fileSvcIdx + ".name", null)) != null
			&& (serviceVolumePath = props.getString("fileService." + fileSvcIdx + ".volumePath", null)) != null) {
			String[] nameParts = serviceName.split(":");
			String serviceThreepartName = nameParts[0] + ":" + domainName + ":" + organizationName;
			fileServiceSpecs.put(serviceThreepartName, serviceVolumePath);
			fileSvcIdx++;
		}
		
		sppHandshakeCheckInterval = props.getInt("spp.handshakeCheckInterval", sppHandshakeCheckInterval);
		sppHandshakeSendackCountdown = props.getInt("spp.handshakeSendackCountdown", sppHandshakeSendackCountdown);
		sppHandshakeResendCountdown = props.getInt("spp.handshakeResendCountdown", sppHandshakeResendCountdown);
		sppHandshakeMaxResends = props.getInt("spp.handshakeMaxResends", sppHandshakeMaxResends);
		sppResendDelay = props.getInt("spp.resendDelay", sppResendDelay);
		sppSendingTimeGap = props.getInt("spp.sendingTimeGap", sppSendingTimeGap);
		sppResendPacketCount = props.getInt("spp.resendPacketCount", sppResendPacketCount);
		
		// do verifications
		boolean outcome = true;
		
		if (mId != null) {
			String[] submacs = mId.split("-");
			if (submacs.length != 6) {
				System.err.printf("Error: invalid processor id format (not XX-XX-XX-XX-XX-XX): %s\n", mId);
				outcome = false;
			} else {
				long macId = 0;
				for (int i = 0; i < submacs.length; i++) {
					macId = macId << 8;
					try {
						macId |= Integer.parseInt(submacs[i], 16) & 0xFF;
					} catch (Exception e) {
						System.err.printf("Error: invalid processor id format (not XX-XX-XX-XX-XX-XX): %s\n", mId); 
						outcome = false;
					}
				}
				machineId = macId;
			}
		}
		
		if (startChsAndAuth || fileServiceSpecs.size() > 0) {
			if (isEmpty(organizationName)) {
				System.err.printf("Error: organizationName may not be empty\n");
				outcome = false;
			}
			if (isEmpty(domainName)) {
				System.err.printf("Error: domainName may not be empty\n");
				outcome = false;
			}
		}
		
		return outcome;
	}
	
	private static boolean isEmpty(String s) {
		return (s == null || s.isEmpty());
	}

	public static void main(String[] args) throws XnsException {
		String baseCfgFile = null;
		String cfgFile = null;
		boolean dumpChs = false;

		// get commandline args
		for (String arg : args) {
			if (arg.toLowerCase().startsWith("-basecfg:")) {
				String[] parts = arg.split(":");
				baseCfgFile = parts[1];
			} else if ("-dumpchs".equalsIgnoreCase(arg)) {
				dumpChs = true;
			} else if (cfgFile == null) {
				cfgFile = arg;
			} else {
				System.out.printf("Warning: ignoring unknown argument: %s\n", arg);
			}
		}
		
		// load configuration
		if (baseCfgFile == null) {
			File f = new File(DEFAULT_BASECONFIG_FILE);
			if (f.exists() && f.canRead()) {
				baseCfgFile = DEFAULT_BASECONFIG_FILE;
				System.out.printf("Found and using default commons configuration: %s\n", baseCfgFile);
			}
		}
		if (cfgFile == null && (new File(DEFAULT_CONFIG_FILE)).canRead()) {
			cfgFile = DEFAULT_CONFIG_FILE;
		}
		if (baseCfgFile != null) {
			initializeConfiguration(baseCfgFile);
		}
		if (cfgFile != null && !initializeConfiguration(cfgFile)) {
			return;
		}
		
		// configure SPP resend / acknowledge technical parameters, if any
		if (sppHandshakeCheckInterval > 0) { SppConnection.setHandshakeCheckInterval(sppHandshakeCheckInterval); }
		if (sppHandshakeSendackCountdown > 0) { SppConnection.setHandshakeSendackCountdown(sppHandshakeSendackCountdown); };
		if (sppHandshakeResendCountdown > 0) { SppConnection.setHandshakeResendCountdown(sppHandshakeResendCountdown); }
		if (sppHandshakeMaxResends > 0) { SppConnection.setHandshakeMaxResends(sppHandshakeMaxResends); }
		if (sppResendDelay >= 0) { SppConnection.setResendDelay(sppResendDelay); }
		if (sppResendPacketCount >= 0) { SppConnection.setResendPacketCount(sppResendPacketCount); }
		if (sppSendingTimeGap >= 0) { SppConnection.setSendingTimeGap(sppSendingTimeGap); }
		
		// configure and start the network engine
		LocalSite.configureHub(netHubHost, netHubPort);
		LocalSite.configureLocal(networkNo, machineId, "DodoServer", doChecksums, doDarkstarWorkaround);
		localSite = LocalSite.getInstance();
		
		// set time base for all time dependent items
		Time2.setTimeWarp(daysBackInTime);
		
		// boot service
		if (startBootService) {
			if (bootServiceSimpleDataSendInterval > 0) {
				BootResponder.setSimpleDataSendInterval(bootServiceSimpleDataSendInterval);
			}
			BootResponder bootService = new BootResponder(bootServiceBasedir, bootServiceVerbose);
			localSite.clientBindToSocket(
					IDP.KnownSocket.BOOT.getSocket(),
					bootService);
		}
		
		// echo service
		if (startEchoService) {
			localSite.clientBindToSocket(
					IDP.KnownSocket.ECHO.getSocket(), 
					new EchoResponder());
		}
		
		// time service
		if (startTimeService) {
			localSite.pexListen(
					IDP.KnownSocket.TIME.getSocket(), 
					new TimeServiceResponder(localTimeOffsetMinutes, timeServiceSendingTimeGap));
		}
		
		// routing protocol responder
		RipResponder ripResponder = null;
		if (startRipService) {
			ripResponder = new RipResponder();
			localSite.clientBindToSocket(IDP.KnownSocket.ROUTING.getSocket(), ripResponder);
		}
		
		// open CHS database if there are services requiring it
		ChsDatabase chsDatabase = null;
		if (startChsAndAuth || !fileServiceSpecs.isEmpty()) {
			// create the clearinghouse database
			chsDatabase = new ChsDatabase(localSite.getNetworkId(), organizationName, domainName, chsDatabaseRoot, strongKeysAsSpecified);
			
			if (dumpChs) {
				chsDatabase.dump();
			}
		}
		if (authSkipTimestampChecks) {
			AuthChsCommon.skipTimestampChecks();
		}
		
		// clearinghouse and authentication services
		if (startChsAndAuth) {
			
			// broadcast for clearinghouse service
			// one common implementation for versions 2 and 3, as both versions have the same RetrieveAddresses method
			Clearinghouse3Impl.init(localSite.getNetworkId(), localSite.getMachineId(), chsDatabase);
			localSite.pexListen(
					IDP.KnownSocket.CLEARINGHOUSE.getSocket(), 
					new BfsClearinghouseResponder(ripResponder),
					null);
			
			// broadcast for authentication service
			// one common implementation for versions 1, 2 and 3, as all versions are assumed to have
			// the same (undocumented) RetrieveAddresses method
			Authentication2Impl.init(localSite.getNetworkId(), localSite.getMachineId(), chsDatabase);
			localSite.pexListen(
					IDP.KnownSocket.AUTH.getSocket(), 
					new BfsAuthenticationResponder());
			
			// register clearinghouse and authentication courier programs in registry
			Clearinghouse3Impl.register();
			Authentication2Impl.register();
		}
		
		// print service
		if (printServiceName != null && printServiceOutputDirectory != null) {
			try {
				Printing3Impl.init(
						printServiceName,
						printServiceOutputDirectory,
						printServiceDisassembleIp,
						printServicePaperSizes,
						printServiceIp2PsProcFilename,
						printServicePsPostprocessor);
				Printing3Impl.register();
			} catch(Exception e) {
				System.out.printf("Error starting printservice '%s': %s\n", printServiceName, e.getMessage());
			}
		}
		
		// file service(s)
		if (fileServiceSpecs.size() > 0) {
			// initialize
			FilingImpl.init(localSite.getNetworkId(), localSite.getMachineId(), chsDatabase);
			
			// open volume(s)
			int openVolumes = 0;
			for (Entry<String, String> spec : fileServiceSpecs.entrySet()) {
				ThreePartName serviceName = ThreePartName.make().from(spec.getKey());
				String volumeBasedirName = spec.getValue();
				if (FilingImpl.addVolume(serviceName, volumeBasedirName)) {
					openVolumes++;
				}
			}
			
			// register Courier implementation if volume(s) were successfully opened
			if (openVolumes > 0) {
				FilingImpl.register();
			} else {
				System.out.printf("No volumes opened successfully, not registering Filing to Courier");
			}
		}
		
		// run courier server with dispatcher
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
