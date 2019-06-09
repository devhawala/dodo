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
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;
import dev.hawala.xns.level4.common.BulkData1.Sink;
import dev.hawala.xns.level4.filing.fs.iContentSink;

/**
 * {@code iContentSink} implementation for transferring (sending) a file content
 * to a {@code BulkData.Sink}.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2019)
 */
public class ByteContentSink implements iContentSink {
	
	private final Sink sink;
	private final iWireStream wireStream;
	
	private boolean done = false;
	
	public ByteContentSink(Sink sink) throws NoMoreWriteSpaceException {
		this.sink = sink;
		this.wireStream = sink.lockWirestream();
	}

	@Override
	public int write(byte[] buffer, int count) {
		if (this.done) {
			throw new IllegalStateException("transfer already closed");
		}
		int transferred = 0;
		try {
			if (buffer == null) {
				this.done = true;
				this.wireStream.writeEOM();
				this.sink.unlockWireStream();
				return 0;
			}
			int limit = Math.min(count, buffer.length);
			for (int i = 0; i < limit; i++) {
				this.wireStream.writeI8(buffer[i]);
				transferred++;
			}
		} catch (NoMoreWriteSpaceException e) {
			System.out.println("ByteContentSink => got NoMoreWriteSpaceException");
			return -1;
		}
		return transferred;
	}

}
