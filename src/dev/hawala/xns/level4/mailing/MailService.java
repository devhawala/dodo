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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import dev.hawala.xns.Log;
import dev.hawala.xns.level3.courier.CARDINAL;
import dev.hawala.xns.level3.courier.ErrorRECORD;
import dev.hawala.xns.level3.courier.LONG_CARDINAL;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.WireBaseStream;
import dev.hawala.xns.level3.courier.WirePacketReader;
import dev.hawala.xns.level3.courier.WireSeqOfUnspecifiedReader;
import dev.hawala.xns.level3.courier.WireWriter;
import dev.hawala.xns.level3.courier.iWireData;
import dev.hawala.xns.level3.courier.iWireDynamic;
import dev.hawala.xns.level3.courier.iWireStream;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;
import dev.hawala.xns.level4.chs.CHEntries0;
import dev.hawala.xns.level4.common.AuthChsCommon;
import dev.hawala.xns.level4.common.AuthChsCommon.Credentials;
import dev.hawala.xns.level4.common.AuthChsCommon.CredentialsType;
import dev.hawala.xns.level4.common.AuthChsCommon.Name;
import dev.hawala.xns.level4.common.AuthChsCommon.NetworkAddress;
import dev.hawala.xns.level4.common.AuthChsCommon.StrongVerifier;
import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;
import dev.hawala.xns.level4.common.AuthChsCommon.Verifier;
import dev.hawala.xns.level4.common.ChsDatabase;
import dev.hawala.xns.level4.filing.ByteContentSink;
import dev.hawala.xns.level4.filing.FilingCommon.Attribute;
import dev.hawala.xns.level4.filing.fs.FileEntry;
import dev.hawala.xns.level4.filing.fs.UninterpretedAttribute;
import dev.hawala.xns.level4.filing.fs.Volume;
import dev.hawala.xns.level4.filing.fs.Volume.Session;
import dev.hawala.xns.level4.filing.fs.iContentSink;
import dev.hawala.xns.level4.filing.fs.iContentSource;
import dev.hawala.xns.level4.mailing.Inbasket.State;
import dev.hawala.xns.level4.mailing.Inbasket.TransferErrorRecord;
import dev.hawala.xns.level4.mailing.MailTransport.ServiceErrorRecord;
import dev.hawala.xns.level4.mailing.MailingCommon.AuthenticationErrorRecord;
import dev.hawala.xns.level4.mailing.MailingCommon.AuthenticationProblem;
import dev.hawala.xns.level4.mailing.MailingCommon.EncodedList;
import dev.hawala.xns.level4.mailing.MailingCommon.ListElementRecord;
import dev.hawala.xns.level4.mailing.MailingCommon.MailHeaderData;
import dev.hawala.xns.level4.mailing.MailingCommon.MessageID;
import dev.hawala.xns.level4.mailing.MailingCommon.NameList;
import dev.hawala.xns.level4.mailing.MailingCommon.Postmark;
import dev.hawala.xns.level4.mailing.MailingCommon.ServiceProblem;
import dev.hawala.xns.level4.mailing.MailingCommon.TransferProblem;

/**
 * Mail service implementation providing the functionality available
 * to Courier programs to create mails and manipulate mailboxes.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2020)
 */
public class MailService {
	
	/*
	 * The mail service uses a Filing volume to store the mails. 
	 * 
	 * A single mail results in a set of files stored at several places:
	 * -> one mail file holding the raw mail content (as transferred as bulk-data
	 *    transfer when the mail is created by a client) as well as some metadata
	 *    in a file property
	 * -> one mail reference file for each recipient in the recipients mailbox, having
	 *    the mail envelope as file content
	 *    
	 * The mail files are stored in the file drawer (volume root folder) "Mailfiles".
	 * 
	 * A mailbox is a folder named with the 3-part-name of the user owning the
	 * mailbox, all mailbox folders are located in the file drawer "Mailboxes".
	 * 
	 * The first 3 of the 5 word of a mail identification are constant and generated more
	 * or less randomly when the filing volume is initialized by the mail service. The
	 * last 2 words of the identification are the linear numbering of the mails.
	 * The last mail-id used is stored as attribute of the volume root folder "Mailfiles".
	 * 
	 * The mail file is named with the millisecond timestamp of the mail posting and
	 * the 2 words of the mail number (i.e. last 2 words of the mail id), separated by dashes.
	 * 
	 * The name of mail reference file is composed of the referenced mail file name and an
	 * additional suffix specifying the status of the mail in the mailbox, the suffix is
	 * one of :new , :known , :received
	 * 
	 * The metadata stored in a file attribute of the mail file contains a reference counter
	 * holding the number of the still existing references from mailbox files. When a mail
	 * is deleted in a mailbox, the mailbox reference file is deleted and the reference counter
	 * on the mail file is decremented. When this count becomes 0, the mail file is also deleted.
	 */
	
	// file types
	private static final long ftMailbox   = 90001L; // user mailbox directories 
	private static final long ftMailFile  = 90002L; // content file for a posted mail
	private static final long ftInboxMail = 90003L; // a mail in a mailbox referencing a ftMailFile
	
	// file attribute types
	private static final long atLastMailId     = 90010L; // attribute on folder 'Mailfiles' having the last MessageID = TYPE[5]
	private static final long atPostedMetaData = 90100L; // attribute on an ftMailFile having the mail meta-data
	
	// filename suffixes for mailbox entry files
	public static final String INBOX_SUFFIX_NEW      = ":new";
	public static final String INBOX_SUFFIX_KNOWN    = ":known";
	public static final String INBOX_SUFFIX_RECEIVED = ":received";
	
	// construction data
	private final long networkId;
	private final long machineId;
	private final NetworkAddress serviceAddress = NetworkAddress.make();
	private final String serviceNameFqn;
	private final Name serviceName = Name.make();
	private final ChsDatabase chsDatabase;
	private final Volume mailsVolume;
	
	// folder where the mail boxes directories reside
	private final FileEntry mailboxesFolder;
	
	// folder where the mail files reside
	private final FileEntry mailfilesFolder;
	
	// mapping => user-lcFqn => mailbox-directory
	private final Map<String,FileEntry> mailboxes = new HashMap<>();
	
	// sessionId => session
	private final Map<Integer,MailSession> activeSessions = new HashMap<>();
	
	/**
	 * Constructor for the mail service, initializing the filing volume if necessary
	 * and ensuring that each user has a mailbox folder. 
	 * 
	 * @param networkId the network id where the mail service is located
	 * @param machineId the processor id of the machine running this mail service
	 * @param serviceFqn the name of the mail service
	 * @param chsDatabase the clearinghouse database to use
	 * @param mailsVolume the (already opened) Filing volume to use for storing mails/mailboxes
	 */
	public MailService(long networkId, long machineId, String serviceFqn, ChsDatabase chsDatabase, Volume mailsVolume) {
		// initialize the finals
		this.networkId = networkId;
		this.machineId = machineId;
		this.serviceNameFqn = serviceFqn;
		this.chsDatabase = chsDatabase;
		this.mailsVolume = mailsVolume;
		this.serviceAddress.network.set(networkId);
		this.serviceAddress.host.set(machineId);
		this.serviceName.from(serviceFqn);
		
		// get the mailfiles folder, initializing it if necessary (i.e. first startup)
		this.mailfilesFolder = this.mailsVolume.openByName("Mailfiles", null, null, null, this.serviceNameFqn);
		if (this.mailfilesFolder.getUninterpretedAttribute(atLastMailId) == null) {
			long nowMs = System.currentTimeMillis();
			Random rand = new Random(nowMs);
			UninterpretedAttribute initAttr = new UninterpretedAttribute(atLastMailId);
			initAttr
				.add(rand.nextInt(0xFFFF))
				.add((int)(nowMs & 0xFFFFL))
				.add((int)((nowMs >> 16) & 0xFFFFL))
				.add(0)
				.add(0);
			try (Session session = this.mailsVolume.startModificationSession()) {
				session.updateFileAttributes(
					this.mailfilesFolder,
					Arrays.asList( fe -> fe.getUninterpretedAttributes().add(initAttr) ),
					this.serviceNameFqn);
			} catch (Exception e) {
				throw new IllegalStateException("unable to initialize mail volume: " + e.getMessage());
			}
			System.out.printf(
				"initialized mailfiles last-mail-id to: 0x %04X %04X %04X %04X %04X\n",
				initAttr.get(0), initAttr.get(1), initAttr.get(2), initAttr.get(3), initAttr.get(4));
		}
		
		// get the mailboxes base folder
		this.mailboxesFolder = this.mailsVolume.openByName("Mailboxes", null, null, null, this.serviceNameFqn);
		long rootId = mailboxesFolder.getFileID();
		
		// get all users and check if their mailbox exists, if found saving it and removing from the names list
		List<String> allUsers = chsDatabase.findFullQualifiedNames(".*", CHEntries0.user);
		List<String> allLcFqns = new ArrayList<>();
		Map<String,String> lc2fqn = new HashMap<>();
		for (String user : allUsers) {
			String lcUser = user.toLowerCase();
			allLcFqns.add(lcUser);
			lc2fqn.put(lcUser, user);
		} 
		this.mailsVolume.listDirectory(
				mailboxesFolder.getFileID(),
				fe -> {
					String lcName = fe.getLcName();
					if (allLcFqns.contains(lcName)) {
						allLcFqns.remove(lcName);
						this.mailboxes.put(lcName, fe);
						System.out.printf("Mail: user '%s' : currently %d mail(s) in mailbox\n", fe.getName(), fe.getChildren().getChildren().size());
					}
					return false;
				}, 1, this.serviceNameFqn);

		// create the mailboxes for users which do not have one
		if (!allLcFqns.isEmpty()) {
			try (Session session = this.mailsVolume.startModificationSession()) {
				for (String lcUser : allLcFqns) {
					String mailboxName = lc2fqn.get(lcUser);
					System.out.printf("Mail: user '%s' : creating mailbox ...", mailboxName);
					FileEntry newMailbox = session.createFile(
							rootId,                  // parentID
							true,                    // isDirectory
							mailboxName,             // name
							null,                    // version
							ftMailbox,               // type
							this.serviceNameFqn,     // creatingUser
							Collections.emptyList(), // valueSetters
							null                     // contentSource
							);
					this.mailboxes.put(lcUser, newMailbox);
					System.out.printf(" done\n");
				}
			} catch (Exception e) {
				System.out.printf(" error: %s\n", e.getMessage());
			}
		}
	}

	/**
	 * Check if the given full qualified name identifies a user having a mailbox.
	 * @param fqn the full qualified name to check
	 * @return {@code true} if the name has a mailbox, i.e. is a user
	 */
	public boolean hasMailbox(String fqn) {
		return mailboxes.containsKey(fqn.toLowerCase());
	}
	
	/**
	 * Check if there is a valid (not timed-out) session for the given
	 * full qualified name.
	 * 
	 * @param mailboxFqn the full qualified name of the mailbox to check
	 * @return {@code true} if there is a valid session for the mailbox
	 */
	public synchronized boolean hasSession(String mailboxFqn) {
		String key = mailboxFqn.toLowerCase();
		boolean found = false;
		for (MailSession session : this.activeSessions.values()) {
			if (session.getUserLcFqn().equals(key)) {
				if (session.isTimedOut()) {
					this.innerDropSession(session);
					continue;
				}
				found = true;
			}
		}
		return found;
	}
	
	/**
	 * Free the given session.
	 * @param session the mail session to drop.
	 */
	public synchronized void dropSession(MailSession session) {
		this.innerDropSession(session);
	}
	
	// !! internal method, must be called from synchronized
	private void innerDropSession(MailSession session) {
		this.activeSessions.remove(session.getSessionId());
	}
	
	/**
	 * Get the mail session with the given id.
	 * 
	 * @param sessionId the session-id of the session to get
	 * @return the mail session or {@code null} if the id is invalid
	 */
	public synchronized MailSession getSession(int sessionId) {
		return this.activeSessions.get(sessionId);
	}
	
	/**
	 * Start a new mail session for a mailbox, no matter if another session already
	 * exists for the same mailbox.
	 * 
	 * @param username the username resp. mailbox name to create the session for
	 * @param remoteHostId the machine-id of the client system opening the session
	 * @param conversationKey the conversation key from the credentials used to open
	 * 		the session
	 * @return the new mail session
	 * @throws IOException
	 */
	public synchronized MailSession createSession(Name username, Long remoteHostId, int[] conversationKey) throws IOException {
		// check for valid username
		String userLcFqn = username.getLcFqn();
		if (!this.hasMailbox(userLcFqn)) {
			return null;
		}
		
		// load the current mails in the mailbox, sharing data with other
		// sessions possibly holding the same mails
		FileEntry mailbox = this.mailboxes.get(userLcFqn);
		List<FileEntry> inboxMails = this.mailsVolume.listDirectory(mailbox.getFileID(), fe -> true, 0xFFFF, this.serviceNameFqn);
		List<MailboxEntry> mailboxEntries = new ArrayList<>();
		for (FileEntry fe : inboxMails) {
			mailboxEntries.add(getMailboxEntry(fe));
		}
		
		// create the session and done
		MailSession session = new MailSession(this, username, remoteHostId, conversationKey, mailboxEntries);
		this.activeSessions.put(session.getSessionId(), session);
		return session;
	}
	
	// !! internal method, must be called from synchronized
	private MailboxEntry getMailboxEntry(FileEntry mailFile) throws IOException {
		// the plain name of the mail
		String contentFileName = getMailFilename(mailFile);
		
		// look for this mail in the other sessions, creating a data-sharing
		// copy if found
		for (MailSession session : this.activeSessions.values()) {
			for (int i = 0; i < session.getMailCount(); i++) {
				MailboxEntry e = session.getMailEntry(i);
				if (e == null) { continue; }
				if (e.isMail(contentFileName)) {
					return new MailboxEntry(e, mailFile);
				}
			}
		}
		
		// find the content file for the mail
		List<FileEntry> contentFiles = this.mailsVolume.findFiles(
				this.mailfilesFolder.getFileID(),
				f -> contentFileName.equalsIgnoreCase(f.getName()),
				1, // maxCount,
				1, // maxDepth,
				this.serviceNameFqn);
		if (contentFiles.size() != 1) {
			return null; // there is nothing appropriate...
		}
		FileEntry contentFile = contentFiles.get(0);
		
		// build the new mailbox entry
		return new MailboxEntry(
				mailFile,
				contentFileName,
				sink -> this.mailsVolume.retrieveContent(contentFile.getFileID(), sink, this.serviceNameFqn),
				sink -> this.mailsVolume.retrieveContent(mailFile.getFileID(), sink, this.serviceNameFqn)
				);
	}
	
	/**
	 * Create a new mail posted to one or more recipients.
	 * 
	 * @param sender the user sending the mail
	 * @param recipients list of already verified recipients of the mail, having
	 * 		groups (aka distribution-lists) already expanded
	 * @param contentsType the type id for the mail content
	 * @param source the filing source for the mail content
	 * @return the mail identification for the created mail
	 */
	public int[] postMail(ThreePartName sender, NameList recipients, long contentsType, iContentSource source) {
		try (Session fSession = this.mailsVolume.startModificationSession()) {
			// get the next free mail-id
			int[] mailId = this.allocateMailId(fSession);
			String mailFileName = String.format("%08X-%04X-%04X", System.currentTimeMillis(), mailId[3], mailId[4]);
			
			// create the meta-data for the mail content file
			MailMetaData postedMetaData = new MailMetaData();
			postedMetaData.sender.from(sender);
			for (int i = 0; i < recipients.size();i++) {
				postedMetaData.recipients.add(recipients.get(i));
			}
			postedMetaData.contentsType.set(contentsType);
			postedMetaData.mailboxReferences.set(recipients.size());
			UninterpretedAttribute postedMetaDataAttr = new UninterpretedAttribute(atPostedMetaData);
			postedMetaData.to(postedMetaDataAttr);
			
			// create the mail content file
			FileEntry mailFile = fSession.createFile(
									mailfilesFolder.getFileID(), // . parentId
									false, // ....................... isDirectory
									mailFileName, // ................ name
									1, // ........................... version
									ftMailFile, // .................. type
									this.serviceNameFqn, // ......... creatingUser
									Arrays.asList( fe -> fe.getUninterpretedAttributes().add(postedMetaDataAttr) ),
									source);
			
			// create the envelope
			EncodedList env = EncodedList.make();
			env.add(Attribute.make().set(MailingCommon.atMtPostmark, Postmark::make, a -> {
				a.server.from(this.chsDatabase.getMailServiceFqn());
				a.time.fromUnixMillisecs(System.currentTimeMillis());
			}));
			env.add(Attribute.make().set(MailingCommon.atMtMessageID, MessageID::make, a -> {
				for(int i = 0; i < mailId.length; i++) {
					a.get(i).set(mailId[i]);
				}
			}));
			env.add(Attribute.make().setAsLongCardinal(MailingCommon.atMtContentsType, contentsType));
			env.add(Attribute.make().setAsLongCardinal(MailingCommon.atMtContentsSize, mailFile.getDataSize()));
			env.add(Attribute.make().setAsChsName(MailingCommon.atMtOriginator, sender));
			env.add(Attribute.make().setAsChsName(MailingCommon.atMtReturnToName, sender));
			ValueContentSource envelopeSource = new ValueContentSource(env);
			
			// create the inbox-references in the mailboxes of all recipients
			String inboxMailFileName = mailFileName + INBOX_SUFFIX_NEW;
			for (int i = 0; i < recipients.size(); i++) {
				Name rcpt = recipients.get(i);
				FileEntry mailbox = this.mailboxes.get(rcpt.getLcFqn());
				if (mailbox == null) { continue; } // should never happen...
				fSession.createFile(
					mailbox.getFileID(), // ......... parentId 
					false, // ....................... isDirectory
					inboxMailFileName, // ........... name
					1, // ........................... version
					ftInboxMail, // ................. type
					this.serviceNameFqn, // ......... creatingUser
					Collections.emptyList(),
					envelopeSource.reset());
			}
			
			// done
			return mailId;
		} catch (IllegalStateException e) {
			Log.C.printf("MS", "postMail() -> rejecting with ServiceError[serviceUnavailable], cause: %s\n", e.getMessage());
			new ServiceErrorRecord(ServiceProblem.serviceUnavailable).raise();
		} catch (InterruptedException e) {
			Log.C.printf("MS", "postMail() -> rejecting with ServiceError[serviceUnavailable], cause: %s\n", e.getMessage());
			new ServiceErrorRecord(ServiceProblem.serviceUnavailable).raise();
		} catch (Exception e) {
			Log.C.printf("MS", "postMail() -> rejecting with ServiceError[serviceUnavailable], cause: %s\n", e.getMessage());
			new ServiceErrorRecord(ServiceProblem.serviceUnavailable).raise();
		}
		return null; // keep the compiler happy (errors raised do not return)
	}
	
	/**
	 * Get the number new mails and total number of mails in a mailbox. 
	 * 
	 * @param mailbox the name of the mailbox to check
	 * @param state the state (Courier-) object to fill with the count data
	 * @return {@code true} if the mailbox exists, {@code false} otherwise
	 */
	public boolean getMailboxState(ThreePartName mailbox, State state) {
		String mailboxLcFqn = mailbox.getLcFqn();
		FileEntry mbx = this.mailboxes.get(mailboxLcFqn);
		if (mbx == null) {
			state.lastIndex.set(0);
			state.newCount.set(0);
			return false;
		}
		
		List<FileEntry> mails = mbx.getChildren().getChildren();
		int mailCount = 0;
		int mailNew = 0;
		for (FileEntry fe : mails) {
			if (fe.getName().endsWith(INBOX_SUFFIX_NEW)) {
				mailNew++;
			}
			mailCount++;
		}
		state.lastIndex.set(mailCount);
		state.newCount.set(mailNew);
		return true;
	}
	
	/**
	 * Access a mail and retrieve its various data.
	 * 
	 * @param mail the mailbox entry to access
	 * @param mailContentSink the destination to send the mail content to
	 * @param transportEnvelope the (Courier-) object to fill with the mail-post envelope
	 * @param inbasketEnvelope the (Courier-) object to fill with the mailbox-status envelope
	 * @return {@code null} if accessing the mail was successful, otherwise the Courier error
	 * 		holding the problem to report to the client (in which case the possibly partially
	 * 		filled / transmitted data must be discarded)
	 */
	public ErrorRECORD readMail(
						MailboxEntry mail,
						ByteContentSink mailContentSink,
						EncodedList transportEnvelope,
						EncodedList inbasketEnvelope) {
		
		// build the inbasket envelope
		inbasketEnvelope.add(Attribute.make().set(
				MailingCommon.atIbMessageStatus,
				MailingCommon.mkMessageStatus,
				a-> a.set(mail.messageStatus())
				));
		
		// get the transport envelope from the mail reference file
		try {
			ValueContentSink envMaker = new ValueContentSink(transportEnvelope);
			mail.transferPostboxEnvelope(envMaker);
		} catch (IOException e) {
			return new ServiceErrorRecord(ServiceProblem.serviceUnavailable);
		}
		
		// transfer mail content
		try {
			mail.transferContent(mailContentSink);
		} catch (IOException e) {
			return new TransferErrorRecord(TransferProblem.aborted);
		}
		
		// successfully done
		return null;
	}
	
	/**
	 * Delete the given mail in the given mail session.
	 * 
	 * @param session the mail session for the mailbox where the mail is located
	 * @param mail the mail to be deleted
	 * @return {@code null} if deleting the mail in the mailbox was successful,
	 * 		otherwise the Courier error holding the problem to report to the client
	 */
	public synchronized ErrorRECORD dropMail(MailSession session, MailboxEntry mail) {
		/*
		 * 1. in all other sessions for the same mailbox: .resetInboxEntry() for the same postboxId
		 * 2. delete the mailbox-file in this session
		 * 3. possibly delete the contentFile if the last mailbox-file referencing it was deleted
		 */
		
		// check if this mailbox-entry was already deleted (possibly in a different session)
		FileEntry mailFile = mail.inboxEntry();
		if (mailFile == null) {
			return null; // already deleted, not an error
		}
		
		// in all other sessions for the same mailbox: .resetInboxEntry() for the same postboxId
		String mailboxName = session.getUserLcFqn();
		String mailId = mail.postboxId();
		for (MailSession candidate : this.activeSessions.values()) {
			if (candidate == session) { continue; }
			if (candidate.getUserLcFqn().equals(mailboxName)) { continue; }
			for (int i = 0; i < candidate.getMailCount(); i++) {
				MailboxEntry me = candidate.getMailEntry(i);
				if (me.isMail(mailId)) {
					me.resetInboxEntry();
					continue;
				}
			}
		}
		
		// delete the file and possibly the contentFile
		List<FileEntry> contentFiles = this.mailsVolume.findFiles(
				this.mailfilesFolder.getFileID(),
				f -> mailId.equalsIgnoreCase(f.getName()),
				1, // maxCount,
				1, // maxDepth,
				this.serviceNameFqn);
		if (contentFiles.size() != 1) {
			return new ServiceErrorRecord(ServiceProblem.serviceUnavailable); // there is nothing appropriate...
		}
		FileEntry contentFile = contentFiles.get(0);
		
		// get the meta-data for the mail content file
		UninterpretedAttribute postedMetaDataAttr = contentFile.getUninterpretedAttribute(atPostedMetaData);
		MailMetaData postedMetaData = MetaData.from(postedMetaDataAttr, MailMetaData::make);
		
		// check if the posted mail has also to be deleted
		int refCount = postedMetaData.mailboxReferences.get() - 1;
		boolean deletePostedMail = (refCount < 1);
		
		// drop resp. modify the file(s)
		try (Session fSession = this.mailsVolume.startModificationSession()) {
			if (deletePostedMail) {
				fSession.deleteFile(contentFile, this.serviceNameFqn);
			} else {
				postedMetaData.mailboxReferences.set(refCount);
				postedMetaData.to(postedMetaDataAttr);
				fSession.updateFileAttributes(contentFile, Arrays.asList( fe -> {} ), this.serviceNameFqn);
			}
			fSession.deleteFile(mailFile, this.serviceNameFqn);
		} catch (IllegalStateException e) {
			Log.C.printf("MS", "dropMail() -> rejecting with ServiceError[serviceUnavailable], cause: %s\n", e.getMessage());
			return new ServiceErrorRecord(ServiceProblem.serviceUnavailable);
		} catch (InterruptedException e) {
			Log.C.printf("MS", "dropMail() -> rejecting with ServiceError[serviceUnavailable], cause: %s\n", e.getMessage());
			return new ServiceErrorRecord(ServiceProblem.serviceUnavailable);
		} catch (Exception e) {
			Log.C.printf("MS", "dropMail() -> rejecting with ServiceError[serviceUnavailable], cause: %s\n", e.getMessage());
			return new ServiceErrorRecord(ServiceProblem.serviceUnavailable);
		}
		
		return null; // successful
	}
	
	/**
	 * Create the list entry of a mailbox entry for the mailbox enumeration.
	 * 
	 * @param session the mail session for the mailbox
	 * @param mailboxIndex the 1-based index of the mailbox entry to process
	 * @param doTransportEnvelope include the (posted) transport envelope in the list entry?
	 * @param doInbasketEnvelope include the inbasket-status envelope in the list entry?
	 * @param reqAttributes the mail attributes to be included in the list entry as requested
	 * 		by the client
	 * @return the list entry generated for the mailbox entry
	 */
	public ListElementRecord produceListEntry(
			MailSession session,
			int mailboxIndex,
			boolean doTransportEnvelope,
			boolean doInbasketEnvelope,
			List<Long> reqAttributes) {
		// get the mail entry
		if (mailboxIndex < 1 || mailboxIndex > session.getMailCount()) {
			return null;
		}
		MailboxEntry mail = session.getMailEntry(mailboxIndex - 1);
		
		// create and initialize the result
		ListElementRecord entry = ListElementRecord.make();
		entry.message.set(mailboxIndex);
		
		// get the transport envelope from the mail reference file if requested
		if (doTransportEnvelope) {
			try {
				ValueContentSink envMaker = new ValueContentSink(entry.transportEnvelope);
				mail.transferPostboxEnvelope(envMaker);
			} catch (IOException e) {
				// ignored for now...
			}
		}
		
		// build the inbasket envelope if requested
		if (doInbasketEnvelope) {
			entry.inbasketEnvelope.add(Attribute.make().set(
				MailingCommon.atIbMessageStatus,
				MailingCommon.mkMessageStatus,
				a-> a.set(mail.messageStatus())
				));
		}
		
		// read the mail attributes from the (serialized file) header of the mail content
		MailHeaderData mailHeader = MailHeaderData.make();
		try {
			mail.transferContent( src -> {
				iWireStream wire = new WireBaseStream() {
					protected int getByte() throws EndOfMessageException {
						try {
							return src.read();
						} catch (IOException e) {
							throw new EndOfMessageException();
						}
					}
					protected void putByte(int b) throws NoMoreWriteSpaceException {
						throw new NoMoreWriteSpaceException();
					}
					@Override
					public void writeEOM() throws NoMoreWriteSpaceException {
						throw new NoMoreWriteSpaceException();
					}
					@Override
					public void flush() throws NoMoreWriteSpaceException {
						throw new NoMoreWriteSpaceException();
					}
					@Override
					public void beginStreamType(byte datastreamType) throws NoMoreWriteSpaceException {
						throw new NoMoreWriteSpaceException();
					}
					@Override
					public boolean isAtEnd() {
						return false;
					}
					@Override
					public byte getStreamType() {
						return 0;
					}	
				};
				try {
					mailHeader.deserialize(wire);
				} catch (EndOfMessageException e) { }
			});
		} catch (IOException e) {
			// ignored for now...
		}
		
		// lookup the attributes requested and add them to the result in the requested order
		System.out.printf("  mail metadata from serialized content:\n");
		Map<Long,Attribute> attrs = new HashMap<>();
		for (int i = 0; i < mailHeader.metadata.size(); i++) {
			Attribute a = mailHeader.metadata.get(i);
			attrs.put(a.type.get(), a);
			System.out.printf("      type: %4d : [%d]{", a.type.get(), a.value.size());
			for (int idx = 0; idx < a.value.size(); idx++) { System.out.printf(" %04X", a.value.get(idx).get()); }
			System.out.printf(" }\n");
		}
		System.out.printf("  ----------------------------\n");
		for (long reqType : reqAttributes) {
			Attribute a = attrs.get(reqType);
			if (a != null) {
				entry.mailAttributes.add(a);
			} else {
				// the requested attribute was not found: add empty attribute, which is possibly the wrong approach...
				System.out.printf("    ... missing requested attribute %d\n", reqType);
				Attribute tmp = Attribute.make();
				tmp.type.set(reqType);
				entry.mailAttributes.add(tmp);
			}
		}
		
		// done
		return entry;
	}
	
	// get the mail file name from a {mail,mailbox} files
	private static String getMailFilename(FileEntry fe) {
		String[] fnParts = fe.getName().split(":");
		return fnParts[0];
	}
	
	/**
	 * Allocate a new mail-id for this mail-service by updating
	 * the file attribute on the mailfiles folder, where the
	 * last mail-id is persisted.
	 * 
	 * @param session the Filing(!) session for saving the newly allocated id
	 * @return the 5 words holding the new file-id
	 */
	private int[] allocateMailId(Session session) {
		// get the last mail-id
		UninterpretedAttribute attr = this.mailfilesFolder.getUninterpretedAttribute(atLastMailId);
		if (attr == null) {
			throw new IllegalStateException("unable to allocate new mail-id: (null last-mail-id attribute)");
		}
		int[] mailId = new int[] {
			attr.get(0) & 0xFFFF,
			attr.get(1) & 0xFFFF,
			attr.get(2) & 0xFFFF,
			attr.get(3) & 0xFFFF,
			attr.get(4) & 0xFFFF
		};
		
		// increment the running counter
		long newId = ((mailId[3] << 16) | mailId[4]) + 1;
		mailId[3] = (int)((newId >> 16) & 0xFFFFL);
		mailId[4] = (int)(newId & 0xFFFFL);
		
		// update the mailfiles folder
		try {
			session.updateFileAttributes(
				this.mailfilesFolder,
				Arrays.asList( fe -> {
					attr.clear()
						.add(mailId[0])
						.add(mailId[1])
						.add(mailId[2])
						.add(mailId[3])
						.add(mailId[4]);
				}),
				this.serviceNameFqn);
		} catch (Exception e) {
			throw new IllegalStateException("unable to allocate new mail-id: " + e.getMessage());
		}
		
		// done
		return mailId;
	}

	/**
	 * @return the network id served by this mail service
	 */
	public long getNetworkId() { return networkId; }

	/**
	 * @return the processor-id of the machine running this mail service
	 */
	public long getMachineId() { return machineId; }
	
	/**
	 * @return the network adrdress of the mail service
	 */
	public NetworkAddress getServiceAddress() { return this.serviceAddress; }

	/**
	 * @return the full qualified name of this mail service
	 */
	public String getServiceNameFqn() { return serviceNameFqn; }

	/**
	 *@return the name of this service
	 */
	public Name getServiceName() { return serviceName; }

	/**
	 * @return the clearinghouse database used by this mail services
	 */
	public ChsDatabase getChsDatabase() { return chsDatabase; }

	/**
	 * Check the credentials for validity to this mail service and return
	 * the user name from the credentials, raising an {@code AuthenticationError}
	 * if the credentials are invalid.
	 * 
	 * @param forRecipient recipient of the credentials
	 * @param credentials the credentials to check
	 * @param verifier the accompanying encoded verifier
	 * @param decodedConversationKey the extracted conversion key from the credentials
	 * @param decodedVerifier the decoded verifier
	 * @return the user name
	 */
	public ThreePartName checkCredentials(
				Name forRecipient,
				long forMachineId,
				Credentials credentials,
				Verifier verifier,
				int[] decodedConversationKey,
				StrongVerifier decodedVerifier) {
		ThreePartName username = null;
		try {
			if (credentials.type.get() == CredentialsType.simple) {
				if (credentials.value.size() == 0) {
					// anonymous access resp. secondary credentials currently not supported
					new AuthenticationErrorRecord(AuthenticationProblem.other).raise();
				}
				username = AuthChsCommon.simpleCheckPasswordForSimpleCredentials(
									this.chsDatabase,
									credentials,
									verifier);
			} else {
				username = AuthChsCommon.checkStrongCredentials(
									this.chsDatabase,
									credentials,
									verifier,
									forRecipient,
									forMachineId,
									decodedConversationKey,
									decodedVerifier);
			}
		} catch (IllegalArgumentException iac) {
			AuthenticationErrorRecord err = new AuthenticationErrorRecord(AuthenticationProblem.badNamelnldentity);
			Log.C.printf("MS", "checkCredentials() IllegalArgumentException (name not existing) -> rejecting with AuthenticationError[badNamelnldentity]\n");
			err.raise();
		} catch (EndOfMessageException e) {
			AuthenticationErrorRecord err = new AuthenticationErrorRecord(AuthenticationProblem.badPwdlnldentity);
			Log.C.printf("MS", "checkCredentials() EndOfMessageException when deserializing credsObject -> rejecting with AuthenticationError[badPwdlnldentity]\n");
			err.raise();
		} catch (Exception e) {
			AuthenticationErrorRecord err = new AuthenticationErrorRecord(AuthenticationProblem.other);
			Log.C.printf("MS", "checkCredentials() Exception when checking credentials -> rejecting with AuthenticationError[other]: %s\n", e.getMessage());
			err.raise();
		}
		if (username == null) {
			AuthenticationErrorRecord err = new AuthenticationErrorRecord(AuthenticationProblem.badNamelnldentity);
			Log.C.printf("MS", "checkCredentials() -> rejecting with AuthenticationError[badNamelnldentity]\n");
			err.raise();
		}
		return username;
	}
	
	/*
	 * utilities
	 */

	/**
	 * Helper class for (de-)serializing a Courier type from/to a Filing uninterpreted attribute.
	 */
	private static abstract class MetaData extends RECORD {
		
		public static <T extends iWireData> T from(UninterpretedAttribute attr, iWireDynamic<T> maker) {
			T result = maker.make();
			if (attr == null) {
			}
			int[] pos = { 0 };
			try {
				result.deserialize(new WireSeqOfUnspecifiedReader(attr.size(), () -> attr.get(pos[0]++)));
				return result;
			} catch (EndOfMessageException e) {
				return null;
			}
		}
		
		public void to(UninterpretedAttribute attr) {
			WireWriter sink = new WireWriter();
			try {
				this.serialize(sink);
			} catch (NoMoreWriteSpaceException e) {
				return; // should never happen
			}
			int[] words = sink.getWords();
			attr.clear();
			for (int word : words) {
				attr.add(word);
			}
		}
		
	}

	/**
	 * Courier type for the metadata stored as uninterpreted attribute in a mail file.
	 * Besides the mail data provided at mail creation time (sender, recipients, content-type),
	 * this is the number of (still) existing references from/by mailboxes to this mail.
	 * <p>
	 * The mail metadata is stored as attribute {@code atPostedMetaData} (90100L)
	 * </p>
	 */
	private static class MailMetaData extends MetaData {
		public final ThreePartName sender = mkRECORD(ThreePartName::make);
		public final NameList recipients = mkMember(NameList::make);
		public final LONG_CARDINAL contentsType = mkLONG_CARDINAL();
		public final CARDINAL mailboxReferences = mkCARDINAL();
		
		public MailMetaData() { }
		
		public static MailMetaData make() { return new MailMetaData(); }
	}
	
	/**
	 * Helper class for writing a Courier type instance as content into a Filing file,
	 * the source object is given at contruction and the serialized representation
	 * as byte stream can be read by a Filing file system through the {@code iContentSource}
	 * interface.
	 */
	private static class ValueContentSource implements iContentSource {
		
		private final byte[] bytes;
		
		private int currPos = 0;
		
		public ValueContentSource(iWireData value) {
			WireWriter sink = new WireWriter();
			try {
				value.serialize(sink);
			} catch (NoMoreWriteSpaceException e) {
				// ignored, as should never happen
			}
			this.bytes = sink.getBytes();
		}

		@Override
		public int read(byte[] buffer) {
			if (this.currPos >= this.bytes.length) { return 0; }
			
			int count = 0;
			for (int i = 0; i < buffer.length; i++) {
				buffer[i] = this.bytes[this.currPos++];
				count++;
				if (this.currPos >= this.bytes.length) { return count; }
			}
			return count;
		}
		
		public ValueContentSource reset() {
			this.currPos = 0;
			return this;
		}
		
	}
	
	/**
	 * Helper class for reading from a Filing file (through the interface
	 * {@code iContentSink} and deserializing the byte stream into an
	 * existing Courier type instance.
	 */
	private static class ValueContentSink implements iContentSink {
		
		private final iWireData target;
		
		private final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		public ValueContentSink(iWireData target) {
			this.target = target;
		}

		@Override
		public int write(byte[] buffer, int count) {
			if (buffer == null) {
				byte[] data = this.bos.toByteArray();
				WirePacketReader wire = new WirePacketReader(data);
				try {
					this.target.deserialize(wire);
				} catch (EndOfMessageException e) {
					// ignored!!
				}
				return 0;
			}
			this.bos.write(buffer, 0, count);
			return count;
		}
		
	}
	
}
