package org.twak.viewTrace.facades;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.MiniFacade.Feature;
import org.w3c.dom.css.Rect;

public class FeatureGenerator extends MultiMap<Feature, FRect> {

	MiniFacade mf;
	
	public FeatureGenerator( MiniFacade mf ) {
		this.mf = mf;
	}
	
	public FeatureGenerator( MiniFacade mf, MultiMap<Feature, FRect> features ) {
		
		this(mf);
		
		for (Map.Entry<Feature, List<FRect>> ee : features.entrySet()) {
			for (FRect e : ee.getValue())
				put( ee.getKey(), new FRect( e ) );
		}
	}

	public FeatureGenerator( FeatureGenerator featureGen ) {
		this (featureGen.mf, featureGen);
	}

	public void add( Feature feat, DRectangle rect ) {
		FRect f = new FRect( rect );
		f.f = feat;
		put( feat, f );
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
			for (FRect e : ee.getValue())
				out.put( ee.getKey(), new FRect( e ) );
		}
		return out;
	}

}
