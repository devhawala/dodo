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

package dev.hawala.xns;

import dev.hawala.xns.level2.Error.ErrorCode;

/**
 * Handler for PEX request packets sent to a service socket
 * on the local machine,
 * <p>
 * An instance of this interface is registered as listener for
 * a local socket and will invoked for each packet directed to
 * this socket. 
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2016-2018)
 */
@FunctionalInterface
public interface iPexResponder {

	@FunctionalInterface
	public interface ResponseSender {
		void sendResponse(byte[] buffer, int offset, int length);
	}
	
	@FunctionalInterface
	public interface ErrorSender {
		void sendError(ErrorCode code, int param);
	}
	
	void handlePacket(
			long fromMachineId,
			int clientType,
			byte[] payload,
			ResponseSender responseSender,
			ErrorSender errorSender);
}
