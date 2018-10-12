/*
Copyright (c) 2018, Dr. Hans-Walter Latz
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

package dev.hawala.xns.level4.common;

import dev.hawala.xns.Log;
import dev.hawala.xns.level3.courier.CHOICE;
import dev.hawala.xns.level3.courier.Constants;
import dev.hawala.xns.level3.courier.CrProgram;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.UNSPECIFIED2;
import dev.hawala.xns.level3.courier.UNSPECIFIED3;
import dev.hawala.xns.level3.courier.iWireData;
import dev.hawala.xns.level3.courier.iWireStream;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;

/**
 * Definition the BulkData transfer Courier datatype defined in a
 * separate program (as PROGRAM 0 VERSION 1), allowing to read/write
 * data from the wire stream for {@code immediate} transfers (so-called
 * "intra-call bulk data transfer").
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class BulkData1 extends CrProgram {
	
	public static final int PROGRAM = 0;
	public static final int VERSION = 1;
	
	public int getProgramNumber() { return PROGRAM; }
	
	public int getVersionNumber() { return VERSION; }
	
	/*
	 * Identifier: TYPE = RECORD [
	 *     host: UNSPECIFIED3,  
	 *     hostRelativeIdentifier: UNSPECIFIED2 ];
	 */
	public static class Identifier extends RECORD {
		public final UNSPECIFIED3 host = mkMember(UNSPECIFIED3::make);
		public final UNSPECIFIED2 hostRelativeIdentifier = mkMember(UNSPECIFIED2::make);
		
		public static Identifier make() { return new Identifier(); }
	}
	
	/*
	 * Descriptor: TYPE = CHOICE OF {
	 *   null(0), immediate(1) => RECORD [ ],
	 *   passive(2), active(3) => RECORD [ 
	 *  	network: UNSPECIFIED2,
	 *  	host: UNSPECIFIED3,
	 *  	identifier: Identifier ]
	 * };
	 */
	public enum DescriptorKind { nullKind, immediate, passive, active };
	public static final EnumMaker<DescriptorKind> mkDescriptorKind = buildEnum(DescriptorKind.class).get();
	public static class Descriptor_active_passive extends RECORD {
		public final UNSPECIFIED2 network = mkMember(UNSPECIFIED2::make);
		public final UNSPECIFIED3 host = mkMember(UNSPECIFIED3::make);
		public final Identifier   identifier = mkRECORD(Identifier::make);
		
		public static Descriptor_active_passive make() { return new Descriptor_active_passive(); }
	}
	private static final ChoiceMaker<DescriptorKind> mkDescriptor = buildChoice(mkDescriptorKind)
			.choice(DescriptorKind.nullKind, RECORD::empty)
			.choice(DescriptorKind.immediate, RECORD::empty)
			.choice(DescriptorKind.passive, Descriptor_active_passive::make)
			.choice(DescriptorKind.active, Descriptor_active_passive::make)
			.get();

	private static abstract class Descriptor extends RECORD {
		
		public final CHOICE<DescriptorKind> descriptor = mkCHOICE(mkDescriptor);
		
		protected iWireStream wireStream = null;
		protected DescriptorKind transferKind = null;
		
		@Override
		public void deserialize(iWireStream ws) throws EndOfMessageException {
			super.deserialize(ws);
			this.wireStream = ws;
			this.transferKind = this.descriptor.getChoice();
		}
		
		protected void checkTransferMode() {
			if (this.wireStream == null || this.transferKind == null) {
				throw new IllegalStateException("BulkData.1Sink not yet deserialized, unable to perform bulk data transfer");
			}
			if (this.transferKind == DescriptorKind.nullKind) {
				throw new IllegalStateException("BulkData1.Sink: no bulk data transfer requested (Descriptor == null)");
			}
			if (this.transferKind != DescriptorKind.immediate) {
				throw new IllegalStateException("BulkData.1Sink: third party bulk data transfer not supported (Descriptor == " + this.transferKind +  ")");
			}
		}
		
	}
	
	public static class Sink extends Descriptor {
		
		/**
		 * Send {@code content} as intra-call bulk data transfer, if the receiver
		 * expects an 'immediate' transfer.
		 * 
		 * @param content the Courier serializable data
		 * @return {@code true} if the receiver requested an abort during transfer
		 * 		by sending an interrupt.
		 * @throws NoMoreWriteSpaceException in case of a transmission problem 
		 */
		public boolean send(iWireData content) throws NoMoreWriteSpaceException {
			boolean aborted = false;
			this.checkTransferMode();
			
			try {
				// switch Courier connection to bulk data transfer  
				this.wireStream.beginStreamType(Constants.SPPSST_BDT);
				
				// send content, handling interruption from receiver
				if (content != null) {
					try {
						content.serialize(this.wireStream);
					} catch(NoMoreWriteSpaceException writeEx) {
						aborted = true;
						Log.L4.printf(null, "** BulkData1.Sink.send() :: bulk transfer interrupted by receiver => premature end of data transfer\n");
					}
				}
				this.wireStream.writeEOM();
			} finally {
				// switch Courier connection to RPC
				this.wireStream.beginStreamType(Constants.SPPSST_RPC);
			}
			
			return aborted;
		}
		
		private Sink() { }
		public static Sink make() { return new Sink(); }
	}
	
	public static class Source extends Descriptor {
		
		public boolean receive(iWireData target) throws EndOfMessageException {
			boolean prematureEnd = false;
			this.checkTransferMode();
			
			if (this.wireStream.getStreamType() != Constants.SPPSST_BDT) {
				this.wireStream.dropToEOM(Constants.SPPSST_BDT);
			}
			
			if (target != null) {
				try {
					target.deserialize(this.wireStream);
				} catch (EndOfMessageException e) {
					prematureEnd = true;
					Log.L4.printf(null, "** BulkData1.Source.receive() :: bulk transfer prematurely end while receiving data\n");
				}
			}

			if (!this.wireStream.isAtEnd()) {
				this.wireStream.dropToEOM(Constants.SPPSST_END);
			}
			
			return prematureEnd;
		}
		
		private Source() { }
		public static Source make() { return new Source(); }
	}
	
	/*
	 * missing : cancel() (necessary ??)
	 */
}
