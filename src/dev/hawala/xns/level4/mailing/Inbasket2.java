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
	
	/*
	 * procedure name/function guesses based on order of invocation and recognizable params/results:
	 *  -> procedure 7 : inbasketPoll (expedited courier)
	 *  -> procedure 5 : logon
	 *  -> procedure 2 :   getNextMail
	 *  -> procedure 8 :     getMailPart
	 *  -> procedure 3 :   handleMailParts
	 *  -> procedure 4 : logout
	 */
	
	/*
	 * getNextMail
	 *   = procedure 2
	 */
	public static class GetNextMailParams extends RECORD {
		public final LONG_CARDINAL prevUniqueMailNo = mkLONG_CARDINAL();
		public final CARDINAL unknown2 = mkCARDINAL();
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
		//public final BigEndianLong mailTs = mkRECORD(BigEndianLong::make);
		public final CARDINAL unknown1 = mkCARDINAL();
		public final CARDINAL unknown2 = mkCARDINAL();
		
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
	 * handleMailParts (?)
	 *   = procedure 3
	 */
	public static class MailPartHandling extends RECORD {
		public final CARDINAL mailPartIndex = mkCARDINAL();
		public final BOOLEAN keepMailPart = mkBOOLEAN();
		
		private MailPartHandling() {}
		public static MailPartHandling make() { return new MailPartHandling(); }
	}
	public static class HandleMailPartsParams extends RECORD {
		public final LONG_CARDINAL uniqueMailNo = mkLONG_CARDINAL();
		public final SEQUENCE<MailPartHandling> mailPartHandling = mkSEQUENCE(MailPartHandling::make);
		public final UNSPECIFIED2 sessionId = mkUNSPECIFIED2();
		public final Verifier verifier = mkMember(Verifier::make);
		
		private HandleMailPartsParams() {}
		public static HandleMailPartsParams make() { return new HandleMailPartsParams(); }
	}
	public static class HandleMailPartsResults extends RECORD {
		public final BOOLEAN deleted  = mkBOOLEAN();
		
		private HandleMailPartsResults() {}
		public static HandleMailPartsResults make() { return new HandleMailPartsResults(); }
	}
	public final PROC<HandleMailPartsParams,HandleMailPartsResults> HandleMailParts = mkPROC(
			"HandleMailParts",
			3,
			HandleMailPartsParams::make,
			HandleMailPartsResults::make
			// there are probably some ERRORs (invalid session, ...), but the definitions are not known...
			);
	
	/*
	 * logoff
	 *   = procedure 4
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
			RECORD::empty
			// there are probably some ERRORs (invalid session, ...), but the definitions are not known...
			);
	

	/*
	 * logon
	 *  = procedure 5
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
		public final CARDINAL lastIndex = mkCARDINAL();
		public final CARDINAL newCount = mkCARDINAL();
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
			LogonResults::make
			// there are probably some ERRORs (invalid credentials, ...), but the definitions are not known...
			);

	
	/*
	 * inbasketPoll
	 *  = procedure 7 (called through a single PEX packet == expedited courier)
	 */
	public static class InbasketPollParams extends RECORD {
		public final ThreePartName mailboxName = mkRECORD(ThreePartName::make);
		public final Credentials credentials = mkRECORD(Credentials::make);
		public final Verifier verifier = mkMember(Verifier::make);
		
		private InbasketPollParams() {}
		public static InbasketPollParams make() { return new InbasketPollParams(); }
	}
	public static class InbasketPollResults extends RECORD {
		public final CARDINAL lastIndex = mkCARDINAL();
		public final CARDINAL newCount = mkCARDINAL();
		
		private InbasketPollResults() {}
		public static InbasketPollResults make() { return new InbasketPollResults(); }
	}
	public final PROC<InbasketPollParams,InbasketPollResults> InbasketPoll = mkPROC(
			"InbasketPoll",
			7,
			InbasketPollParams::make,
			InbasketPollResults::make
			// there are probably some ERRORs (invalid credentials, ...), but the definitions are not known...
			);	


	/*
	 * getMailPart
	 *   = procedure 8
	 * (the requested 1..n mail part content(s) are sent via bulk-data transfer to the invoker)
	 */
	public static class GetMailPartParams extends RECORD {
		public final LONG_CARDINAL uniqueMailNo = mkLONG_CARDINAL();
		public final SEQUENCE<CARDINAL> mailPartIndices = mkSEQUENCE(CARDINAL::make);
		public final BulkData1.Sink content = mkRECORD(BulkData1.Sink::make);
		public final UNSPECIFIED2 sessionId = mkUNSPECIFIED2();
		public final Verifier verifier = mkMember(Verifier::make);
		
		private GetMailPartParams() {}
		public static GetMailPartParams make() { return new GetMailPartParams(); }
	}
	public final PROC<GetMailPartParams,RECORD> GetMailPart = mkPROC(
			"GetMailPart",
			8,
			GetMailPartParams::make,
			RECORD::empty
			// there are probably some ERRORs (invalid session, ...), but the definitions are not known...
			);
}
