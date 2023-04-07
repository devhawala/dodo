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

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Implementation of an {@code iJsonReader} scanning a pre-loaded string
 * holding an JSON item (object, array or simple value).
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2023)
 */
public class JsonStringReader implements iJsonReader {
	
	private enum Token {
		startBlock,
		endBlock,
		startArray,
		endArray,
		colon,
		comma,
		string,
		number,
		boolTrue,
		boolFalse
	}
	
	private final char[] chars;
	private int currPos = -1;
	
	private int line = 1;
	private int lineChar = 0;
	
	public JsonStringReader(String source) {
		this.chars = source.toCharArray();
	}
	
	private char nextChar() {
		this.currPos++;
		this.lineChar++;
		if (this.currPos < this.chars.length) {
			return this.chars[this.currPos];
		}
		throw new IllegalStateException("invalid JSON: out-of-source-chars");
	}
	
	private void unNextChar() {
		this.currPos--;
		this.lineChar--;
	}
	
	private void syntaxError() {
		throw new IllegalStateException("invalid JSON: invalid syntax at line " + this.line + ", column " + this.lineChar);
	}
	private void syntaxError(Token expected, Token found) {
		throw new IllegalStateException("invalid JSON: expected " + expected + " but found " + found + " at line " + this.line + ", column " + this.lineChar);
	}
	private void syntaxError(Token expected1, Token expected2, Token found) {
		throw new IllegalStateException("invalid JSON: expected " + expected1 + " or " + expected2 + " but found " + found + " at line " + this.line + ", column " + this.lineChar);
	}
	
	private final StringBuilder sb = new StringBuilder();
	
	private static final Set<Character> trueChars = mkChars("true");
	private static final Set<Character> falseChars = mkChars("false");
	private static final Set<Character> numberChars = mkChars("0123456789.efEF+-");
	
	private static  Set<Character> mkChars(String s) {
		Set<Character> result = new HashSet<>();
		char[] chars = s.toCharArray();
		for (char c : chars) { result.add(c); }
		return result;
	}
	
	private String readIdentifier(char first, Set<Character> allowed) {
		this.sb.setLength(0);
		this.sb.append(first);
		char c = this.nextChar();
		while (allowed.contains(c)) {
			this.sb.append(c);
			c = this.nextChar();
		}
		this.unNextChar();
		return this.sb.toString();
	}
	
	private Token pendingToken = null;
	private String stringToken = null;
	private Number numberToken = null;
	
	private void ungetToken(Token token) {
		if (this.pendingToken != null) {
			throw new IllegalStateException("error parsing JSON: double unget token not allowed");
		}
		this.pendingToken = token;
	}
	
	private Token nextToken() {
		if (this.pendingToken != null) {
			Token result = this.pendingToken;
			this.pendingToken = null;
			return result;
		}
		this.stringToken = null;
		this.numberToken = null;
		while(true) {
			char c = this.nextChar();
			switch(c) {
			
			case ' ':
			case '\t':
				continue;
			case '\n':
				this.lineChar = 0;
				this.line++;
				continue;
			
			case '{': return Token.startBlock;
			case '}': return Token.endBlock;
			
			case '[': return Token.startArray;
			case ']': return Token.endArray;
			
			case ':': return Token.colon;
			case ',': return Token.comma;
			
			case 't':
				if ("true".equals(this.readIdentifier('t', trueChars))) {
					return Token.boolTrue;
				}
				syntaxError();
				return null; // keep the compiler happy
			case 'f':
				if ("false".equals(this.readIdentifier('f', falseChars))) {
					return Token.boolFalse;
				}
				syntaxError();
				return null; // keep the compiler happy
				
			case '"':
				this.sb.setLength(0);
				c = this.nextChar();
				while(c != '"') {
					if (c == '\\') {
						c = this.nextChar();
						if (c == '"') {
							this.sb.append('"');
						} else if (c == '\\') {
							this.sb.append('\\');
						} else {
							this.sb.append(c - 32);
						}
					} else {
						sb.append(c);
					}
					c = this.nextChar();
				}
				this.stringToken = this.sb.toString();
				return Token.string;
				
			default: // should be a number
				String num = this.readIdentifier(c, numberChars);
				try {
					this.numberToken = Double.parseDouble(num);
					return Token.number;
				} catch (NumberFormatException nfe) {
					syntaxError();
					return null; // keep the compiler happy
				}
			}
		}
	}

	@Override
	public String readString() {
		Token token = this.nextToken();
		if (token != Token.string) {
			syntaxError(Token.string, token);
		}
		return this.stringToken;
	}

	@Override
	public boolean readBoolean() {
		Token token = this.nextToken();
		if (token != Token.boolTrue && token != Token.boolFalse) {
			syntaxError(Token.boolFalse, Token.boolTrue, token);
		}
		return (token == Token.boolTrue);
	}

	@Override
	public long readNumber() {
		Token token = this.nextToken();
		if (token != Token.number) {
			syntaxError(Token.number, token);
		}
		return this.numberToken.longValue();
	}

	@Override
	public <T extends iWireData> void readArray(iWireDynamic<T> memberCreator, Consumer<T> addNewMember) {
		Token token = this.nextToken();
		if (token != Token.startArray) {
			syntaxError(Token.startArray, token);
		}
		while(true) {
			token = this.nextToken();
			
			if (token == Token.endArray) { return; }
			if (token == Token.comma) { continue; }
			this.ungetToken(token);
			
			T newMember = memberCreator.make();
			newMember.deserialize(this);
			addNewMember.accept(newMember);
		}
	}

	@Override
	public void readNumberArray(Consumer<Long> addNewMember) {
		Token token = this.nextToken();
		if (token != Token.startArray) {
			syntaxError(Token.startArray, token);
		}
		while(true) {
			token = this.nextToken();
			
			if (token == Token.endArray) { return; }
			if (token == Token.comma) { continue; }
			
			if (token != Token.number) {
				syntaxError(Token.number, token);
			}
			addNewMember.accept(this.numberToken.longValue());
		}
	}

	@Override
	public <T extends iWireData> void readStruct(T member) {
		Token token = this.nextToken();
		if (token != Token.startBlock) {
			syntaxError(Token.startBlock, token);
		}
		while(true) {
			token = this.nextToken();
			if (token == Token.endBlock) { break; }
			if (token != Token.string) { this.syntaxError(Token.string, token); }
			String fieldName = this.stringToken;
			
			if (this.nextToken() != Token.colon) { this.syntaxError(Token.colon, token); }
			member.handleField(fieldName, this);
			
			token = this.nextToken();
			if (token == Token.endBlock) { break; }
			if (token != Token.comma) { syntaxError(Token.comma, Token.endBlock, token); }
		}
	}

	@Override
	public void readStructure(FieldReader fr) {
		Token token = this.nextToken();
		if (token != Token.startBlock) {
			syntaxError(Token.startBlock, token);
		}
		while(true) {
			token = this.nextToken();
			if (token != Token.string) { this.syntaxError(Token.string, token); }
			String fieldName = this.stringToken;
			
			if (this.nextToken() != Token.colon) { this.syntaxError(Token.colon, token); }
			boolean proceed = fr.read(fieldName, this);
			if (!proceed) {
				fr = (n,r) -> { r.skip(); return true; };
			}
			
			token = this.nextToken();
			if (token == Token.endBlock) { break; }
			if (token != Token.comma) { syntaxError(Token.comma, Token.endBlock, token); }
		}
	}
	
	private void skipNested() {
		Token token = this.nextToken();
		while(token != Token.endBlock && token != Token.endArray) {
			if (token == Token.startArray || token == Token.startBlock) {
				this.skipNested();
			}
			token = this.nextToken();
		}
	}

	@Override
	public void skip() {
		Token token = this.nextToken();
		if (token == Token.startArray || token == Token.startBlock) {
			this.skipNested();
		}
	}

	@Override
	public void fail() {
		throw new IllegalStateException("invalid JSON: field/substructure not expected here");
	}

}
