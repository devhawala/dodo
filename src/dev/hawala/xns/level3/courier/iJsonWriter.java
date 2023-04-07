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

/**
 * Definition of sink objects for serializing Courier values to a JSON sink.
 * <p>
 * It is the responsibility of the Courier values to produce correct JSON structures,
 * implementations of this interface will only produce the necessary JSON syntax elements
 * as requested by the invokers.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2023)
 */
public interface iJsonWriter {
	
	/**
	 * Write the label of an JSON object member field.
	 * @param label the field name
	 * @return this writer for fluent API
	 */
	iJsonWriter writeFieldLabel(String label);

	/**
	 * Write a string value to JSON.
	 * @param value the value to write
	 * @return this writer for fluent API
	 */
	iJsonWriter writeString(String value);
	
	/**
	 * Write a boolean value to JSON.
	 * @param value the value to write
	 * @return this writer for fluent API
	 */
	iJsonWriter writeBoolean(boolean value);
	
	/**
	 * Write a numeric value to JSON.
	 * @param value the value to write
	 * @return this writer for fluent API
	 */
	iJsonWriter writeNumber(long value);
	
	/**
	 * Begin issuing an array of JSON items.
	 * @return  this writer for fluent API
	 */
	iJsonWriter openArray();
	
	/**
	 * End issuing an array of JSON items.
	 * @return  this writer for fluent API
	 */
	iJsonWriter closeArray();
	
	/**
	 * Begin issuing an JSON object.
	 * @return  this writer for fluent API
	 */
	iJsonWriter openStruct();
	
	/**
	 * End issuing an JSON object.
	 * @return  this writer for fluent API
	 */
	iJsonWriter closeStruct();
	
}
