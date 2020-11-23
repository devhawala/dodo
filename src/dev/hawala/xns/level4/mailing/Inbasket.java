/*
Copyright (c) 2020, Dr. Hans-Walter Latz
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

import dev.hawala.xns.level3.courier.ARRAY;
import dev.hawala.xns.level3.courier.BOOLEAN;
import dev.hawala.xns.level3.courier.CARDINAL;
import dev.hawala.xns.level3.courier.ENUM;
import dev.hawala.xns.level3.courier.ErrorRECORD;
import dev.hawala.xns.level3.courier.LONG_CARDINAL;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.UNSPECIFIED;
import dev.hawala.xns.level3.courier.UNSPECIFIED2;
import dev.hawala.xns.level4.common.AuthChsCommon.Name;
import dev.hawala.xns.level4.common.AuthChsCommon.Verifier;
import dev.hawala.xns.level4.common.BulkData1;

/**
 * Definition of the Inbasket Courier program for accessing user
 * mailboxes by mail agents, defining the subset used by ViewPoint/XDE.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2020)
 */
public class Inbasket extends MailingCommon {

	public static final int PROGRAM = 18;
	public static final int VERSION = 1;

	@Override
	public int getProgramNumber() { return PROGRAM; }

	@Override
	public int getVersionNumber() { return VERSION; }
	
	/*
	 * data types
	 */
	
	// access information for Logon to Inbasket
	public static class CredsAndMbx extends RECORD {
		public final AuthPair creds = mkRECORD(AuthPair::make);
		public final Name name = mkRECORD(Name::make);
		
		private CredsAndMbx() { }
		public static CredsAndMbx make() { return new CredsAndMbx(); }
	}
	
	// session data for an Inbasked session (Logon result)
	public static class ProtocolSession extends RECORD {
		public final UNSPECIFIED2 token = mkUNSPECIFIED2();
		public final Verifier verifier = mkMember(Verifier::make);
		
		private ProtocolSession() { }
		public static ProtocolSession make() { return new ProtocolSession(); }
	}
	
	// status information for an Inbasket
	public static class State extends RECORD {
		public final CARDINAL lastIndex = mkCARDINAL(); // valid indexes: 1..lastIndex
		public final CARDINAL newCount = mkCARDINAL();
		public final BOOLEAN isPrimary = mkBOOLEAN();
		public final BOOLEAN isPrimaryUp = mkBOOLEAN();
		
		private State(boolean isPrimary, boolean isPrimaryUp) {
			this.isPrimary.set(isPrimary);
			this.isPrimaryUp.set(isPrimaryUp);
		}
		public static State make() { return new State(true, true); }
		
	}
	public static final int nullIndex = 0; // the 'no index' resp. 'no mail found' index value
	
	// index range e.g. for deleting / listing mails
	public static class IndexRange extends RECORD {
		public final CARDINAL first = mkCARDINAL();
		public final CARDINAL last = mkCARDINAL();
		
		private IndexRange() { }
		public static IndexRange make() { return new IndexRange(); }
	}
	
	// cache verifier (logoff -> next logon)
	public static class CacheVerifier extends ARRAY<UNSPECIFIED> {
		private CacheVerifier() { super(4, UNSPECIFIED::make); }
		public static CacheVerifier make() { return new CacheVerifier(); }
	}
	
	// cache status enum
	public enum CacheStatus { correct, incomplete, invalid};
	public static final EnumMaker<CacheStatus> mkCacheStatus = buildEnum(CacheStatus.class).get();
	
	/*
	 * errors for Inbasket
	 * 
	 * courier-error-numbers:
     *  0 = access
     *  1 = authentication
     *  2 = connection
     *  3 = contentsType
     *  4 = index
     *  5 = session
     *  6 = service
     *  7 = transfer
     *  8 = undefined
	 */
	
	public static class ContentsTypeErrorRecord extends ErrorRECORD {
		public final LONG_CARDINAL correctType = mkLONG_CARDINAL();
		@Override public int getErrorCode() { return 3; }
		public ContentsTypeErrorRecord(long correctType) {
			this.correctType.set(correctType);
		}
	}
	public final ERROR<ContentsTypeErrorRecord> ContentsTypeError = mkERROR(ContentsTypeErrorRecord.class);
	
	public static class IndexErrorRecord extends ErrorRECORD {
		public final CARDINAL badIndex = mkCARDINAL();
		@Override public int getErrorCode() { return 4; }
		public IndexErrorRecord(int badIndex) {
			this.badIndex.set(badIndex);
		}
	}
	public final ERROR<IndexErrorRecord> IndexError = mkERROR(IndexErrorRecord.class);
	
	public static class SessionErrorRecord extends ErrorRECORD {
		public final ENUM<SessionProblem> problem = mkENUM(mkSessionProblem);
		@Override public int getErrorCode() { return 5; }
		public SessionErrorRecord(SessionProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<SessionErrorRecord> SessionError = mkERROR(SessionErrorRecord.class);
	
	public static class ServiceErrorRecord extends ErrorRECORD {
		public final ENUM<ServiceProblem> problem = mkENUM(mkServiceProblem);
		@Override public int getErrorCode() { return 6; }
		public ServiceErrorRecord(ServiceProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<ServiceErrorRecord> ServiceError = mkERROR(ServiceErrorRecord.class);
	
	public static class TransferErrorRecord extends ErrorRECORD {
		public final ENUM<TransferProblem> problem = mkENUM(mkTransferProblem);
		@Override public int getErrorCode() { return 7; }
		public TransferErrorRecord(TransferProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<TransferErrorRecord> TransferError = mkERROR(TransferErrorRecord.class);
	
	public static class UndefinedErrorRecord extends ErrorRECORD {
		public final CARDINAL problem = mkCARDINAL();
		@Override public int getErrorCode() { return 8; }
		public UndefinedErrorRecord(int problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<UndefinedErrorRecord> UndefinedError = mkERROR(UndefinedErrorRecord.class);
	
	
	/*
	 * procedures
	 */
	
	/*
	 * changeStatus
	 * 	= procedure 0
	 */
	// this Courier procedure seems not to be used by VP or XDE
	
	
	/*
	 * delete
	 * 	= procedure 1
	 */
	public static class DeleteParams extends RECORD {
		public final ProtocolSession session = mkRECORD(ProtocolSession::make);
		public final IndexRange range = mkRECORD(IndexRange::make);
		
		private DeleteParams() { }
		public static DeleteParams make() { return new DeleteParams(); }
	}
	public final PROC<DeleteParams,RECORD> Delete = mkPROC(
						"Delete",
						1,
						DeleteParams::make,
						RECORD::empty,
						AuthenticationError, SessionError, ServiceError,
						IndexError
						);
	
	/*
	 * list
	 * 	= procedure 2
	 */
	public static class ListParams extends RECORD {
		public final ProtocolSession session = mkRECORD(ProtocolSession::make);
		public final IndexRange range = mkRECORD(IndexRange::make);
		public final EncodedSelections selections = mkRECORD(EncodedSelections::make);
		public final BulkData1.Sink listing = mkRECORD(BulkData1.Sink::make);
		
		public ListParams() {}
		public static ListParams make() { return new ListParams(); }
	}
	public final PROC<ListParams,RECORD> List = mkPROC(
						"List",
						2,
						ListParams::make,
						RECORD::empty,
						AuthenticationError, SessionError, ServiceError, IndexError
						);
	
	
	/*
	 * locate
	 * 	= procedure 3
	 */
	public static class LocateParams extends RECORD {
		public final ProtocolSession session = mkRECORD(ProtocolSession::make);
		public final ENUM<MessageStatus> status = mkENUM(mkMessageStatus::make);
		
		private LocateParams() { }
		public static LocateParams make() { return new LocateParams(); }
	}
	public static class LocateResults extends RECORD {
		public final CARDINAL index = mkCARDINAL();
		
		private LocateResults() { }
		public static LocateResults make() { return new LocateResults(); }
	}
	public final PROC<LocateParams,LocateResults> Locate = mkPROC(
						"Locate",
						3,
						LocateParams::make,
						LocateResults::make,
						AuthenticationError, SessionError, ServiceError
						);
	
	
	/*
	 * logoff
	 * 	= procedure 4
	 */
	public static class LogoffParams extends RECORD {
		public final ProtocolSession session = mkRECORD(ProtocolSession::make);
		
		private LogoffParams() { }
		public static LogoffParams make() { return new LogoffParams(); }
	}
	public static class LogoffResults extends RECORD {
		public final CacheVerifier cacheVerifier = mkMember(CacheVerifier::make);
		
		private LogoffResults() { }
		public static LogoffResults make() { return new LogoffResults(); }
	}
	public final PROC<LogoffParams,LogoffResults> Logoff = mkPROC(
						"Logoff",
						4,
						LogoffParams::make,
						LogoffResults::make,
						SessionError
						);
	
	/*
	 * logon
	 * 	= procedure 5
	 */
	public static class LogonParams extends RECORD {
		public final CredsAndMbx mbx = mkRECORD(CredsAndMbx::make);
		public final CacheVerifier cacheCheck = mkMember(CacheVerifier::make);
		public final BOOLEAN allowSharing = mkBOOLEAN();
		
		private LogonParams() { }
		public static LogonParams make() { return new LogonParams(); }
	}
	public static class LogonResults extends RECORD {
		public final ProtocolSession session = mkRECORD(ProtocolSession::make);
		public final ENUM<CacheStatus> cacheStatus = mkENUM(mkCacheStatus);
		
		private LogonResults() { }
		public static LogonResults make() { return new LogonResults(); }
	}
	public final PROC<LogonParams,LogonResults> Logon = mkPROC(
						"Logon",
						5,
						LogonParams::make,
						LogonResults::make,
						AccessError, AuthenticationError, /* (stub only) LocationError,*/ ServiceError
						);
	
	/*
	 * mailCheck
	 * 	= procedure 6
	 */
	public static class MailCheckParams extends RECORD {
		public final ProtocolSession session = mkRECORD(ProtocolSession::make);
		
		private MailCheckParams() { }
		public static MailCheckParams make() { return new MailCheckParams(); }
	}
	public static class MailCheckResults extends RECORD {
		public final State checkState = mkRECORD(State::make);
		public final CARDINAL checkAgainWithin = mkCARDINAL();
		
		private MailCheckResults() { }
		public static MailCheckResults make() { return new MailCheckResults(); }
	}
	public final PROC<MailCheckParams,MailCheckResults> MailCheck = mkPROC(
						"Mailcheck",
						6,
						MailCheckParams::make,
						MailCheckResults::make,
						AuthenticationError, SessionError, ServiceError
						);
	
	/*
	 * inbasketPoll
	 * 	= procedure 7
	 */
	public static class InbasketPollParams extends RECORD {
		public final CredsAndMbx mbx = mkRECORD(CredsAndMbx::make);
		
		private InbasketPollParams() { }
		public static InbasketPollParams make() { return new InbasketPollParams(); }
	}
	public static class InbasketPollResults extends RECORD {
		public final State pollState = mkRECORD(State::make);
		
		private InbasketPollResults() { }
		public static InbasketPollResults make() { return new InbasketPollResults(); }
	}
	public final PROC<InbasketPollParams,InbasketPollResults> InbasketPoll = mkPROC(
						"InbasketPoll",
						7,
						InbasketPollParams::make,
						InbasketPollResults::make,
						AccessError, AuthenticationError, /* (stub only) LocationError,*/ ServiceError
						);
	
	/*
	 * retrieve
	 * 	= procedure 8
	 */
	public static class RetrieveParams extends RECORD {
		public final ProtocolSession session = mkRECORD(ProtocolSession::make);
		public final CARDINAL message = mkCARDINAL();
		public final LONG_CARDINAL expectedContentsType = mkLONG_CARDINAL();
		public final BulkData1.Sink contents = mkRECORD(BulkData1.Sink::make);
		
		private RetrieveParams() { }
		public static RetrieveParams make() { return new RetrieveParams(); }
	}
	public static class RetrieveResults extends RECORD {
		public final EncodedList transportEnv = mkMember(EncodedList::make);
		public final EncodedList inbasketEnv = mkMember(EncodedList::make);
		
		private RetrieveResults() { }
		public static RetrieveResults make() { return new RetrieveResults(); }
	}
	public final PROC<RetrieveParams,RetrieveResults> Retrieve = mkPROC(
						"Retrieve",
						8,
						RetrieveParams::make,
						RetrieveResults::make,
						AuthenticationError, ConnectionError, SessionError, ServiceError, TransferError,
						ContentsTypeError, IndexError
						);

}
