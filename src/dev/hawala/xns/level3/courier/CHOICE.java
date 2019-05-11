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

/**
 * Representation of the Courier CHOICE datatype.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public abstract class CHOICE<T extends Enum<T>> implements iWireData {
	
	protected final ENUM<T> choiceValue;
	
	protected RECORD choiceContent = null;
	
	public CHOICE(ENUM<T> cv) {
		this.choiceValue = cv;
	}
	
	abstract protected boolean isAcceptableChoice(T val);
	
	abstract protected RECORD createContentFor(T choiceValue); 

	public T getChoice() {
		return this.choiceValue.get();
	}
	
	public RECORD setChoice(T val) {
		if (!this.isAcceptableChoice(val)) {
			throw new IllegalArgumentException("value for CHOICE has no mapped content definition");
		}
		this.choiceValue.set(val);
		this.choiceContent = this.createContentFor(this.choiceValue.get());
		return this.choiceContent;
	}
	
	public RECORD getContent() {
		return this.choiceContent;
	}
}
