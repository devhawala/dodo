package dev.hawala.xns.tests;

import dev.hawala.xns.level4.common.ChsDatabase;

public class TestChsDatabase {

	public static void main(String[] args) {
		ChsDatabase chsdb = new ChsDatabase("org.o", "dom.d", "./chs-database", true);
		chsdb.dump();
	}
	
}
