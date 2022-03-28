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
import java.util.List;
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
import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;
import dev.hawala.xns.level4.common.ChsDatabase;
import dev.hawala.xns.level4.common.Time2;
import dev.hawala.xns.level4.echo.EchoResponder;
import dev.hawala.xns.level4.filing.FilingImpl;
import dev.hawala.xns.level4.mailing.MailingExpeditedCourierResponder;
import dev.hawala.xns.level4.mailing.MailingOldImpl;
import dev.hawala.xns.level4.mailing.MailingNewImpl;
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
 * @author Dr. Hans-Walter Latz / Berlin (2018,2019,2020)
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
	private static int bootServiceSimpleDataSendInterval = 40; // default: ~ 25 packets per second
	private static int bootServiceSppDataSendInterval = 20; // default: ~ 50 packets per second
	
	private static String mailServiceVolumePath = null;
	
	private static int sppHandshakeCheckInterval = 10;
	private static int sppHandshakeSendackCountdown = 4;
	private static int sppHandshakeResendCountdown = 50;
	private static int sppHandshakeMaxResends = 5;
	private static int sppResendDelay = 20;
	private static int sppSendingTimeGap = 5;
	private static int sppResendPacketCount = 2;
	
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
		authSkipTimestampChecks = props.getBoolean(MachineIds.CFG_AUTH_SKIP_TIMESTAMP_CHECKS, authSkipTimestampChecks);
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
		bootServiceSimpleDataSendInterval = props.getInt(MachineIds.CFG_BOOTSVC_SIMPLEDATA_SEND_INTERVAL, bootServiceSimpleDataSendInterval);
		bootServiceSppDataSendInterval = props.getInt(MachineIds.CFG_BOOTSVC_SPPDATA_SEND_INTERVAL, bootServiceSppDataSendInterval);
		
		mailServiceVolumePath = props.getString("mailService.volumePath", mailServiceVolumePath);
		
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
		sppHandshakeSendackCountdown = props.getInt(MachineIds.CFG_SPP_HANDSHAKE_SENDACK_COUNTDOWN, sppHandshakeSendackCountdown);
		sppHandshakeResendCountdown = props.getInt(MachineIds.CFG_SPP_HANDSHAKE_RESEND_COUNTDOWN, sppHandshakeResendCountdown);
		sppHandshakeMaxResends = props.getInt(MachineIds.CFG_SPP_HANDSHAKE_MAX_RESENDS, sppHandshakeMaxResends);
		sppResendDelay = props.getInt(MachineIds.CFG_SPP_RESEND_DELAY, sppResendDelay);
		sppSendingTimeGap = props.getInt(MachineIds.CFG_SPP_SENDING_TIME_GAP, sppSendingTimeGap);
		sppResendPacketCount = props.getInt(MachineIds.CFG_SPP_RESEND_PACKET_COUNT, sppResendPacketCount);
		
		// do verifications
		boolean outcome = true;
		
		if (mId != null) {
			machineId = MachineIds.resolve(mId);
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
		String machinesFile = null;
		String baseCfgFile = null;
		String cfgFile = null;
		boolean dumpChs = false;

		// get commandline args
		for (String arg : args) {
			if (arg.toLowerCase().startsWith("-machinecfg:")) {
				String[] parts = arg.split(":");
				machinesFile = parts[1];
			} else if (arg.toLowerCase().startsWith("-basecfg:")) {
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
		
		// load machines configuration
		if (!MachineIds.loadDefinitions(machinesFile)) {
			System.out.println("!! failed to load machines configuration");
		}
		
		// load configuration(s)
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
		
		// put parts of the configuration data as defaults for client machines
		MachineIds.setDefault(MachineIds.CFG_AUTH_SKIP_TIMESTAMP_CHECKS, authSkipTimestampChecks);
		
		MachineIds.setDefault(MachineIds.CFG_BOOTSVC_SIMPLEDATA_SEND_INTERVAL, bootServiceSimpleDataSendInterval);
		MachineIds.setDefault(MachineIds.CFG_BOOTSVC_SPPDATA_SEND_INTERVAL, bootServiceSppDataSendInterval);
		
		MachineIds.setDefault(MachineIds.CFG_SPP_HANDSHAKE_SENDACK_COUNTDOWN, sppHandshakeSendackCountdown);
		MachineIds.setDefault(MachineIds.CFG_SPP_RESEND_DELAY, sppResendDelay);
		MachineIds.setDefault(MachineIds.CFG_SPP_HANDSHAKE_RESEND_COUNTDOWN, sppHandshakeResendCountdown);
		MachineIds.setDefault(MachineIds.CFG_SPP_HANDSHAKE_MAX_RESENDS, sppHandshakeMaxResends);
		MachineIds.setDefault(MachineIds.CFG_SPP_RESEND_PACKET_COUNT, sppResendPacketCount);
		MachineIds.setDefault(MachineIds.CFG_SPP_SENDING_TIME_GAP, sppSendingTimeGap);
		
		// this parameter is global to all SPP connections (cannot be specified at client machine level)
		SppConnection.setHandshakeCheckInterval(sppHandshakeCheckInterval);
		
		// open CHS database if there are services requiring it
		ChsDatabase chsDatabase = null;
		if (startChsAndAuth || !fileServiceSpecs.isEmpty()) {
			// create the clearinghouse database
			chsDatabase = new ChsDatabase(networkNo, organizationName, domainName, chsDatabaseRoot, strongKeysAsSpecified);
			
			if (dumpChs) {
				System.out.println("\n==\n== machine-id pre-definitions:\n==\n");
				MachineIds.dump();
				System.out.println("\n==\n== clearinghouse database dump: \n==\n");
				chsDatabase.dump();
				System.out.println("\n==\n== end of machine-id and clearinghouse database dumps\n==\n");
			}
		}
		
		// check if we have undefined machine names, aborting startup if any
		List<String> undefinedMachineNames = MachineIds.getUndefinedMachineNames();
		if (!undefinedMachineNames.isEmpty()) {
			System.out.println("The following machine names or IDs are undefined or invalid:");
			for (String name : undefinedMachineNames) {
				System.out.printf(" -> name: %s\n", name);
			}
			System.out.printf("Aborting Dodo startup ...\n... please correct the configuration or define the above names in the machine ids file (%s)\n", machinesFile);
			System.exit(2);
		}
		
		// configure and start the network engine
		LocalSite.configureHub(netHubHost, netHubPort);
		LocalSite.configureLocal(networkNo, machineId, "DodoServer", doChecksums, doDarkstarWorkaround);
		localSite = LocalSite.getInstance();
		
		// set time base for all time dependent items
		Time2.setTimeWarp(daysBackInTime);
		
		// boot service
		if (startBootService) {
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
			
			// start the mailService always co-located with the clearinghouse
			if (mailServiceVolumePath != null) {
				MailingOldImpl.init(localSite.getNetworkId(), localSite.getMachineId(), chsDatabase, mailServiceVolumePath);
				MailingOldImpl.register();
				MailingNewImpl.init(localSite.getNetworkId(), localSite.getMachineId(), chsDatabase, MailingOldImpl.getMailService());
				MailingNewImpl.register();
				localSite.pexListen(0x001A, new MailingExpeditedCourierResponder());
			}
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
