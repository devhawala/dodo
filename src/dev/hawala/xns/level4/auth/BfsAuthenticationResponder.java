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

package dev.hawala.xns.level4.auth;

import dev.hawala.xns.Log;
import dev.hawala.xns.level3.courier.WirePEXRequestResponse;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;
import dev.hawala.xns.level4.common.AbstractBfsResponder;

/**
 * Implementation of the responder for the "Broadcast for Servers" (Bfs)
 * packets, providing the "retrieve addresses of authentication servers"
 * function. 
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class BfsAuthenticationResponder extends AbstractBfsResponder {
	
	private Authentication2 bfsImpl = Authentication2Impl.getBfsImplementation();

	@Override
	protected boolean isProgramNoOk(int programNo) {
		return (programNo == this.bfsImpl.getProgramNumber());
	}

	@Override
	protected boolean isVersionOk(int versionNo) {
		return true; // (assuming that all versions have the same RetrieveAddresses method) ... (versionNo == this.bfsImpl.getVersionNumber());
	}

	@Override
	protected boolean isProcNoOk(int procNo) {
		return (procNo == this.bfsImpl.RetrieveAddresses.getProcNumber());
	}

	@Override
	protected void processRequest(long fromMachineId, int programNo, int versionNo, int procNo, int transaction, WirePEXRequestResponse wire) throws NoMoreWriteSpaceException, EndOfMessageException {
		Log.C.printf("BfsAth", "BfsAuthenticationResponder.processRequest() - begin\n");
		this.bfsImpl.RetrieveAddresses.process(transaction, wire);
		Log.C.printf("BfsAth", "BfsAuthenticationResponder.processRequest() - done\n");
	}

}
