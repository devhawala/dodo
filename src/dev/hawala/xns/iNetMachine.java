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

import dev.hawala.xns.level0.Payload;
import dev.hawala.xns.network.iIDPReceiver;
import dev.hawala.xns.network.iIDPSender;

/**
 * Network functionality provided by the local XNS machine.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016-2018
 */
public interface iNetMachine {
	
	long getNetworkId();
	
	long getMachineId();
	
	String getMachineName();
	
	boolean clientBindToSocket(int sockNo, iIDPReceiver listener);
	
	int clientBindToFreeSocket(iIDPReceiver listener);
	
	boolean stopListening(int sockNo);
	
	iIDPSender getIdpSender();

	/**
	 * Open a local listener socket waiting for incoming remote
	 * requests to start a SPP connection with the local machine,
	 * 
	 * @param localPort
	 * 	 	the local socket number to be used for accepting
	 *   	incoming requests.
	 * @return
	 * 		the server socket allowing the accept new incoming
	 *   	SPP connections
	 * @throws XnsException
	 */
	iSppServerSocket sppListen(
			int localPort
		) throws XnsException;
	
	
	/**
	 * Open a SPP (client) connection to a socket at a
	 * remote machine in the local network.
	 * 
	 * @param remoteHostAddress
	 * 		identification of the remote machine to connect to
	 * @param remotePort
	 * 		socket number to connect to at the remote machine
	 * @return
	 * 		the SPP connection created to the remote machine 
	 * @throws XnsException
	 */
	iSppSocket sppConnect(
			long remoteHostAddress,
			int remotePort
		) throws XnsException;
	
	
	/**
	 * Open a SPP (client) connection to a socket at a
	 * remote machine.
	 * 
	 * @param remoteEndpoint
	 * 		identification of the remote machine to connect to
	 * @return
	 * 		the SPP connection created to the remote machine 
	 * @throws XnsException
	 */
	iSppSocket sppConnect(
			EndpointAddress remoteEndpoint
		) throws XnsException;
	
	
	/**
	 * Open a local listener socket waiting for incoming remote
	 * PEX packets directed to the given port at the local machine.
	 * 
	 * @param localPort
	 * 	 	the local socket number to be used for accepting
	 *   	incoming PEX packets.
	 * @param responder
	 * 		the packet processor to be used for servicing incoming
	 * 		PEX packets directed to the opened port.
	 * @return
	 * 		the pex socket instance allowing to control the PEX
	 * 		listener
	 * @throws XnsException
	 */
	iPexSocket pexListen(
			int localPort,
			iPexResponder responder
		) throws XnsException;
	
	iPexSocket pexListen(
			int localPort,
			iPexResponder responder,
			iWakeupRequestor wakeupRequestor
		) throws XnsException;
	
	/**
	 * Send a PEX request packet to a remote machine, await the
	 * response and return the response packet.
	 * 
	 * @param remoteHostAddress
	 * 		identification of the remote machine to send the request to
	 * @param remotePort
	 * 		socket number to connect to at the remote machine
	 * @param clientType
	 * 		PEX request type
	 * @param pBuffer
	 * 		data buffer with the PEX request content
	 * @param pOffset
	 * 		start offset for the request content in {@code pBuffer}
	 * @param pLength
	 * 		length of the request content in {@code pBuffer}
	 * @return
	 * 		the response packet from the remote machine, this can
	 * 		be:
	 * 		<ul>
	 * 		<li>a {@code PEX} packet in case of an ingone response
	 * 			to the request</li>
	 * 		<li>an {@code Error} packet in case of an error received
	 * 			through the error protocol for the request</li>
	 * 		<li>{@code null} is case of a timeout</li>
	 * 		</ul>
	 * @throws XnsException
	 */
	Payload pexRequest(
			long remoteHostAddress,
			int remotePort,
			int clientType,
			byte[] pBuffer,
			int pOffset,
			int pLength
		) throws XnsException;
	
	
	/**
	 * Send a PEX request packet to a remote machine, await the
	 * response and return the response packet.
	 * 
	 * @param remoteEndpoint
	 * 		identification of the remote machine to send the request to
	 * @param clientType
	 * 		PEX request type
	 * @param pBuffer
	 * 		data buffer with the PEX request content
	 * @param pOffset
	 * 		start offset for the request content in {@code pBuffer}
	 * @param pLength
	 * 		length of the request content in {@code pBuffer}
	 * @return
	 * 		the response packet from the remote machine, this can
	 * 		be:
	 * 		<ul>
	 * 		<li>a {@code PEX} packet in case of an ingone response
	 * 			to the request</li>
	 * 		<li>an {@code Error} packet in case of an error received
	 * 			through the error protocol for the request</li>
	 * 		<li>{@code null} is case of a timeout</li>
	 * 		</ul>
	 * @throws XnsException
	 */
	Payload pexRequest(
			EndpointAddress remoteEndpoint,
			int clientType,
			byte[] pBuffer,
			int pOffset,
			int pLength
		) throws XnsException;
	
	/**
	 * Configure the communication parameters for all subsequent PEX
	 * requests.
	 * 
	 * @param retransmitIntervalMs
	 * 		the time interval after which the request is sent again
	 * 		if no response arrived in the meantime. (default: 1000 ms)
	 * @param retryCount
	 * 		the number of resents to be tried before the whole request
	 * 		is considered to be timed out. (default: 1)
	 * @param raiseExceptionOnTimeout
	 * 		is the timeout of a PEX request to be signaled by raising
	 * 		a {@code XnsException} ({@code true}) or by returning
	 * 		{@code null} ({@code false})?
	 * @throws XnsException
	 */
	void configPexRequestors(
			long retransmitIntervalMs,
			int retryCount,
			boolean raiseExceptionOnTimeout
		) throws XnsException;
}
