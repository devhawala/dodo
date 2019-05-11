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
 * Exception used to signal communication problems. 
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2016-2018)
 */
public class XnsException extends Exception {

	private static final long serialVersionUID = -4446974290780108147L;

	/**
	 * Detail information describing the communication
	 * problem provoking this exception.
	 */
	public enum ExceptionType {
		SocketAlreadyInUse,
		NoLocalSocketAvailable,
		NoRemoteListener,
		TransmissionTimeout,
		RemoteServiceDisappeared,
		ConnectionClosed,
		Stopped;
	};
	
	private final ExceptionType type;
	
	public XnsException(ExceptionType type, String message) {
		super(message);
		this.type = type;
	}
	
	public XnsException(ExceptionType type) {
		this.type = type;
	}
	
	public ExceptionType getType() {
		return this.type;
	}
	
}
