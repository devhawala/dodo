/*
Copyright (c) 2022, Dr. Hans-Walter Latz
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

package dev.hawala.xns.level4.mailing;

import dev.hawala.xns.level3.courier.BOOLEAN;
import dev.hawala.xns.level3.courier.CARDINAL;
import dev.hawala.xns.level3.courier.CrProgram;
import dev.hawala.xns.level3.courier.ENUM;
import dev.hawala.xns.level3.courier.ErrorRECORD;
import dev.hawala.xns.level3.courier.INTEGER;
import dev.hawala.xns.level3.courier.LONG_CARDINAL;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.SEQUENCE;
import dev.hawala.xns.level3.courier.UNSPECIFIED2;
import dev.hawala.xns.level3.courier.UNSPECIFIED3;
import dev.hawala.xns.level4.common.AuthChsCommon.Credentials;
import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;
import dev.hawala.xns.level4.common.AuthChsCommon.Verifier;
import dev.hawala.xns.level4.common.BulkData1;
import dev.hawala.xns.level4.filing.FilingCommon.AttributeSequence;
import dev.hawala.xns.level4.mailing.MailingCommon.AuthenticationErrorRecord;

/**
 * Definition of the Inbasket Courier program version 2 for accessing user
 * mailboxes by mail agents, defining the subset used by GlobalView.
 * 
 * Most structures and procedures defined here are highly speculative, as
 * no courier program definition (.cr or .courier file) and no documentation
 * was found in the internet for the mailing protocols used by GlobalView (and
 * possibly by ViewPoint 3.0). The names given to the items here are also some
 * kind of "best guesses", as well as the true meaning of substructure elements.
 * 
 * However some information about the datatypes became available with the sources
 * of Interlisp-D/Medley, as the Lafite mail client of Medley 3.51 uses the new
 * courier mail protocol versions (MailTransport 5 resp. Inbasket 2), see: 
 * https://github.com/Interlisp/medley/blob/master/library/lafite/NEWNSMAIL
 * 
 * So this additional information was used to redefine/refine/rename some of the
 * items defined here to be more in line with the (supposed) true definition of
 * these courier protocols.
 * As an other consequence, the documentation comments for protocol items use the
 * Lisp notation instead of the Courier language notation.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2022)
 */
public class Inbasket2 extends CrProgram {

	public static final int PROGRAM = 18;
	public static final int VERSION = 2;

	@Override
	public int getProgramNumber() { return PROGRAM; }

	@Override
	public int getVersionNumber() { return VERSION; }
	
	/*
	 * common types
	 */
	
	public static class Range extends RECORD {
		public final LONG_CARDINAL low = mkLONG_CARDINAL();
		public final LONG_CARDINAL high = mkLONG_CARDINAL();
		private Range() { }
		public static Range make() { return new Range(); }
	}
	
	public static class BigEndianLong extends RECORD {
		public final INTEGER high = mkINTEGER();
		public final CARDINAL low = mkCARDINAL();
		
		public void set(long value) {
			this.high.set((int)(value >> 16));
			this.low.set((int)(value & 0xFFFF));
		}
		
		public long get() {
			return (this.high.get() << 16) | (this.low.get() & 0xFFFF);
		}
		
		private BigEndianLong() {}
		public static BigEndianLong make() { return new BigEndianLong(); }
	}
	
	public enum WhichMessage { thisMessage, nextMessage };
	public static final EnumMaker<WhichMessage> mkWhichMessage = buildEnum(WhichMessage.class).get();
	
	
	/*
	 * error enums and errors
	 * 
	 * (ACCESS.PROBLEM (ENUMERATION (AccessRightsInsufficient 0)
     *                        (AccessRightsIndeterminate 1)
     *                        (NoSuchInbasket 2)
     *                        (InbasketIndeterminate 3)
     *                        (WrongService 4)))
     * (CONNECTION.PROBLEM (FILING . CONNECTION.PROBLEM))
     * (SERVICE.PROBLEM (ENUMERATION (CannotAuthenticate 0)
     *                         (ServiceFull 1)
     *                         (ServiceUnavailable 2)))
     * (TRANSFER.PROBLEM (ENUMERATION (Aborted 0)))
     * (SESSION.PROBLEM (ENUMERATION (TokenInvalid 0)))
     * (OTHER.PROBLEM (ENUMERATION (USE.COURIER 0)
     *                       (MalformedMessage 1)
     *                       (InvalidOperation 2)
     *                       (LAST 65535)))
     * (INDEX.PROBLEM (ENUMERATION (InvalidIndex 0)
     *                       (InvalidBodyPartIndex 1]
     *                       
     *  ERRORS
     *   ((ACCESS.ERROR 0 (ACCESS.PROBLEM))
     *    (AUTHENTICATION.ERROR 1 ((AUTHENTICATION . PROBLEM)))
     *    (SESSION.ERROR 5 (SESSION.PROBLEM))
     *    (SERVICE.ERROR 6 (SERVICE.PROBLEM))
     *    (TRANSFER.ERROR 7 (TRANSFER.PROBLEM))
     *    (OTHER.ERROR 8 (OTHER.PROBLEM))
     *    (INDEX.ERROR 9 (INDEX.PROBLEM))
     *    (INBASKET.IN.USE 10 (NAME)))
	 */
	
	// (ACCESS.ERROR 0 (ACCESS.PROBLEM))
	public enum AccessProblem {
		accessRightslnsufficient, accessRightslndeterminate, noSuchInbasket, inbasketIndeterminate, wrongService
	};
	public static final EnumMaker<AccessProblem> mkAccessProblem = buildEnum(AccessProblem.class).get();
	public static class AccessErrorRecord extends ErrorRECORD {
		public final ENUM<AccessProblem> problem = mkENUM(mkAccessProblem);
		@Override public int getErrorCode() { return 0; }
		public AccessErrorRecord(AccessProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<AccessErrorRecord> AccessError = mkERROR(AccessErrorRecord.class);
	
	// (AUTHENTICATION.ERROR 1 ((AUTHENTICATION . PROBLEM)))
	// => MailingCommon.AuthenticationError
	public final ERROR<AuthenticationErrorRecord> AuthenticationError = mkERROR(AuthenticationErrorRecord.class);
	
	// (SESSION.ERROR 5 (SESSION.PROBLEM))
	public enum SessionProblem { handleInvalid };
	public static final EnumMaker<SessionProblem> mkSessionProblem = buildEnum(SessionProblem.class).get();
	public static class SessionErrorRecord extends ErrorRECORD {
		public final ENUM<SessionProblem> problem = mkENUM(mkSessionProblem);
		@Override public int getErrorCode() { return 5; }
		public SessionErrorRecord(SessionProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<SessionErrorRecord> SessionError = mkERROR(SessionErrorRecord.class);
	
	// (SERVICE.ERROR 6 (SERVICE.PROBLEM))
	public enum ServiceProblem { cannotAuthenticate, serviceFull, serviceUnavailable };
	public static final EnumMaker<ServiceProblem> mkServiceProblem = buildEnum(ServiceProblem.class).get();
	public static class ServiceErrorRecord extends ErrorRECORD {
		public final ENUM<ServiceProblem> problem = mkENUM(mkServiceProblem);
		@Override public int getErrorCode() { return 6; }
		public ServiceErrorRecord(ServiceProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<ServiceErrorRecord> ServiceError = mkERROR(ServiceErrorRecord.class);
	
	// (TRANSFER.ERROR 7 (TRANSFER.PROBLEM))
	public enum TransferProblem { aborted };
	public static final EnumMaker<TransferProblem> mkTransferProblem = buildEnum(TransferProblem.class).get();
	public static class TransferErrorRecord extends ErrorRECORD {
		public final ENUM<TransferProblem> problem = mkENUM(mkTransferProblem);
		@Override public int getErrorCode() { return 7; }
		public TransferErrorRecord(TransferProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<TransferErrorRecord> TransferError = mkERROR(TransferErrorRecord.class);
	
	// (OTHER.ERROR 8 (OTHER.PROBLEM))
	public enum OtherProblem { useCourier, malformedMessage, invalidOperation };
	public static final EnumMaker<OtherProblem> mkOtherProblem = buildEnum(OtherProblem.class).get();
	public static class OtherErrorRecord extends ErrorRECORD {
		public final ENUM<OtherProblem> problem = mkENUM(mkOtherProblem);
		@Override public int getErrorCode() { return 8; }
		public OtherErrorRecord(OtherProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<OtherErrorRecord> OtherError = mkERROR(OtherErrorRecord.class);
	
	// (INDEX.ERROR 9 (INDEX.PROBLEM))
	public enum IndexProblem { invalidIndex, invalidBodyPartIndex };
	public static final EnumMaker<IndexProblem> mkIndexProblem = buildEnum(IndexProblem.class).get();
	public static class IndexErrorRecord extends ErrorRECORD {
		public final ENUM<IndexProblem> problem = mkENUM(mkIndexProblem);
		@Override public int getErrorCode() { return 8; }
		public IndexErrorRecord(IndexProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<IndexErrorRecord> IndexError = mkERROR(IndexErrorRecord.class);
	
	
	/*
	 * getNextMail
	 *   = procedure 2 ::
	 *   
	 * (RETRIEVE.ENVELOPES 2 (INDEX WHICH.MESSAGE SESSION)
     *        RETURNS
     *        (ENVELOPE STATUS INDEX)
     *        REPORTS
     *        (AUTHENTICATION.ERROR INDEX.ERROR SERVICE.ERROR OTHER.ERROR))
	 */
	public static class GetNextMailParams extends RECORD {
		public final LONG_CARDINAL index = mkLONG_CARDINAL();
		public final ENUM<WhichMessage> which = mkENUM(mkWhichMessage);
		public final UNSPECIFIED2 sessionId = mkUNSPECIFIED2();
		public final Verifier verifier = mkMember(Verifier::make);
		
		private GetNextMailParams() {}
		public static GetNextMailParams make() { return new GetNextMailParams(); }
	}
	public static class GetNextMailResults extends RECORD {
		public final AttributeSequence mailInfos = mkMember(AttributeSequence::make);
		public final UNSPECIFIED2 unknown0 = mkUNSPECIFIED2();
		public final SEQUENCE<CARDINAL> unknownSeq = mkSEQUENCE(CARDINAL::make); // must come in multiples of 4
		public final LONG_CARDINAL uniqueMailNo = mkLONG_CARDINAL();
		
		private GetNextMailResults() {}
		public static GetNextMailResults make() { return new GetNextMailResults(); }
	}
	public final PROC<GetNextMailParams,GetNextMailResults> GetNextMail = mkPROC(
			"GetNextMail",
			2,
			GetNextMailParams::make,
			GetNextMailResults::make
			// there are probably some ERRORs (invalid session, ...), but the definitions are not known...
			);
	
	//
	// constants and types for mailInfos in results of GetNextMail
	//
	
	public static final long miMailServer = 0x0000L;
	public static class MiMailServer extends RECORD {
		public final CARDINAL unknown0 = mkCARDINAL();
		public final ThreePartName name = mkRECORD(ThreePartName::make);
		public final BigEndianLong mailTs = mkRECORD(BigEndianLong::make);
		
		private MiMailServer() {}
		public static MiMailServer make() { return new MiMailServer(); }
	}
	
	public static final long miMessageId = 0x0001L;
	// type: MailingCommon.MessageID
	
	public static final long miWhatever = 0x0002L;
	public static class MiWhatever extends RECORD {
		public final BigEndianLong value = mkRECORD(BigEndianLong::make);
		
		private MiWhatever() { this.value.set(4L); }
		public static MiWhatever make() { return new MiWhatever(); }
	}
	
	public static final long miMailparts = 0x0003L;
	public static class MiMailPart extends RECORD {
		public final LONG_CARDINAL mailPartType = mkLONG_CARDINAL();
		public final LONG_CARDINAL mailPartLength = mkLONG_CARDINAL();
		
		private MiMailPart() {}
		public static MiMailPart make() { return new MiMailPart(); }
	}
	public static class MiMailParts extends SEQUENCE<MiMailPart> {
		private MiMailParts() { super(MiMailPart::make); }
		public static MiMailParts make() { return new MiMailParts(); }
	}
	
	public static final long miTotalPartsLength = 0x0004L;
	public static class MiTotalPartsLength extends RECORD {
		public final BigEndianLong totalLength = mkRECORD(BigEndianLong::make);
		
		private MiTotalPartsLength() { this.totalLength.set(4L); }
		public static MiTotalPartsLength make() { return new MiTotalPartsLength(); }
	}
	

	public static final long miSender0 = 0x0005L;
	public static final long miSender1 = 0x0007L;
	public static class MiSender extends RECORD {
		public final CARDINAL role = mkCARDINAL();
		public final ThreePartName senderName = mkRECORD(ThreePartName::make);
		
		private MiSender() {}
		public static MiSender make() { return new MiSender(); }
	}
	
	/*
	 * changeBodyPartsStatus
	 *   = procedure 3 ::
	 *   
	 *   (CHANGE.BODY.PARTS.STATUS 3 (INDEX BODY.PART.STATUS.CHANGE.SEQUENCE SESSION)
     *        RETURNS
     *        (BOOLEAN)
     *        REPORTS
     *        (AUTHENTICATION.ERROR INDEX.ERROR SESSION.ERROR SERVICE.ERROR OTHER.ERROR))
	 */
	public static class BodyPartChangeStatus extends RECORD {
		public final CARDINAL mailPartIndex = mkCARDINAL();
		public final BOOLEAN keepMailPart = mkBOOLEAN();
		
		private BodyPartChangeStatus() {}
		public static BodyPartChangeStatus make() { return new BodyPartChangeStatus(); }
	}
	public static class ChangeBodyPartStatusParams extends RECORD {
		public final LONG_CARDINAL uniqueMailNo = mkLONG_CARDINAL();
		public final SEQUENCE<BodyPartChangeStatus> mailPartHandling = mkSEQUENCE(BodyPartChangeStatus::make);
		public final UNSPECIFIED2 sessionId = mkUNSPECIFIED2();
		public final Verifier verifier = mkMember(Verifier::make);
		
		private ChangeBodyPartStatusParams() {}
		public static ChangeBodyPartStatusParams make() { return new ChangeBodyPartStatusParams(); }
	}
	public static class ChangeBodyPartStatusResults extends RECORD {
		public final BOOLEAN deleted  = mkBOOLEAN();
		
		private ChangeBodyPartStatusResults() {}
		public static ChangeBodyPartStatusResults make() { return new ChangeBodyPartStatusResults(); }
	}
	public final PROC<ChangeBodyPartStatusParams,ChangeBodyPartStatusResults> ChangeBodyPartStatus = mkPROC(
			"ChangeBodyPartStatus",
			3,
			ChangeBodyPartStatusParams::make,
			ChangeBodyPartStatusResults::make,
			AuthenticationError,
			IndexError,
			SessionError,
			ServiceError,
			OtherError
			);
	
	/*
	 * logoff
	 *   = procedure 4 ::
	 *   
	 *   (LOGOFF 4 (SESSION)
     *        RETURNS NIL REPORTS (AUTHENTICATION.ERROR SESSION.ERROR OTHER.ERROR))
	 */
	public static final class LogoffParams extends RECORD {
		public final UNSPECIFIED2 sessionId = mkUNSPECIFIED2();
		public final Verifier verifier = mkMember(Verifier::make);
		
		private LogoffParams() {}
		public static LogoffParams make() { return new LogoffParams(); }
	}
	public final PROC<LogoffParams,RECORD> Logoff = mkPROC(
			"Logoff",
			4,
			LogoffParams::make,
			RECORD::empty,
			AuthenticationError,
			SessionError,
			OtherError
			);
	

	/*
	 * logon
	 *  = procedure 5 ::
	 *  
	 *  (LOGON 5 (NAME CREDENTIALS VERIFIER)
     *        RETURNS
     *        (SESSION STATE ANCHOR)
     *        REPORTS
     *        (ACCESS.ERROR AUTHENTICATION.ERROR INBASKET.IN.USE SERVICE.ERROR OTHER.ERROR))
	 */
	public static class LogonParams extends RECORD {
		public final ThreePartName mailboxName = mkRECORD(ThreePartName::make);
		public final Credentials credentials = mkRECORD(Credentials::make);
		public final Verifier verifier = mkMember(Verifier::make);
		
		private LogonParams() {}
		public static LogonParams make() { return new LogonParams(); }
	}
	public static class LogonResults extends RECORD {
		public final UNSPECIFIED2 sessionId = mkUNSPECIFIED2();
		public final Verifier verifier = mkMember(Verifier::make);
		public final CARDINAL newCount = mkCARDINAL();
		public final CARDINAL totalCount = mkCARDINAL();
		public final UNSPECIFIED3 machineId = mkMember(UNSPECIFIED3::make);
		public final CARDINAL constant0 = mkCARDINAL();
		public final CARDINAL constant1 = mkCARDINAL();
		
		private LogonResults() {       // initialize "constants" (same in several different calls...)
			this.constant0.set(0x3C2E); // whyever these...
			this.constant1.set(0xA4C9); // ... values are necessary
		}
		public static LogonResults make() { return new LogonResults(); }
	}
	public final PROC<LogonParams,LogonResults> Logon = mkPROC(
			"Logon",
			5,
			LogonParams::make,
			LogonResults::make,
			AccessError,
			AuthenticationError,
			// InbasketInUse, // never reported?
			ServiceError,
			OtherError
			);

	
	/*
	 * mailPoll (called through a single PEX packet == expedited courier)
	 *  = procedure 7 ::
	 *  
	 *  (MAILPOLL 7 (NAME CREDENTIALS VERIFIER)
     *        RETURNS
     *        (STATE)
     *        REPORTS
     *        (ACCESS.ERROR AUTHENTICATION.ERROR SERVICE.ERROR OTHER.ERROR))
	 */
	public static class MailPollParams extends RECORD {
		public final ThreePartName mailboxName = mkRECORD(ThreePartName::make);
		public final Credentials credentials = mkRECORD(Credentials::make);
		public final Verifier verifier = mkMember(Verifier::make);
		
		private MailPollParams() {}
		public static MailPollParams make() { return new MailPollParams(); }
	}
	public static class MailPollResults extends RECORD {
		public final CARDINAL lastIndex = mkCARDINAL();
		public final CARDINAL newCount = mkCARDINAL();
		
		private MailPollResults() {}
		public static MailPollResults make() { return new MailPollResults(); }
	}
	public final PROC<MailPollParams,MailPollResults> MailPoll = mkPROC(
			"MailPoll",
			7,
			MailPollParams::make,
			MailPollResults::make,
			AccessError,
			AuthenticationError,
			ServiceError,
			OtherError
			);	


	/*
	 * retrieveBodyParts (the requested 1..n mail part content(s) are sent via bulk-data transfer to the invoker)
	 *   = procedure 8 ::
	 * 
	 *   (RETRIEVE.BODY.PARTS 8 (INDEX BODY.PART.SEQUENCE BULK.DATA.SINK SESSION)
     *        RETURNS NIL REPORTS (AUTHENTICATION.ERROR INDEX.ERROR SESSION.ERROR SERVICE.ERROR 
     *                                   OTHER.ERROR TRANSFER.ERROR))
	 */
	public static class RetrieveBodyPartsParams extends RECORD {
		public final LONG_CARDINAL uniqueMailNo = mkLONG_CARDINAL();
		public final SEQUENCE<CARDINAL> mailPartIndices = mkSEQUENCE(CARDINAL::make);
		public final BulkData1.Sink content = mkRECORD(BulkData1.Sink::make);
		public final UNSPECIFIED2 sessionId = mkUNSPECIFIED2();
		public final Verifier verifier = mkMember(Verifier::make);
		
		private RetrieveBodyPartsParams() {}
		public static RetrieveBodyPartsParams make() { return new RetrieveBodyPartsParams(); }
	}
	public final PROC<RetrieveBodyPartsParams,RECORD> RetrieveBodyParts = mkPROC(
			"RetrieveBodyParts",
			8,
			RetrieveBodyPartsParams::make,
			RECORD::empty,
			AuthenticationError,
			IndexError,
			SessionError,
			ServiceError,
			OtherError,
			TransferError
			);
	
	/*
	 * mailCheck
	 *  = procedure 6 ::
	 *  
	 *  (MAILCHECK 6 (SESSION)
     *        RETURNS
     *        (STATE)
     *        REPORTS
     *        (AUTHENTICATION.ERROR SESSION.ERROR SERVICE.ERROR OTHER.ERROR))
	 */
	public static class MailCheckParams extends RECORD {
		public final UNSPECIFIED2 sessionId = mkUNSPECIFIED2();
		public final Verifier verifier = mkMember(Verifier::make);
		private MailCheckParams() { }
		public static MailCheckParams make() { return new MailCheckParams(); }
	}
	public static class MailCheckResults extends RECORD {
		public final CARDINAL newCount = mkCARDINAL();
		public final CARDINAL totalCount = mkCARDINAL();
		private MailCheckResults() { }
		public static MailCheckResults make() { return new MailCheckResults(); }
	}
	public final PROC<MailCheckParams,MailCheckResults> MailCheck = mkPROC(
			"MailCheck",
			6,
			MailCheckParams::make,
			MailCheckResults::make,
			AuthenticationError,
			SessionError,
			ServiceError,
			OtherError
			);
	
	/*
	 * delete
	 *  = procedure 1 ::
	 *  
	 *  (DELETE 1 (RANGE SESSION)
     *        RETURNS NIL REPORTS (AUTHENTICATION.ERROR SESSION.ERROR SERVICE.ERROR OTHER.ERROR))
	 */
	public static class DeleteParams extends RECORD {
		public final Range range = mkRECORD(Range::make);
		public final UNSPECIFIED2 sessionId = mkUNSPECIFIED2();
		public final Verifier verifier = mkMember(Verifier::make);
		private DeleteParams() { }
		public static DeleteParams make() { return new DeleteParams(); }
	}
	public final PROC<DeleteParams,RECORD> Delete = mkPROC(
			"Delete",
			1,
			DeleteParams::make,
			RECORD::empty,
			SessionError,
			ServiceError,
			OtherError
			);
	
}
