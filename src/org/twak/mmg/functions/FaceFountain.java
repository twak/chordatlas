package org.twak.mmg.functions;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.twak.mmg.Function;
import org.twak.mmg.InputSet;
import org.twak.mmg.MMG;
import org.twak.mmg.Node;
import org.twak.utils.collections.Loop;
import org.twak.viewTrace.facades.GreebleHelper.LPoint2d;

public class FaceFountain extends Function {
	
	public Loop<LPoint2d> face;

	public FaceFountain (Loop<LPoint2d> face) {
		
		this.face = face;
		
//        outputType = OBB.class;
	}

	@Override
	public List<Node> createSN( InputSet inputSet, MMG mmg ) {
		
		Map<String, Node> labels = face.stream().map( l -> l.label ).collect( Collectors.toMap( x->x, y -> new Node( new FixedLabel(y) ) ) );
		
		
		return super.createSN( inputSet, mmg );
	}
	
	@Override
	public Object evaluate( List<Object> params, List<Object> curry, Node node ) {
		// TODO Auto-generated method stub
		return null;
	}
	
}
