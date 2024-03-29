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

import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;

/**
 * Definition of the interface for serializable Courier data values.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018,2023)
 *
 */
public interface iWireData {

	/**
	 * Serialize a Courier data value to a Courier sink. 
	 * @param ws the Courier sink to use
	 * @throws NoMoreWriteSpaceException
	 */
	void serialize(iWireStream ws) throws NoMoreWriteSpaceException;
	
	/**
	 * Deserialize a Courier data value from a Courier source.
	 * @param ws the Courier source to use
	 * @throws EndOfMessageException
	 */
	void deserialize(iWireStream ws) throws EndOfMessageException;
	
	/**
	 * Produce the readable string representation of the data value.
	 * @param to destination where to append the string representation
	 * @param indent number of blanks to prepend to each line for representing the hierarchical structure of the parent items 
	 * @param fieldName the name of this data value in the parents structure
	 * @return {@code to} for fluent API
	 */
	StringBuilder append(StringBuilder to, String indent, String fieldName);
	
	/**
	 * Serialize a Courier data value to a JSON sink. 
	 * @param ws the JSON sink to use
	 * @throws NoMoreWriteSpaceException
	 */
	void serialize(iJsonWriter wr);
	
	/**
	 * Deserialize a Courier data value from a JSON source.
	 * @param ws the JSON source to use
	 * @throws EndOfMessageException
	 */
	void deserialize(iJsonReader rd);
	
	/**
	 * Deserialize the structure member {@code fieldName} of this object (Courier RECORD type) from a JSON source.
	 * @param fieldName the name of the member to deserialize
	 * @param rd the JSON source to use
	 */
	default void handleField(String fieldName, iJsonReader rd) {
		rd.fail();
	}
}
