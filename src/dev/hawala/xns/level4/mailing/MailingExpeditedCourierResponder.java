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

import dev.hawala.xns.level3.courier.CrProgram;
import dev.hawala.xns.level3.courier.CrProgram.PROC;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.WirePEXRequestResponse;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;
import dev.hawala.xns.level4.common.AbstractBfsResponder;

/**
 * Implementation for "expedited Courier" remote procedure calls for
 * Mailing, where "expedited Courier" means that both the request and
 * response must each fit in one IDP packet; in this case a Courier
 * procedure call does not need to be transported in an SPP Courier stream.
 * <p>
 * The following procedures are known to be called through expedited Courier
 * by ViewPoint/XDE:
 * </p>
 * <ul>
 * <li>MailTransport.ServerPoll (program 17, version 4, procedure 0)</li>
 * <li>Inbasket.MailCheck (program 18, version 1, procedure 6)</li>
 * </ul>
 * <p>
 * The following procedures are known to be called through expedited Courier
 * by GlobalView:
 * </p>
 * <ul>
 * <li>MailTransport5.ServerPoll (program 17, version 5, procedure 0)</li>
 * <li>Inbasket2.InbasketPoll (program 18, version 2, procedure 7)</li>
 * </ul>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2020/2022)
 */
public class MailingExpeditedCourierResponder extends AbstractBfsResponder {
	
	private final MailTransport4 mailTransport;
	private final Inbasket1      inbasket;
	
	private final MailTransport5 mailTransport5;
	private final Inbasket2      inbasket2;
	
	public MailingExpeditedCourierResponder() {
		this.mailTransport = MailingOldImpl.getMailTransportImpl();
		this.inbasket = MailingOldImpl.getInbasketImpl();
		this.mailTransport5 = MailingNewImpl.getMailTransport5Impl();
		this.inbasket2 = MailingNewImpl.getInbasket2Impl();
	}

	@Override
	protected boolean isProgramNoOk(int programNo) {
		return (programNo == this.mailTransport.getProgramNumber() || programNo == this.inbasket.getProgramNumber());
	}

	@Override
	protected boolean isVersionOk(int versionNo) {
		return true; // each of the involved programs has a single version, so assume the client behaves
	}

	@Override
	protected boolean isProcNoOk(int procNo) {
		return true; // cannot be checked here
	}

	@Override
	protected void processRequest(long fromMachineId, int programNo, int versionNo, int procNo, int transaction, WirePEXRequestResponse wire) throws NoMoreWriteSpaceException, EndOfMessageException {
		// check if it is for MailTransport
		if (programNo  == this.mailTransport.getProgramNumber() && versionNo == this.mailTransport.getVersionNumber()) {
			if (procNo == this.mailTransport.ServerPoll.getProcNumber()) {
				this.mailTransport.ServerPoll.process(transaction, wire);
				return;
			}
			// others called by VP/XDE?
		}
		
		// check if it is for Inbasket
		if (programNo  == this.inbasket.getProgramNumber() && versionNo == this.inbasket.getVersionNumber()) {
			if (procNo == this.inbasket.MailCheck.getProcNumber()) {
				this.inbasket.MailCheck.process(transaction, wire);
				return;
			}
			// others called by VP/XDE?
		}
		
		// check if it is for MailTransport5.ServerPoll
		if (isDoneBy(this.mailTransport5, this.mailTransport5.ServerPoll, programNo, versionNo, procNo, transaction, wire)) {
			return;
		}
		
		// check if it is for Inbasket2.inbasketPoll
		if (isDoneBy(this.inbasket2, this.inbasket2.MailPoll, programNo, versionNo, procNo, transaction, wire)) {
			return;
		}
		
		// not a procedure allowed for expedited Courier, so abort...
		wire.writeI16(transaction);
		wire.writeI16(1); // MessageType.reject(1)
		wire.writeI16(2); // RejectCode.noSuchProcedureValue(2)
		wire.writeEOM();
	}
	
	private <P extends RECORD,R extends RECORD>
			boolean isDoneBy(
				CrProgram crProgram, PROC<P,R> crProc,
				int programNo, int versionNo, int procNo,
				int transaction, WirePEXRequestResponse wire
			) throws NoMoreWriteSpaceException, EndOfMessageException {
		if (programNo  == crProgram.getProgramNumber() && versionNo == crProgram.getVersionNumber()) {
			if (procNo == crProc.getProcNumber()) {
				crProc.process(transaction, wire);
				return true;
			}
		}
		return false;
	}

}
