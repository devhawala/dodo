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

package dev.hawala.xns.level4.common;

import java.util.List;

import dev.hawala.xns.level3.courier.iWireData;

/**
 * Abstraction for an ClearingHouse object allowing interested service implementations
 * to add properties to the object based on key/value-pairs loaded from the
 * "chs backing store" into the object but uninterpreted by the CHS implementation.
 * <br/>
 * This allows an CHS entry to contain properties defined by a certain Courier program
 * without requiring the ClearingHouse implementation to know about these Courier programs.
 * <br/>
 * Or in other words: this decouples the representation of the Courier data type (known only by
 * the Service implementation) from the values provided by the ClearingHouse (which only knows
 * where to get those values from) and used to fill this Courier data added to the CHS entry.
 * <br/>
 * Or again in other words: this allows the Service represented by certain CHS entries to complete
 * these entries with information (type and content) that the ClearingHouse Service does not know.
 * <p>
 * For example, GAP related CHS entries have several string key/values used by the GAP
 * service to create the GAP-specific Courier RECORD instance for the property on the
 * CHS object (see {@code CHEntries0}) uniquely identifying the connection of the GAP CHS object.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2023)
 */
public interface iUpdatableChsEntry {
	
	/**
	 * @return the full qualified XNS name of this ChsEntry
	 */
	String getFqn();
	
	/**
	 * @return the name of the configuration file defining this ChsEntry
	 */
	String getCfgFilename();
	
	/**
	 * @return the CHS type of the CHS entry (see {@code CHEntries0})
	 */
	int getType();
	
	/**
	 * @return the keys for all uninterpreted key/value-pairs in this CHS entry 
	 */
	List<String> getUninterpretedProperties();

	/**
	 * Get the value of an uninterpreted key/value-pair
	 * @param name the key of the pair
	 * @return the value for the key or {@code null} if the key is unknown
	 */
	String getUninterpretedProperty(String name);
	
	/**
	 * Set the Courier value for the given property of this CHS object.
	 * @param property the property id to be set on this object
	 * @param value the Courier data value for the property
	 */
	void setChsProperty(int property, iWireData value);
	
}
