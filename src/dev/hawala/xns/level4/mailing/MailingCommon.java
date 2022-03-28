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
import dev.hawala.xns.level3.courier.CrEnum;
import dev.hawala.xns.level3.courier.CrProgram;
import dev.hawala.xns.level3.courier.ENUM;
import dev.hawala.xns.level3.courier.ErrorRECORD;
import dev.hawala.xns.level3.courier.LONG_CARDINAL;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.SEQUENCE;
import dev.hawala.xns.level3.courier.UNSPECIFIED;
import dev.hawala.xns.level4.common.AuthChsCommon.Credentials;
import dev.hawala.xns.level4.common.AuthChsCommon.Name;
import dev.hawala.xns.level4.common.AuthChsCommon.Verifier;
import dev.hawala.xns.level4.common.Time2.Time;
import dev.hawala.xns.level4.filing.FilingCommon.Attribute;

/**
 * Definition of the Courier data structures common to the mailing
 * Courier programs, which are defined in subclasses to this module:
 * <ul>
 * <li>MailTransport (program 17, version 4) and</li>
 * <li>Inbasket (program 18, version 1).</li>
 * </ul>
 * <p>
 * As no formal Courier definitions file (.cr or .courier files) are available
 * for the mailing protocols (and possibly never have existed), the data
 * structures were reconstructed the Services Programmers Manual 8.0 (available
 * at Bitsavers) and some corresponding Mesa interface files found on XDE 5.0
 * floppy IMD files for 8010 (also available at Bitsavers), along with many
 * experiments and packet level analysis sessions.
 * <br/>
 * This applies to this module defining the base structures and the 2 Courier
 * programs mentioned above. So the Courier structures may be inaccurate
 * or incomplete in some cases.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2020)
 */
public abstract class MailingCommon extends CrProgram {
	
	/*
	 * common data types for MailTransport and Inbasket Courier programs
	 */
	
	public static class NameList extends SEQUENCE<Name> {
		private NameList() { super(Name::make); }
		public static NameList make() { return new NameList(); }
		
		public void addDistinct(Name name) {
			String nameLcFqn = name.getLcFqn();
			for (int i = 0; i < this.size(); i++) {
				if (nameLcFqn.equals(this.get(i).getLcFqn())) {
					return;
				}
			}
			this.add(name);
		}
	}
	
	public static class AuthPair extends RECORD {
		public final Credentials credentials = mkRECORD(Credentials::make);
		public final Verifier verifier = mkMember(Verifier::make);
		
		private AuthPair() { }
		public static AuthPair make() { return new AuthPair(); }
	}
	
	public static class Postmark extends RECORD {
		public final Name server = mkRECORD(Name::make);
		public final Time time = mkMember(Time::make);
		
		private Postmark() { }
		public static Postmark make() { return new Postmark(); }
	}
	
	public static class MessageID extends ARRAY<UNSPECIFIED> {
		private MessageID() { super(5, UNSPECIFIED::make); }
		public static MessageID make() { return new MessageID(); }
	}
	
	public enum UndeliveredNameType {
		noSuchRecipient,
		cantValidateNow,
		illegalName,
		refused,
		noAccessToDL,
		timeout,
		noDLsAllowed,
		messageTooLong
	}
	public static final EnumMaker<UndeliveredNameType> mkUndeliveredNameType = buildEnum(UndeliveredNameType.class).get();
	
	public static class UndeliveredName extends RECORD {
		public final ENUM<UndeliveredNameType> reason = mkENUM(mkUndeliveredNameType);
		public final Name name = mkRECORD(Name::make);
		
		private UndeliveredName() { }
		public static UndeliveredName make() { return new UndeliveredName(); }
	}
	
	public static class Undeliverables extends SEQUENCE<UndeliveredName> {
		private Undeliverables() { super(UndeliveredName::make); }
		public static Undeliverables make() { return new Undeliverables(); }
	}
	
	public static class EncodedList extends SEQUENCE<Attribute> {
		private EncodedList() { super(Attribute::make); }
		public static EncodedList make() { return new EncodedList(); }
	}
	
	public enum MessageStatus { newMail, known, received }
	public static final EnumMaker<MessageStatus> mkMessageStatus = buildEnum(MessageStatus.class).get();
	
	public enum AttributeType implements CrEnum {
		mailAnswerTo (0, 0x8000),
		mailCopies   (1, 0x4000),
		mailFrom     (2, 0x2000),
		mailInReplyTo(3, 0x1000),
		mailNote     (4, 0x0800),
		mailSubject  (5, 0x0400),
		mailTo       (6, 0x0200),
		mailBodySize (7, 0x0100),
		mailBodyType (8, 0x0080);
		
		private final int wireValue;
		private final int selectionMask;
		
		private AttributeType(int wireValue, int selectionMask) {
			this.wireValue = wireValue;
			this.selectionMask = selectionMask;
		}

		@Override
		public int getWireValue() {
			return this.wireValue;
		}
		
		public boolean isSelected(int selections) {
			return (selections & this.selectionMask) != 0;
		}
	}
	public static final EnumMaker<AttributeType> mkAttributeType = buildEnum(AttributeType.class).get();
	
	public static class EncodedSelections extends RECORD {
		public final BOOLEAN transportEnvelope = mkBOOLEAN();
		public final BOOLEAN inbasketEnvelope = mkBOOLEAN();
		public SEQUENCE<LONG_CARDINAL> mailAttributes = mkSEQUENCE(LONG_CARDINAL::make);
		
		private EncodedSelections() { }
		public static EncodedSelections make() { return new EncodedSelections(); }
	}
	
	public static class ListElementRecord extends RECORD {
		public final CARDINAL message = mkCARDINAL();
		public final EncodedList transportEnvelope = mkMember(EncodedList::make);
		public final EncodedList inbasketEnvelope = mkMember(EncodedList::make);
		public final EncodedList mailAttributes = mkMember(EncodedList::make);
		
		private ListElementRecord() { }
		public static ListElementRecord make() { return new ListElementRecord(); }
	}
	
	public static class MailHeaderData extends RECORD {
		public final LONG_CARDINAL version = mkLONG_CARDINAL();
		public final EncodedList metadata = mkMember(EncodedList::make);
		
		private MailHeaderData() {}
		public static MailHeaderData make() { return new MailHeaderData(); }
	}
	
	
	/*
	 * constants for mail attributes in AttributeLists (same format as for Filing)
	 */
	
	// MailTransport envelope attributes
	public static final int atMtPostmark = 0;
	public static final int atMtMessageID = 1;
	public static final int atMtContentsType = 2;
	public static final int atMtContentsSize = 3; // size in bytes of the mail content (transferred as bulk data)
	public static final int atMtOriginator = 4;
	public static final int atMtOldTransportProblem = 5;
	public static final int atMtTransportProblem = 6;
	public static final int atMtReturnToName = 7;
	public static final int atMtPreviousRecipients = 8;
	
	// Inbasket envelope attributes
	public static final int atIbMessageStatus = 1000;
	
	// Filing attributes on the root file of the serialized file for the mail content
	// (possibly renaming/re-using standard Filing attributes)
	public static final int from             = 4672; // NameList
	public static final int replyTo          = 4674; // NameList(?)
	public static final int to               = 4676; // NameList
	public static final int cc               = 4677; // NameList
	public static final int comments         = 4687; // STRING (text content body if no attachments)
	public static final int inReplyTo        = 4690; // NameList(?)
	public static final int lastMsgAttribute = 4690; // ??
	public static final int subject          = 9;    // from Filing: name
	public static final int bodySize         = 16;   // from Filing: dataSize
	public static final int bodyType         = 17;   // from Filing: type
	public static final int filedEnvelope    = 4281; // ??
	public static final int coversheetOn     = 4362; // boolean
	
	/*
	 * other constants
	 */
	
	// content types
	public static final int ctSerializedFile = 0; // the "usual" content type
	public static final int ctUnspecified = 1;
	
	/*
	 * error types
	 */
	
	public enum ErrorType { access, authentication, connection, handle, location, service, transfer, undefined };
	public static final EnumMaker<ErrorType> mkErrorType = buildEnum(ErrorType.class).get();
	
	public enum AccessProblem {
		accessRightslnsufficient, accessRightslndeterminate, mailboxBusy,
		noSuchMailbox, mailboxNamelndeterminate
	};
	public static final EnumMaker<AccessProblem> mkAccessProblem = buildEnum(AccessProblem.class).get();
	
	public enum AuthenticationProblem {
		badNamelnldentity, badPwdlnldentity, tooBusy, cannotReachAS,
		cantGetKeyAtAS, credsExpiredPleaseRetry, authFlavorTooWeak, other};
	public static final EnumMaker<AuthenticationProblem> mkAuthenticationProblem = buildEnum(AuthenticationProblem.class).get();
	
	public enum ConnectionProblem implements CrEnum {
		// communication problems
		noRoute(0), noResponse(1), transmissionHardware(2), transportTimeout(3),
		// resource problems
		tooManyLocalConnections(4), tooManyRemoteConnections(5),
		// remote program implementation problems
		missingCourier(6), missingProgram(7), missingProcedure(8), protocolMismatch(9),
		parameterInconsistency(10), invalidMessage(11), returnTimedOut(12),
		// miscellaneous
		otherCallProblem(0177777);
		
		final int wireValue;
		private ConnectionProblem(int wireValue) { this.wireValue = wireValue; }
		@Override public int getWireValue() { return this.wireValue; }
	};
	public static final EnumMaker<ConnectionProblem> mkConnectionProblem = buildEnum(ConnectionProblem.class).get();
	
	public enum LocationProblem { noCHAvailable, noSuchName, noMailboxForName, noLocationFound, noMailDropUp };
	public static final EnumMaker<LocationProblem> mkLocationProblem = buildEnum(LocationProblem.class).get();
	
	public enum SessionProblem { handleInvalid, wrongState };
	public static final EnumMaker<SessionProblem> mkSessionProblem = buildEnum(SessionProblem.class).get();
	
	public enum ServiceProblem { cannotAuthenticate, serviceFull, serviceUnavailable, mediumFull };
	public static final EnumMaker<ServiceProblem> mkServiceProblem = buildEnum(ServiceProblem.class).get();
	
	public enum TransferProblem { aborted, noRendezvous, wrongDirection };
	public static final EnumMaker<TransferProblem> mkTransferProblem = buildEnum(TransferProblem.class).get();
	
	/*
	 * common errors for MailTransport and Inbasket
	 * 
	 * courier-error-numbers:
     *  0 = access
     *  1 = authentication
     *  2 = connection
	 */
	
	public static class AccessErrorRecord extends ErrorRECORD {
		public final ENUM<AccessProblem> problem = mkENUM(mkAccessProblem);
		@Override public int getErrorCode() { return 0; }
		public AccessErrorRecord(AccessProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<AccessErrorRecord> AccessError = mkERROR(AccessErrorRecord.class);
	
	public static class AuthenticationErrorRecord extends ErrorRECORD {
		public final ENUM<AuthenticationProblem> problem = mkENUM(mkAuthenticationProblem);
		@Override public int getErrorCode() { return 1; }
		public AuthenticationErrorRecord(AuthenticationProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<AuthenticationErrorRecord> AuthenticationError = mkERROR(AuthenticationErrorRecord.class);
	
	public static class ConnectionErrorRecord extends ErrorRECORD {
		public final ENUM<ConnectionProblem> problem = mkENUM(mkConnectionProblem);
		@Override public int getErrorCode() { return 2; }
		public ConnectionErrorRecord(ConnectionProblem problem) {
			this.problem.set(problem);
		}
	}
	public final ERROR<ConnectionErrorRecord> ConnectionError = mkERROR(ConnectionErrorRecord.class);
	
	
}
