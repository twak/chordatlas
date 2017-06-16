package org.twak.mmg.functions;

import java.util.ArrayList;
import java.util.List;

import org.twak.mmg.Function;
import org.twak.mmg.InputSet;
import org.twak.mmg.MMG;
import org.twak.mmg.Node;
import org.twak.mmg.prim.OBB;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;

public class FeatureFountain  extends Function {

	public MiniFacade mini;
	public Feature feature;
	
	public FeatureFountain (Feature feature, MiniFacade mini) {
		
		this.feature = feature;
		this.mini    = mini;
		
        outputType = OBB.class;

        color = fixed;
	}
	
	public FeatureFountain() {}

	@Override
	public List<Node> createSN( InputSet inputSet, MMG mmg ) {
		List<Node> out = new ArrayList<>();
		
		for (DRectangle r : mini.getRects( feature )) {
			
			Node n = new Node( this );
			n.curriedArguments.add( r );
			out.add(n);
		}
		
		return out;
	}
	
	@Override
	public Object evaluate( List<Object> params, List<Object> curry, Node node ) {
		
		DRectangle rect = (DRectangle) node.curriedArguments.get( 0 );
		return new OBB(rect.x, rect.y, rect.width, rect.height, 0 );
	}
	
	@Override
	public String getDescription() {
		return getClass().getSimpleName() + " ("+ feature.name().toLowerCase() +")";
	}

}
