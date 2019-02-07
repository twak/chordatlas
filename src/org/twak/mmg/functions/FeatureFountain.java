package org.twak.mmg.functions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import javax.vecmath.Point2d;

import org.twak.mmg.Command;
import org.twak.mmg.FollowOnMO;
import org.twak.mmg.Function;
import org.twak.mmg.InputSet;
import org.twak.mmg.MMG;
import org.twak.mmg.Node;
import org.twak.mmg.Reference;
import org.twak.mmg.MMG.Frame;
import org.twak.mmg.MOgram;
import org.twak.mmg.prim.OBB;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacade.Feature;

public class FeatureFountain extends Function implements FollowOnMO {

	public MiniFacade mini;
	public Feature f;

	public FeatureFountain() {
		color = fixed;
	}

	public FeatureFountain( Feature f, MiniFacade mini ) {
		this();
		this.f = f;
		this.mini = mini;
	}

	@Override
	public List<Node> createSN( InputSet inputSet, List<Object> vals, MMG mmg, Command mo, MOgram mogram ) {
		List<Node> out = new ArrayList<>();

		Node parent = new Node( this );
		out.add( parent );

		List<Object> rects = new ArrayList<>();
		
		for ( DRectangle r : mini.featureGen.get( f ) ) {
			
			for (Point2d p : r.points()) {
				rects.add( new Point2d(p) );
			}
			
//			rects.add( new OBB(r.x, r.y, r.width, r.height, 0) );
		}
		
		parent.result = rects;
		parent.curriedArguments.add( rects );

		return out;
	}
	
	@Override
	public Object evaluate( List<Object> params, List<Object> curry, Node node, MMG mmg ) {
		if ( node.function == this )
			return node.curriedArguments.get(0);
		else
			return null; // label
	}

	@Override
	public String getDescription() {
		return getClass().getSimpleName();
	}

	@Override
	public Function createNextMO() {
		return new ListWrapper();
	}

}
