package org.twak.viewTrace.facades;

import org.twak.mmg.MOgram;


public class MMGFeatureGen extends FeatureGenerator {

	// temporary storage for mogram twixt App and SkelGen
	public MOgram mogram;
	
	public MMGFeatureGen(MOgram mogram) {
		this.mogram = mogram;
	}
	
}
