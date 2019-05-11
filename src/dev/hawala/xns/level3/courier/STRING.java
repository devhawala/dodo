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

package dev.hawala.xns.level3.courier;

import java.util.ArrayList;
import java.util.List;

import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;

/**
 * Representation of the Courier STRING datatype.
 * <p>
 * On the Java-side, a reversibly obfuscated unicode string encoding
 * scheme is used to represent an arbitrary Mesa-side string
 * encoded in XNS Character Encoding Standard when deserializing
 * the Courier-STRING, allowing to regenerate the original XNS-side byte
 * sequence when serializing again.
 * <br>/
 * This encoding scheme is primarily intended for use-cases where a string
 * transmitted to a Dodo service is persisted and later sent back to a
 * client, ensuring that the original content is returned.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018,2019)
 */
public class STRING implements iWireData {
	
	private final int maxLength;
	
	private String str;
	
	protected STRING(int maxLen) {
		this.maxLength = maxLen;
	}
	
	protected STRING() { this(0xFFFF); }

	public STRING set(String value) {
		// TODO: transform non-7bit-ascii chars into our specific reversible obfuscated encoding
		this.str = (value != null && value.length() > this.maxLength) ? value.substring(0, this.maxLength) :value;
		return this;
	}
	
	public String get() {
		return this.str;
	}

	@Override
	public void serialize(iWireStream ws) throws NoMoreWriteSpaceException {
		if (this.str == null || this.str.length() == 0) {
			ws.writeI16(0);
			return;
		}
		
		// get XString assuming that that content is a reversibly obfuscated in unicode string
		List<Integer> bytes = recode2XString(this.str);
		
		// use maxLength when issuing the encoded string (possibly truncate but then prevent truncating in the middle of a charset switcn 
		int bytesLength = bytes.size(); 
		if (bytes.size() > this.maxLength) {
			bytesLength = this.maxLength;
			if (bytesLength > 1 && bytes.get(bytesLength - 1) == 0xFF) {
				bytesLength--;
			}
		}
		ws.writeI16(bytesLength);
		for (int i = 0; i < bytesLength; i++) {
			ws.writeI8(bytes.get(i));
		}
		if ((this.str.length() % 2) != 0) {
			ws.writeI8(0);
		} 
	}

	@Override
	public void deserialize(iWireStream ws) throws EndOfMessageException {
		int len = ws.readI16() & 0xFFFF;
		if (len == 0) {
			this.str = "";
			return;
		}
		
		StringBuilder sb = new StringBuilder();
		
		int currCharset = 0;
		boolean lastWasFF = false;
		boolean twoByteMode = false;
		boolean twoByteFirstByte = false;
		for (int i = 0; i < len; i++) {
			int b = ws.readI8() & 0xFF;
			if (twoByteMode && twoByteFirstByte && b == 0xFF) {
				twoByteMode = false;
				lastWasFF = true;
				sb.append(codeEscape).append("0"); // ^0 -> return to 8-bit encoding with character-wise explicit code set switches (default at string start)
			} else if (twoByteMode && twoByteFirstByte) {
				currCharset = b;
				twoByteFirstByte = false;
			} else if (twoByteMode && !twoByteFirstByte) {
				encodeXChar(sb, currCharset, b);
				twoByteFirstByte = true;
			} else if (b == 0xFF && lastWasFF) {
				lastWasFF = false;
				twoByteMode = true;
				twoByteFirstByte = true;
				sb.append(codeEscape).append("1"); //  ^1 -> encoded in original as 16-bit chars sequence
			} else if (lastWasFF) {
				currCharset = b;
				lastWasFF = false;
			} else if (b == 0xFF) {
				lastWasFF = true;
			} else {
				encodeXChar(sb, currCharset, b);
				lastWasFF = false;
			}
		}

		if ((len % 2) != 0) {
			ws.readI8();
		}
		
		// result: XNS Character Encoding Standard => reversibly obfuscated in unicode string
		this.str = sb.toString();
	}
	
	@Override
	public StringBuilder append(StringBuilder to, String indent, String fieldName) {
		String valueStr = (this.str != null) ? "\"" + this.str + "\"" : "<null>";
		to.append(indent).append(fieldName).append(": ").append(valueStr);
		return to;
	}
	
	public static STRING make() { return new STRING(); }
	
	/*
	 * reversible obfuscated encoding of printable 7-bit, non-printable 8-bit or 16-bit characters
	 */
	
	private static final String enc26upperString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private static final String enc26lowerString = "abcdefghijklmnopqrstuvwxyz";
	private static final String enc10digitsString = "0123456789";
	private static final String enc62string = enc10digitsString + enc26upperString + enc26lowerString;
	private static final byte[] enc62 = enc62string.getBytes();
	private static final byte[] enc26lower = enc26lowerString.getBytes(); 
	private static final byte[] enc26upper = enc26upperString.getBytes();
	private static final byte[] enc10digit = enc10digitsString.getBytes();
	private static final char codeEscape = '^';
	
	/**
	 * Append a Courier/XString character either as plain Java char or as
	 * reversible obfuscated encoded 8-bit or 16-char.
	 * 
	 * @param sb the target Java string
	 * @param charset the charset of the XNS source character
	 * @param code the code of the XNS source character
	 */
	private static void encodeXChar(StringBuilder sb, int charset, int code) {
		if (charset == 0 && (code == (byte)codeEscape || code < 0x20 || code > 0x7F)) {
			// our code-escape or non-printable 8-bit charset 0 (~ 8-bit-acsii)
			// => ^[A-Z][0-9a-zA-Z] ( 26x62 = max. 1612 codes)
			int code1 = code / enc62.length;
			int code2 = code % enc62.length;
			sb.append(codeEscape).append((char)enc26upper[code1]).append((char)enc62[code2]);
			return;
		}
		if (charset == 0) {
			// printable ascii => direct encoding
			sb.append((char)code);
			return;
		}
		
		// anything in other charset than 0
		// => ^[a-z][0-9a-zA-Z][0-9a-zA-Z] ( 26x62x62 -> max. 99944 codes)
		int char16 = ((charset << 8) & 0xFF00) | (code & 0xFF);
		int code1 = char16 / (enc62.length * enc62.length);
		int rest1 = char16 % (enc62.length * enc62.length);
		int code2 = rest1 / enc62.length;
		int code3 = rest1 % enc62.length;
		sb.append(codeEscape).append((char)enc26lower[code1]).append((char)enc62[code2]).append((char)enc62[code3]);
	}
	
	/**
	 * Encode an reversibly obfuscated in unicode string back as raw Courier/XString
	 * byte code sequence.
	 * 
	 * @param encString the string to be recoded to a Courier/XString
	 * @return byte code sequence for Courier/XString
	 */
	private static List<Integer> recode2XString(String encString) {
		byte[] bytes = encString.getBytes();
		List<Integer> xs = new ArrayList<>();
		
		int last = bytes.length - 1;
		int pos = 0;
		
		boolean xs16bitsMode = false;
		int currentCharset = 0;
		
		while(pos <= last) {
			char c = (char)bytes[pos++];
			if (c == codeEscape) {
				if (pos >= last) { break; }
				int code1 = bytes[pos++] & 0xFF;
				if (code1 == (int)'1') {
					// switch to 16-bit sequence xns encoding
					xs.add(0xFF);
					xs.add(0xFF);
					xs16bitsMode = true;
				} else if (code1 == (int)'0') {
					// switch back to 8-bit with charset switching mode
					xs16bitsMode = false;
					currentCharset = -1; // force issuing the charset switch on next char
				} else if (code1 >= enc26upper[0] && code1 <= enc26upper[enc26upper.length-1]) {
					// charset 0 single char
					if (pos > last) { break; }
					int code2 = bytes[pos++] & 0xFF;
					int val1 = code1 - enc26upper[0];
					Integer val2 = code62val(code2);
					if (val2 != null) {
						int xchar = (val1 * enc62.length) + val2;
						currentCharset = recodeXChar(xs, xs16bitsMode, currentCharset, xchar);
					} else {
						// not in our encoding schema => pass it unchanged instead of raising an error
						xs.add((int)c);
						xs.add((int)code1);
						xs.add((int)code2);
					}
				} else if (code1 >= enc26lower[0] && code1 <= enc26lower[enc26lower.length-1]) {
					// 16-bit charset and char-code
					if (pos > (last - 1)) { break; }
					int code2 = bytes[pos++] & 0xFF;
					int code3 = bytes[pos++] & 0xFF;
					int val1 = code1 - enc26lower[0];
					Integer val2 = code62val(code2);
					Integer val3 = code62val(code3);
					if (val2 != null && val3 != null) {
						int xchar = (val1 * enc62.length * enc62.length) + (val2 * enc62.length) + val3;
						currentCharset = recodeXChar(xs, xs16bitsMode, currentCharset, xchar);
					} else {
						// not in our encoding schema => pass it unchanged instead of raising an error
						xs.add((int)c);
						xs.add((int)code1);
						xs.add((int)code2);
						xs.add((int)code3);
					}
				} else {
					// not in our encoding schema => pass it unchanged instead of raising an error
					xs.add((int)c);
					xs.add((int)code1);
				}
			} else {
				if (currentCharset != 0) {
					xs.add(0xFF);
					xs.add(0x00);
				}
				currentCharset = 0;
				xs.add((int)c);
			}
		}
		
		return xs;
	}
	
	/**
	 * Get the value encoded in an {@code enc62} character.
	 * 
	 * @param code the character to decode
	 * @return the value encoded in {@code code} (0..61) or {@code null}
	 * 		if the character is not an {@code enc62} character.
	 */
	private static Integer code62val(int code) {
		if (code >= enc10digit[0] && code <= enc10digit[enc10digit.length -1]) {
			return code - enc10digit[0];
		}
		if (code >= enc26upper[0] && code <= enc26upper[enc26upper.length - 1]) {
			return code + enc10digit.length - enc26upper[0];
		}
		if (code >= enc26lower[0] && code <= enc26lower[enc26lower.length - 1]) {
			return code + enc10digit.length + enc26upper.length - enc26lower[0];
		}
		return null;
	}
	
	/**
	 * Append raw Courier/XString byte to the current byte list, in the currently
	 * active encoding (8-/16-bit mode) starting with the current charset and returning
	 * the new charset in the byte list.
	 * 
	 * @param xs the current byte list of the raw Courier/XString
	 * @param xs16bits the current 8-/16-bit mode in {@code xs}
	 * @param currCharset the current charset at the end of {@code xs}
	 * @param xchar the 16-bit character (charset/code) to append
	 * @return the (new) current charset at the end of {@code xs}
	 */
	private static int recodeXChar(List<Integer> xs, boolean xs16bits, int currCharset, int xchar) {
		int newCharset = (xchar >> 8) & 0xFF;
		int charCode = xchar &0xFF;
		if (xs16bits) {
			xs.add(newCharset);
			xs.add(charCode);
		} else if (newCharset == currCharset) {
			xs.add(xchar);
		} else {
			xs.add(0xFF);
			xs.add(newCharset);
			xs.add(charCode);
		}
		return newCharset;
	}
	
}
