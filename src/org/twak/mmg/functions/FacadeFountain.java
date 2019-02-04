package org.twak.mmg.functions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.vecmath.Point2d;

import org.twak.mmg.Command;
import org.twak.mmg.Function;
import org.twak.mmg.InputSet;
import org.twak.mmg.MMG;
import org.twak.mmg.Node;
import org.twak.mmg.prim.Label;
import org.twak.mmg.prim.LineSegment;
import org.twak.mmg.prim.Path;
import org.twak.utils.Line;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.Loopable;
import org.twak.viewTrace.facades.GreebleHelper.LPoint2d;

public class FacadeFountain extends Function {

	public Loop<LPoint2d> face;
	String feature;

	public FacadeFountain() {
		color = fixed;
	}

	public FacadeFountain( Loop<LPoint2d> face, String feature ) {

		this();
		this.face = face;
		this.feature = feature;
	}

	@Override
	public List<Node> createSN( InputSet inputSet, List<Object> vals, MMG mmg, Command mo ) {

		List<Node> out = new ArrayList<>();

		List<Object> result = new ArrayList<>();
		
		for ( Loopable<LPoint2d> lp : face.loopableIterator() ) 
			if (lp.get().label.equals (feature))
				result.add ( new Path ( new LineSegment ( new Line( new Point2d( lp.get() ), new Point2d( lp.getNext().get() ) ) ) ) );

		Node segNode = new Node( this );
		out.add( segNode );
		
		segNode.curriedArguments.add(result);
		
		return out;
	}

	@Override
	public Object evaluate( List<Object> params, List<Object> curry, Node node, MMG mmg ) {
		return node.curriedArguments.get(0);
	}

}
