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

/**
 * Functionality of the sending (output) stream of an SPP connection.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2016-2018,2023)
 */
public interface iSppOutputStream {
	
	boolean isClosed();
	
	void sendAttention(byte attentionByte) throws XnsException, InterruptedException;
	
	void sendAttention(byte attentionByte, byte datastreamType) throws XnsException, InterruptedException;

	int write(byte[] buffer, byte datastreamType) throws XnsException, InterruptedException;
	
	int write(byte[] buffer, byte datastreamType, boolean isEndOfMessage) throws XnsException, InterruptedException;

	int write(byte[] buffer, int offset, int length, byte datastreamType) throws XnsException, InterruptedException;
	
	int write(byte[] buffer, int offset, int length, byte datastreamType, boolean isEndOfMessage) throws XnsException, InterruptedException;
	
	/**
	 * Ensure that all pending outgoing packets are transmitted. 
	 */
	void sync();
	
	/**
	 * Check if the other end sent an interrupt for requesting the
	 * end of the current transmission
	 * 
	 * @return {@code null} if no interrupt was received from the other
	 * 		end or the interruption byte if an interrupt packet was received. 
	 */
	Byte checkForInterrupt();
}
