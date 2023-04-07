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
 * SPP connection with a remote destination.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2016-2018,2023)
 */
public interface iSppSocket {

	/**
	 * @return the local endpoint address data of this SPP connection.
	 */
	EndpointAddress getLocalEndpoint();
	
	/**
	 * @return the remote endpoint address data of this SPP connection.
	 */
	EndpointAddress getRemoteEndpoint();
	
	/**
	 * @return the input packet stream receiver for this connection
	 * 		(this will always return the same instance in the lifetime
	 * 		of the connection).
	 */
	iSppInputStream getInputStream();
	
	/**
	 * @return the output packet stream sender for this connection
	 * 		(this will always return the same instance in the lifetime
	 * 		of the connection).
	 */
	iSppOutputStream getOutputStream();
	
	/**
	 * Close the SPP connection to the remote end.
	 */
	void close();
	
	/**
	 * If the other end actively initiated the connection close protocol,
	 * should we wait in a small time-frame for an active re-use if this
	 * connection (although in closed state) by the remote end and then
	 * re-open this same connection again?
	 * <br/>
	 * (this is {@code false} by default, so closed connections stay closed)
	 * 
	 * @param allowReAwaking if {@code true}, the connection will be kept in
	 *     passive state for some time, awaiting a re-awaking the the remote end
	 */
	void handleAwakeAfterCloseByRemote(boolean allowReAwaking);
	
}
