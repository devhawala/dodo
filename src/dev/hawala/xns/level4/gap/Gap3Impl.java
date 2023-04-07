/*
Copyright (c) 2023, Dr. Hans-Walter Latz
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

package dev.hawala.xns.level4.gap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.hawala.xns.level3.courier.CourierRegistry;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level4.chs.CHEntries0;
import dev.hawala.xns.level4.common.ChsDatabase;
import dev.hawala.xns.level4.common.iUpdatableChsEntry;
import dev.hawala.xns.level4.gap.Gap3.CharLength;
import dev.hawala.xns.level4.gap.Gap3.ControllerLinkType;
import dev.hawala.xns.level4.gap.Gap3.CreateParams;
import dev.hawala.xns.level4.gap.Gap3.CreateResults;
import dev.hawala.xns.level4.gap.Gap3.DeleteParams;
import dev.hawala.xns.level4.gap.Gap3.Duplexity;
import dev.hawala.xns.level4.gap.Gap3.FlowControlType;
import dev.hawala.xns.level4.gap.Gap3.IBM3270Controller;
import dev.hawala.xns.level4.gap.Gap3.IBM3270Device;
import dev.hawala.xns.level4.gap.Gap3.IBM3270HostData;
import dev.hawala.xns.level4.gap.Gap3.IBM3270Language;
import dev.hawala.xns.level4.gap.Gap3.IBMDeviceType;
import dev.hawala.xns.level4.gap.Gap3.LineSpeed;
import dev.hawala.xns.level4.gap.Gap3.MediumConnectFailedErrorRecord;
import dev.hawala.xns.level4.gap.Gap3.Parity;
import dev.hawala.xns.level4.gap.Gap3.PortClientType;
import dev.hawala.xns.level4.gap.Gap3.PortDialerType;
import dev.hawala.xns.level4.gap.Gap3.PortEchoingLocation;
import dev.hawala.xns.level4.gap.Gap3.RS232CData;
import dev.hawala.xns.level4.gap.Gap3.Rs232cLineState;
import dev.hawala.xns.level4.gap.Gap3.Rs232cReserveNeeded;
import dev.hawala.xns.level4.gap.Gap3.SessionHandle;
import dev.hawala.xns.level4.gap.Gap3.SessionParameterObjectType;
import dev.hawala.xns.level4.gap.Gap3.StopBits;
import dev.hawala.xns.level4.gap.Gap3.TransportObjectIbmTerminalOrPrinter;
import dev.hawala.xns.level4.gap.Gap3.TransportObjectRs232c;
import dev.hawala.xns.level4.gap.Gap3.TransportObjectType;

/**
 * Implementation of the Gateway Access Protocol (GAP) service, Courier Program (PROGRAM 3 VERSION 3).
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2023)
 */
public class Gap3Impl {
	
	private static void logf(String format, Object... args) {
		System.out.printf(format, args);
	}
	
	/*
	 * ************************* initialization with completion of Clearinghouse entries with GAP3-specific Item-data
	 */
	
	private static long networkId = -1;
	private static long machineId = -1;
	
	private static Map<Integer,iGapConnectionCreator> ttyConnectors = null;
	private static Map<Integer,iGapConnectionCreator> ibm3270Connectors = null;
	
	public static void init(long network, long machine, ChsDatabase db) {
		networkId = network;
		machineId = machine;
		
		ttyConnectors = new HashMap<>();
		ibm3270Connectors = new HashMap<>();
		
		List<iUpdatableChsEntry> ttyEntries = db.listUpdatableEntries(CHEntries0.rs232CPort);
		for (iUpdatableChsEntry ttyEntry : ttyEntries) {
			String lineNumber = ttyEntry.getUninterpretedProperty("lineNumber");
			String ttyType = ttyEntry.getUninterpretedProperty("ttyType");
			String connectAddress = ttyEntry.getUninterpretedProperty("connectAddress");
			String connectPort = ttyEntry.getUninterpretedProperty("connectPort");
			String terminalType = ttyEntry.getUninterpretedProperty("terminalType");
			String mapCarriageReturn = ttyEntry.getUninterpretedProperty("mapCarriageReturn");
			String mapBreak = ttyEntry.getUninterpretedProperty("mapBreak");
			String mapBackspace = ttyEntry.getUninterpretedProperty("mapBackspace");
			String connectScript = ttyEntry.getUninterpretedProperty("connectScript");
			if (lineNumber != null && connectScript != null) {
				try {
					int lineNo = Integer.parseInt(lineNumber);
					
					RS232CData chsData = RS232CData.make();
					chsData.owningClientType.set(PortClientType.ttyEmulation);
					chsData.preemptionAllowed.set(true);
					chsData.lineNumber.set(lineNo);
					chsData.dialerNumber.set(42);
					chsData.duplexity.set(Duplexity.fullduplex);
					chsData.dialingHardware.set(PortDialerType.none);
					chsData.charLength.set(CharLength.eight);
					chsData.echoing.set(PortEchoingLocation.application);
					chsData.flowControl.type.set(FlowControlType.none);
					chsData.lineSpeed.set(LineSpeed.bps57600);
					chsData.parity.set(Parity.none);
					chsData.stopBits.set(StopBits.one);
					chsData.portActsAsDCE.set(false);
					chsData.accessControl.from("*", db.getDomainName(), db.getOrganizationName());
					chsData.validLineSpeeds.add().set(LineSpeed.bps57600);
					ttyEntry.setChsProperty(CHEntries0.rs232CData, chsData);
					
					if ("shell".equalsIgnoreCase(ttyType)) {
						ttyConnectors.put(lineNo,
							new TTYShellCommandConnectionCreator(
									connectScript, 
									new XeroxControlCharMapper(mapCarriageReturn, mapBreak, mapBackspace))
						);
					} else if ("telnet".equalsIgnoreCase(ttyType)) {
						ttyConnectors.put(lineNo,
							new TTYTelnetConnectionCreator(
									connectAddress,
									Integer.parseInt(connectPort),
									terminalType,
									new XeroxControlCharMapper(mapCarriageReturn, mapBreak, mapBackspace))
						);
					} else {
						ttyConnectors.put(lineNo,
							() -> new SimpleTTYReplier()
						);
					}
				} catch (Exception e) { // we should never get here at this time
					logf("\n**\n** ERROR: invalid configuration for '%s' in file '%s'\n**\n", ttyEntry.getFqn(), ttyEntry.getCfgFilename());
					continue;
				}
			}
		}
		
		List<iUpdatableChsEntry> ibmEntries = db.listUpdatableEntries(CHEntries0.ibm3270Host);
		for (iUpdatableChsEntry ibmEntry : ibmEntries) {
			String connectType = ibmEntry.getUninterpretedProperty("connectType");
			String controllerAddress = ibmEntry.getUninterpretedProperty("controllerAddress");
			String connectAddress = ibmEntry.getUninterpretedProperty("connectAddress");
			String connectPort = ibmEntry.getUninterpretedProperty("connectPort");
			String connectLUName = ibmEntry.getUninterpretedProperty("connectLUName");
			String language = ibmEntry.getUninterpretedProperty("language");
			
			try {
				int controller = Integer.parseInt(controllerAddress);
				
				IBM3270HostData chsData = IBM3270HostData.make();
				IBM3270Controller c = chsData.add();
				c.controllerAddress.set(controller);
				c.portsOnController.set(4);
				c.linkType.set(ControllerLinkType.bsc);
				c.language.set(mapLanguage(language));
				IBM3270Device d = c.devices.add();
				d.model.set(IBMDeviceType.model2);
				d.accessControl.from("*", db.getDomainName(), db.getOrganizationName());
				d = c.devices.add();
				d.model.set(IBMDeviceType.model3);
				d.accessControl.from("*", db.getDomainName(), db.getOrganizationName());
				d = c.devices.add();
				d.model.set(IBMDeviceType.model4);
				d.accessControl.from("*", db.getDomainName(), db.getOrganizationName());
				ibmEntry.setChsProperty(CHEntries0.ibm3270HostData, chsData);
				
				if ("host".equalsIgnoreCase(connectType)) {
					ibm3270Connectors.put(controller,
						new Ibm3270ConnectionCreator(connectAddress, Integer.parseInt(connectPort), connectLUName)
					);
				} else {
					ibm3270Connectors.put(controller,
						() -> new Simple3270Replier()
					);
				}
			} catch (Exception e) { // we should never get here at this time
				logf("\n**\n** ERROR: invalid configuration for '%s' in file '%s'\n**\n", ibmEntry.getFqn(), ibmEntry.getCfgFilename());
				continue;
			}
		}
	}
	
	private static IBM3270Language mapLanguage(String language) {
		if (language == null) {
			return IBM3270Language.USenglish;
		}
		switch(language.toLowerCase()) {
		case "austrian": return IBM3270Language.Austrian;
		case "austrianalt": return IBM3270Language.AustrianAlt;
		case "german": return IBM3270Language.German;
		case "germanalt": return IBM3270Language.GermanAlt;
		case "belgian": return IBM3270Language.Belgian;
		case "brazilian": return IBM3270Language.Brazilian;
		case "canadianfrench": return IBM3270Language.CanadianFrench;
		case "danish": return IBM3270Language.Danish;
		case "danishalt": return IBM3270Language.DanishAlt;
		case "norwegian": return IBM3270Language.Norwegian;
		case "norwegianalt": return IBM3270Language.NorwegianAlt;
		case "finnish": return IBM3270Language.Finnish;
		case "finnishalt": return IBM3270Language.FinnishAlt;
		case "swedish": return IBM3270Language.Swedish;
		case "swedishalt": return IBM3270Language.SwedishAlt;
		case "french": return IBM3270Language.French;
		case "international": return IBM3270Language.International;
		case "italian": return IBM3270Language.Italian;
		case "japaneseenglish": return IBM3270Language.JapaneseEnglish;
		case "japanesekana": return IBM3270Language.JapaneseKana;
		case "portuguese": return IBM3270Language.Portuguese;
		case "spanish": return IBM3270Language.Spanish;
		case "spanishalt": return IBM3270Language.SpanishAlt;
		case "spanishspeaking": return IBM3270Language.SpanishSpeaking;
		case "ukenglish": return IBM3270Language.UKenglish;
		
		default: return IBM3270Language.USenglish;
		}
	}
	
	/*
	 * ************************* sessions
	 */
	
	private static long lastSessionId = System.currentTimeMillis() & 0x000000000FFFFF00L;
	
	private static synchronized void makeSessionHandle(SessionHandle session) {
		long sessionId = ++lastSessionId;
		session.get(0).set((int)((sessionId >> 16) & 0xFFFF));
		session.get(1).set((int)(sessionId & 0xFFFF));
	}
	
	/*
	 * ************************* Courier registration/deregistration
	 */

	public static void register() {
		Gap3 gap3 = new Gap3().setLogParamsAndResults(false);
		
		gap3.Reset.use(Gap3Impl::reset);
		gap3.Create.use(Gap3Impl::create);
		gap3.Delete.use(Gap3Impl::delete);
		
		CourierRegistry.register(gap3);
	}
	
	public static void unregister() {
		CourierRegistry.unregister(Gap3.PROGRAM, Gap3.VERSION);
	}
	
	/*
	 * ************************* implementation of service procedures
	 */
	
	
	/*
	 * Reset: PROCEDURE = 0;
	 * 
	 * (claimed never to be called according to "Services 8.0 Programmers Guide Nov84")
	 */
	private static void reset(RECORD params, RECORD results) {
		logf("##\n## procedure Gap3Impl.reset ( ) -> void\n##\n");
	}
	
	/*
	 * Create: PROCEDURE [
	 *             sessionParameterHandle: SessionParameterObject,
	 *             transportList: SEQUENCE OF TransportObject,
	 *             createTimeout: WaitTime,
	 *             credentials: Authentication.Credentials,
	 *             verifier: Authentication.Verifier ]
	 *   RETURNS [ session: SessionHandle ]
	 *   REPORTS [ badAddressFormat, controllerAlreadyExists, controllerDoesNotExist, dialingHardwareProblem,
     *             illegalTransport, inconsistentParams, mediumConnectFailed, noCommunicationHardware,
     *             noDialingHardware, terminalAddressInUse, terminalAddressInvalid, tooManyGateStreams,
     *             transmissionMediumUnavailable, serviceTooBusy, userNotAuthenticated,
     *             userNotAuthorized serviceNotFound, registeredTwice,
     *             transmissionMediumHardwareProblem, transmissionMediumUnavailable,
     *             transmissionMediumNotReady, noAnswerOrBusy, noRouteToGAPService,
     *             gapServiceNotResponding, courierProtocolMismatch, gapVersionMismatch	]
	 *  = 2;
	 */
	private static void create(CreateParams params, CreateResults results) {
		StringBuilder sb = new StringBuilder();
		params.append(sb, "  ", "arguments");
		logf("##\n## procedure Gap3Impl.create (\n%s\n##  ) -> void\n##\n", sb.toString());
		
		iGapConnectionCreator connectionCreator = null;
		
		// does the client wants a Service console or a TTY line or a 3270 host line ?
		if (params.sessionParameterHandle.getChoice() == SessionParameterObjectType.tty
				|| params.sessionParameterHandle.getChoice() == SessionParameterObjectType.ttyHost
			|| params.sessionParameterHandle.getChoice() == SessionParameterObjectType.oldTtyHost) {
			
			// does the client connect to a remote executive, interactive terminal service etc. ?
			if (params.transportList.size() == 2
				&& params.transportList.get(0).getChoice() == TransportObjectType.service
				&& params.transportList.get(1).getChoice() == TransportObjectType.teletype
			) {
				// => check which one and connect to that
				makeSessionHandle(results.sessionHandle);
				results.setConnectionClient(new SimpleTTYReplier());
				return;
			}
			
			// does the client connect to a TTY line?
			if (params.transportList.size() == 2
					&& params.transportList.get(0).getChoice() == TransportObjectType.rs232c
					&& params.transportList.get(1).getChoice() == TransportObjectType.teletype
				) {
				// RS232C or the like connection to an external system through an TTY terminal
				if (ttyConnectors != null) {
					TransportObjectRs232c transport0 = (TransportObjectRs232c)params.transportList.get(0).getContent();
					if (transport0.line.getChoice() == Rs232cLineState.reserveNeeded) {
						Rs232cReserveNeeded reserveLine = (Rs232cReserveNeeded)transport0.line.getContent();
						int lineNo = reserveLine.lineNumber.get();
						connectionCreator = ttyConnectors.get(lineNo); 
					}
				} else {
					makeSessionHandle(results.sessionHandle);
					results.setConnectionClient(new SimpleTTYReplier());
					return;
				}
			}
			
		} else if (params.sessionParameterHandle.getChoice() == SessionParameterObjectType.ibm3270Host) {
			
			// does the client connect to a 3270 host line?
			if (params.transportList.size() == 1
					&& params.transportList.get(0).getChoice() == TransportObjectType.polledBSCTerminal) {
				// yes, it's a supported 3270 connection type...
				if (ibm3270Connectors != null) {
					TransportObjectIbmTerminalOrPrinter transport = (TransportObjectIbmTerminalOrPrinter)params.transportList.get(0).getContent();
					String controllerName = transport.hostControllerName.get();
					String[] parts = controllerName.split("#");
					if (parts.length == 2 && parts[1].endsWith("B")) {
						try {
							String octalControllerNo = parts[1].substring(0, parts[1].length() - 1);
							int controllerNo = Integer.parseInt(octalControllerNo, 8);
							connectionCreator = ibm3270Connectors.get(controllerNo);
						} catch(Exception e) {
							logf("** invalid polledBSCTerminal.hostControllerName '%s'\n", controllerName);
						}
					}
				} else {
					makeSessionHandle(results.sessionHandle);
					results.setConnectionClient(new Simple3270Replier());
					return;
				}
			}
			
		}
		
		if (connectionCreator != null) {
			GapConnectionHandler connection = connectionCreator.create();
			if (connection != null) {
				makeSessionHandle(results.sessionHandle);
				results.setConnectionClient(connection);
				return;
			}
		}
		
		new MediumConnectFailedErrorRecord().raise();
	}
	
	/*
	 * Delete: PROCEDURE [ session: SessionHandle ] = 3;
	 * 
	 * (claimed never to be called according to "Services 8.0 Programmers Guide Nov84")
	 */
	private static void delete(DeleteParams params, RECORD results) {
		StringBuilder sb = new StringBuilder();
		params.append(sb, "  ", "arguments");
		logf("##\n## procedure Gap3Impl.delete (\n%s\n##  ) -> void\n##\n", sb.toString());
	}
}
