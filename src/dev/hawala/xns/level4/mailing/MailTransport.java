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

import dev.hawala.xns.level3.courier.BOOLEAN;
import dev.hawala.xns.level3.courier.CARDINAL;
import dev.hawala.xns.level3.courier.ENUM;
import dev.hawala.xns.level3.courier.ErrorRECORD;
import dev.hawala.xns.level3.courier.LONG_CARDINAL;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level4.common.AuthChsCommon.Name;
import dev.hawala.xns.level4.common.AuthChsCommon.NetworkAddressList;
import dev.hawala.xns.level4.common.AuthChsCommon.Verifier;
import dev.hawala.xns.level4.common.BulkData1;

/**
 * Definition of the subset for the MailTransport Courier program
 * used by ViewPoint and XDE for sending mails.
 * <p>
 * The missing (unknown) Courier items for MailTransport seem to be used
 * only for the inter-mail-service communication (mail forwarding or the
 * like) by real Xerox Mail and Clearinghouse servers, which is not relevant
 * here as Dodo has exactly one central mail server.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2020)
 */
public class MailTransport extends MailingCommon {

	public static final int PROGRAM = 17;
	public static final int VERSION = 4;

	@Override
	public int getProgramNumber() { return PROGRAM; }

	@Override
	public int getVersionNumber() { return VERSION; }
	
	/*
	 * errors for MailTransport
	 * 
	 * courier-error-numbers:
     *  0 = access
     *  1 = authentication
     *  2 = connection
     *  3 = invalidRecipients
     *  4 = service
     *  5 = transfer
     *  6 = undefined
     *  7 = session
	 */
	
	public static class InvalidRecipientsErrorRecord extends ErrorRECORD {
		public final Undeliverables nameList = mkMember(Undeliverables::make);
		@Override public int getErrorCode() { return 3; }
		public InvalidRecipientsErrorRecord add(UndeliveredNameType reason, Name name) {
			UndeliveredName undlv = UndeliveredName.make();
			undlv.reason.set(reason);
			undlv.name.from(name);
			this.nameList.add(undlv);
			return this;
		}
	}
	public final ERROR<InvalidRecipientsErrorRecord> InvalidRecipientsError = mkERROR(InvalidRecipientsErrorRecord.class);
	
	public static class ServiceErrorRecord extends ErrorRECORD {
		public final ENUM<ServiceProblem> problem = mkENUM(mkServiceProblem);
		@Override public int getErrorCode() { return 4; }
		public ServiceErrorRecord(ServiceProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<ServiceErrorRecord> ServiceError = mkERROR(ServiceErrorRecord.class);
	
	public static class TransferErrorRecord extends ErrorRECORD {
		public final ENUM<TransferProblem> problem = mkENUM(mkTransferProblem);
		@Override public int getErrorCode() { return 5; }
		public TransferErrorRecord(TransferProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<TransferErrorRecord> TransferError = mkERROR(TransferErrorRecord.class);
	
	public static class UndefinedErrorRecord extends ErrorRECORD {
		public final CARDINAL problem = mkCARDINAL();
		@Override public int getErrorCode() { return 6; }
		public UndefinedErrorRecord(int problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<UndefinedErrorRecord> UndefinedError = mkERROR(UndefinedErrorRecord.class);
	
	public static class SessionErrorRecord extends ErrorRECORD {
		public final ENUM<SessionProblem> problem = mkENUM(mkSessionProblem);
		@Override public int getErrorCode() { return 7; }
		public SessionErrorRecord(SessionProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<SessionErrorRecord> SessionError = mkERROR(SessionErrorRecord.class);
	
	
	/*
	 * procedures
	 */
	
	/*
	 * serverPoll
	 *  = procedure 0
	 */
	public static class ServerPollParams extends RECORD {
		public final AuthPair authPair = mkRECORD(AuthPair::make);
		
		private ServerPollParams() { }
		public static ServerPollParams make() { return new ServerPollParams(); }
	}
	public static class ServerPollResults extends RECORD {
		public final CARDINAL willingness = mkCARDINAL();
		public final NetworkAddressList address = mkMember(NetworkAddressList::make);
		public final Verifier returnVerifier = mkMember(Verifier::make);
		public final Name serverName = mkRECORD(Name::make);
		
		private ServerPollResults() { }
		public static ServerPollResults make() { return new ServerPollResults(); }
		
	}
	public static final int mostWilling=1;
	public static final int leastWilling=10;
	
	public final PROC<ServerPollParams,ServerPollResults> ServerPoll = mkPROC(
						"ServerPoll",
						0,
						ServerPollParams::make,
						ServerPollResults::make,
						// defined errors are unknown,
						// -> so we return the "usual" authentication error in case of invalid credentials
						// -> and we always answer with our identification, producing no further errors...
						AuthenticationError
						);
	
	/*
	 * post
	 *  = procedure 1
	 */
	public static class PostParams extends RECORD {
		public final AuthPair authPair = mkRECORD(AuthPair::make);
		public final NameList recipients = mkMember(NameList::make);
		//public final Name returnToName = mkRECORD(Name::make);
		public final BOOLEAN postIfInvalidNames = mkBOOLEAN();
		public final BOOLEAN allowDLRecipients = mkBOOLEAN();
		public final LONG_CARDINAL contentsType = mkLONG_CARDINAL();
		public final EncodedList envOptions = mkMember(EncodedList::make);
		public final BulkData1.Source content = mkRECORD(BulkData1.Source::make);
		
		private PostParams() { }
		public static PostParams make() { return new PostParams(); }
	}
	public static class PostResults extends RECORD {
		public final Undeliverables invalidNames = mkMember(Undeliverables::make);
		public final MessageID msgID = mkMember(MessageID::make);
		
		private PostResults() { }
		public static PostResults make() { return new PostResults(); }
	}
	
	public final PROC<PostParams,PostResults> Post = mkPROC(
						"Post",
						1,
						PostParams::make,
						PostResults::make,
						InvalidRecipientsError,
						AuthenticationError, ConnectionError, /* (stub only) LocationError, */ ServiceError, TransferError
						);

}
