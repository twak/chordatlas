package org.twak.viewTrace.facades;

import java.util.ArrayList;
import java.util.List;

import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.MiniFacade.Feature;

public class FeatureGenerator extends MultiMap<Feature, FRect> {

	MiniFacade mf;
	
	public FeatureGenerator( MiniFacade mf ) {
		this.mf = mf;
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

}
