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

import dev.hawala.xns.level3.courier.ARRAY;
import dev.hawala.xns.level3.courier.BOOLEAN;
import dev.hawala.xns.level3.courier.CARDINAL;
import dev.hawala.xns.level3.courier.CHOICE;
import dev.hawala.xns.level3.courier.CrProgram;
import dev.hawala.xns.level3.courier.ENUM;
import dev.hawala.xns.level3.courier.ErrorRECORD;
import dev.hawala.xns.level3.courier.LONG_CARDINAL;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.SEQUENCE;
import dev.hawala.xns.level3.courier.STRING;
import dev.hawala.xns.level3.courier.UNSPECIFIED;
import dev.hawala.xns.level4.common.AuthChsCommon.Credentials;
import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;
import dev.hawala.xns.level4.common.AuthChsCommon.Verifier;

/**
 * Definition of the Gateway Access Protocol (GAP) Courier Program (PROGRAM 3 VERSION 3)
 * allowing clients (Star, ViewPoint, GlobalView, XDE, InterLisp) to access foreign data
 * processing systems through <b>External Communication Services</b> (e.g. Unix or Host
 * machines) as well as XNS network internal resources (like the remote executive of an
 * XNS server).
 * 
 * <p>
 * This definition was transcribed from 2 sources, recognizable through the characteristic
 * syntax in the comments prefixing the Courier items:
 * <p>
 * <ul>
 * <li>
 *   {@code GAP3.cr}<br/>(see https://stuff.mit.edu/afs/athena/astaff/reference/4.3network/xns/examples/gap/GAP3.cr)
 *   <br/>(comments with definitions in Courier language syntax)
 * </li>
 * <li>
 *   {@code NSCHAT} (InterLisp source file)<br/>(see https://github.com/Interlisp/medley/blob/master/library/NSCHAT)
 *   <br/>(comments with definitions in Lisp syntax)
 * </li>
 * </ul>
 * <p>
 * If possible, definitions were taken from the incomplete {@code GAP3.cr}, the missing parts were
 * reconstructed from the InterLisp {@code NSCHAT} source.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2023)
 */
public class Gap3 extends CrProgram {

	public static final int PROGRAM = 3;
	public static final int VERSION = 3;

	@Override
	public int getProgramNumber() { return PROGRAM; }

	@Override
	public int getVersionNumber() { return VERSION; }
	
	/*
	 * ---------------------------------------------------------------------------- data types
	 */
	
	/*
	 * WaitTime: TYPE = CARDINAL;	-- in seconds --
	 */
	
	/*
	 * SessionHandle: TYPE = ARRAY 2 OF UNSPECIFIED;
	 */
	public static class SessionHandle extends ARRAY<UNSPECIFIED> {
		private SessionHandle() { super(2, UNSPECIFIED::make); }
		public static SessionHandle make() { return new SessionHandle(); }
	}
	
	/*
	 * CharLength: TYPE = {five(0), six(1), seven(2), eight(3)};
	 */
	public enum CharLength { five, six, seven, eight }
	public static final EnumMaker<CharLength> mkCharLength = buildEnum(CharLength.class).get();
	
	/*
	 * Parity: TYPE = {none(0), odd(1), even(2), one(3), zero(4)};
	 */
	public enum Parity { none, odd, even, one, zero }
	public static final EnumMaker<Parity> mkParity = buildEnum(Parity.class).get();
	
	/*
	 * StopBits: TYPE = {oneStopBit(0), twoStopBits(1)};
	 */
	public enum StopBits { one, two }
	public static final EnumMaker<StopBits> mkStopBits = buildEnum(StopBits.class).get();
	
	/*
	 * FlowControl: TYPE = RECORD [
	 *   type: {flowControlNone(0), xOnXOff(1)},
	 *   xOn: UNSPECIFIED,
	 *   xOFF: UNSPECIFIED ];
	 */
	public enum FlowControlType { none, xOnXOff }
	public static final EnumMaker<FlowControlType> mkFlowControlType = buildEnum(FlowControlType.class).get();
	public static class FlowControl extends RECORD {
		public final ENUM<FlowControlType> type = mkENUM(mkFlowControlType);
		public final UNSPECIFIED xOn = mkUNSPECIFIED();
		public final UNSPECIFIED xOff = mkUNSPECIFIED();
		private FlowControl() {}
		public static FlowControl make() { return new FlowControl(); }
	}
	
	/*
	 * BidReply: TYPE = {wack(0), nack(1), defaultBidReply(2)};
	 */
	public enum BidReply { wack, nack, defaultBidReply }
	public static final EnumMaker<BidReply> mkBidReply = buildEnum(BidReply.class).get();
	
	/*
	 * ExtendedBoolean: TYPE = {true(0), false(1), defaultExtendedBoolean(2)};
	 */
	public enum ExtendedBoolean { extTrue, extFalse, defaultExtendedBoolean } // true/false are reserved words
	public static final EnumMaker<ExtendedBoolean> mkExtendedBoolean = buildEnum(ExtendedBoolean.class).get();
	
	/*
	 * DeviceType: TYPE = {undefined(0), terminal(1), printer(2)};
	 */
	public enum DeviceType { undefined, terminal, printer }
	public static final EnumMaker<DeviceType> mkDeviceType = buildEnum(DeviceType.class).get();
	
	/*
	 * -- the following is sometimes called a SessionParamObject --
	 * SessionParameterObject: TYPE = CHOICE OF {
	 *   xerox800(0) => RECORD [],
	 *   xerox850(1), xerox860(2) => RECORD [pollProc: UNSPECIFIED],
	 *   system6(3), cmcll(4), imb2770(5), ibm2770Host(6),
	 *   ibm6670(7), ibm6670Host(8) => RECORD [
	 *   	sendBlocksize, receiveBlocksize: CARDINAL ],
	 *   ibm3270(9), ibm3270Host(10) => RECORD [],
	 *   oldTtyHost(11), oldTty(12) => RECORD [
	 *   	charLength: CharLength,
	 *   	parity: Parity,
	 *   	stopBits: StopBits,
	 *   	frameTimeout: CARDINAL ],	-- in millisec --
	 *   otherSessionType(13) => RECORD [],
	 *   unknown(14) => RECORD [],
	 *   ibm2780(15), ibm2780Host(16), 
	 *   ibm3780(17), ibm3780Host(18) => RECORD [
	 *   	sendBlocksize, receiveBlocksize: CARDINAL ],
	 *   siemens9750(19), siemens9750Host(20) => RECORD [],
	 *   ttyHost(21), tty(22) => RECORD [
	 *   	charLength: CharLength,
	 *   	parity: Parity,
	 *   	stopBits: StopBits,
	 *   	frameTimeout: CARDINAL,		-- in millisec --
	 *   	flowControl: FlowControl ] };
	 */
	public enum SessionParameterObjectType {
		xerox800,
		xerox850, xerox860,
		system6, cmcll, ibm2770, ibm2770Host, ibm6670, ibm6670Host,
		ibm3270, ibm3270Host,
		oldTtyHost, oldTty,
		otherSessionType,
		unknown,
		ibm2780, ibm2780Host, ibm3780, ibm3780Host,
		siemens9750, siemens9750Host,
		ttyHost, tty
	}
	public static final EnumMaker<SessionParameterObjectType> mkSessionParameterObjectType = buildEnum(SessionParameterObjectType.class).get();
	public static class SessionParameterObjectPollproc extends RECORD {
		public final UNSPECIFIED pollProc = mkUNSPECIFIED();
		private SessionParameterObjectPollproc() {}
		public static SessionParameterObjectPollproc make() { return new SessionParameterObjectPollproc(); }
	}
	public static class SessionParameterObjectBlockSizes extends RECORD {
		public final CARDINAL sendBlocksize = mkCARDINAL();
		public final CARDINAL receiveBlocksize = mkCARDINAL();
		private SessionParameterObjectBlockSizes() {}
		public static SessionParameterObjectBlockSizes make() { return new SessionParameterObjectBlockSizes(); }
	}
	public static class SessionParameterObjectOldTty extends RECORD {
		public final CARDINAL charLength = mkCARDINAL();
		public final ENUM<Parity> parity = mkENUM(mkParity);
		public final ENUM<StopBits> stopBits = mkENUM(mkStopBits);		
		public final CARDINAL frameTimeoutMsecs = mkCARDINAL();
		private SessionParameterObjectOldTty() {}
		public static SessionParameterObjectOldTty make() { return new SessionParameterObjectOldTty(); }
	}
	public static class SessionParameterObjectTty extends RECORD {
		public final CARDINAL charLength = mkCARDINAL();
		public final ENUM<Parity> parity = mkENUM(mkParity);
		public final ENUM<StopBits> stopBits = mkENUM(mkStopBits);		
		public final CARDINAL frameTimeoutMsecs = mkCARDINAL();
		public final FlowControl flowControl = mkRECORD(FlowControl::make);
		private SessionParameterObjectTty() {}
		public static SessionParameterObjectTty make() { return new SessionParameterObjectTty(); }
	}
	public static final ChoiceMaker<SessionParameterObjectType> mkSessionParameterObject = buildChoice(mkSessionParameterObjectType)
			.choice(SessionParameterObjectType.xerox800, RECORD::empty)
			
			.choice(SessionParameterObjectType.xerox850, SessionParameterObjectPollproc::make)
			.choice(SessionParameterObjectType.xerox860, SessionParameterObjectPollproc::make)

			.choice(SessionParameterObjectType.system6, SessionParameterObjectBlockSizes::make)
			.choice(SessionParameterObjectType.cmcll, SessionParameterObjectBlockSizes::make)
			.choice(SessionParameterObjectType.ibm2770, SessionParameterObjectBlockSizes::make)
			.choice(SessionParameterObjectType.ibm2770Host, SessionParameterObjectBlockSizes::make)
			.choice(SessionParameterObjectType.ibm6670, SessionParameterObjectBlockSizes::make)
			.choice(SessionParameterObjectType.ibm6670Host, SessionParameterObjectBlockSizes::make)
			
			.choice(SessionParameterObjectType.ibm3270, RECORD::empty)
			.choice(SessionParameterObjectType.ibm3270Host, RECORD::empty)
			
			.choice(SessionParameterObjectType.oldTtyHost, SessionParameterObjectOldTty::make)
			.choice(SessionParameterObjectType.oldTty, SessionParameterObjectOldTty::make)
			
			.choice(SessionParameterObjectType.otherSessionType, RECORD::empty)
			
			.choice(SessionParameterObjectType.unknown, RECORD::empty)
			
			.choice(SessionParameterObjectType.ibm2780, SessionParameterObjectBlockSizes::make)
			.choice(SessionParameterObjectType.ibm2780Host, SessionParameterObjectBlockSizes::make)
			.choice(SessionParameterObjectType.ibm3780, SessionParameterObjectBlockSizes::make)
			.choice(SessionParameterObjectType.ibm3780Host, SessionParameterObjectBlockSizes::make)
			
			.choice(SessionParameterObjectType.siemens9750, RECORD::empty)
			.choice(SessionParameterObjectType.siemens9750Host, RECORD::empty)
			
			.choice(SessionParameterObjectType.ttyHost, SessionParameterObjectTty::make)
			.choice(SessionParameterObjectType.tty, SessionParameterObjectTty::make)
			
			.get();
	
	/*
	 * LineType: TYPE = {
	 *   bitSynchronous(0), byteSynchronous(1), asynchronous(2),
	 *   autoRecognition(3) };
	 */
	public enum LineType { bitSynchronous, byteSynchronous, asynchronous, autoRecognition }
	public static final EnumMaker<LineType> mkLineType = buildEnum(LineType.class).get();
	
	/*
	 * LineSpeed: TYPE = {
	 *   bps50(0), bps75(1), bps110(2), bps135(3), bps150(4),
	 *   bps300(5), bps600(6), bps1200(7), bps2400(8), bps3600(9),
	 *   bps4800(10), bps7200(11), bps9600(12),
	 *   bps19200(13), bps28800(14), bps38400(15), bps48000(16),
	 *   bps56000(17), bps57600(18)
	 *   };
	 */
	public enum LineSpeed {
		bps50, bps75, bps110, bps135, bps150,
		bps300, bps600, bps1200, bps2400, bps3600,
		bps4800, bps7200, bps9600,
		bps19200, bps28800, bps38400, bps48000,
		bps56000, bps57600
	}
	public static final EnumMaker<LineSpeed> mkLineSpeed = buildEnum(LineSpeed.class).get();
	
	/*
	 * Duplexity: TYPE = { fullduplex(0), halfduplex(1)};
	 */
	public enum Duplexity { fullduplex, halfduplex }
	public static final EnumMaker<Duplexity> mkDuplexity = buildEnum(Duplexity.class).get();
	
	/*
	 * CommParamObject: TYPE = RECORD [
	 *   accessDetail: CHOICE OF {
	 *   	directConn(0) => RECORD [
	 *   		duplex: Duplexity,
	 *   		lineType: LineType,
	 *   		lineSpeed: LineSpeed ],
	 *   	dialConn(1) => RECORD [
	 *   		duplex: Duplexity,
	 *   		lineType: LineType,
	 *   		lineSpeed: LineSpeed,
	 *   		dialMode: {manualDial(0), autoDial(1)},
	 *   		dialerNumber: CARDINAL,
	 *   		retryCount: CARDINAL ] }
	 *   ];
	 */
	public enum CommParamObjectType { directConn, dialConn }
	public static final EnumMaker<CommParamObjectType> mkCommParamObjectType = buildEnum(CommParamObjectType.class).get();
	public static class CommParamObjectDirect extends RECORD {
		public final ENUM<Duplexity> duplex = mkENUM(mkDuplexity);
		public final ENUM<LineType> lineType = mkENUM(mkLineType);
		public final ENUM<LineSpeed> lineSpeed = mkENUM(mkLineSpeed);
		private CommParamObjectDirect() {}
		public static CommParamObjectDirect make() { return new CommParamObjectDirect(); }
	}
	public enum DialMode { manualDial, autoDial }
	public static final EnumMaker<DialMode> mkDialMode = buildEnum(DialMode.class).get();
	public static class CommParamObjectDial extends RECORD {
		public final ENUM<Duplexity> duplex = mkENUM(mkDuplexity);
		public final ENUM<LineType> lineType = mkENUM(mkLineType);
		public final ENUM<LineSpeed> lineSpeed = mkENUM(mkLineSpeed);
		public final ENUM<DialMode> dialMode = mkENUM(mkDialMode);
		public final CARDINAL dialerNumber = mkCARDINAL();
		public final CARDINAL retryCount = mkCARDINAL();
		private CommParamObjectDial() {}
		public static CommParamObjectDial make() { return new CommParamObjectDial(); }
	}
	public static final ChoiceMaker<CommParamObjectType> mkCommParamObject = buildChoice(mkCommParamObjectType)
			.choice(CommParamObjectType.directConn, CommParamObjectDirect::make)
			.choice(CommParamObjectType.dialConn, CommParamObjectDial::make)
			.get();
	
	/*
	 * ReserveType: TYPE = { preemptNever(0), preemptAlways(1),
	 *   preemptInactive(2) };
	 */
	public enum ReserveType { preemptNever, preemptAlways, preemptInactive }
	public static final EnumMaker<ReserveType> mkReserveType = buildEnum(ReserveType.class).get();
	
	/*
	 * Resource: TYPE = ARRAY 2 OF UNSPECIFIED;
	 */
	public static class Resource extends ARRAY<UNSPECIFIED> {
		private Resource() { super(2, UNSPECIFIED::make); }
		public static Resource make() { return new Resource(); }
	}
	
	/*
	 * LineControl: TYPE = { primary(0), secondary(1) };
	 */
	public enum LineControl { primary, secondary }
	public static final EnumMaker<LineControl> mkLineControl = buildEnum(LineControl.class).get();
	
	/*
	 * ControllerAddress: TYPE = CARDINAL;
	 *
	 * TerminalAddress: TYPE = CARDINAL;
	 *
	 * TransportObject: TYPE = CHOICE OF {
	 *   rs232c(0) => RECORD [			-- spec doesn't say (0) --
	 *   	commParams: CommParamObject,
	 *   	preemptOthers, preemptMe: ReserveType,
	 *   	phoneNumber: STRING,
	 *   	line: CHOICE OF {		-- spec doesn't say (0) --
	 *   		alreadyReserved(0) => RECORD [resource: Resource],
	 *   		reserveNeeded(1) => RECORD [lineNumber: CARDINAL]
	 *   		}
	 *   	],
	 *   bsc(1) => RECORD [
	 *   	localTerminalID: STRING,
	 *   	localSecurityID: STRING,
	 *   	lineControl: LineControl,
	 *   	authenticateProc: UNSPECIFIED,
	 *   	bidReply: BidReply,
	 *   	sendLineHoldingEOTs: ExtendedBoolean,
	 *   	expectLineHoldingEOTs: ExtendedBoolean ],
	 *   teletype(2) => RECORD [],
	 *   polledBSCController(3), sdlcController(5) => RECORD [
	 *   	hostControllerName: STRING,
	 *   	controllerAddress: ControllerAddress,
	 *   	portsOnController: CARDINAL ],
	 *   polledBSCTerminal(4), sdlcTerminal(6) => RECORD [
	 *   	hostControllerName: STRING,
	 *   	terminalAddress: TerminalAddress ],
	 *   service(7) => RECORD [
	 *   	id: LONG CARDINAL ],
	 *   unused(8) => RECORD [],
	 *   polledBSCPrinter(9), sdlcPrinter(10) => RECORD [
	 *   	hostControllerName: STRING,
	 *   	printerAddress: TerminalAddress]
	 * };
	 */
	public enum TransportObjectType { rs232c, bsc, teletype, polledBSCController, polledBSCTerminal, sdlcController, sdlcTerminal, service, unused, polledBSCPrinter, sdlcPrinter }
	public static final EnumMaker<TransportObjectType> mkLineTransportObjectType = buildEnum(TransportObjectType.class).get();
	public enum Rs232cLineState { alreadyReserved, reserveNeeded }
	public static final EnumMaker<Rs232cLineState> mkRs232cLineState = buildEnum(Rs232cLineState.class).get();
	public static class Rs232cAlreadyReserved extends RECORD {
		public final Resource resource = mkMember(Resource::make);
		private Rs232cAlreadyReserved() {}
		public static Rs232cAlreadyReserved make() { return new Rs232cAlreadyReserved(); }
	}
	public static class Rs232cReserveNeeded extends RECORD {
		public final CARDINAL lineNumber = mkCARDINAL();
		private Rs232cReserveNeeded() {}
		public static Rs232cReserveNeeded make() { return new Rs232cReserveNeeded(); }
	}
	public static final ChoiceMaker<Rs232cLineState> mkRs232cChoice = buildChoice(mkRs232cLineState)
			.choice(Rs232cLineState.alreadyReserved, Rs232cAlreadyReserved::make)
			.choice(Rs232cLineState.reserveNeeded, Rs232cReserveNeeded::make)
			.get();
	public static class TransportObjectRs232c extends RECORD {
		public final CHOICE<CommParamObjectType> commParams = mkCHOICE(mkCommParamObject);
		public final ENUM<ReserveType> preemptOthers = mkENUM(mkReserveType);
		public final ENUM<ReserveType> preemptMe = mkENUM(mkReserveType);
		public final STRING phoneNumber = mkSTRING();
		public final CHOICE<Rs232cLineState> line = mkCHOICE(mkRs232cChoice);
		private TransportObjectRs232c() {}
		public static TransportObjectRs232c make() { return new TransportObjectRs232c(); }
	}
	public static class TransportObjectBsc extends RECORD {
		public final STRING localTerminalID = mkSTRING();
		public final STRING localSecurityID = mkSTRING();
		public final ENUM<LineControl> lineControl = mkENUM(mkLineControl);
		public final UNSPECIFIED authenticateProc = mkUNSPECIFIED();
		public final ENUM<BidReply> bidReply = mkENUM(mkBidReply);
		public final ENUM<ExtendedBoolean> sendLineHoldingEOTs = mkENUM(mkExtendedBoolean);
		public final ENUM<ExtendedBoolean> expectLineHoldingEOTs = mkENUM(mkExtendedBoolean);
		private TransportObjectBsc() {}
		public static TransportObjectBsc make() { return new TransportObjectBsc(); }
	}
	public static class TransportObjectIbmController extends RECORD {
		public final STRING hostControllerName = mkSTRING();
		public final CARDINAL controllerAddress = mkCARDINAL();
		public final CARDINAL portsOnController = mkCARDINAL();
		private TransportObjectIbmController() {}
		public static TransportObjectIbmController make() { return new TransportObjectIbmController(); }
	}
	public static class TransportObjectIbmTerminalOrPrinter extends RECORD {
		public final STRING hostControllerName = mkSTRING();
		public final CARDINAL terminalAddress = mkCARDINAL();
		private TransportObjectIbmTerminalOrPrinter() {}
		public static TransportObjectIbmTerminalOrPrinter make() { return new TransportObjectIbmTerminalOrPrinter(); }
	}
	public static class TransportObjectService extends RECORD {
		public final LONG_CARDINAL id = mkLONG_CARDINAL();
		private TransportObjectService() {}
		public static TransportObjectService make() { return new TransportObjectService(); }
	}
	public static final ChoiceMaker<TransportObjectType> mkTransportObject = buildChoice(mkLineTransportObjectType)
			.choice(TransportObjectType.rs232c, TransportObjectRs232c::make)
			.choice(TransportObjectType.bsc, TransportObjectBsc::make)
			.choice(TransportObjectType.teletype, RECORD::empty)
			.choice(TransportObjectType.polledBSCController, TransportObjectIbmController::make)
			.choice(TransportObjectType.sdlcController, TransportObjectIbmController::make)
			.choice(TransportObjectType.polledBSCTerminal, TransportObjectIbmTerminalOrPrinter::make)
			.choice(TransportObjectType.sdlcTerminal, TransportObjectIbmTerminalOrPrinter::make)
			.choice(TransportObjectType.service, TransportObjectService::make)
			.choice(TransportObjectType.unused, RECORD::empty)
			.choice(TransportObjectType.polledBSCPrinter, TransportObjectIbmTerminalOrPrinter::make)
			.choice(TransportObjectType.sdlcPrinter, TransportObjectIbmTerminalOrPrinter::make)
			.get();
	
	/*
	 * (PortClientType (ENUMERATION (unassigned 0)
     *                        (outOfService 1)
     *                        (its 2)
     *                        (irs 3)
     *                        (gws 4)
     *                        (ibm3270Host 5)
     *                        (ttyEmulation 6)
     *                        (rbs 7)
     *                        (fax 8)
     *                        (mailGateway 9)
     *                        (phototypesetter 10)))
	 */
	public enum PortClientType { unassigned, outOfService, its, irs, gws, ibm3270Host, ttyEmulation, rbs, fax, mailGateway, phototypesetter }
	public static final EnumMaker<PortClientType> mkPortClientType = buildEnum(PortClientType.class).get();
	
	/*
	 * (PortDialerType (ENUMERATION (none 0)
     *                        (vadic 1)
     *                        (hayes 2)
     *                        (ventel 3)
     *                        (rs366 4)))
	 */
	public enum PortDialerType { none, vadic, hayes, ventel, rs366 }
	public static final EnumMaker<PortDialerType> mkPortDialerType = buildEnum(PortDialerType.class).get();
	
	/*
	 * (PortEchoingLocation (ENUMERATION (application 0)
     *                             (ciu 1)
     *                             (terminal 2)))
	 */
	public enum PortEchoingLocation { application, ciu, terminal }
	public static final EnumMaker<PortEchoingLocation> mkPortEchoingLocation = buildEnum(PortEchoingLocation.class).get();
	
	/*
	 * [RS232CData (RECORD (cIUPort BOOLEAN)
     *                    (owningClientType PortClientType)
     *                    (preemptionAllowed BOOLEAN)
     *                    (lineNumber CARDINAL)
     *                    (dialerNumber CARDINAL)
     *                    (duplexity Duplexity)
     *                    (dialingHardware PortDialerType)
     *                    (charLength CharLength)
     *                    (echoing PortEchoingLocation)
     *                    (flowControl FlowControl)
     *                    (lineSpeed LineSpeed)
     *                    (parity Parity)
     *                    (stopBits StopBits)
     *                    (portActsAsDCE BOOLEAN)
     *                    (accessControl NSNAME)
     *                    (validLineSpeeds (SEQUENCE LineSpeed]
	 */
	public static class RS232CData extends RECORD {
		public final BOOLEAN cIUPort = mkBOOLEAN();
		public final ENUM<PortClientType> owningClientType = mkENUM(mkPortClientType);
		public final BOOLEAN preemptionAllowed = mkBOOLEAN();
		public final CARDINAL lineNumber = mkCARDINAL();
		public final CARDINAL dialerNumber = mkCARDINAL();
		public final ENUM<Duplexity> duplexity = mkENUM(mkDuplexity);
		public final ENUM<PortDialerType> dialingHardware = mkENUM(mkPortDialerType);
		public final ENUM<CharLength> charLength = mkENUM(mkCharLength);
		public final ENUM<PortEchoingLocation> echoing = mkENUM(mkPortEchoingLocation);
		public final FlowControl flowControl = mkRECORD(FlowControl::make);
		public final ENUM<LineSpeed> lineSpeed = mkENUM(mkLineSpeed);
		public final ENUM<Parity> parity = mkENUM(mkParity);
		public final ENUM<StopBits> stopBits = mkENUM(mkStopBits);
		public final BOOLEAN portActsAsDCE = mkBOOLEAN();
		public final ThreePartName accessControl = mkRECORD(ThreePartName::make);
		public final SEQUENCE<ENUM<LineSpeed>> validLineSpeeds = mkSEQUENCE(mkLineSpeed);
		private RS232CData() {}
		public static RS232CData make() { return new RS232CData(); }
	}
	
	/*
	 * (RS232CBack (RECORD (owningCIU STRING)
     *                    (owningECS STRING)
     *                    (owningClient STRING)
     *                    (portNumber CARDINAL)))
	 */
	public static class RS232CBack extends RECORD {
		public final STRING owningCIU = mkSTRING();
		public final STRING owningECS = mkSTRING();
		public final STRING owningClient = mkSTRING();
		public final CARDINAL portNumber = mkCARDINAL();
		private RS232CBack() {}
		public static RS232CBack make() { return new RS232CBack(); }
	}
	
	/*
	 * (IBMDeviceType (ENUMERATION (unused 0)
     *                       (model1 1)
     *                       (model2 2)
     *                       (model3 3)
     *                       (model4 4)
     *                       (model5 5)
     *                       (printer 6)
     *                       (other 7)))
	 */
	public enum IBMDeviceType { unused, model1, model2, model3, model4, model5, printer, other }
	public static final EnumMaker<IBMDeviceType> mkIBMDeviceType = buildEnum(IBMDeviceType.class).get();
	
	/*
	 * (IBM3270Languages (ENUMERATION (USenglish 0)
     *                          (Austrian 1)
     *                          (AustrianAlt 2)
     *                          (German 3)
     *                          (GermanAlt 4)
     *                          (Belgian 5)
     *                          (Brazilian 6)
     *                          (CanadianFrench 7)
     *                          (Danish 8)
     *                          (DanishAlt 9)
     *                          (Norwegian 10)
     *                          (NorwegianAlt 11)
     *                          (Finnish 12)
     *                          (FinnishAlt 13)
     *                          (Swedish 14)
     *                          (SwedishAlt 15)
     *                          (French 16)
     *                          (International 17)
     *                          (Italian 18)
     *                          (JapaneseEnglish 19)
     *                          (JapaneseKana 20)
     *                          (Portuguese 21)
     *                          (Spanish 22)
     *                          (SpanishAlt 23)
     *                          (SpanishSpeaking 24)
     *                          (UKenglish 25)
     *                          (unused1 26)
     *                          (unused2 27)
     *                          (unused3 28)
     *                          (unused4 29)
     *                          (unused5 30)
     *                          (unused6 31)))
	 */
	public enum IBM3270Language {
		USenglish, Austrian, AustrianAlt, German, GermanAlt, Belgian, Brazilian,
		CanadianFrench, Danish, DanishAlt, Norwegian, NorwegianAlt, Finnish, FinnishAlt,
		Swedish, SwedishAlt, French, International, Italian, JapaneseEnglish, JapaneseKana,
		Portuguese, Spanish, SpanishAlt, SpanishSpeaking, UKenglish,
		unused1, unused2, unused3, unused4, unused5, unused6
	}
	public static final EnumMaker<IBM3270Language> mkIBM3270Language = buildEnum(IBM3270Language.class).get();
	
	/*
	 * (ControllerLinkType (ENUMERATION (sdlc 0)
     *                            (bsc 1)))
	 */
	public enum ControllerLinkType { sdlc, bsc }
	public static final EnumMaker<ControllerLinkType> mkControllerLinkType = buildEnum(ControllerLinkType.class).get();
	
	/*
	 * (IBM3270Device (RECORD (model IBMDeviceType)
     *                       (accessControl NSNAME)))
	 */
	public static class IBM3270Device extends RECORD {
		public final ENUM<IBMDeviceType> model = mkENUM(mkIBMDeviceType);
		public final ThreePartName accessControl = mkRECORD(ThreePartName::make);
		private IBM3270Device() {}
		public static IBM3270Device make() { return new IBM3270Device(); }
	}
	
	/*
	 * [IBM3270Controller (RECORD (controllerAddress CARDINAL)
     *                           (portsOnController CARDINAL)
     *                           (linkType ControllerLinkType)
     *                           (language IBM3270Languages)
     *                           (devices (SEQUENCE IBM3270Device]
	 */
	public static class IBM3270Controller extends RECORD {
		public final CARDINAL controllerAddress = mkCARDINAL();
		public final CARDINAL portsOnController = mkCARDINAL();
		public final ENUM<ControllerLinkType> linkType = mkENUM(mkControllerLinkType);
		public final ENUM<IBM3270Language> language = mkENUM(mkIBM3270Language);
		public final SEQUENCE<IBM3270Device> devices = mkSEQUENCE(IBM3270Device::make);
		private IBM3270Controller() {}
		public static IBM3270Controller make() { return new IBM3270Controller(); } 
	}
	
	/*
	 * (IBM3270HostData (SEQUENCE IBM3270Controller))
	 */
	public static class IBM3270HostData extends SEQUENCE<IBM3270Controller> {
		private IBM3270HostData() { super(IBM3270Controller::make); }
		public static IBM3270HostData make() { return new IBM3270HostData(); }
	}
	
	/*
	 * (IBM3270HostBack (RECORD (path NSNAME]
	 */
	public static class IBM3270HostBack extends RECORD {
		public final ThreePartName path = mkRECORD(ThreePartName::make);
		private IBM3270HostBack() {}
		public static IBM3270HostBack make() { return new IBM3270HostBack(); }
	}
	
	
	/*
	 * ---------------------------------------------------------------------------- TTY service type constants
	 */
	
	public final int ttyServiceAny = 0;
	public final int ttyServiceSystemAdminsitration = 1;
	public final int ttyServiceExecutive = 2;
	public final int ttyServiceInteractiveTerminal = 0;
	public final int ttyServiceWorkstation = 5;

	
	/*
	 * ---------------------------------------------------------------------------- error types
	 */
	
	private static class SimpleErrorRecord extends ErrorRECORD {
		private final int errorCode;
		@Override public int getErrorCode() { return this.errorCode; }
		private SimpleErrorRecord(int errorCode) { this.errorCode = errorCode; }
	}
	
	/* ERRORS
	 *       ((Unimplemented 0)
     *        (NoCommunicationHardware 1)
     *        (IllegalTransport 2)
     *        (MediumConnectFailed 3)
     *        (BadAddressFormat 4)
     *        (NoDialingHardware 5)
     *        (DialingHardwareProblem 6)
     *        (TransmissionMediumUnavailable 7)
     *        (InconsistentParams 8)
     *        (TooManyGateStreams 9)
     *        (BugInGAPCode 10)
     *        (GapNotExported 11)
     *        (GapCommunicationError 12)
     *        (ControllerAlreadyExists 13)
     *        (ControllerDoesNotExist 14)
     *        (TerminalAddressInUse 15)
     *        (TerminalAddressInvalid 16)
     *        (ServiceTooBusy 17)
     *        (UserNotAuthenticated 18)
     *        (UserNotAuthorized 19)
     *        (ServiceNotFound 20)
     *        (RegisteredTwice 21)
     *        (TransmissionMediumHardwareProblem 22)
     *        (TransmissionMediumUnavailable2 23)
     *        (TransmissionMediumNotReady 24)
     *        (NoAnswerOrBusy 25)
     *        (NoRouteToGAPService 26)
     *        (GapServiceNotResponding 27)
     *        (CourierProtocolMismatch 28)
     *        (GapVersionMismatch 29)))
	 */
	
	public static class UnimplementedErrorRecord extends SimpleErrorRecord { UnimplementedErrorRecord() { super(0); } }
	public final ERROR<UnimplementedErrorRecord> UnimplementedError = mkERROR(UnimplementedErrorRecord.class);
	
	public static class NoCommunicationHardwareErrorRecord extends SimpleErrorRecord { NoCommunicationHardwareErrorRecord() { super(1); } }
	public final ERROR<NoCommunicationHardwareErrorRecord> NoCommunicationHardwareError = mkERROR(NoCommunicationHardwareErrorRecord.class);
	
	public static class IllegalTransportErrorRecord extends SimpleErrorRecord { IllegalTransportErrorRecord() { super(2); } }
	public final ERROR<IllegalTransportErrorRecord> IllegalTransportError = mkERROR(IllegalTransportErrorRecord.class);
	
	public static class MediumConnectFailedErrorRecord extends SimpleErrorRecord { MediumConnectFailedErrorRecord() { super(3); } }
	public final ERROR<MediumConnectFailedErrorRecord> MediumConnectFailedError = mkERROR(MediumConnectFailedErrorRecord.class);
	
	public static class BadAddressFormatErrorRecord extends SimpleErrorRecord { BadAddressFormatErrorRecord() { super(4); } }
	public final ERROR<BadAddressFormatErrorRecord> BadAddressFormatError = mkERROR(BadAddressFormatErrorRecord.class);
	
	public static class NoDialingHardwareErrorRecord extends SimpleErrorRecord { NoDialingHardwareErrorRecord() { super(5); } }
	public final ERROR<NoDialingHardwareErrorRecord> NoDialingHardwareError = mkERROR(NoDialingHardwareErrorRecord.class);
	
	public static class DialingHardwareProblemErrorRecord extends SimpleErrorRecord { DialingHardwareProblemErrorRecord() { super(6); } }
	public final ERROR<DialingHardwareProblemErrorRecord> DialingHardwareProblemError = mkERROR(DialingHardwareProblemErrorRecord.class);
	
	public static class TransmissionMediumUnavailableErrorRecord extends SimpleErrorRecord { TransmissionMediumUnavailableErrorRecord() { super(7); } }
	public final ERROR<TransmissionMediumUnavailableErrorRecord> TransmissionMediumUnavailableError = mkERROR(TransmissionMediumUnavailableErrorRecord.class);
	
	public static class InconsistentParamsErrorRecord extends SimpleErrorRecord { InconsistentParamsErrorRecord() { super(8); } }
	public final ERROR<InconsistentParamsErrorRecord> InconsistentParamsError = mkERROR(InconsistentParamsErrorRecord.class);
	
	public static class TooManyGateStreamsErrorRecord extends SimpleErrorRecord { TooManyGateStreamsErrorRecord() { super(9); } }
	public final ERROR<TooManyGateStreamsErrorRecord> TooManyGateStreamsError = mkERROR(TooManyGateStreamsErrorRecord.class);
	
	public static class BugInGAPCodeErrorRecord extends SimpleErrorRecord { BugInGAPCodeErrorRecord() { super(10); } }
	public final ERROR<BugInGAPCodeErrorRecord> BugInGAPCodeError = mkERROR(BugInGAPCodeErrorRecord.class);
	
	public static class GapNotExportedErrorRecord extends SimpleErrorRecord { GapNotExportedErrorRecord() { super(11); } }
	public final ERROR<GapNotExportedErrorRecord> GapNotExportedError = mkERROR(GapNotExportedErrorRecord.class);
	
	public static class GapCommunicationErrorErrorRecord extends SimpleErrorRecord { GapCommunicationErrorErrorRecord() { super(12); } }
	public final ERROR<GapCommunicationErrorErrorRecord> GapCommunicationErrorError = mkERROR(GapCommunicationErrorErrorRecord.class);
	
	public static class ControllerAlreadyExistsErrorRecord extends SimpleErrorRecord { ControllerAlreadyExistsErrorRecord() { super(13); } }
	public final ERROR<ControllerAlreadyExistsErrorRecord> ControllerAlreadyExistsError = mkERROR(ControllerAlreadyExistsErrorRecord.class);
	
	public static class ControllerDoesNotExistErrorRecord extends SimpleErrorRecord { ControllerDoesNotExistErrorRecord() { super(14); } }
	public final ERROR<ControllerDoesNotExistErrorRecord> ControllerDoesNotExistError = mkERROR(ControllerDoesNotExistErrorRecord.class);
	
	public static class TerminalAddressInUseErrorRecord extends SimpleErrorRecord { TerminalAddressInUseErrorRecord() { super(15); } }
	public final ERROR<TerminalAddressInUseErrorRecord> TerminalAddressInUseError = mkERROR(TerminalAddressInUseErrorRecord.class);
	
	public static class TerminalAddressInvalidErrorRecord extends SimpleErrorRecord { TerminalAddressInvalidErrorRecord() { super(16); } }
	public final ERROR<TerminalAddressInvalidErrorRecord> TerminalAddressInvalidError = mkERROR(TerminalAddressInvalidErrorRecord.class);
	
	public static class ServiceTooBusyErrorRecord extends SimpleErrorRecord { ServiceTooBusyErrorRecord() { super(17); } }
	public final ERROR<ServiceTooBusyErrorRecord> ServiceTooBusyError = mkERROR(ServiceTooBusyErrorRecord.class);
	
	public static class UserNotAuthenticatedErrorRecord extends SimpleErrorRecord { UserNotAuthenticatedErrorRecord() { super(18); } }
	public final ERROR<UserNotAuthenticatedErrorRecord> UserNotAuthenticatedError = mkERROR(UserNotAuthenticatedErrorRecord.class);
	
	public static class UserNotAuthorizedErrorRecord extends SimpleErrorRecord { UserNotAuthorizedErrorRecord() { super(19); } }
	public final ERROR<UserNotAuthorizedErrorRecord> UserNotAuthorizedError = mkERROR(UserNotAuthorizedErrorRecord.class);
	
	public static class ServiceNotFoundErrorRecord extends SimpleErrorRecord { ServiceNotFoundErrorRecord() { super(20); } }
	public final ERROR<ServiceNotFoundErrorRecord> ServiceNotFoundError = mkERROR(ServiceNotFoundErrorRecord.class);
	
	public static class RegisteredTwiceErrorRecord extends SimpleErrorRecord { RegisteredTwiceErrorRecord() { super(21); } }
	public final ERROR<RegisteredTwiceErrorRecord> RegisteredTwiceError = mkERROR(RegisteredTwiceErrorRecord.class);
	
	public static class TransmissionMediumHardwareProblemErrorRecord extends SimpleErrorRecord { TransmissionMediumHardwareProblemErrorRecord() { super(22); } }
	public final ERROR<TransmissionMediumHardwareProblemErrorRecord> TransmissionMediumHardwareProblemError = mkERROR(TransmissionMediumHardwareProblemErrorRecord.class);
	
	public static class TransmissionMediumUnavailable2ErrorRecord extends SimpleErrorRecord { TransmissionMediumUnavailable2ErrorRecord() { super(23); } }
	public final ERROR<TransmissionMediumUnavailable2ErrorRecord> TransmissionMediumUnavailable2Error = mkERROR(TransmissionMediumUnavailable2ErrorRecord.class);
	
	public static class TransmissionMediumNotReadyErrorRecord extends SimpleErrorRecord { TransmissionMediumNotReadyErrorRecord() { super(24); } }
	public final ERROR<TransmissionMediumNotReadyErrorRecord> TransmissionMediumNotReadyError = mkERROR(TransmissionMediumNotReadyErrorRecord.class);
	
	public static class NoAnswerOrBusyErrorRecord extends SimpleErrorRecord { NoAnswerOrBusyErrorRecord() { super(25); } }
	public final ERROR<NoAnswerOrBusyErrorRecord> NoAnswerOrBusyError = mkERROR(NoAnswerOrBusyErrorRecord.class);
	
	public static class NoRouteToGAPServiceErrorRecord extends SimpleErrorRecord { NoRouteToGAPServiceErrorRecord() { super(26); } }
	public final ERROR<NoRouteToGAPServiceErrorRecord> NoRouteToGAPServiceError = mkERROR(NoRouteToGAPServiceErrorRecord.class);
	
	public static class GapServiceNotRespondingErrorRecord extends SimpleErrorRecord { GapServiceNotRespondingErrorRecord() { super(27); } }
	public final ERROR<GapServiceNotRespondingErrorRecord> GapServiceNotRespondingError = mkERROR(GapServiceNotRespondingErrorRecord.class);
	
	public static class CourierProtocolMismatchErrorRecord extends SimpleErrorRecord { CourierProtocolMismatchErrorRecord() { super(28); } }
	public final ERROR<CourierProtocolMismatchErrorRecord> CourierProtocolMismatchError = mkERROR(CourierProtocolMismatchErrorRecord.class);
	
	public static class GapVersionMismatchErrorRecord extends SimpleErrorRecord { GapVersionMismatchErrorRecord() { super(29); } }
	public final ERROR<GapVersionMismatchErrorRecord> GapVersionMismatchError = mkERROR(GapVersionMismatchErrorRecord.class);

	
	/*
	 * ---------------------------------------------------------------------------- procedures
	 */

	/*
	 * Reset: PROCEDURE = 0;
	 */
	public final PROC<RECORD,RECORD> Reset = mkPROC(
						"Reset",
						0,
						RECORD::empty,
						RECORD::empty
						);
	
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
	public static class CreateParams extends RECORD {
		public final CHOICE<SessionParameterObjectType> sessionParameterHandle = mkCHOICE(mkSessionParameterObject);
		public final SEQUENCE<CHOICE<TransportObjectType>> transportList = mkSEQUENCE(mkTransportObject);
		public final CARDINAL createTimeout = mkCARDINAL();
		public final Credentials credentials = mkRECORD(Credentials::make);
		public final Verifier verifier = mkMember(Verifier::make);
		private CreateParams() {}
		public static CreateParams make() { return new CreateParams(); }
	}
	public static class CreateResults extends RawCourierConnectionClientResults {
		public final SessionHandle sessionHandle = mkMember(SessionHandle::make);
		private CreateResults() {}
		public static CreateResults make() { return new CreateResults(); }
		
		//
		// items forRawCourierConnectionClientResults: borrowing the Courier connection after the call
		//
		
		private iRawCourierConnectionClient connectionClient = null;
		
		public void setConnectionClient(iRawCourierConnectionClient client) {
			this.connectionClient = client;
		}
		
		@Override
		public iRawCourierConnectionClient getConnectionClient() {
			return this.connectionClient;
		}
	}
	public final PROC<CreateParams,CreateResults> Create = mkPROC(
			"Create",
			2,
			CreateParams::make,
			CreateResults::make,
			UnimplementedError, NoCommunicationHardwareError, IllegalTransportError, MediumConnectFailedError,
			BadAddressFormatError, NoDialingHardwareError, DialingHardwareProblemError, TransmissionMediumUnavailableError,
			InconsistentParamsError, TooManyGateStreamsError, BugInGAPCodeError, GapNotExportedError,
			GapCommunicationErrorError, ControllerAlreadyExistsError, ControllerDoesNotExistError,
			TerminalAddressInUseError, TerminalAddressInvalidError, ServiceTooBusyError, UserNotAuthenticatedError,
			UserNotAuthorizedError, ServiceNotFoundError, RegisteredTwiceError, TransmissionMediumHardwareProblemError,
			TransmissionMediumUnavailable2Error, TransmissionMediumNotReadyError, NoAnswerOrBusyError, NoRouteToGAPServiceError,
			GapServiceNotRespondingError, CourierProtocolMismatchError, GapVersionMismatchError
			);
	
	/*
	 * Delete: PROCEDURE [ session: SessionHandle ] = 3;
	 */
	public static class DeleteParams extends RECORD {
		public final SessionHandle sessionHandle = mkMember(SessionHandle::make);
		private DeleteParams() {}
		public static DeleteParams make() { return new DeleteParams(); }
	}
	public final PROC<DeleteParams,RECORD> Delete = mkPROC(
			"Delete",
			3,
			DeleteParams::make,
			RECORD::empty
			);
	
}
