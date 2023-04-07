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
import java.io.OutputStream;

/**
 * Configurable mapper for control items sent by the (Xerox-world) terminal emulator
 * to the corresponding control characters for the external system.
 * <p>
 * Such a mapping is necessary for differing representations (line-end, backspace) and
 * also due to the restricted support for true control characters by Xerox terminal emulators.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2023)
 */
public class XeroxControlCharMapper {
	private final boolean mapCrToCrLf;
	private final boolean mapCrToLf;
	private final boolean mapBreakToCtrlC;
	private final boolean mapBreakByNext;
	private final boolean mapBackspaceToRubout;

	private boolean mapNextCharToControl = false;

	/**
	 * Constructor for a character mapper.
	 * 
	 * @param mapCarriageReturn one of: {@code none}, {@code crlf}, {@code lf}
	 * @param mapBreak one of: {@code none}, {@code ctrlC}, {@code ctrlNextChar}
	 * @param mapBackspace one of: {@code none}, {@code rubout}
	 */
	public XeroxControlCharMapper(String mapCarriageReturn, String mapBreak, String mapBackspace) {
		this.mapCrToCrLf = "crlf".equalsIgnoreCase(mapCarriageReturn);
		this.mapCrToLf = "lf".equalsIgnoreCase(mapCarriageReturn);
		this.mapBreakToCtrlC = "ctrlC".equalsIgnoreCase(mapBreak);
		this.mapBreakByNext = "ctrlNextChar".equalsIgnoreCase(mapBreak);
		this.mapBackspaceToRubout =  "rubout".equalsIgnoreCase(mapBackspace);
	}
	
	public boolean handleControlCode(int code, OutputStream streamClient2remote) throws IOException {
		if (code != GapCodes.ctl_interrupt) {
			 return false;
		}
		if (mapBreakToCtrlC) {
			streamClient2remote.write(0x03); // Ctrl-C
			streamClient2remote.flush();
		} else if (code == GapCodes.ctl_interrupt && mapBreakByNext) {
			this.mapNextCharToControl = true;
		}
		return true;
	}
	
	public void map(byte[] buffer, int usedLength, OutputStream streamClient2remote) throws IOException {
		for (int i = 0; i < usedLength; i++) {
			byte b = buffer[i];
			if (this.mapNextCharToControl && b >= '@' && b <= '_') {
				streamClient2remote.write(b - '@');
			} else if (this.mapNextCharToControl && b >= '`' && b <= '~') {
				streamClient2remote.write(b - '`');
			} else if (b == 0x0D && mapCrToCrLf) {
				streamClient2remote.write(0x0D);
				streamClient2remote.write(0x0A);
			} else if (b == 0x0D && mapCrToLf) {
				streamClient2remote.write(0x0A);
			} else if (b == 0x08 && mapBackspaceToRubout) {
				streamClient2remote.write(0x7F); // RUBOUT
			} else {
				streamClient2remote.write(b);
			}
			this.mapNextCharToControl = false;
		}
		streamClient2remote.flush();
	}

	@Override
	public String toString() {
		return "XnsControlCharMapper [mapCrToCrLf=" + mapCrToCrLf +
				", mapCrToLf=" + mapCrToLf +
				", mapBreakToCtrlC=" + mapBreakToCtrlC +
				", mapBreakByNext=" + mapBreakByNext +
				", mapBackspaceToRubout=" + mapBackspaceToRubout +
				"]";
	}
	
}
