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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import dev.hawala.xns.level3.courier.CourierRegistry;
import dev.hawala.xns.level3.courier.ErrorRECORD;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.StreamOf;
import dev.hawala.xns.level3.courier.WireWriter;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;
import dev.hawala.xns.level4.common.AuthChsCommon.Credentials;
import dev.hawala.xns.level4.common.AuthChsCommon.CredentialsType;
import dev.hawala.xns.level4.common.AuthChsCommon.Name;
import dev.hawala.xns.level4.common.AuthChsCommon.StrongVerifier;
import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;
import dev.hawala.xns.level4.common.AuthChsCommon.Verifier;
import dev.hawala.xns.level4.chs.CHEntries0;
import dev.hawala.xns.level4.common.ChsDatabase;
import dev.hawala.xns.level4.common.StrongAuthUtils;
import dev.hawala.xns.level4.filing.ByteContentSink;
import dev.hawala.xns.level4.filing.ByteContentSource;
import dev.hawala.xns.level4.filing.fs.Volume;
import dev.hawala.xns.level4.filing.fs.iErrorRaiser;
import dev.hawala.xns.level4.mailing.Inbasket1.CacheStatus;
import dev.hawala.xns.level4.mailing.Inbasket1.DeleteParams;
import dev.hawala.xns.level4.mailing.Inbasket1.InbasketPollParams;
import dev.hawala.xns.level4.mailing.Inbasket1.InbasketPollResults;
import dev.hawala.xns.level4.mailing.Inbasket1.IndexErrorRecord;
import dev.hawala.xns.level4.mailing.Inbasket1.ListParams;
import dev.hawala.xns.level4.mailing.Inbasket1.LocateParams;
import dev.hawala.xns.level4.mailing.Inbasket1.LocateResults;
import dev.hawala.xns.level4.mailing.Inbasket1.LogoffParams;
import dev.hawala.xns.level4.mailing.Inbasket1.LogoffResults;
import dev.hawala.xns.level4.mailing.Inbasket1.LogonParams;
import dev.hawala.xns.level4.mailing.Inbasket1.LogonResults;
import dev.hawala.xns.level4.mailing.Inbasket1.MailCheckParams;
import dev.hawala.xns.level4.mailing.Inbasket1.MailCheckResults;
import dev.hawala.xns.level4.mailing.Inbasket1.RetrieveParams;
import dev.hawala.xns.level4.mailing.Inbasket1.RetrieveResults;
import dev.hawala.xns.level4.mailing.Inbasket1.SessionErrorRecord;
import dev.hawala.xns.level4.mailing.Inbasket1.TransferErrorRecord;
import dev.hawala.xns.level4.mailing.MailTransport4.InvalidRecipientsErrorRecord;
import dev.hawala.xns.level4.mailing.MailTransport4.PostParams;
import dev.hawala.xns.level4.mailing.MailTransport4.PostResults;
import dev.hawala.xns.level4.mailing.MailTransport4.ServerPollParams;
import dev.hawala.xns.level4.mailing.MailTransport4.ServerPollResults;
import dev.hawala.xns.level4.mailing.MailTransport4.ServiceErrorRecord;
import dev.hawala.xns.level4.mailing.MailingCommon.AccessErrorRecord;
import dev.hawala.xns.level4.mailing.MailingCommon.AccessProblem;
import dev.hawala.xns.level4.mailing.MailingCommon.ConnectionErrorRecord;
import dev.hawala.xns.level4.mailing.MailingCommon.ConnectionProblem;
import dev.hawala.xns.level4.mailing.MailingCommon.ListElementRecord;
import dev.hawala.xns.level4.mailing.MailingCommon.MessageStatus;
import dev.hawala.xns.level4.mailing.MailingCommon.NameList;
import dev.hawala.xns.level4.mailing.MailingCommon.ServiceProblem;
import dev.hawala.xns.level4.mailing.MailingCommon.SessionProblem;
import dev.hawala.xns.level4.mailing.MailingCommon.TransferProblem;
import dev.hawala.xns.level4.mailing.MailingCommon.UndeliveredName;
import dev.hawala.xns.level4.mailing.MailingCommon.UndeliveredNameType;

/**
 * Implementation of the Courier programs MailTransport4 and
 * Inbasket1, providing mailing support for Viewpoint and XDE.
 * <p>
 * This module also initializes the unique mail service instance and
 * forwards the Courier calls to the mentioned Courier programs
 * to the mail service. 
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2020)
 */
public class MailingOldImpl {
	
	/*
	 * local logging
	 */
	
	private static boolean logParamsAndResults = false;
	
	private static void log(String pattern, Object... args) {
		System.out.printf(pattern + "\n", args);
	}
	
	/*
	 * initialization of the mail data volume and mail service
	 */
	
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
	 * @param mailboxesVolumePath the OS file system path of the filing volume where
	 *   the mail files will be stored.
	 * @return {@code true} if the initialization was successful
	 */
	public static boolean init(long network, long machine, ChsDatabase chsDatabase, String mailboxesVolumePath) {
		if (mailService != null) {
			log("MS,Error: mail service already started\n");
			return false;
		}
		
		networkId = network;
		machineId = machine;
		
		// the name of the "one" XNS mail service in a Dodo network
		String svcName = chsDatabase.getMailServiceFqn();
		
		// content of the minimal root folders file for the mail Filing volume
		// (as this volume is reserved for mailing, we take the liberty to force things here)
		String initRootFolders
					= "Mailboxes\t" + svcName + "\n"
					+ "Mailfiles\t" + svcName + "\n";
		
		log("Starting mail service '%s' (mailboxes volume directory: %s)", svcName, mailboxesVolumePath);
		try {
			// check the filing volume root path and ensure that the toplevel folders are present (or created)
			File mailboxesDir = new File(mailboxesVolumePath);
			if (!mailboxesDir.exists()) {
				if (!mailboxesDir.mkdirs()) {
					log("MS,Error: unable to create mailBoxes directory '%s', aborting startup\n", mailboxesVolumePath);
					return false;
				}
			}
			File rootFoldersLst = new File(mailboxesDir, "root-folder.lst");
			try (FileOutputStream fos = new FileOutputStream(rootFoldersLst)) {
				fos.write(initRootFolders.getBytes());
			}
			
			// open the filing volume for the mail files
			Volume volume = Volume.openVolume(mailboxesVolumePath, new ErrorRaiser());
			
			// create the mailservice instance
			mailService = new MailService(networkId, machineId, svcName, chsDatabase, volume);
		} catch (Exception e) {
			log("Error: unable to open mailboxes volume, cause: %s", e.getMessage());
			return false;
		}
		log("Mail service '%s' started", svcName);
		
		return (mailService != null);
	}
	
	/*
	 * ************************* Courier registration/deregistration
	 */
	
	// the instances for the 2 Courier programs for the XNS mail service
	private static MailTransport4 progMailTransport;
	private static Inbasket1      progInbasket;
	
	/**
	 * Register the Courier programs MailTransport and Inbasket.
	 */
	public static void register() {
		if (mailService == null) {
			throw new IllegalStateException("MailingImpl not correctly initialized (no MailService instance created)");
		}
		
		// create the MailTransport version 4 Courier program
		progMailTransport = new MailTransport4().setLogParamsAndResults(false);
		progMailTransport.ServerPoll.use(MailingOldImpl::transport_serverPoll);
		progMailTransport.Post.use(MailingOldImpl::transport_post);
		
		// create the Inbasket version 1 Courier program
		progInbasket = new Inbasket1().setLogParamsAndResults(false);
		progInbasket.Logon.use(MailingOldImpl::inbasket_logon);
		progInbasket.Logoff.use(MailingOldImpl::inbasket_logoff);
		progInbasket.MailCheck.use(MailingOldImpl::inbasket_mailCheck);
		progInbasket.Retrieve.use(MailingOldImpl::inbasket_retrieve);
		progInbasket.Delete.use(MailingOldImpl::inbasket_delete);
		progInbasket.InbasketPoll.use(MailingOldImpl::inbasket_inbasketPoll);
		progInbasket.Locate.use(MailingOldImpl::inbasket_locate);
		progInbasket.List.use(MailingOldImpl::inbasket_list);
		
		// register the programs with the Courier dispatcher
		CourierRegistry.register(progMailTransport);
		CourierRegistry.register(progInbasket);
	}
	
	/**
	 * Deregister the Courier programs MailTransport and Inbasket.
	 */
	public static void unregister() {
		CourierRegistry.unregister(MailTransport4.PROGRAM, MailTransport4.VERSION);
		CourierRegistry.unregister(Inbasket1.PROGRAM, Inbasket1.VERSION);
	}
	
	/**
	 * @return the MailTransport Courier program instance.
	 */
	public static MailTransport4 getMailTransportImpl() {
		return progMailTransport;
	}
	
	/**
	 * @return the Inbasket Courier program instance.
	 */
	public static Inbasket1 getInbasketImpl() {
		return progInbasket;
	}
	
	/**
	 * @return the mail service instance.
	 */
	public static MailService getMailService() {
		return mailService;
	}
	
	/*
	 * ************************* implementation of MailTransport service procedures
	 */
	
	/*
	 *  serverPoll
	 *   = procedure 0
	 */
	private static void transport_serverPoll(ServerPollParams params, ServerPollResults results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			log("##\n## procedure MailingImpl.transport_serverPoll() -- params\n%s\n##\n", sb.toString());
		}
		
		// check the credentials:
		// - this procedure is called for the generic mail server ("Mail Service:CHServers:CHServers")
		// - not for 'this' specific mail service
		// - so use the generic name
		// - and: only the empty machine id works...
		Credentials credentials = params.authPair.credentials;
		Verifier verifier = params.authPair.verifier;
		StrongVerifier decodedVerifier = StrongVerifier.make();
		int[] decodedConversationKey = new int[4];
		mailService.checkCredentials(
				mailService.getChsDatabase().getGenericMailServiceName(),
				0L,
				credentials,
				verifier,
				decodedConversationKey,
				decodedVerifier);
		
		// if we are here, the credentials are valid, so inform the caller of us
		results.willingness.set(MailTransport4.mostWilling);
		results.address.add(mailService.getServiceAddress());
		results.returnVerifier.clear();
		results.serverName.from(mailService.getServiceName());
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			log("##\n## procedure MailingImpl.transport_serverPoll() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	/*
	 *  post
	 *   = procedure 1
	 */
	private static void transport_post(PostParams params, PostResults results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			log("##\n## procedure MailingImpl.transport_post() -- params\n%s\n##\n", sb.toString());
			}
		
		// check the credentials:
		// - this procedure is called for the generic mail server ("Mail Service:CHServers:CHServers")
		// - not for this specific mail service (which the 1st mail service name in the clearinghouse database)
		// - so use the generic name
		// - and: only the machine id for *this* mail service works...
		Credentials credentials = params.authPair.credentials;
		Verifier verifier = params.authPair.verifier;
		StrongVerifier decodedVerifier = StrongVerifier.make();
		int[] decodedConversationKey = new int[4];
		ThreePartName senderName = mailService.checkCredentials( // throws an exception on invalid credentials
				mailService.getChsDatabase().getGenericMailServiceName(),
				mailService.getMachineId(),
				credentials,
				verifier,
				decodedConversationKey,
				decodedVerifier);
		
		// check the recipients
		NameList allRecipients = NameList.make();
		ChsDatabase chs = mailService.getChsDatabase();
		for (int i = 0; i < params.recipients.size(); i++) {
			Name rcpt = params.recipients.get(i);
			String rcptFqn = chs.resolveName(rcpt);
			List<Name> dlMemberNames;
			if (rcptFqn != null && mailService.hasMailbox(rcptFqn)) {
				Name rcptName = Name.make();
				rcptName.from(rcptFqn);
				allRecipients.addDistinct(rcptName);
			} else if (params.allowDLRecipients.get() 
						&& rcptFqn != null
						&& (dlMemberNames = getUserGroupMembersLcFqns(rcptFqn)) != null) {
				for (Name dlMember : dlMemberNames) {
					allRecipients.addDistinct(dlMember);
				}
			} else {
				UndeliveredName undelivered = UndeliveredName.make();
				undelivered.reason.set(UndeliveredNameType.noSuchRecipient);
				undelivered.name.from(rcpt);
				results.invalidNames.add(undelivered);
			}
		}
		
		// if invalid recipients are not allowed and we have some or if all recipients are invalid: throw error...
		if ( (results.invalidNames.size() > 0  && !params.postIfInvalidNames.get())
			 || results.invalidNames.size() == params.recipients.size()) {
			InvalidRecipientsErrorRecord err = new InvalidRecipientsErrorRecord();
			for (int i = 0; i < results.invalidNames.size(); i++) {
				err.nameList.add(results.invalidNames.get(i));
			}
			err.raise();
		}
		
		// so create the mail
		try {
			ByteContentSource source = new ByteContentSource(params.content);
			if (allRecipients.size() > 0) {
				int[] mailId = mailService.postMail(senderName, allRecipients, params.contentsType.get(), source);
				for(int i = 0; i < mailId.length; i++) {
					results.msgID.get(i).set(mailId[i]);
				}
			} else {
				source.read(null); // abort bulk-data transfer
			}
		} catch (EndOfMessageException e) {
			new ConnectionErrorRecord(ConnectionProblem.otherCallProblem).raise();
		}
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			log("##\n## procedure MailingImpl.transport_post() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	/**
	 * Resolve the mailboxes (users) identified by a user-group, possibly
	 * expanding sub-groups.
	 * <p>
	 * In difference to users specified directly in a mail post, user-group
	 * entries which have no mailbox (i.e. which are not a user) do not
	 * generate an error, but will simply be ignored when producing the result.
	 * </p>
	 * <p>
	 * The generated list may contain duplicates, it is the responsibility
	 * of the invoker to skip duplicate entries.
	 * </p>
	 * @param grpFqn the full-qualifies name of the group to resolve
	 * @return the expanded mailbox list for the group and (possibly
	 *   recursively) sub-groups.
	 */
	public static List<Name> getUserGroupMembersLcFqns(String grpFqn) {
		ThreePartName grpName = Name.make().from(grpFqn);
		try {
			// get the group members from the chs database
			List<ThreePartName> members = mailService.getChsDatabase().getEntryGroupMembers(grpName, CHEntries0.members, null);
			
			// filter for members having a mail box
			List<Name> memberNames = new ArrayList<>();
			for (ThreePartName member : members) {
				String memberFqn = member.getLcFqn();
				if (mailService.hasMailbox(memberFqn)) {
					// this is a user
					Name memberName = Name.make();
					memberName.from(member);
					memberNames.add(memberName);
				} else {
					// try to resolve sub-groups
					List<Name> subMembers = getUserGroupMembersLcFqns(memberFqn);
					if (subMembers != null) {
						memberNames.addAll(subMembers);
					}
				}
			}
			
			// done
			return memberNames;
		} catch(Exception e) {
			return null; // signal "not a valid group"
		}
	}
	
	/*
	 * ************************* implementation of Inbasket service procedures
	 */
	
	/*
	 * logon
	 * 	= procedure 5
	 */
	private static void inbasket_logon(LogonParams params, LogonResults results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			log("##\n## procedure MailingImpl.inbasket_logon() -- params\n%s\n##\n", sb.toString());
		}
		
		// check user credentials
		Credentials credentials = params.mbx.creds.credentials;
		Verifier verifier = params.mbx.creds.verifier;
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
		Name reqMbxName = params.mbx.name;
		String mailboxFqn = mailService.getChsDatabase().resolveName(reqMbxName); // mbxName.getLcFqn();
		if (mailboxFqn == null) {
			new AccessErrorRecord(AccessProblem.noSuchMailbox).raise();
		}
		log("## logon to mailbox: %s\n", mailboxFqn);
		Name mbxName = Name.make();
		mbxName.from(mailboxFqn);
		if (!mailService.hasMailbox(mailboxFqn)) {
			new AccessErrorRecord(AccessProblem.noSuchMailbox).raise();
		}
		
		// prevent concurrent sessions to the same mailbox if sharing is disallowed
		if (!params.allowSharing.get() && mailService.hasSession(mailboxFqn)) {
			new AccessErrorRecord(AccessProblem.mailboxBusy).raise();
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
		results.session.token.set(sessionId);
		if (credentials.type.get() == CredentialsType.simple) {
			// return the initiators verifier 
			results.session.verifier.add().set(verifier.get(0).get());
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
						results.session.verifier.add().set(encrypted[i]);
					}
				} catch (Exception e) {
					// log and set no verifier => let the invoker decide if acceptable
					log("** !! unable to serialize or encrypt the verifier in logon results: " + e.getMessage());
				}
			}
		}
		results.cacheStatus.set(CacheStatus.invalid);
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			log("##\n## procedure MailingImpl.inbasket_logon() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	/*
	 * mailCheck
	 * 	= procedure 6
	 */
	private static void inbasket_mailCheck(MailCheckParams params, MailCheckResults results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			log("##\n## procedure MailingImpl.inbasket_mailCheck() -- params\n%s\n##\n", sb.toString());
		}
		
		// get the session (we do not check the verifier, as we trust our clients...)
		MailSession session = mailService.getSession(params.session.token.get());
		if (session == null) {
			new SessionErrorRecord(SessionProblem.handleInvalid).raise();
		}
		
		// build the results
		mailService.getMailboxState(session.getUser(), results.checkState);
		results.checkAgainWithin.set(60);
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			log("##\n## procedure MailingImpl.inbasket_mailCheck() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	/*
	 * retrieve
	 * 	= procedure 8
	 */
	private static void inbasket_retrieve(RetrieveParams params, RetrieveResults results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			log("##\n## procedure MailingImpl.inbasket_retrieve() -- params\n%s\n##\n", sb.toString());
		}
		
		// get the session (we do not check the verifier, as we trust our clients...)
		MailSession session = mailService.getSession(params.session.token.get());
		if (session == null) {
			new SessionErrorRecord(SessionProblem.handleInvalid).raise();
		}
		
		// check if the mail is (still) there (the expected contents type is ignored so far)
		int mailIndex = params.message.get() - 1; // the index is 1-based
		if (mailIndex < 0 || mailIndex >= session.getMailCount() || session.getMailEntry(mailIndex) == null) {
			new IndexErrorRecord(params.message.get()).raise();
		}
		
		// OK, so get the mail...
		MailboxEntry mail = session.getMailEntry(mailIndex);
		ErrorRECORD mailError = null;
		try {
			ByteContentSink sink = new ByteContentSink(params.contents);
			mailError = mailService.readMail(mail, sink, results.transportEnv, results.inbasketEnv);
		} catch (NoMoreWriteSpaceException e) {
			log("##  Inbasket.Retrieve() => %s : %s\n", e.getClass().getName(), e.getMessage());
			mailError = new TransferErrorRecord(TransferProblem.aborted);
		}
		if (mailError != null) {
			mailError.raise();
		}
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			log("##\n## procedure MailingImpl.inbasket_retrieve() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	/*
	 * delete
	 * 	= procedure 1
	 */
	private static void inbasket_delete(DeleteParams params, RECORD results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			log("##\n## procedure MailingImpl.inbasket_delete() -- params\n%s\n##\n", sb.toString());
		}
		
		// get the session (we do not check the verifier, as we trust our clients...)
		MailSession session = mailService.getSession(params.session.token.get());
		if (session == null) {
			new SessionErrorRecord(SessionProblem.handleInvalid).raise();
		}
		
		// delete the inbasket mails in the specified range
		int firstIndex = Math.max(0, params.range.first.get() - 1);
		int limitIndex = Math.min(session.getMailCount(), params.range.last.get());
		for (int i = firstIndex; i < limitIndex; i++) {
			MailboxEntry mail = session.getMailEntry(i);
			if (mail != null) {
				ErrorRECORD mailError = mailService.dropMail(session, mail);
				if (mailError != null) {
					mailError.raise();
					return; // will never happen...
				}
				session.clearMailEntry(i);
			}
		}
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			log("##\n## procedure MailingImpl.inbasket_delete() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	/*
	 * logoff
	 * 	= procedure 4
	 */
	private static void inbasket_logoff(LogoffParams params, LogoffResults results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			log("##\n## procedure MailingImpl.inbasket_logoff() -- params\n%s\n##\n", sb.toString());
		}
		
		// get the session (we do not check the verifier, as we trust our clients...)
		MailSession session = mailService.getSession(params.session.token.get());
		
		// close the session
		if (session != null) {
			mailService.dropSession(session);
		}
		
		// return an empty cache-verifier (when it will be checked later, we will return "invalid cache" anyway)
		results.cacheVerifier.get(0).set(0);
		results.cacheVerifier.get(1).set(0);
		results.cacheVerifier.get(2).set(0);
		results.cacheVerifier.get(3).set(0);
		
		// done
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			log("##\n## procedure MailingImpl.inbasket_logoff() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	/*
	 * inbasketPoll
	 * 	= procedure 7
	 */
	private static void inbasket_inbasketPoll(InbasketPollParams params, InbasketPollResults results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			log("##\n## procedure MailingImpl.inbasket_inbasketPoll() -- params\n%s\n##\n", sb.toString());
		}
		
		// check user credentials
		Credentials credentials = params.mbx.creds.credentials;
		Verifier verifier = params.mbx.creds.verifier;
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
		Name reqMbxName = params.mbx.name;
		String mailboxFqn = mailService.getChsDatabase().resolveName(reqMbxName); // mbxName.getLcFqn();
		if (mailboxFqn == null || !mailService.hasMailbox(mailboxFqn)) {
			new AccessErrorRecord(AccessProblem.noSuchMailbox).raise();
		}
		log("## Inbasket.poll for mailbox: %s\n", mailboxFqn);
		Name mbxName = Name.make();
		mbxName.from(mailboxFqn);
		
		// get the status data
		mailService.getMailboxState(mbxName, results.pollState);
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			log("##\n## procedure MailingImpl.inbasket_inbasketPoll() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	/*
	 * locate
	 * 	= procedure 3
	 */
	private static void inbasket_locate(LocateParams params, LocateResults results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			log("##\n## procedure MailingImpl.inbasket_locate() -- params\n%s\n##\n", sb.toString());
		}
		
		// get the session (we do not check the verifier, as we trust our clients...)
		MailSession session = mailService.getSession(params.session.token.get());
		if (session == null) {
			new SessionErrorRecord(SessionProblem.handleInvalid).raise();
		}
		
		// locate the first mail in the requested status
		MessageStatus status = params.status.get();
		int index = 0; // "not found", as indizes start with 1
		for (int i = 0; i < session.getMailCount(); i++) {
			MailboxEntry me = session.getMailEntry(i);
			if (me.messageStatus() == status) {
				index = i + 1;
				break;
			}
		}
		
		// build results
		results.index.set(index);
		
		// log outgoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			log("##\n## procedure MailingImpl.inbasket_locate() -- results\n%s\n##\n", sb.toString());
		}
	}
	
	/*
	 * list
	 * 	= procedure 2
	 */
	private static void inbasket_list(ListParams params, RECORD results) {
		// log ingoing data
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			params.append(sb, "  ", "params");
			log("##\n## procedure MailingImpl.inbasket_list() -- params\n%s\n##\n", sb.toString());
		}
		
		// get the session (we do not check the verifier, as we trust our clients...)
		MailSession session = mailService.getSession(params.session.token.get());
		if (session == null) {
			new SessionErrorRecord(SessionProblem.handleInvalid).raise();
		}
		
		// get the mail attributes to deliver for a mail
		List<Long> reqAttributes = new ArrayList<>();
		for (int i = 0; i < params.selections.mailAttributes.size(); i++) {
			reqAttributes.add(params.selections.mailAttributes.get(i).get());
		}
		
		// collect the data for the requested mail index range
		StreamOf<ListElementRecord> bulkData = new StreamOf<>(0, 1, 1, ListElementRecord::make);
		for (int mailboxIndex = params.range.first.get(); mailboxIndex <= params.range.last.get(); mailboxIndex++) {
			ListElementRecord elem = mailService.produceListEntry(
					session,
					mailboxIndex,
					params.selections.transportEnvelope.get(),
					params.selections.inbasketEnvelope.get(),
					reqAttributes);
			if (elem != null) {
				bulkData.add(elem);
			}
		}
		
		// send the mail data as bulk-data-transfer
		try {
			params.listing.send(bulkData, false);
		} catch (NoMoreWriteSpaceException e) {
			// ignore if aborted...
		}
		
		// log outgoing data (in fact none)
		if (logParamsAndResults) {
			StringBuilder sb = new StringBuilder();
			results.append(sb, "  ", "results");
			log("##\n## procedure MailingImpl.inbasket_list() -- (bulk-data sent, no results)\n##\n");
		}
	}
	
	/*
	 * utilities
	 */
	
	/**
	 * Error raiser, mapping Filing-Volume problems to IllegalStateException.
	 */
	private static class ErrorRaiser implements iErrorRaiser {
		
		private void log(String msg) {
			System.out.println("MS,Error: " + msg);
		}

		@Override
		public void fileNotFound(long fileID, String msg) {
			this.log(msg);
			throw new IllegalStateException(String.format("Internal error, fileNotFound:%s", msg));
		}

		@Override
		public void duplicateFilenameForChildrenUniquelyNamed(String msg) {
			this.log(msg);
			throw new IllegalStateException(String.format("Internal error, dupliateFilename:%s", msg));
		}

		@Override
		public void notADirectory(long fileID, String msg) {
			this.log(msg);
			throw new IllegalStateException(String.format("Internal error, notADirectory:%s", msg));
		}

		@Override
		public void wouldCreateLoopInHierarchy(String msg) {
			this.log(msg);
			throw new IllegalStateException(String.format("Internal error, wouldCreateLoopInHierarchy:%s", msg));
		}

		@Override
		public void operationNotAllowed(String msg) {
			this.log(msg);
			throw new IllegalStateException(String.format("Internal error, operationNotAllowed:%s", msg));
		}

		@Override
		public void serviceUnavailable(String msg) {
			this.log(msg);
			throw new IllegalStateException(String.format("Internal error, serviceUnavailable:%s", msg));
		}

		@Override
		public void attributeValueError(int attributeType, String msg) {
			this.log(msg);
			throw new IllegalStateException(String.format("Internal error, attributeValueErrir:%s", msg));
		}

		@Override
		public void fileContentDamaged(String msg) {
			this.log(msg);
			throw new IllegalStateException(String.format("Internal error, fileContentDamaged:%s", msg));
		}
	}

}
