/*
Copyright (c) 2019, Dr. Hans-Walter Latz
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
package dev.hawala.xns.examples;

import java.util.ArrayList;
import java.util.List;

import dev.hawala.xns.LocalSite;
import dev.hawala.xns.Log;
import dev.hawala.xns.XnsException;
import dev.hawala.xns.iNetMachine;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level4.common.AuthChsCommon.NetworkAddress;
import dev.hawala.xns.level4.rip.RipResponder;
import dev.hawala.xns.level4.time.TimeServiceResponder;
import dev.hawala.xns.level4.auth.Authentication2Impl;
import dev.hawala.xns.level4.auth.BfsAuthenticationResponder;
import dev.hawala.xns.level4.chs.BfsClearinghouseResponder;
import dev.hawala.xns.level4.chs.Clearinghouse3Impl;
import dev.hawala.xns.level4.common.Time2;

/**
 * Example program for using the XNS-API in Dodo.
 * <p>
 * This program is a configurable responder for the services:
 * </p>
 * <ul>
 * <li>Time protocol</li>
 * <li>Routing Information protocol</li>
 * <li>Broadcast for Services (BfS) protocol for Clearinghouse and Authentication servers</li>
 * </ul>
 * 
 * @author Dr. Hans-Walter Latz / Berlin 2019
 */
public class XnsResponder {
	
	private static long getLong(String value, String arg, String valueKind) {
		if (value == null || value.isEmpty()) {
			System.err.printf("** missing %s value in argument '%s'\n", valueKind, arg);
			usage();
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException nfe) {
			System.err.printf("** invalid %s '%s' in argument '%s'\n", valueKind, value, arg);
			usage();
		}
		return -1;
	}
	
	private static long getHost(String value, String arg) {
		if (value == null || value.isEmpty()) {
			System.err.printf("** missing host value in argument '%s'\n", arg);
			usage();
		}
		long macId = 0;
		String[] submacs = value.split("-");
		if (submacs.length != 6) {
			System.err.printf("Error: invalid processor id '%s' in argument '%s' (not XX-XX-XX-XX-XX-XX)\n", value, arg);
			usage();
		} else {
			for (int i = 0; i < submacs.length; i++) {
				macId = macId << 8;
				try {
					macId |= Integer.parseInt(submacs[i], 16) & 0xFF;
				} catch (Exception e) {
					System.err.printf("Error: invalid processor id '%s' in argument '%s' (not XX-XX-XX-XX-XX-XX)\n", value, arg); 
					usage();
				}
			}
		}
		return macId;
	}
	
	private static String getValue(String arg) {
		String[] values = arg.split(":");
		if (values.length > 1) {
			return values[1];
		}
		return null;
	}
	
	private static void usage() {
		usage(false);
	}
	
	private static void usage(boolean all) {
		System.err.println("args: [help] [+v] [hubhost:<name>] [hubport:<port>] net:<netno> host:<xx-xx-xx-xx-xx-xx>");
		System.err.println("      [-time|+time[:<gmt-offset-minutes>]] [daysbackintime:<days>]");
		System.err.println("      [-rip|+rip]");
		System.err.println("      [+bfs:nnn/xx-xx-xx-xx-xx-xx [+bfs:...]]");
		if (all) {
			System.err.println("with:");
			System.err.println("  help : print all help info");
			System.err.println("  +v : dump requested configuration to stdout");
			System.err.println("  hubhost:<name> : connect to NetHub on host <name>");
			System.err.println("  hubport:<port> : connect to NetHub at port <port>");
			System.err.println("  net:<netno> : the local network has the network number <netno> (required)");
			System.err.println("  host:<xx-xx-xx-xx-xx-xx> : this XnsResponder machine has the given host-id (machine-id) (required)");
			System.err.println("  -time : disable the time server");
			System.err.println("  +time[:<gmt-offset-minutes> : enable the time server with the time zone at the given offset");
			System.err.println("  daysbackintime:<days> : shift the local time by the given days into past");
			System.err.println("  -rip : disable the Routing Information server");
			System.err.println("  +rip : enable the Routine Information server");
			System.err.println("  +bfs:nnn/xx-xx-xx-xx-xx-xx : add a machine at the given network and host ids to BfS responses");
			System.err.println("defaults:");
			System.err.println("  hubhost:localhost");
			System.err.println("  hubport:3333");
			System.err.println("  +time:0");
			System.err.println("  daysbackintime:");
			System.err.println("  +rip");
			System.err.println("  (no Bfs, i.e. at least one must be added to produce BfS responses");
		}
		System.exit(1);
	}

	public static void main(String[] args) throws XnsException {
		// flags/data interpreted from command line args
		boolean verbose = false;
		String hubHost = "localhost";
		int hubPort = 3333;
		long localNetwork = -1;
		long localMachine = -1;
		boolean doTimeSvc = true;
		int localTimeOffsetMinutes = 0;
		int daysBackInTime = 0;
		boolean doRipSvc = true;
		List<NetworkAddress> bfsHostIds = new ArrayList<>();
		
		// interpret command line args
		for (String arg : args) {
			String a = arg.toLowerCase();
			String value = getValue(a);
			if ("help".equals(a)) {
				usage(true);
			} else if ("+v".equals(a)) {
				verbose = true;
			} else if (a.startsWith("hubHost:")) {
				hubHost = value;
			} else if (a.startsWith("hubPort:")) {
				hubPort = (int)getLong(value, arg, "port");
			} else if (a.startsWith("net:")) {
				localNetwork = getLong(value, arg, "network");
			} else if (a.startsWith("host:")) {
				localMachine = getHost(value, arg);
			} else if ("-time".equals(a)) {
				doTimeSvc = false;
			} else if ("+time".equals(a)) {
				doTimeSvc = true;
				daysBackInTime = 0;
			} else if (a.startsWith("+time:")) {
				doTimeSvc = true;
				localTimeOffsetMinutes = (int)getLong(value, arg, "integer");
			} else if (a.startsWith("daysbackintime")) {
				daysBackInTime  = (int)getLong(value, arg, "integer");
			} else if ("-rip".equals(a)) {
				doRipSvc = false;
			} else if ("+rip".equals(a)) {
				doRipSvc = true;
			} else if (a.startsWith("+bfs:")) {
				if (value == null || value.isEmpty()) {
					System.err.printf("** missing value in argument '%s'\n", arg);
					usage();
				}
				String[] parts = value.split("/");
				if (parts.length != 2) {
					System.err.printf("** invalid net/host value '%s' in argument '%s'\n", value, arg);
					usage();
				}
				long bfsNet = getLong(parts[0], arg, "network");
				long bfsHost = getHost(parts[1], arg);
				NetworkAddress addr = NetworkAddress.make();
				addr.network.set((int)bfsNet);
				addr.host.set(bfsHost);
				addr.socket.set(0);
				bfsHostIds.add(addr);
			} else {
				System.err.printf("** invalid argument '%s'\n", arg);
				usage();
			}
		}
		
		// check configured data / requested features
		if (localNetwork < 0 || localMachine < 0) {
			// no local machine configuration
			System.err.println("** no or invalid local machine configuration given");
			usage();
		}
		if (!doTimeSvc && !doRipSvc && bfsHostIds.isEmpty()) {
			// no responder feature to run
			System.err.println("** no responder feature to activate");
			usage();
		}
		
		// dump values if requested
		if (verbose) {
			System.out.printf("NetHub ............. : %s:%d\n", hubHost, hubPort);
			System.out.printf("Responder machine .. : net = %d / host-id = 0x%012X\n", localNetwork, localMachine);
			System.out.printf("Time service ....... : %s , GMT offset = %d minutes%s\n",
					(doTimeSvc) ? "yes" : "no",
					localTimeOffsetMinutes,
					(doTimeSvc && daysBackInTime != 0) ? " ( daysBackInTime = " + daysBackInTime + " )" : "");
			System.out.printf("Rip service ........ : %s\n", (doRipSvc) ? "yes" : "no");
			System.out.printf("Bfs responder ...... : %s\n", (!bfsHostIds.isEmpty()) ? "yes" : "no");
			if (!bfsHostIds.isEmpty()) {
				for (NetworkAddress addr : bfsHostIds) {
					System.out.printf("  -> bfs for: net = %d / host-id = 0x%012X\n", addr.network.get(), addr.host.get());
				}
			}
			System.out.println();
		}

		// silence Dodo's logging a bit
		Log.L0.doLog(false);
		Log.L1.doLog(false);
		Log.L2.doLog(false);
		Log.L3.doLog(false);
		Log.L4.doLog(false);
		
		// configure and start the network engine
		LocalSite.configureHub(hubHost, hubPort);
		LocalSite.configureLocal(localNetwork, localMachine, "XnsResponder", true, false);
		iNetMachine localSite = LocalSite.getInstance();
		
		// set time base for all time dependent items
		Time2.setTimeWarp(daysBackInTime);
		
		// time service responder
		if (doTimeSvc) {
			localSite.pexListen(
					IDP.KnownSocket.TIME.getSocket(), 
					new TimeServiceResponder(localTimeOffsetMinutes, 0, 0, 0));
		}
		
		// routing protocol responder
		RipResponder ripResponder = null;
		if (doRipSvc) {
			ripResponder = new RipResponder();
			localSite.clientBindToSocket(IDP.KnownSocket.ROUTING.getSocket(), ripResponder);
		}
		
		// BfS responder for Clearinghouse and Authentication
		if (!bfsHostIds.isEmpty()) {
			// broadcast for clearinghouse service
			// one common implementation for versions 2 and 3, as both versions have the same RetrieveAddresses method
			Clearinghouse3Impl.init(localSite.getNetworkId(), localSite.getMachineId(), null, bfsHostIds);
			localSite.pexListen(
					IDP.KnownSocket.CLEARINGHOUSE.getSocket(), 
					new BfsClearinghouseResponder(ripResponder),
					null);
			
			// broadcast for authentication service
			// one common implementation for versions 1, 2 and 3, as all versions are assumed to have
			// the same (undocumented) RetrieveAddresses method
			Authentication2Impl.init(localSite.getNetworkId(), localSite.getMachineId(), null, bfsHostIds);
			localSite.pexListen(
					IDP.KnownSocket.AUTH.getSocket(), 
					new BfsAuthenticationResponder());
		}
	}
	
}
