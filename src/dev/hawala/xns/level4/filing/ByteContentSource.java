/*
Copyright (c) 2019, Dr. Hans-Walter Latz
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

package dev.hawala.xns.level4.filing;

import dev.hawala.xns.level3.courier.iWireStream;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;
import dev.hawala.xns.level4.common.BulkData1.Source;
import dev.hawala.xns.level4.filing.fs.iContentSource;

/**
 * {@code iContentSource} implementation for transferring (receiving) a file content
 * from a {@code BulkData.Source}.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2019)
 */
public class ByteContentSource implements iContentSource {
	
	private final Source source;
	private final iWireStream wireStream;
	
	private boolean done = false;
	
	public ByteContentSource(Source source) throws EndOfMessageException {
		this.source = source;
		this.wireStream = source.lockWirestream();
	}

	@Override
	public int read(byte[] buffer) {
		if (this.done) {
			return 0;
		}
		if (buffer == null) {
			this.done = true;
			System.out.printf("...ByteContentSource.read(null) -> transfer locally aborted\n");
			try {
				this.source.unlockWireStream(false);
			} catch (NoMoreWriteSpaceException | EndOfMessageException e1) {
				// ignored...
			}
			return 0;
		}
		
		int transferred = 0;
		try {
			// System.out.printf("...ByteContentSource.read() -> begin buffer\n");
			for (int i = 0; i < buffer.length; i++) {
				buffer[transferred] = (byte)this.wireStream.readI8();
				transferred++;
			}
		} catch (EndOfMessageException e) {
			this.done = true;
			try {
				// System.out.printf("...ByteContentSource.read() -> EndOfMessageException (done)\n");
				this.source.unlockWireStream(true);
			} catch (NoMoreWriteSpaceException | EndOfMessageException e1) {
				// ignored...
			}
		}
		// System.out.printf("...ByteContentSource.read() -> done buffer, transferred: %d\n", transferred);
		return transferred;
	}

}
