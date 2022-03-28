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

import java.util.ArrayList;
import java.util.List;

import dev.hawala.xns.level3.courier.ARRAY;
import dev.hawala.xns.level3.courier.CARDINAL;
import dev.hawala.xns.level3.courier.CrProgram;
import dev.hawala.xns.level3.courier.LONG_CARDINAL;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.SEQUENCE;
import dev.hawala.xns.level3.courier.STRING;
import dev.hawala.xns.level3.courier.UNSPECIFIED;
import dev.hawala.xns.level3.courier.WireSeqOfUnspecifiedReader;
import dev.hawala.xns.level3.courier.iWireData;
import dev.hawala.xns.level3.courier.iWireStream;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;
import dev.hawala.xns.level4.common.AuthChsCommon.Credentials;
import dev.hawala.xns.level4.common.AuthChsCommon.Name;
import dev.hawala.xns.level4.common.AuthChsCommon.NetworkAddressList;
import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;
import dev.hawala.xns.level4.common.AuthChsCommon.Verifier;
import dev.hawala.xns.level4.common.BulkData1;
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
	 * local logging
	 */
	private static final boolean logDeserialzing = false;
	
	private static final String blanks = "                                                                 ";
	
	private static void logf(String pattern, Object... args) {
		if (!logDeserialzing) { return; }
		System.out.printf(pattern, args);
	}
	
	private static void logCourierData(iWireData data, String name) {
		if (!logDeserialzing) { return; }
		StringBuilder sb = new StringBuilder();
		data.append(sb, "  ", name);
		logf("%s\n", sb.toString());
	}
	
	
	/*
	 * procedures
	 */
	
	/*
	 * serverPoll
	 *  = procedure 0
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
	public static final int mostWilling=1;
	public static final int leastWilling=10;
	
	public final PROC<RECORD,ServerPollResults> ServerPoll = mkPROC(
						"ServerPoll",
						0,
						RECORD::empty,
						ServerPollResults::make
						// no parameters means also: the invoker cannot provide wrong data
						);
	
	/*
	 * postBegin
	 *  = procedure 1
	 */
	
	public static class PostRecipient extends RECORD {
		public final CARDINAL unknown0 = mkCARDINAL();
		public final ThreePartName name = mkRECORD(ThreePartName::make);
		public final CARDINAL unknown1 = mkCARDINAL();
		public final CARDINAL unknown2 = mkCARDINAL();
		
		private PostRecipient() { }
		public static PostRecipient make() { return new PostRecipient(); } 
	}
	
	public static class PostOptions extends ARRAY<UNSPECIFIED> {
		public PostOptions(int len)  { super(len, UNSPECIFIED::make); }
	}
	
	public static class PostBeginParams extends RECORD {
		public final SEQUENCE<PostRecipient> recipients = mkSEQUENCE(PostRecipient::make);
		private PostOptions options = null;
		private Credentials credentials = null;
		private Verifier verifier = null;
		
		public PostOptions getOptions() {return options; }
		public Credentials getCredentials() { return credentials; }
		public Verifier getVerifier() { return verifier; }

		// manually deserialize the params structure, as there is an variable length
		// part in the middle of the structure: this was solved by scanning backwards
		// from the end to locate the beginning of the recognized end parts (credentials,
		// verifier) of the structure.
		@Override
		public void deserialize(iWireStream ws) throws EndOfMessageException {
			logf("~~ PostBeginParams .. begin deserialize\n");
			
			// the simple part...
			this.recipients.deserialize(ws);
			
			logf("~~ PostBeginParams .. .. loaded %d recipients\n", this.recipients.size());
			logCourierData(this.recipients, "recipients");
			
			
			// the more complicated part...
			logf("~~ PostBeginParams .. .. loading variable content part\n");
			List<Integer> cardinals = new ArrayList<>();
			try {
				while(!ws.checkIfAtEnd()) {
					cardinals.add(ws.readI16() & 0xFFFF);
				}
			} catch (EndOfMessageException eom) {
				// this is the end of the post data
			}
			int len = cardinals.size();
			
			// ** begin dump the unknown area

			logf("~~ PostBeginParams .. .. loaded %d words\n", len);
			logf("~~ PostBeginParams .. .. content of the unknown area:");
			
			int byteLength = len * 2;
			int w = 0;
			int b = 0;
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < byteLength; i++) {
				if ((i % 2) == 0) {
					w = cardinals.get(i / 2);
					b = (w >> 8) & 0xFF;
				} else {
					b = w & 0xFF;
				}
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
			logf("%s%s\n", filler, sb.toString());
			
			// ** end dump the unknown area
			
			// the last 5 words should be the 'verifier' word range
			if (len < 5) {
				logf("~~ PostBeginParams .. ERROR: Postparams too short\n");
				throw new IllegalStateException("MailTransport5.Postparams too short");
			}
			int verifierLength = cardinals.get(len - 5);
			if (verifierLength != 4) {
				logf("~~ PostBeginParams .. ERROR: verifier is not 4 words long\n");
				throw new IllegalStateException("MailTransport5.Postparams.verifier is not 4 words long");
			}
			
			// locate the beginning of the credentials
			logf("~~ PostBeginParams .. .. locating credentials\n");
			boolean found = false;
			int optionsLength = 10; // 17 seems to be the minimal length of the version 5 post-options block
			int credsDataLength = len - optionsLength - 5 - 2; // 5 = verifier-sequence, 2 = credsType & seqLength
			while (!found && credsDataLength > 2) { // 2 = creds.type + creds.value.length
				int credsType = cardinals.get(optionsLength);
				int credsValueLength = cardinals.get(optionsLength + 1);
				if (credsType == 1 && credsValueLength == credsDataLength) { // assuming strong credentials always!
					found = true;
				} else {
					optionsLength++;
					credsDataLength--;
				}
			}
			if (!found) {
				logf("~~ PostBeginParams .. ERROR: credentials was not located successfully\n");
				throw new IllegalStateException("MailTransport5.Postparams.credentials was not located successfully");
			}
			logf("~~ PostBeginParams .. .. credentials starts at offset %d in unknown area\n", optionsLength);
			
			// create and deserialize the remaining fields from the buffered words
			logf("~~ PostBeginParams .. .. allocating parts of the unknwon area\n");
			this.options = new PostOptions(optionsLength);
			this.credentials = Credentials.make();
			this.verifier = Verifier.make();
			logf("~~ PostBeginParams .. .. alocating sub-stream\n");
			iWireStream stream = new WireSeqOfUnspecifiedReader(cardinals);
			logf("~~ PostBeginParams .. .. deserializing options\n");
			this.options.deserialize(stream);
			logf("~~ PostBeginParams .. .. deserializing credentials\n");
			this.credentials.deserialize(stream);
			logf("~~ PostBeginParams .. .. deserializing verifier\n");
			this.verifier.deserialize(stream);

			logf("~~ PostBeginParams .. done deserializing\n");
		}
		
		@Override
		public void serialize(iWireStream ws) throws NoMoreWriteSpaceException {
			throw new IllegalStateException("serializing not supported for PostParams");
		}
		
		private PostBeginParams() {}
		public static PostBeginParams make() { return new PostBeginParams(); }
	}
	
	public static class PostBeginResults extends RECORD {
		public final LONG_CARDINAL mailTransaction = mkLONG_CARDINAL();
		public final Verifier verifier = mkMember(Verifier::make);
		public final CARDINAL unknown0 = mkCARDINAL(); // this may be the count of a SEQUENCE<whatever>
		
		private PostBeginResults() {}
		public static PostBeginResults make() { return new PostBeginResults(); }
	}
	
	public final PROC<PostBeginParams,PostBeginResults> PostBegin = mkPROC(
						"PostBegin",
						1,
						PostBeginParams::make,
						PostBeginResults::make
						// there are probably some ERRORs (invalid names, ...), but the definitions are not known...
						);
	
	
	/*
	 * postMailPart
	 *  = procedure 8
	 */
	
	public static class PostMailPartParams extends RECORD {
		public final LONG_CARDINAL mailTransaction = mkLONG_CARDINAL();
		public final Verifier verifier = mkMember(Verifier::make);
		public final LONG_CARDINAL mailPartType = mkLONG_CARDINAL();
		public final BulkData1.Source content = mkRECORD(BulkData1.Source::make);
		
		private PostMailPartParams() {}
		public static PostMailPartParams make() { return new PostMailPartParams(); }
	}
	
	public final PROC<PostMailPartParams,RECORD> PostMailPart = mkPROC(
						"PostTransfer",
						8,
						PostMailPartParams::make,
						RECORD::empty
						);
	
	// mail part types => bulk-data content
	
	public static final int mptEnvelope         = 0x0000; // mail-envelope => SEQUENCE<Attribute>
	public static final int mptAttachmentFolder = 0x0001; // folder 
	public static final int mptAttachmentDoc    = 0x0003; // document (Star/VP/GV)
	public static final int mptNoteGV           = 0x0005; // "GlobalView" mail text, bulk-data => raw text (!! may have odd length !!)
	public static final int mptNoteIA5          = 0x0006; // "IA5" mail text, bulk-data => raw text (!! may have odd length !!)
	public static final int mptNoteISO6937      = 0x000B; // "ISO6937" mail text, bulk-data => raw text (!! may have odd length !!)
	
	// bulk-data:
	// elements for mailPartType 0 == SEQUENCE<Attribute>
	// => attribute type IDs...
	
	public static final int atSenderAndDate     = 0x0001; //  1 = sending user and send(?) datetime
                                                          // => ThreePartNameWithTagAndDateString

	public static final int atSenderA           = 0x0002; //  2 = 1st reference of the sending user name
                                                          // => ThreePartNameWithTag

	public static final int atSenderB           = 0x0003; //  3 = 2nd reference of the sending user name
                                                          // => ListOfThreePartNameWithTag

	public static final int atTo                = 0x0004; //  4 = primary recipients
                                                          // => ListOfThreePartNameWithTag

	public static final int atCopiesTo          = 0x0005; //  5 = copy recipients
                                                          // => ListOfThreePartNameWithTag

	public static final int atSubject           = 0x000A; // 10 = subject
                                                          // => STRING
	
	public static final int atReplyTo           = 0x000D; // 13 = reply to
                                                          // => ListOfThreePartNameWithTag
	
	// bulk-data:
	// elements for mailPartType 0 == SEQUENCE<Attribute>
	// => attribute contents...
	
	public static class ThreePartNameWithTag extends RECORD {
		public final CARDINAL tag = mkCARDINAL();
		public ThreePartName name = mkMember(ThreePartName::make);
		
		private ThreePartNameWithTag() {}
		public static ThreePartNameWithTag make() { return new ThreePartNameWithTag(); }
	}
	
	public static class ListOfThreePartNameWithTag extends SEQUENCE<ThreePartNameWithTag> {
		private ListOfThreePartNameWithTag() { super(ThreePartNameWithTag::make); }
		public static ListOfThreePartNameWithTag make() { return new ListOfThreePartNameWithTag(); }
	}
	
	public static class ThreePartNameWithTagAndDateString extends RECORD {
		public final ThreePartNameWithTag nameWithTag = mkMember(ThreePartNameWithTag::make);
		public final STRING date = mkSTRING();
		
		private ThreePartNameWithTagAndDateString() {}
		public static ThreePartNameWithTagAndDateString make() { return new ThreePartNameWithTagAndDateString(); }
	}
	
	
	/*
	 * postEnd
	 *  = procedure 9
	 */
	
	public static class PostEndParams extends RECORD {
		public final LONG_CARDINAL mailTransaction = mkLONG_CARDINAL();
		public final Verifier verifier = mkMember(Verifier::make);
		public final CARDINAL unknown0 = mkCARDINAL(); // boolean ~ test send ???
		
		private PostEndParams() { }
		public static PostEndParams make() { return new PostEndParams(); }
	}
	
	public static class PostEndResults extends RECORD {
		public final MessageID messageId = mkMember(MessageID::make);
		
		private PostEndResults() {}
		public static PostEndResults make() { return new PostEndResults(); }
	}
	
	public final PROC<PostEndParams,PostEndResults> PostEnd = mkPROC(
						"PostEnd",
						9,
						PostEndParams::make,
						PostEndResults::make
						// there are probably some ERRORs (failed, ...), but the definitions are not known...
						);

}
