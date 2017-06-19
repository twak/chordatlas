package org.twak.mmg.functions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.vecmath.Point2d;

import org.twak.mmg.Function;
import org.twak.mmg.InputSet;
import org.twak.mmg.MMG;
import org.twak.mmg.Node;
import org.twak.mmg.prim.Label;
import org.twak.utils.Line;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.Loopable;
import org.twak.viewTrace.facades.GreebleHelper.LPoint2d;

public class FacadeFountain extends Function {

	public Loop<LPoint2d> face;
	Map<String, Label> labelLookup = new LinkedHashMap<>();

	public FacadeFountain() {
	}

	public FacadeFountain( Loop<LPoint2d> face ) {

		this.face = face;

		color = fixed;
	}

	@Override
	public List<Node> createSN( InputSet inputSet, MMG mmg ) {

		List<Node> out = new ArrayList<>();

		for ( LPoint2d f : face ) {
			Label l = labelLookup.get( f.label );
			if ( l == null ) {
				l = new Label( f.label );
				labelLookup.put( f.label, l );
			}
		}

		for (Label l : labelLookup.values()) {
			FixedLabel labelFn = new FixedLabel( l );
			Node label = new Node( labelFn );
			out.add( label );
		}
		
		for ( Loopable<LPoint2d> lp : face.loopableIterator() ) {
			FixedSegmentPath segFn = new FixedSegmentPath( new Line( new Point2d( lp.get() ), new Point2d( lp.getNext().get() ) ) );
			Node segNode = new Node( segFn );
			out.add( segNode );
			labelLookup.get( lp.get().label ).label( segNode, mmg );
		}

		return out;
	}

	@Override
	public Object evaluate( List<Object> params, List<Object> curry, Node node ) {
		// TODO Auto-generated method stub
		return null;
	}

}
