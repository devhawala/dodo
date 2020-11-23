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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import dev.hawala.xns.level4.filing.fs.FileEntry;
import dev.hawala.xns.level4.filing.fs.iContentSink;
import dev.hawala.xns.level4.mailing.MailingCommon.MessageStatus;

/**
 * Runtime representation of an entry in a user mailbox, holding
 * all data that a mail agent could request subsequently.
 * <p>
 * The larger data parts are shared as far as possible between
 * instances representing the same mail, allowing to have more
 * than one session for the same mailbox (as XDE requires it)
 * without wasting too much heap space. Caching the mail data
 * in memory is necessary, as the inbasket (see the services programmers
 * guide) requires that each concurrent session on the same mailbox
 * allows access to mails even if a mail was deleted in an other session
 * (the true mail files may be deleted, but a session still has the
 * data, which will be garbage-collected when the last session is closed).
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2020)
 */
public class MailboxEntry {
	
	/**
	 * Replacement for Consumer<iContentSink> allowing to throw an IOException
	 */
	@FunctionalInterface
	public interface ContentSinkConsumer {
		void accept(iContentSink sink) throws IOException;
	}
	
	/**
	 * Cache for a mail file content.
	 */
	public static class DataSource {
		
		private final byte[] data;
		
		/**
		 * Constructor for caching a Filing file content.
		 * @param filler provider for the Filing content source
		 * @throws IOException
		 */
		public DataSource(ContentSinkConsumer filler) throws IOException {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (
				DeflaterOutputStream dos = new DeflaterOutputStream(baos)
				) {
				filler.accept( (buffer, count) -> {
					if (buffer == null || count == 0) {
						return 0;
					}
					try {
						dos.write(buffer, 0, count);
					} catch (IOException e) {
						return 0;
					}
					return count;
				});
			}
			this.data = baos.toByteArray();
		}
		
		/**
		 * Provide the cached content to the given content user.
		 * 
		 * @param contentSink data transfer target where to put the content
		 * @throws IOException
		 */
		public void retrieveContent(iContentSink contentSink) throws IOException {
			try (
				ByteArrayInputStream bais = new ByteArrayInputStream(this.data);
				InflaterInputStream iis = new InflaterInputStream(bais)
				) {
				byte[] buffer = new byte[512];
				int bytesTransferred;
				while((bytesTransferred = iis.read(buffer)) > 0) {
					if (contentSink.write(buffer, bytesTransferred) < bytesTransferred) {
						throw new IOException("BulkTransfer aborted by other end (NoMoreWriteSpaceException)");
					}
				}
			} finally {
				contentSink.write(null,  0); // signal EOF and cleanup
			}
		}
		
		/**
		 * Provide an {@code InputStream} to the cached content to the given callback.
		 * @param sink callback to invoke for providing the stream
		 * @throws IOException
		 */
		public void retrieveContent(Consumer<InputStream> sink) throws IOException {
			try (
				ByteArrayInputStream bais = new ByteArrayInputStream(this.data);
				InflaterInputStream iis = new InflaterInputStream(bais)
				) {
					sink.accept(iis);	
				}
			}
		
	}

	private FileEntry inboxEntry;
	private MessageStatus messageStatus;
	private final String postboxId;
	private final DataSource content;
	private final DataSource postboxEnvelope;
	
	/**
	 * Constructor for the mailbox entry.
	 * 
	 * @param inboxFile handle to the mailbox file for the mail
	 * @param postboxFilename filename of the mail content file (located in the 'Mailfiles' folder)
	 * @param contentSource data provider to the 'postboxFilename' file for the mail content 
	 * @param envSource data provider to the 'inboxFile' for the postbox envelope 
	 * @throws IOException
	 */
	public MailboxEntry(
			FileEntry inboxFile,
			String postboxFilename,
			ContentSinkConsumer contentSource,
			ContentSinkConsumer envSource
			) throws IOException {
		this.inboxEntry = inboxFile;
		this.messageStatus = getMailStatus(inboxFile);
		this.postboxId = postboxFilename;
		this.content = new DataSource(contentSource);
		this.postboxEnvelope = new DataSource(envSource);
	}
	
	/**
	 * Copy constructor for creating a mail entry in a new session for
	 * the same mail in the same or a different mailbox.
	 * 
	 * @param oldEntry mail entry of an existing session to duplicate
	 * @param inboxFile the mailbox file to this entry (mailbox for the new session)
	 */
	public MailboxEntry(MailboxEntry oldEntry, FileEntry inboxFile) {
		this.inboxEntry = inboxFile;
		this.messageStatus = getMailStatus(inboxFile);
		this.postboxId = oldEntry.postboxId;
		this.content = oldEntry.content;
		this.postboxEnvelope = oldEntry.postboxEnvelope;
	}
	
	/**
	 * @return the mailbox file for the mail
	 */
	public FileEntry inboxEntry() {
		return this.inboxEntry;
	}
	
	/**
	 * Clear the mailbox file in this entry to record that the mail
	 * was deleted from this mailbox.
	 */
	public void resetInboxEntry() {
		this.inboxEntry = null;
	}
	
	/**
	 * @return the cached status of the mail in thismailbox.
	 */
	public MessageStatus messageStatus() {
		return this.messageStatus;
	}
	
	/**
	 * Update the cached status of the mail in this mailbox.
	 * @param status new status value
	 * @return this instance for method chaining (fluent-API)
	 */
	public MailboxEntry messageStatus(MessageStatus status) {
		this.messageStatus = status;
		return this;
	}
	
	/**
	 * Check if the entry has the given mail identification (mail content filename in
	 * the 'Mailfiles' folder).
	 * 
	 * @param mailId the mail-id to check.
	 * @return {@code true} if the mail cached here has the given id
	 */
	public boolean isMail(String mailId) {
		return this.postboxId.equalsIgnoreCase(mailId);
	}
	
	/**
	 * @return the postbox-id of the mail (the filename of the content file)
	 */
	public String postboxId() {
		return this.postboxId;
	}
	
	/**
	 * Transfer the mail content to the given sink.
	 * 
	 * @param to the sink to transfer the content to.
	 * @return this instance for method chaining (fluent-API)
	 * @throws IOException
	 */
	public MailboxEntry transferContent(iContentSink to) throws IOException {
		this.content.retrieveContent(to);
		return this;
	}
	
	/**
	 * Pass an {@code InputStream} on the mail content to the given consumer.
	 * 
	 * @param to the consumer to pass the stream to
	 * @return this instance for method chaining (fluent-API)
	 * @throws IOException
	 */
	public MailboxEntry transferContent(Consumer<InputStream> to) throws IOException {
		this.content.retrieveContent(to);
		return this;
	}
	
	/**
	 * Transfer the mail envelope to the given sink.
	 * 
	 * @param to the sink to transfer the envelope to.
	 * @return this instance for method chaining (fluent-API)
	 * @throws IOException
	 */
	public MailboxEntry transferPostboxEnvelope(iContentSink to) throws IOException {
		this.postboxEnvelope.retrieveContent(to);
		return this;
	}
	
	/**
	 * Extract the status from the inbox mail file.
	 * 
	 * @param inboxFe the inbox mail file to check
	 * @return the mail status taken from the file name
	 */
	private static MessageStatus getMailStatus(FileEntry inboxFe) {
		String lcFn = inboxFe.getLcName();
		if (lcFn.endsWith(MailService.INBOX_SUFFIX_NEW)) {
			return MessageStatus.newMail;
		}
		if (lcFn.endsWith(MailService.INBOX_SUFFIX_RECEIVED)) {
			return MessageStatus.received;
		}
		return MessageStatus.known;
	}
	
}
