/*
Copyright (c) 2023, Dr. Hans-Walter Latz
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

package dev.hawala.xns.level4.gap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import dev.hawala.xns.XnsException;

/**
 * Initiator for a "connection" to an "external system" by executing a shell command
 * which must handle whatever is needed to provide the interaction with that system.
 * <p>
 * <b>WARNING</b>: as the shell command is executed in the context of the Dodo process,
 * it will inherit the user identity, working directory, environment variable etc., so
 * this opens all doors for misuse wide open, so use this connection type <b>very</b>
 * carefully. 
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2023)
 */
public class TTYShellCommandConnectionCreator implements iGapConnectionCreator {
	
	private final String connectScript;
	private final XeroxControlCharMapper clientCharMapper;

	TTYShellCommandConnectionCreator(String connectScript, XeroxControlCharMapper clientCharMapper) {
		this.connectScript = connectScript;
		this.clientCharMapper = clientCharMapper;
	}

	@Override
	public GapConnectionHandler create() {
		System.out.printf("\n+++\n");
		System.out.printf("+++++ TTYShellCommandConnectionCreator: connecting to:\n");
		System.out.printf("+++      -> connectScript: '%s'\n", this.connectScript);
		System.out.printf("+++      -> clientCharMapper: '%s'\n", this.clientCharMapper);
		System.out.printf("+++\n");
		
		try {
			return new TTYShellCommandConnectionHandler(this.connectScript, this.clientCharMapper);
		} catch (Exception e) {
			System.out.printf("\n+++\n");
			System.out.printf("+++++ TTYShellCommandConnectionCreator :: ERROR %s : %s\n", e.getClass().getName(), e.getMessage());
			System.out.printf("+++\n");
			return null;
		}
	}
	
	private static class TTYShellCommandConnectionHandler extends GapConnectionHandler implements Runnable {

		private final XeroxControlCharMapper clientCharMapper;
		
		private final Process process;
		
		private final InputStream streamProcess2client;
		private final OutputStream streamClient2process;
		
		private Thread threadProcess2client = null;
		
		private TTYShellCommandConnectionHandler(String connectScript, XeroxControlCharMapper clientCharMapper) throws Exception {
			this.clientCharMapper = clientCharMapper;
			
			String[] args = connectScript.split(" ");
			List<String> cmdline = Arrays.asList(args);
			ProcessBuilder pb = new ProcessBuilder(cmdline).redirectErrorStream(true);
			this.process = pb.start();
			this.streamProcess2client = this.process.getInputStream();
			this.streamClient2process = this.process.getOutputStream();
		}

		@Override
		protected void handleClientConnected() throws XnsException, InterruptedException {
			this.threadProcess2client = new Thread(this);
			this.threadProcess2client.start();
		}

		@Override
		protected void handleDisconnected() throws XnsException, InterruptedException {
			System.out.printf("+++++ TTYShellCommandConnectionHandler.handleDisconnected() ... delegating to doCleanup()\n");
			this.doCleanup();
		}

		@Override
		protected boolean isRemotePresent() throws XnsException, InterruptedException {
			return this.process.isAlive();
		}

		@Override
		protected synchronized void doCleanup() throws XnsException, InterruptedException {
			if (this.threadProcess2client == null) { return; }
			System.out.printf("+++++ TTYShellCommandConnectionHandler.doCleanup() ... this.process.destroyForcibly()\n");
			int rc = this.process.destroyForcibly().waitFor();
			System.out.printf("+++++ ==> rc = %d\n", rc);
			try {
				System.out.printf("+++++ TTYShellCommandConnectionHandler.doCleanup() ... this.streamProcess2client.close()\n");
				this.streamProcess2client.close();
				System.out.printf("+++++ TTYShellCommandConnectionHandler.doCleanup() ... this.streamClient2process.close()\n");
				this.streamClient2process.close();
			} catch (IOException e) {
				System.out.printf("+++++ TTYShellCommandConnectionHandler.doCleanup() :: ERROR %s : %s\n", e.getClass().getName(), e.getMessage());
			}
			System.out.printf("+++++ TTYShellCommandConnectionHandler.doCleanup() ... this.threadProcess2client.interrupt()\n");
			this.threadProcess2client.interrupt();
			System.out.printf("+++++ TTYShellCommandConnectionHandler.doCleanup() ... this.threadProcess2client.join()\n");
			this.threadProcess2client.join();
			System.out.printf("+++++ TTYShellCommandConnectionHandler.doCleanup() ... this.threadProcess2client = null\n");
			this.threadProcess2client = null;
			System.out.printf("+++++ TTYShellCommandConnectionHandler.doCleanup() done\n");
		}

		@Override
		protected void handleControlCode(int code) throws XnsException, InterruptedException {
			try {
				this.clientCharMapper.handleControlCode(code, this.streamClient2process);
			} catch (IOException e) {
				System.out.printf("+++++ TTYShellCommandConnectionHandler.handleControlCode (client => process) :: ERROR %s : %s\n", e.getClass().getName(), e.getMessage());
			}
		}

		@Override
		protected void handleData(byte[] buffer, int usedLength, boolean isEOM) throws XnsException, InterruptedException {
			try {
				System.out.printf("+++++ TTYShellCommandConnectionHandler.handleData (client => process) :: userlength = %d , isEOM: %s\n", usedLength, isEOM);
				this.clientCharMapper.map(buffer, usedLength, this.streamClient2process);
			} catch (IOException e) {
				System.out.printf("+++++ TTYShellCommandConnectionHandler (client => process) :: ERROR %s : %s\n", e.getClass().getName(), e.getMessage());
			}
		}

		@Override
		protected void handleDataDisconnected(byte[] buffer, int usedLength, boolean isEOM)throws XnsException, InterruptedException {
			// any input from xns client ignored
		}

		@Override
		public void run() {
			byte[] buffer = new byte[512];
			
			try {
				int byteCount = this.streamProcess2client.read(buffer, 0, buffer.length);
				while(byteCount > 0) {
					this.sendDataEOM(buffer, 0, byteCount);
					byteCount = this.streamProcess2client.read(buffer, 0, buffer.length);
				}
			} catch (Exception e) {
				System.out.printf("+++++ TTYShellCommandConnectionHandler (process => client) :: Exception %s : %s\n", e.getClass().getName(), e.getMessage());
			}
			System.out.printf("+++++ TTYShellCommandConnectionHandler (process => client) :: exiting receiver thread\n");
		}
		
	}
	
}
