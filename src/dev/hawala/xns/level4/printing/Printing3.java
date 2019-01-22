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

package dev.hawala.xns.level4.printing;

import dev.hawala.xns.level3.courier.ARRAY;
import dev.hawala.xns.level3.courier.BOOLEAN;
import dev.hawala.xns.level3.courier.CARDINAL;
import dev.hawala.xns.level3.courier.CHOICE;
import dev.hawala.xns.level3.courier.CrEnum;
import dev.hawala.xns.level3.courier.CrProgram;
import dev.hawala.xns.level3.courier.ENUM;
import dev.hawala.xns.level3.courier.ErrorRECORD;
import dev.hawala.xns.level3.courier.LONG_CARDINAL;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.SEQUENCE;
import dev.hawala.xns.level3.courier.STRING;
import dev.hawala.xns.level3.courier.UNSPECIFIED;
import dev.hawala.xns.level3.courier.iWireData;
import dev.hawala.xns.level3.courier.iWireDynamic;
import dev.hawala.xns.level4.common.BulkData1;
import dev.hawala.xns.level4.common.Time2.Time;

/**
 * Definition of the Printing Courier program (PROGRAM 4 VERSION 3),
 * (transcribed from Printing3.cr).
 * 
 * @author Dr. Hans-Walter Latz / Berlin 2019
 */
public class Printing3 extends CrProgram {
	
	public static final int PROGRAM = 4;
	public static final int VERSION = 3;
	
	@Override
	public int getProgramNumber() { return PROGRAM; }
	
	@Override
	public int getVersionNumber() { return VERSION; }
	
	/*
	 * ********* common data structures 
	 */
	
	public static class ValueRecord<T extends iWireData> extends RECORD {
		private ValueRecord(iWireDynamic<T> maker) {
			this.value = mkMember(maker);
		}
		public final T value;
	}
	public static ValueRecord<STRING> makeStringRecord() { return new ValueRecord<STRING>(STRING::make); }
	public static ValueRecord<Time> makeTimeRecord()  { return new ValueRecord<Time>(Time::make); }
	public static ValueRecord<CARDINAL> makeCardinalRecord() { return new ValueRecord<CARDINAL>(CARDINAL::make); }
	public static ValueRecord<LONG_CARDINAL> makeLongCardinalRecord() { return new ValueRecord<LONG_CARDINAL>(LONG_CARDINAL::make); }
	public static ValueRecord<BOOLEAN> makeBooleanRecord() { return new ValueRecord<BOOLEAN>(BOOLEAN::make); }
	
	/*
	 * ********* Courier data structures
	 */
	
	
	/*
	 * RequestID: TYPE = ARRAY 5 OF UNSPECIFIED; -- the standard time and date format --
	 */
	public static class RequestID extends ARRAY<UNSPECIFIED> {
		private RequestID() { super(5, UNSPECIFIED::make); }
		public static RequestID make() { return new RequestID(); }
	}
	
	
	/*
	 * PrintAttributes: TYPE = SEQUENCE 3 OF CHOICE OF {
	 *     printObjectName(0) => STRING, -- default is implementation-dependent --
	 *     printObjectCreateDate(1) => Time, -- default is implementation-dependent --
	 *     senderName(2) => STRING }; -- default is implementation-dependent --
	 */
	public enum PrintAttributeChoice { printObjectName , printObjectCreateDate , senderName }
	public static final EnumMaker<PrintAttributeChoice> mkPrintAttributesChoice = buildEnum(PrintAttributeChoice.class).get();
	
	public static final ChoiceMaker<PrintAttributeChoice> mkPrintAttribute = buildChoice(mkPrintAttributesChoice)
			.choice(PrintAttributeChoice.printObjectName, Printing3::makeStringRecord)
			.choice(PrintAttributeChoice.printObjectCreateDate, Printing3::makeTimeRecord)
			.choice(PrintAttributeChoice.senderName, Printing3::makeStringRecord)
			.get();
	
	public static class PrintAttributes extends SEQUENCE<CHOICE<PrintAttributeChoice>> {
		private PrintAttributes() { super(3, mkPrintAttribute); }
		public static PrintAttributes make() { return new PrintAttributes(); }
	}
	
	
	/*
	 * Paper: TYPE = CHOICE OF {
	 *   unknown(0) => RECORD [],
	 *   knownSize(1) => {
	 *     usLetter(1),	-- defined as 8.5" x 11.0" or 216mm x 297mm --
	 *     usLegal(2),	-- defined as 8.5" x 14.0" or 216mm x 356mm --
	 *     a0(3), a1(4), a2(5), a3(6), a4(7), a5(8), a6(9), a7(10), 
	 *     a8(11), a9(12), a10(35),
	 *     isoB0(13), isoB1(14), isoB2(15), isoB3(16), isoB4(17),
	 *     isoB5(18), isoB6(19), isoB7(20), isoB8(21),
	 *     isoB9(22), isoB10(23),
	 *     jisB0(24), jisB1(25), jisB2(26), jisB3(27), jisB4(28),
	 *     jisB5(29), jisB6(30), jisB7(31), jisB8(32), jisB9(33),
	 *     jisB10(34)},
	 *   otherSize(2) => RECORD [width, length: CARDINAL]}; -- both in millimeters --
	 */
	public enum PaperChoice { unknown , knownSize , otherSize }
	public static final EnumMaker<PaperChoice> mkPaperChoice = buildEnum(PaperChoice.class).get();

	public enum PaperKnownSize implements CrEnum {
		usLetter(1), usLegal(2),
		a0(3), a1(4), a2(5), a3(6), a4(7), a5(8), a6(9), a7(10), a8(11), a9(12), a10(35),
		isoB0(13), isoB1(14), isoB2(15), isoB3(16), isoB4(17), isoB5(18),
		isoB6(19), isoB7(20), isoB8(21), isoB9(22), isoB10(23),
		jisB0(24), jisB1(25), jisB2(26), jisB3(27), jisB4(28), jisB5(29),
		jisB6(30), jisB7(31), jisB8(32), jisB9(33), jisB10(34);
		
		private final int wireValue;
		private PaperKnownSize(int w) { this.wireValue = w; }
		@Override public int getWireValue() { return this.wireValue; }
	}
	public static final EnumMaker<PaperKnownSize> mkPaperKnownSize = buildEnum(PaperKnownSize.class).get();
	public static class KnownSizeRecord extends RECORD {
		public final ENUM<PaperKnownSize> paperKnownSize = mkENUM(mkPaperKnownSize);
		private KnownSizeRecord() {}
		public static KnownSizeRecord make() { return new KnownSizeRecord(); }
	}
	
	public static class OtherSizeRecord extends RECORD {
		public final CARDINAL width = mkCARDINAL();
		public final CARDINAL length = mkCARDINAL();
		private OtherSizeRecord() {}
		public static OtherSizeRecord make() { return new OtherSizeRecord(); }
	}
	
	public static final ChoiceMaker<PaperChoice> mkPaper = buildChoice(mkPaperChoice)
			.choice(PaperChoice.unknown, RECORD::empty)
			.choice(PaperChoice.knownSize, KnownSizeRecord::make)
			.choice(PaperChoice.otherSize, OtherSizeRecord::make)
			.get();
	
	
	/*
	 * Medium: TYPE = CHOICE OF {paper(0) => Paper};
	 */
	public enum MediumChoice { paper }
	public static final EnumMaker<MediumChoice> mkMediumChoice = buildEnum(MediumChoice.class).get();
	
	public static class PaperRecord extends RECORD {
		public final CHOICE<PaperChoice> value = mkCHOICE(mkPaper);
		private PaperRecord () {}
		public static PaperRecord make() { return new PaperRecord(); }
	}
	
	public static final ChoiceMaker<MediumChoice> mkMedium = buildChoice(mkMediumChoice)
			.choice(MediumChoice.paper, PaperRecord::make)
			.get();
	
	
	/*
	 * Media: TYPE = SEQUENCE 100 OF Medium;
	 */
	public static class Media extends SEQUENCE<CHOICE<MediumChoice>> {
		private Media() { super(100, mkMedium); }
		public static Media make() { return new Media(); }
	}
	
	public static class MediaRecord extends RECORD {
		public final Media value = mkMember(Media::make);
		private MediaRecord() {}
		public static MediaRecord make() { return new MediaRecord(); }
	}
	
	
	/*
	 * PrintOptions: TYPE = SEQUENCE 10 OF CHOICE OF {
	 *   printObjectSize(0) => LONG CARDINAL, -- default is size of master --
	 *   recipientName(1) => STRING, -- default is senderName --
	 *   message(2) => STRING, -- default is "" --
	 *   copyCount(3) => CARDINAL, -- default is 1 --
	 *   pagesToPrint(4) => RECORD [
	 *       beginningPageNumber, -- default is 1, the first page of the master --
	 *       endingPageNumber: CARDINAL], -- default is the last page of the master --
	 *   mediumHint(5) => Medium, -- default is implementation-dependent --
	 *   priorityHint(6) => {low(0), normal(1), high(2)}, -- default is implementation-dependent --
	 *   releaseKey(7) => Authentication.HashedPassword, -- default is 177777B --
	 *   staple(8) => BOOLEAN, -- default is FALSE --
	 *   twoSided(9) => BOOLEAN }; -- default is FALSE --
	 */
	public enum PrintOptionsChoice {
		printObjectSize, recipientName, message, copyCount, pagesToPrint,
		mediumHint, priorityHint, releaseKey, staple, twoSided
	}
	public static final EnumMaker<PrintOptionsChoice> mkPrintOptionsChoice = buildEnum(PrintOptionsChoice.class).get();
	
	public static class PagesToPrintRecord extends RECORD {
		public final CARDINAL beginningPageNumber = mkCARDINAL();
		public final CARDINAL endingPageNumber = mkCARDINAL();
		private PagesToPrintRecord() {}
		public static PagesToPrintRecord make() { return new PagesToPrintRecord(); }
	}
	
	public static class MediumRecord extends RECORD {
		public final CHOICE<MediumChoice> value = mkCHOICE(mkMedium);
		private MediumRecord() {}
		public static MediumRecord make() { return new MediumRecord(); }
	}
	
	public enum PriorityHint { low , normal , high }
	public static final EnumMaker<PriorityHint> mkPriorityHint = buildEnum(PriorityHint.class).get();
	public static class PriorityHintRecord extends RECORD {
		public final ENUM<PriorityHint> value = mkENUM(mkPriorityHint);
		private PriorityHintRecord() {}
		public static PriorityHintRecord make() { return new PriorityHintRecord(); }
	}
	
	public static final ChoiceMaker<PrintOptionsChoice> mkPrintOption = buildChoice(mkPrintOptionsChoice)
			.choice(PrintOptionsChoice.printObjectSize, Printing3::makeLongCardinalRecord)
			.choice(PrintOptionsChoice.recipientName, Printing3::makeStringRecord)
			.choice(PrintOptionsChoice.message, Printing3::makeStringRecord)
			.choice(PrintOptionsChoice.copyCount, Printing3::makeCardinalRecord)
			.choice(PrintOptionsChoice.pagesToPrint, PagesToPrintRecord::make)
			.choice(PrintOptionsChoice.mediumHint, MediumRecord::make)
			.choice(PrintOptionsChoice.priorityHint, PriorityHintRecord::make)
			.choice(PrintOptionsChoice.releaseKey, Printing3::makeCardinalRecord) // Authentication.HashedPassword is: CARDINAL
			.choice(PrintOptionsChoice.staple, Printing3::makeBooleanRecord)
			.choice(PrintOptionsChoice.twoSided, Printing3::makeBooleanRecord)
			.get();
	
	public static class PrintOptions extends SEQUENCE<CHOICE<PrintOptionsChoice>> {
		private PrintOptions() { super(10, mkPrintOption); }
		public static PrintOptions make() { return new PrintOptions(); }
	}
	
	
	/*
	 * PrinterProperties: TYPE = SEQUENCE 3 OF CHOICE OF {
	 *   ppmedia(0) => Media,
	 *   ppstaple(1) => BOOLEAN, -- default is FALSE --
	 *   pptwoSided(2) => BOOLEAN}; -- default is FALSE --
	 */
	public enum PrinterPropertiesChoice { ppmedia , ppstaple, pptwoSided }
	public static final EnumMaker<PrinterPropertiesChoice> mkPrinterPropertiesChoice = buildEnum(PrinterPropertiesChoice.class).get();
	
	public static final ChoiceMaker<PrinterPropertiesChoice> mkPrinterProperty = buildChoice(mkPrinterPropertiesChoice)
			.choice(PrinterPropertiesChoice.ppmedia, MediaRecord::make)
			.choice(PrinterPropertiesChoice.ppstaple, Printing3::makeBooleanRecord)
			.choice(PrinterPropertiesChoice.pptwoSided, Printing3::makeBooleanRecord)
			.get();
	public static class PrinterProperties extends SEQUENCE<CHOICE<PrinterPropertiesChoice>> {
		private PrinterProperties() { super(3, mkPrinterProperty); }
		public static PrinterProperties make() { return new PrinterProperties(); }
	}
	
	
	/*
	 * PrinterStatus: TYPE = SEQUENCE 4 OF CHOICE OF {
	 *     spooler(0) => {available(0), busy(1), disabled(2), full(3)},
	 *     formatter(1) => {available(0), busy(1), disabled(2)},
	 *     printer(2) => {available(0), busy(1), disabled(2), needsAttention(3),
	 *         needsKeyOperator(4) },
	 *     media(3) => Media};
	 */
	public enum SpoolerStatusEnum { available, busy, disabled, full }
	public static final EnumMaker<SpoolerStatusEnum> mkSpoolerStatusEnum = buildEnum(SpoolerStatusEnum.class).get();
	public static class SpoolerStatusRecord extends RECORD {
		public final ENUM<SpoolerStatusEnum> value = mkENUM(mkSpoolerStatusEnum);
		private SpoolerStatusRecord() {}
		public static SpoolerStatusRecord make() { return new SpoolerStatusRecord(); }
	}
	
	public enum FormatterStatusEnum { available, busy, disabled }
	public static final EnumMaker<FormatterStatusEnum> mkFormatterStatusEnum = buildEnum(FormatterStatusEnum.class).get();
	public static class FormatterStatusRecord extends RECORD {
		public final ENUM<FormatterStatusEnum> value = mkENUM(mkFormatterStatusEnum);
		private FormatterStatusRecord() {}
		public static FormatterStatusRecord make() { return new FormatterStatusRecord(); }
	}
	
	public enum PrinterStatusEnum { available, busy, disabled, needsAttention, needsKeyOperator }
	public static final EnumMaker<PrinterStatusEnum> mkPrinterStatusEnum = buildEnum(PrinterStatusEnum.class).get();
	public static class PrinterStatusRecord extends RECORD {
		public final ENUM<PrinterStatusEnum> value = mkENUM(mkPrinterStatusEnum);
		private PrinterStatusRecord() {}
		public static PrinterStatusRecord make() { return new PrinterStatusRecord(); }
	}
	
	public enum PrinterStatusChoice { spooler, formatter, printer, media }
	public static final EnumMaker<PrinterStatusChoice> mkPrinterStatusChoice = buildEnum(PrinterStatusChoice.class).get();
	public static final ChoiceMaker<PrinterStatusChoice> mkPrinterStatus = buildChoice(mkPrinterStatusChoice)
			.choice(PrinterStatusChoice.spooler, SpoolerStatusRecord::make)
			.choice(PrinterStatusChoice.formatter, FormatterStatusRecord::make)
			.choice(PrinterStatusChoice.printer, PrinterStatusRecord::make)
			.choice(PrinterStatusChoice.media, MediaRecord::make)
			.get();
	
	public static class PrinterStatus extends SEQUENCE<CHOICE<PrinterStatusChoice>> {
		private PrinterStatus() { super(4, mkPrinterStatus); }
		public static PrinterStatus make() { return new PrinterStatus(); } 
	}
	
	
	/*
	 * RequestStatus: TYPE = SEQUENCE 2 OF CHOICE OF {
	 *     status(0) => {pending(0), inProgress(1), completed(2),
	 *         completedWithWarning(3), unknown(4), rejected(5), aborted(6),
	 *         canceled(7), held(8) },
	 *     statusMessage(1) => STRING}; -- default is "" --
	 */
	public enum StatusEnum { pending, inProgress, completed, completedWithWarning, unknown, rejected, aborted, canceled, held }
	public static final EnumMaker<StatusEnum> mkStatusEnum = buildEnum(StatusEnum.class).get();
	public static class StatusRecord extends RECORD {
		public final ENUM<StatusEnum> value = mkENUM(mkStatusEnum);
		private StatusRecord() {}
		public static StatusRecord make() { return new StatusRecord(); }
	}
	
	public enum RequestStatusChoice { status, statusMessage }
	public static final EnumMaker<RequestStatusChoice> mkRequestStatusChoice = buildEnum(RequestStatusChoice.class).get();
	public static final ChoiceMaker<RequestStatusChoice> mkRequestStatus = buildChoice(mkRequestStatusChoice)
			.choice(RequestStatusChoice.status, StatusRecord::make)
			.choice(RequestStatusChoice.statusMessage, Printing3::makeStringRecord)
			.get();
	public static class RequestStatus extends SEQUENCE<CHOICE<RequestStatusChoice>> {
		private RequestStatus() { super(2, mkRequestStatus); }
		public static RequestStatus make() { return new RequestStatus(); }
	}
	
	/*
	 * ********* Courier errors
	 */
	
	/*
	 * Busy: ERROR = 0; -- print service cannot accept a new request at this time --
	 */
	public static class BusyRecord extends ErrorRECORD {
		@Override public int getErrorCode() { return 0; }
	}
	public final ERROR<BusyRecord> Busy = mkERROR(BusyRecord.class);
	
	/*
	 * InsufficientSpoolSpace: ERROR = 1; -- print service does not have enough space to spool a new request --
	 */
	public static class InsufficientSpoolSpaceRecord extends ErrorRECORD {
		@Override public int getErrorCode() { return 1; }
	}
	public final ERROR<InsufficientSpoolSpaceRecord> InsufficientSpoolSpace = mkERROR(InsufficientSpoolSpaceRecord.class);
	
	/*
	 * InvalidPrintParameters: ERROR = 2; -- call to Print specified inconsistent arguments --
	 */
	public static class InvalidPrintParametersRecord extends ErrorRECORD {
		@Override public int getErrorCode() { return 2; }
	}
	public final ERROR<InvalidPrintParametersRecord> InvalidPrintParameters = mkERROR(InvalidPrintParametersRecord.class);
	
	/*
	 * MasterTooLarge: ERROR = 3; -- master is too large for the printer service to ever accept --
	 */
	public static class MasterTooLargeRecord extends ErrorRECORD {
		@Override public int getErrorCode() { return 3; }
	}
	public final ERROR<MasterTooLargeRecord> MasterTooLarge = mkERROR(MasterTooLargeRecord.class);
	
	/*
	 * MediumUnavailable: ERROR = 4; -- the specified medium was not available --
	 */
	public static class MediumUnavailableRecord extends ErrorRECORD {
		@Override public int getErrorCode() { return 4; }
	}
	public final ERROR<MediumUnavailableRecord> MediumUnavailable = mkERROR(MediumUnavailableRecord.class);
	
	/*
	 * ServiceUnavailable: ERROR = 5; -- the service is not accepting any remote procedure calls --
	 */
	public static class ServiceUnavailableRecord extends ErrorRECORD {
		@Override public int getErrorCode() { return 5; }
	}
	public final ERROR<ServiceUnavailableRecord> ServiceUnavailable = mkERROR(ServiceUnavailableRecord.class);
	
	/*
	 * SpoolingDisabled: ERROR = 6; -- print service is not accepting print requests --
	 */
	public static class SpoolingDisabledRecord extends ErrorRECORD {
		@Override public int getErrorCode() { return 6; }
	}
	public final ERROR<SpoolingDisabledRecord> SpoolingDisabled = mkERROR(SpoolingDisabledRecord.class);
	
	/*
	 * SpoolingQueueFull: ERROR = 7; -- print service does not have enough space to accept a new request --
	 */
	public static class SpoolingQueueFullRecord extends ErrorRECORD {
		@Override public int getErrorCode() { return 7; }
	}
	public final ERROR<SpoolingQueueFullRecord> SpoolingQueueFull = mkERROR(SpoolingQueueFullRecord.class);
	
	/*
	 * SystemError: ERROR = 8; -- print service is in an internally inconsistent state --
	 */
	public static class SystemErrorRecord extends ErrorRECORD {
		@Override public int getErrorCode() { return 8; }
	}
	public final ERROR<SystemErrorRecord> SystemError = mkERROR(SystemErrorRecord.class);
	
	/*
	 * TooManyClients: ERROR = 9; -- print service does not have enough resources to open a new connection --
	 */
	public static class TooManyClientsRecord extends ErrorRECORD {
		@Override public int getErrorCode() { return 9; }
	}
	public final ERROR<TooManyClientsRecord> TooManyClients = mkERROR(TooManyClientsRecord.class);
	
	/*
	 * Undefined: ERROR [problem: UndefinedProblem] = 10; -- some procedure in Printing is not implemented --
	 */
	public static class UndefinedRecord extends ErrorRECORD {
		public final CARDINAL problem = mkCARDINAL();
		public UndefinedRecord(int problem) { this.problem.set(problem); } 
		@Override public int getErrorCode() { return 10; }
	}
	public final ERROR<UndefinedRecord> Undefined = mkERROR(UndefinedRecord.class);
	
	/*
	 * ConnectionError: ERROR [problem: ConnectionProblem] = 11;
	 */
	public enum ConnectionProblem implements CrEnum {
			// -- communications problems --
			noRoute(0),                // -- no route to the other party could be found. --
			noResponse(1),             // -- the other party never answered. --
			transmissionHardware(2),   // -- some local transmission hardware was inoperable. --
			transportTimeout(3),       // -- the other party responded but later failed to respond. --
			// -- resource problems --
			tooManyLocalConnections(4),// -- no additional connection is possible. --
			tooManyRemoteConnections(5),//-- the other party rejected the connection attempt. --
			// -- remote program implementation problems --
			missingCourier(6),         // -- the other party has no Courier implementation. --
			missingProgram(7),         // -- the other party does not implement the Bulk Data program. --
			missingProcedure(8),       // -- the other party does not implement the procedure. --
			protocolMismatch(9),       // -- the two parties have no Courier version in commmon. --
			parameterInconsistency(10),// -- a protocol violation occurred in parameters. --
			invalidMessage(11),        // -- a protocol vilation occurred in message format. --
			returnTimedOut(12),        // -- the procedure call never returned. --
			// -- miscellaneous --
			otherCallProblem(0177777); // -- some other protocol violation occurred during a call. --
			
			private final int wireValue;
			private ConnectionProblem(int w) { this.wireValue = w; }
			@Override public int getWireValue() { return this.wireValue; }
	}
	public static final EnumMaker<ConnectionProblem> mkConnectionProblem = buildEnum(ConnectionProblem.class).get();
	public static class ConnectionErrorRecord extends ErrorRECORD {
		public final ENUM<ConnectionProblem> problem = mkENUM(mkConnectionProblem);
		public ConnectionErrorRecord(ConnectionProblem problem) { this.problem.set(problem); } 
		@Override public int getErrorCode() { return 11; }
	}
	public final ERROR<ConnectionErrorRecord> ConnectionError = mkERROR(ConnectionErrorRecord.class);
	
	/*
	 * TransferError: ERROR [problem: TransferProblem] = 12;
	 */
	public enum TransferProblem implements CrEnum {
			aborted(0),         // -- The bulk data transfer was aborted by the party at the other end of the connection. --
			formatIncorrect(2), // -- The bulk data received from the souce did not have the expected format. --
			noRendezvous(3),    // -- The identifier from the other party never appeared. --
			wrongDirection(4);  // -- The other party wanted to transfer the data in the wrong direction. --
			
			private final int wireValue;
			private TransferProblem(int w) { this.wireValue = w; }
			@Override public int getWireValue() { return this.wireValue; }
	}
	public static final EnumMaker<TransferProblem> mkTransferProblem = buildEnum(TransferProblem.class).get();
	public static class TransferErrorRecord extends ErrorRECORD {
		public final ENUM<TransferProblem> problem = mkENUM(mkTransferProblem);
		public TransferErrorRecord(TransferProblem problem) { this.problem.set(problem); } 
		@Override public int getErrorCode() { return 12; }
	}
	public final ERROR<TransferErrorRecord> TransferError = mkERROR(TransferErrorRecord.class);

	/*
	 * ********* Courier procedures 
	 */
	
	/*
	 * Print: PROCEDURE [master: BulkData.Source, printAttributes: PrintAttributes, printOptions: PrintOptions]
	 *  RETURNS [printRequestID: RequestID]
	 *  REPORTS [Busy, ConnectionError, InsufficientSpoolSpace,
	 *     InvalidPrintParameters, MasterTooLarge, MediumUnavailable,
	 *     ServiceUnavailable, SpoolingDisabled, SpoolingQueueFull, SystemError,
	 *     TooManyClients, TransferError, Undefined] = 0;
	 */
	public static class PrintParams extends RECORD {
		public final BulkData1.Source master = mkRECORD(BulkData1.Source::make);
		public final PrintAttributes printAttributes = mkMember(PrintAttributes::make);
		public final PrintOptions printOptions = mkMember(PrintOptions::make);
		
		private PrintParams() {}
		public static PrintParams make() { return new PrintParams(); }
	}
	public static class PrintResults extends RECORD {
		public final RequestID printRequestID = mkMember(RequestID::make);
		
		private PrintResults() {}
		public static PrintResults make() { return new PrintResults(); }
	}
	public final PROC<PrintParams,PrintResults> Print = mkPROC(
							0,
							PrintParams::make,
							PrintResults::make,
							Busy, ConnectionError, InsufficientSpoolSpace,
							InvalidPrintParameters, MasterTooLarge, MediumUnavailable,
							ServiceUnavailable, SpoolingDisabled, SpoolingQueueFull, SystemError,
							TooManyClients, TransferError, Undefined);
	
	/*
	 * GetPrinterProperties: PROCEDURE
	 *  RETURNS [properties: PrinterProperties]
	 *  REPORTS [ServiceUnavailable, SystemError, Undefined] = 1;
	 */
	public static class GetPrinterPropertiesResults extends RECORD {
		public final PrinterProperties properties = mkMember(PrinterProperties::make);
		
		private GetPrinterPropertiesResults() {}
		public static GetPrinterPropertiesResults make() { return new GetPrinterPropertiesResults(); }
	}
	public final PROC<RECORD,GetPrinterPropertiesResults> GetPrinterProperties = mkPROC(
							1,
							RECORD::empty,
							GetPrinterPropertiesResults::make,
							ServiceUnavailable, SystemError, Undefined);
	
	/*
	 * GetPrintRequestStatus: PROCEDURE [printRequestID: RequestID]
	 *  RETURNS [status: RequestStatus]
	 *  REPORTS [ServiceUnavailable, SystemError, Undefined] = 2;
	 */
	public static class GetPrintRequestStatusParams extends RECORD {
		public final RequestID printRequestID = mkMember(RequestID::make);
		
		private GetPrintRequestStatusParams() {}
		public static GetPrintRequestStatusParams make() { return new GetPrintRequestStatusParams(); }
	}
	public static class GetPrintRequestStatusResults extends RECORD {
		public final RequestStatus status = mkMember(RequestStatus::make);
		
		private GetPrintRequestStatusResults() {}
		public static GetPrintRequestStatusResults make() { return new GetPrintRequestStatusResults(); }
	}
	public final PROC<GetPrintRequestStatusParams,GetPrintRequestStatusResults> GetPrintRequestStatus = mkPROC(
							2,
							GetPrintRequestStatusParams::make,
							GetPrintRequestStatusResults::make,
							ServiceUnavailable, SystemError, Undefined);
	
	/*
	 * GetPrinterStatus: PROCEDURE
	 *  RETURNS [status: PrinterStatus]
	 *  REPORTS [ServiceUnavailable, SystemError, Undefined] = 3;
	 */
	public static class GetPrinterStatusResults extends RECORD {
		public final PrinterStatus status = mkMember(PrinterStatus::make);
		
		private GetPrinterStatusResults() {}
		public static GetPrinterStatusResults make() { return new GetPrinterStatusResults(); }
	}
	public final PROC<RECORD,GetPrinterStatusResults> GetPrinterStatus = mkPROC(
							3,
							RECORD::empty,
							GetPrinterStatusResults::make,
							ServiceUnavailable, SystemError, Undefined);
	
}
