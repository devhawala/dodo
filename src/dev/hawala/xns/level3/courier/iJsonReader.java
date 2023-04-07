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

import java.util.function.Consumer;

/**
 * Definition of source objects for deserializing Courier values from a JSON source.
 * <p>
 * It is the responsibility of the Courier values to correctly interpret JSON syntactical
 * structures, implementations of this interface will only provide the necessary scanner
 * functionality for reading the next JSON item an specified by invokers.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2023)
 */
public interface iJsonReader {
	
	/**
	 * Read the next JSON item, which must be a string value.
	 * @return the string
	 */
	String readString();
	
	/**
	 * Read the next JSON item, which must be a boolean value
	 * @return the boolean value
	 */
	boolean readBoolean();
	
	/**
	 * Read the next JSON item, which must be a numeric value
	 * @return the numeric value, truncated to an integer 
	 */
	long readNumber();
	
	/**
	 * Read the next JSON item, which must be an array of arbitrary items
	 * @param <T> the (Courier) item type for the array elements
	 * @param memberCreator callback for creating a new item of the array
	 * @param addNewMember callback for using an item deserialized from JSON, e.g. for adding it to a list or sequence
	 */
	<T extends iWireData> void readArray(iWireDynamic<T> memberCreator, Consumer<T> addNewMember);
	
	/**
	 * Read the next JSON item, which must be an array of numeric values
	 * @param addNewMember callback for using a number deserialized from JSON, e.g. for adding it to a list or sequence 
	 */
	void readNumberArray(Consumer<Long> addNewMember);
	
	/**
	 * Read the next JSON item, which must be a object, into a Courier RECORD type value
	 * @return the numeric value, truncated to an integer 
	 */
	<T extends iWireData> void readStruct(T member);
	
	/** Callback for reading a single field form a JSON structure */
	@FunctionalInterface
	public interface FieldReader {
		// returns: continue?
		boolean read(String fieldLabel, iJsonReader rd);
	}
	
	/**
	 * Read the next JSON item, which must be a object, calling the callback for each field encountered in the JSON item 
	 * @param fr the field reader callback, invoked for each named sub-item in the JSON object
	 */
	void readStructure(FieldReader fr);
	
	/**
	 * Ignore (skip) the next JSON item
	 */
	void skip();
	
	/**
	 * Abort reading from JSON by throwing an exception
	 */
	void fail();

}
