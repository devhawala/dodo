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

package dev.hawala.xns.level3.courier;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of an {@code iJsonWriter} producing a string holding
 * the JSON item (object, array or simple value).
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2023)
 */
public class JsonStringWriter implements iJsonWriter {
	
	private final StringBuilder sb = new StringBuilder();
	
	private boolean inhibitNextComma = false;
	private boolean atFirst = true;
	private List<Boolean> atFirstStack = new ArrayList<>();
	private void openBlock() {
		this.atFirstStack.add(this.atFirst);
		this.atFirst = true;
		this.inhibitNextComma = false;
	}
	private void closeBlock() {
		if (this.atFirstStack.isEmpty()) {
			throw new IllegalStateException("block underflow => mismatching openBlock() .. closeBlock() calls");
		}
		this.atFirst = this.atFirstStack.remove(this.atFirstStack.size() - 1);
		this.inhibitNextComma = false;
	}
	private void newElem() {
		if (this.inhibitNextComma) {
			this.inhibitNextComma = false;
			return;
		}
		if (!this.atFirst) { this.sb.append(",\n"); }
		for (int i = 0; i < this.atFirstStack.size(); i++) { this.sb.append("  "); }
		this.atFirst = false;
		this.inhibitNextComma = false;
	}
	
	private void wrString(String s) {
		this.sb.append("\"");
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c < 32) {
				this.sb.append('\\').append(c + 32);
			} else if (c == '\\') {
				this.sb.append('\\').append('\\');
			} else if (c == '"') {
				this.sb.append('\\').append(c);
			} else {
				this.sb.append(c);
			}
		}
		this.sb.append("\"");
	}
	
	public void reset() {
		this.sb.setLength(0);
	}
	
	public String get() {
		return this.sb.toString();
	}

	@Override
	public iJsonWriter writeFieldLabel(String label) {
		this.newElem();
		this.wrString(label);
		this.sb.append(": ");
		this.inhibitNextComma = true;
		return this;
	}

	@Override
	public iJsonWriter writeString(String value) {
		this.newElem();
		this.wrString(value);
		return this;
	}

	@Override
	public iJsonWriter writeBoolean(boolean value) {
		this.newElem();
		this.sb.append(value ? "true" : "false");
		return this;
	}

	@Override
	public iJsonWriter writeNumber(long value) {
		this.newElem();
		this.sb.append(value);
		return this;
	}

	@Override
	public iJsonWriter openArray() {
		this.newElem();
		this.sb.append("[\n");
		this.openBlock();
		return this;
	}

	@Override
	public iJsonWriter closeArray() {
		this.closeBlock();
		this.sb.append('\n');
		for (int i = 0; i < this.atFirstStack.size(); i++) { this.sb.append("  "); }
		this.sb.append(']');
		return this;
	}

	@Override
	public iJsonWriter openStruct() {
		this.newElem();
		this.sb.append("{\n");
		this.openBlock();
		return this;
	}

	@Override
	public iJsonWriter closeStruct() {
		this.closeBlock();
		this.sb.append('\n');
		for (int i = 0; i < this.atFirstStack.size(); i++) { this.sb.append("  "); }
		this.sb.append('}');
		return this;
	}

}
