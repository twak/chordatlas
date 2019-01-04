package org.twak.viewTrace.facades;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.twak.viewTrace.franken.PanesLabelApp;
import org.twak.viewTrace.franken.PanesTexApp;

public class FeatureGenerator extends MultiMap<Feature, FRect> {

	MiniFacade mf;
	
	public double[] facadeStyle;
	
	public FeatureGenerator() {}
	
	public FeatureGenerator( MiniFacade mf ) {
		this.mf = mf;
	}
	
	public FeatureGenerator( MiniFacade mf, MultiMap<Feature, FRect> features ) {
		
		this(mf);
		
		for (Map.Entry<Feature, List<FRect>> ee : features.entrySet()) {
			for (FRect e : ee.getValue()) {
				FRect fr = new FRect( e, mf);
				fr.setFeat( ee.getKey() );
				put( fr.getFeat(), fr );
			}
		}
	}

	public FeatureGenerator( FeatureGenerator featureGen ) {
		this (featureGen.mf, featureGen);
		this.facadeStyle = featureGen.facadeStyle;
	}

	public FRect add( Feature feat, DRectangle rect ) {
		
		FRect f;
		if (rect instanceof FRect)
			f = (FRect) rect;
		else {
			f = new FRect( rect, mf );
			f.setFeat( feat );
		}
		
		put( feat, f );
		return f;
	}

	public void update() {/*override me*/}
	
	public List<FRect> getRects( Feature... feats ) {

		if ( feats.length == 0 )
			feats = Feature.values();

		List<FRect> rs = new ArrayList<>();

		for ( Feature f : feats )
			rs.addAll( get( f ) );

		return rs;
	}

	public FeatureGenerator copy(MiniFacade n) {
		FeatureGenerator out = new FeatureGenerator( n );
		for (Map.Entry<Feature, List<FRect>> ee : entrySet()) {
			for (FRect e : ee.getValue()) {
				FRect fr = new FRect( e );
				
				fr.panesLabelApp = (PanesLabelApp) e.panesLabelApp.copy();
				fr.panesTexApp   = (PanesTexApp  ) e.panesTexApp.copy();
				
				out.put( ee.getKey(), fr );
			}
		}
		return out;
	}

	public void setMF( MiniFacade mf ) {
		this.mf = mf;
		for (FRect f : valueList()) {
			f.mf = mf;
		}
		
	}
}
