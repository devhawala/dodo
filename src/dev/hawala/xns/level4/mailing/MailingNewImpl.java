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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import dev.hawala.xns.level3.courier.CHOICE;
import dev.hawala.xns.level3.courier.CourierRegistry;
import dev.hawala.xns.level3.courier.ErrorRECORD;
import dev.hawala.xns.level3.courier.LONG_CARDINAL;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.SEQUENCE;
import dev.hawala.xns.level3.courier.STRING;
import dev.hawala.xns.level3.courier.WireBaseStream;
import dev.hawala.xns.level3.courier.WireWriter;
import dev.hawala.xns.level3.courier.iJsonReader;
import dev.hawala.xns.level3.courier.iJsonWriter;
import dev.hawala.xns.level3.courier.iWireData;
import dev.hawala.xns.level3.courier.iWireStream;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;
import dev.hawala.xns.level4.common.AuthChsCommon.Credentials;
import dev.hawala.xns.level4.common.AuthChsCommon.CredentialsType;
import dev.hawala.xns.level4.common.AuthChsCommon.Name;
import dev.hawala.xns.level4.common.AuthChsCommon.StrongVerifier;
import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;
import dev.hawala.xns.level4.common.AuthChsCommon.Verifier;
import dev.hawala.xns.level4.common.BulkData1;
import dev.hawala.xns.level4.common.ChsDatabase;
import dev.hawala.xns.level4.common.StrongAuthUtils;
import dev.hawala.xns.level4.common.Time2.Time;
import dev.hawala.xns.level4.filing.ByteContentSink;
import dev.hawala.xns.level4.filing.ByteContentSource;
import dev.hawala.xns.level4.filing.FilingCommon;
import dev.hawala.xns.level4.filing.FilingCommon.Attribute;
import dev.hawala.xns.level4.filing.FilingCommon.Content;
import dev.hawala.xns.level4.filing.FilingCommon.SerializedFile;
import dev.hawala.xns.level4.filing.fs.iContentSource;
import dev.hawala.xns.level4.mailing.Inbasket1.State;
import dev.hawala.xns.level4.mailing.Inbasket2.ChangeBodyPartStatusParams;
import dev.hawala.xns.level4.mailing.Inbasket2.ChangeBodyPartStatusResults;
import dev.hawala.xns.level4.mailing.Inbasket2.DeleteParams;
import dev.hawala.xns.level4.mailing.Inbasket2.GetNextMailParams;
import dev.hawala.xns.level4.mailing.Inbasket2.GetNextMailResults;
import dev.hawala.xns.level4.mailing.Inbasket2.LogoffParams;
import dev.hawala.xns.level4.mailing.Inbasket2.LogonParams;
import dev.hawala.xns.level4.mailing.Inbasket2.LogonResults;
import dev.hawala.xns.level4.mailing.Inbasket2.MailCheckParams;
import dev.hawala.xns.level4.mailing.Inbasket2.MailCheckResults;
import dev.hawala.xns.level4.mailing.Inbasket2.MailPollParams;
import dev.hawala.xns.level4.mailing.Inbasket2.MailPollResults;
import dev.hawala.xns.level4.mailing.Inbasket2.MiMailPart;
import dev.hawala.xns.level4.mailing.Inbasket2.MiMailParts;
import dev.hawala.xns.level4.mailing.Inbasket2.MiMailServer;
import dev.hawala.xns.level4.mailing.Inbasket2.MiSender;
import dev.hawala.xns.level4.mailing.Inbasket2.MiTotalPartsLength;
import dev.hawala.xns.level4.mailing.Inbasket2.MiWhatever;
import dev.hawala.xns.level4.mailing.Inbasket2.OtherErrorRecord;
import dev.hawala.xns.level4.mailing.Inbasket2.OtherProblem;
import dev.hawala.xns.level4.mailing.Inbasket2.RetrieveBodyPartsParams;
import dev.hawala.xns.level4.mailing.MailTransport4.ServiceErrorRecord;
import dev.hawala.xns.level4.mailing.MailTransport4.SessionErrorRecord;
import dev.hawala.xns.level4.mailing.MailTransport4.UndefinedErrorRecord;
import dev.hawala.xns.level4.mailing.MailTransport5.BeginPostParams;
import dev.hawala.xns.level4.mailing.MailTransport5.BeginPostResults;
import dev.hawala.xns.level4.mailing.MailTransport5.EndPostParams;
import dev.hawala.xns.level4.mailing.MailTransport5.EndPostResults;
import dev.hawala.xns.level4.mailing.MailTransport5.InvalidName;
import dev.hawala.xns.level4.mailing.MailTransport5.InvalidReason;
import dev.hawala.xns.level4.mailing.MailTransport5.InvalidRecipientsRecord;
import dev.hawala.xns.level4.mailing.MailTransport5.ListOfRName;
import dev.hawala.xns.level4.mailing.MailTransport5.ListOfSimpleRName;
import dev.hawala.xns.level4.mailing.MailTransport5.PostOneBodyPartParams;
import dev.hawala.xns.level4.mailing.MailTransport5.RNameType;
import dev.hawala.xns.level4.mailing.MailTransport5.Recipient;
import dev.hawala.xns.level4.mailing.MailTransport5.ReportType;
import dev.hawala.xns.level4.mailing.MailTransport5.ServerPollResults;
import dev.hawala.xns.level4.mailing.MailTransport5.SimpleIpMessageID;
import dev.hawala.xns.level4.mailing.MailTransport5.SimpleRName;
import dev.hawala.xns.level4.mailing.MailingCommon.AccessErrorRecord;
import dev.hawala.xns.level4.mailing.MailingCommon.AccessProblem;
import dev.hawala.xns.level4.mailing.MailingCommon.EncodedList;
import dev.hawala.xns.level4.mailing.MailingCommon.MessageID;
import dev.hawala.xns.level4.mailing.MailingCommon.NameList;
import dev.hawala.xns.level4.mailing.MailingCommon.ServiceProblem;
import dev.hawala.xns.level4.mailing.MailingCommon.SessionProblem;

/**
 * Implementation of the Courier programs MailTransport5 and
 * Inbasket2, providing mailing support for GlobalView.
 * 
 * The support for GlobalView mailing is restricted in many ways.
 * 
 * First: the courier definitions (structures, constants, procedures)
 * defined here may be incomplete and partially possibly wrong.
 * 
 * Second: the implementation mimics the behavior as expected by resp.
 * observed on GlobalView (specifically GVWin 2.1) and Interlisp-D
 * (specifically Medley 3.51), so other clients using these mail services
 * versions (should these be available) may be unable to use this implementation.
 * 
 * Third: the newer mailing protocol implementation uses the internal mail
 * service used for the initial implementation of the (older) mailing protocol,
 * so mails are always stored by the mail service with the old (VP 2.0 / XDE)
 * compatible feature set: newer mail features (like "importance" or all the
 * mail properties visible when switching to "Show fields: ALL" in GlobalView)
 * are discarded when a mail is sent from GlobalView and will not be present (i.e.
 * have default values) when receiving mails in GlobalView.
 * 
 * Fourth: some behavior of newer procedures is forced to conform to the use-cases
 * of ViewPoint/XDE, for example it seems to be possible to delete single mail parts
 * with the newer protocols, but this implementation simply deletes the complete
 * mail when the procedure 3 (changeBodyPartsStatus) is invoked.
 * 
 * So a mail posted by GlobalView or Interlisp-D is "scaled down" to the old functionality
 * present in ViewPoint/XDE.
 * 
 * When mails are requested by GlobalView using the Inbasket2-protocol, the
 * mails are "received" internally with the old format and transformed on the
 * fly to the new format before transmitting them to GlobalView.
 * 
 * In both cases (sending and receiving), the old protocols use a single call for a
 * transfer operation (post or receive) on a single mail, whereas the new protocols use
 * a sequence of remote procedure calls (begin, transfer mail part(s), finalize) for a
 * single mailing operation.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2022)
 */
public class MailingNewImpl {
	
	/*
	 * local logging
	 */
	
	private static boolean logParamsAndResults = true;
	private static boolean logDebug = false;
	
	private static void logf(String pattern, Object... args) {
		System.out.printf(pattern, args);
	}
	
	private static void dlogf(String pattern, Object... args) {
		if (logDebug) {
			System.out.printf(pattern, args);
		}
	}
	
	/*
	 * initialization of the mail data volume and mail service
	 */
	
	@SuppressWarnings("unused")
	private static long networkId = -1;
	private static long machineId = -1;
	private static MailService mailService = null;
	
	/**
	 * Initialize the filing volume holding the mail files and create
	 * the mail service instance used by the Courier procedures.
	 * 
	 * @param network the network number serviced by the XNS services
	 * @param machine the network number of the machine hosting the mail service
	 * @param chsDatabase the Clearinghouse database to be used by the mail service
	 * @param existingMailService the mail-service to use (already created when
	 *   instantiating the "old" mail implementation
	 * @return {@code true} if the initialization was successful
	 */
	public static boolean init(long network, long machine, ChsDatabase chsDatabase, MailService existingMailService) {
		if (mailService != null) {
			logf("MS,Error: mail service already started\n");
			return false;
		}
		
		networkId = network;
		machineId = machine;
		mailService = existingMailService;
		if (mailService == null) {
			throw new IllegalStateException("MailingNewImpl not correctly initialized (no MailService instance available)");
		}
		
		return true;
	}
	
	/*
	 * ************************* Courier registration/deregistration
	 */
	
	// the instances for the 2 Courier programs for the XNS mail service
	private static MailTransport5 progMailTransport5;
	private static Inbasket2  progInbasket2;
	
	/**
	 * Register the Courier programs MailTransport and Inbasket.
	 */
	public static void register() {
		if (mailService == null) {
			throw new IllegalStateException("MailingImpl not correctly initialized (no MailService instance created)");
		}
		
		// create the MailTransport version 5 Courier program
		progMailTransport5 = new MailTransport5().setLogParamsAndResults(false);
		progMailTransport5.ServerPoll.use(MailingNewImpl::transport5_serverPoll);
		progMailTransport5.BeginPost.use(MailingNewImpl::transport5_beginPost);
		progMailTransport5.PostOneBodyPart.use(MailingNewImpl::transport5_postOneBodyPart);
		progMailTransport5.EndPost.use(MailingNewImpl::transport5_endPost);
		
		
		// create the Inbasket version 2 Courier program
		progInbasket2 = new Inbasket2().setLogParamsAndResults(false);
		progInbasket2.MailPoll.use(MailingNewImpl::inbasket2_mailPoll);
		progInbasket2.Logon.use(MailingNewImpl::inbasket2_logon);
		progInbasket2.Logoff.use(MailingNewImpl::inbasket2_logoff);
		progInbasket2.GetNextMail.use(MailingNewImpl::inbasket2_getNextMail);
		progInbasket2.RetrieveBodyParts.use(MailingNewImpl::inbasket2_retrieveBodyParts);
		progInbasket2.ChangeBodyPartStatus.use(MailingNewImpl::inbasket2_changeBodyPartStatus);
		progInbasket2.MailCheck.use(MailingNewImpl::inbasket2_mailCheck);
		progInbasket2.Delete.use(MailingNewImpl::inbasket2_delete);
		
		// register the programs with the Courier dispatcher
		CourierRegistry.register(progMailTransport5);
		CourierRegistry.register(progInbasket2);
	}
	
	/**
	 * Deregister the Courier programs MailTransport and Inbasket.
	 */
	public static void unregister() {
		CourierRegistry.unregister(MailTransport5.PROGRAM, MailTransport5.VERSION);
		CourierRegistry.unregister(Inbasket2.PROGRAM, Inbasket2.VERSION);
	}
	
	/**
	 * @return the MailTransport Courier program instance.
	 */
	public static MailTransport5 getMailTransport5Impl() {
		return progMailTransport5;
	}
	
	/**
	 * @return the Inbasket Courier program instance.
	 */
	public static Inbasket2 getInbasket2Impl() {
		return progInbasket2;
	}
	
	/*
	 * *************************
	 * ************************* implementation of MailTransport5 service procedures
	 * *************************
	 */
	
	private static final String blanks = "                                                                 ";
	
	/**
	 * Data container holding the data for a single mail during the
	 * post mail procedure sequence.
	 */
	private static class PostMailTransaction {
		
		private static long mailTransaction = 0x12340001;
		
		private final long transactionId;
		private final Time mailPostTime = Time.make().fromUnixMillisecs(System.currentTimeMillis());
		
		private ThreePartName senderName = null;
		private ListOfSimpleRName toNames = null;
		private ListOfSimpleRName ccNames = null;
		@SuppressWarnings("unused")
		private ListOfSimpleRName replyToNames = null;
		
		private Map<String, ThreePartName> allRecipients = new HashMap<>();
		private List<ThreePartName> recipients = new ArrayList<>();
		
		private STRING subject = null;
		private STRING mailText = null;
		private ByteSequence rawTextFile = null;
		
		private SerializedFile attachment = null;
		
		private PostMailTransaction() {
			this.transactionId = mailTransaction++;
		}
	}
	
	private static final int MAILATTR_TEXTFILECONTENT = 0x4200_0001;
	
	private static final Map<Long,PostMailTransaction> currentTransactions = new HashMap<>();
	
	private static synchronized PostMailTransaction createMailTransaction() {
		PostMailTransaction newTransaction = new PostMailTransaction();
		currentTransactions.put(newTransaction.transactionId, newTransaction);
		return newTransaction;
	}
	
	private static synchronized PostMailTransaction getMailTransaction(long id) {
		return currentTransactions.get(id);
	}
	
	private static synchronized void dropMailTransaction(PostMailTransaction t) {
		currentTransactions.remove(t.transactionId);
	}
	
	/*
	 *  serverPoll
	 *   = procedure 0
	 */
	private static void transport5_serverPoll(RECORD params, ServerPollResults results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			logf("##\n## procedure MailingNewImpl.transport5_serverPoll() -- params\n%s\n##\n", sb.toString());
		}
		
		// set return values
		results.address.add(mailService.getServiceAddress());
		results.serverName.from(mailService.getServiceName());
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			logf("##\n## procedure MailingNewImpl.transport5_serverPoll() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	/*
	 * beginPost
	 *   = procedure 1
	 */
	private static void transport5_beginPost(BeginPostParams params,BeginPostResults results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			logf("##\n## procedure MailingNewImpl.transport5_beginPost() -- params\n%s\n##\n", sb.toString());
		}
		
		// check the credentials:
		// - this procedure is called for the generic mail server ("Mail Service:CHServers:CHServers")
		// - not for this specific mail service (which the 1st mail service name in the clearinghouse database)
		// - so use the generic nameconversationKey
		// - and: only the machine id for *this* mail service works...
		Credentials credentials = params.credentials;
		Verifier verifier = params.verifier;
		StrongVerifier decodedVerifier = StrongVerifier.make();
		int[] decodedConversationKey = new int[4];
		ThreePartName senderName = mailService.checkCredentials( // throws an exception on invalid credentials
				mailService.getChsDatabase().getGenericMailServiceName(),
				mailService.getMachineId(),
				credentials,
				verifier,
				decodedConversationKey,
				decodedVerifier);
		
		// start the mail transaction for this post call sequence
		PostMailTransaction mailTransaction = createMailTransaction();
		mailTransaction.senderName = senderName; // just to be sure we have some sender name
		
		// check recipients
		List<InvalidName> invalidNames = new ArrayList<>();
		ChsDatabase chs = mailService.getChsDatabase();
		for (int i = 0; i < params.postingData.recipients.size(); i++) {
			Recipient recipient = params.postingData.recipients.get(i);
			if (recipient.name.getChoice() != RNameType.xns) {
				InvalidName invalidName = InvalidName.make();
				invalidName.id.set(recipient.recipientId.get());
				invalidName.reason.set(InvalidReason.IllegalName);
				if (recipient.report.get() == ReportType.none ) {
					invalidNames.add(invalidName);
				} else {
					results.invalidNames.add(invalidName);
				}
				continue;
			}
			ThreePartName rcpt = (ThreePartName)recipient.name.getContent();
			String rcptFqn = chs.resolveName(rcpt);
			List<Name> dlMemberNames;
			if (rcptFqn != null && mailService.hasMailbox(rcptFqn)) {
				Name rcptName = Name.make();
				rcptName.from(rcptFqn);
				mailTransaction.allRecipients.put(rcptFqn.toLowerCase(), rcptName);
			} else if (rcptFqn != null && (dlMemberNames = MailingOldImpl.getUserGroupMembersLcFqns(rcptFqn)) != null) {
				for (Name dlMember : dlMemberNames) {
					mailTransaction.allRecipients.put(dlMember.getString().toLowerCase(), dlMember);
				}
			} else {
				InvalidName invalidName = InvalidName.make();
				invalidName.id.set(recipient.recipientId.get());
				invalidName.reason.set(InvalidReason.NoSuchRecipient);
				if (recipient.report.get() == ReportType.none ) {
					invalidNames.add(invalidName);
				} else {
					results.invalidNames.add(invalidName);
				}
			}
		}
		if (!invalidNames.isEmpty()) {
			new InvalidRecipientsRecord(invalidNames).raise();
		}
		
		// set return values
		results.session.token.set(mailTransaction.transactionId);
		if (credentials.type.get() == CredentialsType.simple) {
			// return the initiators verifier 
			results.session.verifier.add().set(verifier.get(0).get());
		} else {
			// create a strong verifier based on the received verifier
			int[] conversationKey = decodedConversationKey; // session.getConversationKey();
			if (conversationKey != null && conversationKey.length == 4) {
				// xor-ing values
				long xorHostId = machineId; // the server machine, not(!) the remoteHostId extracted from the Logon request
				long rcptTimestampMachineId32Bits = (xorHostId >> 16) & 0xFFFFFFFFL; // left justified machine-id => upper 32 bits 
				long rcptTicksMachineId32Bits = (xorHostId & 0x0000FFFFL) << 16;     // left justified machine-id => lower 32 bits
				
				// new verifier values
				long newTicks = decodedVerifier.ticks.get() + 1;
				long newTimestamp = decodedVerifier.timeStamp.get();
				if (newTicks > 0xFFFFFFFFL) {
					newTicks = 0;
					newTimestamp++;
				}
				
				// plain (unencrypted) verifier with xor-ed values
				StrongVerifier verfr = StrongVerifier.make();
				verfr.ticks.set(newTicks ^ rcptTicksMachineId32Bits);
				verfr.timeStamp.set(newTimestamp ^ rcptTimestampMachineId32Bits);
				
				// encrypt verifier and transfer into results
				try {
					WireWriter writer = new WireWriter();
					verfr.serialize(writer);
					int[] sourceBytes = writer.getWords();
					int[] encrypted = StrongAuthUtils.xnsDesEncrypt(conversationKey, sourceBytes);
					for (int i = 0; i < encrypted.length; i++) {
						results.session.verifier.add().set(encrypted[i]);
					}
				} catch (Exception e) {
					// log and set no verifier => let the invoker decide if acceptable
					logf("** !! unable to serialize or encrypt the verifier in logon results: " + e.getMessage());
				}
			}
		}
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			logf("##\n## procedure MailingNewImpl.transport5_beginPost() -- results\n%s\n##\n", sb.toString());
		}
		
	}

	/*
	 * postOneBodyPart
	 *  = procedure 8
	 */
	private static void transport5_postOneBodyPart(PostOneBodyPartParams params, RECORD results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			logf("##\n## procedure MailingNewImpl.transport5_postOneBodyPart() -- params\n%s\n##\n", sb.toString());
		}
		
		// get the transaction, raising a SessionError courier exception if invalid...
		PostMailTransaction mt = getMailTransaction(params.session.token.get());
		if (mt == null) {
			new SessionErrorRecord(SessionProblem.handleInvalid).raise();
		}
		
		// get mail part content via bulk-data transfer
		int mailPartType = (int)(params.mailPartType.get() & 0x0000FFFFFFFF);
		String mailPartTypeName;
		switch(mailPartType) {
			case MailTransport5.mptEnvelope: mailPartTypeName = "mtpEnvelope"; break;
			case MailTransport5.mptAttachmentFolder: mailPartTypeName = "mptAttachmentFolder"; break;
			case MailTransport5.mptTextFile: mailPartTypeName = "mptTextFile"; break;
			case MailTransport5.mptAttachmentDoc: mailPartTypeName = "mptAttachmentDoc"; break;
			case MailTransport5.mptOtherFile: mailPartTypeName = "mptOtherFile"; break;
			case MailTransport5.mptNoteGV: mailPartTypeName = "mtpNoteGV"; break;
			case MailTransport5.mptNoteIA5: mailPartTypeName = "mtpNoteIA5"; break;
			case MailTransport5.mptPilotFile: mailPartTypeName = "mptPilotFile"; break;
			case MailTransport5.mptG3Fax: mailPartTypeName = "mptG3Fax"; break;
			case MailTransport5.mptTeletex: mailPartTypeName = "mptTeletex"; break;
			case MailTransport5.mptTelex: mailPartTypeName = "mptTelex"; break;
			case MailTransport5.mptNoteISO6937: mailPartTypeName = "mtpNoteISO6937"; break;
			case MailTransport5.mptInterpress: mailPartTypeName = "mptInterpress"; break;
			default: mailPartTypeName = String.format("unknown[ %d ]", params.mailPartType.get());
		}
		logf("\n--- mail part content ( type: %s ):\n", mailPartTypeName);
		if (mailPartType == MailTransport5.mptEnvelope) {
			// load the envelope from the bulk-stream
			SEQUENCE<Attribute> envelope = new SEQUENCE<>(Attribute::make);
			loadBulkStream(envelope, params.content);
			
			// log the received envelope
			if (logDebug) {
				StringBuilder sb = new StringBuilder();
				envelope.append(sb, "  ", "envelope");
				logf("%s\n\n", sb.toString());
			}
			
			// interpret the envelope and remember relevant fields in our mail-transaction
			try {
				for (int i = 0; i < envelope.size(); i++) {
					Attribute attr = envelope.get(i);
					int attrType = (int)(attr.type.get() & 0xFFFF_FFFFL);
					switch(attrType) {
					
					case MailTransport5.atSender: {
							CHOICE<RNameType> attrValue = attr.decodeData(MailTransport5.mkRName);
							if (attrValue.getChoice() != RNameType.xns) {
								new UndefinedErrorRecord(MailTransport5.SERVICE_ERROR_INVALID_ADDRESS_KIND).raise();
							}
							mt.senderName = (ThreePartName)attrValue.getContent();
						} break;
					
					case MailTransport5.atTo: {
							ListOfRName rNames = attr.decodeData(ListOfRName::make);
							mt.toNames = copyRNamesToSimpleRNames(rNames);
							for (int idx = 0; idx < mt.toNames.size(); idx++) {
								mt.recipients.add(mt.toNames.get(idx).name);
							}
						} break;
						
					case MailTransport5.atCopiesTo: {
							ListOfRName rNames = attr.decodeData(ListOfRName::make);
							mt.ccNames = copyRNamesToSimpleRNames(rNames);
							for (int idx = 0; idx < mt.ccNames.size(); idx++) {
								mt.recipients.add(mt.ccNames.get(idx).name);
							}
						} break;
						
					case MailTransport5.atReplyTo: {
							ListOfRName rNames = attr.decodeData(ListOfRName::make);
							mt.replyToNames = copyRNamesToSimpleRNames(rNames);
						} break;
						
					case MailTransport5.atSubject: {
							mt.subject = attr.decodeData(STRING::make);
						} break;
					
					}
				}
			} catch (EndOfMessageException e) {
				e.printStackTrace();
				new UndefinedErrorRecord(MailTransport5.DECODE_ERROR_ON_SERIALIZED_DATA).raise();
			}
		} else if (mailPartType == MailTransport5.mptAttachmentDoc) {
			// get the serialized document(-tree) from the bulk-stream
			SerializedFile attachment = SerializedFile.make();
			loadBulkStream(attachment, params.content);
			
			// log the document-(tree)
			if (logDebug) {
				StringBuilder sb = new StringBuilder();
				attachment.append(sb, "  ", "attached document");
				logf("%s\n\n", sb.toString());
			}
			
			// save the document(-tree) in our mail-transaction
			mt.attachment = attachment;
		} else {
			try {
				// get the uninterpreted bulk-stream
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ByteContentSource source = new ByteContentSource(params.content);
				byte[] buffer = new byte[512];
				int count = source.read(buffer);
				while(count > 0) {
					bos.write(buffer, 0, count);
					count = source.read(buffer);
				}
				byte[] mailPartContent = bos.toByteArray();
				
				// log the received content
				if (logDebug) {
					int byteLength = mailPartContent.length;
					StringBuilder sb = new StringBuilder();
					for (int i = 0; i < byteLength; i++) {
						int b = mailPartContent[i] & 0x00FF;
						if ((i % 16) == 0) {
							logf("%s\n              0x%03X :", sb.toString(), i);
							sb.setLength(0);
							sb.append("  ");
						}
						logf(" %02X", b);
						if (b >= 32 && b < 127) {
							sb.append((char)b);
						} else {
							sb.append("∙");
						}
					}
					int gap = byteLength % 16;
					String filler = (gap == 0) ? "" : blanks.substring(0, (16 - gap) * 3);
					logf("%s%s\n\n", filler, sb.toString());
				}
				
				// and now try to decode the mail part
				iWireStream iws = null;
				switch(mailPartType) {
				case MailTransport5.mptNoteGV:
				case MailTransport5.mptNoteIA5:
					iws = new ByteArrayWireInputStream(mailPartContent.length, mailPartContent);
					break;
				case MailTransport5.mptNoteISO6937:
					// not really supported so far: any non-ascii chars are ignored (dropped)
					int len = 0;
					for (int i = 0; i < mailPartContent.length; i++) {
						byte b = mailPartContent[i];
						if (b >= 0) {
							mailPartContent[len++] = b;
						}
					}
					iws = new ByteArrayWireInputStream(len, mailPartContent);
					break;
				case MailTransport5.mptTextFile: // Interlisp-D/LAFITE - TEdit formatted mail body
					// save the original mail body for Inbasket2 clients (in the hope the recipient knows the format, i.e. is a LAFITE mail client)
					mt.rawTextFile = new ByteSequence(mailPartContent);
					
					// save the unformatted text part as standard mail-text for Inbasket1 clients
					// (mailPartContent is a serialized file-tree, with the plain text being the file content
					// of the only file  which also has the 2 attributes 0x0011 (file-type, 2 = text) and 0x132F
					// (TEdit text formatting info)
					iWireStream textpartStream = new ByteArrayWireInputStream(mailPartContent);
					SerializedFile textFile = new SerializedFile(null, true); 
					textFile.deserialize(textpartStream);
					iws = new ByteArrayWireInputStream(textFile.file.content);
					break;
				}
				if (iws != null) {
					STRING s = STRING.make();
					s.deserialize(iws);
					mt.mailText = s;
				}
			} catch (EndOfMessageException e) {
				e.printStackTrace();
				new UndefinedErrorRecord(MailTransport5.DECODE_ERROR_ON_SERIALIZED_DATA).raise();
			}
		}
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			logf("##\n## procedure MailingNewImpl.transport5_postOneBodyPart() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	private static ListOfSimpleRName copyRNamesToSimpleRNames(ListOfRName rNames) {
		ListOfSimpleRName simpleRNames = ListOfSimpleRName.make();
		for (int idx = 0; idx < rNames.size(); idx++) {
			CHOICE<RNameType> rName = rNames.get(idx);
			if (rName.getChoice() != RNameType.xns) {
				new UndefinedErrorRecord(MailTransport5.SERVICE_ERROR_INVALID_ADDRESS_KIND).raise();
			}
			simpleRNames.add().name.from((ThreePartName)rName.getContent());
		}
		return simpleRNames;
	}
	
	private static void loadBulkStream(iWireData target, BulkData1.Source source) {
		boolean completeRead = false;
		try {
			target.deserialize(source.lockWirestream());
			completeRead = true;
		} catch (EndOfMessageException e) {
			e.printStackTrace();
			new UndefinedErrorRecord(MailTransport5.DECODE_ERROR_ON_SERIALIZED_DATA).raise();
		} finally {
			try {
				source.unlockWireStream(completeRead);
			} catch (Exception e) {
				logf("!!!! problem during: params.content.unlockWireStream(completeRead)\n");
				e.printStackTrace();
			}
		}
	}
	
	private static class ByteArrayWireInputStream extends WireBaseStream {
		
		private final byte[] content;
		private final int maxPos;
		private int currPos = 0;
		
		private ByteArrayWireInputStream(int firstWord, byte[] data) {
			this.content = new byte[data.length + 2];
			this.content[0] = (byte)((firstWord >> 8) & 0xFF);
			this.content[1] = (byte)(firstWord & 0xFF);
			for (int i = 0; i < data.length; i++) { this.content[i+2] = data[i]; }
			this.maxPos = Math.min(firstWord, data.length) + 2;
		}
		
		private ByteArrayWireInputStream(byte[] data) {
			this.content = data;
			this.maxPos = data.length;
		}
		
		private ByteArrayWireInputStream(Content stringContent) {
			int byteLen = stringContent.data.size() * 2;
			if (!stringContent.lastByteSignificant.get()) {
				byteLen--;
			}
			
			byte[] buffer = new byte[byteLen + 2]; // +2 for string length word
			buffer[0] = (byte)((byteLen >> 8) & 0xFF);
			buffer[1] = (byte)(byteLen & 0xFF);
			
			int pos = 2;
			for (int i = 0; i < (stringContent.data.size() - 1); i++) {
				int w = stringContent.data.get(i);
				buffer[pos++] = transl((w >> 8) & 0xFF);
				buffer[pos++] = transl(w & 0xFF);
			}
			int w = stringContent.data.get(stringContent.data.size() - 1);
			buffer[pos++] = transl((w >> 8) & 0xFF);
			if (stringContent.lastByteSignificant.get()) {
				buffer[pos++] = transl(w & 0xFF);
			}
			this.content = buffer;
			this.maxPos = buffer.length;
		}
		
		private static byte transl(int b) {
			if (b == 0x0A) { return (byte)0x0D; } // LAFITE/TEdit sends LF for line-end instead of Xerox usual CR
			return (byte)b;
		}

		@Override
		public void writeEOM() throws NoMoreWriteSpaceException { }

		@Override
		public void flush() throws NoMoreWriteSpaceException { }

		@Override
		public void beginStreamType(byte datastreamType) throws NoMoreWriteSpaceException { }

		@Override
		protected void putByte(int b) throws NoMoreWriteSpaceException { }

		@Override
		public boolean isAtEnd() {
			return (this.currPos >= this.maxPos);
		}

		@Override
		public boolean checkIfAtEnd() {
			return this.isAtEnd();
		}

		@Override
		public byte getStreamType() {
			return 0;
		}

		@Override
		protected int getByte() throws EndOfMessageException {
			return (this.currPos >= this.maxPos) ? 0 : this.content[this.currPos++] & 0x00FF;
		}		
	}
	
	private static class ByteSequence implements iWireData {
		
		public final LONG_CARDINAL byteLength = LONG_CARDINAL.make();
		private byte[] content = null;
		
		private ByteSequence(byte[] contentBytes) {
			this.content = contentBytes; // no copy => must be invariant
			this.byteLength.set((this.content == null) ? 0 : this.content.length);
		}
		
		public long length() {
			return (this.content == null) ? 0 : this.byteLength.get();
		}
		
		public byte[] getBytes() {
			return (this.content == null) ? new byte[0] : this.content;
		}
		
		public ByteSequence set(ByteSequence other) {
			this.content = other.getBytes(); // no copy => must be invariant
			this.byteLength.set((this.content == null) ? 0 : this.content.length);
			return this;
		}

		@Override
		public void serialize(iWireStream ws) throws NoMoreWriteSpaceException {
			this.byteLength.set((this.content == null) ? 0 : this.content.length);
			this.byteLength.serialize(ws);
			if (this.content != null) {
				for (int i = 0; i < this.content.length; i++) {
					ws.writeI8(this.content[i]);
				}
				if ((this.content.length & 1) != 0) { ws.writeI8(0); }
			}
		}

		@Override
		public void deserialize(iWireStream ws) throws EndOfMessageException {
			this.byteLength.deserialize(ws);
			this.content = new byte[(int)this.byteLength.get()];
			for (int i = 0; i < this.content.length; i++) {
				this.content[i] = (byte)ws.readI8();
			}
			if ((this.content.length & 1) != 0) { ws.readI8(); }
		}

		@Override
		public void serialize(iJsonWriter wr) {
			throw new IllegalStateException("JSON serializing not supported here");
		}

		@Override
		public void deserialize(iJsonReader rd) {
			throw new IllegalStateException("JSON deserializing not supported here");
		}

		@Override
		public StringBuilder append(StringBuilder to, String indent, String fieldName) {
			to.append(indent).append(fieldName).append(": ByteSequence[").append(this.length()).append("]");
			return to;
		}
		
		public void dump() {
			StringBuilder sb = new StringBuilder();
			byte[] bytes = this.getBytes();
			for (int i = 0; i < bytes.length; i++) {
				int b = bytes[i]& 0xFF;
				if ((i % 16) == 0) {
					logf("%s\n              0x%03X :", sb.toString(), i);
					sb.setLength(0);
					sb.append("  ");
				}
				logf(" %02X", b);
				if (b >= 32 && b < 127) {
					sb.append((char)b);
				} else {
					sb.append("∙");
				}
			}
			int gap = bytes.length % 16;
			String filler = (gap == 0) ? "" : blanks.substring(0, (16 - gap) * 3);
			logf("%s%s\n", filler, sb.toString());
		}
		
		public static ByteSequence make() { return new ByteSequence(null); }
	}
	
	/*
	 * postEnd
	 *  = procedure 9
	 */
	private static void transport5_endPost(EndPostParams params, EndPostResults results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			logf("##\n## procedure MailingNewImpl.transport5_endPost() -- params\n%s\n##\n", sb.toString());
		}
		
		// get the transaction, raising a SessionError courier exception if invalid...
		PostMailTransaction mt = getMailTransaction(params.session.token.get());
		if (mt == null) {
			new SessionErrorRecord(SessionProblem.handleInvalid).raise();
		}
		
		// collect recipients
		NameList allRecipients = NameList.make();
		ChsDatabase chs = mailService.getChsDatabase();
		for (ThreePartName rcpt : mt.recipients) {
			String rcptFqn = chs.resolveName(rcpt);
			List<Name> dlMemberNames;
			if (rcptFqn != null && mailService.hasMailbox(rcptFqn)) {
				Name rcptName = Name.make();
				rcptName.from(rcptFqn);
				allRecipients.addDistinct(rcptName);
			} else if (rcptFqn != null && (dlMemberNames = MailingOldImpl.getUserGroupMembersLcFqns(rcptFqn)) != null) {
				for (Name dlMember : dlMemberNames) {
					allRecipients.addDistinct(dlMember);
				}
			}
		}
		if (allRecipients.size() == 0) {
			if (logParamsAndResults) {
				StringBuilder sb = new StringBuilder();
				results.append(sb, "  ", "results");
				logf("##\n## procedure MailingNewImpl.transport5_endPost() -- results (NO valid RECIPIENTS)\n%s\n##\n", sb.toString());
			}
			return; // leave messageId as all-zeroes => (hopefully) NullID => (hopefully) message not sent
		}
		
		// create the items for the mail service
		SerializedFile mailContentFile = mt.attachment;
		if (mailContentFile == null) {
			// no attached serialized item => produce one with the attributes used by Star..VP2.x 
			mailContentFile = new SerializedFile(null, true); // force version = 2
			mailContentFile.file.content.lastByteSignificant.set(true); // leave the content  proper empty
			SEQUENCE<Attribute> attrs = mailContentFile.file.attributes.value;
			attrs.add().set(               FilingCommon.atAccessList,    FilingCommon.AccessList::make4, v -> v.defaulted.set(true));
			attrs.add().setAsTime(         FilingCommon.atBackedUpOn,    mt.mailPostTime.get());
			attrs.add().setAsCardinal(     FilingCommon.atChecksum,      0xFFFF);
			attrs.add().setAsChsName(      FilingCommon.atCreatedBy,     mt.senderName);
			attrs.add().setAsTime(         FilingCommon.atCreatedOn,     mt.mailPostTime.get());
			attrs.add().setAsBoolean(      FilingCommon.atIsDirectory,   false);
			attrs.add().set(               MailingCommon.subject,        STRING::make, v -> v.set(mt.subject));
			attrs.add().setAsLongCardinal( MailingCommon.bodySize,       0);
			attrs.add().setAsLongCardinal( FilingCommon.atStoredSize,    0);
			attrs.add().setAsLongCardinal( FilingCommon.atSubtreeSize,   0);
			attrs.add().setAsLongCardinal( MailingCommon.bodyType,       FilingCommon.tVPMailNote);
			attrs.add().setAsCardinal(     4402,                         0);
			attrs.add().setAsCardinal(     4401,                         1);
			attrs.add().setAsCardinal(     4348,                         11);
			attrs.add().setAsCardinal(     4403,                         0x005C009E);
			attrs.add().setAsBoolean(      MailingCommon.coversheetOn,   true);
			attrs.add().set(               MailingCommon.from,           NameList::make, v -> v.add().from(mt.senderName));
			attrs.add().set(               MailingCommon.to,             NameList::make, v -> copyNames(mt.toNames, v));
			attrs.add().set(               MailingCommon.comments,       STRING::make,   v -> v.set(mt.mailText));
			attrs.add().set(               MailingCommon.cc,             NameList::make, v -> copyNames(mt.ccNames, v));
			if (mt.rawTextFile != null) {
				attrs.add().set(           MAILATTR_TEXTFILECONTENT,     ByteSequence::make, v -> v.set(mt.rawTextFile));
			}
		} else {
			// check if all required Star..VP2.x attributes are present, adding the missing ones if necessary
			boolean hasMailFrom = false;
			boolean hasMailTo = false;
			boolean hasMailCc = false;
			boolean hasCoversheetOn = false;
			SEQUENCE<Attribute> attrs = mailContentFile.file.attributes.value;
			for (int i = 0; i < attrs.size(); i++) {
				int attributeType = (int)(attrs.get(i).type.get() & 0xFFFF_FFFFL);
				hasMailFrom |= (attributeType == MailingCommon.from);
				hasMailTo |= (attributeType == MailingCommon.to);
				hasMailCc |= (attributeType == MailingCommon.cc);
				hasCoversheetOn |= (attributeType == MailingCommon.coversheetOn);
			}
			if (!hasCoversheetOn) { attrs.add().setAsBoolean(MailingCommon.coversheetOn, true); }
			if (!hasMailFrom) { attrs.add().set(MailingCommon.from, NameList::make, v -> v.add().from(mt.senderName)); }
			if (!hasMailTo) { attrs.add().set(MailingCommon.to, NameList::make, v -> copyNames(mt.toNames, v)); }
			if (!hasMailCc) { attrs.add().set(MailingCommon.cc, NameList::make, v -> copyNames(mt.ccNames, v)); }
		}
		
		// create the mail
		iContentSource source = new MailService.ValueContentSource(mailContentFile);
		int[] mailId = mailService.postMail(mt.senderName, allRecipients, MailingCommon.ctSerializedFile, source);
		
		// set return values
		for(int i = 0; i < mailId.length; i++) {
			results.messageId.get(i).set(mailId[i]);
		}
		
		// we're done with this mail, so forget the mail transaction
		dropMailTransaction(mt);
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			logf("##\n## procedure MailingNewImpl.transport5_endPost() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	private static void copyNames(ListOfSimpleRName src, NameList dst) {
		if (src == null) { return; }
		for (int i = 0; i < src.size(); i++) {
			dst.add().from(src.get(i).name);
		}
	}
	
	/*
	 * *************************
	 * ************************* implementation of Inbasket2 service procedures
	 * *************************
	 */
	
	/*
	 * inbasketPoll
	 * 	= procedure 7
	 */
	private static void inbasket2_mailPoll(MailPollParams params, MailPollResults results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			logf("##\n## procedure MailingNewImpl.inbasket2_mailPoll() -- params\n%s\n##\n", sb.toString());
		}
		
		// check user credentials
		Credentials credentials = params.credentials;
		Verifier verifier = params.verifier;
		StrongVerifier decodedVerifier = StrongVerifier.make();
		int[] decodedConversationKey = new int[4];
		mailService.checkCredentials( // throws an exception on invalid credentials
				mailService.getServiceName(),
				mailService.getMachineId(),
				credentials,
				verifier,
				decodedConversationKey,
				decodedVerifier);
		
		// check if the mailbox is available
		ThreePartName reqMbxName = params.mailboxName;
		String mailboxFqn = mailService.getChsDatabase().resolveName(reqMbxName); // mbxName.getLcFqn();
		if (mailboxFqn == null || !mailService.hasMailbox(mailboxFqn)) {
			new AccessErrorRecord(AccessProblem.noSuchMailbox).raise();
		}
		logf("## inbasket2_mailPoll() for mailbox: %s\n", mailboxFqn);
		Name mbxName = Name.make();
		mbxName.from(mailboxFqn);
		
		// get the status data
		State pollState = State.make();
		mailService.getMailboxState(mbxName, pollState);
		results.lastIndex.set(pollState.lastIndex.get());
		results.newCount.set(pollState.newCount.get());
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			logf("##\n## procedure MailingNewImpl.inbasket2_mailPoll() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	/*
	 * logon
	 *   = procedure 5
	 */
	private static void inbasket2_logon(LogonParams params, LogonResults results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			logf("##\n## procedure MailingNewImpl.inbasket2_logon() -- params\n%s\n##\n", sb.toString());
		}
		
		// check user credentials
		Credentials credentials = params.credentials;
		Verifier verifier = params.verifier;
		StrongVerifier decodedVerifier = StrongVerifier.make();
		int[] decodedConversationKey = new int[4];
		mailService.checkCredentials( // throws an exception on invalid credentials
				mailService.getServiceName(),
				mailService.getMachineId(),
				credentials,
				verifier,
				decodedConversationKey,
				decodedVerifier);
		
		// check if the mailbox is available
		ThreePartName reqMbxName = params.mailboxName;
		String mailboxFqn = mailService.getChsDatabase().resolveName(reqMbxName); // mbxName.getLcFqn();
		if (mailboxFqn == null) {
			new AccessErrorRecord(AccessProblem.noSuchMailbox).raise();
		}
		logf("## logon to mailbox: %s\n", mailboxFqn);
		Name mbxName = Name.make();
		mbxName.from(mailboxFqn);
		if (!mailService.hasMailbox(mailboxFqn)) {
			new AccessErrorRecord(AccessProblem.noSuchMailbox).raise();
		}
		
		// start the new session
		long remoteHostId = credentials.remoteHostId.get();
		MailSession session;
		try {
			session = mailService.createSession(mbxName, remoteHostId, decodedConversationKey);
		} catch (IOException ioe) {
			new ServiceErrorRecord(ServiceProblem.serviceUnavailable).raise();
			return; // prevent the 'session not initialized' compiler warning as it cannot know that .raise() never returns...
		}
		
		// build results (verifier encrypted in analogy to FilingImpl, see comments in 'innerLogon')
		int sessionId = session.getSessionId();
		results.sessionId.set(sessionId);
		if (credentials.type.get() == CredentialsType.simple) {
			// return the initiators verifier 
			results.verifier.add().set(verifier.get(0).get());
		} else {
			// create a strong verifier based on the received verifier
			int[] conversationKey = session.getConversationKey();
			if (conversationKey != null && conversationKey.length == 4) {
				// xor-ing values
				long xorHostId = machineId; // the server machine, not(!) the remoteHostId extracted from the Logon request
				long rcptTimestampMachineId32Bits = (xorHostId >> 16) & 0xFFFFFFFFL; // left justified machine-id => upper 32 bits 
				long rcptTicksMachineId32Bits = (xorHostId & 0x0000FFFFL) << 16;     // left justified machine-id => lower 32 bits
				
				// new verifier values
				long newTicks = decodedVerifier.ticks.get() + 1;
				long newTimestamp = decodedVerifier.timeStamp.get();
				if (newTicks > 0xFFFFFFFFL) {
					newTicks = 0;
					newTimestamp++;
				}
				
				// plain (unencrypted) verifier with xor-ed values
				StrongVerifier verfr = StrongVerifier.make();
				verfr.ticks.set(newTicks ^ rcptTicksMachineId32Bits);
				verfr.timeStamp.set(newTimestamp ^ rcptTimestampMachineId32Bits);
				
				// encrypt verifier and transfer into results
				try {
					WireWriter writer = new WireWriter();
					verfr.serialize(writer);
					int[] sourceBytes = writer.getWords();
					int[] encrypted = StrongAuthUtils.xnsDesEncrypt(conversationKey, sourceBytes);
					for (int i = 0; i < encrypted.length; i++) {
						results.verifier.add().set(encrypted[i]);
					}
				} catch (Exception e) {
					// log and set no verifier => let the invoker decide if acceptable
					logf("** !! unable to serialize or encrypt the verifier in logon results: " + e.getMessage());
				}
			}
		}
		
		// fill the mail server's machine-id
		results.machineId.set(machineId);
		
		// fill in the mailbox counts
		results.totalCount.set(session.getMailCount());
		results.newCount.set(session.getNewMailCount());
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			logf("##\n## procedure MailingNewImpl.inbasket2_logon() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	/*
	 * logoff
	 *   = procedure 4
	 */
	private static void inbasket2_logoff(LogoffParams params, RECORD results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			logf("##\n## procedure MailingNewImpl.inbasket2_logoff() -- params\n%s\n##\n", sb.toString());
		}
		
		// get the session (we do not check the verifier, as we trust our clients...)
		MailSession session = mailService.getSession(params.sessionId.get());
		
		// close the session
		if (session != null) {
			mailService.dropSession(session);
		}
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			logf("##\n## procedure MailingNewImpl.inbasket2_logoff() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	/*
	 * getNextMail
	 *   = procedure 2
	 */
	
	/**
	 * Data container holding the mail parts of a mail in new format during
	 * the complete procedure sequence for transferring the mail to GlobalView.
	 */
	private static class MailData {
		private final long mailTransferId;
		private final int mailboxIndex;
		
		private byte[] envelopeBytes = null;
		private byte[] mailtextBytes = null;
		private byte[] attachmentBytes = null;
		
		private int mailtextPartType = MailTransport5.mptNoteGV;
		
		private final List<byte[]> parts = new ArrayList<>();
		
		private MailData(long id, int index) {
			this.mailTransferId = id;
			this.mailboxIndex = index;
		}
	}
	
	private static void inbasket2_getNextMail(GetNextMailParams params, GetNextMailResults results) {
		
		// logging callback used before returning
		UnaryOperator<String> retlog = m -> {
			if (logParamsAndResults) {
				if (m == null) { m = ""; }
				StringBuilder sb = new StringBuilder();
				results.append(sb, "  ", "results");
				logf("##\n## procedure MailingNewImpl.inbasket2_getNextMail() %s -- results\n%s\n##\n", m, sb.toString());
			}
			return null;
		};

		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			logf("##\n## procedure MailingNewImpl.inbasket2_getNextMail() -- params\n%s\n##\n", sb.toString());
		}
		
		// get the session (we do not check the verifier, as we trust our clients...)
		dlogf("  ... fetching mail session ...");
		MailSession session = mailService.getSession(params.sessionId.get());
		if (session == null) {
			logf("failed (invalid session id)\n");
			retlog.apply("[invalid mail session]");
			return;
		}
		
		// check for valid index for the mail in the inbox
		MailData prevMailData = session.getClientData();
		int index = (prevMailData == null) ? 0 : prevMailData.mailboxIndex + 1;
		dlogf("  ... mailIndex: %d\n", index);
		if (index >= session.getMailCount() || index < 0) {
			// not or no longer present => return empty results
			retlog.apply("[no more mails available]");
			return;
		}
		
		// get the mail content
		dlogf("  ... getting mailbox entry ... ");
		MailboxEntry me = session.getMailEntry(index);
		dlogf("%s\n", (me != null) ? "ok" : "failed (null)");
		if (me == null) {
			// not or no longer present => return empty results
			retlog.apply("[mail already deleted]");
			return;
		}
		dlogf("  ... loading mail content file ... ");
		ByteArrayOutputStream mailBos = new ByteArrayOutputStream();
		try {
			me.transferContent( istream -> {
				byte[] buffer = new byte[512];
				try {
					int count = istream.read(buffer);
					while (count > 0) {
						mailBos.write(buffer, 0, count);
						count = istream.read(buffer);
					}
				} catch (IOException e) {
					// ignore that for now
				}
			});
		} catch (IOException e) {
			// we must at least be able to read the mail file, so...
			retlog.apply("[unable to read mail content]");
			return;
		}
		byte[] mailContentBytes = mailBos.toByteArray();
		dlogf("%d bytes\n", mailContentBytes.length);
		
		// extract the relevant fields from the (old format) mail content
		dlogf("  ... deserializing mail content ... ");
		SerializedFile mailContentFile = SerializedFile.make();
		try {
			mailContentFile.deserialize(new ByteArrayWireInputStream(mailContentBytes));
		} catch (EndOfMessageException e) {
			// invalid mail file content??
			retlog.apply("[invalid mail file content]");
			return;
		}
		dlogf("ok\n");
		dlogf("  ... extracting relevant data\n");
		NameList xfrom = null;
		NameList xto = null;
		NameList xcc = null;
		NameList xreplyTo = null;
		STRING xsubject = null;
		STRING mailText = null;
		ByteSequence rawMailText = null;
		boolean xisFolderAttachment = false;
		SEQUENCE<Attribute> attrs = mailContentFile.file.attributes.value;
		for (int i = 0; i < attrs.size(); i++) {
			try {
				Attribute attr = attrs.get(i);
				int attributeType = (int)(attr.type.get() & 0xFFFF_FFFFL);
				switch(attributeType) {
				case MailingCommon.from:
					xfrom = attr.decodeData(NameList::make);
					dlogf("    ... found from\n");
					break;
				case MailingCommon.to:
					xto = attr.decodeData(NameList::make);
					dlogf("    ... found to\n");
					break;
				case MailingCommon.cc:
					xcc = attr.decodeData(NameList::make);
					dlogf("    ... found cc\n");
					break;
				case MailingCommon.replyTo:
					xreplyTo = attr.decodeData(NameList::make);
					dlogf("    ... found replyTo\n");
					break;
				case MailingCommon.subject:
					xsubject = attr.decodeData(STRING::make);
					dlogf("    ... found subject\n");
					break;
				case MailingCommon.comments:
					mailText = attr.decodeData(STRING::make);
					dlogf("    ... found comments\n");
					break;
				case FilingCommon.atIsDirectory:
					xisFolderAttachment = attr.getAsBoolean();
					dlogf("    ... found isDirectory\n");
					break;
				case MAILATTR_TEXTFILECONTENT:
					rawMailText = attr.decodeData(ByteSequence::make);
				}
			} catch (EndOfMessageException e) {
				// invalid mail file content??
				retlog.apply("[unable to extract attribute mail file content]");
				return;
			}
		}
		dlogf("  ... done extracting mail content data\n");
		if (xfrom == null || xfrom.size() == 0 || xto == null || xto.size() == 0) {
			retlog.apply("[missing one of from, to]");
			return;
		}
		
		// extract the relevant fields from the (old format) mail envelope
		dlogf("  ... loading old-format envelope ... ");
		ByteArrayOutputStream envBos = new ByteArrayOutputStream();
		try {
			me.transferPostboxEnvelope( istream -> {
				byte[] buffer = new byte[512];
				try {
					int count = istream.read(buffer);
					while (count > 0) {
						envBos.write(buffer, 0, count);
						count = istream.read(buffer);
					}
				} catch (IOException e) {
					// ignore that for now
				}
			});
		} catch (IOException e) {
			// we must at least be able to read the mail envelope, so...
			retlog.apply("[unable to read mail envelope]");
			return;
		}
		byte[] oldEnvelopeBytes = envBos.toByteArray();
		dlogf("ok -> %d bytes\n", oldEnvelopeBytes.length);
		dlogf("  ... deserializing old-format envelope ... ");
		EncodedList oldEnvelope = EncodedList.make();
		try {
			oldEnvelope.deserialize(new ByteArrayWireInputStream(oldEnvelopeBytes));
		} catch (EndOfMessageException e) {
			// invalid mail file envelope??
			retlog.apply("[invalid mail file envelope]");
			return;
		}
		dlogf("ok\n");
		StringBuilder envSb = new StringBuilder();
		oldEnvelope.append(envSb, "  ", "old-envelope");
		dlogf("+++ %s\n", envSb.toString());
		dlogf("  ... extracting mail-id ... ");
		MessageID xmailId = null;
		for (int i = 0; i < oldEnvelope.size(); i++) {
			Attribute attr = oldEnvelope.get(i);
			if (attr.type.get() == MailingCommon.atMtMessageID) {
				try { xmailId = attr.decodeData(MessageID::make); } catch (EndOfMessageException e) {
					// invalid mail file envelope??
					retlog.apply("[cannot decode messageId from mail file envelope]");
					return;
				}
			}
		}
		if (xmailId == null) {
			// invalid mail file envelope??
			retlog.apply("[invalid mail file envelope]");
			return;
		}
		dlogf("ok\n");
		MessageID mailId = xmailId;
		
		// synthesize a new format envelope from the old format mail data
		dlogf("  ... creating new-format envelope\n");
		NameList from = xfrom;
		NameList to = xto;
		NameList cc = xcc;
		NameList replyTo = xreplyTo;
		STRING subject = (xsubject == null) ? STRING.make() : xsubject;
		boolean isFolderAttachment = xisFolderAttachment;
		long mailCreatedOn = me.inboxEntry().getCreatedOn();
		SEQUENCE<Attribute> newEnvelope = new SEQUENCE<>(Attribute::make);
		newEnvelope.add().set(MailTransport5.atMessageID, SimpleIpMessageID::make, v -> {
			v.nameWithTag.name.from(from.get(0));
			v.uniqueString.set(Long.toOctalString(mailCreatedOn & 0xFFFF_FFFFL) + "." + Long.toOctalString(subject.get().hashCode() & 0xFFFF_FFFFL));
		});
		newEnvelope.add().set(MailTransport5.atSender, SimpleRName::make, v -> {
			v.name.from(from.get(0));
		});
		newEnvelope.add().set(MailTransport5.atFrom, ListOfSimpleRName::make, v -> {
			copyNames(from, v);
		});
		newEnvelope.add().set(MailTransport5.atTo, ListOfSimpleRName::make, v -> {
			copyNames(to, v);
		});
		if (cc != null && cc.size() > 0) {
			newEnvelope.add().set(MailTransport5.atCopiesTo, ListOfSimpleRName::make, v -> {
				copyNames(cc, v);
			});
		}
		if (replyTo != null && replyTo.size() > 0) {
			newEnvelope.add().set(MailTransport5.atReplyTo, ListOfSimpleRName::make, v -> {
				copyNames(replyTo, v);
			});
		}
		newEnvelope.add().set(MailTransport5.atSubject, STRING::make, v -> v.set(subject));
		dlogf("  ... done\n");

		
		// prepare the temp object holding the contents data to be delivered later
		// and create the serialized data for later use
		MailData mailData = new MailData(((long)session.getSessionId() & 0x0000_0000_FFFF_FFFFL) + index, index);
		
		dlogf("  ... serializing new-format envelope ...");
		WireWriter wireWriter = new WireWriter();
		try {
			newEnvelope.serialize(wireWriter);
		} catch (NoMoreWriteSpaceException e) {
			// should never happen => ignore
		}
		mailData.envelopeBytes = wireWriter.getBytes();
		dlogf("ok, %d bytes\n", mailData.envelopeBytes.length);
		
		if (rawMailText != null) {
			mailData.mailtextPartType = MailTransport5.mptTextFile;
			mailData.mailtextBytes = rawMailText.getBytes();
			dlogf("  ... created mail-text (comments) bytes from RAW mail-text with %d bytes\n", mailData.mailtextBytes.length);
		} else if (mailText != null && mailText.get() != null) {
			dlogf("  ... creating mail-text (comments) bytes ... ");
			wireWriter = new WireWriter();
			try {
				mailText.serialize(wireWriter);
			} catch (NoMoreWriteSpaceException e) {
				// should never happen => ignore
			}
			byte[] mailtextBytes = wireWriter.getBytes(2); // skip the length word
			if (mailtextBytes.length > 0) {
				if (mailtextBytes[mailtextBytes.length - 1] == 0x00) {
					mailtextBytes[mailtextBytes.length - 1] = ' ';
				}
				mailData.mailtextBytes = mailtextBytes;
				dlogf("ok, %d bytes\n", mailData.mailtextBytes.length);
			} else {
				dlogf(" empty mail-text, mail part NOT created\n");
			}
		}
		
		if (mailContentFile.file.content.data.size() > 0 || xisFolderAttachment) {
			mailData.attachmentBytes = mailContentBytes;
			dlogf("  ... added mail attachment, %d bytes\n", mailData.attachmentBytes.length);
		}
		
		// prepare the mailInfo-Attributes in the return object
		dlogf("  ... building mail-infos for return data\n");
		dlogf("    ... miMailServer ... ");
		results.mailInfos.value.add().set(Inbasket2.miMailServer, MiMailServer::make, v -> {
			v.name.from(mailService.getServiceName());
			v.mailTs.set(mailCreatedOn);
		});
		dlogf("ok\n");
		dlogf("    ... miMessageId ... ");
		results.mailInfos.value.add().set(Inbasket2.miMessageId, MailingCommon.MessageID::make, v -> {
			for (int i = 0; i < 5; i++) { v.get(i).set(mailId.get(i).get()); }
		});
		dlogf("ok\n");
		dlogf("    ... miWhatever ... ");
		results.mailInfos.value.add().set(Inbasket2.miWhatever, MiWhatever::make, v -> {
			v.value.set(4);
		});
		dlogf("ok\n");
		dlogf("    ... miMailparts ... ");
		long[] lengthSum = { 0 };
		results.mailInfos.value.add().set(Inbasket2.miMailparts, MiMailParts::make, v-> {
			// envelope (always present)
			MiMailPart envPart = v.add();
			envPart.mailPartType.set(MailTransport5.mptEnvelope);
			envPart.mailPartLength.set(mailData.envelopeBytes.length);
			lengthSum[0] = mailData.envelopeBytes.length;
			mailData.parts.add(mailData.envelopeBytes);
			dlogf("envelope ");
			
			// having mail-text?
			if (mailData.mailtextBytes != null) {
				MiMailPart mailTextPart = v.add();
				mailTextPart.mailPartType.set(mailData.mailtextPartType);
				mailTextPart.mailPartLength.set(mailData.mailtextBytes.length);
				lengthSum[0] += mailData.mailtextBytes.length;
				mailData.parts.add(mailData.mailtextBytes);
				dlogf("noteGV ");
			}
			
			// having mail-attachment?
			if (mailData.attachmentBytes != null) {
				MiMailPart mailTextPart = v.add();
				mailTextPart.mailPartType.set(isFolderAttachment ? MailTransport5.mptAttachmentFolder : 4 /*MailTransport5.mptAttachmentDoc*/);
				mailTextPart.mailPartLength.set(mailData.attachmentBytes.length);
				lengthSum[0] += mailData.attachmentBytes.length;
				mailData.parts.add(mailData.attachmentBytes);
				dlogf("attachment(%d) ", mailTextPart.mailPartType.get());
			}
		});
		dlogf("... ok\n");
		dlogf("    ... miTotalPartsLength ... ");
		results.mailInfos.value.add().set(Inbasket2.miTotalPartsLength, MiTotalPartsLength::make, v -> {
			v.totalLength.set(lengthSum[0]);
		});
		dlogf("ok\n");
		dlogf("    ... miUser0 ... ");
		results.mailInfos.value.add().set(Inbasket2.miSender0, MiSender::make, v -> {
			v.senderName.from(from.get(0));
		});
		dlogf("ok\n");
		dlogf("    ... miUser1 ... ");
		results.mailInfos.value.add().set(Inbasket2.miSender1, MiSender::make, v -> {
			v.senderName.from(from.get(0));
		});
		dlogf("ok\n");
		dlogf("  ... done\n");
		
		// fill the obscure array values in the return data
		dlogf("  ... adding obscure array ... ");
		if (mailData.mailtextBytes != null && mailData.attachmentBytes != null) {
			// all 3 mail parts
			int[] vals = { 0x0000, 0x0000, 0x0000, 0x0001, 0x0000, 0x0000, 0x0000, 0x0001, 0x0001, 0x0000, 0x0001, 0x0001 };
			for (int i = 0; i < vals.length; i++) { results.unknownSeq.add().set(vals[i]); }
		} else if (mailData.mailtextBytes == null && mailData.attachmentBytes == null) {
			// only 1 mail part (envelope)
			int[] vals = { 0x0000, 0x0001, 0x0000, 0x0000 };
			for (int i = 0; i < vals.length; i++) { results.unknownSeq.add().set(vals[i]); }
		} else {
			// 2 mail parts (envelope and mail-text or attachment) 
			int[] vals = { 0x0000, 0x0000, 0x0001, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000 };
			for (int i = 0; i < vals.length; i++) { results.unknownSeq.add().set(vals[i]); }
		}
		dlogf("%d words\n", results.unknownSeq.size());
		
		// mark the mail as received
		session.getService().moveFromNewToReceived(me);
		
		
		// done: remember the data container with the mail-parts for later transfer and add the id to the returned data
		session.setClientData(mailData);
		results.uniqueMailNo.set(mailData.mailTransferId);
		dlogf("  ... uniqueMailNo: 0x%08X\n", results.uniqueMailNo.get());
		
		// log outgoing data
		retlog.apply(null);
	}
	
	private static void copyNames(NameList src, ListOfSimpleRName dst) {
		if (src == null) { return; }
		for (int i = 0; i < src.size(); i++) {
			dst.add().name.from(src.get(i));
		}
	}
	
	/*
	 * retrieveBodyParts
	 *   = procedure 8
	 * (1 or more mail part content(s) are sent via bulk-data transfer to the invoker)
	 */
	private static void inbasket2_retrieveBodyParts(RetrieveBodyPartsParams params, RECORD results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			logf("##\n## procedure MailingNewImpl.inbasket2_retrieveBodyParts() -- params\n%s\n##\n", sb.toString());
		}
		
		// get the session (we do not check the verifier, as we trust our clients...)
		MailSession session = mailService.getSession(params.sessionId.get());
		
		// check if we are working with the current mail-data
		MailData mailData = session.getClientData();
		long requestedTransferId = params.uniqueMailNo.get();
		dlogf("  ... requestedTransferId = %d , currentTransferId = %d\n", requestedTransferId, mailData.mailTransferId);
		if (mailData.mailTransferId != requestedTransferId) {
			new OtherErrorRecord(OtherProblem.malformedMessage).raise();
		}
		
		// send the requested mail part(s)
		try {
			dlogf("  ... begin sending bulk-data\n");
			ByteContentSink sink = new ByteContentSink(params.content);
			try {
				for (int i = 0; i < params.mailPartIndices.size(); i++) {
					int idx = params.mailPartIndices.get(i).get();
					if (idx >= 0 && idx < mailData.parts.size()) {
						byte[] partBytes = mailData.parts.get(idx);
						dlogf("    ... sending mailPart[ %d ] ,  length: %d bytes\n", idx, partBytes.length);
						sink.write(partBytes, partBytes.length);
					}
				}
				if (params.mailPartIndices.size() == 0) {
					for (int idx = 0; idx < mailData.parts.size(); idx++) {
						byte[] partBytes = mailData.parts.get(idx);
						dlogf("    ... sending mailPart[ %d ] ,  length: %d bytes\n", idx, partBytes.length);
						sink.write(partBytes, partBytes.length);
					}
				}
			} finally {
				dlogf("  ... closing bulk channel\n");
				sink.write(null, 0); // signal EOF and cleanup
			}
		} catch (Exception e) {
			logf("  +++ error: %s - %s\n", e.getClass().getName(), e.getMessage());
		}
		dlogf("  ... finished sending bulk-data\n");
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			logf("##\n## procedure MailingNewImpl.inbasket2_retrieveBodyParts() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	/*
	 * changeBodyPartStatus
	 *   = procedure 3
	 */
	private static void inbasket2_changeBodyPartStatus(ChangeBodyPartStatusParams params, ChangeBodyPartStatusResults results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			logf("##\n## procedure MailingNewImpl.inbasket2_changeBodyPartStatus() -- params\n%s\n##\n", sb.toString());
		}
		
		// get the session (we do not check the verifier, as we trust our clients...)
		MailSession session = mailService.getSession(params.sessionId.get());
		
		// check if we are working with the current mail-data
		MailData mailData = session.getClientData();
		if (mailData.mailTransferId != params.uniqueMailNo.get()) {
			new OtherErrorRecord(OtherProblem.malformedMessage).raise();
		}
		
		// we do not handle single parts of the mail, but simply delete the whole mail!
		int idx = mailData.mailboxIndex;
		dlogf("  ... dropping mailbox-entry with index %d  ... \n", idx);
		MailboxEntry mail = session.getMailEntry(idx);
		if (mail != null) {
			ErrorRECORD mailError = mailService.dropMail(session, mail);
			if (mailError != null) {
				dlogf("failed\n");
			} else {
				dlogf("ok ... removing from session ... ");
				session.clearMailEntry(idx);
				dlogf("ok\n");
			}
		} else {
			dlogf("already droppedn!\n");
		}
		
		// tell the client we did as commanded
		results.deleted.set(true);
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			logf("##\n## procedure MailingNewImpl.inbasket2_changeBodyPartStatus() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	/*
	 * mailCheck
	 *  = procedure 6
	 */
	public static void inbasket2_mailCheck(MailCheckParams params, MailCheckResults results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			logf("##\n## procedure MailingNewImpl.inbasket2_mailCheck() -- params\n%s\n##\n", sb.toString());
		}
		
		// get the session (we do not check the verifier, as we trust our clients...)
		MailSession session = mailService.getSession(params.sessionId.get());
		
		// set result values
		results.totalCount.set(session.getMailCount());
		results.newCount.set(session.getNewMailCount());
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			logf("##\n## procedure MailingNewImpl.inbasket2_mailCheck() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	/*
	 * delete
	 *  = procedure 1
	 */
	public static void inbasket2_delete(DeleteParams params, RECORD results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			logf("##\n## procedure MailingNewImpl.inbasket2_delete() -- params\n%s\n##\n", sb.toString());
		}
		
		// get the session (we do not check the verifier, as we trust our clients...)
		MailSession session = mailService.getSession(params.sessionId.get());
		
		// delete the inbasket mails in the specified range
		long offset = (params.range.low.get() == params.sessionId.get()) ? params.sessionId.get() : 1; // Interlisp-D/Medley offsets the range with the session-id for some obscure reason 
		int firstIndex = Math.max(0, (int)(params.range.low.get() - offset));
		int limitIndex = Math.min(session.getMailCount(), (int)(params.range.high.get() - offset) + 1);
		System.out.printf("++ offset = %d , firstIndex = %d , limitIndex = %d , mail-count = %d\n", offset, firstIndex, limitIndex, session.getMailCount());
		for (int i = firstIndex; i < limitIndex; i++) {
			MailboxEntry mail = session.getMailEntry(i);
			if (mail != null) {
				System.out.printf("++ dropping mail '%s'\n", mail.inboxEntry().getName());
				ErrorRECORD mailError = mailService.dropMail(session, mail);
				if (mailError != null) {
					mailError.raise();
					return; // will never happen...
				}
				session.clearMailEntry(i);
			}
		}
	}

}
