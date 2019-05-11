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
import dev.hawala.xns.iPexResponder;
import dev.hawala.xns.level3.courier.WirePEXRequestResponse;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;

/**
 * Base implementation for a "Broadcast for Servers" PEX responder.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public abstract class AbstractBfsResponder implements iPexResponder {
	
	protected abstract boolean isProgramNoOk(int programNo);
	
	protected abstract boolean isVersionOk(int versionNo);
	
	protected abstract boolean isProcNoOk(int procNo);
	
	protected abstract void processRequest(
								long fromMachineId,
								int programNo,
								int versionNo,
								int procNo,
								int transaction,
								WirePEXRequestResponse wire)
							throws NoMoreWriteSpaceException, EndOfMessageException;

	@Override
	public void handlePacket(long fromMachineId, int clientType, byte[] payload, ResponseSender responseSender, ErrorSender errorSender) {
		WirePEXRequestResponse wire = new WirePEXRequestResponse(payload);
		int othersLow = -1;
		int othersHigh = -1;
		int messageType = -1;
		int transaction = -1;
		int programNo = -1;
		int programVersion = -1;
		int procNo = -1;
		try {
			// read the courier protocol header
			othersLow = wire.readI16();
			othersHigh = wire.readI16();
			messageType = wire.readI16();
			transaction = wire.readI16();
			programNo = (othersHigh == 3) ? wire.readI32() : wire.readI16() & 0xFFFF;
			programVersion = wire.readI16() & 0xFFFF;
			procNo = wire.readI16() & 0xFFFF;
			if (messageType != 0) {
				Log.E.printf("BfsX", "ERROR not a 'call', courier-vers[%d,%d] , transaction=%d , prog=%d , vers=%d , proc=%d\n",
					othersLow, othersHigh, transaction, programNo, programVersion, procNo);
				return; // broadcast for clearinghouse not a courier call? => ignore
			}
			
			// check if the correct program and procedure is to be invoked
			if (!this.isProgramNoOk(programNo) || !this.isVersionOk(programVersion) || !this.isProcNoOk(procNo)) {
				Log.E.printf("BfsX", "ERROR unimplemented program/version/proc: courier-vers[%d,%d] , transaction=%d , prog=%d , vers=%d , proc=%d\n",
						othersLow, othersHigh, transaction, programNo, programVersion, procNo);
				return; // broadcast for service (clearinghouse/authentication) with wrong method? => ignore 
			}
			Log.I.printf("BfsX",
					"Bfs with courier protocol-version[%d,%d] , transaction=%d , program=%d , version=%d , procedure=%d\n",
					othersLow, othersHigh, transaction, programNo, programVersion, procNo);
			
			// send courier protocol version prefix
			wire.writeI16(othersLow);
			wire.writeI16(othersHigh);
			
			// execute the procedure
			this.processRequest(fromMachineId, programNo, programVersion, procNo, transaction, wire);
			
			// send back response
			wire.sendAsResponse(responseSender);
			Log.I.printf("BfsX",
					"Bfs call done for transaction=%d , program=%d , version=%d , procedure=%d\n",
					transaction, programNo, programVersion, procNo);
		
		} catch(Exception e) {
			Log.E.printf(
					"BfsX",
					"Bfs failed for courier-vers[%d,%d] , transaction=%d , prog=%d , vers=%d , proc=%d, exception: %s\n",
					othersLow, othersHigh, transaction, programNo, programVersion, procNo, e.getMessage());
			// all exceptions are ignored, as this was a broadcast, so don't reply with an error...
		}
	}

}
