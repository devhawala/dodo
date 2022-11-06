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

import java.util.List;

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
import dev.hawala.xns.level4.common.AuthChsCommon.Credentials;
import dev.hawala.xns.level4.common.AuthChsCommon.Name;
import dev.hawala.xns.level4.common.AuthChsCommon.NetworkAddressList;
import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;
import dev.hawala.xns.level4.common.AuthChsCommon.Verifier;
import dev.hawala.xns.level4.common.BulkData1;
import dev.hawala.xns.level4.filing.FilingCommon.Attribute;
import dev.hawala.xns.level4.mailing.MailTransport4.ServiceErrorRecord;
import dev.hawala.xns.level4.mailing.MailTransport4.SessionErrorRecord;
import dev.hawala.xns.level4.mailing.MailTransport4.TransferErrorRecord;
import dev.hawala.xns.level4.mailing.MailTransport4.UndefinedErrorRecord;
import dev.hawala.xns.level4.mailing.MailingCommon.AccessErrorRecord;
import dev.hawala.xns.level4.mailing.MailingCommon.AuthenticationErrorRecord;
import dev.hawala.xns.level4.mailing.MailingCommon.MessageID;

/**
 * Definition of the subset for the MailTransport Courier program
 * version 5 used by GlobalView for sending mails.
 * 
 * Most structures and procedures defined here are highly speculative, as
 * no courier program definition (.cr or .courier file) and no documentation
 * was found in the internet for the mailing protocols used by GlobalView (and
 * possibly by ViewPoint 3.0). The names given to the items here are also some
 * kind of "best guesses", as well as the true meaning of substructure elements.
 * Furthermore functionality for inter-mail-server communication is surely missing.
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
public class MailTransport5 extends CrProgram {

	public static final int PROGRAM = 17;
	public static final int VERSION = 5;

	@Override
	public int getProgramNumber() { return PROGRAM; }

	@Override
	public int getVersionNumber() { return VERSION; }
	
	/*
	 * local "UndefinedError" codes
	 */
	
	// kind of address provided in an envelope is not RNameType.xns
	// i.e.: an address was given to 'postOneBodyPart' although it was rejected by 'beginPost'
	public static final int SERVICE_ERROR_INVALID_ADDRESS_KIND = 4201;
	
	// serialized data received as bulk-data or attribute value could not be deserialized
	// i.e.: EndOfMessageException during deserializing
	public static final int DECODE_ERROR_ON_SERIALIZED_DATA = 4202;
	
	/*
	 * types
	 * 
     * from NEWNSMAIL...:
	 */
	
    /*
     * TYPES
     *   [(CREDENTIALS (AUTHENTICATION . CREDENTIALS))
     *    (VERIFIER (AUTHENTICATION . VERIFIER))
     */
	// AuthChsCommon.Credentials
	// AuthChsCommon.Verifier
	
    /*    (SESSION (RECORD (TOKEN (ARRAY 2 UNSPECIFIED))
     *                    (VERIFIER VERIFIER)))
     */
	public static class Session extends RECORD {
		public final LONG_CARDINAL token = mkLONG_CARDINAL();
		public final Verifier verifier = mkMember(Verifier::make);
		private Session() { }
		public static Session make() { return new Session(); } 
	}
	
    /*    (ENVELOPE.ITEM.TYPE LONGCARDINAL)
     *    (ENVELOPE (SEQUENCE NEW.ENVELOPE.ITEM))
     */
	public static class Envelope extends SEQUENCE<Attribute> {
		private Envelope() { super(Attribute::make); }
		public static Envelope make() { return new Envelope(); }
	}
	
    /*    (INVALID.NAME (RECORD (ID CARDINAL)
     *                         (REASON INVALID.REASON)))
     *    (INVALID.NAME.LIST (SEQUENCE INVALID.NAME))
     *    (INVALID.REASON (ENUMERATION (NoSuchRecipient 0)
     *                           (NoMailboxForRecipient 1)
     *                           (IllegalName 2)
     *                           (NoDlsAllowed 3)
     *                           (ReportNotAllowed 4)))
     */
	public enum InvalidReason { NoSuchRecipient, NoMailboxForRecipient, IllegalName, NoDlsAllowed, ReportNotAllowed }
	public static final EnumMaker<InvalidReason> mkInvalidReason = buildEnum(InvalidReason.class).get();
	
	public static class InvalidName extends RECORD {
		public final CARDINAL id = mkCARDINAL();
		public final ENUM<InvalidReason> reason = mkENUM(mkInvalidReason);
		private InvalidName() { }
		public static InvalidName make() { return new InvalidName(); }
	}
	
	public static class InvalidNameList extends SEQUENCE<InvalidName> {
		private InvalidNameList() { super(InvalidName::make); }
		public static InvalidNameList make() { return new InvalidNameList(); }
	}
	
    /*    (NAME NSNAME)
     *    (RNAME NEW.RNAME                                      (* ; 
     *                                                "(choice (xns 0 name) (gateway 1 gateway.name))")
     *           )
     *    (RNAME.LIST (SEQUENCE RNAME))
     *    [GATEWAY.NAME (RECORD (COUNTRY STRING)
     *                         (ADMIN.DOMAIN STRING)
     *                         (PRIVATE.DOMAIN STRING)
     *                         (ORGANIZATION STRING)
     *                         (ORGANIZATIONAL.UNITS (SEQUENCE STRING))
     *                         (PERSONAL (CHOICE (WHOLE 0 STRING)
     *                                          (BROKEN 1 BROKEN.NAME)))
     *                         (GATEWAY.SPECIFIC.INFORMATION (SEQUENCE X400.ATTRIBUTE]
     *    (BROKEN.NAME (RECORD (GIVEN STRING)
     *                        (INITIALS STRING)
     *                        (FAMILY STRING)
     *                        (GENERATION STRING)))
     *    (X400.ATTRIBUTE (RECORD (TYPE STRING)
     *                           (VALUE STRING)))
     */
	public enum RNameType { xns, x400 }
	public static final EnumMaker<RNameType> mkRNameType = buildEnum(RNameType.class).get();
	
	public enum X400PersonalType { whole, broken }
	public static final EnumMaker<X400PersonalType> mkX400PersonalType = buildEnum(X400PersonalType.class).get();
	
	public static class WholeName extends RECORD {
		public final STRING wholeName = mkSTRING();
		private WholeName() { }
		public static WholeName make() { return new WholeName(); }
	}
	public static class BrokenName extends RECORD {
		public final STRING given = mkSTRING();
		public final STRING initials = mkSTRING();
		public final STRING family = mkSTRING();
		public final STRING generation = mkSTRING();
		private BrokenName() { }
		public static BrokenName make() { return new BrokenName(); }
	}
	public static final ChoiceMaker<X400PersonalType> mkX400PersonalName = buildChoice(mkX400PersonalType)
			.choice(X400PersonalType.whole, WholeName::make)
			.choice(X400PersonalType.broken, BrokenName::make)
			.get();
	
	public static class X400Attribute extends RECORD {
		public final STRING type = mkSTRING();
		public final STRING value = mkSTRING();
		private X400Attribute() { }
		public static X400Attribute make() { return new X400Attribute(); } 
	}
	
	public static class X400Name extends RECORD {
		public final STRING country = mkSTRING();
		public final STRING adminDomain = mkSTRING();
		public final STRING privateDomain = mkSTRING();
		public final SEQUENCE<STRING> organizationalUnits = mkSEQUENCE(STRING::make);
		public final CHOICE<X400PersonalType> personal = mkCHOICE(mkX400PersonalName);
		public final SEQUENCE<X400Attribute> gatewaySpecificInformation = mkSEQUENCE(X400Attribute::make);
		private X400Name() { }
		public static X400Name make() { return new X400Name(); }
	}
	
	// RNAME
	public static final ChoiceMaker<RNameType> mkRName = buildChoice(mkRNameType)
			.choice(RNameType.xns, ThreePartName::make)
			.choice(RNameType.x400, X400Name::make)
			.get();
	
	// RNAME.LIST
	public static final class ListOfRName extends SEQUENCE<CHOICE<RNameType>> {
		private ListOfRName() { super(mkRName); }
		public static ListOfRName make() { return new ListOfRName(); }
	}
	
    /*    (REPORT.TYPE (ENUMERATION (NONE 0)
     *                        (NON.DELIVERY.ONLY 1)
     *                        (ALL 2)))
     *    (RECIPIENT (RECORD (NAME RNAME)
     *                      (RECIPIENT.ID CARDINAL)
     *                      (REPORT REPORT.TYPE)))
     *    (RECIPIENT.LIST (SEQUENCE RECIPIENT))
     */
	public enum ReportType { none, nonDeliveryOnly, all}
	public static final EnumMaker<ReportType> mkReportType = buildEnum(ReportType.class).get();
	
	public static class Recipient extends RECORD {
		public final CHOICE<RNameType> name = mkCHOICE(mkRName);
		public final CARDINAL recipientId = mkCARDINAL();
		public final ENUM<ReportType> report = mkENUM(mkReportType);
		private Recipient() { }
		public static Recipient make() { return new Recipient(); }
	}
	
	public static class RecipientList extends SEQUENCE<Recipient> {
		private RecipientList() { super(Recipient::make); }
		public static RecipientList make() { return new RecipientList(); }
	}
	
    /*    (WILLINGNESS (SEQUENCE WILLINGNESS.METRIC))
     *    (WILLINGNESS.METRIC CARDINAL)
     *    (BODY.PART.TYPE LONGCARDINAL)
     *    (CONTENTS.TYPE LONGCARDINAL)
     *    (MESSAGEID (ARRAY 5 UNSPECIFIED))
     */
	
	
    /*    [POSTING.DATA (RECORD (RECIPIENTS RECIPIENT.LIST)
     *                         (CONTENTS.TYPE CONTENTS.TYPE)
     *                         (CONTENTS.SIZE LONGCARDINAL)
     *                         (BODY.PART.TYPES.SEQUENCE (SEQUENCE BODY.PART.TYPE]
     */
	public static class PostingData extends RECORD {
		public final RecipientList recipients = mkMember(RecipientList::make);
		public final LONG_CARDINAL contentsType = mkLONG_CARDINAL();
		public final LONG_CARDINAL contentsSize = mkLONG_CARDINAL();
		public final SEQUENCE<LONG_CARDINAL> bodyPartTypes = mkSEQUENCE(LONG_CARDINAL::make);
		private PostingData() {}
		public static PostingData make() { return new PostingData(); }
	}
	
    /*    (POSTMARK (RECORD (POSTED.AT RNAME)
     *                     (TIME TIME)))
     *    (TOC (SEQUENCE TOC.ITEM))
     *    (TOC.ITEM (RECORD (TYPE BODY.PART.TYPE)
     *                     (SIZE LONGCARDINAL)))
     *    [REPORT (RECORD (ORIGINAL.ENVELOPE ENVELOPE)
     *                   [FATE (CHOICE (DELIVERED 0 (ENUMERATION (CONTENTS.TRUNCATED 0)
     *                                                     (NO.PROBLEM 1)))
     *                                (NOT.DELIVERED 1 (RECORD (REASON NON.DELIVERY.REASON)
     *                                                        (POSTMARK POSTMARK]
     *                   (REPORT.TYPE (CHOICE (DLMEMBER 0 DLREPORT)
     *                                       (OTHER 1 OTHER.REPORT]
     *    [DLREPORT (RECORD (DLNAME RNAME)
     *                     (INVALID.RECIPIENTS (SEQUENCE NON.DELIVERED.RECIPIENT]
     *    [OTHER.REPORT (RECORD (SUCCEEDED (SEQUENCE DELIVERED.RECIPIENT))
     *                         (FAILED (SEQUENCE NON.DELIVERED.RECIPIENT]
     *    (DELIVERED.RECIPIENT (RECORD (RECIPIENT RECIPIENT)
     *                                (WHEN TIME)))
     *    (NON.DELIVERED.RECIPIENT (RECORD (RECIPIENT RECIPIENT)
     *                                    (REASON NON.DELIVERY.REASON)))
     *    (NON.DELIVERY.REASON (ENUMERATION (NoSuchRecipient 0)
     *                                (NoMailboxForRecipient 1)
     *                                (IllegalName 2)
     *                                (Timeout 3)
     *                                (ReportNotAllowed 4)
     *                                (MessageTooLong 5)
     *                                (AmbiguousRName 6)
     *                                (IllegalCharacters 7)
     *                                (UnsupportedBodyParts 8)
     *                                (UnsupportedContentsType 9)
     *                                (TransientProblem 10)
     *                                (ContentSyntaxError 11)
     *                                (TooManyRecipients 12)
     *                                (ProtocolViolation 13)
     *                                (X400PragmaticConstraintViolation 14)
     *                                (x400NoBilateralAgreement 15)
     *                                (AccessRightsInsufficientForDL 16)
     *                                (Other 17)))
     *    (TRANSPORT.OPTIONS (RECORD (RETURN.OF.CONTENTS BOOLEAN)
     *                              (ALTERNATE.RECIPIENT.ALLOWED BOOLEAN)))
     *    (PRIORITY (ENUMERATION (NonUrgent 0)
     *                     (Normal 1)
     *                     (Urgent 2)))
     *    (CONVERTED.ITEM (ENUMERATION (IA5TextToTeletex 0)
     *                           (TeletexToTelex 1)
     *                           (TeletexToIA5Text 2)
     *                           (TelexToTeletex 3)))
     *    (IP.MESSAGEID (RECORD (ORIGINATOR RNAME)
     *                         (UNIQUESTRING STRING)))
     *    (AUTHENTICATION.LEVEL (ENUMERATION (Strong 0)
     *                                 (Simple 1)
     *                                 (Foreign 2)))
     *    [FORWARDED.MESSAGE.INFO (RECORD (ENVELOPE ENVELOPE)
     *                                   (HEADING (SEQUENCE HEADING.ATTRIBUTE))
     *                                   (ASSOCIATED.BODY.PARTS (SEQUENCE BODY.PART.INDEX))
     *                                   (INDEX.OF.PARENT.HEADING (CHOICE (NULL 0 (RECORD))
     *                                                                   (NESTED 1 CARDINAL]
     *    (BODY.PART.INDEX CARDINAL)
     *    (SERVICE.PROBLEM (ENUMERATION (CannotAuthenticate 0)
     *                            (ServiceFull 1)
     *                            (ServiceUnavailable 2)
     *                            (MediumFull 3)))
     *    (TRANSFER.PROBLEM (ENUMERATION (Aborted 0)))
     *    (OTHER.PROBLEM (ENUMERATION (Can'tExpedite 0)
     *                          (MalformedMessage 1)
     *                          (IncorrectContentsSize 2)
     *                          (LAST 65535)))
     *    (SESSION.PROBLEM (ENUMERATION (InvalidHandle 0)
     *                            (WrongState 1]
	 */
	
	// IP.MESSAGEID
	public static class IpMessageID extends RECORD {
		public final CHOICE<RNameType> originator = mkMember(mkRName);
		public final STRING uniquestring = mkSTRING();
		private IpMessageID() { }
		public static IpMessageID make() { return new IpMessageID(); }
	}
	
	
    /*
	 * ERRORS
     *   ((ACCESS.ERROR 0 (ACCESS.PROBLEM))
     *    (AUTHENTICATION.ERROR 1 ((AUTHENTICATION . PROBLEM)))
     *    (INVALID.RECIPIENTS 3 (INVALID.NAME.LIST))
     *    (SERVICE.ERROR 4 (SERVICE.PROBLEM))
     *    (TRANSFER.ERROR 5 (TRANSFER.PROBLEM))
     *    (OTHER.ERROR 6 (OTHER.PROBLEM))
     *    (SESSION.ERROR 7 (SESSION.PROBLEM))))
	 */
	
	// (ACCESS.ERROR 0 (ACCESS.PROBLEM))
	// => MailingCommon.AccessError
	public final ERROR<AccessErrorRecord> AccessError = mkERROR(AccessErrorRecord.class);
	
	// (AUTHENTICATION.ERROR 1 ((AUTHENTICATION . PROBLEM)))
	// => MailingCommon.AuthenticationError
	public final ERROR<AuthenticationErrorRecord> AuthenticationError = mkERROR(AuthenticationErrorRecord.class);
	
	public static class InvalidRecipientsRecord extends ErrorRECORD {
		public final InvalidNameList invalidNames = mkMember(InvalidNameList::make);
		@Override public int getErrorCode() { return 3; }
		public InvalidRecipientsRecord(List<InvalidName> invalids) {
			for (InvalidName invalidName : invalids) {
				this.invalidNames.add(invalidName);
			}
		}
	}
	public final ERROR<InvalidRecipientsRecord> InvalidRecipients = mkERROR(InvalidRecipientsRecord.class);
	
	// (SERVICE.ERROR 4 (SERVICE.PROBLEM))
	// => MailTransport4.ServiceError
	public final ERROR<ServiceErrorRecord> ServiceError = mkERROR(ServiceErrorRecord.class);
	
	// (TRANSFER.ERROR 5 (TRANSFER.PROBLEM))
	// => MailTransport4.TransferError
	public final ERROR<TransferErrorRecord> TransferError = mkERROR(TransferErrorRecord.class);
	
	// (OTHER.ERROR 6 (OTHER.PROBLEM))
	// => MailTranspor4.UndefinedError
	public final ERROR<UndefinedErrorRecord> UndefinedError = mkERROR(UndefinedErrorRecord.class);
	
	// (SESSION.ERROR 7 (SESSION.PROBLEM))))
	// => MailTransport4.SessionError
	public final ERROR<SessionErrorRecord> SessionError = mkERROR(SessionErrorRecord.class);
	
	
	/*
	 * procedures
	 */
	
	/*
	 * serverPoll
	 *  = procedure 0 ::
	 *
	 *   ((SERVER.POLL 0 NIL RETURNS (WILLINGNESS (CLEARINGHOUSE . NETWORK.ADDRESS.LIST)
     *                                      NAME))
	 */
	public static class ServerPollResults extends RECORD {
		public final SEQUENCE<CARDINAL> willingnesses = mkSEQUENCE(CARDINAL::make);
		public final NetworkAddressList address = mkMember(NetworkAddressList::make);
		public final Name serverName = mkRECORD(Name::make);
		
		private ServerPollResults() {
			CARDINAL ten = CARDINAL.make().set(10);
			CARDINAL nine = CARDINAL.make().set(9);
			this.willingnesses
				.add(ten)
				.add(ten)
				.add(ten)
				.add(ten)
				.add(ten)
				.add(ten)
				.add(nine);
		}
		public static ServerPollResults make() { return new ServerPollResults(); }
		
	}
	
	public final PROC<RECORD,ServerPollResults> ServerPoll = mkPROC(
						"ServerPoll",
						0,
						RECORD::empty,
						ServerPollResults::make
						// no parameters means also: the invoker cannot provide wrong data
						);
	
	/*
	 * beginPost
	 *  = procedure 1 ::
     * 
     *    (BEGIN.POST 1 (POSTING.DATA BOOLEAN BOOLEAN (SEQUENCE NEW.ENVELOPE.ITEM)
     *                         CREDENTIALS VERIFIER)
     *           RETURNS
     *           (SESSION INVALID.NAME.LIST)
     *           REPORTS
     *           (AUTHENTICATION.ERROR INVALID.RECIPIENTS SERVICE.ERROR OTHER.ERROR))
	 */
	
	public static class BeginPostParams extends RECORD {
		public final PostingData postingData = mkRECORD(PostingData::make);
		public final BOOLEAN unknownBoolean1 = mkBOOLEAN();
		public final BOOLEAN unknownBoolean2 = mkBOOLEAN();
		public final Envelope envelope = mkMember(Envelope::make);
		public final Credentials credentials = mkMember(Credentials::make);
		public final Verifier verifier = mkMember(Verifier::make);
		private BeginPostParams() { }
		public static BeginPostParams make() { return new BeginPostParams(); }
	}
	public static class BeginPostResults extends RECORD {
		public final Session session = mkRECORD(Session::make);
		public final InvalidNameList invalidNames = mkMember(InvalidNameList::make);
		private BeginPostResults() { }
		public static BeginPostResults make() { return new BeginPostResults(); }
	}
	public final PROC<BeginPostParams,BeginPostResults> BeginPost = mkPROC(
			"BeginPost",
			1,
			BeginPostParams::make,
			BeginPostResults::make,
			AuthenticationError,
			InvalidRecipients,
			ServiceError,
			UndefinedError
			);
	
	/*
	 * postOneBodyPart
	 *  = procedure 8 ::
	 * 
     *    (POST.ONE.BODY.PART 8 (SESSION BODY.PART.TYPE BULK.DATA.SOURCE)
     *           RETURNS NIL REPORTS (AUTHENTICATION.ERROR SERVICE.ERROR OTHER.ERROR SESSION.ERROR 
     *                                      TRANSFER.ERROR))
	 */
	
	public static class PostOneBodyPartParams extends RECORD {
		public final Session session = mkRECORD(Session::make);
		public final LONG_CARDINAL mailPartType = mkLONG_CARDINAL();
		public final BulkData1.Source content = mkRECORD(BulkData1.Source::make);
		
		private PostOneBodyPartParams() {}
		public static PostOneBodyPartParams make() { return new PostOneBodyPartParams(); }
	}
	public final PROC<PostOneBodyPartParams,RECORD> PostOneBodyPart = mkPROC(
			"PostOneBodyPart",
			8,
			PostOneBodyPartParams::make,
			RECORD::empty,
			AuthenticationError,
			ServiceError,
			UndefinedError,
			SessionError,
			TransferError
			);

	
	/*
	 *  mail part types => bulk-data content
	 *  
	 * (RPAQQ \NSMAIL.BODY.PART.TYPES
     *    ((HEADING 0)
     *     (VPFOLDER 1)
     *     (NSTEXTFILE 2)
     *     (VPDOCUMENT 3)
     *     (OTHERNSFILE 4)
     *     (MULTINATIONAL.NOTE 5)
     *     (IA5.NOTE 6)
     *     (PILOTFILE 7)
     *     (G3FAX 8)
     *     (TELETEX 9)
     *     (TELEX 10)
     *     (ISO6937.NOTE 11)
     *     (INTERPRESS 12)))
	 */
	
	public static final int mptEnvelope         = 0x0000; // mail-envelope => SEQUENCE<Attribute>
	public static final int mptAttachmentFolder = 0x0001; // folder
	public static final int mptTextFile         = 0x0002; // text file 
	public static final int mptAttachmentDoc    = 0x0003; // document (Star/VP/GV)
	public static final int mptOtherFile        = 0x0004; // other file type 
	public static final int mptNoteGV           = 0x0005; // "GlobalView" mail text, bulk-data => raw text (!! may have odd length !!)
	public static final int mptNoteIA5          = 0x0006; // "IA5" mail text, bulk-data => raw text (!! may have odd length !!)
	public static final int mptPilotFile        = 0x0007; // ?pilot file?
	public static final int mptG3Fax            = 0x0008; // G3 fax
	public static final int mptTeletex          = 0x0009; // teletex file
	public static final int mptTelex            = 0x000A; // telex file
	public static final int mptNoteISO6937      = 0x000B; // "ISO6937" mail text, bulk-data => raw text (!! may have odd length !!)
	public static final int mptInterpress       = 0x000C; // interpress master
	
	/*
	 *  bulk-data:
	 * elements for mailPartType 0 == SEQUENCE<Attribute>
	 * => attribute type IDs...
	 * 
	 * (RPAQQ \NSMAIL.HEADING.ATTRIBUTES
     *    ((Message-ID 1 IP.MESSAGEID)
     *     (Sender 2 RNAME)
     *     (From 3 RNAME.LIST)
     *     (To 4 RNAME.LIST)
     *     (cc 5 RNAME.LIST)
     *     (bcc 6 RNAME.LIST)
     *     (In-Reply-to 7 IP.MESSAGEID)
     *     (Obsoletes 8 (SEQUENCE IP.MESSAGEID))
     *     (References 9 (SEQUENCE IP.MESSAGEID))
     *     (Subject 10 STRING)
     *     (Expiration-Date 11 TIME)
     *     (Reply-By 12 TIME)
     *     (Reply-to 13 RNAME.LIST)
     *     (Importance 14 (ENUMERATION (Low 0)
     *                           (Normal 1)
     *                           (High 2)))
     *     (Sensitivity 15 (ENUMERATION (Personal 0)
     *                            (Private 1)
     *                            (CompanyConfidential 2)))
     *     (Auto-Forwarded 16 BOOLEAN)
     *     (Immutable 17 (RECORD))
     *     (Reply-Requested-of 18 RNAME.LIST)
     *     (TextAnnotation 19 STRING)
     *     (ForwardedHeadings 20 (SEQUENCE FORWARDED.MESSAGE.INFO))
     *     (newTextAnnotation 199 STRING)
     *     (BodyOffset 198 LONGCARDINAL)
     *     (LispFormatting 4911 STRING)))
	 */
	
	public static final int atMessageID         = 0x0001; //  1 = sending user and send(?) datetime
                                                          // => IpMessageID (client=>dodo) resp. SimpleIpMessageID (dodo=>client)

	public static final int atSender            = 0x0002; //  2 = 1st reference of the sending user name
                                                          // => RName = 'mkRName' (client=>dodo) resp. SimpleRName (dodo=>client)

	public static final int atFrom              = 0x0003; //  3 = 2nd reference of the sending user name
                                                          // => ListOfRName (client=>dodo) resp. ListOfSimpleRName (dodo=>client)

	public static final int atTo                = 0x0004; //  4 = primary recipients
                                                          // => ListOfRName (client=>dodo) resp. ListOfSimpleRName (dodo=>client)

	public static final int atCopiesTo          = 0x0005; //  5 = copy recipients
                                                          // => ListOfRName (client=>dodo) resp. ListOfSimpleRName (dodo=>client)

	public static final int atBlindCopiesTo     = 0x0006; //  5 = blind copy recipients
                                                          // => ListOfRName (client=>dodo) resp. ListOfSimpleRName (dodo=>client)

	public static final int atSubject           = 0x000A; // 10 = subject
                                                          // => STRING
	
	public static final int atReplyTo           = 0x000D; // 13 = reply to
                                                          // => ListOfRName (client=>dodo) resp. ListOfSimpleRName (dodo=>client)
	
	// bulk-data:
	// elements for mailPartType 0 == SEQUENCE<Attribute>
	// => attribute contents...
	//
	// the following approximated/simplified variants are used for dodo=>client transmissions.
	// As Dodo supports XNS mailing only, "foreign" addresses (RNameType != xns) will never be
	// produced. So using the approximated versions simply eases constructing results...
	
	/*
     * approximated/simplified variant for: CHOICE<RNameType> = RNAME
     */
	public static class SimpleRName extends RECORD {
		public final CARDINAL tag = mkCARDINAL();
		public ThreePartName name = mkMember(ThreePartName::make);
		
		private SimpleRName() {}
		public static SimpleRName make() { return new SimpleRName(); }
	}
	
	/*
     * approximated/simplified variant for: RNameList = RNAME.LIST
     */
	public static class ListOfSimpleRName extends SEQUENCE<SimpleRName> {
		private ListOfSimpleRName() { super(SimpleRName::make); }
		public static ListOfSimpleRName make() { return new ListOfSimpleRName(); }
	}
	
	
    /*
     * approximated/simplified variant for: IpMessageID = IP.MESSAGEID
     */
	public static class SimpleIpMessageID extends RECORD {
		public final SimpleRName nameWithTag = mkMember(SimpleRName::make);
		public final STRING uniqueString = mkSTRING();
		
		private SimpleIpMessageID() {}
		public static SimpleIpMessageID make() { return new SimpleIpMessageID(); }
	}
	
	
	/*
	 * endPost
	 *  = procedure 9 ::
	 *  
     *    (END.POST 9 (SESSION BOOLEAN)
     *           RETURNS
     *           (MESSAGEID)
     *           REPORTS
     *           (AUTHENTICATION.ERROR SERVICE.ERROR OTHER.ERROR SESSION.ERROR TRANSFER.ERROR)))
	 */
	
	public static class EndPostParams extends RECORD {
		public final Session session = mkRECORD(Session::make);
		public final BOOLEAN unknownBoolean = mkBOOLEAN(); // test send ???
		private EndPostParams() { }
		public static EndPostParams make() { return new EndPostParams(); }
	}
	public static class EndPostResults extends RECORD {
		public final MessageID messageId = mkMember(MessageID::make);
		private EndPostResults() { }
		public static EndPostResults make() { return new EndPostResults(); }
	}
	public final PROC<EndPostParams,EndPostResults> EndPost = mkPROC(
			"EndPost",
			9,
			EndPostParams::make,
			EndPostResults::make,
			AuthenticationError,
			ServiceError,
			UndefinedError,
			SessionError,
			TransferError
			);

	/*
	 * mailPoll
	 *  = procedure 7 ::
	 *  
     *    (MAILPOLL 7 (NAME CREDENTIALS VERIFIER)
     *           RETURNS
     *           (BOOLEAN)
     *           REPORTS
     *           (ACCESS.ERROR AUTHENTICATION.ERROR SERVICE.ERROR OTHER.ERROR))
	 */
	
}
